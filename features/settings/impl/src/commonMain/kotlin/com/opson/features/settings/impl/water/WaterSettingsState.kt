package com.opson.features.settings.impl.water

import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.WaterProgress
import com.opson.libraries.nutrition.WaterUnit

/**
 * The goal and glass size are settings, not amounts drunk, so they show in every mode, as the
 * plan says; only the intake on Today goes through the policy.
 */
data class WaterSettingsState(
    val backToMore: Boolean = false,
    val showOnToday: Boolean = true,
    val goals: List<Int> = WaterSettingsStateBuilder.goals,
    val glasses: Int = 0,
    /** "2,000 ml", inside "8 glasses, 2,000 ml a day". */
    val goalAmount: String = "",
    val glassMl: Int = 0,
    val sizes: List<GlassSize> = emptyList(),
    val unit: WaterUnit = WaterUnit.Ml,
)

/** A glass size choice: [ml] is what is stored, [label] is "250 ml" or "8 fl oz". */
data class GlassSize(val ml: Int, val label: String)

object WaterSettingsStateBuilder {

    val goals: List<Int> = listOf(6, 8, 10, 12)

    val glassSizesMl: List<Int> = listOf(200, 250, 330, 500)

    fun build(settings: Settings, backToMore: Boolean): WaterSettingsState = WaterSettingsState(
        backToMore = backToMore,
        showOnToday = settings.numbers.level(Metric.Water) != Visibility.Hide,
        goals = goals,
        glasses = settings.waterGlasses,
        goalAmount = WaterProgress.glassSize(settings.waterGoalMl, settings.waterUnit),
        glassMl = settings.glassMl,
        sizes = glassSizesMl.map { GlassSize(it, WaterProgress.glassSize(it, settings.waterUnit)) },
        unit = settings.waterUnit,
    )

    /**
     * What "Show on Today" asks for. Off hides water. On goes back to the mode's own level when
     * that level shows water at all, and only otherwise to Show, so the switch never shows more
     * than the mode itself would.
     */
    fun toggleChange(numbers: NumbersSettings): NumbersChange = when {
        numbers.level(Metric.Water) != Visibility.Hide -> NumbersChange.Override(Metric.Water, Visibility.Hide)
        Presets[numbers.preset].level(Metric.Water) != Visibility.Hide -> NumbersChange.ClearOverride(Metric.Water)
        else -> NumbersChange.Override(Metric.Water, Visibility.Show)
    }
}
