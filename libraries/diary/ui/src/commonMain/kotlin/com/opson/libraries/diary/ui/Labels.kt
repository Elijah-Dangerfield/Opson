package com.opson.libraries.diary.ui

import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Mood
import com.opson.libraries.diary.Precision
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.diary_fullness_full
import opson.libraries.resources.generated.resources.diary_fullness_satisfied
import opson.libraries.resources.generated.resources.diary_fullness_still_hungry
import opson.libraries.resources.generated.resources.diary_fullness_very_full
import opson.libraries.resources.generated.resources.diary_hunger_hungry
import opson.libraries.resources.generated.resources.diary_hunger_not_hungry
import opson.libraries.resources.generated.resources.diary_hunger_peckish
import opson.libraries.resources.generated.resources.diary_hunger_very_hungry
import opson.libraries.resources.generated.resources.diary_mood_anxious
import opson.libraries.resources.generated.resources.diary_mood_calm
import opson.libraries.resources.generated.resources.diary_mood_happy
import opson.libraries.resources.generated.resources.diary_mood_low
import opson.libraries.resources.generated.resources.diary_mood_rushed
import opson.libraries.resources.generated.resources.diary_mood_social
import opson.libraries.resources.generated.resources.diary_mood_stressed
import opson.libraries.resources.generated.resources.diary_precision_picker_estimate
import opson.libraries.resources.generated.resources.diary_precision_picker_named
import opson.libraries.resources.generated.resources.diary_precision_picker_weighed
import opson.libraries.resources.generated.resources.diary_precision_row_estimate
import opson.libraries.resources.generated.resources.diary_precision_row_named
import opson.libraries.resources.generated.resources.diary_precision_row_weighed
import opson.libraries.resources.generated.resources.diary_slot_breakfast
import opson.libraries.resources.generated.resources.diary_slot_dinner
import opson.libraries.resources.generated.resources.diary_slot_lunch
import opson.libraries.resources.generated.resources.diary_slot_sentence_breakfast
import opson.libraries.resources.generated.resources.diary_slot_sentence_dinner
import opson.libraries.resources.generated.resources.diary_slot_sentence_lunch
import opson.libraries.resources.generated.resources.diary_slot_sentence_snack
import opson.libraries.resources.generated.resources.diary_slot_sentence_uncategorized
import opson.libraries.resources.generated.resources.diary_slot_snack
import opson.libraries.resources.generated.resources.diary_slot_uncategorized
import org.jetbrains.compose.resources.StringResource

/** The slot as a chip or a title: "Dinner". */
val MealSlot.label: StringResource
    get() = when (this) {
        MealSlot.Breakfast -> Res.string.diary_slot_breakfast
        MealSlot.Lunch -> Res.string.diary_slot_lunch
        MealSlot.Snack -> Res.string.diary_slot_snack
        MealSlot.Dinner -> Res.string.diary_slot_dinner
        MealSlot.Uncategorized -> Res.string.diary_slot_uncategorized
    }

/** The slot inside a sentence, lower case as the prototype writes it: "Added to dinner." */
val MealSlot.sentenceName: StringResource
    get() = when (this) {
        MealSlot.Breakfast -> Res.string.diary_slot_sentence_breakfast
        MealSlot.Lunch -> Res.string.diary_slot_sentence_lunch
        MealSlot.Snack -> Res.string.diary_slot_sentence_snack
        MealSlot.Dinner -> Res.string.diary_slot_sentence_dinner
        MealSlot.Uncategorized -> Res.string.diary_slot_sentence_uncategorized
    }

/** How sure, as an item row says it: Weighed / Estimate / Named. */
val Precision.rowLabel: StringResource
    get() = when (this) {
        Precision.Weighed -> Res.string.diary_precision_row_weighed
        Precision.Estimate -> Res.string.diary_precision_row_estimate
        Precision.Named -> Res.string.diary_precision_row_named
    }

/** How sure, as the amount sheet asks it: Weighed / Estimated / Just the name. */
val Precision.pickerLabel: StringResource
    get() = when (this) {
        Precision.Weighed -> Res.string.diary_precision_picker_weighed
        Precision.Estimate -> Res.string.diary_precision_picker_estimate
        Precision.Named -> Res.string.diary_precision_picker_named
    }

val Hunger.label: StringResource
    get() = when (this) {
        Hunger.VeryHungry -> Res.string.diary_hunger_very_hungry
        Hunger.Hungry -> Res.string.diary_hunger_hungry
        Hunger.Peckish -> Res.string.diary_hunger_peckish
        Hunger.NotHungry -> Res.string.diary_hunger_not_hungry
    }

val Fullness.label: StringResource
    get() = when (this) {
        Fullness.StillHungry -> Res.string.diary_fullness_still_hungry
        Fullness.Satisfied -> Res.string.diary_fullness_satisfied
        Fullness.Full -> Res.string.diary_fullness_full
        Fullness.VeryFull -> Res.string.diary_fullness_very_full
    }

val Mood.label: StringResource
    get() = when (this) {
        Mood.Calm -> Res.string.diary_mood_calm
        Mood.Happy -> Res.string.diary_mood_happy
        Mood.Social -> Res.string.diary_mood_social
        Mood.Rushed -> Res.string.diary_mood_rushed
        Mood.Stressed -> Res.string.diary_mood_stressed
        Mood.Anxious -> Res.string.diary_mood_anxious
        Mood.Low -> Res.string.diary_mood_low
    }
