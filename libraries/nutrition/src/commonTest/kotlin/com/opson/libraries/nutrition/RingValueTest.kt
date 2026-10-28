package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ring labels (`ringThree` in the prototype), which the widget's ring draws.
 * The rule a remaining ring has to keep: it never says 0 and never goes
 * negative, whatever it is handed.
 */
class RingValueTest {

    private val full = showAll()

    // Default targets with no activity: 1,900 to 2,300, aiming at 2,100.
    private fun remaining(eaten: Double, min: Double? = 1900.0, max: Double? = 2300.0, target: Double = 2100.0) =
        full.ringValue(Metric.Remaining, target - eaten, Surface.Today, FormatContext(eaten = eaten, min = min, max = max))

    @Test
    fun calories_valueOverUnit() {
        assertEquals(RingValue("1,245", "kcal"), full.ringValue(Metric.Calories, 1245.0, Surface.Today))
        assertEquals(RingValue("2,100", "kcal"), full.ringValue(Metric.Calories, 2100.0, Surface.Today))
    }

    @Test
    fun calories_ignoresBare_theUnitIsAlwaysItsOwnLine() {
        assertEquals(
            RingValue("1,245", "kcal"),
            full.ringValue(Metric.Calories, 1245.0, Surface.Today, FormatContext(bare = true)),
        )
    }

    @Test
    fun calories_anEmptyDayIsZeroEaten() {
        assertEquals(RingValue("0", "kcal"), full.ringValue(Metric.Calories, 0.0, Surface.Today))
    }

    @Test
    fun macros_valueOverGrams() {
        assertEquals(RingValue("47", "g"), full.ringValue(Metric.Protein, 47.4, Surface.Today))
    }

    @Test
    fun remaining_belowTheRange_isTheAmountLeft() {
        assertEquals(RingValue("1,060", "kcal"), remaining(eaten = 1040.0))
        assertEquals(RingValue("201", "kcal"), remaining(eaten = 1899.0))
    }

    @Test
    fun remaining_insideTheRange_isOnTarget() {
        listOf(1900.0, 2000.0, 2100.0, 2200.0, 2300.0).forEach { eaten ->
            assertEquals(RingValue("On", "target"), remaining(eaten), "eaten $eaten")
        }
    }

    @Test
    fun remaining_aboveTheRange_isPastRange() {
        assertEquals(RingValue("Past", "range"), remaining(eaten = 2301.0))
        assertEquals(RingValue("Past", "range"), remaining(eaten = 4000.0))
    }

    @Test
    fun remaining_neverZeroNeverNegative_withNoRangeEither() {
        listOf(0.4, 0.0, -0.4, -1.0, -950.0).forEach { left ->
            val ring = full.ringValue(Metric.Remaining, left, Surface.Today)
            assertEquals(RingValue("On", "target"), ring, "left $left")
        }
    }

    @Test
    fun remaining_neverZero_sweptAcrossADay() {
        var eaten = 0.0
        while (eaten <= 4000.0) {
            val ring = requireNotNull(remaining(eaten))
            assertNotEquals("0", ring.value, "eaten $eaten")
            assertTrue(!ring.value.startsWith("-") && !ring.value.startsWith("−"), "eaten $eaten: ${ring.value}")
            eaten += 0.5
        }
    }

    @Test
    fun remaining_withATargetBelowTheRangeMinimum_stillNeverSaysZero() {
        // A malformed range (target under min) must not produce "0 kcal".
        assertEquals(RingValue("On", "target"), remaining(eaten = 1800.0, min = 1900.0, target = 1800.0))
    }

    @Test
    fun onlyShowProducesALabel() {
        val levels = listOf(Visibility.Soften, Visibility.Hide, Visibility.Delay)
        levels.forEach { level ->
            val p = policy(PresetId.Full, overrides = mapOf(Metric.Calories to level, Metric.Remaining to level))
            assertNull(p.ringValue(Metric.Calories, 1245.0, Surface.Today), "$level calories")
            assertNull(p.ringValue(Metric.Remaining, 500.0, Surface.Today), "$level remaining")
        }
    }

    @Test
    fun blind_noLabelForAnyRingMetric() {
        val blind = policy(PresetId.NoNumbers)
        listOf(Metric.Calories, Metric.Remaining, Metric.EnergyOut, Metric.Protein).forEach { metric ->
            Surface.entries.forEach { surface ->
                assertNull(blind.ringValue(metric, 1245.0, surface), "$metric $surface")
            }
        }
    }

    @Test
    fun delay_opensAtNineLikeTheAmount() {
        val evening = later(now = at(21, 0))
        assertEquals(RingValue("1,245", "kcal"), evening.ringValue(Metric.Calories, 1245.0, Surface.Today))
        assertNull(later().ringValue(Metric.Calories, 1245.0, Surface.Today))
    }

    @Test
    fun missingValue_noLabel() {
        assertNull(full.ringValue(Metric.Calories, null, Surface.Today))
        assertNull(full.ringValue(Metric.Calories, Double.NaN, Surface.Today))
    }

    @Test
    fun unsupportedMetric_isAProgrammingError() {
        assertFailsWith<IllegalArgumentException> { full.ringValue(Metric.Water, 750.0, Surface.Today) }
    }
}
