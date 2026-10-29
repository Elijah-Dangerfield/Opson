package com.opson.features.trends.impl

import com.opson.libraries.nutrition.RevealOn
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.HungerLevel
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.Metric
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.minus
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

    private fun build(preset: PresetId = PresetId.Full, settings: Settings = Settings(), overrides: Map<Metric, Visibility> = emptyMap()) =
        PatternsStateBuilder.build(TrendsFixtures.patternsInputs(settings), TrendsFixtures.policy(preset, overrides))

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
    fun weekly_meanSizesWaitForTheCheckOfTheWeekHoldingToday() {
        // Sunday evening: a Monday week has had its check; a Saturday week checks on Friday.
        val evening = TrendsFixtures.policy(overrides = Presets.laterLevels, clock = TrendsFixtures.evening, revealOn = RevealOn.Week)
        assertNotNull(PatternsStateBuilder.build(TrendsFixtures.patternsInputs(), evening).sizes)
        val saturdayWeek = TrendsFixtures.patternsInputs(Settings(weekStart = DayOfWeek.SATURDAY))
        assertNull(PatternsStateBuilder.build(saturdayWeek, evening).sizes)
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
        val state = build(PresetId.NoNumbers)
        assertNull(state.sizes, "even percentages reveal amounts")
        val often = assertNotNull(state.often)
        assertEquals(MealSlot.meals, often.map { it.slot })
        assertTrue(often.all { it.of == 28 && it.days in 1..28 })
        assertNotNull(state.timing, "timing is not an amount")
    }

    @Test
    fun noNumbers_whenMealsHappen_neverTheGapsBetweenThem() {
        val facts = assertNotNull(build(PresetId.NoNumbers).timing).facts
        assertTrue(facts.all { it is TimingFact.Usually }, "gaps can feed restriction: $facts")
        // Other gentle modes keep them: the gap is a time, and only No numbers opts out.
        val later = assertNotNull(build(overrides = Presets.laterLevels).timing).facts
        assertTrue(later.any { it is TimingFact.LongestGap })
    }

    @Test
    fun delayed_beforeTheReveal_leavesTodayOutOfTheSizes() {
        val inputs = TrendsFixtures.patternsInputs()
        val beforeToday = TrendsFixtures.patternsInputs(days = inputs.days.filter { it.date < TrendsFixtures.today })
        val noon = PatternsStateBuilder.build(inputs, TrendsFixtures.policy(overrides = Presets.laterLevels))
        assertEquals(PatternsStateBuilder.build(beforeToday, TrendsFixtures.policy(overrides = Presets.laterLevels)).sizes, noon.sizes)

        val evening = PatternsStateBuilder.build(inputs, TrendsFixtures.policy(overrides = Presets.laterLevels, clock = TrendsFixtures.evening))
        assertEquals(build().sizes, evening.sizes, "after the reveal today counts, as in Full")
        assertNotEquals(noon.sizes, evening.sizes)
    }

    @Test
    fun nothingLogged_isTheEmptyCard_notRowsOfZeros() {
        val state = PatternsStateBuilder.build(TrendsFixtures.patternsInputs(days = emptyList()), TrendsFixtures.policy())
        assertTrue(state.empty)
        assertNull(state.often)
        assertNull(state.timing)
    }

    @Test
    fun fuel_softCalories_getHowOftenToo() {
        assertNull(build(PresetId.MacrosOnly, overrides = mapOf(Metric.Calories to Visibility.Soften)).sizes)
        assertNull(build(PresetId.MacrosWithGrams).sizes, "no calories, no sizes")
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
        val blind = build(PresetId.NoNumbers)
        assertEquals(true, blind.hungerLast)
        assertNotNull(blind.hunger, "hunger carries no amounts, so No numbers keeps it")
    }

    @Test
    fun hunger_showsEvenWhereTheOldTagsSwitchWasOff() {
        assertNotNull(build(settings = Settings(askTags = false)).hunger)
    }

    @Test
    fun hunger_withTheCheckInOn_comesFromTheCheckIns_tagsOrNot() {
        val inputs = TrendsFixtures.patternsInputs(Settings(askTags = false), checkInOn = true)
        val hunger = assertNotNull(PatternsStateBuilder.build(inputs, TrendsFixtures.policy()).hunger)
        assertEquals(HungerSource.CheckIns, hunger.source)
        assertEquals(DayOfWeek.entries, hunger.days.map { it.day })
        assertTrue(hunger.days.mapNotNull { it.level }.all { it in 0f..1f })
        assertEquals(HungerSource.MealTags, assertNotNull(build().hunger).source, "the tags while the check-in is off")
    }

    @Test
    fun hunger_checkInOnButNeverAnswered_fallsBackToTheTags() {
        val unanswered = TrendsFixtures.days().map { it.copy(checkIn = null) }
        val inputs = TrendsFixtures.patternsInputs(days = unanswered, checkInOn = true)
        assertEquals(HungerSource.MealTags, assertNotNull(PatternsStateBuilder.build(inputs, TrendsFixtures.policy()).hunger).source)
    }

    @Test
    fun checkIns_aBarADayForEachAspectAnswered_inEveryMode() {
        PresetId.entries.forEach { preset ->
            val card = assertNotNull(PatternsStateBuilder.build(TrendsFixtures.patternsInputs(checkInOn = true), TrendsFixtures.policy(preset)).checkIns, "$preset")
            assertEquals(CheckInAspect.entries, card.series.map { it.aspect })
            card.series.forEach { series ->
                assertEquals(PatternsStateBuilder.DAYS, series.levels.size, "${series.aspect}: four weeks, today included")
                assertTrue(series.levels.filterNotNull().all { it in 0f..1f })
                series.levels.zip(series.readouts).forEach { (level, readout) ->
                    assertEquals(level == null, readout == null)
                    readout?.let { assertNull(it.readings.single().value, "words, never an amount") }
                }
            }
        }
        assertNull(build().checkIns, "the flag off")
    }

    @Test
    fun checkIns_hungerIsTallerWhenHungrier_andAnAspectNobodyAnsweredIsLeftOut() {
        val today = TrendsFixtures.today
        val days = listOf(
            Day(date = today, checkIn = CheckIn(hunger = HungerLevel.of(2))),
            Day(date = today.minus(1, DateTimeUnit.DAY), checkIn = CheckIn(hunger = HungerLevel.of(9))),
            Day(date = today.minus(2, DateTimeUnit.DAY), checkIn = CheckIn(hunger = HungerLevel.of(5)), untracked = true),
        )
        val from = today.minus(PatternsStateBuilder.DAYS - 1, DateTimeUnit.DAY)

        val card = assertNotNull(PatternsStateBuilder.checkIns(days, from, today))

        assertEquals(listOf(CheckInAspect.Hunger), card.series.map { it.aspect })
        val levels = card.shown.levels
        assertTrue(assertNotNull(levels.last()) > assertNotNull(levels[levels.lastIndex - 1]), "2 is hungrier than 9")
        assertNull(levels[levels.lastIndex - 2], "an untracked day is a gap")
        assertEquals(card, card.picking(CheckInAspect.Sleep), "no sleep answered, nothing to pick")
        assertNull(PatternsStateBuilder.checkIns(emptyList(), from, today))
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
