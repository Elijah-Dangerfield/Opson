package com.opson.libraries.diary

import com.opson.libraries.nutrition.NumbersChange

/**
 * The one way anything changes which numbers the user sees. Modes and numbers,
 * the water toggle, the weight view and the scores page all describe what they
 * want as a [NumbersChange] and hand it here; none of them writes
 * `Settings.numbers` itself.
 *
 * The rules, from the handoff and `docs/plans/screens/settings.md`:
 *
 * - Showing fewer numbers always applies at once.
 * - With "Pause before showing more" on, any change that shows more, and
 *   turning the pause itself off, comes back [NumbersOutcome.NeedsPause] and
 *   writes nothing. The screen opens the pause sheet, which calls [confirm]
 *   after the wait.
 * - Turning the pause on is instant ([turnOnPause]).
 *
 * Every change is stamped (`changedAt`) so Modes can say when the user last
 * changed it, and every mode app event comes from here.
 */
interface NumbersChanger {

    /** Applies [change] now if it may, or says the pause sheet has to run first. */
    suspend fun request(change: NumbersChange): NumbersOutcome

    /** Applies [change] after the pause sheet's wait, whatever it raises. Only the sheet calls this. */
    suspend fun confirm(change: NumbersChange)

    /** "Pause before showing more" on. Never pauses: it can only show fewer numbers later. */
    suspend fun turnOnPause()

    /** The pause sheet opened for [change]. Telemetry only. */
    fun pauseShown(change: NumbersChange)

    /** The pause sheet closed without applying [change]. Telemetry only. */
    fun pauseKept(change: NumbersChange)
}

sealed interface NumbersOutcome {
    /** Written. [fewer] when the user now sees fewer numbers than before ("Saved. Fewer numbers from now on."). */
    data class Applied(val fewer: Boolean) : NumbersOutcome

    /** Nothing written: the pause sheet has to run first. */
    data object NeedsPause : NumbersOutcome
}
