package de.wuselburg.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import de.wuselburg.core.Drawable

/*
 * Port of fox(), raccoon(), dogSvg(), catSvg(), pigeonSvg(), duckSvg() (and personSvg(), see Person.kt)
 * from scene.js. Every figure is authored around its own origin (the feet) exactly like the SVG
 * snippets; the caller (Renderer) applies translate(x,y) + scale(flip ? -s : s, s) first.
 *
 * Inside an OUTLINE group (`<g stroke="rgba(30,20,40,.32)" stroke-width=".7" stroke-linejoin="round">`)
 * every shape gets fill + soft outline unless the JS element says stroke="none" (-> st = false).
 */

/** Draws kinds person, dog, cat, pigeon, duck, fox, raccoon in local coordinates; false for other kinds. */
fun drawFigure(canvas: Canvas, d: Drawable, mood: String): Boolean {
    val f = Fig.get()
    when (d.kind) {
        "person" -> f.person(canvas, d)
        "dog" -> f.dog(canvas, d)
        "cat" -> f.cat(canvas, d)
        "pigeon" -> f.pigeon(canvas)
        "duck" -> f.duck(canvas)
        "fox" -> f.fox(canvas, FoxLook.of(figStr(d, "style", "fips")))
        "raccoon" -> f.raccoon(canvas, figBool(d, "hasCake", false))
        else -> return false
    }
    return true
}

/** Fox by style name (fips, noScarf, redScarf, plainScarf, noTip, grey); used by FigureIcon. */
fun drawFoxStyle(canvas: Canvas, styleName: String) = Fig.get().fox(canvas, FoxLook.of(styleName))

/** Raccoon, optionally with the cake in its arms; used by FigureIcon. */
fun drawRaccoonFigure(canvas: Canvas, hasCake: Boolean) = Fig.get().raccoon(canvas, hasCake)

// ---------------------------------------------------------------- prop access (robust against typing)

internal fun figStr(d: Drawable, key: String, def: String): String = (d.props[key] as? String) ?: def
internal fun figBool(d: Drawable, key: String, def: Boolean): Boolean = (d.props[key] as? Boolean) ?: def
internal fun figNum(d: Drawable, key: String, def: Double): Double = (d.props[key] as? Number)?.toDouble() ?: def

// ---------------------------------------------------------------- drawing toolkit

internal class Fig {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(82, 30, 20, 40) // rgba(30,20,40,.32)
        strokeWidth = 0.7f
        strokeJoin = Paint.Join.ROUND
    }
    val path = Path()
    private val box = RectF()
    private val colors = HashMap<String, Int>()

    /** True while inside `<g OUTLINE>`: shapes get the soft outline unless st = false. */
    var outline = false

    /** '#rrggbb' -> ARGB, parsed once. */
    fun c(hex: String): Int = colors.getOrPut(hex) {
        try { Color.parseColor(hex) } catch (e: IllegalArgumentException) { 0xFF888888.toInt() }
    }

    private fun fillWith(color: Int): Paint = fillPaint.also { it.color = color }

    // --- filled shapes (+ outline)

    fun ell(cx: Float, cy: Float, rx: Float, ry: Float, fill: Int, rot: Float = 0f, st: Boolean = true) {
        box.set(cx - rx, cy - ry, cx + rx, cy + ry)
        val k = cv
        if (rot != 0f) { k.save(); k.rotate(rot, cx, cy) }
        k.drawOval(box, fillWith(fill))
        if (outline && st) k.drawOval(box, outlinePaint)
        if (rot != 0f) k.restore()
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float, fill: Int, st: Boolean = true) {
        box.set(x, y, x + w, y + h)
        cv.drawRoundRect(box, rx, rx, fillWith(fill))
        if (outline && st) cv.drawRoundRect(box, rx, rx, outlinePaint)
    }

    fun circ(cx: Float, cy: Float, r: Float, fill: Int, st: Boolean = true) {
        cv.drawCircle(cx, cy, r, fillWith(fill))
        if (outline && st) cv.drawCircle(cx, cy, r, outlinePaint)
    }

    /** Fills [path] (and outlines it). */
    fun pathFill(fill: Int, st: Boolean = true) {
        cv.drawPath(path, fillWith(fill))
        if (outline && st) cv.drawPath(path, outlinePaint)
    }

    // --- own strokes (fill="none" with explicit stroke: the outline does not apply)

    private fun lineWith(color: Int, w: Float, round: Boolean): Paint = linePaint.also {
        it.color = color; it.strokeWidth = w
        it.strokeCap = if (round) Paint.Cap.ROUND else Paint.Cap.BUTT
        it.strokeJoin = Paint.Join.MITER
    }

    fun pathStroke(color: Int, w: Float, round: Boolean = false) = cv.drawPath(path, lineWith(color, w, round))

    fun lineS(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, w: Float) =
        cv.drawLine(x1, y1, x2, y2, lineWith(color, w, false))

    fun circS(cx: Float, cy: Float, r: Float, color: Int, w: Float) =
        cv.drawCircle(cx, cy, r, lineWith(color, w, false))

    // --- path helpers

    fun poly(vararg p: Float) {
        path.rewind()
        path.moveTo(p[0], p[1])
        var i = 2
        while (i < p.size) { path.lineTo(p[i], p[i + 1]); i += 2 }
        path.close()
    }

    /** Appends an SVG half-circle/ellipse arc over the top ("A rx ry 0 0 1") from the current point (left) to the right end. */
    fun domeTo(left: Float, top: Float, right: Float, bottom: Float) {
        box.set(left, top, right, bottom)
        path.arcTo(box, 180f, 180f)
    }

    // --- canvas binding (set per figure call)

    private var canvasOrNull: Canvas? = null
    val cv: Canvas get() = canvasOrNull!!

    fun begin(canvas: Canvas) { canvasOrNull = canvas; outline = false }
    fun end() { canvasOrNull = null; outline = false }

    fun shadow(rx: Float, ry: Float, color: Int) = ell(0f, 1f, rx, ry, color)

    companion object {
        private val local = ThreadLocal.withInitial { Fig() }
        fun get(): Fig = local.get()!!
        val SHADOW = Color.argb(33, 0, 0, 0)    // rgba(0,0,0,.13)
        val SHADOW_PIGEON = Color.argb(31, 0, 0, 0) // rgba(0,0,0,.12)
        val DUCK_SHADOW = Color.argb(89, 255, 255, 255) // rgba(255,255,255,.35)
        val CHEEK_FOX = Color.argb(89, 255, 120, 120)   // rgba(255,120,120,.35)
        val CHEEK_PERSON = Color.argb(89, 255, 110, 110) // rgba(255,110,110,.35)
        val STRIPE = Color.argb(77, 0, 0, 0)    // rgba(0,0,0,.3)
    }
}

internal inline fun Fig.figure(canvas: Canvas, body: Fig.() -> Unit) {
    begin(canvas)
    try { body() } finally { end() }
}

// ---------------------------------------------------------------- fox / raccoon

internal class FoxLook(val fur: Int, val scarf: Int, val dots: Boolean, val tip: Boolean) {
    companion object {
        private val TEAL = 0xFF1FA6A0.toInt()
        private val ORANGE = 0xFFE8802A.toInt()
        private val looks = mapOf(
            "fips" to FoxLook(ORANGE, TEAL, true, true),
            "noScarf" to FoxLook(ORANGE, 0, false, true),
            "redScarf" to FoxLook(ORANGE, 0xFFD9433B.toInt(), true, true),
            "plainScarf" to FoxLook(ORANGE, TEAL, false, true),
            "noTip" to FoxLook(ORANGE, TEAL, true, false),
            "grey" to FoxLook(0xFF9A9AA6.toInt(), TEAL, true, true),
        )
        fun of(name: String): FoxLook = looks[name] ?: looks.getValue("fips")
    }
}

internal fun Fig.fox(canvas: Canvas, o: FoxLook) = figure(canvas) {
    val fur = o.fur
    val belly = c("#fff4e0"); val ink = c("#222222"); val ear = c("#5a3320"); val leg = c("#6b3a1d")
    shadow(12f, 3f, Fig.SHADOW)
    outline = true
    ell(16f, -24f, 6f, 14f, fur, 32f)
    if (o.tip) ell(22.5f, -35.5f, 3.6f, 4.6f, 0xFFFFFFFF.toInt(), 32f)
    rect(-8f, -9f, 5f, 9f, 2f, leg); rect(3f, -9f, 5f, 9f, 2f, leg)
    ell(0f, -20f, 10f, 13f, fur); ell(0f, -18f, 6f, 9f, belly, st = false)
    poly(-9f, -43f, -9f, -57f, -1f, -46f); pathFill(fur)
    poly(9f, -43f, 9f, -57f, 1f, -46f); pathFill(fur)
    poly(-7.6f, -46f, -7.6f, -53f, -3.4f, -47f); pathFill(ear, st = false)
    poly(7.6f, -46f, 7.6f, -53f, 3.4f, -47f); pathFill(ear, st = false)
    ell(0f, -39f, 10.5f, 8.5f, fur)
    path.rewind(); path.moveTo(-7f, -38f); path.quadTo(0f, -26f, 7f, -38f); path.close(); pathFill(belly, st = false)
    circ(0f, -32.5f, 1.9f, ink, st = false)
    circ(-4.2f, -40.5f, 1.5f, ink, st = false); circ(4.2f, -40.5f, 1.5f, ink, st = false)
    circ(-7f, -37f, 1.5f, Fig.CHEEK_FOX, st = false); circ(7f, -37f, 1.5f, Fig.CHEEK_FOX, st = false)
    if (o.scarf != 0) {
        rect(3f, -30f, 5.5f, 12f, 2.4f, o.scarf)
        rect(-10.5f, -33.5f, 21f, 6f, 3f, o.scarf)
        if (o.dots) {
            val y = c("#ffd84a")
            circ(-6.5f, -30.5f, 1.3f, y, false); circ(-1f, -30.5f, 1.3f, y, false); circ(4.5f, -30.5f, 1.3f, y, false)
            circ(5.7f, -25.5f, 1.2f, y, false); circ(5.7f, -21f, 1.2f, y, false)
        }
    }
}

internal fun Fig.raccoon(canvas: Canvas, cake: Boolean) = figure(canvas) {
    val fur = c("#8d8d99"); val dark = c("#3a3a45"); val light = c("#cfcfd6"); val white = c("#ffffff")
    shadow(12f, 3f, Fig.SHADOW)
    outline = true
    ell(16f, -22f, 6f, 14f, fur, 32f)
    ell(13f, -17f, 6f, 2.4f, dark, 32f, st = false)
    ell(18f, -27f, 5.6f, 2.4f, dark, 32f, st = false)
    rect(-8f, -9f, 5f, 9f, 2f, dark); rect(3f, -9f, 5f, 9f, 2f, dark)
    ell(0f, -20f, 10f, 13f, fur); ell(0f, -18f, 6f, 9f, light, st = false)
    circ(-8f, -45f, 4f, fur); circ(8f, -45f, 4f, fur)
    ell(0f, -39f, 10.5f, 8.5f, light)
    path.rewind(); path.moveTo(-10.5f, -42f); path.quadTo(0f, -47f, 10.5f, -42f)
    path.lineTo(9f, -37f); path.quadTo(0f, -40f, -9f, -37f); path.close(); pathFill(dark, st = false)
    circ(-4f, -40f, 1.6f, white, false); circ(4f, -40f, 1.6f, white, false)
    val black = 0xFF000000.toInt()
    circ(-4f, -40f, .8f, black, false); circ(4f, -40f, .8f, black, false)
    ell(0f, -34f, 2.4f, 1.8f, dark, st = false)
    if (cake) {
        cv.save(); cv.translate(-1f, -17f)
        rect(-8f, -6f, 16f, 9f, 2f, c("#f7c6d9"))
        rect(-8f, -9f, 16f, 4.5f, 2.2f, white)
        circ(0f, -11.5f, 2.2f, c("#d9433b"))
        cv.restore()
    }
}

// ---------------------------------------------------------------- animals

internal fun Fig.dog(canvas: Canvas, d: Drawable) = figure(canvas) {
    val col = c(figStr(d, "color", "#a0703f"))
    val dark = c("#111111"); val cream = c("#f3e6d3")
    shadow(15f, 3f, Fig.SHADOW)
    outline = true
    when (figStr(d, "breed", "lab")) {
        "lab" -> {
            path.rewind(); path.moveTo(14f, -14f); path.quadTo(22f, -22f, 19f, -26f); pathStroke(col, 3.5f, true)
            rect(-12f, -9f, 4f, 9f, 2f, col); rect(8f, -9f, 4f, 9f, 2f, col)
            ell(0f, -15f, 15f, 8f, col); circ(-14f, -22f, 7f, col)
            ell(-19.5f, -20f, 4f, 3f, cream); circ(-12f, -23.5f, 1.1f, dark, false)
            path.rewind(); path.moveTo(-12f, -27f); path.rQuadTo(-3f, 6f, -6f, 3f); pathFill(c("#4a2f17"))
        }
        "dachs" -> {
            path.rewind(); path.moveTo(20f, -11f); path.quadTo(26f, -15f, 24f, -19f); pathStroke(col, 3f, true)
            rect(-17f, -6f, 4f, 6f, 2f, col); rect(-6f, -6f, 4f, 6f, 2f, col)
            rect(8f, -6f, 4f, 6f, 2f, col); rect(16f, -6f, 4f, 6f, 2f, col)
            ell(2f, -11f, 21f, 6.5f, col); circ(-20f, -15f, 6f, col)
            ell(-25f, -13.5f, 3.6f, 2.6f, cream); circ(-19f, -16.5f, 1f, dark, false)
            path.rewind(); path.moveTo(-17f, -20f); path.rQuadTo(4f, 2f, 3f, 9f); path.rQuadTo(-4f, -2f, -3f, -9f); path.close()
            pathFill(c("#3b2a18"))
        }
        "spot" -> {
            val w = c("#f5f1e8")
            path.rewind(); path.moveTo(14f, -15f); path.quadTo(22f, -23f, 19f, -27f); pathStroke(w, 3.5f, true)
            rect(-12f, -9f, 4f, 9f, 2f, w); rect(8f, -9f, 4f, 9f, 2f, w)
            ell(0f, -15f, 15f, 8f, w); circ(-14f, -22f, 7f, w)
            ell(-3f, -17f, 5f, 4f, col, st = false); ell(7f, -13f, 4f, 3.2f, col, st = false)
            ell(-17f, -25f, 3.4f, 4f, col, st = false)
            ell(-19.5f, -20f, 4f, 3f, c("#ffffff")); circ(-12f, -23.5f, 1.1f, dark, false)
        }
        else -> { // poodle
            val grey = c("#555555")
            rect(-9f, -9f, 2.6f, 9f, 0f, grey); rect(7f, -9f, 2.6f, 9f, 0f, grey)
            circ(-6f, -17f, 7.5f, col); circ(6f, -17f, 7.5f, col); circ(0f, -19f, 8f, col)
            circ(15f, -22f, 4f, col); circ(-14f, -25f, 6.5f, col); circ(-20f, -27f, 4.2f, col)
            circ(-9f, -29f, 4.2f, col)
            ell(-19f, -23f, 3f, 2.4f, c("#3a2a30")); circ(-14f, -26f, 1f, dark, false)
        }
    }
}

internal fun Fig.cat(canvas: Canvas, d: Drawable) = figure(canvas) {
    val col = c(figStr(d, "color", "#555555"))
    val eye = c("#9be07a")
    val stripes = figBool(d, "stripes", false)
    shadow(11f, 3f, Fig.SHADOW)
    outline = true
    if (figStr(d, "pose", "stand") == "stand") {
        path.rewind(); path.moveTo(12f, -10f); path.quadTo(24f, -16f, 18f, -30f); pathStroke(col, 3f, true)
        ell(0f, -10f, 12f, 7.5f, col); circ(-11f, -17f, 6f, col)
        path.rewind()
        path.moveTo(-16f, -21f); path.lineTo(-15f, -27f); path.lineTo(-11f, -22f); path.close()
        path.moveTo(-7f, -22f); path.lineTo(-6f, -27f); path.lineTo(-3f, -20f); path.close()
        pathFill(col)
        circ(-13f, -17.5f, 1f, eye, false); circ(-9f, -17.5f, 1f, eye, false)
        if (stripes) {
            path.rewind()
            path.moveTo(-4f, -17f); path.rLineTo(0f, 6f)
            path.moveTo(1f, -17f); path.rLineTo(0f, 7f)
            path.moveTo(6f, -16f); path.rLineTo(0f, 6f)
            pathStroke(Fig.STRIPE, 1.2f)
        }
    } else {
        path.rewind(); path.moveTo(7f, -3f); path.quadTo(18f, -3f, 16f, -12f); path.quadTo(15f, -16f, 11f, -12f)
        pathStroke(col, 3f, true)
        ell(0f, -10f, 8.5f, 10.5f, col); circ(0f, -26f, 6.6f, col)
        path.rewind()
        path.moveTo(-6.4f, -30f); path.lineTo(-5.6f, -36.5f); path.lineTo(-1.4f, -31f); path.close()
        path.moveTo(6.4f, -30f); path.lineTo(5.6f, -36.5f); path.lineTo(1.4f, -31f); path.close()
        pathFill(col)
        circ(-2.6f, -26.4f, 1.1f, eye, false); circ(2.6f, -26.4f, 1.1f, eye, false)
        ell(-3.6f, -1.6f, 3f, 1.8f, col); ell(3.6f, -1.6f, 3f, 1.8f, col)
        if (stripes) {
            path.rewind()
            path.moveTo(-4f, -16f); path.rLineTo(0f, 7f)
            path.moveTo(0f, -17f); path.rLineTo(0f, 8f)
            path.moveTo(4f, -16f); path.rLineTo(0f, 7f)
            pathStroke(Fig.STRIPE, 1.2f)
        }
    }
}

internal fun Fig.pigeon(canvas: Canvas) = figure(canvas) {
    val wing = c("#7f8594"); val orange = c("#e8a33c")
    shadow(8f, 2f, Fig.SHADOW_PIGEON)
    outline = true
    ell(0f, -6f, 7f, 5f, c("#9aa0ad")); circ(-6f, -10f, 3.2f, wing)
    path.rewind(); path.moveTo(-9f, -10f); path.rLineTo(-3f, 1f); path.rLineTo(3f, 1f); path.close(); pathFill(orange)
    path.rewind(); path.moveTo(5f, -5f); path.rLineTo(8f, 2f); path.rLineTo(-8f, 2f); path.close(); pathFill(wing)
    path.rewind()
    path.moveTo(-2f, 0f); path.rLineTo(0f, 3f); path.moveTo(2f, 0f); path.rLineTo(0f, 3f)
    pathStroke(orange, 1f)
}

internal fun Fig.duck(canvas: Canvas) = figure(canvas) {
    shadow(11f, 3f, Fig.DUCK_SHADOW)
    outline = true
    ell(0f, -5f, 9f, 5.5f, c("#ffffff"))
    path.rewind(); path.moveTo(6f, -7f); path.quadTo(12f, -12f, 10f, -3f); path.close(); pathFill(c("#eeeeee"))
    circ(-7f, -11f, 4f, c("#2f8f46"))
    path.rewind(); path.moveTo(-11f, -11f); path.rLineTo(-5f, 1.5f); path.rLineTo(5f, 1.5f); path.close(); pathFill(c("#f2a22e"))
    circ(-8f, -12f, .8f, c("#111111"), false)
}
