package com.opson.features.settings.impl.pause

import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.time.DateCopy
import kotlinx.datetime.LocalTime

data class PauseState(
    val subject: PauseSubject,
    val secondsLeft: Int = PAUSE_SECONDS,
    /** Only ever on screen. Never stored, never logged: "Only you see the answer." */
    val reason: PauseReason? = null,
    /** Show them or Keep has run, so a swipe down afterwards changes and logs nothing. */
    val finished: Boolean = false,
    /** `release.findSupport`: the quiet "Talk to someone" link to Find support. */
    val talkToSomeone: Boolean = false,
) {
    val canShow: Boolean get() = secondsLeft == 0 && !finished

    /** "0:07", the wait as the button spells it. */
    val countdown: String get() = "0:" + secondsLeft.coerceAtLeast(0).toString().padStart(2, '0')

    /** How much of the wait has passed, 0 to 1: the countdown button fills in as it runs. */
    val waited: Float get() = (PAUSE_SECONDS - secondsLeft).coerceIn(0, PAUSE_SECONDS) / PAUSE_SECONDS.toFloat()
}

/** What comes back if the user waits: named in the sheet's title, body and button. */
sealed interface PauseSubject {
    data class Preset(val id: PresetId) : PauseSubject

    data class Number(val metric: Metric) : PauseSubject

    /** Turning the pause itself off. */
    data object ThePause : PauseSubject

    /** Moving the reveal time earlier, so Later numbers open sooner. [text] is "7:30 PM". */
    data class RevealTime(val time: LocalTime) : PauseSubject {
        val text: String get() = DateCopy.time(time.hour, time.minute)
    }

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

/** Design v3's answers to "What's prompting this?". */
enum class PauseReason { Curious, PlanningAMeal, ProviderAsked, FeelingAnxious, SomethingElse }

/** The wait, in seconds. The handoff's 10. */
const val PAUSE_SECONDS = 10
