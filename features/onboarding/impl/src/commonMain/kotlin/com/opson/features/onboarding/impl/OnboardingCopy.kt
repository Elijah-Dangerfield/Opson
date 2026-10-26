package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.PresetId
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.onboarding_feel_love_them
import opson.libraries.resources.generated.resources.onboarding_feel_not_sure
import opson.libraries.resources.generated.resources.onboarding_feel_rather_not
import opson.libraries.resources.generated.resources.onboarding_feel_some_are_fine
import opson.libraries.resources.generated.resources.onboarding_hard_before
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
import opson.libraries.resources.generated.resources.onboarding_q_feel
import opson.libraries.resources.generated.resources.onboarding_q_hard
import opson.libraries.resources.generated.resources.onboarding_q_hard_sub
import opson.libraries.resources.generated.resources.onboarding_q_notice
import opson.libraries.resources.generated.resources.onboarding_q_why
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
        SetupQuestion.Hard -> Res.string.onboarding_q_hard
    }

internal val SetupQuestion.subtitle: StringResource?
    get() = when (this) {
        SetupQuestion.Reason, SetupQuestion.Focus -> Res.string.onboarding_pick_any
        SetupQuestion.Feeling -> null
        SetupQuestion.Hard -> Res.string.onboarding_q_hard_sub
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
        HardBefore -> Res.string.onboarding_hard_before
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

/** "What you'll see" for each mode. */
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
