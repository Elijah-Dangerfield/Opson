package com.opson.libraries.nutrition

import com.opson.libraries.nutrition.Visibility.Delay
import com.opson.libraries.nutrition.Visibility.Hide
import com.opson.libraries.nutrition.Visibility.Show
import com.opson.libraries.nutrition.Visibility.Soften
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The four presets (all numbers, macros with grams, macros only, no numbers), metric for metric, and the flags screens read
 * (layout, meal count, fasting, photos, meal gaps). All numbers shows micros, where the
 * prototype made them opt-in (`docs/decisions.md`, 2026-10-27).
 *
 * Not covered here: how a level renders, which is [VisibilityPolicyResolveTest] and
 * [VisibilityPolicyRevealTest]; old presets, which is [LegacyPresetTest].
 */
class PresetsTest {

    private fun levels(preset: ModePreset) = Metric.entries.map { preset.level(it) }

    // Order: calories, remaining, energyOut, protein, carbs, fat, fiber, micros, weight, water.

    @Test
    fun full_showsEverything_microsIncluded() {
        assertEquals(listOf(Show, Show, Show, Show, Show, Show, Show, Show, Show, Show), levels(Presets.Full))
    }

    @Test
    fun macrosWithGrams_showsEverythingButEnergy() {
        assertEquals(listOf(Hide, Hide, Hide, Show, Show, Show, Show, Show, Show, Show), levels(Presets.MacrosWithGrams))
    }

    @Test
    fun macrosOnly_softensTheMacrosAndHidesCalories() {
        assertEquals(listOf(Hide, Hide, Hide, Soften, Soften, Soften, Soften, Hide, Soften, Soften), levels(Presets.MacrosOnly))
    }

    @Test
    fun noPreset_butAllNumbers_showsEnergy() {
        Presets.all.filter { it.id != PresetId.Full }.forEach { preset ->
            listOf(Metric.Calories, Metric.Remaining, Metric.EnergyOut).forEach { metric ->
                assertEquals(Hide, preset.level(metric), "${preset.id.name} ${metric.name}")
            }
        }
    }

    @Test
    fun laterLevels_delayEverythingSoftenWeightHideMicros() {
        assertEquals(listOf(Delay, Delay, Delay, Delay, Delay, Delay, Delay, Hide, Soften, Soften), Metric.entries.map { Presets.laterLevels.getValue(it) })
    }

    @Test
    fun noNumbers_hidesEverythingButTheWaterBar() {
        assertEquals(listOf(Hide, Hide, Hide, Hide, Hide, Hide, Hide, Hide, Hide, Soften), levels(Presets.NoNumbers))
    }

    @Test
    fun onlyTheTwoNumberModes_showAnAmount() {
        Presets.all.filter { it.id == PresetId.MacrosOnly || it.id == PresetId.NoNumbers }.forEach { preset ->
            assertTrue(Metric.entries.none { preset.level(it) == Show }, preset.id.name)
        }
    }

    @Test
    fun water_isSoftenedWhereAmountsAreHidden() {
        Presets.all.forEach { preset ->
            val expected = if (preset.id == PresetId.Full || preset.id == PresetId.MacrosWithGrams) Show else Soften
            assertEquals(expected, preset.level(Metric.Water), preset.id.name)
        }
    }

    @Test
    fun everyPreset_coversEveryMetric() {
        Presets.all.forEach { assertEquals(Metric.entries.toSet(), it.vis.keys, it.id.name) }
    }

    @Test
    fun todayLayouts() {
        assertEquals(
            mapOf(
                PresetId.Full to TodayLayout.Full,
                PresetId.MacrosWithGrams to TodayLayout.Full,
                PresetId.MacrosOnly to TodayLayout.Full,
                PresetId.NoNumbers to TodayLayout.MealTimeline,
            ),
            Presets.all.associate { it.id to it.today },
        )
    }

    @Test
    fun mealCount_isOnlyOnInMacrosOnly() {
        assertEquals(listOf(PresetId.MacrosOnly), Presets.all.filter { it.mealCount }.map { it.id })
    }

    @Test
    fun mealGaps_areOffInNoNumbers() {
        assertEquals(listOf(PresetId.NoNumbers), Presets.all.filterNot { it.mealGaps }.map { it.id })
    }

    @Test
    fun photos_hideByDefaultInMacrosOnlyAndNoNumbers() {
        assertEquals(listOf(PresetId.MacrosOnly, PresetId.NoNumbers), Presets.all.filterNot { it.progressPhotos }.map { it.id })
    }

    @Test
    fun fasting_isOnlyAllowedInFull() {
        PresetId.entries.forEach { id -> assertEquals(id == PresetId.Full, Fasting.allowed(id), id.name) }
    }

    @Test
    fun all_listsEveryPresetOnceInTheModesScreenOrder() {
        assertEquals(PresetId.entries.toList(), Presets.all.map { it.id })
        PresetId.entries.forEach { assertEquals(it, Presets[it].id) }
    }

    @Test
    fun keys_areStable() {
        assertEquals(
            listOf("calories", "remaining", "energyOut", "protein", "carbs", "fat", "fiber", "micros", "weight", "water"),
            Metric.entries.map { it.key },
        )
        assertEquals(listOf("full", "macros_grams", "macros_only", "no_numbers"), PresetId.entries.map { it.key })
        assertEquals(PresetId.MacrosOnly, PresetId.fromKey("macros_only"))
        assertEquals(null, PresetId.fromKey("coast"), "a legacy key is LegacyPreset's to read")
        assertEquals(null, PresetId.fromKey("later"), "a retired key is RetiredPreset's to read")
        assertEquals(listOf("nutrients_first", "later"), RetiredPreset.entries.map { it.key })
    }

    @Test
    fun visibilityScores() {
        assertEquals(listOf(3, 2, 1, 0), listOf(Show, Soften, Delay, Hide).map { it.score })
        assertTrue(Show.score > Soften.score)
        assertFalse(Hide.score > Delay.score)
    }
}

class RetiredPresetTest {

    @Test
    fun nutrientsFirst_becomesMacrosOnly_keepingThePersonsChanges() {
        val changes = mapOf(Metric.Carbs to Show)
        assertEquals(PresetId.MacrosOnly to changes, RetiredPreset.read("nutrients_first", changes))
    }

    @Test
    fun later_becomesAllNumbers_withEveryNumberStillOnLater() {
        val (preset, overrides) = assertNotNull(RetiredPreset.read("later", emptyMap()))
        val settings = NumbersSettings(preset, overrides)
        assertEquals(PresetId.Full, preset)
        Metric.entries.forEach { assertEquals(Presets.laterLevels.getValue(it), settings.level(it), it.name) }
        assertEquals(NumbersSettings.later(), settings)
    }

    @Test
    fun later_keepsAChangeOnTop_andDropsOneThatAllNumbersAlreadyMakes() {
        val (_, overrides) = assertNotNull(RetiredPreset.read("later", mapOf(Metric.Water to Hide, Metric.Protein to Show)))
        assertEquals(Hide, overrides[Metric.Water])
        assertFalse(Metric.Protein in overrides, "Show is All numbers' own level")
    }

    @Test
    fun currentKeys_readAsThemselves_andUnknownKeysAsNothing() {
        assertEquals(PresetId.MacrosWithGrams to emptyMap<Metric, Visibility>(), RetiredPreset.read("macros_grams", emptyMap()))
        assertNull(RetiredPreset.read("sparkle", emptyMap()))
    }

    @Test
    fun timerTargets() {
        assertEquals(PresetId.MacrosOnly, RetiredPreset.timerTarget("nutrients_first"))
        assertNull(RetiredPreset.timerTarget("later"), "no one preset is Later without showing more at once")
        assertEquals(PresetId.NoNumbers, RetiredPreset.timerTarget("no_numbers"))
    }
}
