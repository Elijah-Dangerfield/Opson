package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The recommendation step's lists are built from each preset's levels. These hold the lists to
 * the levels, so the screen can never call a number "kept out of view" while the policy shows it.
 */
class ModePreviewTest {

    private val subjects = listOf(Metric.Calories, Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber, Metric.Weight)

    @Test
    fun nutrientsFirst_readsAsWordsAndBars() {
        val preview = ModePreview.of(Presets.NutrientsFirst)

        assertEquals(
            listOf(
                PreviewLine(PreviewMark.Shown, PreviewItem.MealCount),
                PreviewLine(PreviewMark.Softened, PreviewItem.EnergyWord),
                PreviewLine(PreviewMark.Softened, PreviewItem.ProteinFiberBars),
            ),
            preview.seen,
        )
        assertEquals(
            listOf(
                PreviewLine(PreviewMark.Hidden, PreviewItem.CaloriesHidden),
                PreviewLine(PreviewMark.Hidden, PreviewItem.CarbsFatHidden),
                PreviewLine(PreviewMark.Softened, PreviewItem.WeightTrend),
            ),
            preview.keptOut,
        )
    }

    @Test
    fun full_showsEveryNumber_andKeepsNothingOut() {
        val preview = ModePreview.of(Presets.Full)

        assertEquals(
            listOf(PreviewItem.CalorieAmounts, PreviewItem.MacroAmounts, PreviewItem.WeightAmounts),
            preview.seen.map { it.item },
        )
        assertTrue(preview.keptOut.isEmpty())
    }

    @Test
    fun everyShownSubject_isSeenAsShown_inEveryPreset() {
        Presets.all.forEach { preset ->
            val shown = ModePreview.of(preset).seen
                .filter { it.mark == PreviewMark.Shown }
                .flatMap { it.item.metrics }
                .toSet()
            subjects.filter { preset.level(it) == Visibility.Show }.forEach { metric ->
                assertTrue(metric in shown, "${preset.id}: $metric is shown but not listed as seen")
            }
        }
    }

    @Test
    fun nothingHidden_isListedAsSeen_andNothingShown_isListedAsKeptOut() {
        Presets.all.forEach { preset ->
            val preview = ModePreview.of(preset)
            preview.seen.flatMap { it.item.metrics }.forEach { metric ->
                assertTrue(preset.level(metric) != Visibility.Hide, "${preset.id}: hidden $metric listed as seen")
            }
            preview.keptOut.flatMap { it.item.metrics }.forEach { metric ->
                assertTrue(preset.level(metric) != Visibility.Show, "${preset.id}: shown $metric listed as kept out")
            }
        }
    }

    @Test
    fun everySubject_isAccountedFor_inEveryPreset() {
        Presets.all.forEach { preset ->
            val preview = ModePreview.of(preset)
            val listed = (preview.seen + preview.keptOut).flatMap { it.item.metrics }.toSet()
            subjects.forEach { metric ->
                assertTrue(metric in listed, "${preset.id}: $metric is in neither list")
            }
        }
    }

    @Test
    fun everyPreset_seesSomething() {
        Presets.all.forEach { preset ->
            assertTrue(ModePreview.of(preset).seen.isNotEmpty(), "${preset.id} has nothing under You'll see")
        }
    }

    @Test
    fun seen_isOrderedShownThenSoftenedThenLater() {
        Presets.all.forEach { preset ->
            val marks = ModePreview.of(preset).seen.map { it.mark.ordinal }
            assertEquals(marks.sorted(), marks, "${preset.id} lists its seen lines out of order")
        }
    }

    @Test
    fun delayedNumbers_sayWhenTheyOpen() {
        assertEquals(
            listOf(PreviewItem.CaloriesLater, PreviewItem.MacrosLater),
            ModePreview.of(Presets.Later).seen.map { it.item },
        )
    }
}
