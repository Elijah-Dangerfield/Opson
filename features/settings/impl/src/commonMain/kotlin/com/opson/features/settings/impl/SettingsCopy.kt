package com.opson.features.settings.impl

import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.modes_preset_full
import opson.libraries.resources.generated.resources.modes_preset_full_line
import opson.libraries.resources.generated.resources.modes_preset_no_numbers
import opson.libraries.resources.generated.resources.modes_preset_no_numbers_line
import opson.libraries.resources.generated.resources.modes_preset_macros_grams
import opson.libraries.resources.generated.resources.modes_preset_macros_only
import opson.libraries.resources.generated.resources.modes_preset_macros_grams_line
import opson.libraries.resources.generated.resources.modes_preset_macros_only_line
import opson.libraries.resources.generated.resources.settings_level_hide
import opson.libraries.resources.generated.resources.settings_level_later
import opson.libraries.resources.generated.resources.settings_level_show
import opson.libraries.resources.generated.resources.settings_level_soft
import opson.libraries.resources.generated.resources.settings_metric_calories
import opson.libraries.resources.generated.resources.settings_metric_calories_inline
import opson.libraries.resources.generated.resources.settings_metric_carbs_inline
import opson.libraries.resources.generated.resources.settings_metric_energy_out_inline
import opson.libraries.resources.generated.resources.settings_metric_fat_inline
import opson.libraries.resources.generated.resources.settings_metric_fiber_inline
import opson.libraries.resources.generated.resources.settings_metric_micros_inline
import opson.libraries.resources.generated.resources.settings_metric_protein_inline
import opson.libraries.resources.generated.resources.settings_metric_remaining_inline
import opson.libraries.resources.generated.resources.settings_metric_water_inline
import opson.libraries.resources.generated.resources.settings_metric_weight_inline
import opson.libraries.resources.generated.resources.settings_metric_carbs
import opson.libraries.resources.generated.resources.settings_metric_energy_out
import opson.libraries.resources.generated.resources.settings_metric_fat
import opson.libraries.resources.generated.resources.settings_metric_fiber
import opson.libraries.resources.generated.resources.settings_metric_micros
import opson.libraries.resources.generated.resources.settings_metric_protein
import opson.libraries.resources.generated.resources.settings_metric_remaining
import opson.libraries.resources.generated.resources.settings_metric_water
import opson.libraries.resources.generated.resources.settings_metric_weight
import org.jetbrains.compose.resources.StringResource

/*
 * Copy keyed by domain enums. The domain stays free of strings (labels live in the UI, keyed by
 * id), so the mapping is written out once here for every settings page.
 */

internal val PresetId.label: StringResource
    get() = when (this) {
        PresetId.Full -> Res.string.modes_preset_full
        PresetId.MacrosWithGrams -> Res.string.modes_preset_macros_grams
        PresetId.MacrosOnly -> Res.string.modes_preset_macros_only
        PresetId.NoNumbers -> Res.string.modes_preset_no_numbers
    }

/** The one-line description under a preset ("Log all day. Totals open at a time you pick."). */
internal val PresetId.line: StringResource
    get() = when (this) {
        PresetId.Full -> Res.string.modes_preset_full_line
        PresetId.MacrosWithGrams -> Res.string.modes_preset_macros_grams_line
        PresetId.MacrosOnly -> Res.string.modes_preset_macros_only_line
        PresetId.NoNumbers -> Res.string.modes_preset_no_numbers_line
    }

internal val Metric.label: StringResource
    get() = when (this) {
        Metric.Calories -> Res.string.settings_metric_calories
        Metric.Remaining -> Res.string.settings_metric_remaining
        Metric.EnergyOut -> Res.string.settings_metric_energy_out
        Metric.Protein -> Res.string.settings_metric_protein
        Metric.Carbs -> Res.string.settings_metric_carbs
        Metric.Fat -> Res.string.settings_metric_fat
        Metric.Fiber -> Res.string.settings_metric_fiber
        Metric.Micros -> Res.string.settings_metric_micros
        Metric.Weight -> Res.string.settings_metric_weight
        Metric.Water -> Res.string.settings_metric_water
    }

/** The metric inside a sentence: "Showing calories again". */
internal val Metric.inline: StringResource
    get() = when (this) {
        Metric.Calories -> Res.string.settings_metric_calories_inline
        Metric.Remaining -> Res.string.settings_metric_remaining_inline
        Metric.EnergyOut -> Res.string.settings_metric_energy_out_inline
        Metric.Protein -> Res.string.settings_metric_protein_inline
        Metric.Carbs -> Res.string.settings_metric_carbs_inline
        Metric.Fat -> Res.string.settings_metric_fat_inline
        Metric.Fiber -> Res.string.settings_metric_fiber_inline
        Metric.Micros -> Res.string.settings_metric_micros_inline
        Metric.Weight -> Res.string.settings_metric_weight_inline
        Metric.Water -> Res.string.settings_metric_water_inline
    }

/** The grid's column names: Show, Soft, Later, Hide. */
internal val Visibility.label: StringResource
    get() = when (this) {
        Visibility.Show -> Res.string.settings_level_show
        Visibility.Soften -> Res.string.settings_level_soft
        Visibility.Delay -> Res.string.settings_level_later
        Visibility.Hide -> Res.string.settings_level_hide
    }
