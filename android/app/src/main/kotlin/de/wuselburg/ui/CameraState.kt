package de.wuselburg.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import de.wuselburg.core.WORLD_H
import de.wuselburg.core.WORLD_W
import kotlin.math.max
import kotlin.math.min

/**
 * Pan/zoom camera. Screen point = world point * [k] + ([x], [y]).
 * Mirrors game.js cam / clampCam / limits / resetCamera / zoomAt / flyTo.
 */
@Stable
class CameraState {
    var k by mutableFloatStateOf(1f); private set
    var x by mutableFloatStateOf(0f); private set
    var y by mutableFloatStateOf(0f); private set
    var minK = 0.2f; private set
    var maxK = 2f; private set

    /** Display density; 1 css px of game.js == 1 dp, so maxK is 1.4 dp per world unit. */
    var density = 1f
        set(value) {
            field = value
            updateLimits()
        }

    private val worldW = WORLD_W.toFloat()
    private val worldH = WORLD_H.toFloat()
    private var vw = 0f
    private var vh = 0f
    private var resetPending = false
    private var flyToken = 0

    /** Changes whenever a fly, reset or button zoom interrupts camera motion (used to stop flings). */
    val flyTokenValue get() = flyToken

    private fun updateLimits() {
        if (vw <= 0f || vh <= 0f) return
        minK = min(vw / worldW, vh / worldH)
        maxK = max(minK * 8f, 1.4f * density)
    }

    private fun clamp() {
        if (vw <= 0f || vh <= 0f) return
        val mw = worldW * k
        val mh = worldH * k
        x = if (mw <= vw) (vw - mw) / 2f else x.coerceIn(vw - mw, 0f)
        y = if (mh <= vh) (vh - mh) / 2f else y.coerceIn(vh - mh, 0f)
    }

    /** Keeps the world point at the viewport centre fixed (rotation, split screen). */
    fun onViewportChanged(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val hadViewport = vw > 0f && vh > 0f
        val cx = if (hadViewport) (vw / 2f - x) / k else worldW / 2f
        val cy = if (hadViewport) (vh / 2f - y) / k else worldH / 2f
        vw = w
        vh = h
        updateLimits()
        if (resetPending || !hadViewport) {
            resetPending = false
            reset()
            return
        }
        k = k.coerceIn(minK, maxK)
        x = vw / 2f - cx * k
        y = vh / 2f - cy * k
        clamp()
    }

    /** Overview zoom (2.2x the fit scale), centred on the world. */
    fun reset() {
        cancelFly()
        if (vw <= 0f || vh <= 0f) {
            resetPending = true
            return
        }
        k = min(maxK, minK * 2.2f)
        x = vw / 2f - worldW / 2f * k
        y = vh / 2f - worldH / 2f * k
        clamp()
    }

    fun zoomAt(px: Float, py: Float, newK: Float) {
        val nk = newK.coerceIn(minK, max(minK, maxK))
        val wx = (px - x) / k
        val wy = (py - y) / k
        k = nk
        x = px - wx * nk
        y = py - wy * nk
        clamp()
    }

    fun panBy(dx: Float, dy: Float) {
        x += dx
        y += dy
        clamp()
    }

    /** Aborts a running [flyTo] (it returns at its next frame). */
    fun cancelFly() {
        flyToken++
    }

    suspend fun flyTo(wx: Double, wy: Double, targetK: Float, durationMs: Int = 550) {
        if (vw <= 0f || vh <= 0f) return
        val token = ++flyToken
        val tk = targetK.coerceIn(minK, max(minK, maxK))
        val fromK = k
        val fromX = x
        val fromY = y
        val toX = vw / 2f - wx.toFloat() * tk
        val toY = vh / 2f - wy.toFloat() * tk
        var start = -1L
        while (token == flyToken) {
            val now = withFrameNanos { it }
            if (token != flyToken) return
            if (start < 0) start = now
            val t = ((now - start) / 1_000_000f / durationMs).coerceIn(0f, 1f)
            val e = t * t * (3f - 2f * t) // smoothstep
            k = fromK + (tk - fromK) * e
            x = fromX + (toX - fromX) * e
            y = fromY + (toY - fromY) * e
            clamp()
            if (t >= 1f) return
        }
    }

    fun screenToWorld(px: Float, py: Float): Offset = Offset((px - x) / k, (py - y) / k)
}
