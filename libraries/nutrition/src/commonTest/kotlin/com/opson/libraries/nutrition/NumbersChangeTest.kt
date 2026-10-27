package com.opson.libraries.nutrition

import kotlinx.datetime.LocalTime
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
    fun setRevealAt_movesTheRevealAndStamps() {
        val next = NumbersChange.SetRevealAt(LocalTime(19, 30)).applyTo(NumbersSettings(PresetId.Delayed), at)
        assertEquals(LocalTime(19, 30), next.revealAt)
        assertEquals(at, next.changedAt)
    }

    @Test
    fun setRevealAt_earlierPauses_laterDoesNot() {
        val delayed = NumbersSettings(PresetId.Delayed, pauseBeforeMore = true)
        assertTrue(NumbersChange.SetRevealAt(LocalTime(20, 0)).needsPause(delayed))
        assertFalse(NumbersChange.SetRevealAt(LocalTime(22, 0)).needsPause(delayed))
        assertFalse(NumbersChange.SetRevealAt(delayed.revealAt).needsPause(delayed))
        assertFalse(NumbersChange.SetRevealAt(LocalTime(6, 0)).needsPause(delayed.copy(pauseBeforeMore = false)))
    }

    @Test
    fun progressPhotos_followThePreset_untilPicked() {
        val hiddenBy = PresetId.entries.filterNot { NumbersSettings(it).showsProgressPhotos }
        assertEquals(listOf(PresetId.Blind, PresetId.Rhythm, PresetId.Mindful, PresetId.Fuel), hiddenBy)

        val shown = NumbersChange.SetProgressPhotos(shown = true).applyTo(NumbersSettings(PresetId.Fuel), at)
        assertTrue(shown.showsProgressPhotos)
        assertTrue(shown.progressPhotosChanged)
        assertEquals(at, shown.changedAt)
        assertFalse(NumbersSettings(PresetId.Full, progressPhotos = true).progressPhotosChanged, "the same as the preset's")
    }

    @Test
    fun pickPreset_clearsThePhotosChoiceToo() {
        val next = NumbersChange.PickPreset(PresetId.Full).applyTo(NumbersSettings(PresetId.Full, progressPhotos = false), at)
        assertEquals(null, next.progressPhotos)
        assertTrue(next.showsProgressPhotos)
    }

    @Test
    fun progressPhotos_showingPauses_hidingNever() {
        val fuelPaused = NumbersSettings(PresetId.Fuel, pauseBeforeMore = true)
        assertTrue(NumbersChange.SetProgressPhotos(shown = true).needsPause(fuelPaused))
        assertFalse(NumbersChange.SetProgressPhotos(shown = true).needsPause(fuelPaused.copy(pauseBeforeMore = false)))
        assertFalse(NumbersChange.SetProgressPhotos(shown = false).needsPause(fullPaused))
        // A preset that hides fewer numbers but brings photos back is still showing more.
        assertTrue(NumbersChange.PickPreset(PresetId.Explorer).needsPause(NumbersSettings(PresetId.Blind, pauseBeforeMore = true)))
        val photosHidden = NumbersSettings(PresetId.Nutrient, progressPhotos = false, pauseBeforeMore = true)
        assertTrue(NumbersChange.PickPreset(PresetId.Explorer).needsPause(photosHidden))
    }

    @Test
    fun isLower_hidingPhotosAlone_isFewer() {
        val full = NumbersSettings(PresetId.Full)
        assertTrue(full.isLower(full.copy(progressPhotos = false)))
        assertFalse(full.copy(progressPhotos = false).isLower(full))
        assertFalse(full.isLower(full))
    }

    @Test
    fun changes_countOnlyLevelsThatDifferFromThePreset() {
        val fuel = NumbersSettings(
            PresetId.Fuel,
            overrides = mapOf(Metric.Carbs to Visibility.Show, Metric.Protein to Presets[PresetId.Fuel].level(Metric.Protein)),
        )
        assertEquals(mapOf(Metric.Carbs to Visibility.Show), fuel.changes)
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
            NumbersChange.SetProgressPhotos(shown = true),
        )
        changes.forEach { change ->
            val json = Json.encodeToString(NumbersChange.serializer(), change)
            assertEquals(change, Json.decodeFromString(NumbersChange.serializer(), json), json)
        }
    }
}
