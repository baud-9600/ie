# 家 ie — Zuhause

Eine kleine Android-App für das [Mudita Kompakt](https://mudita.com/products/kompakt/), mit der
sich Geräte in einer Home-Assistant-Instanz schalten und Medienplayer steuern lassen. Gebaut mit
Muditas [MMD](https://github.com/mudita/MMD)-Designsystem für E Ink.

Die App heißt auf Deutsch und Japanisch **Zuhause**, auf Englisch **Home**. Der Projektname ist
*ie* (家), das japanische Wort für Haus und Zuhause; die Application ID ist
`de.asdfghasdfgh.ie`.

## Was sie kann

- **Schalter, Lichter, Lüfter und Helfer** aus Home Assistant anzeigen und mit einem Tipp auf die
  ganze Zeile an- oder ausschalten.
- **Medienplayer** steuern: leiser, lauter, Play, Pause und Stopp.
- **Auswählen, was erscheint**: Unter Einstellungen → Geräte auswählen lässt sich für jedes
  Gerät und jeden Medienplayer einzeln entscheiden, ob es auf der Hauptseite steht. Bis zur
  ersten Auswahl wird alles angezeigt; die Auswahl bleibt auf dem Telefon gespeichert.
- **Jede Home-Assistant-Adresse**: lokal (`http://homeassistant.local:8123`), per IP, hinter einem
  Reverse-Proxy mit Pfad oder von außen über HTTPS. Die Adresse wird beim Einrichten frei
  eingetragen.
- **Optionale Server-Adresse**: Löst der Name vom Telefon aus auf einen anderen Server auf als im
  lokalen Netz, kann dort die lokale IP eingetragen werden, zum Beispiel `192.168.1.10`. Die
  Verbindung geht dann dorthin, das TLS-Zertifikat wird aber weiterhin auf den Namen oben geprüft.
- **Aktuelle Zustände**, solange die App auf dem Bildschirm ist: alle fünf Sekunden fragt sie die
  Zustände neu ab. Änderungen, die anderswo ausgelöst werden, erscheinen so von selbst.
- **Englisch und Deutsch**, je nach Sprache des Telefons.

Die App zeigt bewusst nur Geräte und keine Dashboards, Automationen oder Sensoren. Das hält die
Liste auf dem E-Ink-Bildschirm kurz und die Bedienung eindeutig.

## Einrichten

1. In Home Assistant ein langlebiges Zugangstoken erstellen:
   **Profil → Sicherheit → Langlebige Zugangstokens → Token erstellen**.
2. Die App öffnen, die Adresse der Instanz eintragen, zum Beispiel
   `http://homeassistant.local:8123` oder `https://meine-instanz.ui.nabu.casa`, und den Token
   einfügen. Bei Bedarf unter **Server-Adresse** die lokale IP eintragen, falls der Name vom
   Telefon aus woandershin zeigt.
3. **Verbinden** drücken. Die App prüft die Verbindung, bevor sie Adresse und Token behält.

Ein eigenes Home-Assistant-Benutzerkonto für die App ist sinnvoll: Der Token hat genau die Rechte
dieses Kontos. Für diese App genügen Rechte auf die Geräte, die geschaltet werden sollen.

## Unterstützte Geräte

| Home-Assistant-Domain | Anzeige | Bedienung |
|---|---|---|
| `light` | Licht | An/Aus |
| `switch` | Schalter | An/Aus |
| `input_boolean` | Helfer | An/Aus |
| `fan` | Lüfter | An/Aus |
| `media_player` | Medienplayer | Leiser, Lauter, Play, Pause, Stopp |

Andere Domains werden nicht angezeigt.

## Netzwerk und Datenschutz

Die App spricht ausschließlich mit der eingetragenen Home-Assistant-Adresse, über die offizielle
REST-API. Es gibt keine Konten, keine Analyse und keine Werbung.

Der Token liegt **verschlüsselt** im privaten Speicher der App: Ein AES-GCM-Schlüssel wird im
Android Keystore erzeugt und verlässt ihn nie; in den Preferences steht nur der Geheimtext mit
seinem zufälligen IV. Ein Backup der Preferences ist damit kein Backup des Tokens. Wer den Token
selbst braucht, kann ihn in Home Assistant neu erzeugen.

Die App fordert **nur die Internet-Berechtigung** an. Es gibt keinen Standort, keine Kamera,
kein Mikrofon, keine Kontakte und keinen Zugriff auf Dateien. `android:allowBackup="false"`
verhindert außerdem, dass die Preferences in ein Systembackup wandern.

Für eine lokale Instanz ohne TLS ist `android:usesCleartextTraffic="true"` gesetzt. Wer nur HTTPS
verwendet, kann das in `AndroidManifest.xml` entfernen. Selbstsignierte HTTPS-Zertifikate werden
nicht akzeptiert.

## Bauen

Vorausgesetzt werden JDK 17 oder neuer und das Android SDK mit API 36. MMD 1.0.2 liegt als lokale
Veröffentlichung in `~/.m2` (aus dem MMD-Checkout); der Build holt es über `mavenLocal()`.

```
./gradlew assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

```
./gradlew testDebugUnitTest
```

prüft Adressen, das Einlesen der Zustände, die optionale Server-Adresse und die Auswahl der
unterstützten Geräte.

Installieren auf einem angeschlossenen Telefon:

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Technik

- Kotlin, Jetpack Compose und Muditas MMD-Komponenten (`ThemeMMD`, `TopAppBarMMD`, `TextMMD`,
  `SwitchMMD`, `TextFieldMMD`, `HouseButton`).
- Home Assistant über HTTP/REST (`/api/`, `/api/states`, `/api/services/...`) mit OkHttp.
- Ein `ViewModel` hält die Geräteliste; abgefragt wird nur, solange die App im Vordergrund ist.
- Der Home-Assistant-Token wird mit AES-GCM und einem Schlüssel aus dem Android Keystore
  verschlüsselt; ausgewählte Entitäten liegen als Menge von Entity-IDs in den Preferences.
- Kein WebSocket: Für eine kurze Geräteliste und fünf Sekunden Aktualisierungsabstand ist REST
  einfacher und auf E Ink ruhiger.

## Lizenz

GPL-3.0-only. Siehe [LICENSE](LICENSE).
