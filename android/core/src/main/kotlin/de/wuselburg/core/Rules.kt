// OWNER: CORE
package de.wuselburg.core

import kotlin.math.hypot

/** Game rules, mirrors game.js hitTest() and finish(). */
object Rules {
    /** True if world point (x, y) hits [hit], enlarged by [tol] world units on every side. */
    fun hitTest(hit: Hit, x: Double, y: Double, tol: Double): Boolean = when (hit) {
        is Hit.Circle -> hypot(hit.x - x, hit.y - y) <= hit.r + tol
        is Hit.Rect -> x >= hit.x - tol && x <= hit.x + hit.w + tol && y >= hit.y - tol && y <= hit.y + hit.h + tol
    }

    /** 1 star for finishing, +1 without hints, +1 with at most 2 misses. */
    fun stars(hints: Int, misses: Int): Int = 1 + (if (hints == 0) 1 else 0) + (if (misses <= 2) 1 else 0)
}
