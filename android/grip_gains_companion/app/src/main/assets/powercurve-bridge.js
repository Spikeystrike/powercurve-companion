/* Powercurve DOM adapter. No React internals, credentials, API calls or timer patches. */
(function install() {
  'use strict';
  if (location.origin !== 'https://powercurve.tantaluspath.com' || window !== window.top) return;
  if (!document.body) { document.addEventListener('DOMContentLoaded', install, { once: true }); return; }
  if (window.PowercurveCompanion) { window.PowercurveCompanion.refresh(); return; }
  let previousPhase = '', generation = 0, lastEndedKey = null, recommendationNotBefore = 0;
  const visible = el => !!el && el.getClientRects().length > 0;
  const text = el => el?.textContent?.trim() || '';
  function applyStartupSelection() {
    const token = window.PowercurveStartupToken;
    if (!token) return;
    try {
      if (sessionStorage.getItem('powercurve.nativeStartup') === token) return;
      const face = document.querySelector('.timerPanel .timerFace');
      if (!face?.classList.contains('timerFace-setup')) return;
      const fields = [...document.querySelectorAll('.timerSetupGrid label')];
      const gripper = fields.find(el => text(el.querySelector('span')) === 'Gripper')?.querySelector('select');
      const side = fields.find(el => text(el.querySelector('span')) === 'Side')?.querySelector('select');
      if (!gripper || !side || gripper.disabled || side.disabled) return;
      if (![...gripper.options].some(o => o.value === 'micro') || ![...side.options].some(o => o.value === 'left')) return;
      sessionStorage.setItem('powercurve.nativeStartup', token);
      recommendationNotBefore = Date.now() + 500;
      const setValue = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value').set;
      for (const [input, value] of [[gripper, 'micro'], [side, 'left']]) {
        setValue.call(input, value);
        input.dispatchEvent(new Event('change', { bubbles: true }));
      }
    } catch (_) { /* Leave the website usable if browser storage is unavailable. */ }
  }
  function applyRecommendedTarget() {
    const token = window.PowercurveRecommendationToken;
    if (!token || Date.now() < recommendationNotBefore) return;
    try {
      if (sessionStorage.getItem('powercurve.nativeRecommendation') === token) return;
      const face = document.querySelector('.timerPanel .timerFace');
      if (!face?.classList.contains('timerFace-setup')) return;
      const input = document.querySelector('.timerSetupGrid input[aria-label^="Weight ("]');
      // An existing choice (including an edit in progress) takes precedence.
      if (input?.value.trim()) {
        sessionStorage.setItem('powercurve.nativeRecommendation', token);
        return;
      }
      const button = document.querySelector('.timerZoneButton-recommended');
      if (!visible(button) || button.disabled) return;
      sessionStorage.setItem('powercurve.nativeRecommendation', token);
      button.click();
    } catch (_) { /* Wait for a usable recommendation without blocking the page. */ }
  }
  function snapshot() {
    applyStartupSelection();
    applyRecommendedTarget();
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
    const weightInput = document.querySelector('.timerSetupGrid input[aria-label^="Weight ("]');
    let weight = fields.Weight || null;
    if (phase === 'setup') {
      weight = null;
      if (weightInput && weightInput.getAttribute('aria-invalid') !== 'true') {
        const unit = /^Weight \((kg|lbs?)\)$/i.exec(weightInput.getAttribute('aria-label') || '')?.[1];
        const value = weightInput.value.trim().replace(',', '.');
        if (unit && /^(?:\d+(?:\.\d*)?|\.\d+)$/.test(value) && Number(value) > 0) weight = Number(value) + ' ' + unit;
      }
    }
    const seconds = Number(text(face?.querySelector('strong')));
    return {
      phase, active, repKey: generation + ':' + text(face?.querySelector('p')),
      seconds: Number.isFinite(seconds) ? seconds : null,
      weight, gripper: fields.Gripper || null, side: fields.Side || null,
      plannedReps: Number(fields['Target reps']) || 6,
      completedReps: Array.from(document.querySelectorAll('.timerRepList strong')).map(el => {
        const m = /^(\d+):(\d{2})$/.exec(text(el));
        return m ? Number(m[1])*60+Number(m[2]) : 0;
      }),
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
  document.addEventListener('input', refresh);
  document.addEventListener('change', refresh);
  setInterval(refresh, 200);
  refresh();
})();
