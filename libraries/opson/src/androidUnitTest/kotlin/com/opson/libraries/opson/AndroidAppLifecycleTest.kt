package com.opson.libraries.opson

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers: registration with the process lifecycle is handed to the main thread
 * instead of running on whichever thread the caller arrived on, and add/remove
 * stay in call order once they get there.
 *
 * NOT covered: that the production default really targets the main looper.
 * That needs a real `Looper`, which a host-JVM unit test does not have; the
 * test drives the same seam with a queue it can drain.
 */
class AndroidAppLifecycleTest {

    private val lifecycle = RecordingLifecycle()
    private val mainThreadQueue = mutableListOf<() -> Unit>()
    private val appLifecycle = AndroidAppLifecycle(lifecycle) { mainThreadQueue += it }

    @Test
    fun addObserver_doesNotRegisterOnTheCallersThread() {
        appLifecycle.addObserver(NoopObserver())

        assertEquals(
            emptyList(),
            lifecycle.calls,
            "registration ran inline, so it runs on whatever thread the caller arrived on — " +
                "off the main thread LifecycleRegistry throws instead of synchronizing.",
        )

        drainMainThread()

        assertEquals(listOf("add"), lifecycle.calls)
    }

    @Test
    fun removeObserver_cannotOvertakeTheAddItPairsWith() {
        val observer = NoopObserver()

        appLifecycle.addObserver(observer)
        appLifecycle.removeObserver(observer)
        drainMainThread()

        assertEquals(listOf("add", "remove"), lifecycle.calls)
    }

    private fun drainMainThread() {
        while (mainThreadQueue.isNotEmpty()) {
            mainThreadQueue.removeAt(0).invoke()
        }
    }
}

private class NoopObserver : AppLifecycleObserver {
    override fun onEnterForeground() = Unit
    override fun onEnterBackground() = Unit
}

private class RecordingLifecycle : Lifecycle() {
    val calls = mutableListOf<String>()

    override val currentState: State = State.RESUMED

    override fun addObserver(observer: LifecycleObserver) {
        calls += "add"
    }

    override fun removeObserver(observer: LifecycleObserver) {
        calls += "remove"
    }
}
