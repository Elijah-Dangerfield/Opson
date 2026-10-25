package com.opson.libraries.navigation.impl

import kotlinx.coroutines.channels.Channel

/**
 * Applies every item currently in the channel and returns how many there were.
 *
 * Split out from the router so the drain loop can be tested without a
 * `NavHostController`, which needs a real Android or iOS host to construct. The
 * loop is the part with the interesting behaviour: it has to stop at the items
 * present when it started rather than following the channel, or a command that
 * enqueues another command would spin here forever.
 *
 * `tryReceive` rather than `receive`: this runs on the main thread from a
 * watchdog, so it must not suspend, and an empty channel is the expected case.
 */
internal fun <T> Channel<T>.drainInto(apply: (T) -> Unit): Int {
    var drained = 0
    while (true) {
        val item = tryReceive().getOrNull() ?: return drained
        apply(item)
        drained++
    }
}
