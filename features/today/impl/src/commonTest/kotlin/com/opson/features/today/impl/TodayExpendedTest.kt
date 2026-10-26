package com.opson.features.today.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/**
 * Expended so far (0.2) in [TodayStateBuilder]: on the energy card, today only, and only while
 * energy out is effectively Show. It has no soften word, so every other level hides it.
 */
class TodayExpendedTest {

    private fun build(
        preset: PresetId,
        overrides: Map<Metric, Visibility> = emptyMap(),
        at: Instant = TodayFixtures.noon,
        day: Day = TodayFixtures.day(),
        expended: EnergyExpended? = TodayFixtures.expended,
    ): TodayState = TodayStateBuilder.build(TodayFixtures.inputs(day, expended = expended), policy(preset, at, overrides))

    private fun TodayState.expended(): Display = assertIs<TodaySummary.Energy>(summary).expended

    @Test
    fun full_showsTheTotalWithItsUnit() {
        assertEquals(Display.Shown("1,990 kcal"), build(PresetId.Full).expended())
    }

    @Test
    fun full_withNothingFromTheStore_showsNoLine() {
        assertEquals(Display.Hidden, build(PresetId.Full, expended = null).expended())
    }

    @Test
    fun aRecordedZero_isShownAsZero() {
        assertEquals(Display.Shown("0 kcal"), build(PresetId.Full, expended = EnergyExpended(0.0, 0.0, 0.0)).expended())
    }

    @Test
    fun full_withEnergyOutHidden_showsNoLine() {
        assertEquals(Display.Hidden, build(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide)).expended())
    }

    @Test
    fun full_withEnergyOutSoftened_showsNoLine() {
        assertEquals(Display.Hidden, build(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Soften)).expended())
    }

    @Test
    fun aPastDay_showsNoLine_becauseItSaysSoFar() {
        assertEquals(Display.Hidden, build(PresetId.Full, day = TodayFixtures.pastDay()).expended())
    }

    @Test
    fun delayed_atNight_opensWithTheCalories() {
        // Delayed shows the Revealed link rather than the energy card, so read the open card
        // through a Full mode that delays only energy out.
        val evening = LocalDateTime(TodayFixtures.today, LocalTime(21, 30)).toInstant(TodayFixtures.timeZone)
        val noon = build(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Delay))
        val night = build(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Delay), at = evening)
        assertEquals(Display.Hidden, noon.expended())
        assertEquals(Display.Shown("1,990 kcal"), night.expended())
    }

    @Test
    fun everyPreset_showsTheLineOnlyWhereEnergyOutShows() {
        PresetId.entries.forEach { preset ->
            val expended = (build(preset).summary as? TodaySummary.Energy)?.expended ?: Display.Hidden
            val shows = policy(preset).effectiveLevel(Metric.EnergyOut, Surface.Today) == Visibility.Show
            assertEquals(shows, expended is Display.Shown, "in $preset")
        }
    }

    @Test
    fun blind_showsNoLine() {
        assertEquals(null, build(PresetId.Blind).summary as? TodaySummary.Energy)
    }
}
