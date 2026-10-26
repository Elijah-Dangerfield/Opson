package com.opson.features.settings.impl.more

import com.opson.libraries.diary.MacroMode
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy

data class MoreState(val rows: List<MoreRow> = emptyList())

/** The More list, top to bottom. Fasting, Reminders, Watch and health and Widgets are not in 0.1. */
sealed interface MoreRow {
    /** "Full numbers" or "Full numbers, adjusted". */
    data class Modes(val preset: PresetId, val adjusted: Boolean) : MoreRow

    /** "2,100 kcal, ratio macros", or "Out of view in this mode" when [energy] is hidden. */
    data class Targets(val energy: Display, val macroMode: MacroMode) : MoreRow

    data object Water : MoreRow

    data object LookAndFeel : MoreRow

    data object Support : MoreRow
}

object MoreStateBuilder {

    /**
     * The Targets subtitle names the energy target only when calories are configured to Show,
     * as the prototype does: a delayed or softened mode says "Out of view in this mode" even
     * after 9 pm, because More is not where a delayed day opens.
     */
    fun build(settings: Settings, policy: VisibilityPolicy): MoreState {
        val energy = if (policy.level(Metric.Calories) == Visibility.Show) {
            policy.resolve(Metric.Calories, settings.targets.energyKcal.toDouble(), Surface.Settings)
        } else {
            Display.Hidden
        }
        return MoreState(
            rows = listOf(
                MoreRow.Modes(preset = settings.numbers.preset, adjusted = settings.numbers.isAdjusted),
                MoreRow.Targets(energy = energy, macroMode = settings.macroMode),
                MoreRow.Water,
                MoreRow.LookAndFeel,
                MoreRow.Support,
            ),
        )
    }
}
