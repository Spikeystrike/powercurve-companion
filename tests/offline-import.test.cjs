const {test}=require('node:test');const assert=require('node:assert/strict');const vm=require('node:vm');const fs=require('node:fs');
const script=fs.readFileSync('android/grip_gains_companion/app/src/main/assets/offline-import.js','utf8');
const record={id:'one',owner:'7',date_time:'2026-09-26T12:00:00.123Z',gripper:'crusher',side:'left',weightKg:20,reps:[12,8],rest:10};
const row={date_time:record.date_time,gripper:'crusher',side:'left',weight:20/0.45359237,rep_durations:[12,8]};
function fixture(options={}) {
 let clicks=0, imported=false, value='', message='', error='';const requests=[];
 class TextArea {get value(){return value} set value(v){value=v} dispatchEvent(){}}
 const input=new TextArea();input.disabled=false;
 const button={disabled:false,getAttribute:()=> 'false',click(){clicks++;if(options.serverError){error='Network failed';return;} imported=true;value='';message='Imported 1 session; merged 0; skipped 0. Graph refresh queued for 1 side.';}};
 const panel={querySelector(s){if(s==='textarea')return input;if(s==='button[type="submit"]')return button;if(s.includes('successMessage'))return {textContent:message};if(s.includes('errorMessage'))return error?{textContent:error}:null;return null;}};
 const window={};window.top=window;
 const context={window,location:{origin:options.origin||'https://powercurve.tantaluspath.com'},document:{querySelector:()=>options.missingForm?null:panel},HTMLTextAreaElement:TextArea,Event:class{},setTimeout:cb=>{cb();},fetch:async(url,opts)=>{requests.push([url,opts]);if(url.includes('auth/me'))return {ok:!options.signedOut,json:async()=>({user:{id:options.account||7,name:'Test'}})};if(url.includes('/graphs'))return {ok:true,json:async()=>options.graph || {sides:[]}};if(options.curveRows)return {ok:true,json:async()=>({sessions:options.curveRows})};if(options.readFailure)return {ok:false};return {ok:true,json:async()=>({sessions:options.existing || (imported&&!options.missingRow) ? [row] : []})};},Date,JSON,Error};
 vm.runInNewContext(script,context);
 return {api:window.PowercurveOfflineImport,requests,get clicks(){return clicks}};
}
test('offline import origin guard',()=>assert.equal(fixture({origin:'https://evil.example'}).api,undefined));
test('imports through form and verifies the saved session before acknowledgement',async()=>{const f=fixture();await f.api.submit(record);assert.equal(f.api.poll().state,'success');assert.equal(f.clicks,1);assert.ok(f.requests.every(([,r])=>!r.method));});
test('lost acknowledgement reconciles existing row without a second import',async()=>{const f=fixture({existing:true});await f.api.submit(record);assert.equal(f.api.poll().state,'success');assert.equal(f.clicks,0);});
test('wrong account cannot submit',async()=>{const f=fixture({account:8});await f.api.submit(record);assert.equal(f.api.poll().state,'error');assert.equal(f.clicks,0);});
test('expired login retains queued set',async()=>{const f=fixture({signedOut:true});await f.api.submit(record);assert.equal(f.api.poll().state,'error');assert.equal(f.clicks,0);});
test('unavailable reconciliation never blindly posts again',async()=>{const f=fixture({readFailure:true});await f.api.submit(record);assert.equal(f.api.poll().state,'error');assert.equal(f.clicks,0);});
test('website errors are not acknowledged',async()=>{const f=fixture({serverError:true});await f.api.submit(record);assert.equal(f.api.poll().state,'error');});
test('success text without saved row is not acknowledged',async()=>{const f=fixture({missingRow:true});await f.api.submit(record);assert.equal(f.api.poll().state,'error');});
test('changed form fails safely',async()=>{const f=fixture({missingForm:true});await f.api.submit(record);assert.equal(f.api.poll().state,'error');assert.equal(f.clicks,0);});
test('concurrent submit is ignored',async()=>{const f=fixture();await Promise.all([f.api.submit(record),f.api.submit(record)]);assert.equal(f.clicks,1);});

const flush=async()=>{for(let i=0;i<30;i++)await Promise.resolve();};
test('curve cache verifies account and excludes stale or mismatched curves',async()=>{
 const side={gripper:'crusher',side:'left',session_count:5,params:{a:400,b:0.025,x0:0,c:0,d:0}};
 const rows=Array.from({length:5},()=>({gripper:'crusher',side:'left'}));
 const f=fixture({graph:{sides:[side]},curveRows:rows});f.api.poll();await flush();
 assert.equal(f.api.poll().curves.owner,'7');assert.equal(f.api.poll().curves.data.sides.length,1);
 const stale=fixture({graph:{sides:[side],stale_sides:[{gripper:'crusher',side:'left'}]},curveRows:rows});stale.api.poll();await flush();assert.equal(stale.api.poll().curves.data.sides.length,0);
 const wrongCount=fixture({graph:{sides:[side]},curveRows:rows.slice(1)});wrongCount.api.poll();await flush();assert.equal(wrongCount.api.poll().curves.data.sides.length,0);
});
