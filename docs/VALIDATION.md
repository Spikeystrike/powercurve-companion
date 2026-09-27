# Validation

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
