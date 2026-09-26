# Powercurve for Android

Android companion for **https://powercurve.tantaluspath.com** with native Bluetooth device support, a live force curve and automatic rep completion on a sustained force drop.

## Getting started

1. Install the APK on Android 10 or newer and allow Bluetooth permissions. Android 10/11 also requires location services for BLE discovery.
2. Sign in to Powercurve inside the app. Training setup and session saving use the Powercurve website and its backend.
3. Select a **Tindeq Progressor**, **PitchSix Force Board** or **Weiheng WH-C06** from the Bluetooth menu. Keep the sensor unloaded during the initial five-second calibration.
4. Enter the weight in Powercurve's **Weight** field. The app's target indicator, graph line and target feedback follow this field and the active set's weight. Old manual overrides and the former 20 kg default no longer replace it. Empty or invalid input means no target.
5. Start a set. The live force curve is displayed during a rep. App display units may be kg or lb; internal values are kg.
6. Configure **Settings → Advanced → End rep on force drop**, the drop percentage (10–80%) and confirmation time (100–1000 ms).
7. Online, tap **Save session** in Powercurve after the set. Offline sets are saved on the phone and synchronized automatically as described below.

## Offline training (0.1.4)

When internet access is unavailable, the Timer screen offers a native **Offline set timer**. Bluetooth measurements and the native force graph do not need internet access; the graph panel stays expanded while using the offline timer (when Show Force Graph is enabled). A scale must still be connected to supply live measurements.

- Choose gripper, side, target weight, reps, rest and countdown. End each rep manually or use the existing target-based force-drop detection. Completing the planned reps saves the set automatically; **Save set now** during rest saves a shorter set. New sets can be started immediately.
- Completed reps and pending sets are written atomically to private app storage. After an app restart, an interrupted set is recovered in a paused state. Completed reps remain intact; an unfinished rep is not assigned an invented duration.
- If reception is lost during a recognized website set, its countdown, reps and rests stay on screen. Completed rep durations are checkpointed locally, and the completed set is queued for import. Reconnecting does not reload a running set.
- When internet returns while the app is running, pending sets are imported one at a time through Powercurve's **Import training data** form in a separate first-party WebView. The user's timer is not navigated away. If the app was closed, synchronization resumes on the next launch.
- A banner shows the number of sets waiting to sync and any sign-in or retry requirement. Successfully imported sets are counted with a completion time until **Dismiss** is tapped. Ordinary online use with no offline backlog or unacknowledged sync result has no additional sync banner.
- Sets are tied to the last verified Powercurve account. Switching accounts does not upload another account's queue. If a set was created before any account could be identified, choose **Sync to [account]** once after signing in.
- A set leaves the queue only after the matching saved server row is verified. A lost acknowledgement triggers verification before another submission. Network errors, expired sign-in and changed import markup retain the local set for retry.

Import uses the existing single-session JSON format: original date/time, gripper, side, weight in pounds and individual rep durations. Countdown/rest settings remain local because they are not part of that import format. Raw force samples are not uploaded. Account and session checks use the same read endpoints as the website; all writes go through the existing import form. Authenticated production import and physical BLE acceptance still require validation with a real account and scale.

## WH-C06 readings

The WH-C06 broadcasts measurements in BLE advertisements; selecting it does not establish a GATT measurement connection. The app now waits for a decoded measurement before showing **Connected**. No valid readings for 15 seconds changes the state to **Reconnecting** while it keeps listening.

Version 0.1.2 accepts measurement payloads from the selected scale even when its firmware uses a different manufacturer ID or omits a recognized unit code. Valid kg/lb/stone/jin codes are converted to kg. For firmware without a valid unit code, **WH-C06 fallback unit: pounds** selects the scale's unit: off = kg, on = lb. Set it to match the physical scale. This setting is independent of the app's display unit.

Reopening the screen no longer starts a duplicate measurement scan. If readings still do not arrive, open the app's logs: the first rejected packet reports manufacturer IDs and payload lengths, and successful reception is logged. Hardware verification with the user's scale remains necessary.

## Automatic rep completion

Defaults: enabled, **50% force drop**, **250 ms confirmation**. Detection arms after at least 300 ms of load at or above **90% of Target Weight**. The completion limit is **Target Weight × (1 − drop percentage)**. For a 20 kg target and 50% drop, it arms at 18 kg and ends the rep at 10 kg or below after the confirmation time. Peak force does not affect either limit. A three-sample median rejects isolated outliers; device sampling rate affects response time.

The same Powercurve Weight field controls the graph, target feedback, arming threshold and force-drop limit. There is no fixed 3 kg minimum. A missing, zero, negative or invalid target disables auto-end. Changing the target requires a fresh stable load before rearming.

Auto-end acts only on a visible, active rep. Countdown, rest, set completion, missing measurements, stale timer state and disconnections cannot trigger it. A measurement gap over 1.5 seconds requires stable loading again. Auto-end pauses in the background. Powercurve remains responsible for online timing and session saving; the native offline timer handles locally queued sets.

## Backend and data

The embedded WebView loads Powercurve. External links open in the browser. The native message bridge is restricted to the Powercurve origin and main frame. It does not read passwords or call undocumented write APIs.

On a force drop, the integration clicks the existing **End rep** button at most once per rep. A rep key prevents delayed actions from affecting the next rep. Unrecognized timer states disable the action and appear in the status line.

**Raw force curves are not uploaded to the server.** Local history stays on the phone; Powercurve saves the values supported by its website. Uploading complete force curves would require a coordinated backend extension.

## Build and tests

Use JDK 17, Android SDK 36 and Build Tools 36.0.0. Project directory: `android/grip_gains_companion`. Application ID: `com.tantaluspath.powercurve`.

```sh
cd android/grip_gains_companion
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Debug APKs are for testing; use a securely retained release key for long-term updates or Play Store distribution. The GitHub Actions workflow also builds an APK when account limits allow it.

Standalone force-drop, WH-C06 decoder and DOM bridge tests require JDK 17 and Node.js:

```sh
bash tests/run.sh
```

## Credits and license

MIT license: see [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

- Android foundation: [bdrmakes/grip-gains-isotonic-companion](https://github.com/bdrmakes/grip-gains-isotonic-companion), commit `467ac80a29e21a0cb616e37613ea4cbeb3f48603`.
- Rep failure and WH-C06 reference: [jakemcc/grip_gains_companion](https://github.com/jakemcc/grip_gains_companion), commit `fad9bddfd7262f9541851441fbdb845c42581c22`.

The Bluetooth drivers derive from these projects. The timer bridge and percentage-based force-drop detection are Powercurve adaptations. See the [device test plan](docs/DEVICE_TEST_PLAN.md) for remaining hardware validation.
