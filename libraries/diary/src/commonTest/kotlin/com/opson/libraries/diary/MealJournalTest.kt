package com.opson.libraries.diary

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** What Trends charts from the meal notes: hunger levels and counts, never the note text. */
class MealJournalTest {

    private val start = LocalDate(2026, 10, 19)
    private val end = start.plus(6, DateTimeUnit.DAY)

    private fun item(date: LocalDate, slot: MealSlot) = LogItem(
        id = "$date-$slot",
        day = date,
        slot = slot,
        loggedAt = Instant.fromEpochSeconds(0),
        name = "Toast",
        precision = Precision.Estimate,
        method = LogMethod.Search,
    )

    /** Every slot in [feelings] is logged, so its feeling counts. */
    private fun day(date: LocalDate, feelings: Map<MealSlot, MealFeeling>, untracked: Boolean = false) = Day(
        date = date,
        itemsBySlot = MealSlot.entries.associateWith { slot -> if (slot in feelings) listOf(item(date, slot)) else emptyList() },
        feelings = feelings,
        untracked = untracked,
    )

    private fun facts(days: List<Day>) = MealJournal.of(days, start, end)

    @Test
    fun levelsRunFromStuffedAtZeroToStarvingAtTheTop() {
        assertEquals(listOf(4, 3, 2, 1, 0), Hunger.entries.map { it.level })
        assertEquals(MealPatterns.HUNGER_STEPS, Hunger.Starving.level)
    }

    @Test
    fun eachDayKeepsItsLevelsAndEachMealItsAverageAndSpread() {
        val facts = facts(
            listOf(
                day(start, mapOf(MealSlot.Breakfast to MealFeeling(hunger = Hunger.Neutral), MealSlot.Dinner to MealFeeling(hunger = Hunger.Starving))),
                day(start.plus(1, DateTimeUnit.DAY), mapOf(MealSlot.Dinner to MealFeeling(hunger = Hunger.Hungry))),
                day(start.plus(2, DateTimeUnit.DAY), mapOf(MealSlot.Dinner to MealFeeling(hunger = Hunger.Starving))),
            ),
        )

        assertEquals(mapOf(start to listOf(2, 4), start.plus(1, DateTimeUnit.DAY) to listOf(3), start.plus(2, DateTimeUnit.DAY) to listOf(4)), facts.hungerByDay)
        val dinner = facts.hungerBySlot.getValue(MealSlot.Dinner)
        assertEquals(SlotHunger(average = 11.0 / 3, lowest = 3, highest = 4, answers = 3, usual = Hunger.Starving), dinner)
        assertEquals(SlotHunger(2.0, 2, 2, 1, Hunger.Neutral), facts.hungerBySlot.getValue(MealSlot.Breakfast))
        assertEquals(4, facts.hungerMeals)
        assertEquals(4, facts.meals)
    }

    @Test
    fun aMealsUsualAnswerIsTheOneGivenMostWithTiesGoingHungrier() {
        val facts = facts(
            listOf(
                day(start, mapOf(MealSlot.Lunch to MealFeeling(hunger = Hunger.Full))),
                day(start.plus(1, DateTimeUnit.DAY), mapOf(MealSlot.Lunch to MealFeeling(hunger = Hunger.Hungry))),
            ),
        )

        assertEquals(Hunger.Hungry, facts.hungerBySlot.getValue(MealSlot.Lunch).usual)
    }

    @Test
    fun anEmptyMealsLeftoverFeelingAndAnUntrackedDayDoNotCount() {
        val leftover = Day(date = start, feelings = mapOf(MealSlot.Snack to MealFeeling(hunger = Hunger.Starving, note = "gone")))
        val holiday = day(start.plus(1, DateTimeUnit.DAY), mapOf(MealSlot.Dinner to MealFeeling(hunger = Hunger.Stuffed)), untracked = true)
        val outside = day(end.plus(1, DateTimeUnit.DAY), mapOf(MealSlot.Dinner to MealFeeling(hunger = Hunger.Full)))

        val facts = facts(listOf(leftover, holiday, outside))

        assertTrue(facts.isEmpty)
        assertEquals(0, facts.meals)
        assertEquals(emptyMap(), facts.hungerByDay)
    }

    @Test
    fun ateNoDetailsIsADoneMealSoItsHungerCounts() {
        val date = start
        val day = Day(date = date, ateNoDetails = setOf(MealSlot.Lunch), feelings = mapOf(MealSlot.Lunch to MealFeeling(hunger = Hunger.Hungry)))

        assertEquals(mapOf(date to listOf(3)), facts(listOf(day)).hungerByDay)
    }

    @Test
    fun notesFullnessAndMoodsAreCountedPerMeal() {
        val facts = facts(
            listOf(
                day(
                    start,
                    mapOf(
                        MealSlot.Breakfast to MealFeeling(note = "On the train", fullness = Fullness.Satisfied, moods = setOf(Mood.Rushed)),
                        MealSlot.Lunch to MealFeeling(note = "  ", fullness = Fullness.Satisfied, moods = setOf(Mood.Calm, Mood.Social)),
                        MealSlot.Dinner to MealFeeling(note = "With Sam", moods = setOf(Mood.Calm)),
                        MealSlot.Snack to MealFeeling(),
                    ),
                ),
            ),
        )

        assertEquals(4, facts.meals)
        assertEquals(2, facts.notedMeals)
        assertEquals(0, facts.hungerMeals)
        assertEquals(mapOf(Fullness.Satisfied to 2), facts.fullness)
        assertEquals(mapOf(Mood.Rushed to 1, Mood.Calm to 2, Mood.Social to 1), facts.moods)
        assertTrue(!facts.isEmpty)
    }
}
