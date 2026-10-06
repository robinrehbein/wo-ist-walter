package de.wuselburg

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.wuselburg.core.Drawable
import de.wuselburg.core.Levels
import de.wuselburg.core.SceneBuilder
import de.wuselburg.render.recordScenePicture
import de.wuselburg.render.renderSceneToBitmap
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class RenderShotsTest {
    private val outDir = File("/tmp/claude-0/-home-user-wo-ist-walter/34a5a771-de7d-5e7d-81c8-aa5008781eb4/scratchpad/android-shots3")
    private val big = 6000
    private val k = big / 2180.0

    private val grain: Bitmap by lazy {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        BitmapFactory.decodeResource(ctx.resources, R.drawable.paper_grain, BitmapFactory.Options().apply { inScaled = false })
    }

    private fun save(b: Bitmap, name: String) {
        outDir.mkdirs()
        FileOutputStream(File(outDir, name)).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun scene(id: String) = SceneBuilder.build(Levels.all.first { it.id == id })

    /** Crop around world point (cx, cy) with half-size hw x hh world units. */
    private fun crop(bigBmp: Bitmap, cx: Double, cy: Double, hw: Double, hh: Double): Bitmap {
        val l = ((cx - hw) * k).toInt().coerceIn(0, bigBmp.width - 2)
        val t = ((cy - hh) * k).toInt().coerceIn(0, bigBmp.height - 2)
        val w = (2 * hw * k).toInt().coerceAtMost(bigBmp.width - l)
        val h = (2 * hh * k).toInt().coerceAtMost(bigBmp.height - t)
        return Bitmap.createBitmap(bigBmp, l, t, w, h)
    }

    private fun cropD(bigBmp: Bitmap, d: Drawable, hw: Double = 110.0, hh: Double = 110.0) =
        crop(bigBmp, d.x, d.y - hh * 0.45, hw, hh)

    @Test fun overviews() {
        for (id in listOf("fips1", "fips2", "fips3", "case1")) {
            val s = scene(id)
            val bmp = renderSceneToBitmap(s, 1800, paper = true, grain = grain)
            save(bmp, "${id}_full.png")
            val seen = HashSet<Int>()
            for (x in 0 until bmp.width step 7) for (y in 0 until bmp.height step 7) seen.add(bmp.getPixel(x, y))
            assertTrue("distinct colours ${seen.size}", seen.size > 20)
        }
    }

    @Test fun classicOverview() {
        val s = scene("fips3")
        save(renderSceneToBitmap(s, 1800, paper = false), "fips3_classic_full.png")
        val big6 = renderSceneToBitmap(s, big, paper = false)
        s.drawables.firstOrNull { it.kind == "prop" && it.strOrNull("type") == "blanket" }?.let { save(cropD(big6, it, 160.0, 120.0), "z_picnic_classic.png") }
    }

    @Test fun zoomCrops() {
        for (id in listOf("fips1", "fips3", "case1")) {
            val s = scene(id)
            val bmp = renderSceneToBitmap(s, big, paper = true, grain = grain)
            val step = s.steps.first()
            save(crop(bmp, step.centerX, step.centerY, 120.0, 120.0), "${id}_zoom_target.png")
            if (id != "fips3") continue
            fun first(pred: (Drawable) -> Boolean) = s.drawables.firstOrNull(pred)
            fun propD(t: String) = first { it.kind == "prop" && it.strOrNull("type") == t }
            propD("blanket")?.let { save(cropD(bmp, it, 160.0, 120.0), "z_picnic.png") }
            propD("cart")?.let { save(cropD(bmp, it, 150.0, 130.0), "z_cart.png") }
            propD("rod")?.let { save(cropD(bmp, it, 200.0, 130.0), "z_angler.png") }
            propD("guitarcase")?.let { save(cropD(bmp, it, 150.0, 120.0), "z_musician.png") }
            propD("easel")?.let { save(cropD(bmp, it, 150.0, 120.0), "z_painter.png") }
            propD("bench")?.let { save(cropD(bmp, it, 150.0, 120.0), "z_bench.png") }
            first { it.kind == "stall" }?.let { save(cropD(bmp, it, 150.0, 130.0), "z_stall.png") }
            s.blocks.firstOrNull { b -> b.houses.any { it.shop.isNotEmpty() } }?.let { b ->
                save(crop(bmp, b.x + b.w / 2, b.y + b.h / 2, 215.0, 190.0), "z_houses.png")
            }
            s.blocks.firstOrNull { it.type.name == "PLAZA" }?.let { b -> save(crop(bmp, b.x + b.w / 2, b.y + b.h / 2, 215.0, 190.0), "z_plaza.png") }
            s.blocks.firstOrNull { it.pond != null }?.let { b -> save(crop(bmp, b.x + b.w / 2, b.y + b.h / 2, 215.0, 190.0), "z_park.png") }
            s.drawables.filter { it.kind == "fox" }.groupBy { it.str("style") }.values.map { it.first() }
                .forEachIndexed { i, d -> save(cropD(bmp, d, 70.0, 70.0), "z_fox$i.png") }
            // person contact sheet: one of each variant
            val persons = s.drawables.filter { it.kind == "person" }
            val seenKey = HashSet<String>()
            val picks = ArrayList<Drawable>()
            for (p in persons) {
                val keys = listOf("body", "hairStyle", "hat", "bottom", "glasses", "beard", "bag", "prop", "pose", "seat", "pattern")
                    .map { it + ":" + p.props[it] }
                if (keys.any { seenKey.add(it) }) picks.add(p)
            }
            val cell = 100.0; val cw = (cell * k).toInt(); val ch = (cell * 1.3 * k).toInt()
            val cols = 8; val rows = (picks.size + cols - 1) / cols
            val sheet = Bitmap.createBitmap(cols * cw, rows * ch, Bitmap.Config.ARGB_8888)
            val cv = Canvas(sheet)
            picks.forEachIndexed { i, p ->
                val c = crop(bmp, p.x, p.y - 55 * p.scale, cell / 2, cell * 0.65)
                cv.drawBitmap(c, null, Rect((i % cols) * cw, (i / cols) * ch, (i % cols) * cw + cw, (i / cols) * ch + ch), null)
            }
            save(sheet, "z_persons_sheet.png")
            println("PERSONS picked ${picks.size}")
        }
    }

    @Test fun perf() {
        val s = scene("fips3")
        recordScenePicture(s, true, grain) // warm-up
        val t0 = System.nanoTime()
        val n = 5
        repeat(n) { recordScenePicture(s, true, grain) }
        val ms = (System.nanoTime() - t0) / 1e6 / n
        val t1 = System.nanoTime()
        repeat(n) { recordScenePicture(s, false) }
        println("PERF classic recordScenePicture avg ${"%.1f".format((System.nanoTime() - t1) / 1e6 / n)} ms")
        println("PERF paper drawables=${s.drawables.size} recordScenePicture avg ${"%.1f".format(ms)} ms")
    }
}
