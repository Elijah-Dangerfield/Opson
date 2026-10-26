package com.opson.libraries.nutrition

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every shown string, ported from `format` in the prototype, with locale-free
 * thousands separators so the JVM and iOS agree.
 *
 * Not covered here: soften words ([VisibilityPolicyResolveTest]).
 */
class FormatTest {

    private val full = showAll()

    private fun calories(value: Double, bare: Boolean = false) = full.text(Metric.Calories, value, FormatContext(bare = bare))

    private fun remaining(value: Double, eaten: Double? = null, min: Double? = null, max: Double? = null) =
        full.text(Metric.Remaining, value, FormatContext(eaten = eaten, min = min, max = max))

    @Test
    fun calories_bare() {
        assertEquals("1,245", calories(1245.0, bare = true))
        assertEquals("999", calories(999.0, bare = true))
        assertEquals("0", calories(0.0, bare = true))
        assertEquals("1,234,567", calories(1_234_567.0, bare = true))
    }

    @Test
    fun calories_withUnit() {
        assertEquals("1,245 kcal", calories(1245.0))
    }

    @Test
    fun calories_roundHalfUpLikeMathRound() {
        assertEquals("1,245", calories(1244.5, bare = true))
        assertEquals("1,244", calories(1244.49, bare = true))
        assertEquals("1,000", calories(999.5, bare = true))
    }

    @Test
    fun energyOut_bareAndWithUnit() {
        assertEquals("1,990 kcal", full.text(Metric.EnergyOut, 1990.0))
        assertEquals("1,990", full.text(Metric.EnergyOut, 1990.0, FormatContext(bare = true)))
    }

    @Test
    fun macros_grams() {
        assertEquals("47 g", full.text(Metric.Protein, 47.0))
        assertEquals("47 g", full.text(Metric.Fat, 47.4))
        assertEquals("1,250 g", full.text(Metric.Carbs, 1250.0))
    }

    @Test
    fun macros_ofGoal() {
        assertEquals("47 g of 120 g", full.text(Metric.Protein, 47.0, FormatContext(of = 120.0)))
        assertEquals("12 g of 30 g", full.text(Metric.Fiber, 12.0, FormatContext(of = 30.0)))
        assertEquals("1,250 g of 2,500 g", full.text(Metric.Carbs, 1250.0, FormatContext(of = 2500.0)))
    }

    @Test
    fun macros_zeroGoalIsNoGoal() {
        assertEquals("47 g", full.text(Metric.Protein, 47.0, FormatContext(of = 0.0)))
    }

    @Test
    fun weight_oneDecimalKg() {
        assertEquals("72.6 kg", full.text(Metric.Weight, 72.6))
        assertEquals("72.6 kg", full.text(Metric.Weight, 72.64))
        assertEquals("72.7 kg", full.text(Metric.Weight, 72.66))
        assertEquals("72.0 kg", full.text(Metric.Weight, 72.0))
        assertEquals("0.5 kg", full.text(Metric.Weight, 0.5))
    }

    @Test
    fun weight_inPounds() {
        assertEquals("160.1 lb", full.text(Metric.Weight, 72.6, FormatContext(weightUnit = WeightUnit.Lb)))
    }

    @Test
    fun weight_bareDropsTheUnit() {
        assertEquals("72.6", full.text(Metric.Weight, 72.6, FormatContext(bare = true)))
        assertEquals("160.1", full.text(Metric.Weight, 72.6, FormatContext(bare = true, weightUnit = WeightUnit.Lb)))
    }

    @Test
    fun micros_percent() {
        assertEquals("62%", full.text(Metric.Micros, 62.0))
        assertEquals("62%", full.text(Metric.Micros, 61.6))
        assertEquals("140%", full.text(Metric.Micros, 140.0))
    }

    @Test
    fun water_mlOfTheDefaultGoal() {
        assertEquals("750 of 2,000 ml", full.text(Metric.Water, 750.0))
        assertEquals("750 of 2,000 ml", full.text(Metric.Water, 750.0, FormatContext(of = 0.0)))
    }

    @Test
    fun water_mlWithThousands() {
        assertEquals("1,500 of 3,000 ml", full.text(Metric.Water, 1500.0, FormatContext(of = 3000.0)))
    }

    @Test
    fun water_flOz() {
        // 750 / 29.57 = 25.4, 2000 / 29.57 = 67.6
        assertEquals("25 of 68 fl oz", full.text(Metric.Water, 750.0, FormatContext(unit = WaterUnit.FlOz)))
    }

    @Test
    fun water_flOzWithThousands() {
        // 30,000 / 29.57 = 1,014.5 and 60,000 / 29.57 = 2,029.1
        assertEquals(
            "1,015 of 2,029 fl oz",
            full.text(Metric.Water, 30_000.0, FormatContext(of = 60_000.0, unit = WaterUnit.FlOz)),
        )
    }

    @Test
    fun remaining_insideTheRangeIsOnTarget() {
        assertEquals("On target", remaining(100.0, eaten = 2000.0, min = 1900.0, max = 2300.0))
        assertEquals("On target", remaining(-150.0, eaten = 2250.0, min = 1900.0, max = 2300.0))
    }

    @Test
    fun remaining_rangeEdgesAreOnTarget() {
        assertEquals("On target", remaining(200.0, eaten = 1900.0, min = 1900.0, max = 2300.0))
        assertEquals("On target", remaining(-200.0, eaten = 2300.0, min = 1900.0, max = 2300.0))
    }

    @Test
    fun remaining_overTheRangeIsAboveYourRange() {
        assertEquals("Above your range today", remaining(-201.0, eaten = 2301.0, min = 1900.0, max = 2300.0))
        assertEquals("Above your range today", remaining(-900.0, eaten = 3000.0, max = 2300.0))
    }

    @Test
    fun remaining_underTheRangeIsNLeft() {
        assertEquals("900 left", remaining(900.0, eaten = 1200.0, min = 1900.0, max = 2300.0))
        assertEquals("1,600 left", remaining(1600.0, eaten = 500.0, min = 1900.0, max = 2300.0))
        assertEquals("1 left", remaining(0.5))
    }

    @Test
    fun remaining_neverSaysZeroLeft() {
        assertEquals("On target", remaining(0.0))
        assertEquals("On target", remaining(0.4))
        assertEquals("On target", remaining(-50.0))
        // A range that sits right on the target, eaten just under it.
        assertEquals("On target", remaining(0.3, eaten = 2099.7, min = 2100.0, max = 2300.0))
    }

    @Test
    fun formatter_spellsEverySoftWord() {
        val words = SoftWord.entries.map { EnglishNutritionFormatter.soft(it) }
        assertEquals(
            listOf(
                "Early in the day", "Partway through", "Most of the way", "In your range", "A bigger day",
                "Building", "Getting there", "Plenty today",
                "Some", "Most", "Plenty",
                "Trend only",
                "Steady",
                "Rising slowly",
                "Falling slowly",
            ),
            words,
        )
    }

    @Test
    fun groupThousands_handlesSignsAndBoundaries() {
        assertEquals("0", groupThousands(0))
        assertEquals("999", groupThousands(999))
        assertEquals("1,000", groupThousands(1000))
        assertEquals("100,000", groupThousands(100_000))
        assertEquals("-1,245", groupThousands(-1245))
    }

    @Test
    fun customFormatter_isUsedForShownAndSoftText() {
        val loud = object : NutritionFormatter by EnglishNutritionFormatter {
            override fun energy(kcal: Double, bare: Boolean) = "E${kcal.toInt()}"
            override fun soft(word: SoftWord) = word.name
        }
        fun policy(preset: PresetId) = VisibilityPolicy(NumbersSettings(preset), clockAt(MORNING), TimeZone.UTC, loud)

        assertEquals(Display.Shown("E1245"), policy(PresetId.Full).resolve(Metric.Calories, 1245.0, Surface.Today))
        assertEquals(Display.Soft("TrendOnly", null), policy(PresetId.Nutrient).resolve(Metric.Weight, 70.0, Surface.Today))
    }
}
