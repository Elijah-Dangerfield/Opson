package com.opson.features.trends.impl

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.PresetId
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Patterns over the fixtures' last four weeks: meals around 8:10, 12:20, 3:30
 * and 6:40 with a few minutes' drift, dinner an hour later on Fridays and
 * Saturdays, hunger tagged on most days. The facts themselves are
 * `MealPatternsTest`; this checks what reaches the page in each mode.
 */
class PatternsStateBuilderTest {

    private fun build(preset: PresetId = PresetId.Full, settings: Settings = Settings()) =
        PatternsStateBuilder.build(TrendsFixtures.patternsInputs(settings), TrendsFixtures.policy(preset))

    @Test
    fun timing_aRowPerMeal_dotsOnTheTrack_withTheUsualWindow() {
        val timing = assertNotNull(build().timing)
        assertEquals(MealSlot.meals, timing.rows.map { it.slot })
        timing.rows.forEach { row ->
            assertTrue(row.dots.isNotEmpty(), "${row.slot} has meals")
            assertTrue(row.dots.all { it in 0f..1f })
            val usual = assertNotNull(row.usual)
            assertTrue(usual.start <= usual.endInclusive)
        }
        assertTrue(timing.rows.first().dots.max() < timing.rows.last().dots.min(), "breakfast sits left of dinner")
    }

    @Test
    fun timing_plainFacts_usuallyLaterOnAndLongestGap() {
        val facts = assertNotNull(build().timing).facts
        assertEquals(MealSlot.Breakfast, assertIs<TimingFact.Usually>(facts[0]).slot)
        val later = assertIs<TimingFact.LaterOn>(facts[1])
        assertEquals(MealSlot.Dinner, later.slot)
        assertEquals(listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), later.days)
        assertIs<TimingFact.LongestGap>(facts[2])
    }

    @Test
    fun full_howBigEachMealIs_energyAndShare_noHowOften() {
        val state = build()
        val sizes = assertNotNull(state.sizes)
        assertEquals(MealSlot.meals, sizes.map { it.slot })
        sizes.forEach {
            assertTrue(assertIs<Display.Shown>(it.energy).text.endsWith("kcal"))
            assertTrue(assertIs<Display.Shown>(it.share).text.endsWith("%"))
        }
        assertEquals(1.0, sizes.sumOf { it.fraction.toDouble() }, 0.001)
        assertNull(state.often)
    }

    @Test
    fun blind_noSizesAtAll_howOftenInstead() {
        val state = build(PresetId.Blind)
        assertNull(state.sizes, "even percentages reveal amounts")
        val often = assertNotNull(state.often)
        assertEquals(MealSlot.meals, often.map { it.slot })
        assertTrue(often.all { it.of == 28 && it.days in 1..28 })
        assertNotNull(state.timing, "timing is not an amount")
    }

    @Test
    fun blind_whenMealsHappen_neverTheGapsBetweenThem() {
        val facts = assertNotNull(build(PresetId.Blind).timing).facts
        assertTrue(facts.all { it is TimingFact.Usually }, "gaps can feed restriction: $facts")
        // Other gentle modes keep them: the gap is a time, and only Blind opts out.
        val rhythm = assertNotNull(build(PresetId.Rhythm).timing).facts
        assertTrue(rhythm.any { it is TimingFact.LongestGap })
    }

    @Test
    fun delayed_beforeTheReveal_leavesTodayOutOfTheSizes() {
        val inputs = TrendsFixtures.patternsInputs()
        val beforeToday = TrendsFixtures.patternsInputs(days = inputs.days.filter { it.date < TrendsFixtures.today })
        val noon = PatternsStateBuilder.build(inputs, TrendsFixtures.policy(PresetId.Delayed))
        assertEquals(PatternsStateBuilder.build(beforeToday, TrendsFixtures.policy(PresetId.Delayed)).sizes, noon.sizes)

        val evening = PatternsStateBuilder.build(inputs, TrendsFixtures.policy(PresetId.Delayed, clock = TrendsFixtures.evening))
        assertEquals(build().sizes, evening.sizes, "after the reveal today counts, as in Full")
        assertNotEquals(noon.sizes, evening.sizes)
    }

    @Test
    fun fuel_softCalories_getHowOftenToo() {
        assertNull(build(PresetId.Fuel).sizes)
    }

    @Test
    fun hunger_aBarPerWeekday_mondayFirst_whenTagsAreOn() {
        val hunger = assertNotNull(build().hunger)
        assertEquals(DayOfWeek.entries, hunger.days.map { it.day })
        assertTrue(hunger.days.any { it.level != null })
        assertTrue(hunger.days.mapNotNull { it.level }.all { it in 0f..1f })
    }

    @Test
    fun hunger_sitsUnderNewFoodsInBlind_andAboveInFull() {
        assertEquals(false, build().hungerLast)
        val blind = build(PresetId.Blind)
        assertEquals(true, blind.hungerLast)
        assertNotNull(blind.hunger, "hunger carries no amounts, so Blind keeps it")
    }

    @Test
    fun hunger_hiddenWithTagsOff() {
        assertNull(build(settings = Settings(askTags = false)).hunger)
    }

    @Test
    fun newFoods_theFoodsNotSeenInTheLookback() {
        assertEquals(listOf("Chicken kabsa", "Rice pudding", "Poutine"), build().newFoods)
    }

    @Test
    fun nothingLogged_noTiming_noSizes_noNewFoods() {
        val state = PatternsStateBuilder.build(TrendsFixtures.patternsInputs(days = emptyList()), TrendsFixtures.policy())
        assertNull(state.timing)
        assertNull(state.sizes)
        assertEquals(emptyList(), state.newFoods)
        assertEquals(false, state.loading)
    }
}
