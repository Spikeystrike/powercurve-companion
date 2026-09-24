# Validation

## 0.1.1 startup fix

- Robolectric Android API 34 regression tests: 3 passed. Reproduced the `IllegalArgumentException` with `android.R.id.custom`; verified the app resource ID works, remains idempotent, and is scoped to each View.
- Android debug APK versionCode 3 / versionName 0.1.1 built successfully; APK signature verified.
- Full Gradle unit test run: 4 tests passed, zero failures/errors (3 regression tests plus existing example test).
- The reported phone crash has no stack trace, so matching this confirmed startup bug to that specific incident remains unverified until the user tests 0.1.1.

## General checks

- Android build: JDK 17, Gradle 9.1.0, Android Gradle Plugin 9.0.0, SDK 36, Build Tools 36.0.0.
- `:app:assembleDebug :app:testDebugUnitTest`: successful locally.
- Sample-driven force-drop detector: 174 assertions passed (stable loading, sustained drop, percentage setting, spikes, once-per-rep, rest, gaps, invalid data).
- WH-C06 decoder: 5 assertions passed (payload size, kg, lb, negative readings, unknown unit).
- Powercurve DOM adapter: 5 Node.js tests passed (origin restriction, idempotent installation, stale/duplicate rep actions, inactive/background/disabled states, injection before document body exists).
- GitHub Actions: workflow installed, but the first run was blocked before any build steps by GitHub account billing/spending limits. No billing settings were changed.
- Not performed: physical BLE device tests, an authenticated full training session against the production backend, emulator/device UI inspection, release signing and Play Store submission.

The supplied APK is debug-signed for installation/testing, not a production release. Session saving remains an explicit user action in Powercurve. Hardware test checklist: `DEVICE_TEST_PLAN.md`.
