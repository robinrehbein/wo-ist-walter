# Wuselburg – Wo steckt Fips? (Android-Prototyp)

Native Android-Umsetzung des Wimmelbild-Suchspiels aus dem Web-Prototyp (`../index.html`, `../game.js`).
Kotlin, Jetpack Compose und Canvas-Zeichnung, **kein WebView, keine Web-Assets**. Alle Figuren und Szenen
werden zur Laufzeit aus einem Seed gezeichnet. Enthalten sind vier Level: *Marktplatz*, *Park-Wirbel*,
*Rushhour* und der Detektiv-Fall *Der Kuchen-Fall*.

- Paketname: `de.wuselburg.game`, minSdk 26 (Android 8.0), targetSdk/compileSdk 35
- Läuft auf Handy und Tablet, im Hoch- und Querformat
- Steuerung: ein Finger verschiebt, zwei Finger zoomen, Tippen markiert einen Verdacht

## Bauen

Voraussetzungen: JDK 17 oder 21 und das Android SDK (Plattform 35, Build-Tools 35.0.0).
Der Pfad zum SDK steht in `local.properties` (`sdk.dir=...`) oder in der Umgebungsvariable `ANDROID_HOME`;
Android Studio legt die Datei beim Öffnen des Ordners `android/` automatisch an.

```sh
cd android
./gradlew assembleDebug          # Ergebnis: app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:test :app:testDebugUnitTest   # Tests
```

Eine fertig gebaute APK liegt unter `../dist/wuselburg-debug.apk`.

## Installieren und testen

### Auf dem Handy
1. `wuselburg-debug.apk` aufs Handy laden (Download, Mail, Cloud-Ordner oder per USB-Kabel).
2. Datei in der Dateien-App öffnen. Android fragt nach der Erlaubnis, Apps aus dieser Quelle zu installieren
   (Einstellungen > Apps > Spezieller App-Zugriff > Unbekannte Apps installieren). Erlauben und installieren.
3. Falls Play Protect warnt: "Trotzdem installieren" wählen (die APK ist nur mit einem Debug-Schlüssel signiert).

Alternativ per Kabel: Entwickleroptionen und USB-Debugging aktivieren, dann

```sh
adb install -r dist/wuselburg-debug.apk
```

### Auf dem Tablet
Genauso wie auf dem Handy. Das Layout passt sich der Breite an; bitte beide Ausrichtungen ausprobieren
und beim Drehen prüfen, dass Spielstand und Kameraposition erhalten bleiben.

### Im Emulator
In Android Studio den Ordner `android/` öffnen, unter Device Manager ein virtuelles Gerät (Handy oder Tablet,
API 26 oder höher) anlegen und die Konfiguration `app` starten. Per Kommandozeile:
`adb install -r dist/wuselburg-debug.apk` bei laufendem Emulator.

## Bekannte Einschränkungen des Prototyps

- Nur vier Level, kein Ton, keine Musik
- Debug-Build: nicht optimiert, nur Debug-Signatur, nicht für den Play Store gedacht
- Keine Cloud-Speicherung, der Fortschritt (Sterne) liegt nur lokal auf dem Gerät
- Optik ist an den Web-Prototyp angelehnt; kleine Abweichungen bei Strichstärken und Details sind möglich
- Auf sehr schwachen Geräten kann das Zoomen der großen Welt (2180 x 1540) ruckeln
- Nicht auf allen Geräteklassen getestet (insbesondere faltbare Geräte)

## Aufbau

```
android/
  core/   reines Kotlin/JVM (kein Android): Datenmodell, Level, Szenengenerator, Spielregeln, Tests
  app/    Android-App: Zeichnen, Kamera, Oberfläche, Spielablauf
```

### :core (`de.wuselburg.core`)
- `Levels.all`: Liste der `LevelDef` (Seed, Menge an Figuren, Doppelgänger, Versteck, Intro)
- `SceneBuilder.build(level)`: erzeugt deterministisch eine `Scene` (Blöcke, Krümel, Figuren, Schritte)
  – gleicher Seed ergibt gleiche Szene, wie im Web-Prototyp
- `Scene.drawables` ist nach `y` aufsteigend sortiert (Painter-Reihenfolge); `x,y` ist der Fußpunkt
- `Rules.hitTest(hit, x, y, tol)` und `Rules.stars(hints, misses)` (1 Stern fürs Lösen, +1 ohne Tipp, +1 mit höchstens 2 Fehlversuchen)
- Weltgröße `WORLD_W = 2180`, `WORLD_H = 1540`; Farben sind ARGB-Ints

### :app (`de.wuselburg`)
- `render/Renderer.kt`: `recordScenePicture(scene)` zeichnet die ganze Welt einmal pro Level in ein
  `android.graphics.Picture` (Weltkoordinaten); `FigureIcon` für Icons in der Oberfläche
- `ui/CameraState.kt`: Zoom und Verschiebung (`zoomAt`, `panBy`, `flyTo`, `screenToWorld`)
- `ui/WorldView.kt`: Compose-Ansicht mit Gesten; `WorldEffect` (Found, Miss, Hint) für Animationen,
  Tippen liefert Weltkoordinaten über `onTapWorld`
- `game/GameViewModel.kt`, `ui/MenuScreen.kt`, `ui/GameScreen.kt`, `MainActivity.kt`: Spielablauf, Menü, Spielansicht

Der Vertrag zwischen den Modulen: `:app` hängt von `:core` ab, nie umgekehrt. Die App rendert nur, was
`Scene` beschreibt, und fragt Treffer über `Rules` ab.

## CI

`.github/workflows/android.yml` führt bei Änderungen unter `android/` die Tests aus, baut die Debug-APK und
lädt sie als Artifact `wuselburg-debug-apk` hoch.
