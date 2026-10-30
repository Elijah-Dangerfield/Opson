package com.opson.features.settings.impl.reminders

import com.opson.features.settings.impl.FakeNotificationPermissions
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.HabitsSource
import com.opson.libraries.diary.LoggingHabits
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.Tone
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.reminders.NotificationPermission
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reminders and celebrations: the page's state and every action. What the writes schedule is
 * `ReminderCoordinatorTest` and `ReminderPlannerTest` in `:libraries:reminders`.
 */
class RemindersTest : CoroutineTest() {

    private fun vm(
        settings: Settings = SettingsFixtures.settings(),
        permissions: FakeNotificationPermissions = FakeNotificationPermissions(),
    ): Triple<RemindersViewModel, FakeSettingsRepository, FakeNotificationPermissions> {
        val repository = FakeSettingsRepository(settings)
        return Triple(RemindersViewModel(repository, permissions), repository, permissions)
    }

    // The builder

    @Test
    fun neverChosen_mealRemindersReadOff_andEveryMealRowOff() {
        // 0.1 synced Subtle and three meals on for everyone; none of it was chosen.
        val state = RemindersStateBuilder.build(SettingsFixtures.settings())

        assertEquals(EventLevel.Off, state.mealLevel)
        assertTrue(state.meals.none { it.on })
        assertEquals(MealSlot.meals, state.meals.map { it.slot })
    }

    @Test
    fun theDesignsSettingsReadAsTheDesign() {
        val state = RemindersStateBuilder.build(SettingsFixtures.remindersSet)

        assertEquals(EventLevel.Subtle, state.mealLevel)
        assertEquals(listOf(true, true, false, true), state.meals.map { it.on })
        assertEquals(listOf("8:30 AM", "1:00 PM", "3:30 PM", "7:15 PM"), state.meals.map { it.timeLabel })
        assertTrue(state.quiet)
        assertEquals(EventLevel.Full, state.bigDay)
    }

    private val learned = LoggingHabits(
        usual = MealTimes(breakfast = LocalTime(7, 15), lunch = LocalTime(12, 45), snack = MealTimes.DEFAULT_SNACK, dinner = LocalTime(18, 40)),
        learned = setOf(MealSlot.Breakfast, MealSlot.Lunch, MealSlot.Dinner),
    )

    @Test
    fun turningRemindersOnTheFirstTime_startsFromTheTimesThisPersonUsuallyLogsAt() = runUnitTest {
        val repository = FakeSettingsRepository(Settings())
        val vm = RemindersViewModel(repository, FakeNotificationPermissions(), habits = HabitsSource { learned })
        assertTrue(vm.state.timesLearned, "the rows already show the learned times")
        assertEquals("7:15 AM", vm.state.meals.first().timeLabel)

        vm.takeAction(RemindersAction.MealLevelPicked(EventLevel.Subtle))

        assertEquals(learned.usual, repository.settings.value.mealTimes)
        assertTrue(vm.state.timesLearned)
    }

    @Test
    fun timesThePersonSetThemselves_areNeverReplaced() = runUnitTest {
        val own = Settings(mealTimes = MealTimes(breakfast = LocalTime(9, 0)))
        val repository = FakeSettingsRepository(own)
        val vm = RemindersViewModel(repository, FakeNotificationPermissions(), habits = HabitsSource { learned })
        assertFalse(vm.state.timesLearned)

        vm.takeAction(RemindersAction.MealToggled(MealSlot.Breakfast))

        assertEquals(own.mealTimes, repository.settings.value.mealTimes)
    }

    @Test
    fun onceRemindersWereChosen_learningNeverMovesTheTimes() {
        val chosen = Settings(remindersChosen = true)
        assertEquals(null, RemindersStateBuilder.suggestedTimes(chosen, learned))
        assertEquals(null, RemindersStateBuilder.suggestedTimes(Settings(), LoggingHabits()), "nothing learned yet")
    }

    @Test
    fun thePermissionNoteShowsOnlyWhenSomethingIsOnAndTheSystemSaidNo() {
        val denied = NotificationPermission.Denied
        assertTrue(RemindersStateBuilder.build(SettingsFixtures.remindersSet, permission = denied).showPermissionNote)
        assertFalse(RemindersStateBuilder.build(SettingsFixtures.settings(), permission = denied).showPermissionNote)
        assertFalse(RemindersStateBuilder.build(SettingsFixtures.remindersSet, permission = NotificationPermission.NotAsked).showPermissionNote)
    }

    @Test
    fun thePageIsTheSameInEveryMode() {
        val base = RemindersStateBuilder.build(SettingsFixtures.remindersSet)
        PresetId.entries.forEach { preset ->
            val settings = SettingsFixtures.remindersSet.copy(numbers = NumbersSettings(preset))
            assertEquals(base, RemindersStateBuilder.build(settings), "in $preset")
        }
    }

    @Test
    fun theOldVoiceAndCheckInSwitches_changeNothingOnThePage() {
        // Tone, the hunger questions and the day-close check-in are not settings any more; a
        // value stored before they went must not move the page.
        val legacy = SettingsFixtures.remindersSet.copy(tone = Tone.Warm, askTags = false, checkIn = false)
        assertEquals(RemindersStateBuilder.build(SettingsFixtures.remindersSet), RemindersStateBuilder.build(legacy))
    }

    // The view model

    @Test
    fun settingsAndPermissionArriveAsState() = runUnitTest {
        val (vm, repository) = vm(permissions = FakeNotificationPermissions(status = NotificationPermission.Denied))

        repository.state.value = SettingsFixtures.remindersSet

        assertEquals(RemindersStateBuilder.build(SettingsFixtures.remindersSet, NotificationPermission.Denied), vm.state)
    }

    @Test
    fun openingThePageNeverAsksForPermission() = runUnitTest {
        val (vm, _, permissions) = vm()
        vm.takeAction(RemindersAction.ScreenResumed)
        assertEquals(0, permissions.prompts)
        assertEquals(NotificationPermission.NotAsked, vm.state.permission)
    }

    @Test
    fun pickingALevelChoosesIt_andTurningOnAsksForPermission() = runUnitTest {
        val (vm, repository, permissions) = vm()

        vm.takeAction(RemindersAction.MealLevelPicked(EventLevel.Full))

        assertTrue(repository.state.value.remindersChosen)
        assertEquals(EventLevel.Full, repository.state.value.mealReminderLevel)
        assertEquals(1, permissions.prompts)
        assertEquals(NotificationPermission.Granted, vm.state.permission)
        assertEquals(listOf(true, true, false, true), vm.state.meals.map { it.on })
    }

    @Test
    fun pickingOffAsksForNothing_andIsStillAChoice() = runUnitTest {
        val (vm, repository, permissions) = vm()

        vm.takeAction(RemindersAction.MealLevelPicked(EventLevel.Off))

        assertTrue(repository.state.value.remindersChosen)
        assertEquals(EventLevel.Off, repository.state.value.mealReminderLevel)
        assertEquals(0, permissions.prompts)
    }

    @Test
    fun aDenialKeepsTheChoice_andShowsTheNote() = runUnitTest {
        val (vm, repository) = vm(permissions = FakeNotificationPermissions(answer = NotificationPermission.Denied))

        vm.takeAction(RemindersAction.MealLevelPicked(EventLevel.Subtle))

        assertEquals(EventLevel.Subtle, repository.state.value.mealReminderLevel)
        assertTrue(vm.state.showPermissionNote)
    }

    @Test
    fun turningAMealOnWhileOffTurnsRemindersOnAtSubtle() = runUnitTest {
        val (vm, repository, permissions) = vm()

        vm.takeAction(RemindersAction.MealToggled(MealSlot.Snack))

        val settings = repository.state.value
        assertEquals(EventLevel.Subtle, settings.mealReminderLevel)
        assertTrue(settings.mealReminders.isOn(MealSlot.Snack))
        assertEquals(1, permissions.prompts)
        assertTrue(vm.state.meals.single { it.slot == MealSlot.Snack }.on)
    }

    @Test
    fun turningAMealOffLeavesTheLevel_andAsksNothing() = runUnitTest {
        val (vm, repository, permissions) = vm(SettingsFixtures.remindersSet, FakeNotificationPermissions(NotificationPermission.Granted))

        vm.takeAction(RemindersAction.MealToggled(MealSlot.Dinner))

        assertFalse(repository.state.value.mealReminders.isOn(MealSlot.Dinner))
        assertEquals(EventLevel.Subtle, repository.state.value.mealReminderLevel)
        assertEquals(0, permissions.prompts)
    }

    @Test
    fun aMealTimeIsPickedInTheDialog_andTheSameTimeWritesNothing() = runUnitTest {
        val (vm, repository) = vm(SettingsFixtures.remindersSet)

        vm.takeAction(RemindersAction.MealTimeTapped(MealSlot.Dinner))
        assertEquals(MealSlot.Dinner, vm.state.pickingTime)
        assertEquals(LocalTime(19, 15), vm.state.pickingRow?.time)

        vm.takeAction(RemindersAction.MealTimePicked(LocalTime(18, 45)))
        assertEquals(LocalTime(18, 45), repository.state.value.mealTimes.dinner)
        assertEquals(null, vm.state.pickingTime)

        val before = repository.writes
        vm.takeAction(RemindersAction.MealTimeTapped(MealSlot.Dinner))
        vm.takeAction(RemindersAction.MealTimePicked(LocalTime(18, 45)))
        assertEquals(before, repository.writes)

        vm.takeAction(RemindersAction.MealTimeTapped(MealSlot.Lunch))
        vm.takeAction(RemindersAction.MealTimeDismissed)
        assertEquals(null, vm.state.pickingTime)
        assertEquals(LocalTime(13, 0), repository.state.value.mealTimes.lunch)
    }

    @Test
    fun quietAndBigDayArePlainWrites() = runUnitTest {
        val (vm, repository, permissions) = vm()

        vm.takeAction(RemindersAction.QuietToggled)
        vm.takeAction(RemindersAction.BigDayPicked(EventLevel.Subtle))

        val settings = repository.state.value
        assertFalse(settings.quiet)
        assertEquals(EventLevel.Subtle, settings.bigDayEvent)
        assertEquals(0, permissions.prompts)
    }

    @Test
    fun openSettingsOpensTheSystemPage_andComingBackRechecks() = runUnitTest {
        val permissions = FakeNotificationPermissions(status = NotificationPermission.Denied)
        val (vm) = vm(SettingsFixtures.remindersSet, permissions)

        vm.takeAction(RemindersAction.OpenSettingsTapped)
        assertEquals(1, permissions.settingsOpened)

        permissions.status = NotificationPermission.Granted
        vm.takeAction(RemindersAction.ScreenResumed)
        assertFalse(vm.state.showPermissionNote)
    }

    @Test
    fun backCloses() = runUnitTest {
        val (vm) = vm()
        val events = eventsOf(vm)

        vm.takeAction(RemindersAction.BackTapped)

        assertEquals(listOf<RemindersEvent>(RemindersEvent.Close), events)
    }
}
