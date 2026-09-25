# Validation

## 0.1.3

- Target-based force-drop detector: 440 assertions passed, including 90% arming, targets below 3 kg, sustained overshoot, changed/invalid targets, confirmation timing, outliers, disconnect gaps and one action per rep.
- Existing WH-C06 decoder: 31 assertions passed. DOM bridge: 7 tests passed.
- Android build and runtime tests: pending for this revision.
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
- No physical BLE device acceptance tests, authenticated end-to-end training session, emulator/device UI inspection, release signing or Play Store submission have been performed.
- Supplied APKs are debug-signed test builds. Session saving remains an explicit action in Powercurve.
