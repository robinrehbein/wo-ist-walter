package de.wuselburg.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/** Opaque colour from 0xRRGGBB (like the CSS hex values in game.js). */
internal fun rgb(hex: Int): Int = hex or (0xFF shl 24)

/** Colour from 0xRRGGBB with CSS-style alpha 0..1 (rgba(...) in game.js). */
internal fun rgba(hex: Int, alpha: Float): Int = (hex and 0xFFFFFF) or ((alpha * 255f + 0.5f).toInt() shl 24)

/**
 * Small drawing toolkit that reuses one fill paint, one stroke paint, one Path and one RectF,
 * so figure drawing allocates nothing per call. Not thread-safe: use [Pen.get] (one per thread).
 */
internal class Pen {
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val path = Path()
    private val box = RectF()

    private fun fillWith(color: Int): Paint = fillPaint.also { it.color = color }

    private fun strokeWith(color: Int, width: Float, round: Boolean = false): Paint = linePaint.also {
        it.color = color
        it.strokeWidth = width
        it.strokeCap = if (round) Paint.Cap.ROUND else Paint.Cap.BUTT
    }

    fun rect(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, rx: Float = 0f) {
        box.set(x, y, x + w, y + h)
        if (rx > 0f) c.drawRoundRect(box, rx, rx, fillWith(color)) else c.drawRect(box, fillWith(color))
    }

    fun rectStroke(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, width: Float, rx: Float = 0f) {
        box.set(x, y, x + w, y + h)
        c.drawRoundRect(box, rx, rx, strokeWith(color, width))
    }

    fun circle(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        c.drawCircle(cx, cy, r, fillWith(color))
    }

    fun circleStroke(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, width: Float) {
        c.drawCircle(cx, cy, r, strokeWith(color, width))
    }

    /** Ellipse, optionally rotated clockwise by [rotDeg] around its own centre (SVG rotate(a cx cy)). */
    fun oval(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int, rotDeg: Float = 0f) {
        box.set(cx - rx, cy - ry, cx + rx, cy + ry)
        if (rotDeg != 0f) {
            c.save()
            c.rotate(rotDeg, cx, cy)
            c.drawOval(box, fillWith(color))
            c.restore()
        } else {
            c.drawOval(box, fillWith(color))
        }
    }

    fun ovalStroke(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int, width: Float) {
        box.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawOval(box, strokeWith(color, width))
    }

    /** Filled polygon from x,y pairs. */
    fun poly(c: Canvas, color: Int, vararg pts: Float) {
        path.rewind()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        path.close()
        c.drawPath(path, fillWith(color))
    }

    fun fillPath(c: Canvas, color: Int) = c.drawPath(path, fillWith(color))

    fun strokePath(c: Canvas, color: Int, width: Float, round: Boolean = false) =
        c.drawPath(path, strokeWith(color, width, round))

    fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        c.drawLine(x1, y1, x2, y2, strokeWith(color, width))
    }

    fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int) {
        textPaint.textSize = size
        textPaint.color = color
        c.drawText(s, x, y, textPaint)
    }

    /** Half-ellipse over the top, from the left to the right end of the box (SVG "A rx ry 0 0 1"). */
    fun domeTo(left: Float, top: Float, right: Float, bottom: Float) {
        box.set(left, top, right, bottom)
        path.arcTo(box, 180f, 180f)
    }

    companion object {
        private val local = ThreadLocal.withInitial { Pen() }
        fun get(): Pen = local.get()!!
    }
}
