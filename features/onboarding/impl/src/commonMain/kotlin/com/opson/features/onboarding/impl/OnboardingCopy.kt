package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.PresetId
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.modes_preset_full
import opson.libraries.resources.generated.resources.modes_preset_full_line
import opson.libraries.resources.generated.resources.modes_preset_macros_grams
import opson.libraries.resources.generated.resources.modes_preset_macros_grams_line
import opson.libraries.resources.generated.resources.modes_preset_macros_only
import opson.libraries.resources.generated.resources.modes_preset_macros_only_line
import opson.libraries.resources.generated.resources.modes_preset_no_numbers
import opson.libraries.resources.generated.resources.modes_preset_no_numbers_line
import opson.libraries.resources.generated.resources.onboarding_feel_intro
import opson.libraries.resources.generated.resources.onboarding_feel_love_them
import opson.libraries.resources.generated.resources.onboarding_feel_not_sure
import opson.libraries.resources.generated.resources.onboarding_feel_rather_not
import opson.libraries.resources.generated.resources.onboarding_feel_some_are_fine
import opson.libraries.resources.generated.resources.onboarding_pick_any
import opson.libraries.resources.generated.resources.onboarding_preview_calorie_amounts
import opson.libraries.resources.generated.resources.onboarding_preview_calories_hidden
import opson.libraries.resources.generated.resources.onboarding_preview_calories_later
import opson.libraries.resources.generated.resources.onboarding_preview_carbs_fat_hidden
import opson.libraries.resources.generated.resources.onboarding_preview_energy_word
import opson.libraries.resources.generated.resources.onboarding_preview_how_meals_felt
import opson.libraries.resources.generated.resources.onboarding_preview_macro_amounts
import opson.libraries.resources.generated.resources.onboarding_preview_macro_bars
import opson.libraries.resources.generated.resources.onboarding_preview_macros_hidden
import opson.libraries.resources.generated.resources.onboarding_preview_macros_later
import opson.libraries.resources.generated.resources.onboarding_preview_meal_by_meal
import opson.libraries.resources.generated.resources.onboarding_preview_meal_count
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
import opson.libraries.resources.generated.resources.onboarding_q_statement
import opson.libraries.resources.generated.resources.onboarding_q_why
import opson.libraries.resources.generated.resources.onboarding_reason_gentler
import opson.libraries.resources.generated.resources.onboarding_reason_loves_numbers
import opson.libraries.resources.generated.resources.onboarding_reason_no_answers
import opson.libraries.resources.generated.resources.onboarding_reason_not_sure
import opson.libraries.resources.generated.resources.onboarding_reason_rather_not
import opson.libraries.resources.generated.resources.onboarding_reason_some_are_fine
import opson.libraries.resources.generated.resources.onboarding_statement_no
import opson.libraries.resources.generated.resources.onboarding_statement_yes
import opson.libraries.resources.generated.resources.onboarding_why_changing_weight
import opson.libraries.resources.generated.resources.onboarding_why_curious
import opson.libraries.resources.generated.resources.onboarding_why_eating_enough
import opson.libraries.resources.generated.resources.onboarding_why_eating_regularly
import opson.libraries.resources.generated.resources.onboarding_why_medical_need
import opson.libraries.resources.generated.resources.onboarding_why_performance
import opson.libraries.resources.generated.resources.onboarding_why_working_with_provider
import opson.libraries.resources.generated.resources.settings_targets_style_deficit
import opson.libraries.resources.generated.resources.settings_targets_style_deficit_line
import opson.libraries.resources.generated.resources.settings_targets_style_exact
import opson.libraries.resources.generated.resources.settings_targets_style_range
import opson.libraries.resources.generated.resources.settings_targets_style_surplus
import opson.libraries.resources.generated.resources.settings_targets_style_surplus_line
import org.jetbrains.compose.resources.StringResource

internal val SetupQuestion.title: StringResource
    get() = when (this) {
        SetupQuestion.Reason -> Res.string.onboarding_q_why
        SetupQuestion.Feeling -> Res.string.onboarding_q_feel
        SetupQuestion.Statement -> Res.string.onboarding_q_statement
    }

/** The line under the title. The statement has its own quote and body instead. */
internal val SetupQuestion.subtitle: StringResource?
    get() = when (this) {
        SetupQuestion.Reason -> Res.string.onboarding_pick_any
        SetupQuestion.Feeling -> Res.string.onboarding_feel_intro
        SetupQuestion.Statement -> null
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
        StatementAnswer.Agree -> Res.string.onboarding_statement_yes
        StatementAnswer.NotReally -> Res.string.onboarding_statement_no
    }

internal val PresetId.label: StringResource
    get() = when (this) {
        PresetId.Full -> Res.string.modes_preset_full
        PresetId.MacrosWithGrams -> Res.string.modes_preset_macros_grams
        PresetId.MacrosOnly -> Res.string.modes_preset_macros_only
        PresetId.NoNumbers -> Res.string.modes_preset_no_numbers
    }

/** The line under the mode's name on the recommendation step. */
internal val PresetId.line: StringResource
    get() = when (this) {
        PresetId.Full -> Res.string.modes_preset_full_line
        PresetId.MacrosWithGrams -> Res.string.modes_preset_macros_grams_line
        PresetId.MacrosOnly -> Res.string.modes_preset_macros_only_line
        PresetId.NoNumbers -> Res.string.modes_preset_no_numbers_line
    }

internal val PreviewItem.label: StringResource
    get() = when (this) {
        PreviewItem.CalorieAmounts -> Res.string.onboarding_preview_calorie_amounts
        PreviewItem.EnergyWord -> Res.string.onboarding_preview_energy_word
        PreviewItem.CaloriesLater -> Res.string.onboarding_preview_calories_later
        PreviewItem.CaloriesHidden -> Res.string.onboarding_preview_calories_hidden
        PreviewItem.MacroAmounts -> Res.string.onboarding_preview_macro_amounts
        PreviewItem.ProteinFiberAmounts -> Res.string.onboarding_preview_protein_fiber_amounts
        PreviewItem.SomeNutrientAmounts -> Res.string.onboarding_preview_some_nutrient_amounts
        PreviewItem.MacroBars -> Res.string.onboarding_preview_macro_bars
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
        PreviewItem.HowMealsFelt -> Res.string.onboarding_preview_how_meals_felt
    }

internal val RecommendationReason.line: StringResource
    get() = when (this) {
        RecommendationReason.NoAnswers -> Res.string.onboarding_reason_no_answers
        RecommendationReason.LovesNumbers -> Res.string.onboarding_reason_loves_numbers
        RecommendationReason.SomeAreFine -> Res.string.onboarding_reason_some_are_fine
        RecommendationReason.RatherNot -> Res.string.onboarding_reason_rather_not
        RecommendationReason.Gentler -> Res.string.onboarding_reason_gentler
        RecommendationReason.NotSure -> Res.string.onboarding_reason_not_sure
    }

/** Targets' own names for the goal styles, so the two screens say the same thing. */
internal val GoalChoice.label: StringResource
    get() = when (this) {
        GoalChoice.Range -> Res.string.settings_targets_style_range
        GoalChoice.OneNumber -> Res.string.settings_targets_style_exact
        GoalChoice.Deficit -> Res.string.settings_targets_style_deficit
        GoalChoice.Surplus -> Res.string.settings_targets_style_surplus
    }

/** The line under a goal style. The range's is its amount, which the step resolves through the policy. */
internal val GoalChoice.line: StringResource?
    get() = when (this) {
        GoalChoice.Range, GoalChoice.OneNumber -> null
        GoalChoice.Deficit -> Res.string.settings_targets_style_deficit_line
        GoalChoice.Surplus -> Res.string.settings_targets_style_surplus_line
    }

