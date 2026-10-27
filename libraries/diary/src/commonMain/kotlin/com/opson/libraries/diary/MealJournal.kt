package com.opson.libraries.diary

import kotlinx.datetime.LocalDate

/**
 * What someone wrote down about their meals over a span of days, for Trends: how hungry each meal
 * was, how many carry a note, and what older "How it felt" rows held (fullness and moods, which
 * nothing asks for any more). Counts and levels only. Note text never leaves here, so nothing a
 * person typed can end up on a chart.
 *
 * Hunger is a [Hunger.level], 0 for stuffed up to [MealPatterns.HUNGER_STEPS] for starving. It is
 * information, not a score: no level is better than another.
 */
data class JournalFacts(
    /** Each day's hunger levels, one per answered meal in meal order; days with none are missing. */
    val hungerByDay: Map<LocalDate, List<Int>>,
    /** Hunger before each of the four meals, for meals with any answer. */
    val hungerBySlot: Map<MealSlot, SlotHunger>,
    /** Meals done in the span (items or "ate, no details"). */
    val meals: Int,
    /** Done meals with a hunger level. */
    val hungerMeals: Int,
    /** Done meals with note text. */
    val notedMeals: Int,
    /** How many meals had each fullness, for the ones any did. */
    val fullness: Map<Fullness, Int>,
    /** How many meals had each mood, for the ones any did. */
    val moods: Map<Mood, Int>,
) {
    /** Nothing written about any meal in the span. */
    val isEmpty: Boolean get() = hungerMeals == 0 && notedMeals == 0 && fullness.isEmpty() && moods.isEmpty()
}

/** One meal's hunger over the span: the average level, the spread, and the answer given most. */
data class SlotHunger(val average: Double, val lowest: Int, val highest: Int, val answers: Int, val usual: Hunger)

/** Where a [Hunger] sits on the scale [MealPatterns] and [MealJournal] average: 0 stuffed, [MealPatterns.HUNGER_STEPS] starving. */
val Hunger.level: Int get() = MealPatterns.HUNGER_STEPS - ordinal

object MealJournal {

    /**
     * Facts over [days] between [from] and [to], both included. A day marked untracked is left
     * out, as Patterns leaves it out. Only done meals count: a feeling stays stored when its
     * meal's items are deleted, and an empty meal's old hunger says nothing about this span.
     */
    fun of(days: List<Day>, from: LocalDate, to: LocalDate): JournalFacts {
        val entries = days
            .filter { it.date in from..to && !it.untracked }
            .flatMap { day -> MealSlot.meals.filter(day::isMealDone).map { slot -> Entry(day.date, slot, day.feelings[slot]) } }
        val hungry = entries.mapNotNull { entry -> entry.feeling?.hunger?.let { entry to it } }
        return JournalFacts(
            hungerByDay = hungry.groupBy({ it.first.date }, { it.second.level }),
            hungerBySlot = hungry.groupBy({ it.first.slot }, { it.second }).mapValues { (_, answers) ->
                val levels = answers.map { it.level }
                SlotHunger(levels.average(), levels.min(), levels.max(), answers.size, MealPatterns.mostCommon(answers))
            },
            meals = entries.size,
            hungerMeals = hungry.size,
            notedMeals = entries.count { !it.feeling?.note.isNullOrBlank() },
            fullness = entries.mapNotNull { it.feeling?.fullness }.groupingBy { it }.eachCount(),
            moods = entries.flatMap { it.feeling?.moods.orEmpty() }.groupingBy { it }.eachCount(),
        )
    }

    private class Entry(val date: LocalDate, val slot: MealSlot, val feeling: MealFeeling?)
}
