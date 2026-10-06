// OWNER: CORE
package de.wuselburg.core

/** The four levels, copied from game.js LEVELS (German texts verbatim). */
object Levels {
    val all: List<LevelDef> = listOf(
        LevelDef(
            id = "fips1", kind = LevelKind.FIPS, title = "Marktplatz", subtitle = "Leicht · Fips ist gut zu sehen",
            seed = 11, crowd = 230, foxes = 8, decoys = listOf(FoxStyle.NO_SCARF), hidden = false,
            intro = "Fips, der kleine Fuchs, ist beim Markttrubel verloren gegangen. Findest du ihn?",
        ),
        LevelDef(
            id = "fips2", kind = LevelKind.FIPS, title = "Park-Wirbel", subtitle = "Mittel · Fips versteckt sich",
            seed = 23, crowd = 380, foxes = 26,
            decoys = listOf(FoxStyle.NO_SCARF, FoxStyle.RED_SCARF, FoxStyle.PLAIN_SCARF),
            hidden = true, hideOffset = 36.0,
            intro = "Heute ist der Park voll! Fips spielt Verstecken – und es laufen viele andere Füchse herum.",
        ),
        LevelDef(
            id = "fips3", kind = LevelKind.FIPS, title = "Rushhour", subtitle = "Schwer · Doppelgänger überall",
            seed = 37, crowd = 560, foxes = 56,
            decoys = listOf(FoxStyle.NO_SCARF, FoxStyle.RED_SCARF, FoxStyle.PLAIN_SCARF, FoxStyle.NO_TIP, FoxStyle.GREY),
            hidden = true, hideOffset = 30.0,
            intro = "Feierabend in Wuselburg! Achte genau auf Schal, Punkte und Schwanzspitze – es gibt Doppelgänger.",
        ),
        LevelDef(
            id = "case1", kind = LevelKind.CASE, title = "Der Kuchen-Fall", subtitle = "Detektiv · Folge der Spur",
            seed = 5, crowd = 380, foxes = 6, decoys = listOf(FoxStyle.NO_SCARF, FoxStyle.RED_SCARF), raccoons = 5,
            intro = "In der Bäckerei ist der Geburtstagskuchen verschwunden! Finde zuerst die Bäckerei und folge " +
                "dann der Krümelspur bis zum Dieb. Pass auf: Nicht jede Spur führt zum Ziel.",
        ),
    )
}
