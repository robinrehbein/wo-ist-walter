// OWNER: CORE
package de.wuselburg.core

/** The four levels, copied from scene.js LEVELS (German texts verbatim). */
object Levels {
    val all: List<LevelDef> = listOf(
        LevelDef(
            id = "fips1", kind = LevelKind.FIPS, title = "Marktplatz", subtitle = "Leicht · Fips ist gut zu sehen",
            seed = 11, crowd = 230, foxes = 8, decoys = listOf("noScarf"), hidden = false, groups = 4, mood = "day",
            intro = "Fips, der kleine Fuchs, ist beim Markttrubel verloren gegangen. Findest du ihn?",
        ),
        LevelDef(
            id = "fips2", kind = LevelKind.FIPS, title = "Park-Wirbel", subtitle = "Mittel · Fips versteckt sich",
            seed = 23, crowd = 380, foxes = 26,
            decoys = listOf("noScarf", "redScarf", "plainScarf"),
            hidden = true, hideOffset = 36.0, groups = 6, mood = "day",
            intro = "Heute ist der Park voll! Fips spielt Verstecken – und es laufen viele andere Füchse herum.",
        ),
        LevelDef(
            id = "fips3", kind = LevelKind.FIPS, title = "Rushhour", subtitle = "Schwer · Doppelgänger überall",
            seed = 37, crowd = 560, foxes = 56,
            decoys = listOf("noScarf", "redScarf", "plainScarf", "noTip", "grey"),
            hidden = true, hideOffset = 30.0, groups = 8, mood = "evening",
            intro = "Feierabend in Wuselburg! Achte genau auf Schal, Punkte und Schwanzspitze – es gibt Doppelgänger.",
        ),
        LevelDef(
            id = "case1", kind = LevelKind.CASE, title = "Der Kuchen-Fall", subtitle = "Detektiv · Folge der Spur",
            seed = 5, crowd = 380, foxes = 6, decoys = listOf("noScarf", "redScarf"), raccoons = 5, groups = 5, mood = "day",
            intro = "In der Bäckerei ist der Geburtstagskuchen verschwunden! Finde zuerst die Bäckerei und folge " +
                "dann der Krümelspur bis zum Dieb. Pass auf: Nicht jede Spur führt zum Ziel.",
        ),
    )
}
