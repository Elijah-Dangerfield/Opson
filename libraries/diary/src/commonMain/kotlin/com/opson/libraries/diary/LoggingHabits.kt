package com.opson.libraries.diary

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.pow

/**
 * How someone usually logs, from their last few weeks (owner feedback, 2026-10-29): when each meal
 * usually goes in, and how much a logged day usually holds. Today's "Usually around" times and its
 * late-day nudge read it.
 *
 * [usual] is the learned time for each meal in [learned], and the person's own time
 * ([Settings.mealTimes], set in onboarding and Reminders) for the rest, so a new install reads as
 * it always did until there is enough to learn from.
 */
data class LoggingHabits(
    val usual: MealTimes = MealTimes(),
    val learned: Set<MealSlot> = emptySet(),
    /** Days in the window with anything logged, untracked ones left out. */
    val loggedDays: Int = 0,
    /** The middle of those days' known energy, raw kcal; null with no logged day. */
    val typicalKcal: Double? = null,
    /** The middle of those days' entries: foods logged plus meals marked "ate, no details". */
    val typicalEntries: Double? = null,
) {
    /** The latest of the learned meals: when the day's eating usually ends. Null until one is learned. */
    val lastMealTime: LocalTime? get() = learned.maxOfOrNull { usual.at(it) }
}

object LoggingHabitsEstimator {
    /** Four weeks: long enough to steady a time, short enough to follow a new routine. */
    const val WINDOW_DAYS = 28

    /** A meal needs this many days with an honest time before its time is learned. */
    const val MIN_SAMPLES = 3

    /** A day's weight halves every two weeks, so this month's routine outweighs last month's. */
    private const val HALF_LIFE_DAYS = 14.0
    private const val ROUND_TO_MINUTES = 5
    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

    /**
     * Habits from [days] in the [WINDOW_DAYS] before [today]. Today's meals count for when meals
     * usually are, so a meal logged now moves its time at once; today is left out of the usual
     * day's size, which today is measured against. A day marked untracked says nothing.
     *
     * A meal's time on a day is when its first item was logged, and only when that was on the
     * meal's own day: an item back-filled later says nothing about when it was eaten. Each
     * meal's usual time is the weighted median of those times, recent days counting for more,
     * so one late dinner or one forgotten breakfast logged at noon doesn't move it.
     */
    fun of(days: List<Day>, today: LocalDate, timeZone: TimeZone, fallback: MealTimes): LoggingHabits {
        val from = today.minus(WINDOW_DAYS, DateTimeUnit.DAY)
        val window = days.filter { it.date >= from && it.date <= today && !it.untracked }
        val logged = window.filter { it.hasLogged && it.date < today }

        var usual = fallback
        val learned = mutableSetOf<MealSlot>()
        MealSlot.meals.forEach { slot ->
            val samples = window.mapNotNull { day -> firstLogged(day, slot, timeZone)?.let { it to weight(day.date, today) } }
            if (samples.size < MIN_SAMPLES) return@forEach
            usual = usual.with(slot, toTime(roundTo(weightedMedian(samples), ROUND_TO_MINUTES)))
            learned += slot
        }
        return LoggingHabits(
            usual = usual,
            learned = learned,
            loggedDays = logged.size,
            typicalKcal = median(logged.map { it.totals().kcal.known }),
            typicalEntries = median(logged.map { entries(it).toDouble() }),
        )
    }

    /** Foods logged plus meals marked eaten with no details: how much of a day went in, in any mode. */
    fun entries(day: Day): Int = day.allItems.size + day.ateNoDetails.size

    private fun firstLogged(day: Day, slot: MealSlot, timeZone: TimeZone): Int? =
        day.items(slot)
            .map { it.loggedAt.toLocalDateTime(timeZone) }
            .filter { it.date == day.date }
            .minOfOrNull { it.hour * MINUTES_PER_HOUR + it.minute }

    private fun weight(date: LocalDate, today: LocalDate): Double {
        val age = (today.toEpochDays() - date.toEpochDays()).toDouble()
        return 0.5.pow(age / HALF_LIFE_DAYS)
    }

    /** The value at which half the weight lies on each side; the lower of two that straddle it. */
    internal fun weightedMedian(samples: List<Pair<Int, Double>>): Int {
        val sorted = samples.sortedBy { it.first }
        val half = sorted.sumOf { it.second } / 2
        var running = 0.0
        sorted.forEach { (value, weight) ->
            running += weight
            if (running >= half) return value
        }
        return sorted.last().first
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private fun roundTo(minutes: Int, step: Int): Int = (((minutes + step / 2) / step) * step).coerceAtMost(MINUTES_PER_DAY - step)

    private fun toTime(minutes: Int): LocalTime = LocalTime(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
}
