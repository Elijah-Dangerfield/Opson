package com.opson.libraries.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ShakeDetector::class)
class AndroidShakeDetector(
    private val context: Context,
) : ShakeDetector, SensorEventListener {

    private val sensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val accelerometer by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private val recognizer = ShakeRecognizer()

    private val shakes = MutableSharedFlow<ShakeEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val shakeEvents: Flow<ShakeEvent> = shakes.asSharedFlow()

    override fun start() {
        // Deliver faster than the recognizer's cadence and let it downsample,
        // so its window arithmetic holds regardless of what the device does
        // with the delay hint.
        accelerometer?.let {
            recognizer.reset()
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        recognizer.reset()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        // Android reports m/s² including gravity, which is what the
        // recognizer expects.
        val now = System.currentTimeMillis()
        val recognized = recognizer.onSample(
            x = event.values[0].toDouble(),
            y = event.values[1].toDouble(),
            z = event.values[2].toDouble(),
            atMs = now,
        )

        if (recognized) {
            shakes.tryEmit(ShakeEvent(now, recognizer.lastIntensity))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
