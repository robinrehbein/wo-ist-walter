package de.wuselburg.render

import android.graphics.Canvas
import de.wuselburg.core.Drawable
import de.wuselburg.core.House

/*
 * Decor drawing: trees, stalls, street props (drawables) and houses. 1:1 port of treeSvg / stallSvg /
 * propSvg / houseSvg of scene.js. Coordinates are the local SVG ones: the caller applies
 * translate(x, y) scale(+-s, s) before calling.
 */

private val OL_FIG = parseCssColor("rgba(30,20,40,.32)")
private val OL_HOUSE = parseCssColor("rgba(30,20,40,.28)")
private const val OL_FIG_W = 0.7f
private const val OL_HOUSE_W = 1f

private val WHITE = parseCssColor("#ffffff")
private val SHADOW_14 = parseCssColor("rgba(0,0,0,.14)")
private val SHADOW_13 = parseCssColor("rgba(0,0,0,.13)")
private val SHADOW_12 = parseCssColor("rgba(0,0,0,.12)")

private fun Drawable.ps(key: String): String = (props[key] as? String) ?: ""
private fun Drawable.pn(key: String): Float = (props[key] as? Number)?.toFloat() ?: 0f

/**
 * Draws tree / stall / prop drawables (already transformed to their local origin).
 * Returns false for every other kind (figures: see drawFigure).
 */
internal fun drawDecorDrawable(canvas: Canvas, d: Drawable, mood: String): Boolean {
    val ink = Ink.get()
    when (d.kind) {
        "tree" -> ink.tree(canvas, d.ps("type"), ink.col(d.ps("color")))
        "stall" -> ink.stall(canvas, ink.col(d.ps("color")), d.ps("goods"))
        "prop" -> ink.prop(canvas, d, mood)
        else -> return false
    }
    return true
}

// ---------------------------------------------------------------- Bäume
internal fun Ink.tree(c: Canvas, type: String, crown: Int) {
    oval(c, 0f, 2f, 20f, 5f, SHADOW_14, o = false)
    outlined(OL_FIG, OL_FIG_W) {
        if (type == "pine") {
            rect(c, -3.5f, -22f, 7f, 22f, col("#7a4e2d"), 2f)
            tri(c, -22f, -18f, 0f, -52f, 22f, -18f, col("#2f7d4a"))
            tri(c, -18f, -42f, 0f, -76f, 18f, -42f, col("#3b9158"))
            tri(c, -13f, -66f, 0f, -96f, 13f, -66f, col("#48a566"))
            return
        }
        if (type == "birch") {
            rect(c, -3.5f, -40f, 7f, 40f, col("#f1efe8"), 2f)
            val bark = col("#555555")
            rect(c, -3.5f, -30f, 4f, 1.6f, bark, o = false)
            rect(c, 0f, -20f, 3.5f, 1.6f, bark, o = false)
            rect(c, -3.5f, -10f, 3f, 1.6f, bark, o = false)
            oval(c, 0f, -64f, 20f, 30f, col("#8fd16a"))
            oval(c, -6f, -72f, 7f, 10f, col("rgba(255,255,255,.18)"), o = false)
            return
        }
        rect(c, -4f, -34f, 8f, 34f, col("#7a4e2d"), 3f)
        circle(c, -20f, -48f, 18f, crown)
        circle(c, 20f, -48f, 18f, crown)
        circle(c, 0f, -62f, 26f, crown)
        circle(c, -8f, -68f, 9f, col("rgba(255,255,255,.16)"), o = false)
        oval(c, 0f, -38f, 30f, 6f, col("rgba(0,0,0,.07)"), o = false)
        if (type == "apple") {
            val red = col("#e05a5a")
            circle(c, -14f, -52f, 2.6f, red, o = false)
            circle(c, 10f, -70f, 2.6f, red, o = false)
            circle(c, 18f, -48f, 2.6f, red, o = false)
            circle(c, -4f, -58f, 2.6f, red, o = false)
            circle(c, -24f, -44f, 2.6f, red, o = false)
        }
        if (type == "blossom") {
            val white = col("rgba(255,255,255,.75)") // opacity .75
            circle(c, -14f, -52f, 2f, white, o = false)
            circle(c, 8f, -72f, 2f, white, o = false)
            circle(c, 20f, -46f, 2f, white, o = false)
            circle(c, -6f, -60f, 2f, white, o = false)
        }
    }
}

// ---------------------------------------------------------------- Stände
internal fun Ink.stall(c: Canvas, awning: Int, goods: String) {
    oval(c, 0f, 4f, 52f, 7f, SHADOW_12, o = false)
    outlined(OL_FIG, OL_FIG_W) {
        val pole = col("#8a6a45")
        rect(c, -42f, -44f, 5f, 44f, pole)
        rect(c, 37f, -44f, 5f, 44f, pole)
        rect(c, -42f, -24f, 84f, 24f, col("#c99a62"), 3f)
        for (i in 0 until 6) rect(c, -45f + i * 15f, -62f, 15f, 18f, if (i % 2 == 1) WHITE else awning, o = false)
        path.rewind()
        path.moveTo(-45f, -44f)
        for (i in 0 until 6) q(7.5f, 10f, 15f, 0f)
        path.close()
        fillPath(c, awning)
        when (goods) {
            "fruit" -> {
                val red = col("#e45b4b"); val yel = col("#f2b84b"); val grn = col("#7bc47f")
                circle(c, -24f, -28f, 4.5f, red)
                circle(c, -12f, -28f, 4.5f, yel)
                circle(c, 2f, -28f, 4.5f, grn)
                circle(c, 16f, -28f, 4.5f, red)
                circle(c, 29f, -28f, 4.5f, yel)
            }
            "veg" -> {
                val orange = col("#f08a2c")
                tri(c, -30f, -24f, -26f, -36f, -22f, -24f, orange, o = false)
                tri(c, -20f, -24f, -16f, -36f, -12f, -24f, orange, o = false)
                circle(c, -4f, -29f, 5f, col("#7bc47f"), o = false)
                circle(c, 8f, -29f, 5f, col("#a56bd6"), o = false)
                circle(c, 20f, -29f, 5f, col("#7bc47f"), o = false)
                circle(c, 31f, -28f, 4f, col("#e05a5a"), o = false)
            }
            "flowers" -> {
                val pot = col("#b9792f")
                rect(c, -32f, -30f, 14f, 8f, pot, 2f, o = false)
                rect(c, -8f, -30f, 14f, 8f, pot, 2f, o = false)
                rect(c, 16f, -30f, 14f, 8f, pot, 2f, o = false)
                circle(c, -29f, -34f, 3f, col("#ff9fc2"), o = false)
                circle(c, -23f, -35f, 3f, col("#ffd84a"), o = false)
                circle(c, -5f, -34f, 3f, col("#ffffff"), o = false)
                circle(c, 1f, -35f, 3f, col("#c9a0ff"), o = false)
                circle(c, 19f, -34f, 3f, col("#e05a5a"), o = false)
                circle(c, 25f, -35f, 3f, col("#ff9fc2"), o = false)
            }
            "bread" -> {
                val a = col("#d79a4a"); val b = col("#c98636")
                oval(c, -24f, -28f, 7f, 4f, a, o = false)
                oval(c, -8f, -28f, 7f, 4f, b, o = false)
                oval(c, 8f, -28f, 7f, 4f, a, o = false)
                oval(c, 25f, -28f, 7f, 4f, b, o = false)
            }
            "fish" -> {
                val f = col("#9fb4c7")
                oval(c, -22f, -28f, 8f, 3.6f, f, o = false)
                tri(c, -14f, -28f, -9f, -32f, -9f, -24f, f, o = false)
                oval(c, 2f, -27f, 8f, 3.6f, f, o = false)
                tri(c, 10f, -27f, 15f, -31f, 15f, -23f, f, o = false)
                oval(c, 25f, -28f, 6f, 3f, f, o = false)
            }
        }
    }
}

// ---------------------------------------------------------------- Straßenmöbel & Requisiten
internal fun Ink.prop(c: Canvas, d: Drawable, mood: String) {
    val evening = mood == "evening"
    when (d.ps("type")) {
        "lamp" -> {
            oval(c, 0f, 1f, 6f, 2f, col("rgba(0,0,0,.15)"), o = false)
            if (evening) circle(c, 0f, -60f, 22f, col("rgba(255,220,120,.30)"), o = false)
            outlined(OL_FIG, OL_FIG_W) {
                rect(c, -2f, -58f, 4f, 58f, col("#4a4f57"))
                rect(c, -5f, -4f, 10f, 4f, col("#3a3f47"), 1f)
                quad(c, -6f, -58f, 6f, -58f, 4f, -66f, -4f, -66f, col("#3a3f47"))
                circle(c, 0f, -62f, 4f, if (evening) col("#ffe9a8") else col("#fff3c4"))
            }
        }
        "bench" -> {
            oval(c, 0f, 1f, 24f, 3f, SHADOW_13, o = false)
            outlined(OL_FIG, OL_FIG_W) {
                rect(c, -20f, -22f, 40f, 5f, col("#a9733a"), 1.5f)
                rect(c, -20f, -16f, 40f, 3f, col("#8a5a2a"))
                rect(c, -21f, -12f, 42f, 5f, col("#b9823f"), 1.5f)
                rect(c, -18f, -7f, 3f, 7f, col("#4a4f57"))
                rect(c, 15f, -7f, 3f, 7f, col("#4a4f57"))
            }
        }
        "bush" -> {
            val cc = col(d.ps("color")); val c2 = col(d.ps("color2"))
            oval(c, 0f, 2f, 16f, 4f, SHADOW_13, o = false)
            outlined(OL_FIG, OL_FIG_W) {
                circle(c, -9f, -8f, 9f, cc)
                circle(c, 9f, -8f, 9f, cc)
                circle(c, 0f, -13f, 10f, cc)
            }
            circle(c, -6f, -10f, 1.8f, c2, o = false)
            circle(c, 5f, -14f, 1.8f, c2, o = false)
            circle(c, 10f, -7f, 1.8f, c2, o = false)
        }
        "blanket" -> {
            val cc = col(d.ps("color")); val c2 = col(d.ps("color2"))
            outlined(OL_FIG, OL_FIG_W) {
                rect(c, -48f, -40f, 96f, 44f, cc, 3f)
                for (i in 0 until 4) for (j in 0 until 3) if ((i + j) % 2 == 0)
                    rect(c, -48f + i * 24f, -40f + j * 14.7f, 24f, 14.7f, c2, o = false)
            }
        }
        "basket" -> outlined(OL_FIG, OL_FIG_W) {
            quad(c, -9f, -9f, 9f, -9f, 7f, 0f, -7f, 0f, col("#b9792f"))
            path.rewind(); path.moveTo(-7f, -9f); q(7f, -13f, 14f, 0f)
            strokePath(c, col("#8a5a2a"), 1.4f)
            rect(c, -8f, -11f, 16f, 3f, col("#e05a5a"), 1f)
        }
        "guitarcase" -> {
            outlined(OL_FIG, OL_FIG_W) {
                rect(c, -14f, -7f, 28f, 9f, col("#3a3f47"), 3f)
                rect(c, -11f, -5f, 22f, 5f, col("#7a2f3a"), 2f)
            }
            val y = col("#ffd84a")
            circle(c, -5f, -2f, 1.6f, y, o = false)
            circle(c, 1f, -3f, 1.6f, y, o = false)
            circle(c, 6f, -2f, 1.6f, y, o = false)
        }
        "cart" -> {
            val cc = col(d.ps("color"))
            oval(c, 0f, 3f, 34f, 5f, SHADOW_13, o = false)
            outlined(OL_FIG, OL_FIG_W) {
                rect(c, -1.5f, -70f, 3f, 40f, col("#8a6a45"))
                for (i in 0 until 6) {
                    path.rewind(); path.moveTo(-34f + i * 11.3f, -66f); q(5.65f, -16f, 11.3f, 0f); path.close()
                    fillPath(c, if (i % 2 == 1) WHITE else cc, o = false)
                }
                rect(c, -28f, -30f, 56f, 26f, col("#fff4e0"), 4f)
                rect(c, -28f, -30f, 56f, 8f, cc, 4f)
                val wheel = col("#555555")
                circle(c, -18f, -2f, 5f, wheel)
                circle(c, 18f, -2f, 5f, wheel)
                circle(c, -12f, -35f, 5f, col("#f7c6d9"))
                circle(c, 0f, -36f, 5f, col("#fff4e0"))
                circle(c, 12f, -35f, 5f, col("#a9733a"))
            }
        }
        "leash" -> {
            val dx = d.pn("dx"); val dy = d.pn("dy")
            path.rewind(); path.moveTo(0f, 0f); path.quadTo(dx * 0.5f, dy + 8f, dx, dy)
            strokePath(c, col("#a02f3a"), 1.1f)
        }
        "easel" -> {
            oval(c, 0f, 2f, 14f, 3f, SHADOW_12, o = false)
            outlined(OL_FIG, OL_FIG_W) {
                path.rewind()
                path.moveTo(-10f, 0f); path.lineTo(-3f, -44f)
                path.moveTo(10f, 0f); path.lineTo(3f, -44f)
                path.moveTo(0f, 0f); path.lineTo(0f, -30f)
                strokePath(c, col("#8a6a45"), 2f)
                rect(c, -13f, -52f, 26f, 22f, WHITE)
            }
            circle(c, -6f, -45f, 3.4f, col("#e05a5a"), o = false)
            circle(c, 2f, -40f, 3.4f, col("#4a7fd6"), o = false)
            circle(c, 6f, -47f, 2.6f, col("#ffd84a"), o = false)
            path.rewind(); path.moveTo(-10f, -34f); q(8f, -6f, 20f, 0f)
            strokePath(c, col("#7bc47f"), 2.2f)
        }
        "ball" -> {
            val y = -d.pn("lift") - 5f
            oval(c, 0f, 1f, 6f, 2f, col("rgba(0,0,0,.18)"), o = false)
            outlined(OL_FIG, OL_FIG_W) {
                circle(c, 0f, y, 5f, WHITE)
                path.rewind(); path.moveTo(-5f, y)
                path.arcTo(-5f, y - 5f, 5f, y + 5f, 180f, 180f, false) // a5,5 0 0 1 10,0
                path.close()
                fillPath(c, col("#e05a5a"))
            }
        }
        "rod" -> {
            val dx = d.pn("dx"); val dy = d.pn("dy")
            path.rewind(); path.moveTo(0f, 0f); path.lineTo(dx * 0.45f, -30f)
            strokePath(c, col("#6b4a2b"), 1.6f, round = true)
            path.rewind(); path.moveTo(dx * 0.45f, -30f); path.lineTo(dx, dy)
            strokePath(c, col("#999999"), 0.7f)
            outlined(OL_FIG, OL_FIG_W) { circle(c, dx, dy, 2.6f, col("#e05a5a")) }
        }
        "bucket" -> {
            outlined(OL_FIG, OL_FIG_W) {
                quad(c, -6f, -9f, 6f, -9f, 4.5f, 0f, -4.5f, 0f, col("#4a7fd6"))
                path.rewind(); path.moveTo(-5f, -9f); q(5f, -7f, 10f, 0f)
                strokePath(c, col("#777777"), OL_FIG_W) // stroke inherited width .7
            }
            oval(c, 0f, -9f, 6f, 1.6f, col("#9fd8f0"), o = false)
        }
        "notes" -> {
            val k = col("#333333")
            oval(c, 0f, 0f, 3f, 2.2f, k, o = false)
            rect(c, 2f, -12f, 1.2f, 12f, k, o = false)
            path.rewind(); path.moveTo(3.2f, -12f); q(5f, 1f, 3f, 5f); fillPath(c, k, o = false)
            oval(c, 14f, -8f, 3f, 2.2f, k, o = false)
            rect(c, 16f, -20f, 1.2f, 12f, k, o = false)
            path.rewind(); path.moveTo(17.2f, -20f); q(5f, 1f, 3f, 5f); fillPath(c, k, o = false)
        }
    }
}

// ---------------------------------------------------------------- Häuser
private const val SHOP_CAFE = "#4a7fd6"
private const val SHOP_FLOWERS = "#7bc47f"
private const val SHOP_BOOKS = "#a56bd6"
private const val SHOP_BAKERY = "#e45b4b"

internal fun Ink.house(c: Canvas, hs: House, mood: String) {
    val x = hs.x.toFloat(); val by = hs.baseY.toFloat(); val w = hs.w.toFloat(); val h = hs.h.toFloat()
    val top = by - h
    val floors = if (h > 150f) 2 else 1
    val winFill = if (mood == "evening") col("#ffe9a8") else col("#bfe6f5")
    val white = WHITE
    outlined(OL_HOUSE, OL_HOUSE_W) {
        rect(c, x, top, w, h, col(hs.wall))
        rect(c, x + w - 16f, top, 16f, h, col("rgba(0,0,0,.07)"), o = false)
        if (hs.roofType == "flat") {
            rect(c, x - 5f, top - 14f, w + 10f, 14f, col(hs.roof), 2f)
            rect(c, x + 10f, top - 24f, 18f, 10f, col("#cfd3d8"))
        } else {
            val steep = hs.roofType == "steep"
            val apex = if (steep) 66f else 46f
            val ov = if (steep) 2f else 7f
            tri(c, x - ov, top, x + w / 2f, top - apex, x + w + ov, top, col(hs.roof))
            val tile = col("rgba(0,0,0,.16)")
            for (f in FLOORS_T) {
                val half = (w / 2f + ov) * (1f - f)
                val ty = f1(top - apex * f)
                path.rewind()
                path.moveTo(f1(x + w / 2f - half), ty)
                path.lineTo(f1(x + w / 2f + half), ty)
                strokePath(c, tile, OL_HOUSE_W)
            }
            if (hs.chimney) {
                rect(c, x + w * 0.68f, top - 40f, 12f, 22f, col("#9c5b45"))
                rect(c, x + w * 0.68f - 2f, top - 43f, 16f, 4f, col("#7e4634"))
            }
        }
        val noShop = hs.shop.isEmpty()
        for (f in 0 until floors) {
            for (cI in 0 until 2) {
                val wx = x + 16f + cI * (w - 32f - 24f)
                val wy = top + 16f + f * 56f
                if (hs.shutters) {
                    val sh = col(hs.shutterColor)
                    rect(c, wx - 7f, wy, 6f, 28f, sh)
                    rect(c, wx + 25f, wy, 6f, 28f, sh)
                }
                rect(c, wx, wy, 24f, 28f, winFill, 3f, sc = white, sw = 3f)
                path.rewind()
                path.moveTo(wx + 12f, wy); path.rLineTo(0f, 28f)
                path.moveTo(wx, wy + 14f); path.rLineTo(24f, 0f)
                strokePath(c, white, 2f)
                val cur = hs.curtains.getOrNull(f * 2 + cI)
                if (!cur.isNullOrEmpty()) rect(c, wx + 2f, wy + 2f, 9f, 24f, withOpacity(col(cur), 0.8f), o = false)
                if (hs.plantBox && f == floors - 1 && !hs.bakery && noShop) {
                    rect(c, wx - 2f, wy + 28f, 28f, 6f, col("#8a5a3b"), 1.5f)
                    val green = col("#5bbf5a")
                    circle(c, wx + 4f, wy + 27f, 3.4f, green, o = false)
                    circle(c, wx + 12f, wy + 26f, 3.4f, col("#e05a5a"), o = false)
                    circle(c, wx + 20f, wy + 27f, 3.4f, green, o = false)
                }
            }
        }
        rect(c, x + w / 2f - 13f, by - 42f, 26f, 42f, if (hs.bakery) col("#7a4e2d") else col(hs.door), 3f)
        circle(c, x + w / 2f + 7f, by - 20f, 2f, col("#ffd84a"), o = false)

        val shopColor: String?
        val shopText: String
        if (hs.bakery) { shopColor = SHOP_BAKERY; shopText = "Bäckerei" }
        else when (hs.shop) {
            "cafe" -> { shopColor = SHOP_CAFE; shopText = "Café" }
            "flowers" -> { shopColor = SHOP_FLOWERS; shopText = "Blumen" }
            "books" -> { shopColor = SHOP_BOOKS; shopText = "Bücher" }
            else -> { shopColor = null; shopText = "" }
        }
        if (shopColor != null) {
            val sc = col(shopColor)
            val sw = w / 8f
            for (i in 0 until 8) rect(c, x + i * sw, by - 62f, sw, 16f, if (i % 2 == 1) white else sc, o = false)
            path.rewind()
            path.moveTo(x, by - 46f)
            for (i in 0 until 8) q(w / 16f, 8f, sw, 0f)
            path.close()
            fillPath(c, sc, o = false)
            rect(c, x + 6f, top + 66f, w - 12f, 22f, col("#fff6dc"), 4f, sc = col("#c99a62"), sw = 2f)
            text(c, shopText, x + w / 2f, top + 82f, 14f, col("#8a4b1c"))
            if (hs.shop == "flowers") {
                val pot = col("#b9792f")
                rect(c, x + 6f, by - 10f, 12f, 10f, pot, o = false)
                circle(c, x + 12f, by - 14f, 5f, col("#ff9fc2"), o = false)
                rect(c, x + w - 18f, by - 10f, 12f, 10f, pot, o = false)
                circle(c, x + w - 12f, by - 14f, 5f, col("#ffd84a"), o = false)
            }
            if (hs.shop == "cafe") {
                val leg = col("#777777")
                circle(c, x + 14f, by - 8f, 7f, white, o = false)
                rect(c, x + 13f, by - 8f, 2f, 8f, leg, o = false)
                circle(c, x + w - 14f, by - 8f, 7f, white, o = false)
                rect(c, x + w - 15f, by - 8f, 2f, 8f, leg, o = false)
            }
        }
    }
}

private val FLOORS_T = floatArrayOf(0.28f, 0.55f, 0.8f)
