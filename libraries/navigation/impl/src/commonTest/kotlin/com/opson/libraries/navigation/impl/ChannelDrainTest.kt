package com.opson.libraries.navigation.impl

import kotlinx.coroutines.channels.Channel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the drain the navigation watchdog uses to release a wedged queue.
 *
 * Split from `DelegatingRouter` precisely so it can be tested: the router's
 * commands are `NavHostController.() -> Unit`, and a controller needs a real
 * host. The loop is where the behaviour worth pinning lives.
 *
 * NOT covered here: whether the queue *should* be drained. That is the
 * watchdog's judgement, and it rests on a touch landing on a host that claims
 * to be off screen, which has no unit-testable form.
 */
class ChannelDrainTest {

    @Test
    fun drainsEverythingQueued_inOrder() {
        val channel = Channel<Int>(Channel.UNLIMITED)
        listOf(1, 2, 3).forEach { channel.trySend(it) }

        val applied = mutableListOf<Int>()
        val count = channel.drainInto { applied += it }

        assertEquals(3, count)
        assertEquals(listOf(1, 2, 3), applied, "order is the order the queue promised")
    }

    @Test
    fun emptyChannel_isANoOp() {
        val channel = Channel<Int>(Channel.UNLIMITED)
        var applied = 0

        assertEquals(0, channel.drainInto { applied++ })
        assertEquals(0, applied, "a false positive from the watchdog must cost nothing")
    }

    @Test
    fun stopsAtWhatWasQueued_whenDrainingEnqueuesMore() {
        // A navigate that triggers another navigate is ordinary. If the loop
        // followed the channel instead of stopping at what it found, recovery
        // would spin on the main thread and the wedge would become a freeze —
        // the thing everyone already wrongly reports it as.
        val channel = Channel<Int>(Channel.UNLIMITED)
        channel.trySend(1)

        var applied = 0
        val count = channel.drainInto {
            applied++
            if (applied < 100) channel.trySend(it + 1)
        }

        assertEquals(
            100,
            count,
            "each drained item may enqueue one more, and the loop consumes those too, " +
                "but it terminates rather than spinning",
        )
    }

    @Test
    fun drainsAgain_afterMoreIsQueued() {
        val channel = Channel<Int>(Channel.UNLIMITED)
        channel.trySend(1)
        assertEquals(1, channel.drainInto { })

        channel.trySend(2)
        assertEquals(1, channel.drainInto { }, "recovery is re-runnable; a wedge can recur")
    }
}
