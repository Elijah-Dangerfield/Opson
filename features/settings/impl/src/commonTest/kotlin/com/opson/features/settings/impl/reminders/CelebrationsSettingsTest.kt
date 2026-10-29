package com.opson.features.settings.impl.reminders

import com.opson.features.settings.impl.FakeNotificationPermissions
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.more.MoreRow
import com.opson.features.settings.impl.more.MoreStateBuilder
import com.opson.libraries.diary.CelebrationStyle
import com.opson.libraries.diary.CelebrationsRelease
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Celebrations on Reminders and celebrations (`release.celebrations`), and the More row that reaches them. */
class CelebrationsSettingsTest : CoroutineTest() {

    private fun vm(settings: Settings, on: Boolean = true): Pair<RemindersViewModel, FakeSettingsRepository> {
        val repository = FakeSettingsRepository(settings)
        return RemindersViewModel(repository, FakeNotificationPermissions(), CelebrationsRelease { on }) to repository
    }

    @Test
    fun withTheFlagOff_thePageHasNoCelebrationsRows() = runUnitTest {
        val (vm) = vm(SettingsFixtures.settings(), on = false)
        assertNull(vm.state.celebrations)
    }

    @Test
    fun onWithCounts_byDefault_andCountsOffAfterNumbersHaveBeenHard() = runUnitTest {
        assertEquals(CelebrationsChoice(on = true, counts = true), vm(SettingsFixtures.settings()).first.state.celebrations)
        val hard = SettingsFixtures.settings().copy(onboarding = OnboardingAnswers(hard = true))
        assertEquals(CelebrationsChoice(on = true, counts = false), vm(hard).first.state.celebrations)
    }

    @Test
    fun theSwitches_writeTheSyncedKeys_andTurnBackOn() = runUnitTest {
        val (vm, repository) = vm(SettingsFixtures.settings())

        vm.takeAction(RemindersAction.CelebrationsToggled)
        assertFalse(repository.state.value.celebrationsEnabled)
        assertEquals(CelebrationStyle.Off, repository.state.value.celebrationStyle)

        vm.takeAction(RemindersAction.CelebrationsToggled)
        assertTrue(repository.state.value.celebrationsEnabled)

        vm.takeAction(RemindersAction.CelebrationCountsToggled)
        assertEquals(false, repository.state.value.celebrationCounts)
        assertEquals(CelebrationsChoice(on = true, counts = false), vm.state.celebrations)
    }

    @Test
    fun afterNumbersHaveBeenHard_theCountsSwitch_turnsCountsOn() = runUnitTest {
        val (vm, repository) = vm(SettingsFixtures.settings().copy(onboarding = OnboardingAnswers(hard = true)))
        vm.takeAction(RemindersAction.CelebrationCountsToggled)
        assertEquals(true, repository.state.value.celebrationCounts)
        assertEquals(CelebrationStyle.Counts, repository.state.value.celebrationStyle)
    }

    @Test
    fun more_reachesCelebrations_withRemindersOff_andSaysWhetherTheyAreOn() {
        val settings = SettingsFixtures.settings()
        fun row(remindersOn: Boolean, celebrationsOn: Boolean, s: Settings = settings) =
            MoreStateBuilder.build(s, remindersOn = remindersOn, celebrationsOn = celebrationsOn).groups.flatten().filterIsInstance<MoreRow.Reminders>().singleOrNull()

        assertNull(row(remindersOn = false, celebrationsOn = false))
        assertEquals(MoreRow.Reminders(quiet = settings.quiet), row(remindersOn = true, celebrationsOn = true))
        assertEquals(MoreRow.Reminders(quiet = settings.quiet, celebrations = true), row(remindersOn = false, celebrationsOn = true))
        assertEquals(false, row(remindersOn = false, celebrationsOn = true, s = settings.copy(celebrationsEnabled = false))?.celebrations)
    }
}
