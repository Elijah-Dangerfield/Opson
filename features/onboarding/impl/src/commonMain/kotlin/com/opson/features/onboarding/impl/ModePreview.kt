package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ModePreset
import com.opson.libraries.nutrition.RevealOn
import com.opson.libraries.nutrition.TodayLayout
import com.opson.libraries.nutrition.Visibility

/**
 * What a starting mode shows and keeps out of view, as the recommendation step lists it (design
 * v3, 1.5). Built from the preset's own levels rather than written per mode, so the list can
 * never say a number is out of view while the policy shows it.
 *
 * Water and vitamins are left out: every gentle mode softens water and no preset shows
 * vitamins, so neither line would tell modes apart.
 */
data class ModePreview(val seen: List<PreviewLine>, val keptOut: List<PreviewLine>) {

    companion object {
        private val macros = listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber)

        fun of(preset: ModePreset): ModePreview {
            val seen = mutableListOf<PreviewLine>()
            val keptOut = mutableListOf<PreviewLine>()

            layoutLine(preset.today)?.let { seen += PreviewLine(PreviewMark.Shown, it) }

            when (preset.level(Metric.Calories)) {
                Visibility.Show -> seen += PreviewLine(PreviewMark.Shown, PreviewItem.CalorieAmounts)
                Visibility.Soften -> {
                    seen += PreviewLine(PreviewMark.Softened, PreviewItem.EnergyWord)
                    keptOut += PreviewLine(PreviewMark.Hidden, PreviewItem.CaloriesHidden)
                }
                Visibility.Delay -> seen += PreviewLine(
                    PreviewMark.Later,
                    if (preset.revealOn == RevealOn.Week) PreviewItem.CaloriesWeekly else PreviewItem.CaloriesLater,
                )
                Visibility.Hide -> keptOut += PreviewLine(PreviewMark.Hidden, PreviewItem.CaloriesHidden)
            }

            macros.groupBy { preset.level(it) }.forEach { (level, metrics) ->
                val group = metrics.toSet()
                when (level) {
                    Visibility.Show -> seen += PreviewLine(PreviewMark.Shown, macroItem(group, Visibility.Show))
                    Visibility.Soften -> seen += PreviewLine(PreviewMark.Softened, macroItem(group, Visibility.Soften))
                    Visibility.Delay -> seen += PreviewLine(PreviewMark.Later, macroItem(group, Visibility.Delay))
                    Visibility.Hide -> keptOut += PreviewLine(PreviewMark.Hidden, macroItem(group, Visibility.Hide))
                }
            }

            if (preset.mealCount) seen += PreviewLine(PreviewMark.Shown, PreviewItem.MealCount)

            when (preset.level(Metric.Weight)) {
                Visibility.Show -> seen += PreviewLine(PreviewMark.Shown, PreviewItem.WeightAmounts)
                Visibility.Soften, Visibility.Delay ->
                    keptOut += PreviewLine(PreviewMark.Softened, PreviewItem.WeightTrend)
                Visibility.Hide -> keptOut += PreviewLine(PreviewMark.Hidden, PreviewItem.WeightHidden)
            }

            return ModePreview(
                seen = seen.sortedBy { it.mark.ordinal },
                keptOut = keptOut,
            )
        }

        private fun layoutLine(layout: TodayLayout): PreviewItem? = when (layout) {
            TodayLayout.Full -> null
            TodayLayout.Meals -> PreviewItem.MealByMeal
            TodayLayout.Rhythm -> PreviewItem.MealChecklist
            TodayLayout.Explorer -> PreviewItem.NewFoods
            TodayLayout.Journal -> PreviewItem.HowMealsFelt
        }

        private fun macroItem(group: Set<Metric>, level: Visibility): PreviewItem {
            val all = group == macros.toSet()
            val proteinFiber = group == setOf(Metric.Protein, Metric.Fiber)
            val carbsFat = group == setOf(Metric.Carbs, Metric.Fat)
            return when (level) {
                Visibility.Show -> when {
                    all -> PreviewItem.MacroAmounts
                    proteinFiber -> PreviewItem.ProteinFiberAmounts
                    else -> PreviewItem.SomeNutrientAmounts
                }
                Visibility.Soften -> if (proteinFiber) PreviewItem.ProteinFiberBars else PreviewItem.SomeNutrientBars
                Visibility.Delay -> if (all) PreviewItem.MacrosLater else PreviewItem.SomeNutrientsLater
                Visibility.Hide -> when {
                    all -> PreviewItem.MacrosHidden
                    carbsFat -> PreviewItem.CarbsFatHidden
                    else -> PreviewItem.OtherNutrientsHidden
                }
            }
        }
    }
}

/** One line of a [ModePreview]. [mark] picks the icon. */
data class PreviewLine(val mark: PreviewMark, val item: PreviewItem)

/** In the order the "You'll see" card lists them: shown first, then softened, then later. */
enum class PreviewMark { Shown, Softened, Later, Hidden }

/**
 * Each line's subject. [metrics] are the numbers the line speaks for, which `ModePreviewTest`
 * holds against the preset's levels.
 */
enum class PreviewItem(val metrics: Set<Metric> = emptySet()) {
    CalorieAmounts(setOf(Metric.Calories)),
    EnergyWord(setOf(Metric.Calories)),
    CaloriesLater(setOf(Metric.Calories)),
    CaloriesWeekly(setOf(Metric.Calories)),
    CaloriesHidden(setOf(Metric.Calories)),
    MacroAmounts(setOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber)),
    ProteinFiberAmounts(setOf(Metric.Protein, Metric.Fiber)),
    SomeNutrientAmounts,
    ProteinFiberBars(setOf(Metric.Protein, Metric.Fiber)),
    SomeNutrientBars,
    MacrosLater(setOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber)),
    SomeNutrientsLater,
    MacrosHidden(setOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber)),
    CarbsFatHidden(setOf(Metric.Carbs, Metric.Fat)),
    OtherNutrientsHidden,
    MealCount,
    WeightAmounts(setOf(Metric.Weight)),
    WeightTrend(setOf(Metric.Weight)),
    WeightHidden(setOf(Metric.Weight)),
    MealByMeal,
    MealChecklist,
    NewFoods,
    HowMealsFelt,
}
