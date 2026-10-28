package com.opson.features.trends.impl

import com.opson.libraries.nutrition.RevealOn
import com.opson.features.trends.NutrientsRange
import com.opson.libraries.nutrition.Display
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.MicroCoverage
import com.opson.libraries.nutrition.MicroGroup
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.Visibility
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Nutrients detail for Day (surface Nutrients) and Week average (surface Week)
 * in each branch of the plan's table. Today in the fixtures is Sunday Oct 25 at
 * noon: oats, banana, chicken adobo and rice, 1,130 kcal.
 */
class NutrientsStateBuilderTest {

    private val microsOn = mapOf(Metric.Micros to Visibility.Show)

    private fun build(
        range: NutrientsRange = NutrientsRange.Day,
        preset: PresetId = PresetId.Full,
        overrides: Map<Metric, Visibility> = emptyMap(),
        inputs: NutrientsInputs = TrendsFixtures.nutrientsInputs(range),
        policyAt: LocalTime = LocalTime(12, 0),
        revealOn: RevealOn = RevealOn.DayClose,
    ) = NutrientsStateBuilder.build(
        inputs,
        TrendsFixtures.policy(preset, overrides, TrendsFixtures.fixedClock(LocalDateTime(TrendsFixtures.today, policyAt)), revealOn),
    )

    @Test
    fun fullDay_energyAgainstItsRange_macrosSpellTheGoal_microsShow() {
        val state = build()
        val energy = assertNotNull(state.energy)
        assertEquals("1,130 kcal", assertIs<Display.Shown>(energy.eaten).text)
        assertEquals("1,900 to 2,300", assertIs<Display.Shown>(energy.range).text)
        assertEquals((1900 / (2300 * 1.15)).toFloat(), energy.rangeStart)
        assertEquals((2300 / (2300 * 1.15)).toFloat(), energy.rangeEnd)
        val rows = assertNotNull(state.macros)
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), rows.map { it.metric })
        assertEquals("53 of 120 g", assertIs<Display.Shown>(rows[0].display).text)
        assertEquals(false, state.microsOff)
        assertNotNull(state.micros)
        assertEquals(false, state.nothing)
    }

    @Test
    fun fullWithMicrosHidden_offCardLinksToModes() {
        val state = build(overrides = mapOf(Metric.Micros to Visibility.Hide))
        assertEquals(true, state.microsOff)
        assertNull(state.micros)
        assertEquals(false, state.nothing)
    }

    @Test
    fun day_backLabelAndSubtitle() {
        val state = build()
        assertEquals(BackLabel.Day("Today"), state.backLabel)
        assertEquals(NutrientsSubtitle.Day("Sunday, Oct 25"), state.subtitle)
    }

    @Test
    fun weekAverage_fromTrends_backToTrendsOverTheWindow() {
        val state = build(NutrientsRange.WeekAverage)
        assertEquals(BackLabel.Trends, state.backLabel)
        assertEquals(NutrientsSubtitle.WeekAverage("Oct 19 to 25"), state.subtitle)
        // The same five days as the Trends average: nothing on Thursday, Sunday still in progress.
        assertEquals("1,818 kcal", assertIs<Display.Shown>(state.energy?.eaten).text)
    }

    @Test
    fun microsShown_bothGroups_noScores() {
        val state = build(NutrientsRange.WeekAverage, overrides = microsOn)
        assertEquals(false, state.microsOff)
        val groups = assertNotNull(state.micros)
        assertEquals(listOf(MicroGroup.Vitamins, MicroGroup.Minerals), groups.map { it.group })
        assertEquals(listOf(12, 10), groups.map { it.rows.size })
        val sodium = groups[1].rows.single { it.micro == Micronutrient.Sodium }
        assertEquals("2,300 mg limit", sodium.reference)
        assertTrue(sodium.isLimit)
        assertTrue(assertIs<Display.Shown>(sodium.display).text.endsWith("%"))
    }

    @Test
    fun microsSoftened_areWords() {
        val state = build(overrides = mapOf(Metric.Micros to Visibility.Soften))
        val row = assertNotNull(state.micros).flatMap { it.rows }.single { it.micro == Micronutrient.Iron }
        assertIs<Display.Soft>(row.display)
    }

    /** Today: oats, adobo and rice list iron, the banana does not. */
    @Test
    fun micros_sayHowManyFoodsListThem() {
        val rows = assertNotNull(build().micros).flatMap { it.rows }.associateBy { it.micro }
        val iron = rows.getValue(Micronutrient.Iron)
        assertEquals(MicroCoverage(listing = 3, of = 4), iron.coverage)
        assertFalse(iron.notListed)
        assertTrue(iron.opensFoods)
    }

    /** Nothing today lists vitamin D: no percent and no 0%, "Not listed" instead, and nothing to open. */
    @Test
    fun aMicroNoFoodLists_isNotListed_notZero() {
        val vitaminD = assertNotNull(build().micros).flatMap { it.rows }.single { it.micro == Micronutrient.VitaminD }
        assertTrue(vitaminD.notListed)
        assertEquals(Display.Hidden, vitaminD.display)
        assertFalse(vitaminD.opensFoods)
    }

    @Test
    fun references_followTheirAgeAndSex_andTheirOwnNumber() {
        val settings = Settings(birthYear = 1990, bodySex = BodySex.Female).withMicroTarget(Micronutrient.Zinc, 10.0)
        val rows = assertNotNull(build(inputs = TrendsFixtures.nutrientsInputs(settings = settings)).micros).flatMap { it.rows }.associateBy { it.micro }
        assertEquals("18 mg", rows.getValue(Micronutrient.Iron).reference)
        assertEquals("10 mg", rows.getValue(Micronutrient.Zinc).reference)
        // Iron 5.3 mg of 18 is 29%; zinc 3.5 mg of their own 10 is 35%.
        assertEquals("29%", assertIs<Display.Shown>(rows.getValue(Micronutrient.Iron).display).text)
        assertEquals("35%", assertIs<Display.Shown>(rows.getValue(Micronutrient.Zinc).display).text)
    }

    @Test
    fun macros_withSomethingLogged_openTheirFoods() {
        assertTrue(assertNotNull(build().macros).all { it.opensFoods })
    }

    @Test
    fun delayed_weekAverage_isRevealed() {
        val state = build(NutrientsRange.WeekAverage, overrides = Presets.laterLevels)
        assertIs<Display.Shown>(assertNotNull(state.energy).eaten)
        assertEquals(true, state.microsOff)
    }

    @Test
    fun delayed_day_staysShutUntilNinePm() {
        val noon = build(overrides = Presets.laterLevels)
        assertNull(noon.energy)
        assertNull(noon.macros)
        val evening = build(overrides = Presets.laterLevels, policyAt = LocalTime(21, 30))
        assertIs<Display.Shown>(assertNotNull(evening.energy).eaten)
    }

    @Test
    fun softCalories_energyIsASoftWordWithNoRange_macrosAreWords() {
        val state = build(preset = PresetId.MacrosOnly, overrides = mapOf(Metric.Calories to Visibility.Soften))
        val energy = assertNotNull(state.energy)
        assertNotNull(assertIs<Display.Soft>(energy.eaten).text)
        assertEquals(Display.Hidden, energy.range, "a range has no soft word")
        val rows = assertNotNull(state.macros)
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), rows.map { it.metric })
        assertIs<Display.Soft>(rows[0].display)
    }

    @Test
    fun weekly_weekAverage_waitsForTheWeeklyCheck() {
        assertNull(build(NutrientsRange.WeekAverage, overrides = Presets.laterLevels, revealOn = RevealOn.Week).energy)
        val evening = build(NutrientsRange.WeekAverage, overrides = Presets.laterLevels, policyAt = LocalTime(21, 30), revealOn = RevealOn.Week)
        assertIs<Display.Shown>(assertNotNull(evening.energy).eaten)
    }

    @Test
    fun blind_nothing() {
        val state = build(preset = PresetId.NoNumbers)
        assertNull(state.energy)
        assertNull(state.macros)
        assertEquals(false, state.microsOff)
        assertNull(state.micros)
        assertEquals(true, state.nothing)
    }

    @Test
    fun blind_withMicrosOn_showsMicrosNotNothing() {
        val state = build(preset = PresetId.NoNumbers, overrides = microsOn)
        assertEquals(false, state.nothing)
        assertNotNull(state.micros)
        assertNull(state.macros)
    }

    @Test
    fun emptyWeek_noRows_butNotNothing() {
        val inputs = TrendsFixtures.nutrientsInputs(NutrientsRange.WeekAverage, days = emptyList())
        val state = build(NutrientsRange.WeekAverage, inputs = inputs)
        assertNull(state.energy)
        assertNull(state.micros)
        assertEquals(false, state.nothing)
        assertEquals(false, state.microsOff)
    }

    @Test
    fun emptyDay_isTheEmptyCard_notAPageOfZeros() {
        val state = build(inputs = TrendsFixtures.nutrientsInputs(days = emptyList()))
        assertTrue(state.empty)
        assertNull(state.energy)
        assertNull(state.macros)
        assertNull(state.micros)
        assertFalse(state.microsOff)
    }

    @Test
    fun emptyWeek_isTheEmptyCard_andNoNumbersStillSaysNothingShowsHere() {
        assertTrue(build(NutrientsRange.WeekAverage, inputs = TrendsFixtures.nutrientsInputs(NutrientsRange.WeekAverage, days = emptyList())).empty)
        val blind = build(preset = PresetId.NoNumbers, inputs = TrendsFixtures.nutrientsInputs(days = emptyList()))
        assertFalse(blind.empty)
        assertTrue(blind.nothing)
    }
}
