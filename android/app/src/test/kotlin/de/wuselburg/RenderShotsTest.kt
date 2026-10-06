package de.wuselburg

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.wuselburg.core.FoxD
import de.wuselburg.core.FoxStyle
import de.wuselburg.core.Levels
import de.wuselburg.core.SceneBuilder
import de.wuselburg.render.renderSceneToBitmap
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class RenderShotsTest {
    private val outDir = File("/tmp/claude-0/-home-user-wo-ist-walter/34a5a771-de7d-5e7d-81c8-aa5008781eb4/scratchpad/android-shots")

    private fun save(b: Bitmap, name: String) {
        outDir.mkdirs()
        FileOutputStream(File(outDir, name)).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun rendersFips1AndZoom() {
        val scene = SceneBuilder.build(Levels.all.first { it.id == "fips1" })
        val bmp = renderSceneToBitmap(scene, 1600)
        save(bmp, "fips1_full.png")
        // not blank: many distinct colours
        val seen = HashSet<Int>()
        for (x in 0 until bmp.width step 7) for (y in 0 until bmp.height step 7) seen.add(bmp.getPixel(x, y))
        assertTrue("distinct colours ${seen.size}", seen.size > 20)

        val step = scene.steps.first()
        val big = renderSceneToBitmap(scene, 6400)
        val k = 6400 / 2180.0
        val half = 220
        val l = ((step.centerX * k) - half).toInt().coerceIn(0, big.width - 2 * half)
        val t = ((step.centerY * k) - half).toInt().coerceIn(0, big.height - 2 * half)
        save(Bitmap.createBitmap(big, l, t, 2 * half, 2 * half), "fips1_zoom_fips.png")
    }

    @Test fun rendersFoxStyleSheet() {
        // all fox variants on a quiet scene by reusing a level and cropping its decoys
        val scene = SceneBuilder.build(Levels.all.first { it.id == "fips3" })
        save(renderSceneToBitmap(scene, 1600), "fips3_full.png")
        val case = SceneBuilder.build(Levels.all.first { it.id == "case1" })
        save(renderSceneToBitmap(case, 1600), "case1_full.png")
        val foxes = scene.drawables.filterIsInstance<FoxD>()
        assertTrue(foxes.map { it.style }.toSet().size >= 3)
    }
}
