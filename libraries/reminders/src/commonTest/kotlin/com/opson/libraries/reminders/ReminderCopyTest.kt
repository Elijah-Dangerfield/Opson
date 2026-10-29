package com.opson.libraries.reminders

import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The notification's words and how loud it is. That no number reaches the text is proved on the
 * rendered strings by `reminder-notification` in `CaloriesHiddenLeakTest`; here, the choices.
 */
class ReminderCopyTest {

    private val date = LocalDate(2026, 10, 25)

    @Test
    fun eachMealHasItsOwnTitle() {
        val titles = MealSlot.meals.map { ReminderCopy.text(ReminderKey.Meal(date, it)).title }
        assertEquals(4, titles.toSet().size)
    }

    @Test
    fun mealNudgesCarryV3sThreeButtons_waterNone() {
        val actions = ReminderCopy.text(ReminderKey.Meal(date, MealSlot.Dinner)).actions.map { it.first }
        assertEquals(listOf(ReminderAction.AlreadyAte, ReminderAction.LogIt, ReminderAction.Snooze), actions)
        assertEquals(emptyList(), ReminderCopy.text(ReminderKey.Water(date, 1)).actions)
    }

    @Test
    fun loudnessFollowsTheLevel_andQuietModeSilencesFull() {
        val dinner = ReminderKey.Meal(date, MealSlot.Dinner)
        val full = Settings(remindersChosen = true, remindersEvent = EventLevel.Full, quiet = false)

        assertEquals(ReminderStyle.Full, ReminderCopy.style(dinner, full))
        assertEquals(ReminderStyle.FullQuiet, ReminderCopy.style(dinner, full.copy(quiet = true)))
        assertEquals(ReminderStyle.Subtle, ReminderCopy.style(dinner, full.copy(remindersEvent = EventLevel.Subtle)))
        assertEquals(ReminderStyle.Subtle, ReminderCopy.style(ReminderKey.Water(date, 0), full))
    }

    @Test
    fun idsAreStableAndASnoozeGetsItsOwn() {
        val key = ReminderKey.Meal(date, MealSlot.Lunch)
        val at = kotlin.time.Instant.parse("2026-10-25T12:30:00Z")
        assertEquals("meal-2026-10-25-lunch", Reminder(key, at).id)
        assertEquals("meal-2026-10-25-lunch-snoozed", Reminder(key, at, snoozed = true).id)
        assertEquals("water-2026-10-25-1", ReminderKey.Water(date, 1).id)
    }
}
