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
        strokeJoin = Paint.Join.ROUND
    }
    private val style = PaperStyle.get()

    /** Outline paint of the current style (OL() of scene.js); null in shadow mode (silhouette only). */
    private fun outlineP(): Paint? {
        if (style.shadowMode) return null
        outlinePaint.color = style.figEdgeColor
        outlinePaint.strokeWidth = if (silPass) style.silEdgeWidth else style.figEdgeWidth
        outlinePaint.strokeCap = if (silPass) Paint.Cap.ROUND else Paint.Cap.BUTT
        return outlinePaint
    }
    val path = Path()
    private val box = RectF()
    private val colors = HashMap<String, Int>()

    /** True during the wide outline pass of [sil] (OUT(): width 2.8 / 1.5, round caps). */
    var silPass = false

    /**
     * sil() of scene.js: one-piece silhouette. First the shapes with a wide outline, then the same shapes filled
     * without stroke. In shadow mode just the fill, once.
     */
    inline fun sil(body: () -> Unit) {
        if (!PaperStyle.get().shadowMode) {
            outline = true; silPass = true
            try { body() } finally { outline = false; silPass = false }
        }
        outline = false
        body()
    }

    /** True while inside `<g OUTLINE>`: shapes get the soft outline unless st = false. */
    var outline = false

    /** '#rrggbb' -> ARGB, parsed once. */
    fun c(hex: String): Int = colors.getOrPut(hex) {
        try { Color.parseColor(hex) } catch (e: IllegalArgumentException) { 0xFF888888.toInt() }
    }

    private fun fillWith(color: Int): Paint = fillPaint.also { it.color = style.tone(color) }

    // --- filled shapes (+ outline)

    fun ell(cx: Float, cy: Float, rx: Float, ry: Float, fill: Int, rot: Float = 0f, st: Boolean = true) {
        box.set(cx - rx, cy - ry, cx + rx, cy + ry)
        val k = cv
        if (rot != 0f) { k.save(); k.rotate(rot, cx, cy) }
        k.drawOval(box, fillWith(fill))
        if (outline && st) outlineP()?.let { k.drawOval(box, it) }
        if (rot != 0f) k.restore()
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float, fill: Int, st: Boolean = true) {
        box.set(x, y, x + w, y + h)
        cv.drawRoundRect(box, rx, rx, fillWith(fill))
        if (outline && st) outlineP()?.let { cv.drawRoundRect(box, rx, rx, it) }
    }

    fun circ(cx: Float, cy: Float, r: Float, fill: Int, st: Boolean = true) {
        cv.drawCircle(cx, cy, r, fillWith(fill))
        if (outline && st) outlineP()?.let { cv.drawCircle(cx, cy, r, it) }
    }

    /** Fills [path] (and outlines it). */
    fun pathFill(fill: Int, st: Boolean = true) {
        cv.drawPath(path, fillWith(fill))
        if (outline && st) outlineP()?.let { cv.drawPath(path, it) }
    }

    // --- own strokes (fill="none" with explicit stroke: the outline does not apply)

    private fun lineWith(color: Int, w: Float, round: Boolean): Paint = linePaint.also {
        it.color = style.tone(color); it.strokeWidth = w
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
        val EAR_LAB = Color.argb(115, 40, 20, 5)    // rgba(40,20,5,.45)
        val EAR_DACHS = Color.argb(128, 40, 20, 5)  // rgba(40,20,5,.5)
        val CAT_EYE = 0xFF7FCF5A.toInt()
        val CAT_NOSE = 0xFFE48A8A.toInt()
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
    fun nose(x: Float, y: Float) = ell(x, y, 1.7f, 1.3f, c("#1c1418"), st = false)
    fun eye(x: Float, y: Float) = circ(x, y, 1.2f, dark, false)
    shadow(15f, 3f, Fig.SHADOW)
    when (figStr(d, "breed", "lab")) {
        "lab" -> {
            sil {
                ell(19f, -24f, 2.6f, 7.5f, col, 35f)
                rect(-13f, -10f, 5f, 10f, 2.4f, col); rect(-5f, -10f, 5f, 10f, 2.4f, col)
                rect(6f, -10f, 5f, 10f, 2.4f, col); rect(13f, -10f, 5f, 10f, 2.4f, col)
                ell(3f, -16f, 16f, 8f, col); ell(-11f, -21f, 7f, 8f, col, -25f)
                ell(-15f, -26f, 7.5f, 6.5f, col); ell(-22f, -23.5f, 5.5f, 3.8f, col)
            }
            ell(-22f, -22.6f, 4.6f, 2.8f, cream, st = false); nose(-25.5f, -24.2f); eye(-16.5f, -27.5f)
            ell(-11f, -25f, 3.2f, 6f, Fig.EAR_LAB, 14f, st = false)
        }
        "dachs" -> {
            sil {
                ell(25f, -16f, 2.4f, 6.5f, col, 40f)
                rect(-18f, -7f, 5f, 7f, 2.4f, col); rect(-9f, -7f, 5f, 7f, 2.4f, col)
                rect(10f, -7f, 5f, 7f, 2.4f, col); rect(18f, -7f, 5f, 7f, 2.4f, col)
                ell(3f, -12f, 22f, 6.5f, col); ell(-17f, -15f, 6f, 7f, col)
                ell(-20f, -18f, 6.8f, 6f, col); ell(-27f, -16f, 6f, 3.2f, col)
            }
            nose(-32f, -17.2f); eye(-21.5f, -19.5f)
            ell(-17f, -15.5f, 3f, 6f, Fig.EAR_DACHS, 10f, st = false)
        }
        "spot" -> {
            val w = c("#f5f1e8")
            sil {
                ell(19f, -24f, 2.6f, 7.5f, w, 35f)
                rect(-13f, -10f, 5f, 10f, 2.4f, w); rect(-5f, -10f, 5f, 10f, 2.4f, w)
                rect(6f, -10f, 5f, 10f, 2.4f, w); rect(13f, -10f, 5f, 10f, 2.4f, w)
                ell(3f, -16f, 16f, 8f, w); ell(-11f, -21f, 7f, 8f, w, -25f)
                ell(-15f, -26f, 7.5f, 6.5f, w); ell(-22f, -23.5f, 5.5f, 3.8f, w)
            }
            ell(2f, -18f, 5.5f, 4f, col, st = false); ell(11f, -14f, 4f, 3.2f, col, st = false)
            ell(-17.5f, -28.5f, 3.2f, 3f, col, st = false)
            nose(-25.5f, -24.2f); eye(-16.5f, -27.5f)
            ell(-11f, -25f, 3.2f, 6f, col, 14f, st = false)
        }
        else -> { // poodle
            sil {
                rect(-8f, -10f, 2.8f, 10f, 0f, col); rect(-2f, -10f, 2.8f, 10f, 0f, col)
                rect(5f, -10f, 2.8f, 10f, 0f, col); rect(11f, -10f, 2.8f, 10f, 0f, col)
                circ(-7f, -5f, 3.8f, col); circ(12f, -5f, 3.8f, col)
                ell(3f, -16f, 13f, 7.5f, col); circ(-8f, -20f, 7f, col)
                circ(-13f, -26f, 6f, col); circ(-13f, -32.5f, 3.8f, col)
                circ(-7f, -26f, 3.8f, col); ell(-20f, -24f, 4.4f, 3f, col)
                cv.save(); cv.rotate(25f, 14f, -14f); rect(14f, -22f, 2f, 8f, 0f, col); cv.restore()
                circ(19f, -24f, 4f, col)
            }
            nose(-23.5f, -24.4f); eye(-14.5f, -26.5f)
        }
    }
}

internal fun Fig.cat(canvas: Canvas, d: Drawable) = figure(canvas) {
    val col = c(figStr(d, "color", "#555555"))
    val stripes = figBool(d, "stripes", false)
    fun eyes(x1: Float, x2: Float, y: Float) {
        ell(x1, y, 1.1f, 1.3f, Fig.CAT_EYE, st = false); ell(x2, y, 1.1f, 1.3f, Fig.CAT_EYE, st = false)
        val k = c("#111111")
        ell(x1, y, .4f, 1f, k, st = false); ell(x2, y, .4f, 1f, k, st = false)
    }
    fun nose(x: Float, y: Float) {
        path.rewind(); path.moveTo(x, y); path.rLineTo(1.4f, 1.2f); path.rLineTo(1.4f, -1.2f); path.close()
        pathFill(Fig.CAT_NOSE, st = false)
    }
    shadow(11f, 3f, Fig.SHADOW)
    if (figStr(d, "pose", "stand") == "stand") {
        sil {
            ell(19f, -19f, 2.4f, 10f, col, 28f)
            rect(-9f, -8f, 4f, 8f, 2f, col); rect(-3f, -8f, 4f, 8f, 2f, col)
            rect(6f, -8f, 4f, 8f, 2f, col); rect(11f, -8f, 4f, 8f, 2f, col)
            ell(2f, -12f, 13f, 6.5f, col); ell(-9f, -16f, 5f, 6f, col)
            circ(-12f, -19f, 6f, col)
            path.rewind()
            path.moveTo(-17f, -22f); path.lineTo(-16f, -29f); path.lineTo(-11f, -24f); path.close()
            path.moveTo(-8f, -24f); path.lineTo(-6f, -30f); path.lineTo(-4f, -22f); path.close()
            pathFill(col)
        }
        if (stripes) {
            path.rewind()
            path.moveTo(-3f, -17f); path.rLineTo(0f, 6f)
            path.moveTo(2f, -18f); path.rLineTo(0f, 7f)
            path.moveTo(7f, -17f); path.rLineTo(0f, 6f)
            pathStroke(Fig.STRIPE, 1.3f)
        }
        eyes(-14.4f, -9.8f, -19.4f); nose(-12.8f, -16.6f)
    } else {
        sil {
            ell(11f, -4f, 8f, 2.8f, col, -12f); circ(17f, -8f, 2.6f, col)
            ell(0f, -11f, 8.8f, 11f, col); circ(0f, -26f, 6.8f, col)
            path.rewind()
            path.moveTo(-6.8f, -29.5f); path.lineTo(-5.8f, -37f); path.lineTo(-1.4f, -31f); path.close()
            path.moveTo(6.8f, -29.5f); path.lineTo(5.8f, -37f); path.lineTo(1.4f, -31f); path.close()
            pathFill(col)
            ell(-3.8f, -1.5f, 3.2f, 2f, col); ell(3.8f, -1.5f, 3.2f, 2f, col)
        }
        if (stripes) {
            path.rewind()
            path.moveTo(-4f, -17f); path.rLineTo(0f, 7f)
            path.moveTo(0f, -18f); path.rLineTo(0f, 8f)
            path.moveTo(4f, -17f); path.rLineTo(0f, 7f)
            pathStroke(Fig.STRIPE, 1.3f)
        }
        eyes(-2.7f, 2.7f, -26.4f); nose(-1.2f, -23.6f)
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
