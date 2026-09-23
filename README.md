# Powercurve für Android

Android-Companion für **https://powercurve.tantaluspath.com** mit nativen Bluetooth-Gerätetreibern, Live-Kraftkurve und automatischem Ende einer Wiederholung bei Kraftabfall.

## Verwendung

1. APK installieren (Android 10 oder neuer) und Bluetooth-Berechtigungen erlauben. Bei Android 10/11 muss für BLE-Suche zusätzlich der Standortdienst eingeschaltet sein.
2. Im eingebetteten Powercurve anmelden. Anmeldung, Trainingseinstellungen und Speichern der Sessions nutzen die echte Powercurve-Website und deren Backend.
3. Über den Bluetooth-Chip **Tindeq Progressor**, **PitchSix Force Board** oder **Weiheng WH-C06** verbinden. Während der anfänglichen 5-s-Kalibrierung den Sensor entlasten.
4. Einen Satz im Powercurve-Timer starten. Während einer Rep wird die Kraftkurve aufgeklappt. Gelbe Linie: Powercurve-Zielgewicht. Anzeige in kg oder lb; intern immer kg.
5. Unter Settings → Advanced die Automatik, den prozentualen Kraftabfall (10–80 %) und die Bestätigungszeit (100–1000 ms) einstellen.
6. Nach dem Satz in Powercurve **Save session** drücken. Die App klickt diesen Button nicht automatisch.

Standard: Automatik an, **50 % Kraftabfall**, **250 ms Bestätigung**. Nach mindestens 300 ms Belastung ab 3 kg wird die Erkennung aktiv. Referenz ist der gefilterte Spitzenwert der aktuellen Rep, nicht das eingegebene Zielgewicht. Bei 20 kg Spitzenwert beendet eine mindestens 250 ms anhaltende Kraft von höchstens 10 kg die Rep. Ein Median aus drei Messwerten unterdrückt isolierte Spitzen/Ausreißer. Die Messrate des Geräts beeinflusst die tatsächliche Reaktionszeit.

Die Automatik wirkt nur auf eine aktive, sichtbare Wiederholung. Countdown, Pause, Satzende, fehlende Messwerte, veralteter Timerstatus und Verbindungsabbrüche lösen kein Rep-Ende aus. Nach einer Messlücke über 1,5 s muss erneut stabile Belastung anliegen. Im Hintergrund ist die Automatik pausiert. Der Powercurve-Timer bleibt für Zeitführung und Session-Speicherung verantwortlich.

## Backend und Daten

Die App lädt ausschließlich Powercurve in der eingebetteten WebView. Externe Links öffnen im Browser. Die native Nachrichtenbrücke ist auf den Powercurve-Ursprung und das Hauptfenster beschränkt. Es werden keine Passwörter ausgelesen und keine undokumentierten Schreib-APIs angesprochen.

Die Integration erkennt die bestehenden Powercurve-Timerzustände und betätigt bei Kraftabfall dessen **End rep**-Button. Damit gelten die vorhandenen Regeln zur Zeitmessung und Speicherung. Pro Rep ist nur ein automatischer Klick möglich; ein Schlüssel schützt vor verspäteten Aktionen auf der nächsten Rep. Falls sich die Web-Oberfläche ändert, wird nicht auf beliebige ähnliche Buttons ausgewichen. Die Statuszeile zeigt eine fehlende Anbindung an.

**Roh-Kraftkurven werden nicht zum Server hochgeladen.** Die übernommenen lokalen Verlaufsfunktionen speichern Daten auf dem Telefon; die normale Powercurve-Session speichert die von der Website vorgesehenen Werte. Ein serverseitiges Speichern kompletter Kraftkurven benötigt eine abgestimmte Backend-Erweiterung.

## Build

Android Studio mit JDK 17, Android SDK 36 und Build Tools 36.0.0. Projektordner: `android/grip_gains_companion` (interner Paketname aus der MIT-Basis beibehalten). Eigenständige Installations-ID: `com.tantaluspath.powercurve`.

```sh
cd android/grip_gains_companion
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Debug-APK zum Testen; für dauerhafte Updates/Play Store einen eigenen, sicher verwahrten Release-Schlüssel verwenden. GitHub Actions baut die Debug-APK und stellt sie als Artefakt bereit.

Tests für Kraftabfall, WH-C06-Einheiten und DOM-Anbindung ohne Android-Gerät (JDK 17 + Node.js):

```sh
bash tests/run.sh
```

## Grundlage / Lizenz

MIT-Lizenz, siehe [LICENSE](LICENSE) und [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

- Android-Grundlage: [bdrmakes/grip-gains-isotonic-companion](https://github.com/bdrmakes/grip-gains-isotonic-companion), Commit `467ac80a29e21a0cb616e37613ea4cbeb3f48603`.
- Referenz für aktive-Rep-/Kraftabfall-Verhalten und WH-C06: [jakemcc/grip_gains_companion](https://github.com/jakemcc/grip_gains_companion), Commit `fad9bddfd7262f9541851441fbdb845c42581c22`.

Die Gerätetreiber stammen aus diesen Projekten. Die neue Timer-Brücke und prozentuale Abfallerkennung sind Anpassungen für Powercurve. Eine praktische Abnahme mit allen drei Geräten steht aus; siehe [Testplan](docs/DEVICE_TEST_PLAN.md).
