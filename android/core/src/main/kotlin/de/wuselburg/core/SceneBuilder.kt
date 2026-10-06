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

/** mulberry32 exactly as in game.js (Math.imul == Int multiply, >>> == ushr). */
internal class Mulberry32(seed: Int) {
    private var a = seed

    fun next(): Double {
        a += 0x6D2B79F5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
    }
}

/** '#rrggbb' or '#rgb' -> opaque ARGB Int. */
private fun hex(s: String): Int {
    val h = s.removePrefix("#").let { if (it.length == 3) it.map { c -> "$c$c" }.joinToString("") else it }
    return (0xFF000000L or h.toLong(16)).toInt()
}

private val SHIRT = listOf("#e05a5a", "#4a7fd6", "#f2b84b", "#7bc47f", "#a56bd6", "#ef8fb4", "#5ec2c9", "#d9d9d9", "#ff9f55").map(::hex)
private val SKIN = listOf("#f6d2b0", "#e3a97b", "#b97a52", "#8a5a3b", "#fde0c8").map(::hex)
private val HAIR = listOf("#2d2118", "#6b4423", "#c9a24b", "#b5472b", "#555", "#111").map(::hex)
private val PANTS = listOf("#3b4a6b", "#5b4a3b", "#444", "#2f5d62", "#7a5c8a").map(::hex)
private val TREE = listOf("#4caf50", "#3f9f4a", "#5bbf5a", "#2f8f46").map(::hex)
private val STALL = listOf("#e45b4b", "#4a7fd6", "#7bc47f", "#f2b84b").map(::hex)
private val ROOFS = listOf("#c8553d", "#7a5c8a", "#3f7cac", "#b5722a").map(::hex)
private val WALLS = listOf("#f6e0b5", "#f2c9c9", "#cfe3f0", "#e9ecc1", "#f7d9a8").map(::hex)
private val FLOWERS = listOf("#fff", "#ffd84a", "#ff9fc2").map(::hex)
private val DOGS = listOf("#a0703f", "#6b4a2b", "#d9b98a", "#333").map(::hex)
private val CATS = listOf("#555", "#d98c3f", "#ccc", "#222").map(::hex)
private val HATS = listOf(HatStyle.CAP, HatStyle.BEANIE, HatStyle.TOP, HatStyle.NONE)
private val SAND = hex("#e3c88f")
private const val BAKERY_WALL = "#fbe9c8"
private const val BAKERY_ROOF = "#c8553d"

private class Spot(val x: Double, val y: Double)
private class Obstacle(val x: Double, val y: Double, val r: Double)
private class Walk(val x: Double, val y: Double, val w: Double, val h: Double)
private class Cell(val col: Int, val row: Int, val x: Double, val y: Double, val type: BlockType)

object SceneBuilder {
    /** Deterministically builds the scene for [level] (same seed -> same scene), port of game.js buildScene(). */
    fun build(level: LevelDef): Scene = Builder(level).build()

    private class Builder(val cfg: LevelDef) {
        val rnd = Mulberry32(cfg.seed)
        fun r() = rnd.next()
        fun <T> pick(list: List<T>): T = list[floor(r() * list.size).toInt()]

        val walk = ArrayList<Walk>()
        val obstacles = ArrayList<Obstacle>()
        val placed = ArrayList<Spot>()
        val trees = ArrayList<Spot>()
        val drawables = ArrayList<Drawable>()
        val crumbs = ArrayList<Crumb>()
        val steps = ArrayList<Step>()

        fun addTree(x: Double, y: Double) {
            trees += Spot(x, y)
            obstacles += Obstacle(x, y, 22.0)
            drawables += TreeD(x, y, 1.0, false, pick(TREE))
        }

        fun free(x: Double, y: Double, d: Double): Boolean =
            obstacles.none { sq(it.x - x) + sq(it.y - y) < sq(it.r + 8) } &&
                placed.none { sq(it.x - x) + sq(it.y - y) < d * d }

        private fun sq(v: Double) = v * v

        val areaSum: Double get() = walk.sumOf { it.w * it.h }

        fun randomSpot(): Spot {
            var t = r() * areaSum
            var rect = walk[0]
            for (w in walk) {
                t -= w.w * w.h
                if (t <= 0) { rect = w; break }
            }
            val x = rect.x + r() * rect.w
            val y = rect.y + r() * rect.h
            return Spot(x, y)
        }

        fun freeSpot(d: Double, tries: Int): Spot {
            var s = randomSpot()
            var n = 0
            while (!free(s.x, s.y, d) && n++ < tries) s = randomSpot()
            return s
        }

        fun addActor(d: Drawable) {
            placed += Spot(d.x, d.y)
            drawables += d
        }

        fun person(x: Double, y: Double, sc: Double, flip: Boolean): PersonD {
            val shirt = pick(SHIRT); val skin = pick(SKIN); val hair = pick(HAIR); val pants = pick(PANTS)
            val hat = if (r() < 0.5) pick(HATS) else HatStyle.NONE
            val hc = pick(SHIRT)
            val balloon = if (r() < 0.09) pick(SHIRT) else 0
            return PersonD(x, y, sc, flip, shirt, skin, hair, pants, hat, hc, balloon)
        }

        fun build(): Scene {
            val types = mutableListOf(
                BlockType.PARK, BlockType.PARK, BlockType.MARKET, BlockType.PLAZA,
                *Array(8) { BlockType.HOUSES },
            )
            // Fisher-Yates, same as game.js shuffle()
            for (i in types.size - 1 downTo 1) {
                val j = floor(r() * (i + 1)).toInt()
                types[i] = types[j].also { types[j] = types[i] }
            }
            val cells = ArrayList<Cell>()
            for (row in 0 until ROWS) for (col in 0 until COLS) {
                cells += Cell(col, row, ROAD + col * (BLOCK_W + ROAD), ROAD + row * (BLOCK_H + ROAD), types[row * COLS + col])
            }
            val bakeryCell = if (cfg.kind == LevelKind.CASE) pick(cells.filter { it.type == BlockType.HOUSES }) else null
            var bakery: Step? = null
            var door = Spot(0.0, 0.0)

            for (k in 0..COLS) walk += Walk(k * (BLOCK_W + ROAD) + 14, 14.0, ROAD - 28, WORLD_H - 28)
            for (j in 0..ROWS) walk += Walk(14.0, j * (BLOCK_H + ROAD) + 14, WORLD_W - 28, ROAD - 28)

            val blocks = ArrayList<Block>()
            for (b in cells) {
                val x = b.x; val y = b.y
                val rect = RectD(x, y, BLOCK_W, BLOCK_H)
                when (b.type) {
                    BlockType.PARK -> {
                        val px = x + 120 + r() * 180
                        val py = y + 120 + r() * 140
                        obstacles += Obstacle(px, py, 55.0)
                        val dots = List(40) {
                            val dx = x + 14 + r() * (BLOCK_W - 28)
                            val dy = y + 14 + r() * (BLOCK_H - 28)
                            Dot(dx, dy, pick(FLOWERS))
                        }
                        repeat(7) { addTree(x + 40 + r() * (BLOCK_W - 80), y + 80 + r() * (BLOCK_H - 90)) }
                        walk += Walk(x + 20, y + 20, BLOCK_W - 40, BLOCK_H - 30)
                        blocks += Block(rect, b.type, pond = Pond(px, py, 70.0, 44.0), dots = dots)
                    }
                    BlockType.MARKET -> {
                        val dots = List(24) {
                            val dx = x + 12 + r() * (BLOCK_W - 24)
                            val dy = y + 12 + r() * (BLOCK_H - 24)
                            Dot(dx, dy, SAND)
                        }
                        listOf(
                            x + 90 to y + 120, x + 210 to y + 120, x + 330 to y + 120,
                            x + 130 to y + 290, x + 290 to y + 290,
                        ).forEach { (sx, sy) ->
                            drawables += StallD(sx, sy, 1.0, false, pick(STALL))
                            obstacles += Obstacle(sx, sy - 10, 46.0)
                        }
                        addTree(x + 30, y + 40); addTree(x + BLOCK_W - 30, y + BLOCK_H - 20)
                        walk += Walk(x + 14, y + 14, BLOCK_W - 28, BLOCK_H - 20)
                        blocks += Block(rect, b.type, dots = dots)
                    }
                    BlockType.PLAZA -> {
                        val cx = x + BLOCK_W / 2; val cy = y + BLOCK_H / 2
                        obstacles += Obstacle(cx, cy, 66.0)
                        addTree(x + 36, y + 50); addTree(x + BLOCK_W - 36, y + 50)
                        addTree(x + 36, y + BLOCK_H - 20); addTree(x + BLOCK_W - 36, y + BLOCK_H - 20)
                        walk += Walk(x + 20, y + 20, BLOCK_W - 40, BLOCK_H - 28)
                        blocks += Block(rect, b.type, pond = Pond(cx, cy, 62.0, 62.0))
                    }
                    BlockType.HOUSES -> {
                        val baseY = y + BLOCK_H - 85
                        val houses = ArrayList<House>()
                        for (i in 0 until 3) {
                            val hx = x + 20 + i * 140
                            val hh = 150.0 + floor(r() * 3) * 25
                            val isBakery = b === bakeryCell && i == 1
                            val wall = if (isBakery) hex(BAKERY_WALL) else pick(WALLS)
                            val roof = if (isBakery) hex(BAKERY_ROOF) else pick(ROOFS)
                            val windows = if (hh > 150) 4 else 2
                            val curtains = List(windows) { if (r() < 0.4) pick(SHIRT) else 0 }
                            houses += House(hx, baseY, 120.0, hh, wall, roof, isBakery, curtains)
                            if (isBakery) {
                                bakery = Step(
                                    "Finde die Bäckerei!", "Dort fehlt der Geburtstagskuchen",
                                    Hit.Rect(hx - 7, baseY - hh - 46, 134.0, hh + 46),
                                    hx + 60, baseY - hh / 2,
                                    onFound = "Hier stand der Kuchen! Auf dem Boden liegen Krümel …",
                                )
                                door = Spot(hx + 60, baseY + 20)
                            }
                        }
                        addTree(x + 140, baseY + 30); addTree(x + 280, baseY + 30)
                        walk += Walk(x + 10, y + BLOCK_H - 66, BLOCK_W - 20, 50.0)
                        blocks += Block(rect, b.type, houses = houses)
                    }
                }
            }

            if (cfg.kind == LevelKind.FIPS) buildFipsTarget() else buildCase(bakeryCell!!, bakery!!, door)

            val kinds = cfg.decoys
            for (i in 0 until cfg.foxes) {
                val s = freeSpot(34.0, 40)
                val sc = 0.92 + r() * 0.16
                val flip = r() < .5
                addActor(FoxD(s.x, s.y, sc, flip, kinds[i % kinds.size]))
            }
            for (i in 0 until cfg.raccoons) {
                val s = freeSpot(34.0, 40)
                val sc = 0.95 + r() * 0.15
                val flip = r() < .5
                addActor(RaccoonD(s.x, s.y, sc, flip, false))
            }
            val rest = max(0, cfg.crowd - cfg.foxes - cfg.raccoons)
            for (i in 0 until rest) {
                val s = freeSpot(30.0, 40)
                val roll = r()
                val sc = 0.9 + r() * 0.2
                val flip = r() < .5
                addActor(
                    when {
                        roll < 0.1 -> DogD(s.x, s.y, sc, flip, pick(DOGS))
                        roll < 0.17 -> CatD(s.x, s.y, sc, flip, pick(CATS))
                        else -> person(s.x, s.y, sc, flip)
                    },
                )
            }

            return Scene(blocks, crumbs, drawables.sortedBy { it.y }, steps)
        }

        private fun buildFipsTarget() {
            val fx: Double
            val fy: Double
            if (cfg.hidden) {
                val t = pick(trees.filter { it.x > 60 && it.x < WORLD_W - 60 && it.y > 120 && it.y < WORLD_H - 60 })
                val side = if (r() < 0.5) -1 else 1
                fx = t.x + side * cfg.hideOffset
                fy = t.y - 4
            } else {
                val s = freeSpot(40.0, 80)
                fx = s.x; fy = s.y
            }
            addActor(FoxD(fx, fy, 1.0, false, FoxStyle.FIPS))
            steps += Step(
                "Finde Fips!", "Fuchs mit türkisem Schal, gelben Punkten und weißer Schwanzspitze",
                Hit.Circle(fx, fy - 28, 30.0), fx, fy - 28,
            )
        }

        private fun buildCase(b: Cell, bakeryStep: Step, door: Spot) {
            val yh = roadY(b.row + 1)
            val cands = (0..4).filter { abs(it - b.col) >= 2 }
            val kv = pick(cands)
            val jt = if (b.row + 1 <= 1) 3 else 0
            val yt = roadY(jt)
            val dir = if (roadX(kv) > door.x) 1 else -1
            val tx = min(WORLD_W - 80, max(80.0, roadX(kv) + (if (r() < 0.5) -1 else 1) * (150 + r() * 150)))
            val route = listOf(door.x to door.y, door.x to yh, roadX(kv) to yh, roadX(kv) to yt, tx to yt)
            dropCrumbs(route)
            // false trail: continues straight at the corner and ends at the pigeons
            val fEnd = min(WORLD_W - 60, max(60.0, roadX(kv) + dir * 330)) to yh
            dropCrumbs(listOf(roadX(kv) to yh, fEnd))
            for (i in 0 until 3) {
                val py = fEnd.second + (r() - .5) * 20
                val flip = r() < .5
                addActor(PigeonD(fEnd.first + dir * (14 + i * 20), py, 1.0, flip))
            }
            val thiefX = tx
            val thiefY = yt + 12
            addActor(RaccoonD(thiefX, thiefY, 1.05, r() < .5, true))

            steps += bakeryStep
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
                    val px = ax + (bx - ax) * t + (r() - .5) * 10
                    val py = ay + (by - ay) * t + (r() - .5) * 10
                    crumbs += Crumb(px, py, m % 3 == 0)
                }
            }
        }
    }
}
