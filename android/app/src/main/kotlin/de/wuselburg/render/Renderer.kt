package de.wuselburg.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Picture
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import de.wuselburg.core.Block
import de.wuselburg.core.BlockType
import de.wuselburg.core.House
import de.wuselburg.core.Scene
import de.wuselburg.core.WORLD_H
import de.wuselburg.core.WORLD_W
import de.wuselburg.core.WorldLayout
import kotlin.math.min

private val GROUND = rgb(0xbfe3a4)
private val ROAD_COLOR = rgb(0xdcd3c0)
private val ROAD_LINE = rgba(0xffffff, .7f)
private val WHITE = rgb(0xffffff)

/** Records the whole world (0..WORLD_W x 0..WORLD_H, world coordinates) ONCE per level. */
fun recordScenePicture(scene: Scene): Picture {
    val picture = Picture()
    val canvas = picture.beginRecording(WORLD_W.toInt(), WORLD_H.toInt())
    try {
        drawScene(canvas, scene)
    } finally {
        picture.endRecording()
    }
    return picture
}

/** Renders the scene straight into a software bitmap [widthPx] wide (aspect of the world). Used by tests. */
fun renderSceneToBitmap(scene: Scene, widthPx: Int): Bitmap {
    val k = widthPx / WORLD_W.toFloat()
    val bitmap = Bitmap.createBitmap(widthPx, (WORLD_H * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(k, k)
    drawScene(canvas, scene)
    return bitmap
}

/** Layer order of game.js: ground, roads, blocks (+houses), crumbs, then drawables in painter order. */
internal fun drawScene(c: Canvas, scene: Scene) {
    val pen = Pen.get()
    c.drawColor(GROUND)
    drawRoads(c, pen)
    for (b in scene.blocks) drawBlock(c, pen, b)
    for (cr in scene.crumbs) {
        if (cr.big) pen.circle(c, cr.x.toFloat(), cr.y.toFloat(), 3.4f, rgb(0xc98a2b))
        else pen.circle(c, cr.x.toFloat(), cr.y.toFloat(), 2.4f, rgb(0xe0a845))
    }
    for (d in scene.drawables) drawDrawable(c, d)
}

private fun drawRoads(c: Canvas, pen: Pen) {
    val pitch = (WorldLayout.BLOCK_W + WorldLayout.ROAD).toFloat()
    val pitchY = (WorldLayout.BLOCK_H + WorldLayout.ROAD).toFloat()
    val road = WorldLayout.ROAD.toFloat()
    val w = WORLD_W.toFloat(); val h = WORLD_H.toFloat()
    val dash = DashPathEffect(floatArrayOf(26f, 22f), 0f)
    pen.linePaint.pathEffect = dash
    for (k in 0..WorldLayout.COLS) {
        val x = k * pitch
        pen.rect(c, x, 0f, road, h, ROAD_COLOR)
        pen.line(c, x + road / 2, 0f, x + road / 2, h, ROAD_LINE, 3f)
    }
    for (j in 0..WorldLayout.ROWS) {
        val y = j * pitchY
        pen.rect(c, 0f, y, w, road, ROAD_COLOR)
        pen.line(c, 0f, y + road / 2, w, y + road / 2, ROAD_LINE, 3f)
    }
    pen.linePaint.pathEffect = null
    // Intersections hide the dashes
    for (k in 0..WorldLayout.COLS) for (j in 0..WorldLayout.ROWS) pen.rect(c, k * pitch, j * pitchY, road, road, ROAD_COLOR)
}

private fun drawBlock(c: Canvas, pen: Pen, b: Block) {
    val x = b.rect.x.toFloat(); val y = b.rect.y.toFloat()
    val w = b.rect.w.toFloat(); val h = b.rect.h.toFloat()
    when (b.type) {
        BlockType.PARK -> {
            pen.rect(c, x, y, w, h, rgb(0x8fcf7a), 26f)
            b.pond?.let {
                pen.oval(c, it.cx.toFloat(), it.cy.toFloat(), it.rx.toFloat(), it.ry.toFloat(), rgb(0x9fd8f0))
                pen.ovalStroke(c, it.cx.toFloat(), it.cy.toFloat(), it.rx.toFloat(), it.ry.toFloat(), WHITE, 4f)
                pen.oval(c, it.cx.toFloat() - 20f, it.cy.toFloat() + 4f, 10f, 5f, rgb(0x5fae5b))
            }
            for (d in b.dots) pen.circle(c, d.x.toFloat(), d.y.toFloat(), 2.2f, d.color)
        }
        BlockType.MARKET -> {
            pen.rect(c, x, y, w, h, rgb(0xf0dcae), 14f)
            for (d in b.dots) pen.circle(c, d.x.toFloat(), d.y.toFloat(), 3f, d.color)
        }
        BlockType.PLAZA -> {
            pen.rect(c, x, y, w, h, rgb(0xe8e1d2), 14f)
            b.pond?.let {
                val cx = it.cx.toFloat(); val cy = it.cy.toFloat()
                pen.circle(c, cx, cy, 62f, rgb(0xb9e3f2))
                pen.circleStroke(c, cx, cy, 62f, WHITE, 8f)
                pen.circle(c, cx, cy, 14f, rgb(0x8cc9e0))
            }
        }
        BlockType.HOUSES -> {
            pen.rect(c, x, y, w, h, rgb(0xd3e7b9), 14f)
            pen.rect(c, x, y + h - 78f, w, 78f, rgb(0xe6dfcf), 10f)
            for (house in b.houses) drawHouse(c, pen, house)
        }
    }
}

private fun drawHouse(c: Canvas, pen: Pen, hs: House) {
    val x = hs.x.toFloat(); val base = hs.baseY.toFloat()
    val w = hs.w.toFloat(); val h = hs.h.toFloat()
    pen.rect(c, x, base - h, w, h, hs.wall)
    pen.poly(c, hs.roof, x - 7f, base - h, x + w / 2, base - h - 46f, x + w + 7f, base - h)
    val floors = if (hs.h > 150) 2 else 1
    val glass = rgb(0xbfe6f5)
    var idx = 0
    for (f in 0 until floors) for (col in 0..1) {
        val wx = x + 16f + col * (w - 32f - 24f)
        val wy = base - h + 16f + f * 56f
        pen.rect(c, wx, wy, 24f, 28f, glass, 3f)
        pen.rectStroke(c, wx, wy, 24f, 28f, WHITE, 3f, 3f)
        pen.line(c, wx + 12f, wy, wx + 12f, wy + 28f, WHITE, 2f)
        pen.line(c, wx, wy + 14f, wx + 24f, wy + 14f, WHITE, 2f)
        val curtain = hs.curtains.getOrElse(idx++) { 0 }
        if (curtain != 0) pen.rect(c, wx + 2f, wy + 2f, 9f, 24f, (curtain and 0xFFFFFF) or (0xCC shl 24))
    }
    pen.rect(c, x + w / 2 - 13f, base - 42f, 26f, 42f, if (hs.bakery) rgb(0x7a4e2d) else rgb(0x8a5a3b), 3f)
    pen.circle(c, x + w / 2 + 7f, base - 20f, 2f, rgb(0xffd84a))
    if (hs.bakery) {
        val sw = w / 8f
        for (i in 0 until 8) pen.rect(c, x + i * sw, base - 62f, sw, 16f, if (i % 2 == 1) WHITE else rgb(0xe45b4b))
        pen.rect(c, x + 6f, base - h + 66f, w - 12f, 22f, rgb(0xfff6dc), 4f)
        pen.rectStroke(c, x + 6f, base - h + 66f, w - 12f, 22f, rgb(0xc99a62), 2f, 4f)
        pen.text(c, "Bäckerei", x + w / 2, base - h + 82f, 14f, rgb(0x8a4b1c))
    }
}

/** Figures drawn as small icons in the UI (goal bar, dialogs, menu). */
enum class IconKind { FIPS, RACCOON_CAKE, CAKE, MAGNIFIER }

/** Icon viewBox, same as the web version: -30 -70 60 75. */
private const val VB_X = -30f
private const val VB_Y = -70f
private const val VB_W = 60f
private const val VB_H = 75f

@Composable
fun FigureIcon(kind: IconKind, modifier: Modifier) {
    ComposeCanvas(modifier) {
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            val s = min(size.width / VB_W, size.height / VB_H)
            if (s <= 0f) return@drawIntoCanvas
            c.save()
            c.translate((size.width - VB_W * s) / 2f - VB_X * s, (size.height - VB_H * s) / 2f - VB_Y * s)
            c.scale(s, s)
            drawIcon(c, kind)
            c.restore()
        }
    }
}

internal fun drawIcon(c: Canvas, kind: IconKind) {
    val pen = Pen.get()
    when (kind) {
        IconKind.FIPS -> pen.fox(c, de.wuselburg.core.FoxStyle.FIPS)
        IconKind.RACCOON_CAKE -> pen.raccoon(c, true)
        IconKind.CAKE -> {
            pen.oval(c, 0f, -4f, 26f, 5f, rgb(0xe6dfcf))
            pen.rect(c, -20f, -30f, 40f, 24f, rgb(0xf7c6d9), 5f)
            pen.rect(c, -20f, -38f, 40f, 12f, WHITE, 6f)
            for (i in -1..1) pen.rect(c, i * 11f - 1.5f, -26f, 3f, 8f, WHITE, 1.5f)
            pen.rect(c, -1.5f, -52f, 3f, 14f, rgb(0xf2b84b), 1f)
            pen.oval(c, 0f, -55f, 2.6f, 3.6f, rgb(0xffd84a))
            pen.circle(c, 0f, -42f, 3.2f, rgb(0xd9433b))
        }
        IconKind.MAGNIFIER -> {
            pen.path.rewind(); pen.path.moveTo(8f, -18f); pen.path.lineTo(24f, -2f)
            pen.strokePath(c, rgb(0x8a5a3b), 8f, round = true)
            pen.circle(c, -5f, -34f, 20f, rgba(0xbfe6f5, .55f))
            pen.circleStroke(c, -5f, -34f, 20f, rgb(0x176c68), 5f)
            pen.oval(c, -12f, -42f, 5f, 3f, rgba(0xffffff, .7f), -35f)
        }
    }
}
