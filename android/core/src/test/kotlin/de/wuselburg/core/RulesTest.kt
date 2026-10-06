package de.wuselburg.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulesTest {
    @Test
    fun circleHitUsesTolerance() {
        val hit = Hit.Circle(100.0, 100.0, 30.0)
        assertTrue(Rules.hitTest(hit, 100.0, 125.0, 0.0))
        assertFalse(Rules.hitTest(hit, 100.0, 140.0, 5.0))
        assertTrue(Rules.hitTest(hit, 100.0, 140.0, 10.0))
    }

    @Test
    fun starsMatchGameJs() {
        assertEquals(3, Rules.stars(0, 2))
        assertEquals(2, Rules.stars(1, 2))
        assertEquals(2, Rules.stars(0, 3))
        assertEquals(1, Rules.stars(2, 5))
    }

    @Test
    fun fourLevels() = assertEquals(4, Levels.all.size)
}
