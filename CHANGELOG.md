# Änderungen

## 0.1.1

- Startabsturz in der WebView behoben: `View.setTag` verwendet nun eine eigene App-Ressourcen-ID statt der reservierten `android.R.id.custom`.
- Android-Laufzeittest reproduziert die bisherige Ausnahme und prüft die neue Markierung einschließlich mehrfacher Verwendung und getrennter Views.
- Versionscode auf 3 erhöht.

Die Korrektur adressiert einen bestätigten Fehler im Startpfad. Die genaue Ursache auf dem gemeldeten Telefon kann ohne dessen Absturzprotokoll nicht abschließend zugeordnet werden.
