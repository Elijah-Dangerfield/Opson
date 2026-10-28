package com.opson.features.trends.impl

import com.opson.features.trends.NutrientsRange
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.MicroCoverage
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.Nutrient
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.Visibility
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which foods gave a nutrient, in each mode. Today in the fixtures is Sunday Oct 25: oat
 * porridge (iron 3 mg), a banana (no iron listed), chicken adobo (2 mg) and rice (0.3 mg).
 */
class NutrientFoodsStateBuilderTest {

    private val iron = Nutrient.Micro(Micronutrient.Iron)
    private val protein = Nutrient.Macro(Metric.Protein)

    private fun build(
        nutrient: Nutrient = iron,
        preset: PresetId = PresetId.Full,
        overrides: Map<Metric, Visibility> = emptyMap(),
        range: NutrientsRange = NutrientsRange.Day,
        at: LocalTime = LocalTime(12, 0),
    ) = NutrientFoodsStateBuilder.build(
        TrendsFixtures.nutrientFoodsInputs(nutrient, range),
        TrendsFixtures.policy(preset, overrides, TrendsFixtures.fixedClock(LocalDateTime(TrendsFixtures.today, at))),
    )

    @Test
    fun allNumbers_ranksTheFoods_withAmountAndShare() {
        val state = build()
        assertEquals(listOf("Oat porridge with milk", "Chicken adobo", "Rice"), state.rows.map { it.name })
        assertEquals(listOf("3 mg", "2 mg", "0.3 mg"), state.rows.map { assertIs<Display.Shown>(it.amount).text })
        assertEquals(listOf("57%", "38%", "6%"), state.rows.map { assertIs<Display.Shown>(it.share).text })
        assertFalse(state.rankedOnly)
        assertEquals("Today", state.subtitle)
    }

    /** The banana says nothing about iron, so the amounts are a floor, and the sheet says so. */
    @Test
    fun someFoodsNotListingIt_isSaid() {
        assertEquals(MicroCoverage(listing = 3, of = 4), build().coverage)
    }

    @Test
    fun macros_haveNoCoverageLine() {
        assertNull(build(protein).coverage)
    }

    @Test
    fun nothingListingIt_isNone_notZero() {
        val state = build(Nutrient.Micro(Micronutrient.VitaminD))
        assertTrue(state.none)
        assertEquals(emptyList(), state.rows)
    }

    @Test
    fun macrosWithGrams_showsGramsAndMicroAmounts() {
        assertEquals("38 g", assertIs<Display.Shown>(build(protein, PresetId.MacrosWithGrams).rows.first().amount).text)
        assertEquals("3 mg", assertIs<Display.Shown>(build(iron, PresetId.MacrosWithGrams).rows.first().amount).text)
    }

    /** Macros only: the order, by how much each gave, and nothing else. */
    @Test
    fun macrosOnly_ranksWithoutAmounts() {
        val state = build(protein, PresetId.MacrosOnly)
        assertEquals("Chicken adobo", state.rows.first().name)
        assertTrue(state.rows.all { it.amount == Display.Hidden && it.share == Display.Hidden })
        assertTrue(state.rankedOnly)
        assertFalse(state.hidden)
    }

    /** Macros only hides micros, and No numbers hides everything: the sheet keeps even the food names to itself. */
    @Test
    fun aHiddenNutrient_showsNothing() {
        listOf(build(iron, PresetId.MacrosOnly), build(protein, PresetId.NoNumbers), build(iron, PresetId.NoNumbers)).forEach { state ->
            assertTrue(state.hidden)
            assertEquals(emptyList(), state.rows)
        }
    }

    @Test
    fun later_opensAtTheReveal() {
        assertTrue(build(overrides = Presets.laterLevels + (Metric.Micros to Visibility.Delay)).hidden)
        val evening = build(overrides = Presets.laterLevels + (Metric.Micros to Visibility.Delay), at = LocalTime(21, 30))
        assertIs<Display.Shown>(evening.rows.first().amount)
    }

    /** The week's amounts are per day over the days the average counts, so they read against the page. */
    @Test
    fun week_isTheTrendsWeek_perDay() {
        val state = build(Nutrient.Micro(Micronutrient.Sodium), range = NutrientsRange.WeekAverage)
        assertEquals("Oct 19 to 25", state.subtitle)
        assertEquals("Chicken adobo", state.rows.first().name)
        // Adobo on Mon, Tue and Fri at 1,100 mg, over the five days counted.
        assertEquals("660 mg", assertIs<Display.Shown>(state.rows.first().amount).text)
    }

    @Test
    fun manyFoods_listTheTopEight_andCountTheRest() {
        val state = build(Nutrient.Macro(Metric.Carbs), range = NutrientsRange.WeekAverage)
        assertEquals(NutrientFoodsStateBuilder.MAX_ROWS, state.rows.size)
        assertTrue(state.more > 0)
    }
}
