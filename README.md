# Wuselburg – Wo steckt Fips?

Prototyp eines Wimmelbild-Suchspiels für Handy und Tablet (läuft im Browser, keine Abhängigkeiten).
Inspiriert vom Genre der Suchbücher und der Detektiv-Wimmelbilder – mit **eigener Welt und eigenen Figuren**.

## Spielidee

Die Stadt **Wuselburg** ist ein riesiges, zoom- und verschiebbares Wimmelbild voller Menschen, Tiere und Details.

- **Finde Fips** – Fips ist ein kleiner Fuchs mit türkisem Schal, gelben Punkten und weißer Schwanzspitze.
  Mit steigender Schwierigkeit verstecken sich mehr Doppelgänger (anderer Schal, keine Punkte, graues Fell …)
  und Fips versteckt sich teilweise hinter Bäumen.
- **Detektiv-Fälle** – Kurze Geschichten auf derselben Karte: erst den Tatort finden, dann einer Spur
  folgen (inklusive falscher Fährte) und den Täter überführen. Erster Fall: *Der Kuchen-Fall*.
- **Sterne** – 1 Stern fürs Lösen, +1 ohne Tipp, +1 mit höchstens 2 Fehlversuchen. Kein Zeitdruck, keine Strafen.
- **Tipp** – zeigt einen ungefähren Bereich und fliegt die Kamera dorthin.

## Ausprobieren

```sh
python3 -m http.server 8000   # im Projektordner
# dann http://localhost:8000 im Browser (am Handy: IP des Rechners)
```

Oder `index.html` direkt im Browser öffnen. Steuerung: ein Finger verschiebt, zwei Finger zoomen
(am Rechner: Ziehen, Mausrad, +/−), Tippen markiert einen Verdacht.

## Aufbau

| Datei | Inhalt |
| --- | --- |
| `index.html` | Menü, Spielansicht, Overlay-Karte |
| `style.css` | Layout und Look (Mobile first, Safe-Area-Insets) |
| `game.js` | Level-Daten, Szenengenerator (SVG aus Seed), Kamera, Eingabe, Spielablauf |

Die Szenen werden pro Level deterministisch aus einem Seed erzeugt (`buildScene`). Neue Level sind
ein weiterer Eintrag in `LEVELS` (Seed, Menge an Figuren, Doppelgänger-Typen, Versteck an/aus).

## Nächste Schritte

- Handgezeichnete Wimmelbilder statt generierter SVGs (eigener Illustrationsstil, größere Detailtiefe).
- Weitere Fälle mit mehreren Stationen und Hinweisen, Suchlisten ("Finde 5 Dinge").
- Koop-Modus für zwei Spieler auf einem Tablet, tägliches Rätsel.
- Native Verpackung (z. B. Capacitor), Ton und Musik, Kinderschutz-Konformität (COPPA/DSGVO).
- Vor einem Release: Namen und Figuren markenrechtlich prüfen (DPMA/EUIPO).

## Native Android-Prototyp

Neben der Web-Version gibt es einen nativen Android-Prototyp (Kotlin, Jetpack Compose, Canvas – kein WebView).
Er setzt die Level, Figuren und Texte dieses Prototyps um und läuft auf Handy und Tablet, hoch und quer.

- Anleitung, Build und Aufbau: [android/README.md](android/README.md)
- Fertige Test-APK zum Installieren: [dist/wuselburg-debug.apk](dist/wuselburg-debug.apk)
