package com.easyhooon.dari.shake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeAnalyzerTest {

    private val gravity = GRAVITY_EARTH_MS2

    @Test
    fun `still device at 1g does not register a shake`() {
        val analyzer = ShakeAnalyzer()
        // Phone resting flat: y axis ~= 1g, others 0
        assertFalse(analyzer.onAcceleration(0f, gravity, 0f, nowMs = 0L))
    }

    @Test
    fun `acceleration above threshold registers a shake`() {
        val analyzer = ShakeAnalyzer()
        // 3g on a single axis
        assertTrue(analyzer.onAcceleration(3 * gravity, 0f, 0f, nowMs = 0L))
    }

    @Test
    fun `acceleration just below threshold does not register`() {
        val analyzer = ShakeAnalyzer(thresholdG = 2.5f)
        // 2g - below 2.5g threshold
        assertFalse(analyzer.onAcceleration(2 * gravity, 0f, 0f, nowMs = 0L))
    }

    @Test
    fun `combined axes contribute to total g-force`() {
        val analyzer = ShakeAnalyzer()
        // sqrt(2^2 + 2^2 + 2^2) ~ 3.46g
        assertTrue(
            analyzer.onAcceleration(2 * gravity, 2 * gravity, 2 * gravity, nowMs = 0L),
        )
    }

    @Test
    fun `second shake within cooldown is ignored`() {
        val analyzer = ShakeAnalyzer(cooldownMs = 1000L)
        assertTrue(analyzer.onAcceleration(3 * gravity, 0f, 0f, nowMs = 0L))
        // 500 ms later, still inside cooldown
        assertFalse(analyzer.onAcceleration(3 * gravity, 0f, 0f, nowMs = 500L))
    }

    @Test
    fun `second shake after cooldown is registered`() {
        val analyzer = ShakeAnalyzer(cooldownMs = 1000L)
        assertTrue(analyzer.onAcceleration(3 * gravity, 0f, 0f, nowMs = 0L))
        // 1500 ms later, past cooldown
        assertTrue(analyzer.onAcceleration(3 * gravity, 0f, 0f, nowMs = 1500L))
    }

    @Test
    fun `cooldown is not triggered by sub-threshold readings`() {
        val analyzer = ShakeAnalyzer(cooldownMs = 1000L)
        // Sub-threshold reading should not start a cooldown
        assertFalse(analyzer.onAcceleration(0f, gravity, 0f, nowMs = 0L))
        // A real shake right after should still register
        assertTrue(analyzer.onAcceleration(3 * gravity, 0f, 0f, nowMs = 100L))
    }

    @Test
    fun `custom threshold is respected`() {
        val analyzer = ShakeAnalyzer(thresholdG = 4.0f)
        // 3g - below custom 4g threshold
        assertFalse(analyzer.onAcceleration(3 * gravity, 0f, 0f, nowMs = 0L))
        // 5g - above custom threshold
        assertTrue(analyzer.onAcceleration(5 * gravity, 0f, 0f, nowMs = 0L))
    }
}
