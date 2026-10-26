package com.opson.features.trends.impl

import com.opson.features.trends.NutrientsRange
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.MicroGroup
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
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
    ) = NutrientsStateBuilder.build(
        inputs,
        TrendsFixtures.policy(preset, overrides, TrendsFixtures.fixedClock(LocalDateTime(TrendsFixtures.today, policyAt))),
    )

    @Test
    fun fullDay_energyAgainstItsRange_macrosSpellTheGoal_microsOffLinksToModes() {
        val state = build()
        val energy = assertNotNull(state.energy)
        assertEquals("1,130 kcal", assertIs<Display.Shown>(energy.eaten).text)
        assertEquals("1,900 to 2,300", assertIs<Display.Shown>(energy.range).text)
        assertEquals((1900 / (2300 * 1.15)).toFloat(), energy.rangeStart)
        assertEquals((2300 / (2300 * 1.15)).toFloat(), energy.rangeEnd)
        val rows = assertNotNull(state.macros)
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), rows.map { it.metric })
        assertEquals("53 g of 120 g", assertIs<Display.Shown>(rows[0].display).text)
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
        // The same five days as the Trends average: Thursday untracked, Sunday still in progress.
        assertEquals("1,818 kcal", assertIs<Display.Shown>(state.energy?.eaten).text)
    }

    @Test
    fun microsShown_bothGroups_noScores() {
        val state = build(NutrientsRange.WeekAverage, overrides = microsOn)
        assertEquals(false, state.microsOff)
        val groups = assertNotNull(state.micros)
        assertEquals(listOf(MicroGroup.Vitamins, MicroGroup.Minerals), groups.map { it.group })
        assertEquals(listOf(7, 6), groups.map { it.rows.size })
        val sodium = groups[1].rows.single { it.micro == Micronutrient.Sodium }
        assertEquals("2,300 mg limit", sodium.reference)
        assertTrue(sodium.isLimit)
        assertTrue(assertIs<Display.Shown>(sodium.display).text.endsWith("%"))
    }

    @Test
    fun microsSoftened_areWords() {
        val state = build(overrides = mapOf(Metric.Micros to Visibility.Soften))
        val row = assertNotNull(state.micros).first().rows.first()
        assertIs<Display.Soft>(row.display)
    }

    @Test
    fun delayed_weekAverage_isRevealed() {
        val state = build(NutrientsRange.WeekAverage, PresetId.Delayed)
        assertIs<Display.Shown>(assertNotNull(state.energy).eaten)
        assertEquals(true, state.microsOff)
    }

    @Test
    fun delayed_day_staysShutUntilNinePm() {
        val noon = build(preset = PresetId.Delayed)
        assertNull(noon.energy)
        assertNull(noon.macros)
        val evening = build(preset = PresetId.Delayed, policyAt = LocalTime(21, 30))
        assertIs<Display.Shown>(assertNotNull(evening.energy).eaten)
    }

    @Test
    fun fuel_energyIsASoftWordWithNoRange_proteinAndFiberShow() {
        val state = build(preset = PresetId.Fuel)
        val energy = assertNotNull(state.energy)
        assertNotNull(assertIs<Display.Soft>(energy.eaten).text)
        assertEquals(Display.Hidden, energy.range, "a range has no soft word")
        val rows = assertNotNull(state.macros)
        assertEquals(listOf(Metric.Protein, Metric.Fiber), rows.map { it.metric })
        assertIs<Display.Shown>(rows[0].display)
    }

    @Test
    fun coast_weekAverage_waitsForTheWeeklyCheck() {
        assertNull(build(NutrientsRange.WeekAverage, PresetId.Coast).energy)
        assertIs<Display.Shown>(assertNotNull(build(NutrientsRange.WeekAverage, PresetId.Coast, policyAt = LocalTime(21, 30)).energy).eaten)
    }

    @Test
    fun blind_nothing() {
        val state = build(preset = PresetId.Blind)
        assertNull(state.energy)
        assertNull(state.macros)
        assertEquals(false, state.microsOff)
        assertNull(state.micros)
        assertEquals(true, state.nothing)
    }

    @Test
    fun blind_withMicrosOn_showsMicrosNotNothing() {
        val state = build(preset = PresetId.Blind, overrides = microsOn)
        assertEquals(false, state.nothing)
        assertNotNull(state.micros)
        assertNull(state.macros)
    }

    @Test
    fun emptyWeek_noRows_butNotNothing() {
        val inputs = TrendsFixtures.nutrientsInputs(NutrientsRange.WeekAverage, days = emptyList())
        val state = build(NutrientsRange.WeekAverage, inputs = inputs)
        assertNull(state.energy)
        assertEquals(false, state.nothing)
        assertEquals(true, state.microsOff)
    }

    @Test
    fun emptyDay_isARealZero() {
        val state = build(inputs = TrendsFixtures.nutrientsInputs(days = emptyList()))
        assertEquals("0 g of 120 g", assertIs<Display.Shown>(assertNotNull(state.macros).single { it.metric == Metric.Protein }.display).text)
    }
}
