# Validation

## 0.1.17

Local validation on 2026-09-28:

- Optimized release APK built (versionCode 19 / versionName 0.1.17); all 50 JVM/Robolectric tests passed.
- Installed and launched the release APK in the Android emulator. Verified the Open offline mode icon beside Settings and tapped it with internet available: the Local set timer and Force curve panel appeared.
- The shortcut reuses the existing local timer entry point and leaves background synchronization enabled. It is disabled while a website set is running.
- Physical-device and authenticated-account checks remain unverified.

## 0.1.16

Local validation on 2026-09-27:

- Optimized release APK built (versionCode 18 / versionName 0.1.16). 50 JVM/Robolectric tests and 9 Android emulator tests passed on the debug test build.
- New queue tests verify edited values and deletions persist, invalid durations/weights are rejected, management pauses new uploads, and upload-started rows cannot be modified even after restart. Android tests exercise editing and confirmed deletion through the dialog.
- Fresh unchanged history updates the visible check time. Manual refresh restarts the background page without interrupting an in-flight import and times out with a retry message.
- Bluetooth sample tests check receiving status and expiry after silence. Unsupported curve taps are tested to preserve the previous timer weight and display an explanation.
- Release startup and update installation are smoke-tested separately. Physical BLE, authenticated production imports and 90/120-Hz performance remain unverified.

## 0.1.15

Local validation on 2026-09-27:

- Optimized release APK built with R8 and resource shrinking (versionCode 17 / versionName 0.1.15), with no debuggable flag. It retains the previous local signing certificate for in-place updates.
- All 48 JVM/Robolectric tests and 7 Android emulator tests passed on the debug test build. New tests check high-rate requests through long gestures, successive gestures, delayed restoration, pause reset and 60-Hz fallback.
- Installed the actual optimized release APK over the debug build without uninstalling. Release startup initially exposed a stripped Room constructor; a targeted keep rule fixes it. The corrected release starts successfully with no AndroidRuntime crash.
- High refresh rates are requested during touch interaction at the current screen resolution and released two seconds after the gesture or on pause. Actual 90/120-Hz rendering and frame-time improvement require a compatible physical device and remain unverified. Authenticated production and physical BLE checks remain unverified.
- Release lintVital checks pass; existing full-project lint limitations remain.

## 0.1.14

Local validation on 2026-09-27:

- Debug APK and test APK built successfully (versionCode 16 / versionName 0.1.14). All 46 JVM/Robolectric tests and 7 Android emulator instrumented tests passed.
- New sync regression test simulates null/missing page state and a signed-out page with zero queued sets, checks retry spacing and no retries offline, then verifies history refresh after recovery.
- New Android UI test checks Prime/Micro/Crusher and left/right switches clear weight, reps and target hold and prevent starting with stale values. Clicking the current selection preserves manual input.
- Healthy page polling, curve selection and existing storage/timer tests pass. Authenticated production-account and physical-device behavior remain unverified; existing lint limitations remain.

## 0.1.13

Local validation on 2026-09-27:

- Debug APK and test APK built (versionCode 15 / versionName 0.1.13). All 45 JVM/Robolectric tests, 22 JavaScript tests and 6 Android emulator tests passed.
- Curve tests verify a finite zero crossing, a 5% right margin, the 1-second fallback for asymptotic fits and valid timer selection at the right edge. Emulator checks cover the expanded axis and guide positions.
- History tests cover Prime, Crusher and Micro: stale snapshots retain recently acknowledged sets, fresh snapshots remove deleted imported sets even when the server cache contents are otherwise unchanged, queued sets remain, and removal persists after restart.
- Successful session fetches carry their request-start timestamp. Only acknowledged local history older than that fetch is replaced; queued sets and other accounts are untouched. Existing local copies without acknowledgement timestamps are reconciled on the first fresh snapshot.
- Authenticated production deletion and physical-device behavior remain unverified; existing lint limitations remain.

## 0.1.12

Local validation on 2026-09-27:

- Debug APK and test APK built successfully (versionCode 14 / versionName 0.1.12).
- All 43 JVM/Robolectric tests passed, including automatic restart of silent discovery, manual retry cooldown, no restart during a healthy measurement stream, cancellation on shutdown and late WH-C06 packets.
- All 6 Android emulator instrumented tests passed. The curve test verifies a zero-second axis label and guide positions against the fixed 0–300-second range.
- The Bluetooth status action previously called the idempotent scan-start method and could not restart an internally active scan. It now requests a real restart with an 8-second minimum interval. Silent scans retry after 30 seconds; WH-C06 filters also allow discovery beyond the remembered address.
- No physical phone was attached during validation. The user-reported WH-C06 startup-order problem is not yet confirmed resolved on hardware. Additional app logs record scan starts, failures and discovered devices. Authenticated production-account checks and existing lint limitations remain.

## 0.1.11

Local validation on 2026-09-27:

- Debug APK and test APK built successfully (versionCode 13 / versionName 0.1.11).
- All 42 JVM/Robolectric tests passed. New coverage checks oldest-zone selection including local history, and a saved WH-C06 switched on after 60 seconds without a device name, reconnecting after silence, scan failure recovery, and preserving the remembered device on shutdown.
- All 6 Android emulator instrumented tests passed. Curve checks verify Endurance is initially selected with no history, axis labels and readout follow selection and direct weight entry, and pixel checks confirm both guides stop at the selected point. Existing 300-second graph cap, matching, timer, storage and background checks also pass.
- Physical WH-C06 behavior and authenticated production-account checks remain unverified. Existing project-wide lint limitations remain.

## 0.1.10

Local validation on 2026-09-27:

- Debug APK and test APK built successfully (versionCode 12 / versionName 0.1.10).
- 40 JVM/Robolectric tests passed. Added inverse-matching coverage for 1, 20, 100, 350 and 400 seconds in kg/lb, invalid limits and unreachable curve targets.
- All 6 Android emulator instrumented tests passed. UI checks verify manual 10 kg input updates the fixture target to 231 seconds and 4 reps, matching 1 second sets 6 reps, and a 350-second target leaves the graph capped at 300 seconds.
- The background pixel test now samples inside the app content rather than the test activity's overlapping system status bar.
- Physical-device and authenticated production-account checks remain unverified; existing lint limitations remain.


## 0.1.9

Local validation on 2026-09-27:

- Debug APK and test APK built successfully (versionCode 11 / versionName 0.1.9).
- 39 JVM/Robolectric tests passed. New coverage includes longest-rep time-zone classification, rank-based opacity, calendar-day age, account/side isolation, pending/server deduplication, local history after acknowledgement/restart, and history-only cache updates.
- 21 JavaScript tests passed, including per-side retention of the latest 60 sets plus older latest-zone details.
- All 6 Android emulator instrumented tests passed. UI coverage checks 60 historical points and last-zone details without a fitted curve, plus hiding the target countdown after rep one. The fading points and zone highlights were visually inspected.
- History is cached per account. Offline queued sets are merged immediately; acknowledged local sets remain available until refreshed server data arrives. The fitted curve itself still refreshes online.
- Older server history requires one connected signed-in cache refresh after updating. Production authenticated history retrieval and physical BLE remain unverified; existing lint limitations remain.


## 0.1.8

Local validation on 2026-09-27:

- Debug APK and test APK built successfully (versionCode 10 / versionName 0.1.8).
- 33 JVM/Robolectric tests passed. New cases verify discard across active/rest/countdown/recovered states, preservation of previously queued sets, restart behavior, and target countdown through zero into negative time and reset at the next rep.
- All 5 Android emulator instrumented tests passed. Tests verify curve taps populate weight and target time, the target countdown appears during a rep, and discard removes the active record without queuing it.
- Target hold time is optional and editable. It is independent of the measured rep duration and does not automatically end a rep at zero.
- Physical-device and authenticated production-account checks remain unverified; existing project-wide lint limitations remain.


## 0.1.7

Local validation on 2026-09-27:

- Debug APK and Android test APK built successfully (versionCode 9 / versionName 0.1.7).
- 30 JVM/Robolectric tests passed, including the lower weight-axis bound and tap-to-curve mapping at both endpoints and the midpoint.
- All 4 Android emulator instrumented tests passed. UI checks cover Endurance-first selection, tapping the graph to display weight/time, and unchanged timer selection and storage behavior.
- Visually inspected the tighter weight axis, reversed zone order, and highlighted inspected point with weight/time readout. Tapping inspects the curve without changing timer settings.
- Physical-device and production-account checks remain unverified; existing lint limitations remain.


## 0.1.6

Local validation on 2026-09-27:

- Debug APK and Android test APK built successfully (versionCode 8 / versionName 0.1.6).
- 29 JVM/Robolectric tests passed. Added coverage verifies increasing weight/decreasing hold-time plot coordinates, no UI invalidations for idle ticks, one visible timer update per second while retaining every heartbeat, and unchanged curve/model reuse without disk writes.
- 20 JavaScript tests passed, including suppression of already received curve payloads.
- All 4 Android 13 emulator instrumented tests passed. The weight-horizontal / hold-time-vertical chart was visually inspected, including zone selection and the weight/time match.
- Full app offline smoke check: after startup settled and frame statistics were reset, a subsequent idle sample reported zero additional rendered frames. This is an idle-work check, not a frame-rate benchmark under physical BLE load.
- Curve samples and draw paths are cached. The main screen no longer subscribes to every timer revision, unchanged curves are not repeatedly copied across the WebView bridge or persisted, and a collapsed live graph stops drawing and collecting display data. Sensor processing and the timer heartbeat remain active.
- Performance on the user's phone with a real sensor and authenticated production import remains unverified. Existing project-wide lint limitations remain.


## 0.1.5

Local validation on 2026-09-27:

- Debug APK built with JDK 17 and SDK 36 (versionCode 7 / versionName 0.1.5).
- 26 JVM/Robolectric tests passed. New coverage includes curve inversion, all five zone matches in kg/lb, Prime weight steps, missing evidence, forecast bounds, persisted account isolation and timer target duration.
- 19 JavaScript tests passed, including authenticated curve-cache reads and rejection of stale curves or mismatched session counts.
- Android 13 emulator: all 4 instrumented tests passed. The curve UI test selects Strength, verifies 25 kg / 101 s / 5 reps for a synthetic fixture, and compares background pixels before and after resizing. Existing queue/storage tests remain green.
- Full app cold-started without Wi-Fi/mobile data. The Bluetooth panel was dragged down; the timer retained its background. The cached curve rendering was visually inspected with isolated test data.
- Production authenticated curve retrieval/import and physical BLE measurements remain unverified. Synthetic test data is isolated and never uploaded.
- Existing project-wide lint limitations listed below remain; lint was not rerun for this change.


## 0.1.4

Local validation on 2026-09-26:

- Debug APK and Android test APK built successfully with JDK 17, SDK 36 and Build Tools 36.0.0 (versionCode 6 / versionName 0.1.4).
- 20 JVM/Robolectric tests passed, including 10 offline tests: durable queue updates, acknowledgement matching, restart recovery, account binding, invalid input, corruption preservation, online/offline set isolation and reception loss during a website set.
- 18 JavaScript tests passed: 8 timer bridge tests and 10 import tests covering account mismatch, expired login, preflight read failure, import errors, missing form, concurrent submission and reconciliation after a lost acknowledgement.
- Existing force-drop detector: 440 assertions passed. WH-C06 decoder: 31 assertions passed.
- Android 13 emulator: 3 instrumented tests passed, including actual atomic storage and a Compose UI flow completing two local sets, checking the pending count, acknowledging them and dismissing the success banner. These tests use isolated app storage and do not upload fabricated training data.
- Full app launched with emulator Wi-Fi and mobile data disabled. Offline controls and the expanded graph panel rendered without a startup crash; the disconnected-scale message is expected without a BLE sensor.
- Static lint reports 12 existing errors in unchanged PitchSixService.kt (permission annotations/checks and Bluetooth status constants), plus warnings. No offline-feature lint errors were reported. Lint is not a clean project-wide gate yet.
- Production authenticated import, real BLE force data and the new version on the user's physical phone remain unverified. Import transport tests simulate the observed production form/read contracts; they do not establish backend acceptance.

## 0.1.3

- Target-based force-drop detector: 440 assertions passed, including 90% arming, targets below 3 kg, sustained overshoot, changed/invalid targets, confirmation timing, outliers, disconnect gaps and one action per rep.
- Existing WH-C06 decoder: 31 assertions passed. DOM bridge: 7 tests passed.
- Local Windows validation on 2026-09-25, based on main commit `573c799`: `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest` completed successfully using JDK 17.0.11, Android SDK 36 and Build Tools 36.0.0.
- Android JVM tests: 10 passed, zero failures/errors/skips (including 9 Robolectric tests).
- APK metadata: application ID `com.tantaluspath.powercurve`, versionCode 5 / versionName 0.1.3, minimum API 29, target API 36. APK signature verified (v2).
- Debug APK SHA-256: `910f481b2eb948ed7804c63ffd39e8ff72377df172f88b8c40be9560de59c5d3`.
- Corrected the existing instrumented package identity test to expect the current application ID. It passed on a Samsung SM-G781B running Android 13 (1 instrumented test, zero failures).
- Installed 0.1.3 on that phone after the user explicitly approved removing the differently signed 0.1.2 installation and its local data. Cold launch succeeded to the Android permission dialog; the app process remained alive and the inspected process log contained no fatal startup exception. Permission handling, signed-in training and physical sensor measurements remain unverified.
- Physical hardware acceptance remains pending.

## 0.1.2

- Force-drop detector: 174 assertions passed.
- WH-C06 decoder: 31 assertions passed, including short/missing-unit packets, explicit fallback units, recognized units and malformed lengths.
- Powercurve DOM bridge: 7 tests passed, including live Weight input changes, decimal commas, units, invalid/empty values and consecutive set targets.
- Android build: `:app:assembleDebug :app:testDebugUnitTest` completed successfully.
- Android runtime tests (Robolectric API 34): 10 passed, zero failures/errors: 3 WH-C06 advertisement/timeout/unit tests, 3 target weight tests, 3 startup regression tests and 1 existing example test.
- APK metadata: versionCode 4 / versionName 0.1.2, Android 10 minimum. APK signature verified.
- Physical WH-C06 reception and an authenticated training session on the user's phone remain unverified. See DEVICE_TEST_PLAN.md.

## 0.1.1

- Built with JDK 17, Gradle 9.1.0, Android Gradle Plugin 9.0.0, SDK 36 and Build Tools 36.0.0.
- Android unit tests: 4 passed, including 3 Robolectric tests for the startup tag-key fix.
- Debug APK versionCode 3 / versionName 0.1.1 built successfully; signature verified.

## Limits

- GitHub Actions is configured, but earlier runs were blocked before build steps by account billing/spending limits. No billing settings were changed.
- No physical BLE device acceptance tests, authenticated end-to-end training session, full device UI inspection, release signing or Play Store submission have been performed. The 0.1.3 physical-device checks cover package identity and startup to the permission dialog only. Version 0.1.4 adds the emulator checks listed above.
- Supplied APKs are debug-signed test builds. Session saving remains an explicit action in Powercurve.
