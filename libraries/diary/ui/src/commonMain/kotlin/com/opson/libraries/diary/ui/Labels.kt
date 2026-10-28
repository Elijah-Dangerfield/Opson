package com.opson.libraries.diary.ui

import com.opson.libraries.diary.DayHunger
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.diary_hunger_full
import opson.libraries.resources.generated.resources.diary_hunger_hungry
import opson.libraries.resources.generated.resources.diary_hunger_neutral
import opson.libraries.resources.generated.resources.diary_hunger_starving
import opson.libraries.resources.generated.resources.diary_hunger_stuffed
import opson.libraries.resources.generated.resources.diary_precision_picker_estimate
import opson.libraries.resources.generated.resources.diary_precision_picker_label
import opson.libraries.resources.generated.resources.diary_precision_picker_named
import opson.libraries.resources.generated.resources.diary_precision_picker_weighed
import opson.libraries.resources.generated.resources.diary_precision_row_estimate
import opson.libraries.resources.generated.resources.diary_precision_row_label
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
import opson.libraries.resources.generated.resources.today_checkin_hunger_often
import opson.libraries.resources.generated.resources.today_checkin_hunger_rarely
import opson.libraries.resources.generated.resources.today_checkin_hunger_some
import opson.libraries.resources.generated.resources.today_checkin_hunger_steady
import opson.libraries.resources.generated.resources.today_checkin_hunger_very
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

/** How sure, as an item row says it: Weighed / Estimate / Named / Label. */
val Precision.rowLabel: StringResource
    get() = when (this) {
        Precision.Weighed -> Res.string.diary_precision_row_weighed
        Precision.Estimate -> Res.string.diary_precision_row_estimate
        Precision.Named -> Res.string.diary_precision_row_named
        Precision.Label -> Res.string.diary_precision_row_label
    }

/** How sure, as the amount sheet asks it: Weighed / Estimated / Just the name. */
val Precision.pickerLabel: StringResource
    get() = when (this) {
        Precision.Weighed -> Res.string.diary_precision_picker_weighed
        Precision.Estimate -> Res.string.diary_precision_picker_estimate
        Precision.Named -> Res.string.diary_precision_picker_named
        Precision.Label -> Res.string.diary_precision_picker_label
    }

val Hunger.label: StringResource
    get() = when (this) {
        Hunger.Starving -> Res.string.diary_hunger_starving
        Hunger.Hungry -> Res.string.diary_hunger_hungry
        Hunger.Neutral -> Res.string.diary_hunger_neutral
        Hunger.Full -> Res.string.diary_hunger_full
        Hunger.Stuffed -> Res.string.diary_hunger_stuffed
    }

/** The day's check-in answer to "Hunger through the day": Rarely to Very. */
val DayHunger.label: StringResource
    get() = when (this) {
        DayHunger.Rarely -> Res.string.today_checkin_hunger_rarely
        DayHunger.Some -> Res.string.today_checkin_hunger_some
        DayHunger.Steady -> Res.string.today_checkin_hunger_steady
        DayHunger.Often -> Res.string.today_checkin_hunger_often
        DayHunger.Very -> Res.string.today_checkin_hunger_very
    }
