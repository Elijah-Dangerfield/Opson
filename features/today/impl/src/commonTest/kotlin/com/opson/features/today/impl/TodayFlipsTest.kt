package com.opson.features.today.impl

import com.opson.libraries.diary.MainNumberChoice
import com.opson.libraries.diary.Reading
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [TodayFlips] per mode: which readings each flippable figure may show, so a flip never shows
 * what the mode keeps back, and after "numbers have been hard" never moves into a budget framing.
 * Pure rules against real policies. How the builder applies them (the figures, their words, the
 * sticky bar) is in `TodayEnergyCardTest` and `TodaySummaryBuilderTest`; the tap writing the
 * choice is in `TodayViewModelTest`.
 */
class TodayFlipsTest {

    private fun VisibilityPolicy.level(): (Metric) -> Visibility = { effectiveLevel(it, Surface.Today) }

    private fun main(policy: VisibilityPolicy, burned: Boolean = true, hard: Boolean = false, balanceIsTheGoal: Boolean = false) =
        TodayFlips.mainNumber(policy.level(), burnedShown = burned, hard = hard, balanceIsTheGoal = balanceIsTheGoal)

    @Test
    fun allNumbers_flipsTheMainNumberThroughTheGap_theBalance_andEaten() {
        assertEquals(listOf(MainNumberChoice.Goal, MainNumberChoice.Balance, MainNumberChoice.Eaten), main(policy(PresetId.Full)))
    }

    @Test
    fun withoutBurned_orWithWatchEnergyHidden_theresNoBalanceToFlipTo() {
        val goalAndEaten = listOf(MainNumberChoice.Goal, MainNumberChoice.Eaten)
        assertEquals(goalAndEaten, main(policy(PresetId.Full), burned = false))
        assertEquals(goalAndEaten, main(policy(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide))))
        assertEquals(goalAndEaten, main(policy(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Soften))))
    }

    @Test
    fun withTheRemainderHidden_theGapIsNotOffered() {
        assertEquals(
            listOf(MainNumberChoice.Balance, MainNumberChoice.Eaten),
            main(policy(PresetId.Full, overrides = mapOf(Metric.Remaining to Visibility.Hide))),
        )
    }

    @Test
    fun afterNumbersHaveBeenHard_theBalanceIsOnlyThereWhenItIsTheGoalTheyChose() {
        assertEquals(listOf(MainNumberChoice.Goal, MainNumberChoice.Eaten), main(policy(PresetId.Full), hard = true))
        assertEquals(
            listOf(MainNumberChoice.Goal, MainNumberChoice.Balance, MainNumberChoice.Eaten),
            main(policy(PresetId.Full), hard = true, balanceIsTheGoal = true),
        )
    }

    /** Every mode that keeps calories back, Later before its reveal included: the main number has no readings at all. */
    @Test
    fun everyModeThatKeepsCaloriesBack_hasNoMainNumberReading() {
        val held = Presets.all.filter { it.level(Metric.Calories) != Visibility.Show }.map { policy(it.id) } +
            policy(NumbersSettings.later())
        assertTrue(held.isNotEmpty())
        held.forEach { assertEquals(emptyList(), main(it), it.settings.preset.key) }
        // Later once open reads as All numbers.
        assertEquals(
            listOf(MainNumberChoice.Goal, MainNumberChoice.Balance, MainNumberChoice.Eaten),
            main(policy(NumbersSettings.later(), at = TodayFixtures.evening)),
        )
    }

    @Test
    fun theMacroRow_readsEatenLeftAndPercent_andAfterHardNeverLeft() {
        assertEquals(listOf(Reading.Eaten, Reading.Left, Reading.Percent), TodayFlips.macros(anyWithGoal = true, hard = false))
        assertEquals(listOf(Reading.Eaten, Reading.Percent), TodayFlips.macros(anyWithGoal = true, hard = true))
        assertEquals(emptyList(), TodayFlips.macros(anyWithGoal = false, hard = false), "no macro in full with a goal: nothing to flip")
    }

    @Test
    fun deficitSoFar_flipsToBurned_onlyWhereBothEnergiesShow() {
        assertEquals(listOf(Reading.Deficit, Reading.Burned), TodayFlips.deficitSoFar(policy(PresetId.Full).level()))
        assertEquals(emptyList(), TodayFlips.deficitSoFar(policy(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Soften)).level()))
        PresetId.entries.filter { it != PresetId.Full }.forEach {
            assertEquals(emptyList(), TodayFlips.deficitSoFar(policy(it).level()), it.key)
        }
    }

    @Test
    fun water_flipsBetweenDrunkAndToGo_whereverItsCountShows() {
        assertEquals(listOf(Reading.Eaten, Reading.Left), TodayFlips.water(policy(PresetId.Full).level()))
        assertEquals(listOf(Reading.Eaten, Reading.Left), TodayFlips.water(policy(PresetId.Full, overrides = mapOf(Metric.Water to Visibility.Soften)).level()))
        assertEquals(emptyList(), TodayFlips.water(policy(PresetId.Full, overrides = mapOf(Metric.Water to Visibility.Hide)).level()))
    }
}
