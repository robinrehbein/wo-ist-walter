package de.wuselburg.render

import android.graphics.Canvas
import de.wuselburg.core.CatD
import de.wuselburg.core.Drawable
import de.wuselburg.core.DogD
import de.wuselburg.core.FoxD
import de.wuselburg.core.FoxStyle
import de.wuselburg.core.HatStyle
import de.wuselburg.core.PersonD
import de.wuselburg.core.PigeonD
import de.wuselburg.core.RaccoonD
import de.wuselburg.core.StallD
import de.wuselburg.core.TreeD

/*
 * Ports of fox(), raccoon(), person(), dog(), cat(), pigeon(), treeSvg(), stallSvg() from game.js.
 * Every figure is authored around its own origin (the feet) exactly like the SVG snippets;
 * the public draw functions place it with translate + scale (negative x scale = flip).
 */

internal val TEAL = rgb(0x1fa6a0)
private val SCARF_RED = rgb(0xd9433b)
private val FOX_ORANGE = rgb(0xe8802a)
private val FOX_GREY = rgb(0x9a9aa6)
private val BELLY = rgb(0xfff4e0)
private val WHITE = rgb(0xffffff)
private val INK = rgb(0x222222)

private inline fun Canvas.placed(x: Double, y: Double, scale: Double, flip: Boolean, block: () -> Unit) {
    save()
    translate(x.toFloat(), y.toFloat())
    val s = scale.toFloat()
    scale(if (flip) -s else s, s)
    block()
    restore()
}

fun drawFox(canvas: Canvas, x: Double, y: Double, scale: Double, flip: Boolean, style: FoxStyle) =
    canvas.placed(x, y, scale, flip) { Pen.get().fox(canvas, style) }

fun drawRaccoon(canvas: Canvas, x: Double, y: Double, scale: Double, flip: Boolean, hasCake: Boolean) =
    canvas.placed(x, y, scale, flip) { Pen.get().raccoon(canvas, hasCake) }

fun drawPerson(canvas: Canvas, d: PersonD) =
    canvas.placed(d.x, d.y, d.scale, d.flip) { Pen.get().person(canvas, d) }

fun drawDog(canvas: Canvas, x: Double, y: Double, scale: Double, flip: Boolean, color: Int) =
    canvas.placed(x, y, scale, flip) { Pen.get().dog(canvas, color) }

fun drawCat(canvas: Canvas, x: Double, y: Double, scale: Double, flip: Boolean, color: Int) =
    canvas.placed(x, y, scale, flip) { Pen.get().cat(canvas, color) }

fun drawPigeon(canvas: Canvas, x: Double, y: Double, scale: Double, flip: Boolean) =
    canvas.placed(x, y, scale, flip) { Pen.get().pigeon(canvas) }

fun drawTree(canvas: Canvas, x: Double, y: Double, scale: Double, flip: Boolean, color: Int) =
    canvas.placed(x, y, scale, flip) { Pen.get().tree(canvas, color) }

fun drawStall(canvas: Canvas, x: Double, y: Double, scale: Double, flip: Boolean, color: Int) =
    canvas.placed(x, y, scale, flip) { Pen.get().stall(canvas, color) }

/** Dispatches on the drawable type. */
fun drawDrawable(canvas: Canvas, d: Drawable) {
    when (d) {
        is PersonD -> drawPerson(canvas, d)
        is DogD -> drawDog(canvas, d.x, d.y, d.scale, d.flip, d.color)
        is CatD -> drawCat(canvas, d.x, d.y, d.scale, d.flip, d.color)
        is PigeonD -> drawPigeon(canvas, d.x, d.y, d.scale, d.flip)
        is FoxD -> drawFox(canvas, d.x, d.y, d.scale, d.flip, d.style)
        is RaccoonD -> drawRaccoon(canvas, d.x, d.y, d.scale, d.flip, d.hasCake)
        is TreeD -> drawTree(canvas, d.x, d.y, d.scale, d.flip, d.color)
        is StallD -> drawStall(canvas, d.x, d.y, d.scale, d.flip, d.color)
    }
}

internal fun Pen.fox(c: Canvas, style: FoxStyle) {
    val fur = if (style == FoxStyle.GREY) FOX_GREY else FOX_ORANGE
    val scarf = when (style) {
        FoxStyle.NO_SCARF -> 0
        FoxStyle.RED_SCARF -> SCARF_RED
        else -> TEAL
    }
    val dots = style == FoxStyle.FIPS || style == FoxStyle.RED_SCARF || style == FoxStyle.NO_TIP || style == FoxStyle.GREY

    oval(c, 16f, -24f, 6f, 14f, fur, 32f)
    if (style != FoxStyle.NO_TIP) oval(c, 22.5f, -35.5f, 3.6f, 4.6f, WHITE, 32f)
    val leg = rgb(0x6b3a1d)
    rect(c, -8f, -9f, 5f, 9f, leg, 2f); rect(c, 3f, -9f, 5f, 9f, leg, 2f)
    oval(c, 0f, -20f, 10f, 13f, fur)
    oval(c, 0f, -18f, 6f, 9f, BELLY)
    poly(c, fur, -9f, -43f, -9f, -57f, -1f, -46f)
    poly(c, fur, 9f, -43f, 9f, -57f, 1f, -46f)
    val ear = rgb(0x5a3320)
    poly(c, ear, -7.6f, -46f, -7.6f, -53f, -3.4f, -47f)
    poly(c, ear, 7.6f, -46f, 7.6f, -53f, 3.4f, -47f)
    oval(c, 0f, -39f, 10.5f, 8.5f, fur)
    path.rewind(); path.moveTo(-7f, -38f); path.quadTo(0f, -26f, 7f, -38f); path.close()
    fillPath(c, BELLY)
    circle(c, 0f, -32.5f, 1.9f, INK)
    circle(c, -4.2f, -40.5f, 1.5f, INK); circle(c, 4.2f, -40.5f, 1.5f, INK)
    if (scarf != 0) {
        rect(c, 3f, -30f, 5.5f, 12f, scarf, 2.4f)
        rect(c, -10.5f, -33.5f, 21f, 6f, scarf, 3f)
        if (dots) {
            val yellow = rgb(0xffd84a)
            circle(c, -6.5f, -30.5f, 1.3f, yellow); circle(c, -1f, -30.5f, 1.3f, yellow)
            circle(c, 4.5f, -30.5f, 1.3f, yellow)
            circle(c, 5.7f, -25.5f, 1.2f, yellow); circle(c, 5.7f, -21f, 1.2f, yellow)
        }
    }
}

internal fun Pen.raccoon(c: Canvas, cake: Boolean) {
    val fur = rgb(0x8d8d99); val dark = rgb(0x3a3a45); val light = rgb(0xcfcfd6)
    oval(c, 16f, -22f, 6f, 14f, fur, 32f)
    oval(c, 13f, -17f, 6f, 2.4f, dark, 32f)
    oval(c, 18f, -27f, 5.6f, 2.4f, dark, 32f)
    rect(c, -8f, -9f, 5f, 9f, dark, 2f); rect(c, 3f, -9f, 5f, 9f, dark, 2f)
    oval(c, 0f, -20f, 10f, 13f, fur)
    oval(c, 0f, -18f, 6f, 9f, light)
    circle(c, -8f, -45f, 4f, fur); circle(c, 8f, -45f, 4f, fur)
    oval(c, 0f, -39f, 10.5f, 8.5f, light)
    path.rewind(); path.moveTo(-10.5f, -42f); path.quadTo(0f, -47f, 10.5f, -42f)
    path.lineTo(9f, -37f); path.quadTo(0f, -40f, -9f, -37f); path.close()
    fillPath(c, dark)
    circle(c, -4f, -40f, 1.6f, WHITE); circle(c, 4f, -40f, 1.6f, WHITE)
    circle(c, -4f, -40f, 0.8f, 0xFF000000.toInt()); circle(c, 4f, -40f, 0.8f, 0xFF000000.toInt())
    oval(c, 0f, -34f, 2.4f, 1.8f, dark)
    if (cake) {
        c.save(); c.translate(-1f, -17f)
        rect(c, -8f, -6f, 16f, 9f, rgb(0xf7c6d9), 2f)
        rect(c, -8f, -9f, 16f, 4.5f, WHITE, 2.2f)
        circle(c, 0f, -11.5f, 2.2f, SCARF_RED)
        c.restore()
    }
}

internal fun Pen.person(c: Canvas, p: PersonD) {
    rect(c, -6.5f, -12f, 5.5f, 12f, p.pants, 2f); rect(c, 1f, -12f, 5.5f, 12f, p.pants, 2f)
    rect(c, -11f, -34f, 4.2f, 17f, p.shirt, 2f); rect(c, 6.8f, -34f, 4.2f, 17f, p.shirt, 2f)
    rect(c, -8f, -36f, 16f, 26f, p.shirt, 6f)
    circle(c, 0f, -43.5f, 7.5f, p.skin)
    if (p.hat == HatStyle.NONE) {
        path.rewind(); path.moveTo(-7.5f, -43.5f); domeTo(-7.5f, -51f, 7.5f, -36f)
        path.quadTo(0f, -47f, -7.5f, -43.5f); path.close()
        fillPath(c, p.hair)
    }
    circle(c, -2.6f, -42.5f, 1f, INK); circle(c, 2.6f, -42.5f, 1f, INK)
    val hc = p.hatColor
    when (p.hat) {
        HatStyle.CAP -> {
            path.rewind(); path.moveTo(-8f, -45.5f); domeTo(-8f, -53.5f, 8f, -37.5f); path.close()
            fillPath(c, hc)
            rect(c, 2f, -47f, 9.5f, 2.6f, hc, 1.2f)
        }
        HatStyle.BEANIE -> {
            path.rewind(); path.moveTo(-8f, -45f); domeTo(-8f, -54.5f, 8f, -35.5f); path.close()
            fillPath(c, hc)
            circle(c, 0f, -54f, 2.2f, WHITE)
        }
        HatStyle.TOP -> {
            rect(c, -9.5f, -50f, 19f, 3f, INK, 1.5f)
            rect(c, -6f, -60f, 12f, 11f, INK, 1.5f)
            rect(c, -6f, -53f, 12f, 2.6f, hc)
        }
        HatStyle.NONE -> Unit
    }
    if (p.balloonColor != 0) {
        path.rewind(); path.moveTo(11f, -27f); path.quadTo(18f, -50f, 14f, -72f)
        strokePath(c, rgb(0x777777), 0.8f)
        oval(c, 14f, -80f, 7f, 9f, p.balloonColor)
    }
}

internal fun Pen.dog(c: Canvas, col: Int) {
    path.rewind(); path.moveTo(14f, -14f); path.quadTo(22f, -22f, 19f, -26f)
    strokePath(c, col, 3.5f, round = true)
    rect(c, -12f, -9f, 4f, 9f, col, 2f); rect(c, 8f, -9f, 4f, 9f, col, 2f)
    oval(c, 0f, -15f, 15f, 8f, col)
    circle(c, -14f, -22f, 7f, col)
    oval(c, -19.5f, -20f, 4f, 3f, rgb(0xf3e6d3))
    circle(c, -12f, -23.5f, 1.1f, rgb(0x111111))
    path.rewind(); path.moveTo(-12f, -27f); path.rQuadTo(-3f, 6f, -6f, 3f)
    fillPath(c, rgb(0x4a2f17))
}

internal fun Pen.cat(c: Canvas, col: Int) {
    path.rewind(); path.moveTo(12f, -10f); path.quadTo(24f, -16f, 18f, -30f)
    strokePath(c, col, 3f, round = true)
    oval(c, 0f, -10f, 12f, 7.5f, col)
    circle(c, -11f, -17f, 6f, col)
    path.rewind()
    path.moveTo(-16f, -21f); path.lineTo(-15f, -27f); path.lineTo(-11f, -22f); path.close()
    path.moveTo(-7f, -22f); path.lineTo(-6f, -27f); path.lineTo(-3f, -20f); path.close()
    fillPath(c, col)
    val eye = rgb(0x9be07a)
    circle(c, -13f, -17.5f, 1f, eye); circle(c, -9f, -17.5f, 1f, eye)
}

internal fun Pen.pigeon(c: Canvas) {
    val orange = rgb(0xe8a33c); val wing = rgb(0x7f8594)
    oval(c, 0f, -6f, 7f, 5f, rgb(0x9aa0ad))
    circle(c, -6f, -10f, 3.2f, wing)
    poly(c, orange, -9f, -10f, -12f, -9f, -9f, -8f)
    poly(c, wing, 5f, -5f, 13f, -3f, 5f, -1f)
    line(c, -2f, 0f, -2f, 3f, orange, 1f); line(c, 2f, 0f, 2f, 3f, orange, 1f)
}

internal fun Pen.tree(c: Canvas, col: Int) {
    oval(c, 0f, 2f, 20f, 5f, rgba(0x000000, .14f))
    rect(c, -4f, -34f, 8f, 34f, rgb(0x7a4e2d), 3f)
    circle(c, -20f, -48f, 18f, col); circle(c, 20f, -48f, 18f, col)
    circle(c, 0f, -62f, 26f, col)
    circle(c, -8f, -68f, 9f, rgba(0xffffff, .14f))
}

internal fun Pen.stall(c: Canvas, col: Int) {
    oval(c, 0f, 4f, 52f, 7f, rgba(0x000000, .12f))
    val post = rgb(0x8a6a45)
    rect(c, -42f, -44f, 5f, 44f, post); rect(c, 37f, -44f, 5f, 44f, post)
    rect(c, -42f, -24f, 84f, 24f, rgb(0xc99a62), 3f)
    for (i in 0 until 6) rect(c, -45f + i * 15f, -62f, 15f, 18f, if (i % 2 == 1) WHITE else col)
    path.rewind(); path.moveTo(-45f, -44f)
    repeat(6) { path.rQuadTo(7.5f, 10f, 15f, 0f) }
    path.close()
    fillPath(c, col)
    val red = rgb(0xe45b4b); val yellow = rgb(0xf2b84b)
    circle(c, -24f, -28f, 4.5f, red); circle(c, -12f, -28f, 4.5f, yellow); circle(c, 2f, -28f, 4.5f, rgb(0x7bc47f))
    circle(c, 16f, -28f, 4.5f, red); circle(c, 29f, -28f, 4.5f, yellow)
}
