package com.opson.libraries.nutrition

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `requestChange` in the prototype: what each change does to the settings,
 * and which changes have to go through "Pause before showing more".
 */
class NumbersChangeTest {

    private val at = SOME_INSTANT
    private val blindPaused = NumbersSettings(preset = PresetId.Blind, pauseBeforeMore = true)
    private val fullPaused = NumbersSettings(preset = PresetId.Full, pauseBeforeMore = true)

    @Test
    fun pickPreset_clearsOverridesAndStamps() {
        val adjusted = NumbersSettings(preset = PresetId.Full, overrides = mapOf(Metric.Weight to Visibility.Hide))
        val next = NumbersChange.PickPreset(PresetId.Blind).applyTo(adjusted, at)
        assertEquals(NumbersSettings(preset = PresetId.Blind, changedAt = at), next)
    }

    @Test
    fun override_setsOneLevel() {
        val next = NumbersChange.Override(Metric.Water, Visibility.Hide).applyTo(NumbersSettings(PresetId.Blind), at)
        assertEquals(Visibility.Hide, next.level(Metric.Water))
        assertEquals(PresetId.Blind, next.preset)
        assertEquals(at, next.changedAt)
    }

    @Test
    fun clearOverride_goesBackToTheModesLevel() {
        val start = NumbersSettings(PresetId.Blind, overrides = mapOf(Metric.Water to Visibility.Hide, Metric.Weight to Visibility.Show))
        val next = NumbersChange.ClearOverride(Metric.Water).applyTo(start, at)
        assertEquals(mapOf(Metric.Weight to Visibility.Show), next.overrides)
        assertEquals(Visibility.Soften, next.level(Metric.Water))
        assertEquals(at, next.changedAt)
    }

    @Test
    fun turnOffPause() {
        val next = NumbersChange.TurnOffPause.applyTo(fullPaused, at)
        assertFalse(next.pauseBeforeMore)
        assertEquals(PresetId.Full, next.preset)
        assertEquals(at, next.changedAt)
    }

    @Test
    fun needsPause_raiseWithPauseOn() {
        assertTrue(NumbersChange.PickPreset(PresetId.Full).needsPause(blindPaused))
        assertTrue(NumbersChange.Override(Metric.Micros, Visibility.Show).needsPause(fullPaused))
        assertTrue(NumbersChange.Override(Metric.Calories, Visibility.Soften).needsPause(blindPaused))
    }

    @Test
    fun needsPause_clearingAHideOverrideIsARaise() {
        val waterHidden = blindPaused.copy(overrides = mapOf(Metric.Water to Visibility.Hide))
        assertTrue(NumbersChange.ClearOverride(Metric.Water).needsPause(waterHidden))
    }

    @Test
    fun needsPause_neverForALower() {
        assertFalse(NumbersChange.PickPreset(PresetId.Blind).needsPause(fullPaused))
        assertFalse(NumbersChange.Override(Metric.Calories, Visibility.Hide).needsPause(fullPaused))
    }

    @Test
    fun needsPause_neverForAnEqualScore() {
        // Full and Sprint share a map, so the switch neither raises nor lowers.
        assertFalse(NumbersChange.PickPreset(PresetId.Sprint).needsPause(fullPaused))
        assertFalse(NumbersChange.Override(Metric.Calories, Visibility.Show).needsPause(fullPaused))
    }

    @Test
    fun needsPause_neverWithPauseOff() {
        val blind = NumbersSettings(PresetId.Blind)
        assertFalse(NumbersChange.PickPreset(PresetId.Full).needsPause(blind))
        assertFalse(NumbersChange.TurnOffPause.needsPause(blind))
    }

    @Test
    fun needsPause_turningThePauseOffAlwaysPauses() {
        assertTrue(NumbersChange.TurnOffPause.needsPause(fullPaused))
        assertTrue(NumbersChange.TurnOffPause.needsPause(blindPaused))
    }

    @Test
    fun serializes_soThePauseSheetCanCarryIt() {
        val changes = listOf(
            NumbersChange.PickPreset(PresetId.Coast),
            NumbersChange.Override(Metric.Remaining, Visibility.Delay),
            NumbersChange.ClearOverride(Metric.Water),
            NumbersChange.TurnOffPause,
        )
        changes.forEach { change ->
            val json = Json.encodeToString(NumbersChange.serializer(), change)
            assertEquals(change, Json.decodeFromString(NumbersChange.serializer(), json), json)
        }
    }
}
