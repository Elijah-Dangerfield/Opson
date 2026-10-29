package com.opson.libraries.reminders

import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.medication_reminder_already
import opson.libraries.resources.generated.resources.medication_reminder_body_text
import opson.libraries.resources.generated.resources.medication_reminder_log
import opson.libraries.resources.generated.resources.medication_reminder_title
import opson.libraries.resources.generated.resources.reminders_note_already_ate
import opson.libraries.resources.generated.resources.reminders_note_breakfast
import opson.libraries.resources.generated.resources.reminders_note_dinner
import opson.libraries.resources.generated.resources.reminders_note_log_it
import opson.libraries.resources.generated.resources.reminders_note_lunch
import opson.libraries.resources.generated.resources.reminders_note_meal_body
import opson.libraries.resources.generated.resources.reminders_note_snack
import opson.libraries.resources.generated.resources.reminders_note_snooze
import opson.libraries.resources.generated.resources.reminders_note_water
import opson.libraries.resources.generated.resources.reminders_note_water_body
import org.jetbrains.compose.resources.StringResource

/** A notification's text before it is resolved: fixed resources only. */
data class ReminderText(
    val title: StringResource,
    val body: StringResource,
    val actions: List<Pair<ReminderAction, StringResource>>,
)

/**
 * The words on a reminder, by key (v3 `reminder-notification.png`). One voice, the plain one:
 * the warm variant went with the How we talk to you setting.
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

    /**
     * The buttons on a dose nudge. "Already did" logs the current dose without opening the app;
     * [doseActionsNoQuickLog] is for a medication with no current dose to log.
     */
    val doseActions: List<Pair<ReminderAction, StringResource>> = listOf(
        ReminderAction.AlreadyAte to Res.string.medication_reminder_already,
        ReminderAction.LogIt to Res.string.medication_reminder_log,
    )

    val doseActionsNoQuickLog: List<Pair<ReminderAction, StringResource>> = listOf(
        ReminderAction.LogIt to Res.string.medication_reminder_log,
    )

    /**
     * [quickLog] is [DoseNudge.quickLog]. A dose nudge never names the medication or the dose:
     * lock screens are public.
     */
    fun text(key: ReminderKey, quickLog: Boolean = true): ReminderText = when (key) {
        is ReminderKey.Meal -> ReminderText(
            title = mealTitle(key.slot),
            body = Res.string.reminders_note_meal_body,
            actions = mealActions,
        )
        is ReminderKey.Water -> ReminderText(
            title = Res.string.reminders_note_water,
            body = Res.string.reminders_note_water_body,
            actions = emptyList(),
        )
        is ReminderKey.Dose -> ReminderText(
            title = Res.string.medication_reminder_title,
            body = Res.string.medication_reminder_body_text,
            actions = if (quickLog) doseActions else doseActionsNoQuickLog,
        )
    }

    /**
     * Meal nudges follow the chosen level and Quiet mode; water is always the quiet kind. A dose
     * nudge follows them too, but fires whatever the level: its own switch is the person's choice,
     * so a level left Off (never chosen) makes it quiet rather than silent.
     */
    fun style(key: ReminderKey, settings: Settings): ReminderStyle = when {
        key is ReminderKey.Water -> ReminderStyle.Subtle
        settings.mealReminderLevel != EventLevel.Full -> ReminderStyle.Subtle
        settings.quiet -> ReminderStyle.FullQuiet
        else -> ReminderStyle.Full
    }

    private fun mealTitle(slot: MealSlot): StringResource = when (slot) {
        MealSlot.Breakfast -> Res.string.reminders_note_breakfast
        MealSlot.Lunch -> Res.string.reminders_note_lunch
        MealSlot.Snack -> Res.string.reminders_note_snack
        // Uncategorized never gets a reminder (MealReminders.isOn); dinner is the safe wording.
        MealSlot.Dinner, MealSlot.Uncategorized -> Res.string.reminders_note_dinner
    }
}
