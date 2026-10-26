package com.opson.libraries.diary

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Plain facts about when and how someone eats over a span of days, for Patterns
 * (design v3, 5.6). Facts only, never advice: the screen states them and lets
 * people connect them themselves.
 *
 * Times are minutes after midnight. Energy is raw kcal for the screen to send
 * through the visibility policy; nothing here is formatted.
 */
data class MealPatternFacts(
    /** Days in the span, both ends included, less any marked untracked. */
    val dayCount: Int,
    /** When each meal was eaten, one entry per day it has an honest time. */
    val times: Map<MealSlot, List<Int>>,
    /** The middle half of each meal's times, for meals with at least [MealPatterns.MIN_FOR_USUAL]. */
    val usual: Map<MealSlot, IntRange>,
    /** Each meal's median time, to the nearest five minutes, alongside [usual]. */
    val typical: Map<MealSlot, Int>,
    /** Days each meal was done (items or "ate, no details"). */
    val frequency: Map<MealSlot, Int>,
    /** A meal that runs clearly later on a few weekdays. */
    val laterOn: LaterOn?,
    /** The pair of back-to-back meals with the longest usual gap between them. */
    val longestGap: MealGap?,
    /** The most common hunger tag before each meal, for meals with any. */
    val hunger: Map<MealSlot, Hunger>,
    /** Meals that carry a hunger tag. */
    val taggedMeals: Int,
    /** Average energy of one meal, over the meals with known energy. */
    val energyPerMeal: Map<MealSlot, Double>,
)

/** [slot] on [days] is usually [minutes] later than on the rest of the week. */
data class LaterOn(val slot: MealSlot, val days: List<DayOfWeek>, val minutes: Int)

/** Usually [minutes] between [from] and [to], the meal that follows it. */
data class MealGap(val from: MealSlot, val to: MealSlot, val minutes: Int)

object MealPatterns {

    /** A usual window needs a few meals before it means anything. */
    const val MIN_FOR_USUAL = 3

    /** How much later a weekday has to run before it is worth saying. */
    const val LATER_MINUTES = 45

    private const val MIN_FOR_LATER = 6
    private const val MAX_LATER_DAYS = 3
    private const val MIN_DAYS_FOR_GAP = 3
    private const val ROUND_TO = 5
    private const val MINUTES_PER_HOUR = 60
    private const val QUARTER = 0.25
    private const val THREE_QUARTERS = 0.75

    /**
     * Facts over [days] between [from] and [to], both included. Days outside are
     * ignored, and a missing date is an empty day. A day marked untracked is
     * left out entirely, from the facts and from [MealPatternFacts.dayCount], so
     * a holiday never reads as a day with a meal missing.
     *
     * A meal's time is when its first item was logged, and only when that was
     * on the meal's own day: an item back-filled on a later day says nothing
     * about when it was eaten, so it counts for how often and how much, not when.
     */
    fun of(days: List<Day>, from: LocalDate, to: LocalDate, timeZone: TimeZone): MealPatternFacts {
        val inRange = days.filter { it.date in from..to }
        val span = inRange.filterNot { it.untracked }
        val dayCount = ((to.toEpochDays() - from.toEpochDays() + 1).toInt() - (inRange.size - span.size)).coerceAtLeast(0)
        val timed: List<Pair<Day, Map<MealSlot, Int>>> = span.map { day -> day to mealTimes(day, timeZone) }

        val times = MealSlot.meals.associateWith { slot -> timed.mapNotNull { (_, meals) -> meals[slot] } }
            .filterValues { it.isNotEmpty() }
        val typical = times.mapValues { (_, list) -> roundTo(median(list), ROUND_TO) }
        val usual = times.filterValues { it.size >= MIN_FOR_USUAL }.mapValues { (_, list) -> middleHalf(list) }

        val tags = span.flatMap { day ->
            MealSlot.meals.mapNotNull { slot -> day.feelings[slot]?.hunger?.takeIf { day.isMealDone(slot) }?.let { slot to it } }
        }

        return MealPatternFacts(
            dayCount = dayCount,
            times = times,
            usual = usual,
            typical = typical,
            frequency = MealSlot.meals.associateWith { slot -> span.count { it.isMealDone(slot) } },
            laterOn = laterOn(timed, typical),
            longestGap = longestGap(timed),
            hunger = tags.groupBy({ it.first }, { it.second }).mapValues { (_, list) -> mostCommon(list) },
            taggedMeals = tags.size,
            energyPerMeal = MealSlot.meals.associateWith { slot ->
                span.filter { it.items(slot).isNotEmpty() }.mapNotNull { it.totals(slot).kcal.value }
            }.filterValues { it.isNotEmpty() }.mapValues { (_, list) -> list.average() },
        )
    }

    private fun mealTimes(day: Day, timeZone: TimeZone): Map<MealSlot, Int> = MealSlot.meals.mapNotNull { slot ->
        day.items(slot)
            .map { it.loggedAt.toLocalDateTime(timeZone) }
            .filter { it.date == day.date }
            .minOfOrNull { it.hour * MINUTES_PER_HOUR + it.minute }
            ?.let { slot to it }
    }.toMap()

    private fun laterOn(timed: List<Pair<Day, Map<MealSlot, Int>>>, typical: Map<MealSlot, Int>): LaterOn? =
        MealSlot.meals.mapNotNull { slot ->
            val usual = typical[slot] ?: return@mapNotNull null
            val byWeekday = timed.mapNotNull { (day, meals) -> meals[slot]?.let { day.date.dayOfWeek to it } }
            if (byWeekday.size < MIN_FOR_LATER) return@mapNotNull null
            val later = byWeekday.groupBy({ it.first }, { it.second })
                .filterValues { it.size >= 2 }
                .mapValues { (_, list) -> median(list) - usual }
                .filterValues { it >= LATER_MINUTES }
            if (later.isEmpty() || later.size > MAX_LATER_DAYS) return@mapNotNull null
            LaterOn(slot, later.keys.sortedBy { it.ordinal }, later.values.average().roundToInt())
        }.maxByOrNull { it.minutes }

    private fun longestGap(timed: List<Pair<Day, Map<MealSlot, Int>>>): MealGap? {
        val gaps = mutableMapOf<Pair<MealSlot, MealSlot>, MutableList<Int>>()
        timed.forEach { (_, meals) ->
            meals.entries.sortedBy { it.value }.zipWithNext { a, b ->
                gaps.getOrPut(a.key to b.key) { mutableListOf() } += b.value - a.value
            }
        }
        return gaps.filterValues { it.size >= MIN_DAYS_FOR_GAP }
            .map { (pair, list) -> MealGap(pair.first, pair.second, median(list)) }
            .maxByOrNull { it.minutes }
    }

    private fun median(values: List<Int>): Int {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private fun middleHalf(values: List<Int>): IntRange {
        val sorted = values.sorted()
        val last = sorted.lastIndex
        return sorted[(last * QUARTER).toInt()]..sorted[ceil(last * THREE_QUARTERS).toInt()]
    }

    private fun roundTo(minutes: Int, step: Int): Int = ((minutes + step / 2) / step) * step

    /** The tag seen most; a tie goes to the hungrier one, which comes first in [Hunger]. */
    private fun mostCommon(tags: List<Hunger>): Hunger {
        val counts = tags.groupingBy { it }.eachCount()
        return counts.keys.sortedWith(compareByDescending<Hunger> { counts.getValue(it) }.thenBy { it.ordinal }).first()
    }
}

/** Foods tried for the first time in a while. */
object NewFoods {

    /** How long a food has to be absent to count as new again. There is no "first ever" query. */
    const val LOOKBACK_DAYS = 90

    /**
     * Names of the foods logged between [from] and [to] (both included) that
     * were not logged in the [lookbackDays] before [from], in the order they
     * were first logged. A food is its catalog id, custom food id, or its
     * lower-cased name; quick numbers are not foods.
     */
    fun between(days: List<Day>, from: LocalDate, to: LocalDate, lookbackDays: Int = LOOKBACK_DAYS): List<String> {
        val lookbackStart = from.minus(lookbackDays, DateTimeUnit.DAY)
        val before = days.asSequence()
            .filter { it.date >= lookbackStart && it.date < from }
            .flatMap { it.allItems }
            .mapNotNull { it.foodKey() }
            .toSet()
        return days.asSequence()
            .filter { it.date in from..to }
            .sortedBy { it.date }
            .flatMap { day -> day.allItems.sortedBy { it.loggedAt } }
            .mapNotNull { item -> item.foodKey()?.let { it to item.name.trim() } }
            .filter { (key, _) -> key !in before }
            .distinctBy { it.first }
            .map { it.second }
            .toList()
    }

    private fun LogItem.foodKey(): String? {
        if (method == LogMethod.QuickNumber) return null
        return foodId?.let { "food:$it" }
            ?: customFoodId?.let { "custom:$it" }
            ?: name.trim().lowercase().takeIf(String::isNotEmpty)?.let { "name:$it" }
    }
}
