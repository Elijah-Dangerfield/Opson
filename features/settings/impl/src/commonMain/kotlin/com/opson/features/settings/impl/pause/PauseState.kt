package com.opson.features.settings.impl.pause

import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.PresetId
import kotlinx.datetime.LocalTime

data class PauseState(
    val subject: PauseSubject,
    val secondsLeft: Int = PAUSE_SECONDS,
    /** Only ever on screen. Never stored, never logged: "Only you see the answer." */
    val reason: PauseReason? = null,
    /** Show them or Keep has run, so a swipe down afterwards changes and logs nothing. */
    val finished: Boolean = false,
) {
    val canShow: Boolean get() = secondsLeft == 0 && !finished
}

/** What comes back if the user waits: named in the sheet's sentence. */
sealed interface PauseSubject {
    data class Preset(val id: PresetId) : PauseSubject

    data class Number(val metric: Metric) : PauseSubject

    /** Turning the pause itself off. */
    data object ThePause : PauseSubject

    /** Moving the reveal time earlier, so Later numbers open sooner. */
    data class RevealTime(val time: LocalTime) : PauseSubject

    companion object {
        fun of(change: NumbersChange): PauseSubject = when (change) {
            is NumbersChange.PickPreset -> Preset(change.id)
            is NumbersChange.Override -> Number(change.metric)
            is NumbersChange.ClearOverride -> Number(change.metric)
            NumbersChange.TurnOffPause -> ThePause
            is NumbersChange.SetRevealAt -> RevealTime(change.time)
        }
    }
}

enum class PauseReason { Curious, WorkingTowardAGoal, WorriedOffTrack, SomeoneAsked }

/** The wait, in seconds. The handoff's 10. */
const val PAUSE_SECONDS = 10
