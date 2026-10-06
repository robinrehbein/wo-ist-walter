package de.wuselburg.game

import android.content.Context

/** Persists the best star rating per level id (game.js loadSave / saveStars). */
interface ProgressStore {
    fun load(): Map<String, Int>
    fun saveBest(levelId: String, stars: Int)

    /** Paper cut-out look (default) or classic dark outline (web: localStorage 'wuselburg.style'). */
    fun loadPaperStyle(): Boolean = true
    fun savePaperStyle(paper: Boolean) {}
}

/** Non-persistent store, default for tests. */
class MemoryProgressStore(initial: Map<String, Int> = emptyMap()) : ProgressStore {
    private val data = initial.toMutableMap()
    private var paper = true
    override fun loadPaperStyle(): Boolean = paper
    override fun savePaperStyle(paper: Boolean) { this.paper = paper }
    override fun load(): Map<String, Int> = data.toMap()
    override fun saveBest(levelId: String, stars: Int) {
        data[levelId] = maxOf(data[levelId] ?: 0, stars)
    }
}

/** SharedPreferences backed store; every access is guarded so a broken disk never crashes the game. */
class SharedPrefsProgressStore(context: Context) : ProgressStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): Map<String, Int> = try {
        prefs.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    override fun loadPaperStyle(): Boolean = try {
        prefs.getBoolean(KEY_PAPER, true)
    } catch (e: Exception) {
        true
    }

    override fun savePaperStyle(paper: Boolean) {
        try { prefs.edit().putBoolean(KEY_PAPER, paper).apply() } catch (e: Exception) { /* nicety */ }
    }

    override fun saveBest(levelId: String, stars: Int) {
        try {
            val best = maxOf(prefs.getInt(levelId, 0), stars)
            prefs.edit().putInt(levelId, best).apply()
        } catch (e: Exception) {
            // progress is a nicety, ignore
        }
    }

    private companion object {
        const val FILE = "wuselburg.v1"
        const val KEY_PAPER = "style.paper" // Boolean, not an Int: ignored by load()
    }
}
