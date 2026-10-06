package de.wuselburg.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Compares the Kotlin scene generator with golden-scenes.json, dumped from game.js by dump-scenes.js. */
class SceneBuilderTest {
    private val golden = MiniJson(
        checkNotNull(javaClass.getResourceAsStream("/golden-scenes.json")).readBytes().decodeToString(),
    ).parse() as Map<*, *>

    private fun level(id: String) = Levels.all.first { it.id == id }
    private fun hex(s: String?): Int? = s?.let {
        val h = it.removePrefix("#").let { v -> if (v.length == 3) v.map { c -> "$c$c" }.joinToString("") else v }
        (0xFF000000L or h.toLong(16)).toInt()
    }

    private fun num(o: Any?) = (o as Number).toDouble()
    private fun close(exp: Any?, act: Double, what: String) =
        assertTrue(abs(num(exp) - act) < 1e-6, "$what: expected $exp, was $act")

    private fun kindKey(d: Drawable): String = when (d) {
        is PersonD -> "person"; is DogD -> "dog"; is CatD -> "cat"; is PigeonD -> "pigeon"
        is FoxD -> "fox:${d.style}"; is RaccoonD -> "raccoon"; is TreeD -> "tree"; is StallD -> "stall"
    }

    @Test
    fun matchesGoldenFromGameJs() {
        for (lv in Levels.all) {
            val g = golden[lv.id] as Map<*, *>
            val scene = SceneBuilder.build(lv)
            val tag = lv.id

            assertEquals((g["types"] as List<*>).map { it.toString().uppercase() }, scene.blocks.map { it.type.name }, "$tag types")
            assertEquals(num(g["crumbs"]).toInt(), scene.crumbs.size, "$tag crumbs")
            assertEquals(num(g["total"]).toInt(), scene.drawables.size, "$tag total")
            val counts = scene.drawables.groupingBy(::kindKey).eachCount()
            val gc = (g["counts"] as Map<*, *>).entries.associate { it.key.toString() to num(it.value).toInt() }
            assertEquals(gc, counts, "$tag counts")

            val gs = g["steps"] as List<*>
            assertEquals(gs.size, scene.steps.size, "$tag steps")
            gs.zip(scene.steps).forEachIndexed { i, (e, st) ->
                e as Map<*, *>
                assertEquals(e["title"], st.title); assertEquals(e["sub"], st.subtitle)
                assertEquals(e["onFound"], st.onFound); assertEquals(e["epilogue"], st.epilogue)
                val c = e["center"] as Map<*, *>
                close(c["x"], st.centerX, "$tag step$i cx"); close(c["y"], st.centerY, "$tag step$i cy")
                val hit = e["hit"] as Map<*, *>
                (hit["circle"] as Map<*, *>?)?.let {
                    val h = st.hit as Hit.Circle
                    close(it["x"], h.x, "cx"); close(it["y"], h.y, "cy"); close(it["r"], h.r, "r")
                }
                (hit["rect"] as Map<*, *>?)?.let {
                    val h = st.hit as Hit.Rect
                    close(it["x"], h.x, "rx"); close(it["y"], h.y, "ry"); close(it["w"], h.w, "rw"); close(it["h"], h.h, "rh")
                }
            }

            val sample = (g["first10"] as List<*>) + (g["last3"] as List<*>)
            val actual = scene.drawables.take(10) + scene.drawables.takeLast(3)
            sample.zip(actual).forEachIndexed { i, (e, d) ->
                e as Map<*, *>
                val w = "$tag drawable#$i"
                close(e["x"], d.x, "$w x"); close(e["y"], d.y, "$w y"); close(e["scale"], d.scale, "$w scale")
                assertEquals(e["flip"], d.flip, "$w flip")
                val k = e["kind"]
                assertEquals(if (k == "fox") "fox:${e["style"]}" else k, kindKey(d), "$w kind")
                when (d) {
                    is PersonD -> {
                        assertEquals(hex(e["shirt"] as String?), d.shirt, "$w shirt")
                        assertEquals(hex(e["skin"] as String?), d.skin, "$w skin")
                        assertEquals(hex(e["pants"] as String?), d.pants, "$w pants")
                        assertEquals(e["hat"], d.hat.name, "$w hat")
                        hex(e["hair"] as String?)?.let { assertEquals(it, d.hair, "$w hair") }
                        hex(e["hatColor"] as String?)?.let { assertEquals(it, d.hatColor, "$w hatColor") }
                        assertEquals(hex(e["balloon"] as String?) ?: 0, d.balloonColor, "$w balloon")
                    }
                    is DogD -> assertEquals(hex(e["color"] as String?), d.color, "$w dog")
                    is CatD -> assertEquals(hex(e["color"] as String?), d.color, "$w cat")
                    is TreeD -> assertEquals(hex(e["color"] as String?), d.color, "$w tree")
                    is StallD -> assertEquals(hex(e["color"] as String?), d.color, "$w stall")
                    is RaccoonD -> assertEquals(e["hasCake"], d.hasCake, "$w cake")
                    else -> Unit
                }
            }
        }
    }

    @Test
    fun deterministicAndSortedByY() {
        for (lv in Levels.all) {
            val a = SceneBuilder.build(lv)
            assertEquals(a, SceneBuilder.build(lv))
            assertTrue(a.drawables.zipWithNext().all { (p, q) -> p.y <= q.y }, "${lv.id} sorted")
        }
    }

    @Test
    fun hiddenFipsOverlapsATree() {
        for (lv in Levels.all.filter { it.kind == LevelKind.FIPS && it.hidden }) {
            val s = SceneBuilder.build(lv)
            val fips = s.drawables.filterIsInstance<FoxD>().single { it.style == FoxStyle.FIPS }
            assertTrue(s.drawables.filterIsInstance<TreeD>().any { hypot(it.x - fips.x, it.y - 4 - fips.y) < lv.hideOffset + 1 })
        }
    }

    @Test
    fun thiefIsAtEndOfCrumbRoute() {
        val s = SceneBuilder.build(level("case1"))
        val thief = s.drawables.filterIsInstance<RaccoonD>().single { it.hasCake }
        assertNotNull(s.crumbs.minByOrNull { hypot(it.x - thief.x, it.y - thief.y) })
        assertTrue(s.crumbs.any { hypot(it.x - thief.x, it.y - thief.y) < 40 })
    }
}

/** Tiny JSON reader (objects, arrays, strings, numbers, booleans, null) to avoid a test dependency. */
private class MiniJson(private val s: String) {
    private var i = 0
    fun parse(): Any? { val v = value(); return v }
    private fun ws() { while (s[i].isWhitespace()) i++ }
    private fun value(): Any? {
        ws()
        return when (val c = s[i]) {
            '{' -> { i++; val m = LinkedHashMap<String, Any?>(); ws()
                if (s[i] == '}') { i++; return m }
                while (true) { ws(); val k = str(); ws(); i++; m[k] = value(); ws(); if (s[i++] == '}') return m } ; @Suppress("UNREACHABLE_CODE") m }
            '[' -> { i++; val l = ArrayList<Any?>(); ws()
                if (s[i] == ']') { i++; return l }
                while (true) { l += value(); ws(); if (s[i++] == ']') return l } ; @Suppress("UNREACHABLE_CODE") l }
            '"' -> str()
            't' -> { i += 4; true }
            'f' -> { i += 5; false }
            'n' -> { i += 4; null }
            else -> { val st = i; while (s[i] in "+-.eE0123456789") i++; if (c == ' ') error("bad"); s.substring(st, i).toDouble() }
        }
    }
    private fun str(): String {
        i++; val sb = StringBuilder()
        while (s[i] != '"') {
            if (s[i] == '\\') { i++; when (s[i]) { 'n' -> sb.append('\n'); 'u' -> { sb.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }; else -> sb.append(s[i]) } }
            else sb.append(s[i])
            i++
        }
        i++; return sb.toString()
    }
}
