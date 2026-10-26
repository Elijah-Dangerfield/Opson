package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `nv` and `soften` from the prototype: hidden means nothing, a missing value
 * means nothing, and every soften word at both sides of its threshold, plus
 * the bar fractions that come with them.
 *
 * Not covered here: shown strings ([FormatTest]) and delayed reveal
 * ([VisibilityPolicyRevealTest]).
 */
class VisibilityPolicyResolveTest {

    private val soft = policy(
        PresetId.Blind,
        overrides = Metric.entries.associateWith { Visibility.Soften },
    )

    private fun softText(metric: Metric, value: Double, of: Double? = 100.0): String? =
        soft.text(metric, value, FormatContext(of = of))

    @Test
    fun hidden_isHiddenWhateverTheValue() {
        val blind = policy(PresetId.Blind)
        (Metric.entries - Metric.Water).forEach { metric ->
            Surface.entries.forEach { surface ->
                assertEquals(Display.Hidden, blind.resolve(metric, 1245.0, surface, FormatContext(of = 2100.0)), "$metric $surface")
            }
        }
    }

    @Test
    fun missingValue_isHiddenEvenWhenShown() {
        assertEquals(Display.Hidden, showAll().resolve(Metric.Calories, null, Surface.Today))
        assertEquals(Display.Hidden, soft.resolve(Metric.Water, null, Surface.Today))
    }

    @Test
    fun nonFiniteValue_isHidden() {
        assertEquals(Display.Hidden, showAll().resolve(Metric.Calories, Double.NaN, Surface.Today))
        assertEquals(Display.Hidden, showAll().resolve(Metric.Protein, Double.POSITIVE_INFINITY, Surface.Today))
    }

    @Test
    fun calories_softenThresholds() {
        assertEquals("Early in the day", softText(Metric.Calories, 0.0))
        assertEquals("Early in the day", softText(Metric.Calories, 34.99))
        assertEquals("Partway through", softText(Metric.Calories, 35.0))
        assertEquals("Partway through", softText(Metric.Calories, 69.99))
        assertEquals("Most of the way", softText(Metric.Calories, 70.0))
        assertEquals("Most of the way", softText(Metric.Calories, 91.99))
        assertEquals("In your range", softText(Metric.Calories, 92.0))
        assertEquals("In your range", softText(Metric.Calories, 110.0))
        assertEquals("A bigger day", softText(Metric.Calories, 110.01))
    }

    @Test
    fun macros_softenThresholds() {
        listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber).forEach { metric ->
            assertEquals("Building", softText(metric, 39.99), metric.name)
            assertEquals("Getting there", softText(metric, 40.0), metric.name)
            assertEquals("Getting there", softText(metric, 84.99), metric.name)
            assertEquals("Plenty today", softText(metric, 85.0), metric.name)
            assertEquals("Plenty today", softText(metric, 300.0), metric.name)
        }
    }

    @Test
    fun micros_softenThresholds() {
        assertEquals("Some", softText(Metric.Micros, 49.99))
        assertEquals("Most", softText(Metric.Micros, 50.0))
        assertEquals("Most", softText(Metric.Micros, 89.99))
        assertEquals("Plenty", softText(Metric.Micros, 90.0))
    }

    @Test
    fun softenWithoutAGoal_isHidden() {
        listOf(Metric.Calories, Metric.Protein, Metric.Micros).forEach { metric ->
            assertEquals(Display.Hidden, soft.resolve(metric, 50.0, Surface.Today, FormatContext(of = null)), metric.name)
            assertEquals(Display.Hidden, soft.resolve(metric, 50.0, Surface.Today, FormatContext(of = 0.0)), metric.name)
        }
    }

    @Test
    fun weight_softensToTrendOnlyWithNoBar() {
        assertEquals(Display.Soft("Trend only", null), soft.resolve(Metric.Weight, 72.6, Surface.Weight))
        assertEquals(Display.Soft("Trend only", null), policy(PresetId.Nutrient).resolve(Metric.Weight, 72.6, Surface.Today))
    }

    @Test
    fun water_softensToABarWithNoText() {
        val blind = policy(PresetId.Blind)
        assertEquals(Display.Soft(null, 0.375f), blind.resolve(Metric.Water, 750.0, Surface.Today))
        assertEquals(Display.Soft(null, 0.75f), blind.resolve(Metric.Water, 750.0, Surface.Today, FormatContext(of = 1000.0)))
        assertEquals(Display.Soft(null, 1.5f), blind.resolve(Metric.Water, 3000.0, Surface.Today))
    }

    @Test
    fun remainingAndEnergyOut_haveNoSoftWordSoTheyHide() {
        val context = FormatContext(of = 2100.0, eaten = 2000.0, min = 1900.0, max = 2300.0)
        assertEquals(Display.Hidden, soft.resolve(Metric.Remaining, 100.0, Surface.Today, context))
        assertEquals(Display.Hidden, soft.resolve(Metric.EnergyOut, 1990.0, Surface.Today, context))
    }

    @Test
    fun soft_carriesTheFillFraction() {
        assertEquals(Display.Soft("Partway through", 0.5f), soft.resolve(Metric.Calories, 1050.0, Surface.Today, FormatContext(of = 2100.0)))
        assertEquals(Display.Soft("Building", 0.25f), soft.resolve(Metric.Protein, 30.0, Surface.Today, FormatContext(of = 120.0)))
    }

    @Test
    fun fuel_softensCaloriesButShowsProtein() {
        val fuel = policy(PresetId.Fuel)
        assertEquals("In your range", fuel.text(Metric.Calories, 2100.0, FormatContext(of = 2100.0)))
        assertEquals("47 g of 120 g", fuel.text(Metric.Protein, 47.0, FormatContext(of = 120.0)))
        assertNull(fuel.text(Metric.Carbs, 47.0, FormatContext(of = 250.0)))
    }

    @Test
    fun shown_carriesTheFillFractionWhenThereIsAGoal() {
        val full = showAll()
        assertEquals(0.5f, (full.resolve(Metric.Calories, 1050.0, Surface.Today, FormatContext(of = 2100.0)) as Display.Shown).fraction)
        assertEquals(0.25f, (full.resolve(Metric.Water, 500.0, Surface.Today) as Display.Shown).fraction)
        assertNull((full.resolve(Metric.Protein, 47.0, Surface.Today) as Display.Shown).fraction)
        assertNull((full.resolve(Metric.Weight, 72.6, Surface.Today) as Display.Shown).fraction)
        assertNull((full.resolve(Metric.EnergyOut, 1990.0, Surface.Today, FormatContext(of = 2100.0)) as Display.Shown).fraction)
    }
}
