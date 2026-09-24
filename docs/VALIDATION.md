# Validation

## 0.1.2

- Force-drop detector: 174 assertions passed.
- WH-C06 decoder: 31 assertions passed, including short/missing-unit packets, explicit fallback units, recognized units and malformed lengths.
- Powercurve DOM bridge: 7 tests passed, including live Weight input changes, decimal commas, units, invalid/empty values and consecutive set targets.
- Android build and runtime regression tests: pending for this revision.
- Physical WH-C06 reception and an authenticated training session on the user's phone remain unverified. See DEVICE_TEST_PLAN.md.

## 0.1.1

- Built with JDK 17, Gradle 9.1.0, Android Gradle Plugin 9.0.0, SDK 36 and Build Tools 36.0.0.
- Android unit tests: 4 passed, including 3 Robolectric tests for the startup tag-key fix.
- Debug APK versionCode 3 / versionName 0.1.1 built successfully; signature verified.

## Limits

- GitHub Actions is configured, but earlier runs were blocked before build steps by account billing/spending limits. No billing settings were changed.
- No physical BLE device acceptance tests, authenticated end-to-end training session, emulator/device UI inspection, release signing or Play Store submission have been performed.
- Supplied APKs are debug-signed test builds. Session saving remains an explicit action in Powercurve.
