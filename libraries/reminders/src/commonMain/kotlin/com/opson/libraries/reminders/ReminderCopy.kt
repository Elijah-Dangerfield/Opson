package com.opson.libraries.reminders

import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.Tone
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.reminders_note_already_ate
import opson.libraries.resources.generated.resources.reminders_note_breakfast
import opson.libraries.resources.generated.resources.reminders_note_breakfast_warm
import opson.libraries.resources.generated.resources.reminders_note_dinner
import opson.libraries.resources.generated.resources.reminders_note_dinner_warm
import opson.libraries.resources.generated.resources.reminders_note_log_it
import opson.libraries.resources.generated.resources.reminders_note_lunch
import opson.libraries.resources.generated.resources.reminders_note_lunch_warm
import opson.libraries.resources.generated.resources.reminders_note_meal_body
import opson.libraries.resources.generated.resources.reminders_note_meal_body_warm
import opson.libraries.resources.generated.resources.reminders_note_snack
import opson.libraries.resources.generated.resources.reminders_note_snack_warm
import opson.libraries.resources.generated.resources.reminders_note_snooze
import opson.libraries.resources.generated.resources.reminders_note_water
import opson.libraries.resources.generated.resources.reminders_note_water_body
import opson.libraries.resources.generated.resources.reminders_note_water_body_warm
import opson.libraries.resources.generated.resources.reminders_note_water_warm
import org.jetbrains.compose.resources.StringResource

/** A notification's text before it is resolved: fixed resources only. */
data class ReminderText(
    val title: StringResource,
    val body: StringResource,
    val actions: List<Pair<ReminderAction, StringResource>>,
)

/**
 * The words on a reminder, by key and tone (v3 `reminder-notification.png`).
 *
 * **This is a visibility surface with nothing to resolve.** Every string is a fixed resource
 * with no format argument, so no number, hidden or not, can reach a notification in any mode;
 * v3: "The same copy works in every mode, because it never mentions food amounts." A line that
 * ever needs a number takes a `VisibilityPolicy` and a `Display`, and `reminder-notification` in
 * `CaloriesHiddenLeakTest` is what catches one that does not.
 */
object ReminderCopy {

    /** The buttons on a meal nudge, in v3's order. Water has none: there is nothing to answer. */
    val mealActions: List<Pair<ReminderAction, StringResource>> = listOf(
        ReminderAction.AlreadyAte to Res.string.reminders_note_already_ate,
        ReminderAction.LogIt to Res.string.reminders_note_log_it,
        ReminderAction.Snooze to Res.string.reminders_note_snooze,
    )

    fun text(key: ReminderKey, tone: Tone): ReminderText {
        val warm = tone == Tone.Warm
        return when (key) {
            is ReminderKey.Meal -> ReminderText(
                title = mealTitle(key.slot, warm),
                body = if (warm) Res.string.reminders_note_meal_body_warm else Res.string.reminders_note_meal_body,
                actions = mealActions,
            )
            is ReminderKey.Water -> ReminderText(
                title = if (warm) Res.string.reminders_note_water_warm else Res.string.reminders_note_water,
                body = if (warm) Res.string.reminders_note_water_body_warm else Res.string.reminders_note_water_body,
                actions = emptyList(),
            )
        }
    }

    /** Meal nudges follow the chosen level and Quiet mode; water is always the quiet kind. */
    fun style(key: ReminderKey, settings: Settings): ReminderStyle = when {
        key is ReminderKey.Water -> ReminderStyle.Subtle
        settings.mealReminderLevel != EventLevel.Full -> ReminderStyle.Subtle
        settings.quiet -> ReminderStyle.FullQuiet
        else -> ReminderStyle.Full
    }

    private fun mealTitle(slot: MealSlot, warm: Boolean): StringResource = when (slot) {
        MealSlot.Breakfast -> if (warm) Res.string.reminders_note_breakfast_warm else Res.string.reminders_note_breakfast
        MealSlot.Lunch -> if (warm) Res.string.reminders_note_lunch_warm else Res.string.reminders_note_lunch
        MealSlot.Snack -> if (warm) Res.string.reminders_note_snack_warm else Res.string.reminders_note_snack
        // Uncategorized never gets a reminder (MealReminders.isOn); dinner is the safe wording.
        MealSlot.Dinner, MealSlot.Uncategorized ->
            if (warm) Res.string.reminders_note_dinner_warm else Res.string.reminders_note_dinner
    }
}
