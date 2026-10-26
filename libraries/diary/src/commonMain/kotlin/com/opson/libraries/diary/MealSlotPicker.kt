package com.opson.libraries.diary

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

/**
 * Which meal the logger opens on when nobody said (the nav +), learned from
 * when this person logs. Design sync 2, "ADD SHEET": the meal they usually log
 * at this time of day over the last [HISTORY_DAYS] days; with too few logs near
 * now, or no clear winner, [MealSlot.Uncategorized], which Today files later.
 *
 * Before there is any history to speak of, the clock default
 * ([MealSlot.defaultFor]) applies, so a new person never opens on
 * Uncategorized on their first day.
 *
 * Only an item logged on its own day says when it was eaten: one back-filled
 * the next morning is left out, as in [MealPatterns].
 */
object MealSlotPicker {

    /** How far back the picker looks. */
    const val HISTORY_DAYS = 14

    /** Logs within this many minutes of now, either side, count as "at this time". */
    const val WINDOW_MINUTES = 60

    /** Fewer logs than this near now is no pattern. */
    const val MIN_LOGS = 3

    /** Fewer days with a timed meal than this is no history yet: the clock decides. */
    const val MIN_HISTORY_DAYS = 3

    /** The winning meal needs at least this share of the logs near now. */
    const val CLEAR_SHARE = 0.6

    private const val MINUTES_PER_DAY = 24 * 60
    private const val MINUTES_PER_HOUR = 60

    /**
     * The meal for [now]. [days] may hold anything; only the [HISTORY_DAYS]
     * days before [now]'s date and that date itself are read. With meal groups
     * off everything is [MealSlot.Uncategorized].
     */
    fun pick(days: List<Day>, now: LocalDateTime, timeZone: TimeZone, mealGroups: Boolean = true): MealSlot {
        if (!mealGroups) return MealSlot.Uncategorized
        val logs = timedLogs(days, now, timeZone)
        if (logs.map { it.day }.distinct().size < MIN_HISTORY_DAYS) return MealSlot.defaultFor(now.time)
        val near = logs.filter { distance(it.minutes, now.minuteOfDay) <= WINDOW_MINUTES && it.slot.isMeal }
        if (near.size < MIN_LOGS) return MealSlot.Uncategorized
        val (slot, count) = near.groupingBy { it.slot }.eachCount().maxBy { it.value }
        return if (count >= near.size * CLEAR_SHARE) slot else MealSlot.Uncategorized
    }

    /**
     * The foods this person logged within [WINDOW_MINUTES] of [now] over the
     * same days, in any slot: most often first, ties to the latest, each as its
     * latest entry. The logger's "Usual around 10:30 am" for Uncategorized.
     */
    fun usualAround(days: List<Day>, now: LocalDateTime, timeZone: TimeZone, limit: Int = 3): List<LogItem> =
        timedLogs(days, now, timeZone)
            .filter { distance(it.minutes, now.minuteOfDay) <= WINDOW_MINUTES && it.item.method != LogMethod.QuickNumber }
            .groupBy { it.item.foodKey() }
            .values
            .sortedWith(compareByDescending<List<TimedLog>> { it.size }.thenByDescending { group -> group.maxOf { it.item.loggedAt } })
            .map { group -> group.maxBy { it.item.loggedAt }.item }
            .take(limit)

    /** "Usual around" names the half hour now falls in: 10:40 reads 10:30. */
    fun aroundLabelTime(now: LocalDateTime): LocalTime {
        val half = MINUTES_PER_HOUR / 2
        return LocalTime(now.hour, now.minute / half * half)
    }

    private data class TimedLog(val day: LocalDate, val slot: MealSlot, val minutes: Int, val item: LogItem)

    private fun timedLogs(days: List<Day>, now: LocalDateTime, timeZone: TimeZone): List<TimedLog> {
        val from = now.date.minus(HISTORY_DAYS, DateTimeUnit.DAY)
        return days.asSequence()
            .filter { it.date >= from && it.date <= now.date && !it.untracked }
            .flatMap { day ->
                day.allItems.asSequence().mapNotNull { item ->
                    val at = item.loggedAt.toLocalDateTime(timeZone)
                    // A meal logged on a later day says nothing about when it was eaten.
                    if (at.date != day.date) return@mapNotNull null
                    TimedLog(day.date, item.slot, at.hour * MINUTES_PER_HOUR + at.minute, item)
                }
            }
            .toList()
    }

    private val LocalDateTime.minuteOfDay: Int get() = hour * MINUTES_PER_HOUR + minute

    /** Minutes between two times of day, the short way round midnight. */
    private fun distance(a: Int, b: Int): Int {
        val d = abs(a - b)
        return minOf(d, MINUTES_PER_DAY - d)
    }

    private fun LogItem.foodKey(): String =
        foodId?.let { "food:$it" } ?: customFoodId?.let { "custom:$it" } ?: "name:${name.trim().lowercase()}"
}
