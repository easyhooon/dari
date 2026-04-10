package com.easyhooon.dari.shake

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs

/** Standard gravity in m/s^2. Hardcoded so the analyzer is testable without Android framework. */
internal const val GRAVITY_EARTH_MS2 = 9.80665f

/** Force threshold per axis: 1.33G (same as React Native ShakeDetector). */
internal const val SHAKE_FORCE_THRESHOLD = GRAVITY_EARTH_MS2 * 1.33f

/** Number of direction reversals required to trigger a shake. */
internal const val REQUIRED_REVERSALS = 8

/** Rolling time window in which reversals must occur (ms). */
internal const val SHAKE_WINDOW_MS = 3_000L

/** Cooldown after a successful shake detection (ms). */
internal const val SHAKE_COOLDOWN_MS = 1_000L

/** Minimum time between sensor samples to avoid over-processing (ms). */
internal const val MIN_SAMPLE_INTERVAL_MS = 20L

/**
 * Shake detection using per-axis direction reversal counting,
 * inspired by React Native's ShakeDetector.
 *
 * A direction reversal is counted when acceleration on any axis exceeds
 * [SHAKE_FORCE_THRESHOLD] and the sign flips from the previous reading.
 * When [requiredReversals] reversals accumulate within [windowMs],
 * a shake is detected.
 */
internal class ShakeAnalyzer(
    private val forceThreshold: Float = SHAKE_FORCE_THRESHOLD,
    private val requiredReversals: Int = REQUIRED_REVERSALS,
    private val windowMs: Long = SHAKE_WINDOW_MS,
    private val cooldownMs: Long = SHAKE_COOLDOWN_MS,
    private val minSampleIntervalMs: Long = MIN_SAMPLE_INTERVAL_MS,
) {
    private var prevX = 0f
    private var prevY = 0f
    private var prevZ = 0f
    private var reversalCount = 0
    private var firstReversalMs = 0L
    private var lastReversalMs = 0L
    private var lastShakeMs = 0L
    private var lastSampleMs = -1L

    /**
     * Feed an accelerometer reading. Returns true when a shake gesture is detected.
     *
     * Z-axis has gravity subtracted so a resting device does not produce false positives.
     */
    fun onAcceleration(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        if (lastSampleMs >= 0L && nowMs - lastSampleMs < minSampleIntervalMs) return false
        lastSampleMs = nowMs

        if (nowMs - lastShakeMs < cooldownMs && lastShakeMs > 0L) return false

        val az = z - GRAVITY_EARTH_MS2

        var reversed = false

        if (abs(x) >= forceThreshold && x * prevX <= 0) {
            reversed = true
            prevX = x
        }
        if (abs(y) >= forceThreshold && y * prevY <= 0) {
            reversed = true
            prevY = y
        }
        if (abs(az) >= forceThreshold && az * prevZ <= 0) {
            reversed = true
            prevZ = az
        }

        if (!reversed) return false

        if (reversalCount == 0) {
            firstReversalMs = nowMs
        }

        reversalCount++
        lastReversalMs = nowMs

        if (nowMs - firstReversalMs > windowMs) {
            reset()
            return false
        }

        if (reversalCount >= requiredReversals) {
            lastShakeMs = nowMs
            reset()
            return true
        }

        return false
    }

    private fun reset() {
        reversalCount = 0
        firstReversalMs = 0L
        lastReversalMs = 0L
    }
}

/**
 * Emits Unit each time a shake gesture is detected via the accelerometer.
 * Uses callbackFlow to wrap the SensorEventListener callback pattern.
 */
internal fun Context.shakeEvents(): Flow<Unit> = callbackFlow {
    val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    if (accelerometer == null) {
        close()
        return@callbackFlow
    }

    val analyzer = ShakeAnalyzer()

    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val isShake = analyzer.onAcceleration(
                x = event.values[0],
                y = event.values[1],
                z = event.values[2],
                nowMs = System.currentTimeMillis(),
            )
            if (isShake) trySend(Unit)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)

    awaitClose {
        sensorManager.unregisterListener(listener)
    }
}
