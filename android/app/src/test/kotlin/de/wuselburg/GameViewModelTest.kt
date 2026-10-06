package de.wuselburg

import android.graphics.Picture
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.wuselburg.core.LevelKind
import de.wuselburg.game.Card
import de.wuselburg.game.GameViewModel
import de.wuselburg.game.MemoryProgressStore
import de.wuselburg.game.Screen
import de.wuselburg.ui.WorldEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class GameViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val store = MemoryProgressStore()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = GameViewModel(
        pictureRecorder = { Picture() },
        store = store,
        buildDispatcher = dispatcher,
        uptimeMs = { dispatcher.scheduler.currentTime },
        random = Random(1),
    )

    private fun GameViewModel.start(id: String) {
        startLevel(id)
        dispatcher.scheduler.runCurrent()
        assertTrue("intro card", card is Card.Intro)
        beginPlay()
    }

    private fun advance(ms: Long) {
        dispatcher.scheduler.advanceTimeBy(ms)
        dispatcher.scheduler.runCurrent()
    }

    @Test fun fipsFlowWithMissThenFound() {
        val vm = vm()
        vm.start("fips1")
        assertEquals(Screen.GAME, vm.screen)
        assertNotNull(vm.goal)
        val (cx, cy) = vm.currentStepCenter()!!

        vm.tapWorld(5.0, 5.0)
        assertEquals(1, vm.missCount)
        assertTrue(vm.effects.any { it is WorldEffect.Miss })

        vm.tapWorld(cx, cy)
        assertTrue(vm.effects.any { it is WorldEffect.Found })
        advance(1000)
        val win = vm.card as Card.Win
        assertEquals(LevelKind.FIPS, win.kind)
        assertEquals(1, win.misses)
        assertEquals(0, win.hints)
        assertTrue(win.stars in 1..3)
        assertEquals(win.stars, vm.bestStars["fips1"])
        assertTrue(win.hasNext)
    }

    @Test fun perfectRunGivesThreeStars() {
        val vm = vm()
        vm.start("fips1")
        val (cx, cy) = vm.currentStepCenter()!!
        vm.tapWorld(cx, cy)
        advance(1000)
        assertEquals(3, (vm.card as Card.Win).stars)
    }

    @Test fun caseFlowHasTwoStepsAndEpilogue() {
        val vm = vm()
        vm.start("case1")
        val (bx, by) = vm.currentStepCenter()!!
        vm.tapWorld(bx, by)
        advance(800)
        assertTrue(vm.card is Card.StepFound)
        assertTrue((vm.card as Card.StepFound).body.isNotBlank())
        vm.continueAfterStep()
        assertNull(vm.card)

        val (tx, ty) = vm.currentStepCenter()!!
        assertTrue(tx != bx || ty != by)
        vm.tapWorld(tx, ty)
        advance(1000)
        val win = vm.card as Card.Win
        assertEquals(LevelKind.CASE, win.kind)
        assertTrue(!win.epilogue.isNullOrBlank())
    }

    @Test fun hintCountsAndEmitsEffect() {
        val vm = vm()
        vm.start("fips1")
        vm.useHint()
        assertEquals(1, vm.hintCount)
        assertTrue(vm.effects.any { it is WorldEffect.Hint })
        advance(4000)
        assertTrue(vm.effects.none { it is WorldEffect.Hint })
    }

    @Test fun inputIgnoredWhileCardShown() {
        val vm = vm()
        vm.startLevel("fips1")
        dispatcher.scheduler.runCurrent()
        vm.tapWorld(1.0, 1.0)
        assertEquals(0, vm.missCount)
    }

    @Test fun timerRunsOnlyWhilePlaying() {
        val vm = vm()
        vm.start("fips1")
        advance(3000)
        assertTrue(vm.elapsedSeconds in 2..3)
        vm.openPause()
        val t = vm.elapsedSeconds
        advance(5000)
        assertEquals(t, vm.elapsedSeconds)
    }
}
