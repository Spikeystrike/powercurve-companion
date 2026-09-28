# Powercurve for Android

Android companion for [Powercurve](https://powercurve.tantaluspath.com) with Bluetooth sensor support, a live force graph and offline training.

## Getting started

1. Install the APK on **Android 10 or newer** and allow Bluetooth permissions. Android 10/11 also requires location services for device discovery.
2. Sign in to Powercurve inside the app.
3. Select a **Tindeq Progressor**, **PitchSix Force Board** or **Weiheng WH-C06** from the Bluetooth menu. Keep the sensor unloaded during the five-second calibration.
4. Enter your target in the timer's **Weight** field. The graph and target feedback follow this weight. Display units can be kg or lb.
5. Start a set. View the live force graph and end reps manually or use automatic force-drop detection.
6. When training online, tap **Save session** after the set.

## Automatic rep completion

Configure **Settings → Advanced → End rep on force drop**:

- **Force drop:** 10–80%; default 50%.
- **Confirmation time:** 100–1000 ms; default 250 ms.

Detection activates after holding at least **90% of Target Weight for 300 ms**. A rep ends when force stays at or below **Target Weight × (1 − drop percentage)** for the confirmation time.

For a **20 kg target** and **50% drop**, hold at least **18 kg** to activate detection. The rep then ends after force stays at **10 kg or below** for 250 ms.

A valid target is required. Changing the target or reconnecting the sensor requires a fresh stable load. Automatic completion only operates during an active rep while the training screen is visible.

## Offline training

Tap the cloud-off icon beside Settings to open the local timer and saved curve even while online. Synchronization stays enabled. The shortcut is disabled during a running website set; use **Return to Powercurve** to go back when no local set is running.

Without internet, use the **Offline set timer**. Bluetooth measurements and the live force graph remain available with a connected sensor. Enable **Show Force Graph** to keep the graph panel expanded.

1. Choose gripper and side. Switching either clears the previous weight, reps and target hold; an available curve recommendation fills them for the new selection. Your personal **Force curve** is saved while signed in online and remains available offline, with weight on the horizontal axis and hold time on the vertical axis. Tap the curve to set the timer weight, target hold time and recommended reps. The selected point, axis values and guide lines update with zone selection or direct weight entry. Both guide lines stop at the point. The weight axis starts just below the lowest plotted weight and extends 5% beyond the curve’s zero crossing. For a fit with no zero crossing within the model range, it extends past the 1-second point instead. Select **Endurance**, **Strength Endurance**, **Strength**, **Power Strength** or **Power** to fill matching weight, estimated hold time and reps. Entering a weight updates target hold time and the standard reps for its calculated zone. **Match hold time** accepts **1–400 seconds** when the saved curve supports that time. The time axis always spans **0–300 seconds**.
2. Set reps, rest and countdown. **Target hold** is optional and can be edited. During the first rep only, **Target countdown** shows the remaining target time and becomes negative when exceeded.
3. End reps manually or through force-drop detection. After all planned reps, tap **Save set** to save on your phone. Until then, the save slot shows **Locked** during rest. Below it, hold **Hold to save set now** for two seconds to save completed reps early. **Discard set without saving** always requires a two-second hold and abandons the current set, including completed reps. Both hold actions show progress and remaining time; releasing early cancels.
4. Start another set whenever you are ready.

The offline graph shows the **last 60 sets per gripper and hand**, including locally saved sets waiting to sync. Older sets fade by their rank in the sequence, not by elapsed days. The newest set in each time zone uses its zone color and an outline. A set’s time and zone use its longest completed rep. Select a zone to see its latest set’s date/time, weight, hold time and calendar days ago. History remains visible even when no fitted curve is available.

The zone with the oldest last set is preselected. Untrained zones come first; ties follow Endurance-to-Power order. Locally saved sets are included, and manual edits are preserved during cache updates.

The timer starts with **Micro / Left** selected. Recovered unfinished sets keep their original gripper and hand.

Curves are stored per account, gripper and hand. Connect once to cache a fitted curve; unavailable zones need more training data. The saved timestamp is shown, and pending sets affect the curve after synchronization and online recalculation.

Saved sets and completed reps survive app restarts. An interrupted set can be resumed; an unfinished rep is not counted.

Offline weights are stored in lbs and passed directly to the backend import. Existing local kg records are migrated automatically without rounding. The selected display unit still controls input and display.

When internet returns, sets are automatically added through **Import training data** while the app is running. If the app was closed, synchronization resumes the next time it opens.

- The status banner shows how many sets are **waiting to sync** and whether sign-in is needed.
- After successful synchronization, the count and completion time remain visible until you tap **Dismiss**.
- No extra sync banner appears during ordinary online use.

Synchronization uses the account associated with the saved sets. If you trained before signing in for the first time, tap **Sync to [account]** after signing in. Failed imports stay saved for retry.

Imported sets include the date, gripper, side, weight and individual rep durations. Countdown and rest settings remain local. Raw force curves and local history stay on your phone.

The background history connection retries after a page-load failure even when no sets are waiting to upload.

Online history refreshes also remove deleted or hidden imported sets from the offline chart. Pending unsynchronized sets remain on the phone. Keep the app online and signed in for a history refresh after deleting a set (normally within about 30 seconds).

## WH-C06 setup

After selecting the scale once, the app remembers it across restarts and waits for its readings even if the scale is switched on later. Turning the scale off and on does not require restarting the app. Failed or silent scans restart after 30 seconds. Tapping the Bluetooth status requests a fresh scan when disconnected; rapid taps are spaced by at least 8 seconds. Discovery also lists WH-C06 devices advertising from a different address; select the device from the list if its address changed. Scan starts, failures and discovered devices appear in the app logs.

**Connected** appears once measurements arrive. After 15 seconds without readings, the app shows **Reconnecting** and keeps listening.

If readings use the wrong unit, set **WH-C06 fallback unit: pounds** to match the scale: off for kg, on for lb. This applies when the scale does not report a recognized unit and is independent of the app's display unit. For connection problems, check the app's logs.

## Build and tests

Use **JDK 17**, **Android SDK 36** and **Build Tools 36.0.0**.

```sh
cd android/grip_gains_companion
./gradlew :app:assembleRelease :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/release/app-release.apk`. Release builds enable R8 code optimization and resource shrinking and are not debuggable. For compatibility with existing private installations, they use the same local signing certificate as previous APKs; keep that keystore when rebuilding. On Windows, use `gradlew.bat`. To retain app data when updating, install over the existing app using the same signing key.

Additional detector, decoder and web integration tests require JDK 17 and Node.js. Run from the repository root:

```sh
bash tests/run.sh
```

See [validation results](docs/VALIDATION.md) and the [device test plan](docs/DEVICE_TEST_PLAN.md) for test coverage and remaining hardware and account checks.

## Credits and license

MIT license: [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Based on [bdrmakes/grip-gains-isotonic-companion](https://github.com/bdrmakes/grip-gains-isotonic-companion) and [jakemcc/grip_gains_companion](https://github.com/jakemcc/grip_gains_companion), with timer integration and force-drop detection adapted for Powercurve.

During touch interactions the app requests the highest display refresh rate available at the current resolution, then restores the system preference two seconds after release or when leaving the app. Android, power-saving settings and display hardware determine the actual rate.

## Reviewing offline data

Use **Review pending sets** in the waiting-to-sync banner to correct gripper, hand, weight in your selected unit (kg or lb) and completed rep durations, or delete a set after confirmation. Numbers display at most three decimal places. Uploads pause while the list is open. Sets whose upload has already started are locked locally because the server may already have received them; wait for confirmation and edit or delete those sets online.

The local timer shows when history was checked. **Update now** requests a fresh online snapshot without interrupting an active import; connection or sign-in failures show a retry message. Curve taps require the same supporting training data as other recommendations and leave timer targets unchanged when evidence is insufficient.

Bluetooth status distinguishes searching, waiting for readings and receiving readings. The receiving indicator expires after three seconds without samples; it does not imply a new measurement or change connection recovery.

The force panel stays collapsed after you swipe it down or close it with its handle. Timer changes and returning readings from the same connection do not reopen it. The first reading from a new device connection can reopen the panel.
