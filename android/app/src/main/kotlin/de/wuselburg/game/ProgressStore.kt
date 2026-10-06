package de.wuselburg.game

import android.content.Context

/** Persists the best star rating per level id (game.js loadSave / saveStars). */
interface ProgressStore {
    fun load(): Map<String, Int>
    fun saveBest(levelId: String, stars: Int)
}

/** Non-persistent store, default for tests. */
class MemoryProgressStore(initial: Map<String, Int> = emptyMap()) : ProgressStore {
    private val data = initial.toMutableMap()
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
    }
}
