package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Level resolution (`overrides[m] ?: preset.vis[m]`), picking a preset,
 * the visibility score and the friction trigger (`isRaise`, `needsPause`),
 * ported from `vis`, `score` and `requestChange` in the prototype.
 *
 * Not covered here: the friction sheet's countdown, which is UI.
 */
class NumbersSettingsTest {

    @Test
    fun level_fallsBackToThePreset() {
        val settings = NumbersSettings(preset = PresetId.NutrientsFirst)
        assertEquals(Visibility.Soften, settings.level(Metric.Protein))
        assertEquals(Visibility.Hide, settings.level(Metric.Carbs))
    }

    @Test
    fun level_overrideWinsOverThePreset() {
        val settings = NumbersSettings(
            preset = PresetId.NoNumbers,
            overrides = mapOf(Metric.Calories to Visibility.Show, Metric.Water to Visibility.Hide),
        )
        assertEquals(Visibility.Show, settings.level(Metric.Calories))
        assertEquals(Visibility.Hide, settings.level(Metric.Water))
        assertEquals(Visibility.Hide, settings.level(Metric.Protein))
    }

    @Test
    fun overrideOnFull_canHideCalories() {
        val policy = policy(PresetId.Full, overrides = mapOf(Metric.Calories to Visibility.Hide))
        assertEquals(Display.Hidden, policy.resolve(Metric.Calories, 1245.0, Surface.Today))
        assertEquals(Display.Shown("47 g"), policy.resolve(Metric.Protein, 47.0, Surface.Today))
    }

    @Test
    fun pickPreset_clearsOverridesAndRecordsWhen() {
        val adjusted = NumbersSettings(
            preset = PresetId.Full,
            overrides = mapOf(Metric.Micros to Visibility.Show, Metric.Calories to Visibility.Soften),
        )
        assertTrue(adjusted.isAdjusted)

        val picked = adjusted.pickPreset(PresetId.NoNumbers, SOME_INSTANT)

        assertEquals(PresetId.NoNumbers, picked.preset)
        assertEquals(emptyMap(), picked.overrides)
        assertEquals(SOME_INSTANT, picked.changedAt)
        assertFalse(picked.isAdjusted)
    }

    @Test
    fun pickPreset_samePresetStillClearsOverrides() {
        val adjusted = NumbersSettings(preset = PresetId.Full, overrides = mapOf(Metric.Micros to Visibility.Show))
        assertEquals(emptyMap(), adjusted.pickPreset(PresetId.Full, SOME_INSTANT).overrides)
    }

    @Test
    fun pickPreset_keepsPauseBeforeMore() {
        val settings = NumbersSettings(pauseBeforeMore = true).pickPreset(PresetId.Later, SOME_INSTANT)
        assertTrue(settings.pauseBeforeMore)
    }

    @Test
    fun override_addsToExistingOverridesAndRecordsWhen() {
        val settings = NumbersSettings(overrides = mapOf(Metric.Micros to Visibility.Show))
            .override(Metric.Water, Visibility.Hide, SOME_INSTANT)
        assertEquals(mapOf(Metric.Micros to Visibility.Show, Metric.Water to Visibility.Hide), settings.overrides)
        assertEquals(SOME_INSTANT, settings.changedAt)
    }

    @Test
    fun defaults_areFullNumbersWithNoPauseAndNoChange() {
        val settings = NumbersSettings()
        assertEquals(PresetId.Full, settings.preset)
        assertFalse(settings.pauseBeforeMore)
        assertEquals(null, settings.changedAt)
    }

    @Test
    fun score_sumsTheRawLevels() {
        assertEquals(27, NumbersSettings(preset = PresetId.Full).score())
        assertEquals(2, NumbersSettings(preset = PresetId.NoNumbers).score())
        // Seven delays at 1, micros hidden, weight and water softened at 2.
        assertEquals(11, NumbersSettings(preset = PresetId.Later).score())
        assertEquals(30, NumbersSettings(overrides = mapOf(Metric.Micros to Visibility.Show)).score())
    }

    @Test
    fun policyScore_countsDelayAsDelayEvenWhenRevealed() {
        val evening = policy(PresetId.Later, now = at(22))
        assertTrue(evening.isRevealed(Surface.Today))
        assertEquals(11, evening.score())
    }

    @Test
    fun isRaise_showingMoreIsARaise() {
        val blind = NumbersSettings(preset = PresetId.NoNumbers)
        assertTrue(blind.isRaise(NumbersSettings(preset = PresetId.Full)))
        assertTrue(blind.isRaise(blind.override(Metric.Calories, Visibility.Delay, SOME_INSTANT)))
        assertTrue(policy(PresetId.NoNumbers).isRaise(NumbersSettings(preset = PresetId.NutrientsFirst)))
    }

    @Test
    fun isRaise_showingLessOrTheSameIsNot() {
        val full = NumbersSettings(preset = PresetId.Full)
        assertFalse(full.isRaise(NumbersSettings(preset = PresetId.NoNumbers)))
        assertFalse(full.isRaise(full))
        assertFalse(full.isRaise(NumbersSettings(preset = PresetId.Full)))
        val blind = NumbersSettings(preset = PresetId.NoNumbers)
        assertFalse(blind.isRaise(blind.override(Metric.Water, Visibility.Hide, SOME_INSTANT)))
    }

    @Test
    fun needsPause_onlyForARaiseWithThePauseOn() {
        val off = NumbersSettings(preset = PresetId.NoNumbers)
        val on = off.copy(pauseBeforeMore = true)
        val full = NumbersSettings(preset = PresetId.Full)

        assertTrue(on.needsPause(full))
        assertFalse(off.needsPause(full))
        assertFalse(on.copy(preset = PresetId.Full).needsPause(on))
        assertFalse(on.needsPause(on.override(Metric.Water, Visibility.Hide, SOME_INSTANT)))
    }
}
