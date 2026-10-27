package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ring labels (`ringThree` in the prototype) and the ring modes on offer.
 * The rule the Today rings exist to keep: the remaining ring never says 0 and
 * never goes negative, whatever it is handed.
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
        val evening = policy(PresetId.Later, now = at(21, 0))
        assertEquals(RingValue("1,245", "kcal"), evening.ringValue(Metric.Calories, 1245.0, Surface.Today))
        assertNull(policy(PresetId.Later).ringValue(Metric.Calories, 1245.0, Surface.Today))
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

    @Test
    fun balance_signedWithATrueMinus() {
        val withOut = policy(PresetId.Full)
        assertEquals(RingValue("−950", "kcal"), withOut.balanceRingValue(1040.0, 1990.0, Surface.Today))
        assertEquals(RingValue("+120", "kcal"), withOut.balanceRingValue(2110.0, 1990.0, Surface.Today))
    }

    @Test
    fun balance_needsBothCaloriesAndEnergyOutShown() {
        val noOut = policy(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide))
        val softCalories = policy(PresetId.Full, overrides = mapOf(Metric.Calories to Visibility.Soften))
        assertNull(noOut.balanceRingValue(1040.0, 1990.0, Surface.Today))
        assertNull(softCalories.balanceRingValue(1040.0, 1990.0, Surface.Today))
    }

    @Test
    fun ringModes_targetAlways() {
        PresetId.entries.forEach { preset ->
            assertEquals(SummaryRingMode.Target, policy(preset).availableRingModes(Surface.Today, hasEnergyOut = false).first())
        }
    }

    @Test
    fun ringModes_remainingOnlyWhenConfiguredShow() {
        assertEquals(
            listOf(SummaryRingMode.Target, SummaryRingMode.Remaining),
            policy(PresetId.Full).availableRingModes(Surface.Today, hasEnergyOut = false),
        )
        // Delayed reveals remaining at 9 pm, but a ring mode that comes and goes with the clock is not offered.
        assertEquals(
            listOf(SummaryRingMode.Target),
            policy(PresetId.Later, now = at(22, 0)).availableRingModes(Surface.Today, hasEnergyOut = false),
        )
        assertEquals(listOf(SummaryRingMode.Target), policy(PresetId.NoNumbers).availableRingModes(Surface.Today, false))
    }

    @Test
    fun ringModes_balanceNeedsEnergyOutDataAndShow() {
        val full = policy(PresetId.Full)
        assertEquals(
            listOf(SummaryRingMode.Target, SummaryRingMode.Remaining, SummaryRingMode.Balance),
            full.availableRingModes(Surface.Today, hasEnergyOut = true),
        )
        assertTrue(SummaryRingMode.Balance !in full.availableRingModes(Surface.Today, hasEnergyOut = false))
        val outHidden = policy(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Soften))
        assertTrue(SummaryRingMode.Balance !in outHidden.availableRingModes(Surface.Today, hasEnergyOut = true))
    }

    @Test
    fun ringModes_cycleAndFallBack() {
        val modes = listOf(SummaryRingMode.Target, SummaryRingMode.Remaining)
        assertEquals(SummaryRingMode.Remaining, SummaryRingMode.Target.nextIn(modes))
        assertEquals(SummaryRingMode.Target, SummaryRingMode.Remaining.nextIn(modes))
        assertEquals(SummaryRingMode.Target, SummaryRingMode.Balance.orTargetIn(modes))
        assertEquals(SummaryRingMode.Target, SummaryRingMode.Remaining.nextIn(listOf(SummaryRingMode.Target)))
    }
}
