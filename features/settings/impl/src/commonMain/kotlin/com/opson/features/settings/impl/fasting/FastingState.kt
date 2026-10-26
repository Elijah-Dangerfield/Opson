package com.opson.features.settings.impl.fasting

import com.opson.libraries.diary.Fast
import com.opson.libraries.diary.FastingAccess
import com.opson.libraries.diary.FastingSchedule
import com.opson.libraries.diary.FastingStatus
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.FastingPlan
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** The Fasting page (docs/plans/screens/fasting.md). */
data class FastingState(
    val loading: Boolean = true,
    val page: FastingPage? = null,
)

sealed interface FastingPage {
    /**
     * Status first (only while the timer is on), then the settings card (design v3). The Today
     * card switch and the plans do nothing while the timer is off, as the prototype dims them.
     */
    data class Offered(
        val timerOn: Boolean,
        val showOnToday: Boolean,
        val plan: FastingPlan,
        val plans: List<FastingPlan>,
        val opens: LocalTime,
        val closes: LocalTime,
        val status: FastingStatus?,
    ) : FastingPage

    /** Reached in a mode without fasting: "Fasting isn't part of this mode". */
    data object NotInThisMode : FastingPage

    /** Fasting does not exist for this person (flag off, or the statement). The page closes. */
    data object Hidden : FastingPage
}

/** Everything the page reads, so [FastingStateBuilder] is a pure function of it. */
data class FastingInputs(
    val settings: Settings,
    val released: Boolean,
    val latest: Fast?,
    val now: Instant,
    val zone: TimeZone,
)

object FastingStateBuilder {

    fun build(inputs: FastingInputs): FastingState {
        val settings = inputs.settings
        val page = when (FastingAccess.of(settings, inputs.released)) {
            FastingAccess.Hidden -> FastingPage.Hidden
            FastingAccess.NotInThisMode -> FastingPage.NotInThisMode
            FastingAccess.Offered -> FastingPage.Offered(
                timerOn = settings.fasting,
                showOnToday = settings.fastToday,
                plan = settings.fastPlan,
                plans = FastingPlan.entries,
                opens = settings.fastPlan.opens,
                closes = settings.fastPlan.closes,
                status = if (settings.fasting) {
                    FastingSchedule.status(settings.fastPlan, inputs.now, inputs.zone, inputs.latest)
                } else {
                    null
                },
            )
        }
        return FastingState(loading = false, page = page)
    }
}
