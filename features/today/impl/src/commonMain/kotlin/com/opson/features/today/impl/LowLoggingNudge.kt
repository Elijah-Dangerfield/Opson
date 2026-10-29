package com.opson.features.today.impl

import com.opson.libraries.diary.LoggingHabits
import com.opson.libraries.diary.LoggingHabitsEstimator
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Visibility
import kotlinx.datetime.LocalTime

/**
 * The late-day card for a day that got away (owner feedback, 2026-10-29): "Looks like logging got
 * away from you today. That's okay." with Skip today, which marks the day untracked so Trends
 * leaves a gap, and Not now.
 *
 * It shows on today alone, in the evening, when today holds much less than the person's own
 * usual day, and only once they have a usual day to compare with. Evening is an hour after their
 * usual last meal, learned from the log, kept between 6 pm and 10 pm; 8 pm until a meal is learned.
 *
 * The comparison respects the mode: where calories are not on show, or for someone who said
 * numbers have been hard, it counts entries (foods and meals marked eaten) instead of energy, so
 * an amount never decides it. Nothing on the card is a number.
 */
internal object LowLoggingNudge {
    /** About a week of logging before there is a usual day to compare with: never on the first days after install. */
    const val MIN_LOGGED_DAYS = 5

    /** "Very little" is under about a third of a usual day. */
    const val SHARE = 0.35

    private val DEFAULT_EVENING = LocalTime(20, 0)
    private val EARLIEST = LocalTime(18, 0)
    private val LATEST = LocalTime(22, 0)
    private const val AFTER_LAST_MEAL_MINUTES = 60
    private const val MINUTES_PER_HOUR = 60

    fun shows(inputs: TodayInputs): Boolean {
        val day = inputs.day
        val habits = inputs.habits ?: return false
        val now = inputs.now ?: return false
        if (day.date != inputs.today || day.untracked || day.closedByUser) return false
        if (inputs.nudgeDismissedOn == inputs.today) return false
        if (habits.loggedDays < MIN_LOGGED_DAYS) return false
        if (now < eveningFrom(habits)) return false
        return littleLogged(inputs, habits)
    }

    /** An hour after the usual last meal, between 6 pm and 10 pm; 8 pm until a meal's time is learned. */
    fun eveningFrom(habits: LoggingHabits): LocalTime {
        val last = habits.lastMealTime ?: return DEFAULT_EVENING
        val minutes = (last.hour * MINUTES_PER_HOUR + last.minute + AFTER_LAST_MEAL_MINUTES)
            .coerceIn(EARLIEST.minutes(), LATEST.minutes())
        return LocalTime(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    }

    private fun littleLogged(inputs: TodayInputs, habits: LoggingHabits): Boolean {
        val day = inputs.day
        val entries = LoggingHabitsEstimator.entries(day)
        if (entries == 0) return true
        val settings = inputs.settings
        val byEnergy = settings.numbers.level(Metric.Calories) != Visibility.Hide && !settings.onboarding.hard && day.ateNoDetails.isEmpty()
        val typicalKcal = habits.typicalKcal
        if (byEnergy && typicalKcal != null && typicalKcal > 0.0) return day.totals().kcal.known < typicalKcal * SHARE
        val typicalEntries = habits.typicalEntries ?: return false
        return entries < typicalEntries * SHARE
    }

    private fun LocalTime.minutes(): Int = hour * MINUTES_PER_HOUR + minute
}
