const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const source = fs.readFileSync('android/grip_gains_companion/app/src/main/assets/powercurve-bridge.js', 'utf8');
function fixture(origin = 'https://powercurve.tantaluspath.com', ready = true) {
  let phase = 'setup', rep = 1, clicks = 0, observers = 0, intervals = 0;
  let weight = '20', unit = 'kg', summaryWeight = '20 kg', invalid = false;
  const input = { get value() { return weight; }, getAttribute(name) { return name === 'aria-label' ? `Weight (${unit})` : (invalid ? 'true' : null); } };
  const messages = [];
  const readyCallbacks = [];
  const visible = { getClientRects: () => [1] };
  const button = { ...visible, disabled: false, get textContent() { return phase === 'rep' ? 'End rep' : 'Start'; }, click() { clicks++; phase = 'rest'; } };
  const face = { ...visible, classList: { contains: value => value === 'timerFace-' + phase }, querySelector: selector => ({ textContent: selector === 'strong' ? '4' : `Rep ${rep} of 3` }) };
  const window = { PowercurveNative: { postMessage: json => messages.push(JSON.parse(json)) } }; window.top = window;
  const context = vm.createContext({window, location: { origin, pathname: '/timer' },
    document: { body: ready ? {} : null, addEventListener: (event, callback) => { if (event === 'DOMContentLoaded') readyCallbacks.push(callback); }, hidden: false, querySelector: selector => selector.includes('timerSetupGrid') ? input : selector.endsWith('.timerFace') ? face : button,
      querySelectorAll: () => ['Weight|' + summaryWeight,'Gripper|20 mm','Side|Left'].map(x => ({querySelector: s => ({textContent:x.split('|')[s === 'span' ? 0 : 1]})})) },
    MutationObserver: class { observe() { observers++; } }, setInterval: () => intervals++ });
  vm.runInContext(source, context);
  return { window, context, messages, button, readyCallbacks, setPhase(p, r=rep) { phase=p; rep=r; }, setWeight(value, u='kg', bad=false) { weight=value;unit=u;invalid=bad; }, setSummary(value) {summaryWeight=value;}, get clicks() {return clicks;}, counts: () => [observers, intervals] };
}
test('strict origin guard', () => assert.equal(fixture('https://example.com').window.PowercurveCompanion, undefined));
test('installer is idempotent across SPA changes', () => {
  const f=fixture(); vm.runInContext(source,f.context); assert.deepEqual(f.counts(),[1,1]);
});
test('ends only the active rep; rejects stale keys and duplicates', () => {
  const f=fixture(), b=f.window.PowercurveCompanion;
  assert.equal(b.endRep(b.refresh().repKey),false);
  f.setPhase('rep'); const s=b.refresh(); assert.equal(s.active,true); assert.equal(s.weight,'20 kg');
  assert.equal(b.endRep('stale'),false); assert.equal(b.endRep(s.repKey),true);
  assert.equal(b.endRep(s.repKey),false); assert.equal(f.clicks,1);
  f.setPhase('rep',2); const second=b.refresh(); assert.notEqual(second.repKey,s.repKey);
  assert.equal(b.endRep(s.repKey),false); assert.equal(b.endRep(second.repKey),true);
});
test('disabled, background, rest and complete timers cannot end a rep', () => {
  const f=fixture(), b=f.window.PowercurveCompanion;
  f.setPhase('rep'); f.button.disabled=true; assert.equal(b.endRep(b.refresh().repKey),false);
  f.button.disabled=false; f.context.document.hidden=true; assert.equal(b.endRep(b.refresh().repKey),false);
  f.context.document.hidden=false;
  for (const p of ['countdown','rest','complete']) {f.setPhase(p); assert.equal(b.endRep(b.refresh().repKey),false);}
  assert.equal(f.clicks,0);
});

test('early WebView injection waits for the document body before installing', () => {
  const f=fixture('https://powercurve.tantaluspath.com',false);
  assert.equal(f.window.PowercurveCompanion,undefined);
  assert.deepEqual(f.counts(),[0,0]);
  f.context.document.body={}; f.readyCallbacks.forEach(callback=>callback());
  assert.deepEqual(f.counts(),[1,1]);
  f.setPhase('rep'); assert.equal(f.window.PowercurveCompanion.refresh().active,true);
});

test('setup target follows Weight input, decimal commas and website units', () => {
  const f=fixture(), b=f.window.PowercurveCompanion;
  assert.equal(b.refresh().weight,'20 kg');
  f.setWeight('32,5'); assert.equal(b.refresh().weight,'32.5 kg');
  f.setWeight('45','lb'); assert.equal(b.refresh().weight,'45 lb');
  for(const v of ['', 'bad', '0', '-4', '1.2.3']) {
    f.setWeight(v); assert.equal(b.refresh().weight,null);
  }
  f.setWeight('20','kg',true); assert.equal(b.refresh().weight,null);
});
test('active set summary replaces setup target; next setup cannot retain old weight', () => {
  const f=fixture(), b=f.window.PowercurveCompanion;
  f.setWeight('10'); assert.equal(b.refresh().weight,'10 kg');
  f.setSummary('30 lb'); f.setPhase('rep'); assert.equal(b.refresh().weight,'30 lb');
  f.setSummary(''); assert.equal(b.refresh().weight,null);
  f.setPhase('setup'); f.setWeight('25'); assert.equal(b.refresh().weight,'25 kg');
  f.setWeight(''); assert.equal(b.refresh().weight,null);
});
