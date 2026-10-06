// OWNER: CORE
package de.wuselburg.core

import de.wuselburg.core.WorldLayout.BLOCK_H
import de.wuselburg.core.WorldLayout.BLOCK_W
import de.wuselburg.core.WorldLayout.COLS
import de.wuselburg.core.WorldLayout.ROAD
import de.wuselburg.core.WorldLayout.ROWS
import de.wuselburg.core.WorldLayout.roadX
import de.wuselburg.core.WorldLayout.roadY
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** mulberry32 exactly as in scene.js (Math.imul == Int multiply, >>> == ushr). */
internal class Mulberry32(seed: Int) {
    private var a = seed

    fun next(): Double {
        a += 0x6D2B79F5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
    }
}

// ---- palettes (scene.js, verbatim)
private val SHIRT = listOf("#e05a5a", "#4a7fd6", "#f2b84b", "#7bc47f", "#a56bd6", "#ef8fb4", "#5ec2c9", "#d9d9d9", "#ff9f55")
private val SKIN = listOf("#f6d2b0", "#e3a97b", "#b97a52", "#8a5a3b", "#fde0c8")
private val HAIR = listOf("#2d2118", "#6b4423", "#c9a24b", "#b5472b", "#555555", "#111111")
private val GREYS = listOf("#c9c9cf", "#e8e8ee", "#a8a8b3")
private val PANTS = listOf("#3b4a6b", "#5b4a3b", "#444444", "#2f5d62", "#7a5c8a")
private val SHOES = listOf("#3a2b22", "#222222", "#7a4e2d", "#e05a5a", "#ffffff")
private val PATTERN = listOf("#ffffff", "#2b2a33", "#ffd84a", "#e05a5a")
private val HATS = listOf("cap", "beanie", "top", "sun", "band")
private val HAIRSTYLES = listOf("short", "long", "bun", "curly", "bald", "pony", "short", "long")
private val DOGC = listOf("#a0703f", "#6b4a2b", "#d9b98a", "#333333", "#c9884a")
private val CATC = listOf("#555555", "#d98c3f", "#cccccc", "#222222", "#e8d6b8")
private val GREENS = listOf("#4caf50", "#3f9f4a", "#5bbf5a", "#2f8f46")
private val PINKS = listOf("#f4a9c2", "#f7c1d3", "#e98fb2")
private val STALLC = listOf("#e45b4b", "#4a7fd6", "#7bc47f", "#f2b84b")
private val FLOWERC = listOf("#ffffff", "#ffd84a", "#ff9fc2", "#c9a0ff")
private val WALLS = listOf("#f6e0b5", "#f2c9c9", "#cfe3f0", "#e9ecc1", "#f7d9a8", "#e6d3f0")
private val ROOFS = listOf("#c8553d", "#7a5c8a", "#3f7cac", "#b5722a", "#4f8a5b")
private val SHUTTER = listOf("#4a7fd6", "#7bc47f", "#c8553d", "#f2b84b")
private val DOORC = listOf("#8a5a3b", "#7a4e2d", "#c8553d", "#3f7cac", "#5b8a4f")
private val BLANKET = listOf(listOf("#e05a5a", "#ffffff"), listOf("#4a7fd6", "#ffffff"), listOf("#f2b84b", "#fff6dc"))
private val BUSHC2 = listOf("#e05a5a", "#ffd84a", "#ff9fc2")

/** scene.js idx(): clamped index by fraction. */
private fun <T> idx(list: List<T>, v: Double): T = list[min(list.size - 1, floor(v * list.size).toInt())]

private class Spot(val x: Double, val y: Double)
private class Obstacle(val x: Double, val y: Double, val r: Double)
private class Walk(val x: Double, val y: Double, val w: Double, val h: Double, val zone: String)
private class TreeRef(val x: Double, val y: Double, val wide: Boolean)

/** makePerson overrides (null = not overridden, like `undefined`/falsy in JS). */
private class PersonOv(val body: String? = null, val prop: String? = null, val pose: String? = null, val seat: String? = null)

private class GroupDef(val zones: List<String>, val R: Double)

object SceneBuilder {
    /** Deterministically builds the scene for [level] (same seed -> same scene), port of scene.js buildSceneData(). */
    fun build(level: LevelDef): Scene = Builder(level).build()

    private class Builder(val cfg: LevelDef) {
        val rnd = Mulberry32(cfg.seed)
        fun r() = rnd.next()
        fun <T> pick(list: List<T>): T = list[floor(r() * list.size).toInt()]
        private fun sq(v: Double) = v * v

        val walk = ArrayList<Walk>()
        val obstacles = ArrayList<Obstacle>()
        val placed = ArrayList<Spot>()
        val trees = ArrayList<TreeRef>()
        val benches = ArrayList<Spot>()
        val ponds = ArrayList<Pond>()
        val drawables = ArrayList<Drawable>()
        val crumbs = ArrayList<Crumb>()
        val steps = ArrayList<Step>()

        fun d(kind: String, x: Double, y: Double, scale: Double, flip: Boolean, props: Map<String, Any> = emptyMap(), z: Double? = null) =
            Drawable(kind, x, y, scale, flip, z, props)

        fun prop(type: String, x: Double, y: Double, extra: Map<String, Any> = emptyMap(), z: Double? = null) =
            d("prop", x, y, 1.0, false, linkedMapOf<String, Any>("type" to type).apply { putAll(extra) }, z)

        // ---- generators (r() order == scene.js)
        fun makePerson(o: PersonOv = PersonOv()): Map<String, Any> {
            val rb = r(); val rBottom = r(); val rShirt = r(); val rSkin = r(); val rHair = r(); val rPants = r(); val rShoes = r()
            val rStyle = r(); val rHat = r(); val rHatC = r(); val rGl = r(); val rBeard = r(); val rPat = r(); val rPatC = r()
            val rBag = r(); val rProp = r(); val rPropC = r()
            val body = o.body ?: (if (rb < 0.7) "adult" else if (rb < 0.88) "kid" else "senior")
            val bottom = idx(listOf("pants", "pants", "skirt", "dress"), rBottom)
            var prop = if (rProp < 0.07) "balloon" else if (rProp < 0.11) "icecream" else if (rProp < 0.15) "umbrella" else "none"
            if (o.prop != null) prop = o.prop
            val m = LinkedHashMap<String, Any>()
            m["body"] = body
            m["bottom"] = bottom
            m["shirt"] = idx(SHIRT, rShirt)
            m["skin"] = idx(SKIN, rSkin)
            m["hair"] = if (body == "senior") idx(GREYS, rHair) else idx(HAIR, rHair)
            m["pants"] = idx(PANTS, rPants)
            m["shoes"] = idx(SHOES, rShoes)
            m["hairStyle"] = idx(HAIRSTYLES, rStyle)
            m["hat"] = if (rHat < 0.42) idx(HATS, rHat / 0.42) else "none"
            m["hatColor"] = idx(SHIRT, rHatC)
            m["glasses"] = rGl < 0.14
            m["beard"] = body == "adult" && bottom == "pants" && rBeard < 0.12
            m["pattern"] = idx(listOf("none", "none", "stripes", "dots", "jacket"), rPat)
            m["patternColor"] = idx(PATTERN, rPatC)
            m["bag"] = idx(listOf("none", "none", "none", "backpack", "tote"), rBag)
            m["prop"] = prop
            m["propColor"] = idx(SHIRT, rPropC)
            m["pose"] = o.pose ?: "stand"
            m["seat"] = o.seat ?: "ground"
            return m
        }

        fun makeDog(): Map<String, Any> {
            val rb = r(); val rc = r()
            return mapOf("breed" to idx(listOf("lab", "dachs", "spot", "poodle"), rb), "color" to idx(DOGC, rc))
        }

        fun makeCat(): Map<String, Any> {
            val rb = r(); val rc = r(); val rs = r()
            return mapOf("pose" to (if (rb < 0.5) "stand" else "sit"), "color" to idx(CATC, rc), "stripes" to (rs < 0.4))
        }

        fun makeTree(): Map<String, Any> {
            val rt = r(); val rc = r()
            val type = idx(listOf("round", "round", "pine", "birch", "blossom", "apple"), rt)
            val color = if (type == "blossom") idx(PINKS, rc) else idx(GREENS, rc)
            return mapOf("type" to type, "color" to color, "wide" to (type == "round" || type == "blossom" || type == "apple"))
        }

        fun makeStall(): Map<String, Any> {
            val rc = r(); val rg = r()
            return mapOf("color" to idx(STALLC, rc), "goods" to idx(listOf("fruit", "veg", "flowers", "bread", "fish"), rg))
        }

        fun makeHouse(x: Double, baseY: Double, w: Double): House {
            val rh = r(); val rw = r(); val rr = r(); val rt = r(); val rch = r(); val rsh = r(); val rpb = r()
            val rshop = r(); val rdoor = r(); val rshc = r()
            val h = 150.0 + floor(rh * 3) * 25
            val floors = if (h > 150) 2 else 1
            val curtains = ArrayList<String>()
            for (i in 0 until floors * 2) { val a = r(); val b = r(); curtains += if (a < 0.4) idx(SHIRT, b) else "" }
            return House(
                x, baseY, w, h, idx(WALLS, rw), idx(ROOFS, rr),
                idx(listOf("gable", "gable", "flat", "steep"), rt),
                if (rshop < 0.3) idx(listOf("cafe", "flowers", "books"), rshop / 0.3) else "",
                idx(DOORC, rdoor), idx(SHUTTER, rshc),
                rch < 0.5, rsh < 0.4, rpb < 0.35, false, curtains,
            )
        }

        // ---- street furniture
        fun addTree(x: Double, y: Double) {
            val t = makeTree()
            trees += TreeRef(x, y, t["wide"] as Boolean)
            obstacles += Obstacle(x, y, 22.0)
            drawables += d("tree", x, y, 1.0, false, t)
        }

        fun addBench(x: Double, y: Double) {
            benches += Spot(x, y)
            obstacles += Obstacle(x, y - 6, 24.0)
            drawables += prop("bench", x, y)
        }

        fun addLamp(x: Double, y: Double) {
            obstacles += Obstacle(x, y, 8.0)
            drawables += prop("lamp", x, y)
        }

        fun addBush(x: Double, y: Double) {
            val a = r(); val b = r()
            obstacles += Obstacle(x, y, 16.0)
            drawables += prop("bush", x, y, mapOf("color" to idx(GREENS, a), "color2" to idx(BUSHC2, b)))
        }

        // ---- placement helpers
        fun free(x: Double, y: Double, dd: Double): Boolean =
            obstacles.none { sq(it.x - x) + sq(it.y - y) < sq(it.r + 8) } &&
                placed.none { sq(it.x - x) + sq(it.y - y) < dd * dd }

        fun spotIn(zones: List<String>, tries: Int, filter: (Double, Double) -> Boolean): Spot? {
            val rects = walk.filter { it.zone in zones }
            var area = 0.0
            for (w in rects) area += w.w * w.h
            for (n in 0 until tries) {
                var t = r() * area
                var rect = rects[0]
                for (w in rects) { t -= w.w * w.h; if (t <= 0) { rect = w; break } }
                val a = r(); val c = r()
                val px = rect.x + a * rect.w
                val py = rect.y + c * rect.h
                if (filter(px, py)) return Spot(px, py)
            }
            return null
        }

        val ALL = listOf("road", "park", "market", "plaza", "sidewalk")
        fun randomSpot(): Spot = spotIn(ALL, 1) { _, _ -> true }!!

        fun freeSpot(dd: Double, tries: Int): Spot {
            var s = randomSpot()
            var t = 0
            while (!free(s.x, s.y, dd) && t++ < tries) s = randomSpot()
            return s
        }

        fun addActor(kind: String, x: Double, y: Double, scale: Double, flip: Boolean, props: Map<String, Any> = emptyMap()) {
            placed += Spot(x, y)
            drawables += d(kind, x, y, scale, flip, props)
        }

        fun person(o: PersonOv = PersonOv()) = makePerson(o)

        // ---- groups
        fun sgn(): Int = if (r() < 0.5) 1 else -1

        fun placeGroup(kind: String, ax: Double, ay: Double) {
            when (kind) {
                "picnic" -> {
                    val col = idx(BLANKET, r())
                    drawables += d("prop", ax, ay, 1.0, false, linkedMapOf("type" to "blanket", "color" to col[0], "color2" to col[1]), z = ay - 60)
                    drawables += prop("basket", ax - 2, ay - 22)
                    listOf(-34.0 to -16.0, 8.0 to -8.0, 38.0 to -20.0).forEachIndexed { i, (dx, dy) ->
                        addActor("person", ax + dx, ay + dy, 1.0, i > 0, person(PersonOv(pose = "sit")))
                    }
                    if (r() < 0.6) { val dg = makeDog(); addActor("dog", ax + 64, ay + 6, 0.95, true, dg) }
                }
                "musician" -> {
                    val s = sgn()
                    addActor("person", ax, ay, 1.0, s < 0, person(PersonOv(body = "adult", prop = "guitar")))
                    drawables += prop("guitarcase", ax - 30.0 * s, ay + 10)
                    drawables += prop("notes", ax + 14.0 * s, ay - 62)
                    val n = 2 + floor(r() * 3).toInt()
                    for (i in 0 until n) {
                        val a = r()
                        addActor(
                            "person", ax + (48 + i * 24) * s, ay + 4 - (i % 2) * 8 + a * 6, 1.0, s > 0,
                            person(PersonOv(prop = if (i == 0) "none" else null)),
                        )
                    }
                }
                "icecream" -> {
                    val s = sgn()
                    drawables += prop("cart", ax, ay, mapOf("color" to idx(STALLC, r())))
                    addActor("person", ax + 36.0 * s, ay - 4, 1.0, s > 0, person(PersonOv(body = "adult", prop = "none")))
                    for (i in 0 until 3) {
                        addActor(
                            "person", ax - (30 + i * 26) * s, ay + 8 + (i % 2) * 4, 1.0, s < 0,
                            person(PersonOv(body = if (i == 1) "kid" else null, prop = if (i < 2) "icecream" else null)),
                        )
                    }
                }
                "dogwalk" -> {
                    val s = sgn()
                    addActor("person", ax, ay, 1.0, s < 0, person(PersonOv(body = "adult", prop = "none")))
                    val dg = makeDog()
                    addActor("dog", ax + 40.0 * s, ay + 6, 0.95, s < 0, dg)
                    drawables += d(
                        "prop", ax + 10.0 * s, ay - 24, 1.0, false,
                        linkedMapOf("type" to "leash", "dx" to 28.0 * s, "dy" to 12.0), z = ay + 7,
                    )
                }
                "photo" -> {
                    val s = sgn()
                    addActor("person", ax, ay, 1.0, s < 0, person(PersonOv(body = "adult", prop = "camera")))
                    listOf(56.0 to -6.0, 80.0 to 2.0, 104.0 to -4.0).forEachIndexed { i, (dx, dy) ->
                        addActor("person", ax + dx * s, ay + dy, 1.0, s > 0, person(PersonOv(body = if (i == 1) "kid" else null, prop = "none")))
                    }
                }
                "ball" -> {
                    listOf(-38.0 to 4.0, 38.0 to -2.0, 0.0 to 24.0).forEach { (dx, dy) ->
                        addActor("person", ax + dx, ay + dy, 1.0, dx > 0, person(PersonOv(body = "kid", prop = "none")))
                    }
                    drawables += prop("ball", ax, ay + 10, mapOf("lift" to 30.0))
                }
                "painter" -> {
                    val s = sgn()
                    addActor("person", ax, ay, 1.0, s < 0, person(PersonOv(body = "adult", prop = "none")))
                    drawables += prop("easel", ax + 36.0 * s, ay + 2)
                    if (r() < 0.6) addActor("person", ax - 40.0 * s, ay + 10, 1.0, s < 0, person(PersonOv(body = "senior", prop = "none")))
                }
            }
        }

        fun placeAngler(pond: Pond) {
            val ax = pond.cx - pond.rx - 12
            val ay = pond.cy + 8
            addActor("person", ax, ay, 1.0, false, person(PersonOv(pose = "sit", prop = "none")))
            drawables += prop("rod", ax + 8, ay - 22, mapOf("dx" to 44.0, "dy" to 22.0))
            drawables += prop("bucket", ax - 20, ay + 6)
        }

        fun <T> shuffle(list: MutableList<T>) {
            for (i in list.size - 1 downTo 1) {
                val j = floor(r() * (i + 1)).toInt()
                val tmp = list[i]; list[i] = list[j]; list[j] = tmp
            }
        }

        fun build(): SceneData {
            val mood = cfg.mood
            val types = mutableListOf(
                BlockType.PARK, BlockType.PARK, BlockType.MARKET, BlockType.PLAZA,
                *Array(8) { BlockType.HOUSES },
            )
            shuffle(types)

            class Cell(val col: Int, val row: Int, val x: Double, val y: Double, val type: BlockType)
            val cells = ArrayList<Cell>()
            for (row in 0 until ROWS) for (col in 0 until COLS) {
                cells += Cell(col, row, ROAD + col * (BLOCK_W + ROAD), ROAD + row * (BLOCK_H + ROAD), types[row * COLS + col])
            }
            val bakeryCell = if (cfg.kind == LevelKind.CASE) pick(cells.filter { it.type == BlockType.HOUSES }) else null
            var bakeryHit: BakeryInfo? = null

            for (k in 0..COLS) walk += Walk(k * (BLOCK_W + ROAD) + 14, 14.0, ROAD - 28, WORLD_H - 28, "road")
            for (j in 0..ROWS) walk += Walk(14.0, j * (BLOCK_H + ROAD) + 14, WORLD_W - 28, ROAD - 28, "road")

            val blocks = ArrayList<Block>()
            for (b in cells) {
                val x = b.x; val y = b.y
                fun block(houses: List<House> = emptyList(), pond: Pond? = null, dots: List<Dot> = emptyList()) =
                    Block(b.col, b.row, x, y, BLOCK_W, BLOCK_H, b.type, houses, pond, dots)
                when (b.type) {
                    BlockType.PARK -> {
                        val px = x + 120 + r() * 180
                        val py = y + 120 + r() * 140
                        val pond = Pond(px, py, 60.0, 40.0)
                        ponds += pond
                        obstacles += Obstacle(px, py, 55.0)
                        val dots = ArrayList<Dot>()
                        for (i in 0 until 40) {
                            val a = r(); val c = r(); val e = r()
                            dots += Dot(x + 14 + a * (BLOCK_W - 28), y + 14 + c * (BLOCK_H - 28), 2.2, idx(FLOWERC, e), "circle")
                        }
                        for (i in 0 until 26) {
                            val a = r(); val c = r()
                            dots += Dot(x + 14 + a * (BLOCK_W - 28), y + 14 + c * (BLOCK_H - 28), 0.0, "#6fb85f", "tuft")
                        }
                        // keep trees and bushes out of the water (scene.js dry())
                        fun dry(tx: Double, ty: Double, m: Double): Pair<Double, Double> {
                            val dx = tx - px; val dy = ty - py
                            val q = hypot(dx / (pond.rx + m), dy / (pond.ry + m))
                            if (q >= 1) return tx to ty
                            if (q < 1e-6) return tx to (py + pond.ry + m)
                            return (px + dx / q * 1.02) to (py + dy / q * 1.02)
                        }
                        for (i in 0 until 7) { val a = r(); val c = r(); val (tx, ty) = dry(x + 40 + a * (BLOCK_W - 80), y + 80 + c * (BLOCK_H - 90), 16.0); addTree(tx, ty) }
                        for (i in 0 until 2) { val a = r(); addBench(x + 70 + a * (BLOCK_W - 140), y + BLOCK_H - 26) }
                        for (i in 0 until 3) { val a = r(); val c = r(); val (bx, by) = dry(x + 30 + a * (BLOCK_W - 60), y + 40 + c * (BLOCK_H - 80), 12.0); addBush(bx, by) }
                        for (i in 0 until 3) {
                            val a = r(); val c = r()
                            drawables += d("duck", px - 40 + a * 80, py - 14 + c * 28, 0.9, a < 0.5)
                        }
                        walk += Walk(x + 20, y + 20, BLOCK_W - 40, BLOCK_H - 30, "park")
                        blocks += block(pond = pond, dots = dots)
                    }
                    BlockType.MARKET -> {
                        val dots = ArrayList<Dot>()
                        for (i in 0 until 24) {
                            val a = r(); val c = r()
                            dots += Dot(x + 12 + a * (BLOCK_W - 24), y + 12 + c * (BLOCK_H - 24), 3.0, "#e3c88f", "circle")
                        }
                        listOf(
                            x + 90 to y + 120, x + 210 to y + 120, x + 330 to y + 120,
                            x + 130 to y + 290, x + 290 to y + 290,
                        ).forEach { (sx, sy) ->
                            drawables += d("stall", sx, sy, 1.0, false, makeStall())
                            obstacles += Obstacle(sx, sy - 10, 46.0)
                        }
                        addTree(x + 30, y + 40); addTree(x + BLOCK_W - 30, y + BLOCK_H - 20)
                        addLamp(x + BLOCK_W / 2, y + 205)
                        walk += Walk(x + 14, y + 14, BLOCK_W - 28, BLOCK_H - 20, "market")
                        blocks += block(dots = dots)
                    }
                    BlockType.PLAZA -> {
                        val cx = x + BLOCK_W / 2; val cy = y + BLOCK_H / 2
                        obstacles += Obstacle(cx, cy, 66.0)
                        addTree(x + 36, y + 50); addTree(x + BLOCK_W - 36, y + 50)
                        addTree(x + 36, y + BLOCK_H - 20); addTree(x + BLOCK_W - 36, y + BLOCK_H - 20)
                        addBench(x + 90, y + 100); addBench(x + BLOCK_W - 90, y + 100); addBench(x + BLOCK_W / 2, y + BLOCK_H - 22)
                        addLamp(x + 60, y + 230); addLamp(x + BLOCK_W - 60, y + 230)
                        for (f in 0 until 2) {
                            val a = r(); val c = r(); val n = 2 + floor(r() * 3).toInt()
                            val fx = x + 70 + a * (BLOCK_W - 140)
                            val fy = y + 150 + c * 160
                            for (i in 0 until n) {
                                val e = r(); val g = r()
                                drawables += d("pigeon", fx + (e - .5) * 40, fy + (g - .5) * 24, 1.0, e < .5)
                            }
                        }
                        walk += Walk(x + 20, y + 20, BLOCK_W - 40, BLOCK_H - 28, "plaza")
                        blocks += block()
                    }
                    BlockType.HOUSES -> {
                        val baseY = y + BLOCK_H - 85
                        val houses = ArrayList<House>()
                        for (i in 0 until 3) {
                            val hx = x + 20 + i * 140
                            var hd = makeHouse(hx, baseY, 120.0)
                            if (b === bakeryCell && i == 1) {
                                hd = hd.copy(
                                    wall = "#fbe9c8", roof = "#c8553d", roofType = "gable", shop = "",
                                    plantBox = false, bakery = true, door = "#7a4e2d",
                                )
                                bakeryHit = BakeryInfo(
                                    RectD(hx - 7, baseY - hd.h - 46, 134.0, hd.h + 46),
                                    PointD(hx + 60, baseY - hd.h / 2),
                                    PointD(hx + 60, baseY + 20),
                                )
                            }
                            houses += hd
                        }
                        addTree(x + 140, baseY + 30); addTree(x + 280, baseY + 30)
                        addLamp(x + 14, baseY + 40); addLamp(x + BLOCK_W - 14, baseY + 40)
                        walk += Walk(x + 10, y + BLOCK_H - 66, BLOCK_W - 20, 50.0, "sidewalk")
                        blocks += block(houses = houses)
                    }
                }
            }

            // ---- target figures
            if (cfg.kind == LevelKind.FIPS) buildFipsTarget() else buildCase(bakeryCell!!.col, bakeryCell.row, bakeryHit!!)

            // ---- seated people on benches
            for (bn in benches) {
                val a = r(); val c = r()
                if (a < 0.6) {
                    val p = person(PersonOv(pose = "sit", seat = "bench"))
                    addActor("person", bn.x - 6 + c * 12, bn.y + 1, 1.0, c < .5, p)
                }
            }

            // ---- scene groups
            val groups = mapOf(
                "picnic" to GroupDef(listOf("park"), 70.0),
                "musician" to GroupDef(listOf("plaza", "market", "sidewalk"), 80.0),
                "icecream" to GroupDef(listOf("plaza", "park", "market"), 80.0),
                "dogwalk" to GroupDef(listOf("road", "sidewalk", "park"), 55.0),
                "photo" to GroupDef(listOf("plaza", "park"), 90.0),
                "ball" to GroupDef(listOf("park", "plaza"), 60.0),
                "painter" to GroupDef(listOf("park", "plaza", "market"), 60.0),
            )
            val kinds = mutableListOf("picnic", "musician", "icecream", "dogwalk", "photo", "ball", "painter", "angler")
            shuffle(kinds)
            for (gi in 0 until cfg.groups) {
                val kind = kinds[gi % kinds.size]
                if (kind == "angler") {
                    val pond = if (ponds.isNotEmpty()) pick(ponds) else null
                    val s0 = steps.firstOrNull()
                    val fipsNear = s0 != null &&
                        hypot(s0.centerX - (if (pond != null) pond.cx - pond.rx else 0.0), s0.centerY - (pond?.cy ?: 0.0)) < 90
                    if (pond != null && !fipsNear) {
                        placeAngler(pond)
                        obstacles += Obstacle(pond.cx - pond.rx - 12, pond.cy + 8, 40.0)
                    }
                    continue
                }
                val gd = groups.getValue(kind)
                val spot = spotIn(gd.zones, 80) { x, y ->
                    !obstacles.any { sq(it.x - x) + sq(it.y - y) < sq(it.r + gd.R * 0.6) } &&
                        !trees.any { abs(it.x - x) < gd.R * 0.9 && it.y - y > -10 && it.y - y < 110 } &&
                        !placed.any { sq(it.x - x) + sq(it.y - y) < gd.R * gd.R }
                } ?: continue
                placeGroup(kind, spot.x, spot.y)
                obstacles += Obstacle(spot.x, spot.y - 10, gd.R * 0.7)
            }

            // ---- decoys, raccoons, population
            val kinds2 = cfg.decoys
            for (i in 0 until cfg.foxes) {
                val s = freeSpot(34.0, 40)
                val sc = 0.92 + r() * 0.16; val fl = r() < .5
                addActor("fox", s.x, s.y, sc, fl, mapOf("style" to kinds2[i % kinds2.size]))
            }
            for (i in 0 until cfg.raccoons) {
                val s = freeSpot(34.0, 40)
                val sc = 0.95 + r() * 0.15; val fl = r() < .5
                addActor("raccoon", s.x, s.y, sc, fl, mapOf("hasCake" to false))
            }
            val rest = max(0, cfg.crowd - cfg.foxes - cfg.raccoons)
            for (i in 0 until rest) {
                val s = freeSpot(30.0, 40)
                val roll = r(); val sc = 0.9 + r() * 0.2; val fl = r() < .5
                if (roll < 0.09) addActor("dog", s.x, s.y, sc, fl, makeDog())
                else if (roll < 0.16) addActor("cat", s.x, s.y, sc, fl, makeCat())
                else if (roll < 0.19) addActor("pigeon", s.x, s.y, 1.0, fl)
                else addActor("person", s.x, s.y, sc, fl, makePerson())
            }

            val sorted = drawables.sortedBy { it.z ?: it.y } // stable, like the indexed JS sort
            return SceneData(mood, blocks, crumbs, sorted, steps, bakeryHit)
        }

        private fun buildFipsTarget() {
            val fx: Double
            val fy: Double
            if (cfg.hidden) {
                val t = pick(trees.filter { it.wide && it.x > 60 && it.x < WORLD_W - 60 && it.y > 120 && it.y < WORLD_H - 60 })
                val side = if (r() < 0.5) -1 else 1
                fx = t.x + side * cfg.hideOffset
                fy = t.y - 4
            } else {
                val s = freeSpot(40.0, 80)
                fx = s.x; fy = s.y
            }
            addActor("fox", fx, fy, 1.0, false, mapOf("style" to "fips"))
            steps += Step(
                "Finde Fips!", "Fuchs mit türkisem Schal, gelben Punkten und weißer Schwanzspitze",
                Hit.Circle(fx, fy - 28, 30.0), fx, fy - 28,
            )
        }

        private fun buildCase(bCol: Int, bRow: Int, bakery: BakeryInfo) {
            val sx = bakery.door.x; val sy = bakery.door.y
            val yh = roadY(bRow + 1)
            val cands = (0..4).filter { abs(it - bCol) >= 2 }
            val kv = pick(cands)
            val jt = if (bRow + 1 <= 1) 3 else 0
            val yt = roadY(jt)
            val dir = if (roadX(kv) > sx) 1 else -1
            val sideSign = if (r() < 0.5) -1 else 1
            val tx = min(WORLD_W - 80, max(80.0, roadX(kv) + sideSign * (150 + r() * 150)))
            val route = listOf(sx to sy, sx to yh, roadX(kv) to yh, roadX(kv) to yt, tx to yt)
            dropCrumbs(route)
            val fEnd = min(WORLD_W - 60, max(60.0, roadX(kv) + dir * 330)) to yh
            dropCrumbs(listOf(roadX(kv) to yh, fEnd))
            for (i in 0 until 3) {
                val a = r(); val c = r()
                addActor("pigeon", fEnd.first + dir * (14 + i * 20), fEnd.second + (a - .5) * 20, 1.0, c < .5)
            }
            val thiefX = tx
            val thiefY = yt + 12
            addActor("raccoon", thiefX, thiefY, 1.05, r() < .5, mapOf("hasCake" to true))
            steps += Step(
                "Finde die Bäckerei!", "Dort fehlt der Geburtstagskuchen",
                Hit.Rect(bakery.rect.x, bakery.rect.y, bakery.rect.w, bakery.rect.h),
                bakery.center.x, bakery.center.y,
                onFound = "Hier stand der Kuchen! Auf dem Boden liegen Krümel …",
            )
            steps += Step(
                "Wer hat den Kuchen?", "Folge der Krümelspur bis zum Dieb",
                Hit.Circle(thiefX, thiefY - 28, 32.0), thiefX, thiefY - 28,
                epilogue = "Erwischt! Waschbär Rocky hatte großen Hunger – und hat den Kuchen mit allen Tauben der Stadt geteilt. Fast.",
            )
        }

        private fun dropCrumbs(pts: List<Pair<Double, Double>>) {
            for (i in 0 until pts.size - 1) {
                val (ax, ay) = pts[i]
                val (bx, by) = pts[i + 1]
                val n = floor(hypot(bx - ax, by - ay) / 26).toInt()
                for (m in 0 until n) {
                    val t = m.toDouble() / n
                    val p1 = r(); val p2 = r()
                    crumbs += Crumb(ax + (bx - ax) * t + (p1 - .5) * 10, ay + (by - ay) * t + (p2 - .5) * 10, m % 3 == 0)
                }
            }
        }
    }
}
