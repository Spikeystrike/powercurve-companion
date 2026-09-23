# Hardware-Abnahme (noch ausstehend)

Für Tindeq Progressor, PitchSix Force Board und WH-C06 jeweils auf einem echten Android-Telefon prüfen:

- Erstinstallation, BLE-Rechte erlauben/ablehnen, Bluetooth aus/ein, Scannen, Verbinden und erneutes Verbinden.
- 5 Sekunden unbelastete Kalibrierung; bekannte Last aufbringen. Angezeigte kg mit Referenzlast vergleichen. Bei WH-C06 kg/lb am Gerät umschalten: Anzeigeeinheit der App darf die interne Messung nicht verfälschen.
- In Powercurve anmelden, Timer konfigurieren, Satz starten. Countdown und Pause dürfen bei Nullkraft keine Rep beenden.
- Mindestens 1 Sekunde 20 kg halten; bei Einstellung 50 % / 250 ms auf 9 kg ablassen. Genau eine Rep endet, Powercurve startet die Pause. 12 kg müssen weiterlaufen.
- Kurzer Ausreißer, kurze Entlastung, einzelner hoher Messwert; keine verfrühte Auslösung.
- Sensor ausschalten oder außer Reichweite bringen: kein künstlicher Nullwert, kein Auto-Ende. Nach Rückkehr erst mit stabiler Belastung wieder scharfstellen.
- Gerät während einer Rep wechseln. Nächste Rep darf keinen Spitzenwert der vorherigen übernehmen.
- Manuell End rep tippen, direkt danach Messwerte senden: nächste Rep wird nicht versehentlich beendet.
- App in Hintergrund, Bildschirm sperren, zurückkehren: keine Hintergrund-Auslösung und kein nachgeholter Klick.
- Satz in Powercurve speichern; nach Neuladen die gespeicherte Session prüfen. Lokale Kraftkurvendaten sind getrennt von der Serversession.
- Rotation/Neustart, langsames Netz, Netzverlust, abgelaufene Anmeldung: Status verständlich, manuelles Training weiterhin kontrollierbar.

Automatisierte Tests validieren die deterministischen Bestandteile. Sie ersetzen weder Firmware-Kompatibilitätstests noch einen angemeldeten Ende-zu-Ende-Test auf dem produktiven Powercurve-Konto.
