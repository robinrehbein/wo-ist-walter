// OWNER: CORE (contract file; field names are the contract, bodies are owned by CORE)
package de.wuselburg.core

/*
 * Pure Kotlin/JVM data model of a Wuselburg scene. Mirrors the data that game.js buildScene()
 * emits as SVG strings, but as typed data so the Android renderer can draw it with Canvas.
 *
 * CONVENTIONS
 *  - All lengths are Doubles in WORLD units (the 2180 x 1540 SVG user space of game.js).
 *  - Colours are ARGB Ints, e.g. 0xFFE8802A.toInt() == '#e8802a' in game.js. 0 means "none / transparent".
 *  - y grows downwards (like SVG).
 */

/** World width: ROAD + COLS * (BW + ROAD) in game.js. */
const val WORLD_W = 2180.0

/** World height: ROAD + ROWS * (BH + ROAD) in game.js. */
const val WORLD_H = 1540.0

/** Grid constants from game.js (ROAD, BW, BH, COLS, ROWS). */
object WorldLayout {
    const val ROAD = 100.0
    const val BLOCK_W = 420.0
    const val BLOCK_H = 380.0
    const val COLS = 4
    const val ROWS = 3

    /** Centre line of vertical road k (0..COLS): roadX in game.js case-level code. */
    fun roadX(k: Int): Double = k * (BLOCK_W + ROAD) + ROAD / 2

    /** Centre line of horizontal road j (0..ROWS): roadY in game.js case-level code. */
    fun roadY(j: Int): Double = j * (BLOCK_H + ROAD) + ROAD / 2
}

/** Axis-aligned rectangle, (x, y) = top-left. */
data class RectD(val x: Double, val y: Double, val w: Double, val h: Double)

/** Fox variants: FIPS is the target, the others are decoys (game.js FIPS / DECOY table). */
enum class FoxStyle {
    /** fur #e8802a, teal scarf #1fa6a0 with yellow dots, white tail tip. */
    FIPS,
    /** DECOY.noScarf: orange, no scarf, tail tip. */
    NO_SCARF,
    /** DECOY.redScarf: red scarf #d9433b with yellow dots, tail tip. */
    RED_SCARF,
    /** DECOY.plainScarf: teal scarf WITHOUT dots, tail tip. */
    PLAIN_SCARF,
    /** DECOY.noTip: teal scarf with dots, NO white tail tip. */
    NO_TIP,
    /** DECOY.grey: grey fur #9a9aa6, teal scarf with dots, tail tip. */
    GREY,
}

/** person() hat variants ('none' | 'cap' | 'beanie' | 'top'). NONE draws hair instead. */
enum class HatStyle { NONE, CAP, BEANIE, TOP }

/** Block kinds from buildScene `types` array. */
enum class BlockType { PARK, MARKET, PLAZA, HOUSES }

/** LevelDef.kind: FIPS = find the fox (1 step); CASE = detective case (bakery, then thief). */
enum class LevelKind { FIPS, CASE }

/**
 * Something drawn in the painter-ordered layer (game.js `items`). (x, y) is the FOOT / BASE point
 * (SVG origin of the figure); figure geometry extends upwards (negative y) from there.
 * [scale] is the uniform size factor, [flip] mirrors horizontally around x (scale(-s s) in game.js).
 */
sealed interface Drawable {
    val x: Double
    val y: Double
    val scale: Double
    val flip: Boolean
}

/** person(): [balloonColor] == 0 means no balloon (about 9% of people have one). */
data class PersonD(
    override val x: Double, override val y: Double, override val scale: Double, override val flip: Boolean,
    val shirt: Int, val skin: Int, val hair: Int, val pants: Int,
    val hat: HatStyle, val hatColor: Int, val balloonColor: Int,
) : Drawable

/** dog(): body colour [color]. */
data class DogD(
    override val x: Double, override val y: Double, override val scale: Double, override val flip: Boolean,
    val color: Int,
) : Drawable

/** cat(): body colour [color]. */
data class CatD(
    override val x: Double, override val y: Double, override val scale: Double, override val flip: Boolean,
    val color: Int,
) : Drawable

/** pigeon() (decoy trail end of the case level). */
data class PigeonD(
    override val x: Double, override val y: Double, override val scale: Double, override val flip: Boolean,
) : Drawable

/** fox(): Fips himself or a decoy, see [FoxStyle]. */
data class FoxD(
    override val x: Double, override val y: Double, override val scale: Double, override val flip: Boolean,
    val style: FoxStyle,
) : Drawable

/** raccoon(cake): [hasCake] draws the pink cake in its arms (the thief). */
data class RaccoonD(
    override val x: Double, override val y: Double, override val scale: Double, override val flip: Boolean,
    val hasCake: Boolean,
) : Drawable

/** treeSvg(): crown colour [color]; (x, y) = trunk base. */
data class TreeD(
    override val x: Double, override val y: Double, override val scale: Double, override val flip: Boolean,
    val color: Int,
) : Drawable

/** stallSvg(): market stall with awning colour [color]; (x, y) = base centre. */
data class StallD(
    override val x: Double, override val y: Double, override val scale: Double, override val flip: Boolean,
    val color: Int,
) : Drawable

/**
 * houseSvg() geometry. [x] = left edge, [baseY] = ground line, [w] x [h] wall box (roof extends 46 above).
 * [curtains] has one entry per window in order floor 0 (left, right), floor 1 (left, right) ...;
 * a colour (drawn at 80% opacity) or 0 for no curtain. Floors = if (h > 150) 2 else 1 -> 2 or 4 entries.
 * Houses are part of the background layer (drawn before crumbs and drawables).
 */
data class House(
    val x: Double, val baseY: Double, val w: Double, val h: Double,
    val wall: Int, val roof: Int, val bakery: Boolean, val curtains: List<Int>,
)

/** Ellipse: park pond (rx 70, ry 44) or plaza fountain (rx = ry = 62, basin only; renderer adds inner circle). */
data class Pond(val cx: Double, val cy: Double, val rx: Double, val ry: Double)

/** Ground speckle: park flowers, market sand dots (small circles, radius chosen by renderer from block type). */
data class Dot(val x: Double, val y: Double, val color: Int)

/**
 * One grid cell ([rect] = the 420x380 block). Background-only data:
 *  - PARK: [pond] + [dots] (flowers) (+ lily pad drawn by the renderer relative to the pond); trees are Drawables.
 *  - MARKET: [dots] (sand); stalls/trees are Drawables.
 *  - PLAZA: [pond] = fountain; trees are Drawables.
 *  - HOUSES: [houses] (3, left to right); trees are Drawables.
 */
data class Block(
    val rect: RectD, val type: BlockType,
    val houses: List<House> = emptyList(), val pond: Pond? = null, val dots: List<Dot> = emptyList(),
)

/** One breadcrumb of the case level trail. [big] = every third crumb (r 3.4, #c98a2b) else (r 2.4, #e0a845). */
data class Crumb(val x: Double, val y: Double, val big: Boolean)

/** Tap target of a [Step] in world coordinates. */
sealed interface Hit {
    data class Circle(val x: Double, val y: Double, val r: Double) : Hit
    data class Rect(val x: Double, val y: Double, val w: Double, val h: Double) : Hit
}

/**
 * One search objective (game.js scene.steps[i]). [title]/[subtitle] go to the goal bar,
 * [centerX]/[centerY] is where hints point and where the "Gefunden!" effect appears.
 * [onFound] = text of the "Gut gemacht!" card after a non-final step; [epilogue] = text on the final card.
 */
data class Step(
    val title: String, val subtitle: String, val hit: Hit,
    val centerX: Double, val centerY: Double,
    val onFound: String? = null, val epilogue: String? = null,
)

/**
 * Fully built level. Layers, bottom to top: ground (roads + [blocks]), [crumbs], [drawables]
 * (sorted by ascending y = painter order, exactly like game.js `items.sort`).
 * Roads are implicit from [WorldLayout] (rects, white dashed centre lines, intersections).
 */
data class Scene(
    val blocks: List<Block>,
    val crumbs: List<Crumb>,
    val drawables: List<Drawable>,
    val steps: List<Step>,
)

/** Level config (game.js LEVELS entry). [hideOffset] only matters if [hidden]; [raccoons] only for CASE. */
data class LevelDef(
    val id: String,
    val kind: LevelKind,
    val title: String,
    val subtitle: String,
    val seed: Int,
    /** Total number of figures incl. decoy foxes and raccoons (people/dogs/cats fill the rest). */
    val crowd: Int,
    /** Number of decoy foxes (cycled through [decoys]). */
    val foxes: Int,
    val decoys: List<FoxStyle>,
    /** FIPS levels: hide Fips beside a tree instead of a random free spot. */
    val hidden: Boolean = false,
    /** Horizontal distance of the hiding spot from the tree trunk. */
    val hideOffset: Double = 0.0,
    val raccoons: Int = 0,
    val intro: String,
)
