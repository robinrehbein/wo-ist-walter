package de.wuselburg.ui

import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max

/** Transient visual effects drawn over the world; [startMs] = SystemClock.uptimeMillis() at creation. */
sealed interface WorldEffect {
    val startMs: Long

    /** Green ring + "Gefunden!" (fx-found). */
    data class Found(val cx: Double, val cy: Double, val radius: Double, override val startMs: Long) : WorldEffect

    /** White ring with red cross at a wrong tap (fx-miss). */
    data class Miss(val x: Double, val y: Double, override val startMs: Long) : WorldEffect

    /** Dashed yellow hint circle (fx-hint, radius 240 in game.js). */
    data class Hint(val x: Double, val y: Double, val radius: Double, override val startMs: Long) : WorldEffect
}

private const val FOUND_MS = 500L
private const val MISS_MS = 700L
private const val HINT_MS = 3600L
private const val TAP_MAX_MS = 700L

private fun WorldEffect.durationMs() = when (this) {
    is WorldEffect.Found -> FOUND_MS
    is WorldEffect.Miss -> MISS_MS
    is WorldEffect.Hint -> HINT_MS
}

@Composable
fun WorldView(
    picture: Picture,
    camera: CameraState,
    effects: List<WorldEffect>,
    onTapWorld: (Double, Double) -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    camera.density = density.density
    val scope = rememberCoroutineScope()
    val currentOnTap by rememberUpdatedState(onTapWorld)

    // Animation clock: only ticks while an effect is still animating.
    var nowMs by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(effects) {
        while (true) {
            nowMs = SystemClock.uptimeMillis()
            if (effects.none { it.startMs + it.durationMs() > nowMs }) break
            withFrameNanos { }
        }
    }

    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) }
    val labelFill = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 26f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
            color = android.graphics.Color.WHITE
        }
    }
    val labelStroke = remember {
        Paint(labelFill).apply {
            style = Paint.Style.STROKE; strokeWidth = 6f; strokeJoin = Paint.Join.ROUND
            color = 0xFF2FBF71.toInt()
        }
    }

    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged { camera.onViewportChanged(it.width.toFloat(), it.height.toFloat()) }
            .cameraGestures(camera, scope) { px, py ->
                val w = camera.screenToWorld(px, py)
                currentOnTap(w.x.toDouble(), w.y.toDouble())
            }
            .wheelZoom(camera),
    ) {
        val k = camera.k
        val cx = camera.x
        val cy = camera.y
        drawIntoNative { canvas ->
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(k, k)
            canvas.drawPicture(picture)
            canvas.restore()
        }
        if (effects.isEmpty()) return@Canvas
        val now = nowMs
        val density = camera.density
        withTransform({ translate(cx, cy); scale(k, k, Offset.Zero) }) {
            for (e in effects) {
                val t = (now - e.startMs).coerceAtLeast(0L).toFloat()
                when (e) {
                    is WorldEffect.Found -> drawFound(e, t, labelFill, labelStroke)
                    is WorldEffect.Miss -> if (t < MISS_MS) drawMiss(e, t / MISS_MS, k, density)
                    is WorldEffect.Hint -> if (t < HINT_MS) drawHint(e, t / HINT_MS, dashEffect)
                }
            }
        }
    }
}

private inline fun DrawScope.drawIntoNative(block: (android.graphics.Canvas) -> Unit) =
    drawContext.canvas.nativeCanvas.let(block)

private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t)

private fun DrawScope.drawFound(
    e: WorldEffect.Found, t: Float, fill: Paint, stroke: Paint,
) {
    val p = easeOut((t / FOUND_MS).coerceIn(0f, 1f))
    val s = 0.3f + 0.7f * p
    val c = Offset(e.cx.toFloat(), e.cy.toFloat())
    val r = e.radius.toFloat()
    withTransform({ scale(s, s, c) }) {
        drawCircle(Color.White.copy(alpha = .25f * p), r, c)
        drawCircle(Color(0xFF2FBF71).copy(alpha = p), r, c, style = Stroke(5f))
        val a = (p * 255).toInt()
        fill.alpha = a
        stroke.alpha = a
        val ty = c.y - r - 8f
        drawContext.canvas.nativeCanvas.apply {
            drawText("Gefunden!", c.x, ty, stroke)
            drawText("Gefunden!", c.x, ty, fill)
        }
    }
}

/** Constant on-screen size like game.js: scale by 1/max(k, .35), then grow .4 -> 1.6 while fading. */
private fun DrawScope.drawMiss(e: WorldEffect.Miss, p: Float, k: Float, density: Float) {
    val base = density / max(k, 0.35f * density)
    val s = base * (0.4f + 1.2f * easeOut(p))
    val alpha = 0.9f * (1f - easeOut(p))
    withTransform({ translate(e.x.toFloat(), e.y.toFloat()); scale(s, s, Offset.Zero) }) {
        drawCircle(Color.White.copy(alpha = alpha), 16f, Offset.Zero, style = Stroke(4f))
        val red = Color(0xFFE05A5A).copy(alpha = alpha)
        drawLine(red, Offset(-6f, -6f), Offset(6f, 6f), 4f, StrokeCap.Round)
        drawLine(red, Offset(6f, -6f), Offset(-6f, 6f), 4f, StrokeCap.Round)
    }
}

/** Opacity keyframes of fx-hint: 0 -> 1 until 15 %, hold until 75 %, fade out. */
private fun DrawScope.drawHint(e: WorldEffect.Hint, p: Float, dash: PathEffect) {
    val a = when {
        p < 0.15f -> p / 0.15f
        p <= 0.75f -> 1f
        else -> (1f - p) / 0.25f
    }
    val c = Offset(e.x.toFloat(), e.y.toFloat())
    val r = e.radius.toFloat()
    drawCircle(Color(0xFFFFE278).copy(alpha = .28f * a), r, c)
    drawCircle(Color(0xFFFFD84A).copy(alpha = a), r, c, style = Stroke(6f, pathEffect = dash))
}

/** Mouse wheel / trackpad scroll zoom around the pointer (game.js: exp(-deltaY * .0015) per px). */
private fun Modifier.wheelZoom(camera: CameraState): Modifier = pointerInput(camera) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Scroll) continue
            val change = event.changes.firstOrNull() ?: continue
            val dy = change.scrollDelta.y
            if (dy == 0f) continue
            camera.cancelFly()
            camera.zoomAt(change.position.x, change.position.y, camera.k * exp(-dy * 0.15f))
            event.changes.forEach { it.consume() }
        }
    }
}

/** One-finger pan with fling, two-finger pinch + pan, and tap detection in a single gesture loop. */
private fun Modifier.cameraGestures(
    camera: CameraState,
    scope: CoroutineScope,
    onTap: (Float, Float) -> Unit,
): Modifier = pointerInput(camera) {
    val slop = 8.dp.toPx()
    var fling: Job? = null
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
        fling?.cancel()
        camera.cancelFly()
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var moved = false
        var multi = false
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed == 0) {
                val up = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                if (!moved && !multi && up.uptimeMillis - down.uptimeMillis < TAP_MAX_MS) {
                    onTap(up.position.x, up.position.y)
                } else if (moved && !multi) {
                    val v = tracker.calculateVelocity()
                    if (hypot(v.x, v.y) > 200f) {
                        fling = startFling(scope, camera, v.x, v.y)
                    }
                }
                event.changes.forEach { it.consume() }
                break
            }
            if (pressed >= 2) {
                multi = true
                moved = true
                val centroid = event.calculateCentroid(useCurrent = true)
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                if (centroid != Offset.Unspecified) {
                    camera.panBy(pan.x, pan.y)
                    if (zoom != 1f) camera.zoomAt(centroid.x, centroid.y, camera.k * zoom)
                }
                tracker.resetTracking()
            } else {
                val c = event.changes.first { it.pressed }
                if (!moved) {
                    val d = c.position - down.position
                    if (hypot(d.x, d.y) > slop) moved = true
                }
                if (moved) {
                    val d = c.position - c.previousPosition
                    camera.panBy(d.x, d.y)
                    tracker.addPosition(c.uptimeMillis, c.position)
                }
            }
            event.changes.forEach { if (it.pressed) it.consume() }
        }
    }
}

/** Fling with exponential decay, launched on the pointer-input scope (cancelled on next touch). */
private fun startFling(
    scope: CoroutineScope, camera: CameraState, vx0: Float, vy0: Float,
): Job = scope.launch {
    var vx = vx0
    var vy = vy0
    val token = camera.flyTokenValue
    var prev = withFrameNanos { it }
    while (hypot(vx, vy) > 40f && camera.flyTokenValue == token) {
        val now = withFrameNanos { it }
        val dt = (now - prev) / 1e9f
        prev = now
        camera.panBy(vx * dt, vy * dt)
        val decay = exp(-4f * dt)
        vx *= decay
        vy *= decay
    }
}
