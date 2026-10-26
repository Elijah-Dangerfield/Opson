package com.opson.features.settings.impl.targets

import com.opson.libraries.diary.MacroMode
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.resolveRange
import com.opson.libraries.nutrition.share

data class TargetsState(
    /** Calories configured to Show. Otherwise the page is the "Targets work in the background" card. */
    val visible: Boolean = false,
    /** "1,900 to 2,300 kcal". */
    val range: Display = Display.Hidden,
    /** "2,100 kcal", inside "Aim for about %s, before activity". */
    val aim: Display = Display.Hidden,
    /** False at the floor: Lower greys out but still answers, so the toast can say why. */
    val canLower: Boolean = false,
    val macroMode: MacroMode = MacroMode.Ratios,
    /** Protein, carbs and fat that are effectively Show. A hidden macro drops its whole row. */
    val macros: List<TargetMacroRow> = emptyList(),
)

/** "Protein 23%  120 g". */
data class TargetMacroRow(val metric: Metric, val share: Display, val grams: Display)

object TargetsStateBuilder {

    /** Energy per gram, for each macro's share of the target. Fiber is a floor, not a share. */
    private val kcalPerGram = listOf(Metric.Protein to 4, Metric.Carbs to 4, Metric.Fat to 9)

    /**
     * Amounts only when calories are configured Show. A delayed mode after 9 pm would resolve
     * them, but this page is not where a delayed day opens, so Soft and Delay modes get the
     * background card and no amounts at all, not even in state.
     */
    fun build(settings: Settings, policy: VisibilityPolicy): TargetsState {
        val targets = settings.targets
        if (policy.level(Metric.Calories) != Visibility.Show) {
            return TargetsState(visible = false, canLower = targets.canLower, macroMode = settings.macroMode)
        }
        val macros = kcalPerGram.mapNotNull { (metric, kcal) ->
            if (policy.effectiveLevel(metric, Surface.Settings) != Visibility.Show) return@mapNotNull null
            val grams = targets.grams(metric).toDouble()
            val percent = if (targets.energyKcal > 0) grams * kcal / targets.energyKcal * 100 else Double.NaN
            TargetMacroRow(
                metric = metric,
                share = policy.share(metric, percent, Surface.Settings),
                grams = policy.resolve(metric, grams, Surface.Settings),
            )
        }
        return TargetsState(
            visible = true,
            range = policy.resolveRange(
                Metric.Calories,
                targets.minKcal.toDouble(),
                targets.maxKcal.toDouble(),
                Surface.Settings,
            ),
            aim = policy.resolve(Metric.Calories, targets.energyKcal.toDouble(), Surface.Settings),
            canLower = targets.canLower,
            macroMode = settings.macroMode,
            macros = macros,
        )
    }

    private fun Targets.grams(metric: Metric): Int = when (metric) {
        Metric.Protein -> proteinG
        Metric.Carbs -> carbsG
        Metric.Fat -> fatG
        else -> error("Targets has no gram goal for $metric")
    }
}
