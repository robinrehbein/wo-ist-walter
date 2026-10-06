package de.wuselburg.render

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/** Parses "#rrggbb", "#rgb" and "rgba(r,g,b,a)" (CSS alpha 0..1) into an ARGB Int; anything else is 0 (transparent). */
internal fun parseCssColor(s: String): Int {
    try {
        if (s.startsWith("#")) {
            val h = s.substring(1)
            if (h.length == 3) {
                val r = h.substring(0, 1).toInt(16) * 17
                val g = h.substring(1, 2).toInt(16) * 17
                val b = h.substring(2, 3).toInt(16) * 17
                return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            return (0xFF shl 24) or h.toInt(16)
        }
        if (s.startsWith("rgba(") && s.endsWith(")")) {
            val p = s.substring(5, s.length - 1).split(",")
            val r = p[0].trim().toInt()
            val g = p[1].trim().toInt()
            val b = p[2].trim().toInt()
            val a = (p[3].trim().toFloat() * 255f + 0.5f).toInt().coerceIn(0, 255)
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    } catch (_: NumberFormatException) {
    }
    return 0
}

/** [color] with its alpha multiplied by [f] (SVG opacity="..." on an element without overlaps). */
internal fun withOpacity(color: Int, f: Float): Int =
    (((color ushr 24) * f + 0.5f).toInt() shl 24) or (color and 0xFFFFFF)

/** JS f1(): round to one decimal. */
internal fun f1(v: Float): Float = Math.round(v * 10f) / 10f

private val DASH = DashPathEffect(floatArrayOf(26f, 22f), 0f)

/**
 * SVG-like drawing toolkit for decor: one fill paint, one stroke paint, one Path/RectF, a colour cache.
 * Shapes inside an [outlined] block get an outline stroke (the `<g OUTLINE>` groups of scene.js) unless
 * `o = false` (stroke="none") or an explicit stroke is given. NOT thread-safe: use [Ink.get] (one per thread).
 */
internal class Ink {
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val path = Path()
    private val box = RectF()
    private val cache = HashMap<String, Int>(128)

    var outlineOn = false
    var outlineColor = 0
    var outlineWidth = 0f
    private var ec = 0
    private var ew = 0f

    /** Colour string (as in scene.js data) -> ARGB, cached. */
    fun col(s: String): Int {
        val hit = cache[s]
        if (hit != null) return hit
        val v = parseCssColor(s)
        cache[s] = v
        return v
    }

    inline fun outlined(color: Int, width: Float, body: () -> Unit) {
        outlineOn = true; outlineColor = color; outlineWidth = width
        try { body() } finally { outlineOn = false }
    }

    private fun edge(o: Boolean, sc: Int, sw: Float) {
        if (sw > 0f) { ec = sc; ew = sw }
        else if (o && outlineOn) { ec = outlineColor; ew = outlineWidth }
        else { ec = 0; ew = 0f }
    }

    private fun shape(c: Canvas, geo: Int, rx: Float, p: Paint) {
        when (geo) {
            0 -> c.drawRect(box, p)
            1 -> c.drawRoundRect(box, rx, rx, p)
            2 -> c.drawOval(box, p)
            else -> c.drawPath(path, p)
        }
    }

    private fun paint(c: Canvas, geo: Int, fill: Int, rx: Float) {
        if ((fill ushr 24) != 0) { fillP.color = fill; shape(c, geo, rx, fillP) }
        if (ew > 0f && (ec ushr 24) != 0) {
            strokeP.color = ec
            strokeP.strokeWidth = ew
            strokeP.strokeCap = Paint.Cap.BUTT
            strokeP.pathEffect = null
            shape(c, geo, rx, strokeP)
        }
    }

    fun rect(c: Canvas, x: Float, y: Float, w: Float, h: Float, fill: Int, rx: Float = 0f,
             o: Boolean = true, sc: Int = 0, sw: Float = 0f) {
        box.set(x, y, x + w, y + h)
        edge(o, sc, sw)
        paint(c, if (rx > 0f) 1 else 0, fill, rx)
    }

    fun oval(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, fill: Int,
             o: Boolean = true, rot: Float = 0f, sc: Int = 0, sw: Float = 0f) {
        box.set(cx - rx, cy - ry, cx + rx, cy + ry)
        edge(o, sc, sw)
        if (rot != 0f) {
            c.save(); c.rotate(rot, cx, cy)
            paint(c, 2, fill, 0f)
            c.restore()
        } else paint(c, 2, fill, 0f)
    }

    fun circle(c: Canvas, cx: Float, cy: Float, r: Float, fill: Int,
               o: Boolean = true, sc: Int = 0, sw: Float = 0f) = oval(c, cx, cy, r, r, fill, o, 0f, sc, sw)

    /** Triangle (SVG polygon / closed path with 3 points). */
    fun tri(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, fill: Int, o: Boolean = true) {
        path.rewind(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
        edge(o, 0, 0f)
        paint(c, 3, fill, 0f)
    }

    /** Quad (closed path with 4 points). */
    fun quad(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float,
             fill: Int, o: Boolean = true) {
        path.rewind(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.lineTo(x4, y4); path.close()
        edge(o, 0, 0f)
        paint(c, 3, fill, 0f)
    }

    /** Fills the current [path] (built by the caller), outlined like the other shapes unless [o] is false. */
    fun fillPath(c: Canvas, fill: Int, o: Boolean = true) {
        edge(o, 0, 0f)
        paint(c, 3, fill, 0f)
    }

    /** Strokes the current [path] only (SVG fill="none" stroke=...). */
    fun strokePath(c: Canvas, color: Int, width: Float, round: Boolean = false, dashed: Boolean = false) {
        strokeP.color = color
        strokeP.strokeWidth = width
        strokeP.strokeCap = if (round) Paint.Cap.ROUND else Paint.Cap.BUTT
        strokeP.pathEffect = if (dashed) DASH else null
        c.drawPath(path, strokeP)
        strokeP.pathEffect = null
    }

    fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float, dashed: Boolean = false) {
        strokeP.color = color
        strokeP.strokeWidth = width
        strokeP.strokeCap = Paint.Cap.BUTT
        strokeP.pathEffect = if (dashed) DASH else null
        c.drawLine(x1, y1, x2, y2, strokeP)
        strokeP.pathEffect = null
    }

    fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int) {
        textP.textSize = size
        textP.color = color
        c.drawText(s, x, y, textP)
    }

    /** SVG "q dx1,dy1 dx2,dy2" on [path]. */
    fun q(dx1: Float, dy1: Float, dx2: Float, dy2: Float) = path.rQuadTo(dx1, dy1, dx2, dy2)

    companion object {
        private val local = ThreadLocal.withInitial { Ink() }
        fun get(): Ink = local.get()!!
    }
}
