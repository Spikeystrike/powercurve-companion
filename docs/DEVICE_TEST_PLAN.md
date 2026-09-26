# Device validation (hardware checks pending)

For Tindeq Progressor, PitchSix Force Board and WH-C06, test on a real Android phone:

- First installation, permission allow/deny, Bluetooth off/on, discovery, connection and reconnection.
- Five seconds of unloaded calibration, followed by a known load. Compare displayed kg against the reference.
- WH-C06: verify Connected appears only after readings. Test kg/lb modes on the scale and the fallback unit for firmware without a unit code. Changing app display units must not change internal kg readings.
- Return from Settings several times: the measurement stream must continue without scan failures.
- Turn the scale off: after 15 seconds the app must leave Connected. Turn it on: readings should resume. No artificial zero-force sample or auto-end may be generated.
- Sign in to Powercurve. Change Weight in setup, clear it, use a decimal comma and switch website units. The target must follow the field and active set, not a previous manual preference. Test consecutive sets with different weights.
- Countdown and rest must not end a rep at zero force.
- Set Target Weight to 20 kg and force drop to 50% / 250 ms. Stay below 18 kg, then release: the detector must not arm. Hold 18 kg or more for at least 300 ms, then lower to 9 kg: exactly one rep should end. A sustained overshoot to 40 kg must not raise the 10 kg completion limit; 12 kg should keep running.
- Set Target Weight to 2 kg: hold 1.8 kg, then drop below 1 kg. This must work without a 3 kg minimum. Clear/change the target or disconnect: stable loading at 90% of the current target is required again.
- Test a brief spike, a short unloading and a single high reading: no premature completion.
- After a signal gap, require stable loading again. Switching devices must reset the arming state.
- End a rep manually and immediately send samples: the next rep must not end accidentally.
- Background the app or lock the screen: no background or delayed auto-end action.
- Save a session in Powercurve and verify it after reload. Local force data is separate from the server session.
- Rotation, restart, slow/offline network and expired sign-in: status should remain understandable.
- Inspect all native labels, errors and settings for English text.

Automated tests cover deterministic behavior; they do not replace firmware compatibility or authenticated end-to-end tests against the production backend.

## Offline acceptance

- Sign in once while online, connect the scale, then disable internet (keep Bluetooth enabled). The force graph must keep updating. Complete at least two local sets and verify the pending count.
- Restart the app without internet: queued sets remain. Interrupt a third set after one rep, restart, then continue or save the recovered completed reps.
- Restore internet with the same account signed in. Verify import through Import training data, the original date/weight/rep durations in Sessions, a pending count of zero and the persistent success count. Dismiss it; ordinary online use should have no sync banner.
- Interrupt connectivity during import, then restore it: verify no duplicate session appears. Expire sign-in or change accounts: sets must stay queued until the correct account is available.
- Complete a website set during a reception loss: the existing timer must survive until completion, and its reps must be queued once.
- Complete an ordinary fully online website set: it must not also enter the offline queue.
