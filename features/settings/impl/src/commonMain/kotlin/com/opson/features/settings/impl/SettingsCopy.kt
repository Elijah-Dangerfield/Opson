package com.opson.features.settings.impl

import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_level_hide
import opson.libraries.resources.generated.resources.settings_level_later
import opson.libraries.resources.generated.resources.settings_level_show
import opson.libraries.resources.generated.resources.settings_level_soft
import opson.libraries.resources.generated.resources.settings_metric_calories
import opson.libraries.resources.generated.resources.settings_metric_carbs
import opson.libraries.resources.generated.resources.settings_metric_energy_out
import opson.libraries.resources.generated.resources.settings_metric_fat
import opson.libraries.resources.generated.resources.settings_metric_fiber
import opson.libraries.resources.generated.resources.settings_metric_micros
import opson.libraries.resources.generated.resources.settings_metric_protein
import opson.libraries.resources.generated.resources.settings_metric_remaining
import opson.libraries.resources.generated.resources.settings_metric_water
import opson.libraries.resources.generated.resources.settings_metric_weight
import opson.libraries.resources.generated.resources.settings_preset_blind
import opson.libraries.resources.generated.resources.settings_preset_blind_line
import opson.libraries.resources.generated.resources.settings_preset_coast
import opson.libraries.resources.generated.resources.settings_preset_coast_line
import opson.libraries.resources.generated.resources.settings_preset_delayed
import opson.libraries.resources.generated.resources.settings_preset_delayed_line
import opson.libraries.resources.generated.resources.settings_preset_explorer
import opson.libraries.resources.generated.resources.settings_preset_explorer_line
import opson.libraries.resources.generated.resources.settings_preset_fuel
import opson.libraries.resources.generated.resources.settings_preset_fuel_line
import opson.libraries.resources.generated.resources.settings_preset_full
import opson.libraries.resources.generated.resources.settings_preset_full_line
import opson.libraries.resources.generated.resources.settings_preset_mindful
import opson.libraries.resources.generated.resources.settings_preset_mindful_line
import opson.libraries.resources.generated.resources.settings_preset_nutrient
import opson.libraries.resources.generated.resources.settings_preset_nutrient_line
import opson.libraries.resources.generated.resources.settings_preset_rhythm
import opson.libraries.resources.generated.resources.settings_preset_rhythm_line
import opson.libraries.resources.generated.resources.settings_preset_sprint
import opson.libraries.resources.generated.resources.settings_preset_sprint_line
import org.jetbrains.compose.resources.StringResource

/*
 * Copy keyed by domain enums. The domain stays free of strings (labels live in the UI, keyed by
 * id), so the mapping is written out once here for every settings page.
 */

internal val PresetId.label: StringResource
    get() = when (this) {
        PresetId.Full -> Res.string.settings_preset_full
        PresetId.Delayed -> Res.string.settings_preset_delayed
        PresetId.Blind -> Res.string.settings_preset_blind
        PresetId.Nutrient -> Res.string.settings_preset_nutrient
        PresetId.Rhythm -> Res.string.settings_preset_rhythm
        PresetId.Explorer -> Res.string.settings_preset_explorer
        PresetId.Mindful -> Res.string.settings_preset_mindful
        PresetId.Fuel -> Res.string.settings_preset_fuel
        PresetId.Sprint -> Res.string.settings_preset_sprint
        PresetId.Coast -> Res.string.settings_preset_coast
    }

/** The one-line description under a preset ("No calories anywhere in the app."). */
internal val PresetId.line: StringResource
    get() = when (this) {
        PresetId.Full -> Res.string.settings_preset_full_line
        PresetId.Delayed -> Res.string.settings_preset_delayed_line
        PresetId.Blind -> Res.string.settings_preset_blind_line
        PresetId.Nutrient -> Res.string.settings_preset_nutrient_line
        PresetId.Rhythm -> Res.string.settings_preset_rhythm_line
        PresetId.Explorer -> Res.string.settings_preset_explorer_line
        PresetId.Mindful -> Res.string.settings_preset_mindful_line
        PresetId.Fuel -> Res.string.settings_preset_fuel_line
        PresetId.Sprint -> Res.string.settings_preset_sprint_line
        PresetId.Coast -> Res.string.settings_preset_coast_line
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

/** The grid's column names: Show, Soft, Later, Hide. */
internal val Visibility.label: StringResource
    get() = when (this) {
        Visibility.Show -> Res.string.settings_level_show
        Visibility.Soften -> Res.string.settings_level_soft
        Visibility.Delay -> Res.string.settings_level_later
        Visibility.Hide -> Res.string.settings_level_hide
    }
