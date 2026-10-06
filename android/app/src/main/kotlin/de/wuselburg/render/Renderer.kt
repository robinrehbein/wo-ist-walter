package de.wuselburg.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Canvas
import android.graphics.Picture
import de.wuselburg.core.Block
import de.wuselburg.core.BlockType
import de.wuselburg.core.Drawable
import de.wuselburg.core.Scene
import de.wuselburg.core.WORLD_H
import de.wuselburg.core.WORLD_W
import de.wuselburg.core.WorldLayout

/*
 * Port of sceneToSvg() (scene.js): background (grass, roads, zebra, blocks + houses), crumbs, drawables in the
 * given (already sorted) order, evening tint. Figures are drawn by Figures.kt (drawFigure), trees / stalls /
 * props / houses by Decor.kt.
 */

private val C_GRASS = parseCssColor("#bfe3a4")
private val C_HOUSE_GRASS = parseCssColor("#d3e7b9")
private val C_ASPHALT = parseCssColor("#dcd3c0")
private val C_CURB_LINE = parseCssColor("rgba(0,0,0,.07)")
private val C_CENTER = parseCssColor("rgba(255,255,255,.7)")      // #fff, opacity .7
private val C_ZEBRA = parseCssColor("rgba(255,255,255,.75)")      // #fff, opacity .75
private val C_WHITE = parseCssColor("#ffffff")
private val C_WHITE_80 = parseCssColor("rgba(255,255,255,.8)")    // ripples, opacity .8
private val C_PARK = parseCssColor("#8fcf7a")
private val C_TUFT = parseCssColor("#6fb85f")
private val C_SAND = parseCssColor("#e9e1c8")
private val C_WATER = parseCssColor("#9fd8f0")
private val C_LILY = parseCssColor("#5fae5b")
private val C_LILY_FLOWER = parseCssColor("#ff9fc2")
private val C_MARKET = parseCssColor("#f0dcae")
private val C_PLAZA = parseCssColor("#e8e1d2")
private val C_PLAZA_LINE = parseCssColor("rgba(0,0,0,.05)")
private val C_BASIN = parseCssColor("#b9e3f2")
private val C_FOUNTAIN_SHADOW = parseCssColor("rgba(45,30,15,.16)")
private val C_STONE = parseCssColor("#f6f1e4")
private val C_STONE_DARK = parseCssColor("#d9d2c0")
private val C_STONE_MID = parseCssColor("#cfc8b5")
private val C_SPRAY = parseCssColor("#e8f8ff")
private val C_SIDEWALK = parseCssColor("#e6dfcf")
private val C_CRUMB_BIG = parseCssColor("#c98a2b")
private val C_CRUMB = parseCssColor("#e0a845")
private val C_ROAD_FOLD = parseCssColor("rgba(255,250,240,.28)")
private val C_EDGE = PaperStyle.EDGE_PAPER
private val C_BLOCK_SHADOW = parseCssColor("rgba(45,30,15,.30)")
private val C_EVENING = parseCssColor("rgba(255,140,60,.16)")

private val ROAD = WorldLayout.ROAD.toFloat()
private val BW = WorldLayout.BLOCK_W.toFloat()
private val BH = WorldLayout.BLOCK_H.toFloat()
private val COLS = WorldLayout.COLS
private val ROWS = WorldLayout.ROWS
private val W = WORLD_W.toFloat()
private val H = WORLD_H.toFloat()

/**
 * Records the whole world (0..WORLD_W x 0..WORLD_H, world coordinates) ONCE per level.
 * [paper] = Papier-Optik (white cut edges, cast shadows, grain), false = the classic dark-outline look.
 * [grain] is the 256x256 paper-grain tile (null = no grain); it is referenced by the Picture, do not recycle it.
 */
fun recordScenePicture(scene: Scene, paper: Boolean = true, grain: Bitmap? = null): Picture {
    val picture = Picture()
    val canvas = picture.beginRecording(WORLD_W.toInt(), WORLD_H.toInt())
    try {
        drawScene(canvas, scene, paper, grain)
    } finally {
        picture.endRecording()
    }
    return picture
}

/** Renders the scene straight into a software bitmap [widthPx] wide (aspect of the world). Used by tests. */
fun renderSceneToBitmap(scene: Scene, widthPx: Int, paper: Boolean = true, grain: Bitmap? = null): Bitmap {
    val k = widthPx / WORLD_W.toFloat()
    val bitmap = Bitmap.createBitmap(widthPx, (WORLD_H * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(k, k)
    drawScene(canvas, scene, paper, grain)
    return bitmap
}

/**
 * Layers of sceneToSvg(): background, houses (paper: own pass), crumbs, drawables (painter order as given),
 * evening tint, paper grain. In paper mode the houses and the drawables each get a cast-shadow pass first
 * (the web's feDropShadow dx 1.8 dy 2.8, #2a1d10 @ .34, here without blur): the group is drawn once as a flat
 * silhouette, offset, inside ONE alpha layer (so overlaps do not darken twice), then drawn normally.
 */
internal fun drawScene(c: Canvas, scene: Scene, paper: Boolean = true, grain: Bitmap? = null) {
    val ink = Ink.get()
    val style = PaperStyle.get()
    val oldPaper = style.paper
    style.paper = paper
    style.shadowMode = false
    try {
        val mood = scene.mood
        drawBackground(c, ink, scene, mood, paper)
        if (paper) {
            castShadowed(c, style) { for (b in scene.blocks) if (b.type == BlockType.HOUSES) for (hd in b.houses) ink.house(c, hd, mood) }
        }
        for (cr in scene.crumbs) {
            if (cr.big) ink.circle(c, f1(cr.x.toFloat()), f1(cr.y.toFloat()), 3.4f, C_CRUMB_BIG, o = false)
            else ink.circle(c, f1(cr.x.toFloat()), f1(cr.y.toFloat()), 2.4f, C_CRUMB, o = false)
        }
        if (paper) castShadowed(c, style) { for (d in scene.drawables) drawPlaced(c, d, mood) }
        else for (d in scene.drawables) drawPlaced(c, d, mood)
        if (mood == "evening") ink.rect(c, 0f, 0f, W, H, C_EVENING, o = false)
        if (paper && grain != null) drawGrain(c, grain)
    } finally {
        style.shadowMode = false
        style.paper = oldPaper
    }
}

/** Draws [body] as shadow silhouette (offset, one alpha layer), then again normally. */
private inline fun castShadowed(c: Canvas, style: PaperStyle, body: () -> Unit) {
    val layer = c.saveLayerAlpha(0f, 0f, W + 8f, H + 8f, PaperStyle.SHADOW_ALPHA)
    c.translate(PaperStyle.SHADOW_DX, PaperStyle.SHADOW_DY)
    style.shadowMode = true
    try { body() } finally { style.shadowMode = false }
    c.restoreToCount(layer)
    body()
}

/** The 256 px tile is shown at 384 world units, repeated over the whole world (pattern in sceneToSvg). */
private fun drawGrain(c: Canvas, grain: Bitmap) {
    val shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    val m = Matrix()
    m.setScale(GRAIN_WORLD / grain.width, GRAIN_WORLD / grain.height)
    shader.setLocalMatrix(m)
    val p = Paint(Paint.FILTER_BITMAP_FLAG)
    p.shader = shader
    c.drawRect(0f, 0f, W, H, p)
}

private const val GRAIN_WORLD = 384f

/** drawableSvg(): translate(f1(x) f1(y)) scale(flip ? -s : s, s), then the kind's own drawing. */
private fun drawPlaced(c: Canvas, d: Drawable, mood: String) {
    val sx = if (d.flip) -d.scale else d.scale
    c.save()
    c.translate(f1(d.x.toFloat()), f1(d.y.toFloat()))
    c.scale((Math.round(sx * 1000.0 * 10.0) / 10.0 / 1000.0).toFloat(), d.scale.toFloat())
    if (!drawDecorDrawable(c, d, mood)) drawFigure(c, d, mood)
    c.restore()
}

private fun drawBackground(c: Canvas, ink: Ink, scene: Scene, mood: String, paper: Boolean) {
    if (paper) {
        // paper: the blocks lie as layers on the asphalt, the road edges are light strips
        ink.rect(c, 0f, 0f, W, H, C_ASPHALT, o = false)
        for (k in 0..COLS) ink.rect(c, k * (BW + ROAD), 0f, ROAD, H, C_ROAD_FOLD, o = false)
        for (j in 0..ROWS) ink.rect(c, 0f, j * (BH + ROAD), W, ROAD, C_ROAD_FOLD, o = false)
    } else drawClassicRoads(c, ink, scene)
    drawMarkings(c, ink)
    for (b in scene.blocks) drawBlock(c, ink, b, mood, paper)
}

private fun drawClassicRoads(c: Canvas, ink: Ink, scene: Scene) {
    ink.rect(c, 0f, 0f, W, H, C_GRASS, o = false)
    // grass under the house blocks
    for (b in scene.blocks) if (b.type == BlockType.HOUSES) {
        ink.rect(c, b.x.toFloat(), b.y.toFloat(), b.w.toFloat(), b.h.toFloat(), C_HOUSE_GRASS, 14f, o = false)
    }
    // roads (asphalt + curb lines)
    for (k in 0..COLS) {
        val x = k * (BW + ROAD)
        ink.rect(c, x, 0f, ROAD, H, C_ASPHALT, o = false)
        ink.line(c, x + 6f, 0f, x + 6f, H, C_CURB_LINE, 4f)
        ink.line(c, x + ROAD - 6f, 0f, x + ROAD - 6f, H, C_CURB_LINE, 4f)
    }
    for (j in 0..ROWS) {
        val y = j * (BH + ROAD)
        ink.rect(c, 0f, y, W, ROAD, C_ASPHALT, o = false)
        ink.line(c, 0f, y + 6f, W, y + 6f, C_CURB_LINE, 4f)
        ink.line(c, 0f, y + ROAD - 6f, W, y + ROAD - 6f, C_CURB_LINE, 4f)
    }
    for (k in 0..COLS) for (j in 0..ROWS) {
        ink.rect(c, k * (BW + ROAD), j * (BH + ROAD), ROAD, ROAD, C_ASPHALT, o = false)
    }
}

private fun drawMarkings(c: Canvas, ink: Ink) {
    // dashed centre lines
    for (k in 0..COLS) {
        val x = k * (BW + ROAD) + ROAD / 2f
        ink.line(c, x, 0f, x, H, C_CENTER, 3f, dashed = true)
    }
    for (j in 0..ROWS) {
        val y = j * (BH + ROAD) + ROAD / 2f
        ink.line(c, 0f, y, W, y, C_CENTER, 3f, dashed = true)
    }
    // zebra crossings at the intersections
    for (k in 0..COLS) for (j in 0..ROWS) {
        val x0 = k * (BW + ROAD); val y0 = j * (BH + ROAD)
        for (i in 0 until 5) {
            if (j > 0) ink.rect(c, x0 + 10f + i * 18f, y0 - 24f, 9f, 18f, C_ZEBRA, o = false)
            if (j < ROWS) ink.rect(c, x0 + 10f + i * 18f, y0 + ROAD + 6f, 9f, 18f, C_ZEBRA, o = false)
            if (k > 0) ink.rect(c, x0 - 24f, y0 + 10f + i * 18f, 18f, 9f, C_ZEBRA, o = false)
            if (k < COLS) ink.rect(c, x0 + ROAD + 6f, y0 + 10f + i * 18f, 18f, 9f, C_ZEBRA, o = false)
        }
    }
}

private fun drawBlock(c: Canvas, ink: Ink, b: Block, mood: String, paper: Boolean) {
    val x = b.x.toFloat(); val y = b.y.toFloat()
    val sc = if (paper) C_EDGE else 0
    val sw = if (paper) 2f else 0f
    if (paper) ink.rect(c, x + 3f, y + 5f, BW, BH, C_BLOCK_SHADOW, if (b.type == BlockType.PARK) 26f else 14f, o = false)
    when (b.type) {
        BlockType.PARK -> {
            ink.rect(c, x, y, BW, BH, C_PARK, 26f, o = false, sc = sc, sw = sw)
            var tufts = false
            ink.path.rewind()
            for (d in b.dots) if (d.shape == "tuft") {
                val dx = f1(d.x.toFloat()); val dy = f1(d.y.toFloat())
                ink.path.moveTo(dx, dy); ink.path.rLineTo(-2f, -5f)
                ink.path.moveTo(dx, dy); ink.path.rLineTo(0f, -6f)
                ink.path.moveTo(dx, dy); ink.path.rLineTo(2f, -5f)
                tufts = true
            }
            if (tufts) ink.strokePath(c, C_TUFT, 1.2f, round = true)
            val p = b.pond
            if (p != null) {
                val cx = f1(p.cx.toFloat()); val cy = f1(p.cy.toFloat())
                val rx = p.rx.toFloat(); val ry = p.ry.toFloat()
                ink.oval(c, cx, cy, rx + 5f, ry + 5f, C_SAND, o = false)
                ink.oval(c, cx, cy, rx, ry, C_WATER, o = false, sc = C_WHITE, sw = 3f)
                ink.path.rewind()
                ink.path.moveTo(f1(cx - 40f), f1(cy - 10f)); ink.q(10f, -5f, 20f, 0f)
                ink.path.moveTo(f1(cx + 14f), f1(cy + 16f)); ink.q(10f, -5f, 20f, 0f)
                ink.path.moveTo(f1(cx + 20f), f1(cy - 20f)); ink.q(8f, -4f, 16f, 0f)
                ink.strokePath(c, C_WHITE_80, 1.6f)
                ink.oval(c, f1(cx - 20f), f1(cy + 4f), 10f, 5f, C_LILY, o = false)
                ink.circle(c, f1(cx - 22f), f1(cy + 2f), 2f, C_LILY_FLOWER, o = false)
            }
            for (d in b.dots) if (d.shape == "circle") {
                ink.circle(c, f1(d.x.toFloat()), f1(d.y.toFloat()), d.r.toFloat(), ink.col(d.color), o = false)
            }
        }
        BlockType.MARKET -> {
            ink.rect(c, x, y, BW, BH, C_MARKET, 14f, o = false, sc = sc, sw = sw)
            for (d in b.dots) ink.circle(c, f1(d.x.toFloat()), f1(d.y.toFloat()), d.r.toFloat(), ink.col(d.color), o = false)
        }
        BlockType.PLAZA -> {
            val cx = x + BW / 2f; val cy = y + BH / 2f
            ink.rect(c, x, y, BW, BH, C_PLAZA, 14f, o = false, sc = sc, sw = sw)
            ink.path.rewind()
            for (i in 1 until 8) { ink.path.moveTo(x + i * BW / 8f, y); ink.path.rLineTo(0f, BH) }
            for (j in 1 until 7) { ink.path.moveTo(x, y + j * BH / 7f); ink.path.rLineTo(BW, 0f) }
            ink.strokePath(c, C_PLAZA_LINE, 2f)
            ink.oval(c, cx + 2f, cy + 6f, 58f, 42f, C_FOUNTAIN_SHADOW, o = false)
            ink.oval(c, cx, cy, 56f, 40f, C_STONE, o = false, sc = C_WHITE, sw = 2f)
            ink.oval(c, cx, cy, 47f, 32f, C_WATER, o = false)
            ink.path.rewind()
            ink.path.moveTo(cx - 34f, cy + 8f); ink.q(8f, -4f, 16f, 0f)
            ink.path.moveTo(cx + 14f, cy + 16f); ink.q(8f, -4f, 16f, 0f)
            ink.path.moveTo(cx + 22f, cy - 4f); ink.q(6f, -3f, 12f, 0f)
            ink.strokePath(c, C_WHITE_80, 1.6f)
            ink.oval(c, cx, cy + 2f, 15f, 9f, C_STONE_DARK, o = false, sc = C_WHITE, sw = 1.6f)
            ink.rect(c, cx - 4f, cy - 26f, 8f, 26f, C_STONE_MID, o = false, sc = C_WHITE, sw = 1.4f)
            ink.oval(c, cx, cy - 24f, 19f, 7f, C_STONE, o = false, sc = C_WHITE, sw = 1.6f)
            ink.oval(c, cx, cy - 25f, 14f, 4.6f, C_BASIN, o = false)
            ink.path.rewind()
            ink.path.moveTo(cx, cy - 25f); ink.q(0f, -14f, 0f, -18f)
            ink.path.moveTo(cx, cy - 25f); ink.q(-9f, -12f, -15f, -4f)
            ink.path.moveTo(cx, cy - 25f); ink.q(9f, -12f, 15f, -4f)
            ink.strokePath(c, C_SPRAY, 2.4f, round = true)
            ink.circle(c, cx - 17f, cy - 22f, 1.6f, C_WHITE, o = false)
            ink.circle(c, cx + 17f, cy - 22f, 1.6f, C_WHITE, o = false)
            ink.circle(c, cx, cy - 46f, 1.8f, C_WHITE, o = false)
        }
        BlockType.HOUSES -> {
            if (paper) ink.rect(c, x, y, BW, BH, C_HOUSE_GRASS, 14f, o = false, sc = sc, sw = sw)
            ink.rect(c, x, y + BH - 78f, BW, 78f, C_SIDEWALK, 10f, o = false)
            ink.path.rewind()
            for (i in 0 until 12) { ink.path.moveTo(x + 10f + i * 35f, y + BH - 78f); ink.path.rLineTo(0f, 78f) }
            ink.strokePath(c, C_PLAZA_LINE, 1.5f)
            if (!paper) for (hd in b.houses) ink.house(c, hd, mood)
        }
    }
}
