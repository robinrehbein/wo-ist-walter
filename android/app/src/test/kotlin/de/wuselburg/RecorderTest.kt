package de.wuselburg

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.wuselburg.core.Levels
import de.wuselburg.core.SceneBuilder
import de.wuselburg.render.recordScenePicture
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import de.wuselburg.render.renderSceneToBitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecorderTest {
    @Test fun recordsAllLevels() {
        for (l in Levels.all) {
            val p = recordScenePicture(SceneBuilder.build(l))
            assertEquals(2180, p.width)
        }
    }

    private fun grain(): Bitmap {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val b = BitmapFactory.decodeResource(ctx.resources, R.drawable.paper_grain, opts)
        assertNotNull(b)
        assertEquals(256, b.width)
        return b
    }

    @Test fun paperAndClassicBothRecordAndDiffer() {
        val s = SceneBuilder.build(Levels.all.first())
        assertEquals(2180, recordScenePicture(s, paper = true, grain = grain()).width)
        assertEquals(2180, recordScenePicture(s, paper = false).width)
        val a = renderSceneToBitmap(s, 300, paper = true)
        val b = renderSceneToBitmap(s, 300, paper = false)
        assertNotEquals(a.getPixel(150, 5), b.getPixel(150, 5)) // asphalt vs. grass at the world edge
    }
}
