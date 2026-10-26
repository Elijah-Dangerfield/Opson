package com.opson.features.today.impl

import com.opson.libraries.diary.Fast
import com.opson.libraries.diary.FastingSchedule
import com.opson.libraries.diary.FastingStatus
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.fastingTimerOn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Today's fasting card (0.2, `docs/plans/screens/fasting.md`). Kept apart from
 * [TodayStateBuilder]: it ticks once a minute on its own, and the rest of Today has no reason to
 * rebuild with it.
 */
object TodayFastingCard {

    /**
     * Whether the card exists: fasting offered (the flag, Full or Sprint, and never after the
     * statement), the timer on, "Card on Today" on, and the day on show is today.
     */
    fun shows(settings: Settings, released: Boolean, day: LocalDate, today: LocalDate): Boolean =
        settings.fastingTimerOn(released) && settings.fastToday && day == today

    /** The card's status, or null when it does not show. */
    fun build(
        settings: Settings,
        released: Boolean,
        day: LocalDate,
        today: LocalDate,
        latest: Fast?,
        now: Instant,
        zone: TimeZone,
    ): FastingStatus? =
        if (shows(settings, released, day, today)) FastingSchedule.status(settings.fastPlan, now, zone, latest) else null
}
