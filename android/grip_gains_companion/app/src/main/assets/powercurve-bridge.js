/* Powercurve DOM adapter. No React internals, credentials, API calls or timer patches. */
(() => {
  'use strict';
  if (location.origin !== 'https://powercurve.tantaluspath.com' || window !== window.top) return;
  if (window.PowercurveCompanion) { window.PowercurveCompanion.refresh(); return; }
  let previousPhase = '', generation = 0, lastEndedKey = null;
  const visible = el => !!el && el.getClientRects().length > 0;
  const text = el => el?.textContent?.trim() || '';
  function snapshot() {
    const face = document.querySelector('.timerPanel .timerFace');
    const phase = ['setup','countdown','rep','rest','complete'].find(p => face?.classList.contains('timerFace-' + p)) || 'unavailable';
    if (phase !== previousPhase) {
      if (phase === 'rep') generation++;
      previousPhase = phase;
    }
    const button = document.querySelector('.timerPanel .timerMainButton');
    const active = phase === 'rep' && visible(face) && visible(button) && !button.disabled && text(button) === 'End rep';
    const fields = {};
    document.querySelectorAll('.timerSetSummary > div').forEach(el => {
      const label = text(el.querySelector('span'));
      fields[label] = text(el.querySelector('strong'));
    });
    const seconds = Number(text(face?.querySelector('strong')));
    return {
      phase, active, repKey: generation + ':' + text(face?.querySelector('p')),
      seconds: Number.isFinite(seconds) ? seconds : null,
      weight: fields.Weight || null, gripper: fields.Gripper || null, side: fields.Side || null,
      targetDuration: /^\d+s$/.test(fields['Estimated hold'] || '') ? parseInt(fields['Estimated hold'], 10) : null,
      url: location.origin + location.pathname
    };
  }
  function refresh() {
    const s = snapshot();
    window.PowercurveNative?.postMessage(JSON.stringify(s));
    return s;
  }
  function endRep(expectedKey) {
    const s = snapshot();
    if (!s.active || s.repKey !== expectedKey || lastEndedKey === expectedKey || document.hidden) return false;
    lastEndedKey = expectedKey;
    document.querySelector('.timerPanel .timerMainButton').click();
    refresh();
    return true;
  }
  window.PowercurveCompanion = { refresh, endRep };
  // One observer and one heartbeat per document, including SPA navigation.
  new MutationObserver(refresh).observe(document.body, { childList: true, subtree: true, characterData: true, attributes: true, attributeFilter: ['class','disabled'] });
  setInterval(refresh, 200);
  refresh();
})();
