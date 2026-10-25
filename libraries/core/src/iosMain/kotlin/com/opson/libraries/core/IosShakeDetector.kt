package com.opson.libraries.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.tatarka.inject.annotations.Inject
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSDate
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTimeInterval
import platform.Foundation.timeIntervalSince1970
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@OptIn(ExperimentalForeignApi::class)
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosShakeDetector : ShakeDetector {

    private val motionManager = CMMotionManager()

    private val recognizer = ShakeRecognizer()

    private val shakes = MutableSharedFlow<ShakeEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val shakeEvents: Flow<ShakeEvent> = shakes.asSharedFlow()

    override fun start() {
        if (!motionManager.accelerometerAvailable) return

        recognizer.reset()
        motionManager.accelerometerUpdateInterval = UPDATE_INTERVAL
        motionManager.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
            data?.acceleration?.useContents {
                // CoreMotion reports multiples of gravity; the recognizer works
                // in m/s². Skipping this conversion is what made iOS roughly
                // twice as hard to shake as Android.
                onSample(
                    x = x * STANDARD_GRAVITY,
                    y = y * STANDARD_GRAVITY,
                    z = z * STANDARD_GRAVITY,
                )
            }
        }
    }

    override fun stop() {
        motionManager.stopAccelerometerUpdates()
        recognizer.reset()
    }

    private fun onSample(x: Double, y: Double, z: Double) {
        val now = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()
        if (recognizer.onSample(x, y, z, now)) {
            shakes.tryEmit(ShakeEvent(now, recognizer.lastIntensity))
        }
    }

    private companion object {
        // Delivered faster than the recognizer's cadence so it, not CoreMotion,
        // decides the sampling rate.
        const val UPDATE_INTERVAL: NSTimeInterval = 0.05
        const val STANDARD_GRAVITY = 9.80665
        const val MILLIS_PER_SECOND = 1000
    }
}
