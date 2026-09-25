# Powercurve for Android

Android companion for **https://powercurve.tantaluspath.com** with native Bluetooth device support, a live force curve and automatic rep completion on a sustained force drop.

## Getting started

1. Install the APK on Android 10 or newer and allow Bluetooth permissions. Android 10/11 also requires location services for BLE discovery.
2. Sign in to Powercurve inside the app. Training setup and session saving use the Powercurve website and its backend.
3. Select a **Tindeq Progressor**, **PitchSix Force Board** or **Weiheng WH-C06** from the Bluetooth menu. Keep the sensor unloaded during the initial five-second calibration.
4. Enter the weight in Powercurve's **Weight** field. The app's target indicator, graph line and target feedback follow this field and the active set's weight. Old manual overrides and the former 20 kg default no longer replace it. Empty or invalid input means no target.
5. Start a set. The live force curve is displayed during a rep. App display units may be kg or lb; internal values are kg.
6. Configure **Settings → Advanced → End rep on force drop**, the drop percentage (10–80%) and confirmation time (100–1000 ms).
7. Tap **Save session** in Powercurve after the set. The app does not save the website session automatically.

## WH-C06 readings

The WH-C06 broadcasts measurements in BLE advertisements; selecting it does not establish a GATT measurement connection. The app now waits for a decoded measurement before showing **Connected**. No valid readings for 15 seconds changes the state to **Reconnecting** while it keeps listening.

Version 0.1.2 accepts measurement payloads from the selected scale even when its firmware uses a different manufacturer ID or omits a recognized unit code. Valid kg/lb/stone/jin codes are converted to kg. For firmware without a valid unit code, **WH-C06 fallback unit: pounds** selects the scale's unit: off = kg, on = lb. Set it to match the physical scale. This setting is independent of the app's display unit.

Reopening the screen no longer starts a duplicate measurement scan. If readings still do not arrive, open the app's logs: the first rejected packet reports manufacturer IDs and payload lengths, and successful reception is logged. Hardware verification with the user's scale remains necessary.

## Automatic rep completion

Defaults: enabled, **50% force drop**, **250 ms confirmation**. Detection arms after at least 300 ms of load at or above **90% of Target Weight**. The completion limit is **Target Weight × (1 − drop percentage)**. For a 20 kg target and 50% drop, it arms at 18 kg and ends the rep at 10 kg or below after the confirmation time. Peak force does not affect either limit. A three-sample median rejects isolated outliers; device sampling rate affects response time.

The same Powercurve Weight field controls the graph, target feedback, arming threshold and force-drop limit. There is no fixed 3 kg minimum. A missing, zero, negative or invalid target disables auto-end. Changing the target requires a fresh stable load before rearming.

Auto-end acts only on a visible, active rep. Countdown, rest, set completion, missing measurements, stale timer state and disconnections cannot trigger it. A measurement gap over 1.5 seconds requires stable loading again. Auto-end pauses in the background. Powercurve remains responsible for timing and session saving.

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
