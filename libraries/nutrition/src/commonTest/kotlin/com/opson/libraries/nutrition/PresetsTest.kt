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
 * The ten presets match `PRESETS` in `docs/design/handoff/phone-logic.js`
 * metric for metric, including the line that softens water everywhere but
 * Full and Sprint, and the flags screens read (layout, reveal, meal count,
 * banner, fasting). One deliberate difference: Full and Sprint show micros,
 * where the prototype made them opt-in (`docs/decisions.md`, 2026-10-27).
 *
 * Not covered here: how a level renders, which is [VisibilityPolicyResolveTest]
 * and [VisibilityPolicyRevealTest].
 */
class PresetsTest {

    private fun levels(preset: ModePreset) = Metric.entries.map { preset.level(it) }

    // Order: calories, remaining, energyOut, protein, carbs, fat, fiber, micros, weight, water.

    @Test
    fun full_showsEverything_microsIncluded() {
        assertEquals(listOf(Show, Show, Show, Show, Show, Show, Show, Show, Show, Show), levels(Presets.Full))
    }

    @Test
    fun delayed_delaysEverythingSoftensWeightHidesMicros() {
        assertEquals(listOf(Delay, Delay, Delay, Delay, Delay, Delay, Delay, Hide, Soften, Soften), levels(Presets.Delayed))
        assertEquals(RevealOn.DayClose, Presets.Delayed.revealOn)
    }

    @Test
    fun blind_hidesEverythingButTheWaterBar() {
        assertEquals(listOf(Hide, Hide, Hide, Hide, Hide, Hide, Hide, Hide, Hide, Soften), levels(Presets.Blind))
    }

    @Test
    fun nutrient_softensProteinFiberAndWeight() {
        assertEquals(listOf(Hide, Hide, Hide, Soften, Hide, Hide, Soften, Hide, Soften, Soften), levels(Presets.Nutrient))
    }

    @Test
    fun rhythmExplorerMindful_hideEverythingButWater() {
        val expected = listOf(Hide, Hide, Hide, Hide, Hide, Hide, Hide, Hide, Hide, Soften)
        assertEquals(expected, levels(Presets.Rhythm))
        assertEquals(expected, levels(Presets.Explorer))
        assertEquals(expected, levels(Presets.Mindful))
    }

    @Test
    fun fuel_softensCaloriesAndShowsProteinAndFiber() {
        assertEquals(listOf(Soften, Hide, Hide, Show, Hide, Hide, Show, Hide, Soften, Soften), levels(Presets.Fuel))
    }

    @Test
    fun sprint_matchesFull() {
        assertEquals(Presets.Full.vis, Presets.Sprint.vis)
    }

    @Test
    fun coast_delaysCaloriesToTheWeek() {
        assertEquals(listOf(Delay, Hide, Hide, Hide, Hide, Hide, Hide, Hide, Soften, Soften), levels(Presets.Coast))
        assertEquals(RevealOn.Week, Presets.Coast.revealOn)
    }

    @Test
    fun water_isSoftenedEverywhereExceptFullAndSprint() {
        Presets.all.forEach { preset ->
            val expected = if (preset.id == PresetId.Full || preset.id == PresetId.Sprint) Show else Soften
            assertEquals(expected, preset.level(Metric.Water), preset.id.name)
        }
    }

    @Test
    fun everyPreset_coversEveryMetric() {
        Presets.all.forEach { assertEquals(Metric.entries.toSet(), it.vis.keys, it.id.name) }
    }

    @Test
    fun todayLayouts() {
        val layouts = Presets.all.associate { it.id to it.today }
        assertEquals(
            mapOf(
                PresetId.Full to TodayLayout.Full,
                PresetId.Delayed to TodayLayout.Full,
                PresetId.Blind to TodayLayout.Meals,
                PresetId.Nutrient to TodayLayout.Full,
                PresetId.Rhythm to TodayLayout.Rhythm,
                PresetId.Explorer to TodayLayout.Explorer,
                PresetId.Mindful to TodayLayout.Journal,
                PresetId.Fuel to TodayLayout.Full,
                PresetId.Sprint to TodayLayout.Full,
                PresetId.Coast to TodayLayout.Meals,
            ),
            layouts,
        )
    }

    @Test
    fun onlyDelayedAndCoast_haveARevealTime() {
        val withReveal = Presets.all.filter { it.revealOn != null }.map { it.id }
        assertEquals(listOf(PresetId.Delayed, PresetId.Coast), withReveal)
    }

    @Test
    fun mealCount_isOnForRhythmAndFuel() {
        assertEquals(listOf(PresetId.Rhythm, PresetId.Fuel), Presets.all.filter { it.mealCount }.map { it.id })
    }

    @Test
    fun banner_isOnForMindfulSprintAndCoast() {
        assertEquals(
            listOf(PresetId.Mindful, PresetId.Sprint, PresetId.Coast),
            Presets.all.filter { it.banner }.map { it.id },
        )
    }

    @Test
    fun fasting_isOnlyAllowedInFullAndSprint() {
        PresetId.entries.forEach { id ->
            val expected = id == PresetId.Full || id == PresetId.Sprint
            assertEquals(expected, Fasting.allowed(id), id.name)
        }
    }

    @Test
    fun all_listsEveryPresetOnceInTheModesScreenOrder() {
        assertEquals(PresetId.entries.toList(), Presets.all.map { it.id })
        PresetId.entries.forEach { assertEquals(it, Presets[it].id) }
    }

    @Test
    fun keys_matchThePrototype() {
        assertEquals(
            listOf("calories", "remaining", "energyOut", "protein", "carbs", "fat", "fiber", "micros", "weight", "water"),
            Metric.entries.map { it.key },
        )
        assertEquals(
            listOf("full", "delayed", "blind", "nutrient", "rhythm", "explorer", "mindful", "fuel", "sprint", "coast"),
            PresetId.entries.map { it.key },
        )
        assertEquals(PresetId.Coast, PresetId.fromKey("coast"))
        assertEquals(null, PresetId.fromKey("strict"))
    }

    @Test
    fun visibilityScores() {
        assertEquals(listOf(3, 2, 1, 0), listOf(Show, Soften, Delay, Hide).map { it.score })
        assertTrue(Show.score > Soften.score)
        assertFalse(Hide.score > Delay.score)
    }
}
