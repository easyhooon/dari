package com.easyhooon.dari.shake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeAnalyzerTest {

    private val g = GRAVITY_EARTH_MS2
    private val force = SHAKE_FORCE_THRESHOLD + 1f

    /**
     * Simulates alternating force readings on the X axis.
     * With `<= 0` sign check (matching React Native), the very first strong
     * reading counts as a reversal because `prevX` starts at 0.
     * Each subsequent sign flip also counts as one reversal.
     */
    private fun feedReversals(analyzer: ShakeAnalyzer, count: Int, startMs: Long): Long {
        var t = startMs
        for (i in 0 until count) {
            val v = if (i % 2 == 0) force else -force
            analyzer.onAcceleration(v, 0f, g, t)
            t += 50
        }
        return t
    }

    @Test
    fun `still device does not register a shake`() {
        val analyzer = ShakeAnalyzer()
        assertFalse(analyzer.onAcceleration(0f, g, 0f, nowMs = 0L))
    }

    @Test
    fun `single spike does not register a shake`() {
        val analyzer = ShakeAnalyzer()
        // 1 reversal is not enough (need 8)
        assertFalse(analyzer.onAcceleration(3 * g, 0f, g, nowMs = 0L))
    }

    @Test
    fun `full shake cycle triggers after required reversals`() {
        val analyzer = ShakeAnalyzer(requiredReversals = 4)
        var t = 0L
        // 3 reversals — not enough
        t = feedReversals(analyzer, 3, t)
        // 4th reversal → trigger
        assertTrue(analyzer.onAcceleration(-force, 0f, g, t))
    }

    @Test
    fun `default 8 reversals required`() {
        val analyzer = ShakeAnalyzer()
        var t = 0L
        // 7 reversals — not enough
        t = feedReversals(analyzer, 7, t)
        // 8th reversal → trigger
        assertTrue(analyzer.onAcceleration(-force, 0f, g, t))
    }

    @Test
    fun `reversals outside time window are reset`() {
        val analyzer = ShakeAnalyzer(requiredReversals = 6, windowMs = 3_000L)
        var t = 0L
        // 3 reversals
        t = feedReversals(analyzer, 3, t)
        // Jump past window
        t += 4_000L
        // New window: 5 reversals — not enough (need 6, prev 3 were reset)
        t = feedReversals(analyzer, 5, t)
        assertFalse(analyzer.onAcceleration(force, 0f, g, t))
    }

    @Test
    fun `cooldown prevents immediate re-trigger`() {
        val analyzer = ShakeAnalyzer(requiredReversals = 4, cooldownMs = 1_000L)
        var t = 0L
        // Trigger first shake (4 reversals)
        t = feedReversals(analyzer, 3, t)
        assertTrue(analyzer.onAcceleration(-force, 0f, g, t))
        t += 50

        // Within cooldown — all readings should be rejected
        for (i in 0 until 8) {
            val v = if (i % 2 == 0) force else -force
            assertFalse(analyzer.onAcceleration(v, 0f, g, t))
            t += 50
        }
    }

    @Test
    fun `shake registers after cooldown expires`() {
        val analyzer = ShakeAnalyzer(requiredReversals = 4, cooldownMs = 1_000L)
        var t = 0L
        // Trigger first shake
        t = feedReversals(analyzer, 3, t)
        assertTrue(analyzer.onAcceleration(-force, 0f, g, t))

        // Wait past cooldown
        t += 1_500L

        // Should trigger again
        t = feedReversals(analyzer, 3, t)
        assertTrue(analyzer.onAcceleration(-force, 0f, g, t))
    }

    @Test
    fun `sub-threshold readings do not count as reversals`() {
        val analyzer = ShakeAnalyzer()
        val weak = SHAKE_FORCE_THRESHOLD * 0.5f
        var t = 0L
        for (i in 0 until 20) {
            assertFalse(analyzer.onAcceleration(weak, 0f, g, t))
            t += 50
            assertFalse(analyzer.onAcceleration(-weak, 0f, g, t))
            t += 50
        }
    }

    @Test
    fun `samples within minimum interval are ignored`() {
        val analyzer = ShakeAnalyzer(minSampleIntervalMs = 20L)
        assertFalse(analyzer.onAcceleration(force, 0f, g, nowMs = 0L))
        // 5ms later — too soon, ignored
        assertFalse(analyzer.onAcceleration(-force, 0f, g, nowMs = 5L))
        // 25ms later — accepted
        assertFalse(analyzer.onAcceleration(-force, 0f, g, nowMs = 25L))
    }

    @Test
    fun `y-axis reversals also count`() {
        val analyzer = ShakeAnalyzer(requiredReversals = 4)
        var t = 0L
        // Feed 3 reversals on Y axis
        for (i in 0 until 3) {
            val v = if (i % 2 == 0) force else -force
            analyzer.onAcceleration(0f, v, g, t)
            t += 50
        }
        // 4th reversal → trigger
        assertTrue(analyzer.onAcceleration(0f, -force, g, t))
    }

    @Test
    fun `custom required reversals is respected`() {
        val analyzer = ShakeAnalyzer(requiredReversals = 2)
        var t = 0L
        // 1st reversal
        assertFalse(analyzer.onAcceleration(force, 0f, g, t))
        t += 50
        // 2nd reversal → trigger
        assertTrue(analyzer.onAcceleration(-force, 0f, g, t))
    }
}
