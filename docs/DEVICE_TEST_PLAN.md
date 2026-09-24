# Device validation (hardware checks pending)

For Tindeq Progressor, PitchSix Force Board and WH-C06, test on a real Android phone:

- First installation, permission allow/deny, Bluetooth off/on, discovery, connection and reconnection.
- Five seconds of unloaded calibration, followed by a known load. Compare displayed kg against the reference.
- WH-C06: verify Connected appears only after readings. Test kg/lb modes on the scale and the fallback unit for firmware without a unit code. Changing app display units must not change internal kg readings.
- Return from Settings several times: the measurement stream must continue without scan failures.
- Turn the scale off: after 15 seconds the app must leave Connected. Turn it on: readings should resume. No artificial zero-force sample or auto-end may be generated.
- Sign in to Powercurve. Change Weight in setup, clear it, use a decimal comma and switch website units. The target must follow the field and active set, not a previous manual preference. Test consecutive sets with different weights.
- Countdown and rest must not end a rep at zero force.
- Hold 20 kg for at least one second, then lower to 9 kg with 50% / 250 ms settings. Exactly one rep should end; 12 kg should keep it running.
- Test a brief spike, a short unloading and a single high reading: no premature completion.
- After a signal gap, require stable loading again. Switching devices must not carry the previous rep's peak into the next rep.
- End a rep manually and immediately send samples: the next rep must not end accidentally.
- Background the app or lock the screen: no background or delayed auto-end action.
- Save a session in Powercurve and verify it after reload. Local force data is separate from the server session.
- Rotation, restart, slow/offline network and expired sign-in: status should remain understandable.
- Inspect all native labels, errors and settings for English text.

Automated tests cover deterministic behavior; they do not replace firmware compatibility or authenticated end-to-end tests against the production backend.
