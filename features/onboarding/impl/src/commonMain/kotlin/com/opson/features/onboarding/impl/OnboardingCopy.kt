package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.PresetId
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.onboarding_feel_love_them
import opson.libraries.resources.generated.resources.onboarding_feel_not_sure
import opson.libraries.resources.generated.resources.onboarding_feel_rather_not
import opson.libraries.resources.generated.resources.onboarding_feel_some_are_fine
import opson.libraries.resources.generated.resources.onboarding_notice_amounts
import opson.libraries.resources.generated.resources.onboarding_notice_how_eating_feels
import opson.libraries.resources.generated.resources.onboarding_notice_nutrients
import opson.libraries.resources.generated.resources.onboarding_notice_patterns
import opson.libraries.resources.generated.resources.onboarding_pick_any
import opson.libraries.resources.generated.resources.onboarding_preset_blind
import opson.libraries.resources.generated.resources.onboarding_preset_blind_line
import opson.libraries.resources.generated.resources.onboarding_preset_coast
import opson.libraries.resources.generated.resources.onboarding_preset_coast_line
import opson.libraries.resources.generated.resources.onboarding_preset_delayed
import opson.libraries.resources.generated.resources.onboarding_preset_delayed_line
import opson.libraries.resources.generated.resources.onboarding_preset_explorer
import opson.libraries.resources.generated.resources.onboarding_preset_explorer_line
import opson.libraries.resources.generated.resources.onboarding_preset_fuel
import opson.libraries.resources.generated.resources.onboarding_preset_fuel_line
import opson.libraries.resources.generated.resources.onboarding_preset_full
import opson.libraries.resources.generated.resources.onboarding_preset_full_line
import opson.libraries.resources.generated.resources.onboarding_preset_mindful
import opson.libraries.resources.generated.resources.onboarding_preset_mindful_line
import opson.libraries.resources.generated.resources.onboarding_preset_nutrient
import opson.libraries.resources.generated.resources.onboarding_preset_nutrient_line
import opson.libraries.resources.generated.resources.onboarding_preset_rhythm
import opson.libraries.resources.generated.resources.onboarding_preset_rhythm_line
import opson.libraries.resources.generated.resources.onboarding_preset_sprint
import opson.libraries.resources.generated.resources.onboarding_preset_sprint_line
import opson.libraries.resources.generated.resources.onboarding_preview_calorie_amounts
import opson.libraries.resources.generated.resources.onboarding_preview_calories_hidden
import opson.libraries.resources.generated.resources.onboarding_preview_calories_later
import opson.libraries.resources.generated.resources.onboarding_preview_calories_weekly
import opson.libraries.resources.generated.resources.onboarding_preview_carbs_fat_hidden
import opson.libraries.resources.generated.resources.onboarding_preview_energy_word
import opson.libraries.resources.generated.resources.onboarding_preview_how_meals_felt
import opson.libraries.resources.generated.resources.onboarding_preview_macro_amounts
import opson.libraries.resources.generated.resources.onboarding_preview_macros_hidden
import opson.libraries.resources.generated.resources.onboarding_preview_macros_later
import opson.libraries.resources.generated.resources.onboarding_preview_meal_by_meal
import opson.libraries.resources.generated.resources.onboarding_preview_meal_checklist
import opson.libraries.resources.generated.resources.onboarding_preview_meal_count
import opson.libraries.resources.generated.resources.onboarding_preview_new_foods
import opson.libraries.resources.generated.resources.onboarding_preview_other_nutrients_hidden
import opson.libraries.resources.generated.resources.onboarding_preview_protein_fiber_amounts
import opson.libraries.resources.generated.resources.onboarding_preview_protein_fiber_bars
import opson.libraries.resources.generated.resources.onboarding_preview_some_nutrient_amounts
import opson.libraries.resources.generated.resources.onboarding_preview_some_nutrient_bars
import opson.libraries.resources.generated.resources.onboarding_preview_some_nutrients_later
import opson.libraries.resources.generated.resources.onboarding_preview_weight_amounts
import opson.libraries.resources.generated.resources.onboarding_preview_weight_hidden
import opson.libraries.resources.generated.resources.onboarding_preview_weight_trend
import opson.libraries.resources.generated.resources.onboarding_q_feel
import opson.libraries.resources.generated.resources.onboarding_q_notice
import opson.libraries.resources.generated.resources.onboarding_q_statement
import opson.libraries.resources.generated.resources.onboarding_q_why
import opson.libraries.resources.generated.resources.onboarding_statement_no
import opson.libraries.resources.generated.resources.onboarding_statement_yes
import opson.libraries.resources.generated.resources.onboarding_why_changing_weight
import opson.libraries.resources.generated.resources.onboarding_why_curious
import opson.libraries.resources.generated.resources.onboarding_why_eating_enough
import opson.libraries.resources.generated.resources.onboarding_why_eating_regularly
import opson.libraries.resources.generated.resources.onboarding_why_medical_need
import opson.libraries.resources.generated.resources.onboarding_why_performance
import opson.libraries.resources.generated.resources.onboarding_why_working_with_provider
import org.jetbrains.compose.resources.StringResource

internal val SetupQuestion.title: StringResource
    get() = when (this) {
        SetupQuestion.Reason -> Res.string.onboarding_q_why
        SetupQuestion.Feeling -> Res.string.onboarding_q_feel
        SetupQuestion.Focus -> Res.string.onboarding_q_notice
        SetupQuestion.Statement -> Res.string.onboarding_q_statement
    }

/** The line under the title. The statement has its own quote and body instead. */
internal val SetupQuestion.subtitle: StringResource?
    get() = when (this) {
        SetupQuestion.Reason -> Res.string.onboarding_pick_any
        SetupQuestion.Feeling, SetupQuestion.Focus, SetupQuestion.Statement -> null
    }

internal val SetupOption.label: StringResource
    get() = when (this) {
        Why.Curious -> Res.string.onboarding_why_curious
        Why.Performance -> Res.string.onboarding_why_performance
        Why.EatingEnough -> Res.string.onboarding_why_eating_enough
        Why.EatingRegularly -> Res.string.onboarding_why_eating_regularly
        Why.MedicalNeed -> Res.string.onboarding_why_medical_need
        Why.ChangingWeight -> Res.string.onboarding_why_changing_weight
        Why.WorkingWithProvider -> Res.string.onboarding_why_working_with_provider
        NumbersFeel.LoveThem -> Res.string.onboarding_feel_love_them
        NumbersFeel.SomeAreFine -> Res.string.onboarding_feel_some_are_fine
        NumbersFeel.RatherNot -> Res.string.onboarding_feel_rather_not
        NumbersFeel.NotSure -> Res.string.onboarding_feel_not_sure
        Notice.Amounts -> Res.string.onboarding_notice_amounts
        Notice.Nutrients -> Res.string.onboarding_notice_nutrients
        Notice.Patterns -> Res.string.onboarding_notice_patterns
        Notice.HowEatingFeels -> Res.string.onboarding_notice_how_eating_feels
        StatementAnswer.Agree -> Res.string.onboarding_statement_yes
        StatementAnswer.NotReally -> Res.string.onboarding_statement_no
    }

internal val PresetId.label: StringResource
    get() = when (this) {
        PresetId.Full -> Res.string.onboarding_preset_full
        PresetId.Delayed -> Res.string.onboarding_preset_delayed
        PresetId.Blind -> Res.string.onboarding_preset_blind
        PresetId.Nutrient -> Res.string.onboarding_preset_nutrient
        PresetId.Rhythm -> Res.string.onboarding_preset_rhythm
        PresetId.Explorer -> Res.string.onboarding_preset_explorer
        PresetId.Mindful -> Res.string.onboarding_preset_mindful
        PresetId.Fuel -> Res.string.onboarding_preset_fuel
        PresetId.Sprint -> Res.string.onboarding_preset_sprint
        PresetId.Coast -> Res.string.onboarding_preset_coast
    }

/** The line under the mode's name on the recommendation step. */
internal val PresetId.line: StringResource
    get() = when (this) {
        PresetId.Full -> Res.string.onboarding_preset_full_line
        PresetId.Delayed -> Res.string.onboarding_preset_delayed_line
        PresetId.Blind -> Res.string.onboarding_preset_blind_line
        PresetId.Nutrient -> Res.string.onboarding_preset_nutrient_line
        PresetId.Rhythm -> Res.string.onboarding_preset_rhythm_line
        PresetId.Explorer -> Res.string.onboarding_preset_explorer_line
        PresetId.Mindful -> Res.string.onboarding_preset_mindful_line
        PresetId.Fuel -> Res.string.onboarding_preset_fuel_line
        PresetId.Sprint -> Res.string.onboarding_preset_sprint_line
        PresetId.Coast -> Res.string.onboarding_preset_coast_line
    }

internal val PreviewItem.label: StringResource
    get() = when (this) {
        PreviewItem.CalorieAmounts -> Res.string.onboarding_preview_calorie_amounts
        PreviewItem.EnergyWord -> Res.string.onboarding_preview_energy_word
        PreviewItem.CaloriesLater -> Res.string.onboarding_preview_calories_later
        PreviewItem.CaloriesWeekly -> Res.string.onboarding_preview_calories_weekly
        PreviewItem.CaloriesHidden -> Res.string.onboarding_preview_calories_hidden
        PreviewItem.MacroAmounts -> Res.string.onboarding_preview_macro_amounts
        PreviewItem.ProteinFiberAmounts -> Res.string.onboarding_preview_protein_fiber_amounts
        PreviewItem.SomeNutrientAmounts -> Res.string.onboarding_preview_some_nutrient_amounts
        PreviewItem.ProteinFiberBars -> Res.string.onboarding_preview_protein_fiber_bars
        PreviewItem.SomeNutrientBars -> Res.string.onboarding_preview_some_nutrient_bars
        PreviewItem.MacrosLater -> Res.string.onboarding_preview_macros_later
        PreviewItem.SomeNutrientsLater -> Res.string.onboarding_preview_some_nutrients_later
        PreviewItem.MacrosHidden -> Res.string.onboarding_preview_macros_hidden
        PreviewItem.CarbsFatHidden -> Res.string.onboarding_preview_carbs_fat_hidden
        PreviewItem.OtherNutrientsHidden -> Res.string.onboarding_preview_other_nutrients_hidden
        PreviewItem.MealCount -> Res.string.onboarding_preview_meal_count
        PreviewItem.WeightAmounts -> Res.string.onboarding_preview_weight_amounts
        PreviewItem.WeightTrend -> Res.string.onboarding_preview_weight_trend
        PreviewItem.WeightHidden -> Res.string.onboarding_preview_weight_hidden
        PreviewItem.MealByMeal -> Res.string.onboarding_preview_meal_by_meal
        PreviewItem.MealChecklist -> Res.string.onboarding_preview_meal_checklist
        PreviewItem.NewFoods -> Res.string.onboarding_preview_new_foods
        PreviewItem.HowMealsFelt -> Res.string.onboarding_preview_how_meals_felt
    }
