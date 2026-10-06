// OWNER: UI (game flow state holder; port of game.js state, menu, timer, hints, finish)
package de.wuselburg.game

import android.graphics.Picture
import android.os.SystemClock
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.wuselburg.core.Hit
import de.wuselburg.core.LevelDef
import de.wuselburg.core.LevelKind
import de.wuselburg.core.Levels
import de.wuselburg.core.Rules
import de.wuselburg.core.Scene
import de.wuselburg.core.SceneBuilder
import de.wuselburg.core.Step
import de.wuselburg.render.IconKind
import de.wuselburg.render.recordScenePicture
import de.wuselburg.ui.CameraState
import de.wuselburg.ui.WorldEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class Screen { MENU, GAME }

/** Dialog shown on top of the world; the timer is paused while one is visible. */
sealed interface Card {
    data class Intro(val title: String, val body: String) : Card
    data class StepFound(val body: String) : Card
    data object Pause : Card
    data class Win(
        val kind: LevelKind,
        val epilogue: String?,
        val seconds: Int,
        val misses: Int,
        val hints: Int,
        val stars: Int,
        val hasNext: Boolean,
    ) : Card
}

/** Content of the goal bar in the HUD. */
data class Goal(val title: String, val subtitle: String, val icon: IconKind)

/** One-shot events for haptic feedback. */
enum class GameEvent { FOUND, MISS }

/** "m:ss" like game.js fmt(). */
fun formatTime(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

class GameViewModel(
    val levels: List<LevelDef> = Levels.all,
    private val sceneBuilder: (LevelDef) -> Scene = { SceneBuilder.build(it) },
    private val pictureRecorder: (Scene) -> Picture = { recordScenePicture(it) },
    private val store: ProgressStore = MemoryProgressStore(),
    private val buildDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val uptimeMs: () -> Long = { SystemClock.uptimeMillis() },
    private val random: Random = Random.Default,
) : ViewModel() {

    val camera = CameraState()

    var screen by mutableStateOf(Screen.MENU); private set
    var loading by mutableStateOf(false); private set
    var picture by mutableStateOf<Picture?>(null); private set
    var card by mutableStateOf<Card?>(null); private set
    var goal by mutableStateOf<Goal?>(null); private set
    var effects by mutableStateOf<List<WorldEffect>>(emptyList()); private set
    var bestStars by mutableStateOf(store.load()); private set

    private var elapsedMs by mutableLongStateOf(0L)
    val elapsedSeconds: Int by derivedStateOf { (elapsedMs / 1000).toInt() }

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private var level: LevelDef? = null
    private var scene: Scene? = null
    private var stepIdx = 0
    private var misses = 0
    private var hints = 0

    /** True between "found" and the follow-up card, input is ignored then. */
    private var transition = false
    private var appActive = true

    private var buildJob: Job? = null
    private var transitionJob: Job? = null
    private var flyJob: Job? = null
    private var tickJob: Job? = null
    private var lastTickMs = 0L

    private val playing: Boolean
        get() = screen == Screen.GAME && !loading && scene != null && card == null && !transition && appActive

    // ------------------------------------------------------------ Test seam

    internal fun currentStepCenter(): Pair<Double, Double>? =
        scene?.steps?.getOrNull(stepIdx)?.let { it.centerX to it.centerY }

    internal val missCount get() = misses
    internal val hintCount get() = hints

    // ------------------------------------------------------------ Camera hand-off

    /** The UI calls this once the picture and the viewport size are known; true exactly once per level. */

    // ------------------------------------------------------------ Navigation

    fun startLevel(id: String) {
        val def = levels.firstOrNull { it.id == id } ?: return
        cancelJobs()
        level = def
        scene = null
        picture = null
        effects = emptyList()
        card = null
        goal = null
        stepIdx = 0; misses = 0; hints = 0; elapsedMs = 0
        transition = false
        loading = true
        screen = Screen.GAME
        syncTimer()

        buildJob = viewModelScope.launch {
            try {
                val built = withContext(buildDispatcher) {
                    val s = sceneBuilder(def)
                    s to pictureRecorder(s)
                }
                scene = built.first
                camera.reset() // before the picture is visible; deferred if the viewport is unknown
                picture = built.second
                loading = false
                updateGoal()
                card = Card.Intro(def.title, def.intro)
                syncTimer()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMenu()
            }
        }
    }

    fun showMenu() {
        cancelJobs()
        level = null
        scene = null
        picture = null
        effects = emptyList()
        card = null
        goal = null
        loading = false
        transition = false
        screen = Screen.MENU
        bestStars = store.load()
        syncTimer()
    }

    private fun cancelJobs() {
        buildJob?.cancel(); buildJob = null
        transitionJob?.cancel(); transitionJob = null
        flyJob?.cancel(); flyJob = null
    }

    fun onBack() {
        when {
            screen != Screen.GAME -> Unit
            loading -> showMenu()
            else -> when (card) {
                Card.Pause -> resume()
                is Card.Intro, is Card.Win -> showMenu()
                is Card.StepFound -> Unit
                null -> openPause()
            }
        }
    }

    fun openPause() {
        if (loading) showMenu() else if (screen == Screen.GAME && card == null && !transition) {
            card = Card.Pause
            syncTimer()
        }
    }

    fun resume() {
        if (card == Card.Pause) {
            card = null
            syncTimer()
        }
    }

    fun beginPlay() {
        if (card is Card.Intro) {
            card = null
            syncTimer()
        }
    }

    fun continueAfterStep() {
        if (card is Card.StepFound) {
            card = null
            updateGoal()
            syncTimer()
        }
    }

    fun restart() {
        level?.let { startLevel(it.id) }
    }

    fun nextLevel() {
        val i = levels.indexOfFirst { it.id == level?.id }
        levels.getOrNull(i + 1)?.let { startLevel(it.id) }
    }

    fun onAppStopped() { appActive = false; syncTimer() }
    fun onAppStarted() { appActive = true; syncTimer() }

    // ------------------------------------------------------------ Gameplay

    fun tapWorld(wx: Double, wy: Double) {
        if (!playing) return
        val step = scene?.steps?.getOrNull(stepIdx) ?: return
        val tol = 14.0 * camera.density / camera.k // generous for small fingers
        if (Rules.hitTest(step.hit, wx, wy, tol)) onFound(step) else onMiss(wx, wy)
    }

    private fun onMiss(wx: Double, wy: Double) {
        misses++
        _events.tryEmit(GameEvent.MISS)
        addEffect(WorldEffect.Miss(wx, wy, uptimeMs()), MISS_MS)
    }

    private fun onFound(step: Step) {
        val radius = when (val h = step.hit) {
            is Hit.Circle -> h.r + 10
            is Hit.Rect -> max(h.w, h.h) / 2 + 6
        }
        addEffect(WorldEffect.Found(step.centerX, step.centerY, radius, uptimeMs()), null)
        _events.tryEmit(GameEvent.FOUND)
        stepIdx++
        val last = stepIdx >= (scene?.steps?.size ?: 0)
        transition = true
        syncTimer()
        transitionJob = viewModelScope.launch {
            delay(if (last) 900L else 700L)
            transition = false
            if (last) finish(step) else card = Card.StepFound(step.onFound.orEmpty())
            syncTimer()
        }
    }

    fun useHint() {
        if (!playing) return
        val step = scene?.steps?.getOrNull(stepIdx) ?: return
        hints++
        val angle = random.nextDouble() * 2 * PI
        val offset = 50 + random.nextDouble() * 90
        val hx = step.centerX + cos(angle) * offset
        val hy = step.centerY + sin(angle) * offset
        addEffect(WorldEffect.Hint(hx, hy, HINT_RADIUS, uptimeMs()), HINT_MS)
        flyJob?.cancel()
        flyJob = viewModelScope.launch {
            camera.flyTo(hx, hy, minOf(camera.maxK, max(camera.k, camera.minK * 3.2f)))
        }
    }

    private fun finish(lastStep: Step) {
        val def = level ?: return
        val stars = Rules.stars(hints, misses)
        store.saveBest(def.id, stars)
        bestStars = bestStars + (def.id to max(stars, bestStars[def.id] ?: 0))
        val hasNext = levels.indexOfFirst { it.id == def.id } in 0 until levels.lastIndex
        card = Card.Win(def.kind, lastStep.epilogue, elapsedSeconds, misses, hints, stars, hasNext)
    }

    private fun updateGoal() {
        val def = level ?: return
        val step = scene?.steps?.getOrNull(stepIdx) ?: return
        val icon = when {
            def.kind == LevelKind.FIPS -> IconKind.FIPS
            stepIdx == 0 -> IconKind.CAKE
            else -> IconKind.RACCOON_CAKE
        }
        goal = Goal(step.title, step.subtitle, icon)
    }

    private fun addEffect(effect: WorldEffect, ttlMs: Long?) {
        effects = effects + effect
        if (ttlMs != null) viewModelScope.launch {
            delay(ttlMs)
            effects = effects - effect
        }
    }

    // ------------------------------------------------------------ Timer

    /** Runs the ticker exactly while the game is actually being played (no card, app visible). */
    private fun syncTimer() {
        val run = playing
        if (run && tickJob == null) {
            lastTickMs = uptimeMs()
            tickJob = viewModelScope.launch {
                while (isActive) {
                    delay(TICK_MS)
                    accumulate()
                }
            }
        } else if (!run && tickJob != null) {
            accumulate()
            tickJob?.cancel()
            tickJob = null
        }
    }

    private fun accumulate() {
        val now = uptimeMs()
        elapsedMs += now - lastTickMs
        lastTickMs = now
    }

    private companion object {
        const val TICK_MS = 250L
        const val MISS_MS = 750L
        const val HINT_MS = 3700L
        const val HINT_RADIUS = 240.0
    }
}
