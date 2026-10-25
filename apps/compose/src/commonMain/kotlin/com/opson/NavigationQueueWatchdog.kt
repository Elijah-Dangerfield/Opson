package com.opson

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.opson.libraries.core.logging.KLog
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * How long a host may claim to be off screen before a touch on it counts as a
 * contradiction rather than a race.
 *
 * Generous on purpose. The window this has to clear is the real one between a
 * full-screen presentation being dismissed and the host's lifecycle catching
 * up, which is milliseconds. Seconds of it means the host is not catching up at
 * all.
 */
private val WEDGE_THRESHOLD: Duration = 2.seconds

/**
 * Reports a press that lands on a host which has claimed to be off screen for
 * longer than [threshold].
 *
 * The navigation queue drains only at or above `STARTED`, which is correct, and
 * leaves the app dependent on the host telling the truth about its own
 * visibility. On iOS a full-screen UIKit presentation over the Compose host can
 * leave that claim stuck after the view is visible again, and from then on
 * every navigation queues and nothing moves.
 *
 * **A touch is the only signal that is never ordinary.** A host below `STARTED`
 * is completely routine: it is what happens on every backgrounding and behind
 * every full-screen interstitial. Logging that state would be one error per ad
 * and would train everyone to ignore it. But a covered view is not touchable,
 * so a press arriving while the host says its view is off screen is a
 * contradiction, and the host is the half that is wrong. That single
 * observation is what turns an unfalsifiable "sometimes it wedges" into
 * something detectable, and then into something repairable.
 *
 * `PointerEventPass.Initial` so this sees the press before any child consumes
 * it. Nothing is consumed here, so the press still reaches whatever it was
 * aimed at. Detection only.
 *
 * The obvious alternative, ungating the drain, is worse: it removes the
 * protection in the ordinary case to fix the rare one. Gate the repair on the
 * proof instead, which is what [onWedgeDetected] is for.
 */
@Composable
fun Modifier.navigationQueueWatchdog(
    threshold: Duration = WEDGE_THRESHOLD,
    onWedgeDetected: () -> Unit,
): Modifier {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Not Compose state: read from a pointer callback, never rendered, and a
    // write must not schedule recomposition on every lifecycle transition.
    val offScreenSince = remember { arrayOfNulls<TimeMark>(1) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            offScreenSince[0] = when {
                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> null
                else -> offScreenSince[0] ?: TimeSource.Monotonic.markNow()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    return this.pointerInput(threshold) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Press) continue
                val since = offScreenSince[0] ?: continue
                if (since.elapsedNow() < threshold) continue
                // Cleared before reporting, so one wedge produces one report
                // rather than one per finger-down until it resolves.
                offScreenSince[0] = null
                KLog.w {
                    "Press landed on a host that has reported itself off screen for " +
                        "${since.elapsedNow()}. The navigation queue is wedged."
                }
                onWedgeDetected()
            }
        }
    }
}
