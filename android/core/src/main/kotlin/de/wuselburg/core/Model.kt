// OWNER: CORE (contract file; field names are the contract, bodies are owned by CORE)
package de.wuselburg.core

/*
 * Pure Kotlin/JVM data model of a Wuselburg scene: a faithful port of scene.js buildSceneData(cfg)
 * (the web SPEC; game.js is UI only). The renderer draws it with Canvas, mirroring scene.js sceneToSvg().
 *
 * API NOTES FOR RENDERER AGENTS (changes vs. the old model)
 *  - The old sealed Drawable hierarchy (PersonD, FoxD, ...), FoxStyle, HatStyle and ARGB Int colours are GONE.
 *  - Drawable is ONE data class: kind + x/y/scale/flip + optional z + props map mirroring the JS descriptor keys 1:1.
 *  - Colours are '#rrggbb' STRINGS exactly as in scene.js (parse them in the renderer, '' = none, e.g. curtains/shop).
 *  - The scene type is still called [Scene] (typealias of [SceneData], so both names work).
 *  - Fox styles are strings: "fips","noScarf","redScarf","plainScarf","noTip","grey" (see scene.js FOXSTYLES).
 *  - LevelDef.decoys is List<String> of those fox style names; LevelDef has groups + mood.
 *  - Only PARK blocks carry a pond (fountain of PLAZA is drawn by the renderer from the block rect, as in sceneToSvg).
 *
 * CONVENTIONS
 *  - All lengths are Doubles in WORLD units (the 2180 x 1540 SVG user space of scene.js).
 *  - y grows downwards (like SVG). Drawable (x, y) is the FOOT / BASE point; geometry extends to negative y.
 *
 * DRAWABLE PROPS (value types: String, Double, Boolean) per kind
 *  person : body,bottom,shirt,skin,hair,pants,shoes,hairStyle,hat,hatColor,glasses(B),beard(B),pattern,patternColor,
 *           bag,prop,propColor,pose,seat
 *  dog    : breed,color            cat : pose,color,stripes(B)        pigeon, duck : (none)
 *  fox    : style                  raccoon : hasCake(B)
 *  tree   : type,color,wide(B)     stall : color,goods
 *  prop   : type (+ color,color2 | dx,dy | lift where present); `z` (blanket, leash) is Drawable.z, not in props.
 */

/** World width: ROAD + COLS * (BW + ROAD) in scene.js. */
const val WORLD_W = 2180.0

/** World height: ROAD + ROWS * (BH + ROAD) in scene.js. */
const val WORLD_H = 1540.0

/** Grid constants from scene.js (ROAD, BW, BH, COLS, ROWS). */
object WorldLayout {
    const val ROAD = 100.0
    const val BLOCK_W = 420.0
    const val BLOCK_H = 380.0
    const val COLS = 4
    const val ROWS = 3

    /** Centre line of vertical road k (0..COLS): roadX in scene.js case-level code. */
    fun roadX(k: Int): Double = k * (BLOCK_W + ROAD) + ROAD / 2

    /** Centre line of horizontal road j (0..ROWS): roadY in scene.js case-level code. */
    fun roadY(j: Int): Double = j * (BLOCK_H + ROAD) + ROAD / 2
}

/** Axis-aligned rectangle, (x, y) = top-left. */
data class RectD(val x: Double, val y: Double, val w: Double, val h: Double)

/** A point. */
data class PointD(val x: Double, val y: Double)

/** Block kinds from buildSceneData `types` (JSON/JS names are the lower-case enum names). */
enum class BlockType { PARK, MARKET, PLAZA, HOUSES }

/** LevelDef.kind: FIPS = find the fox (1 step); CASE = detective case (bakery, then thief). */
enum class LevelKind { FIPS, CASE }

/**
 * Something drawn in the painter-ordered layer (scene.js `drawables`), already sorted (stable, by z ?: y).
 * [kind] is one of "person","dog","cat","pigeon","duck","fox","raccoon","tree","stall","prop".
 * [z] is the optional sort key override (blanket, leash). [props] mirror the JS descriptor keys 1:1.
 */
data class Drawable(
    val kind: String,
    val x: Double,
    val y: Double,
    val scale: Double,
    val flip: Boolean,
    val z: Double?,
    val props: Map<String, Any>,
) {
    fun str(key: String): String = props[key] as String
    fun num(key: String): Double = (props[key] as Number).toDouble()
    fun bool(key: String): Boolean = props[key] as Boolean
    fun strOrNull(key: String): String? = props[key] as? String
    fun numOrNull(key: String): Double? = (props[key] as? Number)?.toDouble()
    fun has(key: String): Boolean = props.containsKey(key)
}

/**
 * makeHouse() descriptor. [x] = left edge, [baseY] = ground line, [w] x [h] wall box.
 * [curtains]: one entry per window (floor 0 left,right; floor 1 ...), '#rrggbb' or "" for none; floors = h > 150 ? 2 : 1.
 * [shop] is "" | "cafe" | "flowers" | "books"; [roofType] "gable" | "flat" | "steep".
 */
data class House(
    val x: Double, val baseY: Double, val w: Double, val h: Double,
    val wall: String, val roof: String, val roofType: String, val shop: String, val door: String,
    val shutterColor: String,
    val chimney: Boolean, val shutters: Boolean, val plantBox: Boolean, val bakery: Boolean,
    val curtains: List<String>,
)

/** Park pond ellipse (rx 60, ry 40). */
data class Pond(val cx: Double, val cy: Double, val rx: Double, val ry: Double)

/** Ground speckle. shape "circle" (radius [r], [color]) or "tuft" (grass tuft, r = 0). */
data class Dot(val x: Double, val y: Double, val r: Double, val color: String, val shape: String)

/**
 * One grid cell (420x380 at [x],[y]). PARK: [pond] + [dots]; MARKET: [dots] (sand); PLAZA: nothing (fountain is
 * drawn by the renderer); HOUSES: [houses] (3, left to right).
 */
data class Block(
    val col: Int, val row: Int, val x: Double, val y: Double, val w: Double, val h: Double,
    val type: BlockType,
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
 * One search objective (scene.js steps[i]). [title]/[subtitle] go to the goal bar,
 * [centerX]/[centerY] is where hints point and where the "Gefunden!" effect appears.
 * [onFound] = text of the "Gut gemacht!" card after a non-final step; [epilogue] = text on the final card.
 */
data class Step(
    val title: String, val subtitle: String, val hit: Hit,
    val centerX: Double, val centerY: Double,
    val onFound: String? = null, val epilogue: String? = null,
)

/** The bakery of the CASE level (scene.js `bakery`): tap [rect], [center] of the hint, [door] = crumb trail start. */
data class BakeryInfo(val rect: RectD, val center: PointD, val door: PointD)

/**
 * Fully built level. Layers, bottom to top: ground (roads + [blocks]), [crumbs], [drawables] (already sorted).
 * [mood] is "day" | "evening". Roads are implicit from [WorldLayout].
 */
data class SceneData(
    val mood: String,
    val blocks: List<Block>,
    val crumbs: List<Crumb>,
    val drawables: List<Drawable>,
    val steps: List<Step>,
    val bakery: BakeryInfo?,
)

/** Old name of [SceneData]; both are valid. */
typealias Scene = SceneData

/** Level config (scene.js LEVELS entry). [hideOffset] only matters if [hidden]; [raccoons] only for CASE. */
data class LevelDef(
    val id: String,
    val kind: LevelKind,
    val title: String,
    val subtitle: String,
    val seed: Int,
    /** Total number of figures incl. decoy foxes and raccoons (people/dogs/cats/pigeons fill the rest). */
    val crowd: Int,
    /** Number of decoy foxes (cycled through [decoys]). */
    val foxes: Int,
    /** Fox style names, see scene.js FOXSTYLES: "noScarf","redScarf","plainScarf","noTip","grey". */
    val decoys: List<String>,
    /** FIPS levels: hide Fips beside a tree instead of a random free spot. */
    val hidden: Boolean = false,
    /** Horizontal distance of the hiding spot from the tree trunk. */
    val hideOffset: Double = 0.0,
    val raccoons: Int = 0,
    /** Number of scene groups (picnic, musician, ...). */
    val groups: Int = 0,
    /** "day" | "evening". */
    val mood: String = "day",
    val intro: String,
)
