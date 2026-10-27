package com.opson.libraries.nutrition

import com.opson.libraries.nutrition.Visibility.Delay
import com.opson.libraries.nutrition.Visibility.Hide
import com.opson.libraries.nutrition.Visibility.Show
import com.opson.libraries.nutrition.Visibility.Soften
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The four presets of design sync 4 (13.5a), metric for metric, and the flags screens read
 * (layout, meal count, fasting, photos, meal gaps).
 *
 * Not covered here: how a level renders, which is [VisibilityPolicyResolveTest] and
 * [VisibilityPolicyRevealTest]; old presets, which is [LegacyPresetTest].
 */
class PresetsTest {

    private fun levels(preset: ModePreset) = Metric.entries.map { preset.level(it) }

    // Order: calories, remaining, energyOut, protein, carbs, fat, fiber, micros, weight, water.

    @Test
    fun full_showsEverythingButMicros() {
        assertEquals(listOf(Show, Show, Show, Show, Show, Show, Show, Hide, Show, Show), levels(Presets.Full))
    }

    @Test
    fun nutrientsFirst_isProteinFiberAndWaterUpFrontWithCaloriesAsAFillBar() {
        assertEquals(listOf(Soften, Hide, Hide, Soften, Hide, Hide, Soften, Hide, Soften, Soften), levels(Presets.NutrientsFirst))
    }

    @Test
    fun later_delaysEverythingSoftensWeightHidesMicros() {
        assertEquals(listOf(Delay, Delay, Delay, Delay, Delay, Delay, Delay, Hide, Soften, Soften), levels(Presets.Later))
    }

    @Test
    fun noNumbers_hidesEverythingButTheWaterBar() {
        assertEquals(listOf(Hide, Hide, Hide, Hide, Hide, Hide, Hide, Hide, Hide, Soften), levels(Presets.NoNumbers))
    }

    @Test
    fun noPresetButFull_showsAnAmount() {
        Presets.all.filter { it.id != PresetId.Full }.forEach { preset ->
            assertTrue(Metric.entries.none { preset.level(it) == Show }, preset.id.name)
        }
    }

    @Test
    fun water_isSoftenedEverywhereExceptFull() {
        Presets.all.forEach { preset ->
            val expected = if (preset.id == PresetId.Full) Show else Soften
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
                PresetId.NutrientsFirst to TodayLayout.Full,
                PresetId.Later to TodayLayout.Full,
                PresetId.NoNumbers to TodayLayout.MealTimeline,
            ),
            Presets.all.associate { it.id to it.today },
        )
    }

    @Test
    fun mealCount_isOnlyOnInNutrientsFirst() {
        assertEquals(listOf(PresetId.NutrientsFirst), Presets.all.filter { it.mealCount }.map { it.id })
    }

    @Test
    fun mealGaps_areOffInNoNumbers() {
        assertEquals(listOf(PresetId.NoNumbers), Presets.all.filterNot { it.mealGaps }.map { it.id })
    }

    @Test
    fun photos_hideByDefaultInNutrientsFirstAndNoNumbers() {
        assertEquals(listOf(PresetId.NutrientsFirst, PresetId.NoNumbers), Presets.all.filterNot { it.progressPhotos }.map { it.id })
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
        assertEquals(listOf("full", "nutrients_first", "later", "no_numbers"), PresetId.entries.map { it.key })
        assertEquals(PresetId.Later, PresetId.fromKey("later"))
        assertEquals(null, PresetId.fromKey("coast"), "a legacy key is LegacyPreset's to read")
    }

    @Test
    fun visibilityScores() {
        assertEquals(listOf(3, 2, 1, 0), listOf(Show, Soften, Delay, Hide).map { it.score })
        assertTrue(Show.score > Soften.score)
        assertFalse(Hide.score > Delay.score)
    }
}
