package de.wuselburg.render

/**
 * Look of the scene toolkit (scene.js STYLE.paper / OL() / HOUSE_EDGE()) plus the "shadow mode" switch used by the
 * cast-shadow pass. One instance per thread ([get]); recordScenePicture / renderSceneToBitmap set [paper] and
 * [shadowMode] around the drawing, [Ink] and [Fig] read them for every fill / stroke primitive.
 */
internal class PaperStyle {
    /**
     * Paper cut-out look (white edges, shadows, grain) instead of the classic dark outline. Off by default so that
     * figures drawn outside a scene (menu icons) keep the classic look; the scene renderers set it explicitly.
     */
    var paper = false

    /** True while drawing the shadow silhouette: every shape is painted in [SHADOW_RGB] (source alpha kept), no outlines. */
    var shadowMode = false

    val figEdgeColor: Int get() = if (paper) EDGE_PAPER else EDGE_FIG_CLASSIC
    val figEdgeWidth: Float get() = if (paper) 1.3f else 0.7f
    /** OUT() of scene.js: wide outline of one-piece silhouettes (dogs, cats). Colour is [figEdgeColor]. */
    val silEdgeWidth: Float get() = if (paper) 2.8f else 1.5f
    val houseEdgeColor: Int get() = if (paper) EDGE_PAPER else EDGE_HOUSE_CLASSIC
    val houseEdgeWidth: Float get() = if (paper) 1.6f else 1f

    /** [color] as it is painted in the current mode: the shadow ink with the colour's own alpha in shadow mode. */
    fun tone(color: Int): Int = if (shadowMode) (color and 0xFF000000.toInt()) or SHADOW_RGB else color

    companion object {
        const val SHADOW_RGB = 0x2A1D10
        const val SHADOW_DX = 1.8f
        const val SHADOW_DY = 2.8f
        /** feDropShadow flood-opacity .34 */
        const val SHADOW_ALPHA = 87
        val EDGE_PAPER = parseCssColor("#fffaf0")
        val EDGE_FIG_CLASSIC = parseCssColor("rgba(30,20,40,.32)")
        val EDGE_HOUSE_CLASSIC = parseCssColor("rgba(30,20,40,.28)")

        private val local = ThreadLocal.withInitial { PaperStyle() }
        fun get(): PaperStyle = local.get()!!
    }
}
