# DynamicDroid

Dynamic Island / iOS-Live-Activities für Android: laufende Turn-by-Turn-Navigation als **Live Update** in
Statusleiste und auf dem Sperrbildschirm (Android 16), mit Fallback für ältere Versionen und optionaler
Dynamic-Island-Pille um den Kamera-Cutout.

> **Status:** Die Logik ist per Unit-Tests abgesichert (`./gradlew testDebugUnitTest`) und die App baut als Debug-APK.
> Die Darstellung auf einem echten Gerät bzw. Emulator (Chip, Sperrbildschirm, Overlay) wurde **nicht** geprüft –
> siehe [Testanleitung](#testanleitung) und [Bekannte Einschränkungen](#bekannte-einschränkungen).

## Was ist drin

Das Repo war vorher leer (nur README/LICENSE). Es gibt **keine Routing-Engine**: Routen kommen aus einer
synthetischen Demo-Route (`DemoRoute`) und werden entweder simuliert (`MockRouteSimulator`) oder per GPS verfolgt
(`LocationRouteTracker`). Eine echte Routenberechnung kann später über `ActiveRoute` angebunden werden.

## Architektur

```
 Fortschrittsquelle                 Single Source of Truth                 Anzeige
 ┌────────────────────┐             ┌──────────────────────┐   ┌─ LiveUpdateContentMapper ─┐
 │ MockRouteSimulator │──┐          │ NavigationState      │   │  State → LiveUpdateContent │
 │ LocationRouteTracker│─┴─ update ▶│ StateFlow<UiState>   │──▶│                            │
 └────────────────────┘             └──────────────────────┘   └─────┬──────────────┬──────┘
                                                                      │              │
                                                         UpdateThrottler       OverlayPresenter
                                                                      │       (IslandOverlayPresenter,
                                                           LiveUpdatePresenter  Compose-Pille)
                                                       (ProgressStyle / RemoteViews)
                                                                      │
                                                          NavigationService (FGS "location")
```

| Paket | Inhalt |
|---|---|
| `model` | `ActiveRoute`, `ManeuverStep`, `RouteProgress`, `ManeuverType`, `LatLng` |
| `state` | `NavigationState` (StateFlow, Idle/Active) |
| `routing` | `RouteProgressCalculator`, `RouteGeometry` (Snapping), `DemoRoute`, `MockRouteSimulator`, `LocationRouteTracker` |
| `liveupdate` | `LiveUpdateContentMapper` (rein, testbar), `Formatters`, `LiveUpdatePresenter`, `LiveUpdateCapabilities` |
| `update` | `UpdateThrottler` (rein, testbar) |
| `service` | `NavigationService` (Foreground Service, Typ `location`) |
| `overlay` | `OverlayPresenter`, `IslandOverlayPresenter`, `IslandPill` (Compose, Spring-Animation) |

**Update-Drosselung.** Der Mapper rundet bewusst (Distanz in 10-/50-m-Schritten, Restzeit in Minuten, ETA auf die
Minute, Fortschrittsleiste in 0,5-%-Schritten). Ein GPS-Fix, der nichts Sichtbares ändert, erzeugt dadurch einen
identischen `LiveUpdateContent` und wird übersprungen. Neue Abbiegung, Ankunft und Off-Route werden sofort
gepostet; reine Distanz-/ETA-Änderungen höchstens alle 2 s. Wischt der Nutzer die Benachrichtigung weg, wird sie
nicht erneut gepostet (Plattformvorgabe für Live Updates).

## Unterstützte Android-Versionen

- `minSdk 26`, `targetSdk 36`, `compileSdk 36.1`.
- **Android 16+ (API 36):** `Notification.ProgressStyle` mit Segmenten/Punkten, `setShortCriticalText`,
  Promotion-Anforderung. Auf **API 36.1** (Android 16 QPR2) über `setRequestPromotedOngoing(true)`; auf 36.0 wird
  derselbe Schlüssel (`EXTRA_REQUEST_PROMOTED_ONGOING`) direkt als Extra gesetzt.
- **Android 8–15:** klassische ongoing Notification, `CATEGORY_NAVIGATION`, `DecoratedCustomViewStyle` mit
  `RemoteViews`. Die Auswahl erfolgt per `Build.VERSION.SDK_INT` (`LiveUpdateCapabilities`), nicht per Gerätename.

### Was ich an der API geprüft habe (und wo sie von der Doku abweicht)

Gegen die `android.jar` von API 36 und 36.1 geprüft (`javap`), nicht geraten:

- `Notification.Builder#setRequestPromotedOngoing`, `isRequestPromotedOngoing`, `EXTRA_REQUEST_PROMOTED_ONGOING`
  und `Manifest.permission.POST_PROMOTED_NOTIFICATIONS` gibt es **erst im SDK 36.1**, nicht in `android-36`.
  Darum `compileSdk 36.1` (AGP 8.13.2; `compileSdk { version = release(36) { minorApiLevel = 1 } }`).
- `ProgressStyle` (`Segment`, `Point`, `setStyledByProgress`, `setProgressTrackerIcon`), `setShortCriticalText`,
  `canPostPromotedNotifications()`, `FLAG_PROMOTED_ONGOING` sind in 36.0 vorhanden.
- `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` steht in der Doku, ist aber in **keinem** der beiden SDKs
  vorhanden. Die App öffnet stattdessen die App-Benachrichtigungseinstellungen (`ACTION_APP_NOTIFICATION_SETTINGS`);
  dort liegt der Schalter für Live-Updates.
- Voraussetzungen laut Doku, die der Code erfüllt: `ProgressStyle`, ongoing, Titel gesetzt, kein `RemoteViews`,
  nicht colorized, Channel nicht `IMPORTANCE_MIN`.

## Berechtigungen und Degradierung

| Berechtigung | Zweck | Fehlt sie … |
|---|---|---|
| `ACCESS_FINE/COARSE_LOCATION` (Runtime) | Android 14+ verlangt sie für einen `location`-Foreground-Service | Start ist deaktiviert (gilt auch für die Simulation) |
| `POST_NOTIFICATIONS` (Runtime, 13+) | Benachrichtigung sichtbar | Navigation läuft, Anzeige bleibt unsichtbar; App zeigt Hinweis |
| Live Updates erlaubt (Systemschalter) | Promotion in Chip/Sperrbildschirm | normale `ProgressStyle`-Benachrichtigung ohne Promotion |
| `SYSTEM_ALERT_WINDOW` (Spezialzugriff) | nur für die Pille | Pille bleibt aus; Benachrichtigung unberührt |

**Hintergrund-Standort (`ACCESS_BACKGROUND_LOCATION`) wird bewusst nicht angefordert.** Ein Foreground Service, der
aus der sichtbaren App gestartet wird, behält den Standortzugriff auch bei gesperrtem Bildschirm und in anderen
Apps (while-in-use). Die Berechtigung wäre nur nötig, wenn die Navigation aus dem Hintergrund gestartet würde,
und bringt zusätzlichen Prüfaufwand im Play Store. Der Start erfolgt deshalb nur aus der Activity.

## Dynamic-Island-Pille (optional, Feature-Flag)

Schalter in der App („Dynamic-Island-Pille"), standardmäßig **aus**.

**Permission-Flow:** Schalter an → Zeile „Über anderen Apps anzeigen" → Button öffnet die Systemseite
(`ACTION_MANAGE_OVERLAY_PERMISSION`) → Nutzer erlaubt → Rückkehr in die App (Status wird bei `ON_RESUME` neu gelesen).
Ohne Erlaubnis startet die Navigation trotzdem, nur ohne Pille.

**Verhalten:** Position und Breite stammen aus `DisplayCutout` (Geräte ohne Cutout: mittig unter der Statusleiste).
Die Pille zeigt links das Abbiege-Icon, rechts den Chip-Text und klappt bei jeder neuen Abbiegung 4 s lang per
Spring-Animation zu einer Karte auf. Kurzer Tap öffnet die App, langer Tap zeigt „App öffnen" / „Navigation beenden".

**Grenzen:**
- Anwendungs-Overlays erscheinen **nie über dem Sperrbildschirm**. Dort zeigt nur die Live-Update-Benachrichtigung etwas.
- Solange die eigene App im Vordergrund ist, wird die Pille ausgeblendet.
- Manche Apps/Systemoberflächen (z. B. Vollbild-Videos, einige Sicherheitsdialoge) verdecken Overlays.
- Das Fenster ist `WRAP_CONTENT`; die Größen-Animation kann auf manchen Geräten leicht ruckeln.

## Akku, Doze und Hersteller

- Der Foreground Service (Typ `location`) ist von Doze ausgenommen, solange er läuft. Er wird mit `START_NOT_STICKY`
  gestartet: Die Route liegt nur im Speicher, ein Neustart durch das System hätte nichts anzuzeigen.
- Samsung, Xiaomi/HyperOS, Huawei, Oppo/OnePlus u. a. beenden Foreground Services teils trotzdem aggressiv (Akku-
  Optimierung, „Schlafende Apps", Autostart). Die App verlinkt die Akku-Optimierungsliste; zusätzlich muss man dort
  die App auf „Nicht optimieren"/„Uneingeschränkt" stellen und auf Xiaomi „Autostart" erlauben.
- **Manuell zu testen:** auf einem Samsung- und einem Xiaomi-Gerät 20+ Minuten navigieren, Bildschirm aus, andere App
  im Vordergrund, einmal mit und einmal ohne Akku-Ausnahme. Prüfen, ob der Service lebt und die Updates weiterlaufen.

## Testanleitung

**Unit-Tests (JVM, laufen überall):** `./gradlew testDebugUnitTest`
Abgedeckt: Mapper (State → Inhalt, Chip-Text, Segmente/Punkte, Ankunft/Off-Route), `UpdateThrottler`,
Fortschrittsberechnung, Geometrie/Snapping, Simulator mit virtueller Zeit.
Nicht abgedeckt: das tatsächliche Bauen der `Notification` (benötigt Android-Framework) und die Compose-Pille.

**Emulator (Android 16, Image API 36.1 empfohlen):**
1. `./gradlew installDebug`, App starten, Standort- und Benachrichtigungsberechtigung erteilen.
2. „Einrichten" bei „Live-Updates" → in den Benachrichtigungseinstellungen den Live-Updates-Schalter aktivieren.
3. Geschwindigkeit ×5 oder ×20 wählen, „Mock-Route starten". Die Demo-Route (~3,8 km) hat 6 Abbiegungen inkl.
   Kreisverkehr und Ankunft. Dauer: ×1 ≈ 4,5 Min, ×5 ≈ 55 s, ×20 ≈ 14 s.
4. Statusleiste/Benachrichtigungsschublade prüfen, dann Display sperren (Power-Taste) und den Sperrbildschirm prüfen.
5. Auf Emulator mit Android < 16: Es erscheint die klassische Benachrichtigung (Fallback-Layout).
6. `adb shell dumpsys notification --noredact | grep -i -A3 promoted` hilft zu prüfen, ob die Benachrichtigung als
   promoted eingestuft wurde (Ausgabeformat kann je Build abweichen).

**Nicht auf dem Emulator verlässlich testbar (echtes Gerät mit Android 16 nötig):** Darstellung des Status-Chips,
Live Update auf dem Sperrbildschirm, Cutout-Geometrie echter Geräte (Kamera-Loch, Notch), Hersteller-Kill-Verhalten.
Ich konnte auf der Entwicklungsumgebung weder Emulator noch Gerät starten; diese Teile sind ungetestet.

**Echtes Gerät:** zusätzlich „GPS-Test: Demo-Route ab aktuellem Standort": Die Demo-Route wird beim ersten GPS-Fix
am Standort verankert (erste Etappe nach Norden). Wer ihr folgt, sieht echten Fortschritt, Abbiegungen und
„Abseits der Route" (> 50 m daneben). Zum Test der Pille Schalter einschalten, Overlay-Recht erteilen, Navigation
starten und zur Home-Ansicht oder in eine andere App wechseln.

## Bekannte Einschränkungen

- Keine Routenberechnung, kein Re-Routing, keine Kartenansicht: Off-Route wird nur angezeigt.
- Keine Sprachausgabe. Die optionale „Stumm"-Aktion wurde deshalb weggelassen statt als leere Funktion eingebaut.
- Distanzen/Zeiten nur metrisch; die Einheit „Min"/„h" ist nicht lokalisiert.
- Das Abbiege-Icon für Kreisverkehr/Wenden ist eine einfache Vektorgrafik, kein ausdifferenziertes Set.
- Auf 36.0-Geräten wird die Promotion über den Extra-Schlüssel angefordert; ob jedes 36.0-Build ihn auswertet, ist
  ungeprüft.
- Nach Prozess-Tod ist die Route weg (nur im Speicher).
