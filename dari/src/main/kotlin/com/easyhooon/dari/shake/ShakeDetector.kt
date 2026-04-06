package com.easyhooon.dari.shake

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

internal const val SHAKE_THRESHOLD_G = 2.5f
internal const val SHAKE_COOLDOWN_MS = 1000L

/** Standard gravity in m/s^2. Hardcoded so the analyzer is testable without Android framework. */
internal const val GRAVITY_EARTH_MS2 = 9.80665f

/**
 * Pure shake detection logic. Holds the cooldown state but has no Android dependencies,
 * so it can be unit tested without mocking sensors.
 */
internal class ShakeAnalyzer(
    private val thresholdG: Float = SHAKE_THRESHOLD_G,
    private val cooldownMs: Long = SHAKE_COOLDOWN_MS,
) {
    private var lastShakeTime = 0L
    private var hasShaken = false

    /**
     * Returns true if the given accelerometer reading represents a shake event,
     * accounting for cooldown since the last detected shake.
     *
     * @param x acceleration on the X axis in m/s^2
     * @param y acceleration on the Y axis in m/s^2
     * @param z acceleration on the Z axis in m/s^2
     * @param nowMs current time in milliseconds
     */
    fun onAcceleration(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        val gX = x / GRAVITY_EARTH_MS2
        val gY = y / GRAVITY_EARTH_MS2
        val gZ = z / GRAVITY_EARTH_MS2
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

        if (gForce <= thresholdG) return false
        if (hasShaken && nowMs - lastShakeTime <= cooldownMs) return false

        hasShaken = true
        lastShakeTime = nowMs
        return true
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