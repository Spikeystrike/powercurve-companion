# Changelog

## 0.1.3

- Base automatic rep completion on Target Weight instead of peak force.
- Arm after 300 ms at or above 90% of Target Weight, replacing the fixed 3 kg minimum.
- Require a valid positive target; rearm after target changes or measurement gaps.
- Retain the configured drop percentage, confirmation time, outlier filter and active-rep guards.
- Update English settings, documentation and regression tests. Version code 5.

## 0.1.2

- WH-C06: accept measurement payloads with varying manufacturer IDs and missing/unknown unit codes; use an explicit fallback scale unit independent of display units.
- Show Connected only after a valid WH-C06 measurement; handle batched advertisements and avoid duplicate scans when reopening a screen.
- Add first-packet diagnostics and a no-readings timeout to the in-app logs.
- Read the target from Powercurve's Weight input during setup and the active set summary while training. Clear invalid/missing targets; remove stale override and fixed 20 kg fallback.
- Translate app messages and repository documentation into English.
- Increase version code to 4.

Hardware reception still needs confirmation on the user's WH-C06.

## 0.1.1

- Fix an immediate WebView startup exception by replacing the reserved `android.R.id.custom` tag key with an app resource ID.
- Add Android runtime regression tests for the previous exception, repeated installation and separate Views.
- Increase version code to 3.

The fix addresses a confirmed startup-path bug. The original phone incident did not include a stack trace.
