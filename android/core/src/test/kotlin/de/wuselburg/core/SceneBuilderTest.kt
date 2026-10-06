package de.wuselburg.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parity test: compares EVERYTHING SceneBuilder produces with golden-scenes.json, dumped from scene.js
 * buildSceneData() by scratchpad dump-scenes.js (numbers rounded to 6 decimals, tolerance here 1e-6).
 * Reports the first mismatch with a readable path such as `case1.drawables[17].props.shirt`.
 */
class SceneBuilderTest {
    private val golden = MiniJson(
        checkNotNull(javaClass.getResourceAsStream("/golden-scenes.json")).readBytes().decodeToString(),
    ).parse() as Map<*, *>

    private fun pt(x: Double, y: Double) = mapOf("x" to x, "y" to y)

    private fun toTree(s: SceneData): Map<String, Any?> = mapOf(
        "mood" to s.mood,
        "blocks" to s.blocks.map { b ->
            mapOf(
                "col" to b.col, "row" to b.row, "x" to b.x, "y" to b.y, "w" to b.w, "h" to b.h,
                "type" to b.type.name.lowercase(),
                "houses" to b.houses.map { h ->
                    mapOf(
                        "x" to h.x, "baseY" to h.baseY, "w" to h.w, "h" to h.h, "wall" to h.wall, "roof" to h.roof,
                        "roofType" to h.roofType, "chimney" to h.chimney, "shutters" to h.shutters, "plantBox" to h.plantBox,
                        "shop" to h.shop, "door" to h.door, "shutterColor" to h.shutterColor, "curtains" to h.curtains,
                        "bakery" to h.bakery,
                    )
                },
                "pond" to b.pond?.let { mapOf("cx" to it.cx, "cy" to it.cy, "rx" to it.rx, "ry" to it.ry) },
                "dots" to b.dots.map { mapOf("x" to it.x, "y" to it.y, "r" to it.r, "color" to it.color, "shape" to it.shape) },
            )
        },
        "crumbs" to s.crumbs.map { mapOf("x" to it.x, "y" to it.y, "big" to it.big) },
        "drawables" to s.drawables.map {
            mapOf("kind" to it.kind, "x" to it.x, "y" to it.y, "scale" to it.scale, "flip" to it.flip, "z" to it.z, "props" to it.props)
        },
        "steps" to s.steps.map { st ->
            mapOf(
                "title" to st.title, "sub" to st.subtitle,
                "hit" to when (val h = st.hit) {
                    is Hit.Circle -> mapOf("type" to "circle", "x" to h.x, "y" to h.y, "r" to h.r)
                    is Hit.Rect -> mapOf("type" to "rect", "x" to h.x, "y" to h.y, "w" to h.w, "h" to h.h)
                },
                "center" to pt(st.centerX, st.centerY), "onFound" to st.onFound, "epilogue" to st.epilogue,
            )
        },
        "bakery" to s.bakery?.let {
            mapOf(
                "rect" to mapOf("x" to it.rect.x, "y" to it.rect.y, "w" to it.rect.w, "h" to it.rect.h),
                "center" to pt(it.center.x, it.center.y), "door" to pt(it.door.x, it.door.y),
            )
        },
    )

    /** Returns a description of the first mismatch, or null if [exp] and [act] are equal (numbers within 1e-6). */
    private fun diff(path: String, exp: Any?, act: Any?): String? {
        when {
            exp == null || act == null -> if (exp != act) return "$path: expected $exp, was $act"
            exp is Map<*, *> -> {
                if (act !is Map<*, *>) return "$path: expected object, was $act"
                for (k in exp.keys) {
                    if (!act.containsKey(k)) return "$path.$k: missing in Kotlin"
                    diff("$path.$k", exp[k], act[k])?.let { return it }
                }
                for (k in act.keys) if (!exp.containsKey(k)) return "$path.$k: extra in Kotlin (value ${act[k]})"
            }
            exp is List<*> -> {
                if (act !is List<*>) return "$path: expected array, was $act"
                for (i in 0 until minOf(exp.size, act.size)) diff("$path[$i]", exp[i], act[i])?.let { return it }
                if (exp.size != act.size) return "$path: expected ${exp.size} elements, was ${act.size}"
            }
            exp is Number -> {
                if (act !is Number) return "$path: expected number $exp, was $act"
                if (abs(exp.toDouble() - act.toDouble()) > 1e-6) return "$path: expected $exp, was $act"
            }
            else -> if (exp != act) return "$path: expected $exp, was $act"
        }
        return null
    }

    @Test
    fun matchesGoldenFromSceneJs() {
        for (lv in Levels.all) {
            val g = golden[lv.id] as Map<*, *>
            val msg = diff(lv.id, g, toTree(SceneBuilder.build(lv)))
            assertTrue(msg == null, "Parity mismatch at $msg")
        }
    }

    @Test
    fun deterministicAndSorted() {
        for (lv in Levels.all) {
            val a = SceneBuilder.build(lv)
            assertEquals(a, SceneBuilder.build(lv))
            assertTrue(a.drawables.zipWithNext().all { (p, q) -> (p.z ?: p.y) <= (q.z ?: q.y) }, "${lv.id} sorted")
        }
    }

    @Test
    fun thiefIsAtEndOfCrumbRoute() {
        val s = SceneBuilder.build(Levels.all.first { it.id == "case1" })
        val thief = s.drawables.single { it.kind == "raccoon" && it.bool("hasCake") }
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
