package com.opson.libraries.navigation

/**
 * Releases navigation commands that are stuck behind a shut lifecycle gate.
 *
 * The router queues commands and drains them only while the Compose host's
 * lifecycle is at least STARTED. That is correct: applying a command to a
 * controller whose host is genuinely gone loses the back stack or crashes. The
 * whole mechanism rests on the host telling the truth about whether its view is
 * on screen, and on iOS it sometimes does not. A full-screen UIKit presentation
 * over the Compose host can leave the host reporting a state below STARTED
 * after its view is visible and taking touches again, and every navigation from
 * that point queues and never runs.
 *
 * The app then takes taps and does nothing with them. Not a freeze: the screen
 * holds its last frame, logging carries on, the shake gesture still works.
 * Buttons simply have no effect. Every bug report for it says "the app froze",
 * which sends you to the main thread, where there is nothing wrong.
 *
 * **Deliberately not a member of [Router].** Feature code injects `Router` and
 * must never reach this: ungating the drain in the ordinary case removes the
 * protection that makes queueing correct, to fix a rare case. The only caller
 * is the watchdog that has evidence the host is lying, which is what makes the
 * repair safe. Keeping it a separate type is what stops it being called on a
 * hunch.
 *
 * @see com.opson.libraries.navigation.NavigationRecovery.drainQueuedNavigation
 */
interface NavigationRecovery {

    /**
     * Applies every queued command immediately, ignoring the lifecycle gate.
     *
     * A no-op when nothing is queued or no controller is attached, so calling
     * it on a false positive costs nothing.
     *
     * Returns nothing on purpose. The work has to hop to the main thread, so
     * any count handed back here would be read before the drain ran, which is a
     * plausible-looking zero. The implementation logs how many commands it
     * released instead, and that count is the diagnosis: zero means the
     * watchdog was wrong, non-zero is the proof it was not.
     */
    fun drainQueuedNavigation()
}

/** A [NavigationRecovery] for graphs that have no router to recover. */
object NoOpNavigationRecovery : NavigationRecovery {
    override fun drainQueuedNavigation() = Unit
}
