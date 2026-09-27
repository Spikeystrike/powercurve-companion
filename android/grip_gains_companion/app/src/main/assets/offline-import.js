/* Offline queue transport: reads account/session state, writes only through Import training data. */
(function () {
  'use strict';
  if (location.origin !== 'https://powercurve.tantaluspath.com' || window !== window.top || window.PowercurveOfflineImport) return;
  let result = {state: 'idle', account: null, name: ''}, running = false, checked = 0;
  const auth = async () => {
    const response = await fetch('/api/auth/me', {credentials:'include', cache:'no-store', headers:{Accept:'application/json'}});
    if (!response.ok) throw new Error('Sign in to Powercurve to sync saved sets.');
    const data = await response.json();
    if (!data.user || data.user.id == null || data.support_session) throw new Error('Sign in to your own Powercurve account to sync.');
    return data.user;
  };
  let curveChecked = 0, curveLoading = false, curves = null;
  async function refreshCurves(user) {
    if (curveLoading || Date.now()-curveChecked < 30000) return;
    curveLoading=true; curveChecked=Date.now();
    try {
      const owner=String(user.id);
      const options={credentials:'include',cache:'no-store',headers:{Accept:'application/json','X-Powercurve-Expected-User-Id':owner}};
      const [graphResponse, sessionResponse]=await Promise.all([fetch('/api/curvefit/graphs',options),fetch('/api/curvefit/sessions',options)]);
      if(!graphResponse.ok || !sessionResponse.ok) return;
      const graph=await graphResponse.json(), rows=await sessionResponse.json();
      if(!Array.isArray(graph.sides) || !Array.isArray(rows.sessions)) return;
      const sides=graph.sides.filter(side => {
        const count=rows.sessions.filter(s=>s.gripper===side.gripper && s.side===side.side && !s.excluded_from_metrics && !s.hidden_from_graphs).length;
        return count>=5 && count===side.session_count && ['a','b','x0','c','d'].every(k=>Number.isFinite(side.params?.[k]))
          && !(graph.stale_sides || []).some(s=>s.gripper===side.gripper && s.side===side.side);
      });
      // Keep the last 60 per gripper/hand plus the latest set of every time zone for its detail card.
      const ordered=rows.sessions.filter(s=>!s.excluded_from_metrics && !s.hidden_from_graphs)
        .sort((a,b)=>new Date(b.date_time)-new Date(a.date_time) || Number(b.id)-Number(a.id));
      const counts=new Map(), zones=new Set();
      const sessions=ordered.filter(s=>{
        const key=s.gripper+':'+s.side, count=counts.get(key)||0;
        const hold=Array.isArray(s.rep_durations)&&s.rep_durations.length ? Math.max(...s.rep_durations) : s.max_hold;
        const zone=hold<48?0:hold<82?1:hold<129?2:hold<180?3:4;
        const zoneKey=key+':'+zone, latest=!zones.has(zoneKey);
        counts.set(key,count+1);zones.add(zoneKey);return count<60 || latest;
      }).map(s=>({id:s.id,date_time:s.date_time,gripper:s.gripper,side:s.side,weight:s.weight,rep_durations:s.rep_durations,max_hold:s.max_hold}));
      const current=await auth();
      if(String(current.id)!==owner) return;
      if(curves?.owner!==owner || JSON.stringify(curves.data.sides)!==JSON.stringify(sides) || JSON.stringify(curves.data.sessions)!==JSON.stringify(sessions)) curves={owner,data:{sides,sessions,savedAt:Date.now()}};
    } catch (_) { /* Keep the last verified local curve when offline. */ }
    finally { curveLoading=false; }
  }
  const sameSession = (s, r) => new Date(s.date_time).getTime() === new Date(r.date_time).getTime()
    && s.gripper === r.gripper && s.side === r.side && Math.abs(Number(s.weight) - r.weightKg / 0.45359237) < 0.011
    && JSON.stringify(s.rep_durations) === JSON.stringify(r.reps);
  async function submit(record) {
    if (running) return;
    running = true;
    result = {...result, state:'checking', id:record.id, message:''};
    try {
      const user = await auth();
      result.account = String(user.id); result.name = user.name || '';
      if (String(user.id) !== record.owner) throw new Error('Sign in to the account used for these offline sets.');
      const response = await fetch('/api/curvefit/sessions', {credentials:'include', cache:'no-store', headers:{Accept:'application/json','X-Powercurve-Expected-User-Id':record.owner}});
      if (!response.ok) throw new Error('Could not verify saved sessions. Retrying when connected.');
      const sessions = await response.json();
      if (!Array.isArray(sessions.sessions)) throw new Error('Session verification is unavailable. Saved sets are kept.');
      if (sessions.sessions.some(s => sameSession(s, record))) {
        result.state = 'success'; running = false; return;
      }
      const panel = document.querySelector('.sessionImportPanel');
      const input = panel?.querySelector('textarea');
      const button = panel?.querySelector('button[type="submit"]');
      if (!input || !button || input.disabled) throw new Error('Waiting for Import training data.');
      const payload = JSON.stringify({date_time:record.date_time, gripper:record.gripper, side:record.side,
        weight_lbs:record.weightKg/0.45359237, reps:record.reps});
      Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(input,payload);
      input.dispatchEvent(new Event('input',{bubbles:true}));
      input.dispatchEvent(new Event('change',{bubbles:true}));
      await new Promise(resolve => setTimeout(resolve,100));
      if (button.disabled || input.value !== payload) throw new Error('Import form is not ready. Saved sets are kept.');
      button.click();
      result.state='importing';
      const started=Date.now();
      // A fresh success message, exact result counts and cleared input confirm this submission.
      while (Date.now()-started < 60000) {
        await new Promise(resolve=>setTimeout(resolve,250));
        const message=panel.querySelector('.trainingImportUpdateResult .successMessage')?.textContent?.trim() || '';
        const error=panel.querySelector('.trainingImportUpdateResult .errorMessage')?.textContent?.trim();
        if (error) throw new Error(error);
        if (input.value === '' && /^Imported \d+ sessions?; (merged|updated) \d+; skipped \d+\./.test(message)) {
          // Confirm the actual row as well: never delete a queued set based only on UI text.
          const verified=await fetch('/api/curvefit/sessions',{credentials:'include',cache:'no-store',headers:{Accept:'application/json','X-Powercurve-Expected-User-Id':record.owner}});
          if(!verified.ok) throw new Error('Import confirmation interrupted. Will verify before retrying.');
          const rows=await verified.json();
          if(!Array.isArray(rows.sessions) || !rows.sessions.some(s=>sameSession(s,record))) throw new Error('Imported data could not be verified. Saved set retained.');
          result.state='success';running=false;return;
        }
      }
      throw new Error('Import confirmation timed out. Will verify before retrying.');
    } catch(error) { result.state='error';result.message=error.message;running=false; }
  }
  function poll(knownCurveVersion) {
    if(!running && Date.now()-checked>15000) {
      checked=Date.now();
      auth().then(user=>{result.account=String(user.id);result.name=user.name||'';refreshCurves(user);}).catch(()=>{result.account=null;result.name='';});
    }
    return {...result, curves: curves?.owner===result.account && knownCurveVersion!==curves.owner+":"+curves.data.savedAt ? curves : null};
  }
  window.PowercurveOfflineImport={poll,submit};
})();
