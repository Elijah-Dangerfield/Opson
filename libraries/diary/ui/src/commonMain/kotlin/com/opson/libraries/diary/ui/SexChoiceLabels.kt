package com.opson.libraries.diary.ui

import com.opson.libraries.nutrition.SexChoice
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.plan_sex_breastfeeding
import opson.libraries.resources.generated.resources.plan_sex_female
import opson.libraries.resources.generated.resources.plan_sex_male
import opson.libraries.resources.generated.resources.plan_sex_note_binary
import opson.libraries.resources.generated.resources.plan_sex_note_breastfeeding
import opson.libraries.resources.generated.resources.plan_sex_note_pregnant
import opson.libraries.resources.generated.resources.plan_sex_note_unsaid
import opson.libraries.resources.generated.resources.plan_sex_pregnant
import opson.libraries.resources.generated.resources.plan_sex_unsaid
import org.jetbrains.compose.resources.StringResource

/** The choice's name, the same in onboarding and on Profile. */
val SexChoice.label: StringResource
    get() = when (this) {
        SexChoice.Female -> Res.string.plan_sex_female
        SexChoice.Male -> Res.string.plan_sex_male
        SexChoice.Pregnant -> Res.string.plan_sex_pregnant
        SexChoice.Breastfeeding -> Res.string.plan_sex_breastfeeding
        SexChoice.Unsaid -> Res.string.plan_sex_unsaid
    }

/** What picking it does, honestly: it only changes the estimate, and pregnancy keeps weight change out of the plan. */
val SexChoice.note: StringResource
    get() = when (this) {
        SexChoice.Female, SexChoice.Male -> Res.string.plan_sex_note_binary
        SexChoice.Pregnant -> Res.string.plan_sex_note_pregnant
        SexChoice.Breastfeeding -> Res.string.plan_sex_note_breastfeeding
        SexChoice.Unsaid -> Res.string.plan_sex_note_unsaid
    }
