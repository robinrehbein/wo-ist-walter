// OWNER: FIGURE agent. NOTE FOR DECOR AGENT: FigureIcon / IconKind / drawIcon were MOVED here from
// Renderer.kt (they use the new fox/raccoon code). Delete them (and VB_* constants) from Renderer.kt.
package de.wuselburg.render

import android.graphics.Canvas
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.min

/** Figures drawn as small icons in the UI (goal bar, dialogs, menu). */
enum class IconKind { FIPS, RACCOON_CAKE, CAKE, MAGNIFIER }

/** Icon viewBox, same as the web version: -30 -70 60 75. */
private const val ICON_VB_X = -30f
private const val ICON_VB_Y = -70f
private const val ICON_VB_W = 60f
private const val ICON_VB_H = 75f

@Composable
fun FigureIcon(kind: IconKind, modifier: Modifier) {
    ComposeCanvas(modifier) {
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            val s = min(size.width / ICON_VB_W, size.height / ICON_VB_H)
            if (s <= 0f) return@drawIntoCanvas
            c.save()
            c.translate((size.width - ICON_VB_W * s) / 2f - ICON_VB_X * s, (size.height - ICON_VB_H * s) / 2f - ICON_VB_Y * s)
            c.scale(s, s)
            drawIconFigure(c, kind)
            c.restore()
        }
    }
}

internal fun drawIconFigure(c: Canvas, kind: IconKind) {
    val pen = Pen.get()
    val white = rgb(0xffffff)
    when (kind) {
        IconKind.FIPS -> drawFoxStyle(c, "fips")
        IconKind.RACCOON_CAKE -> drawRaccoonFigure(c, true)
        IconKind.CAKE -> {
            pen.oval(c, 0f, -4f, 26f, 5f, rgb(0xe6dfcf))
            pen.rect(c, -20f, -30f, 40f, 24f, rgb(0xf7c6d9), 5f)
            pen.rect(c, -20f, -38f, 40f, 12f, white, 6f)
            for (i in -1..1) pen.rect(c, i * 11f - 1.5f, -26f, 3f, 8f, white, 1.5f)
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
