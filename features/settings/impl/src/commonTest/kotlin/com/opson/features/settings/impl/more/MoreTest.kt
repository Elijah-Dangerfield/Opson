package com.opson.features.settings.impl.more

import com.opson.features.settings.MoreRowContribution
import com.opson.features.settings.impl.FakeAuthRepository
import com.opson.features.settings.impl.FakeDiarySync
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.goalsAndEnergyFlag
import com.opson.features.settings.impl.integrations.FakeHealthRepository
import com.opson.features.settings.impl.integrations.healthFlag
import com.opson.features.settings.impl.remindersFlag
import com.opson.features.settings.impl.widgetsFlag
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.navigation.Route
import com.opson.libraries.nutrition.FastingPlan
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.components.icon.AppIcons
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_more_support
import opson.libraries.resources.generated.resources.settings_more_support_sub
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The More list (design v3): its three cards then About, Account and other modules' rows, the changes
 * count on the Display row, the water goal, log out, and where each row goes. No row carries an
 * amount, so nothing here reads the policy. Not covered: the routes the events become (the entry
 * point).
 */
class MoreTest : CoroutineTest() {

    private fun build(numbers: NumbersSettings = NumbersSettings()) = MoreStateBuilder.build(SettingsFixtures.settings(numbers))

    @Test
    fun theRowsAreTheDesignsThreeCards_thenAbout() {
        assertEquals(
            listOf(
                listOf(MoreRow.Display(PresetId.Full, changes = 0), MoreRow.Targets),
                listOf(MoreRow.Water(glasses = 8)),
                listOf(MoreRow.Support, MoreRow.Plan),
                listOf(MoreRow.Privacy, MoreRow.Terms, MoreRow.Credits),
            ),
            build().groups,
        )
    }

    @Test
    fun signedIn_anAccountCardFollowsAbout_andContributedRowsComeLast() {
        val account = MoreAccount(email = "sam@example.com", guest = false)
        val groups = MoreStateBuilder.build(SettingsFixtures.settings(), account = account, contributed = listOf(contribution)).groups

        assertEquals(listOf(MoreRow.LogOut(account)), groups[4])
        assertEquals(listOf<MoreRow>(MoreRow.Contributed(contribution)), groups[5])
        assertEquals(listOf(null, null, null, MoreSection.About, MoreSection.Account, null), groups.map { it.section() })
    }

    @Test
    fun theAccountCardFollowsTheSession() = runUnitTest {
        val auth = FakeAuthRepository(initial = AuthState.Authenticated(userId = "u1", isAnonymous = true, email = null))
        val vm = moreVm(auth = auth)
        assertEquals(MoreRow.LogOut(MoreAccount(email = null, guest = true)), vm.state.groups.flatten().filterIsInstance<MoreRow.LogOut>().single())

        auth.state.value = AuthState.Unauthenticated()

        assertTrue(vm.state.groups.flatten().none { it is MoreRow.LogOut })
    }

    @Test
    fun logOut_asksFirst_thenSignsOut_andGoesToOnboarding() = runUnitTest {
        val auth = FakeAuthRepository(initial = AuthState.Authenticated(userId = "u1", isAnonymous = false, email = "sam@example.com"))
        val vm = moreVm(auth = auth)
        val events = eventsOf(vm)
        val row = vm.state.groups.flatten().filterIsInstance<MoreRow.LogOut>().single()

        vm.takeAction(MoreAction.RowTapped(row))
        assertEquals(LogOutStep.Confirming(guest = false), vm.state.logOut)
        assertEquals(0, auth.signOuts, "nothing happens before the confirmation")

        vm.takeAction(MoreAction.LogOutConfirmed)

        assertEquals(1, auth.signOuts)
        assertEquals(LogOutStep.Idle, vm.state.logOut)
        assertEquals(listOf<MoreEvent>(MoreEvent.GoToOnboarding), events)
    }

    @Test
    fun logOut_cancelled_signsNothingOut() = runUnitTest {
        val auth = FakeAuthRepository(initial = AuthState.Authenticated(userId = "u1", isAnonymous = true, email = null))
        val vm = moreVm(auth = auth)

        vm.takeAction(MoreAction.RowTapped(vm.state.groups.flatten().filterIsInstance<MoreRow.LogOut>().single()))
        assertEquals(LogOutStep.Confirming(guest = true), vm.state.logOut, "a guest is warned their log stays behind")
        vm.takeAction(MoreAction.LogOutCancelled)
        vm.takeAction(MoreAction.LogOutConfirmed)

        assertEquals(LogOutStep.Idle, vm.state.logOut)
        assertEquals(0, auth.signOuts)
    }

    @Test
    fun noContributions_noExtraCard() = runUnitTest {
        assertTrue(moreVm().state.groups.flatten().none { it is MoreRow.Contributed })
    }

    @Test
    fun aContributedRow_showsWhileVisible_andOpensItsRoute() = runUnitTest {
        assertTrue(moreVm(contribution.copy(visible = false)).state.groups.flatten().none { it is MoreRow.Contributed })

        val vm = moreVm(contribution)
        val events = eventsOf(vm)
        val row = vm.state.groups.flatten().single { it is MoreRow.Contributed }
        vm.takeAction(MoreAction.RowTapped(row))

        assertEquals(listOf<MoreEvent>(MoreEvent.OpenContributed(contribution.route)), events)
    }

    @Test
    fun theVersionLine_namesTheCommitOffTheStoreOnly() {
        assertEquals("0.2.0 (702)", MoreVersion.line("0.2.0", 702, "store", "4a8b46391234"))
        assertEquals("0.2.0 (702) · beta · 4a8b463", MoreVersion.line("0.2.0", 702, "beta", "4a8b46391234"))
    }

    private fun moreVm(
        vararg contributions: MoreRowContribution,
        auth: FakeAuthRepository = FakeAuthRepository(),
        sync: FakeDiarySync = FakeDiarySync(),
    ) = MoreViewModel(
        FakeSettingsRepository(SettingsFixtures.settings()), FakeHealthRepository(), healthFlag(on = false), remindersFlag(),
        FastingRelease { false }, widgetsFlag(), goalsAndEnergyFlag(), auth, sync, contributions.toSet(),
    )

    private fun signedIn() = FakeAuthRepository(initial = AuthState.Authenticated(userId = "u1", isAnonymous = false, email = "sam@example.com"))

    private fun MoreViewModel.confirmLogOut() {
        takeAction(MoreAction.RowTapped(state.groups.flatten().filterIsInstance<MoreRow.LogOut>().single()))
        takeAction(MoreAction.LogOutConfirmed)
    }

    // --- Changes still on the phone when logging out (journeys audit) ---

    @Test
    fun logOut_sendsWhatIsQueued_beforeSigningOut() = runUnitTest {
        val auth = signedIn()
        val sync = FakeDiarySync()
        val vm = moreVm(auth = auth, sync = sync)

        vm.confirmLogOut()

        assertEquals(1, sync.syncs)
        assertEquals(1, auth.signOuts)
    }

    @Test
    fun logOut_whenTheAccountCantBeReached_asksAgain_andWipesNothingYet() = runUnitTest {
        val auth = signedIn()
        val vm = moreVm(auth = auth, sync = FakeDiarySync(Result.failure(IllegalStateException("offline"))))
        val events = eventsOf(vm)

        vm.confirmLogOut()

        assertEquals(LogOutStep.Unsynced, vm.state.logOut)
        assertEquals(0, auth.signOuts, "signing out wipes the outbox, so it waits for a second yes")
        assertTrue(events.isEmpty())

        vm.takeAction(MoreAction.LogOutConfirmed)

        assertEquals(1, auth.signOuts)
        assertEquals(LogOutStep.Idle, vm.state.logOut)
        assertEquals(listOf<MoreEvent>(MoreEvent.GoToOnboarding), events)
    }

    /** A day further ahead than the server takes yet stays queued however well the sync went. */
    @Test
    fun logOut_aSyncThatLeavesChangesQueued_asksAgain_andWipesNothingYet() = runUnitTest {
        val auth = signedIn()
        val vm = moreVm(auth = auth, sync = FakeDiarySync().apply { pending = 2 })

        vm.confirmLogOut()

        assertEquals(LogOutStep.Unsynced, vm.state.logOut)
        assertEquals(0, auth.signOuts)
    }

    @Test
    fun logOut_unreached_stayKeepsEverything() = runUnitTest {
        val auth = signedIn()
        val vm = moreVm(auth = auth, sync = FakeDiarySync(Result.failure(IllegalStateException("offline"))))

        vm.confirmLogOut()
        vm.takeAction(MoreAction.LogOutCancelled)

        assertEquals(LogOutStep.Idle, vm.state.logOut)
        assertEquals(0, auth.signOuts)
    }

    @Test
    fun logOut_aSyncThatHangs_isGivenUpOn_andAsksAgain() = runUnitTest {
        val auth = signedIn()
        val vm = moreVm(auth = auth, sync = FakeDiarySync(hangs = true))

        vm.confirmLogOut()
        assertEquals(LogOutStep.Working(guest = false), vm.state.logOut)

        advanceTimeBy(11.seconds)

        assertEquals(LogOutStep.Unsynced, vm.state.logOut)
        assertEquals(0, auth.signOuts)
    }

    @Test
    fun logOut_backPressedWhileItSends_stillShowsTheWarning() = runUnitTest {
        val auth = signedIn()
        val vm = moreVm(auth = auth, sync = FakeDiarySync(hangs = true))

        vm.confirmLogOut()
        // The dialog's own dismiss (back, or a tap outside) while the buttons are off.
        vm.takeAction(MoreAction.LogOutCancelled)
        advanceTimeBy(11.seconds)

        assertEquals(LogOutStep.Unsynced, vm.state.logOut, "the warning is read, not swallowed by a queued cancel")
        assertEquals(0, auth.signOuts)
    }

    @Test
    fun logOut_anyway_keepsSayingWhatIsLost_whileItSignsOut() = runUnitTest {
        val auth = signedIn().apply { signOutHangs = true }
        val vm = moreVm(auth = auth, sync = FakeDiarySync(Result.failure(IllegalStateException("offline"))))

        vm.confirmLogOut()
        vm.takeAction(MoreAction.LogOutConfirmed)

        assertTrue(vm.state.logOut is LogOutStep.Working)
        assertEquals(LogOutCopy.Unsynced, LogOutCopy.of(vm.state.logOut), "not \"Your log stays in your account\"")
    }

    @Test
    fun logOut_asAGuest_sendsNothing_sinceNoAccountCanBeReachedAgain() = runUnitTest {
        val auth = FakeAuthRepository(initial = AuthState.Authenticated(userId = "u1", isAnonymous = true, email = null))
        val sync = FakeDiarySync(Result.failure(IllegalStateException("offline")))
        val vm = moreVm(auth = auth, sync = sync)

        vm.confirmLogOut()

        assertEquals(0, sync.syncs)
        assertEquals(1, auth.signOuts)
    }

    private val contribution = FakeContribution()

    private data class FakeContribution(val visible: Boolean = true, val route: Route = Route()) : MoreRowContribution {
        override val section = Res.string.settings_more_support
        override val title = Res.string.settings_more_support
        override val subtitle = Res.string.settings_more_support_sub
        override val icon = AppIcons.Support
        override fun route(): Route = route
        override fun visible(): Boolean = visible
    }

    @Test
    fun theDisplayRowCountsTheChanges() {
        assertEquals(MoreRow.Display(PresetId.MacrosOnly, changes = 1), build(SettingsFixtures.nutrientsFirstWithACarbsChange).groups[0][0])
        assertEquals(MoreRow.Display(PresetId.Full, changes = 2), build(SettingsFixtures.adjusted).groups[0][0])
    }

    @Test
    fun theListIsTheSameInEveryMode() {
        val rows = build().groups.flatten().map { it::class }
        PresetId.entries.forEach { preset ->
            assertEquals(rows, build(NumbersSettings(preset)).groups.flatten().map { it::class }, "in $preset")
        }
    }

    @Test
    fun settingsArriveAsState() = runUnitTest {
        val settings = FakeSettingsRepository(SettingsFixtures.settings())
        val vm = MoreViewModel(settings, FakeHealthRepository(), healthFlag(on = false), remindersFlag(), FastingRelease { false }, widgetsFlag(), goalsAndEnergyFlag(), FakeAuthRepository(), FakeDiarySync())

        settings.state.value = settings.state.value.copy(waterGlasses = 10)

        assertEquals(MoreRow.Water(glasses = 10), vm.state.groups[1].single())
    }

    @Test
    fun eachRowOpensItsPage_andPlanOpensNothing() = runUnitTest {
        val vm = MoreViewModel(FakeSettingsRepository(), FakeHealthRepository(), healthFlag(on = true), remindersFlag(), FastingRelease { false }, widgetsFlag(), goalsAndEnergyFlag(), FakeAuthRepository(), FakeDiarySync())
        val events = eventsOf(vm)

        vm.state.groups.flatten().forEach { vm.takeAction(MoreAction.RowTapped(it)) }

        assertEquals(
            listOf(
                MoreEvent.OpenDisplay, MoreEvent.OpenTargets, MoreEvent.OpenHealth,
                MoreEvent.OpenWater, MoreEvent.OpenSupport, MoreEvent.OpenPrivacy, MoreEvent.OpenTerms, MoreEvent.OpenCredits,
            ),
            events,
        )
    }

    // --- Fasting (0.2) ---

    private fun withFasting(preset: PresetId, hard: Boolean = false, released: Boolean = true, on: Boolean = false) =
        MoreStateBuilder.build(
            SettingsFixtures.settings(NumbersSettings(preset)).copy(fasting = on, onboarding = OnboardingAnswers(hard = hard)),
            fastingReleased = released,
        )

    @Test
    fun fastingSitsAfterWaterOnlyInFullAndSprint() {
        PresetId.entries.forEach { preset ->
            val extras = withFasting(preset).groups[1]
            val expected = if (preset == PresetId.Full || preset == PresetId.Full) {
                listOf(MoreRow.Water(glasses = 8), MoreRow.Fasting(on = false, plan = FastingPlan.FourteenTen))
            } else {
                listOf(MoreRow.Water(glasses = 8))
            }
            assertEquals(expected, extras, "in $preset")
        }
    }

    @Test
    fun fastingNeverShowsAfterTheStatementInAnyMode() {
        PresetId.entries.forEach { preset ->
            assertEquals(listOf(MoreRow.Water(glasses = 8)), withFasting(preset, hard = true, on = true).groups[1], "in $preset")
        }
    }

    @Test
    fun fastingNeverShowsWithTheFlagOff() {
        assertEquals(listOf(MoreRow.Water(glasses = 8)), withFasting(PresetId.Full, released = false).groups[1])
        assertEquals(listOf(MoreRow.Water(glasses = 8)), build().groups[1], "0.1 callers pass no flag")
    }

    @Test
    fun theFastingRowSaysOnWithThePlan() {
        assertEquals(MoreRow.Fasting(on = true, plan = FastingPlan.FourteenTen), withFasting(PresetId.Full, on = true).groups[1][1])
    }

    @Test
    fun theFastingRowOpensFasting() = runUnitTest {
        val vm = MoreViewModel(FakeSettingsRepository(SettingsFixtures.settings()), FakeHealthRepository(), healthFlag(on = false), remindersFlag(), FastingRelease { true }, widgetsFlag(), goalsAndEnergyFlag(), FakeAuthRepository(), FakeDiarySync())
        val events = eventsOf(vm)

        vm.takeAction(MoreAction.RowTapped(vm.state.groups[1][1]))

        assertEquals(listOf(MoreEvent.OpenFasting), events)
    }

    // --- Widgets (0.2) ---

    @Test
    fun widgetsFlagOff_noRow_inAnyMode() {
        PresetId.entries.forEach { preset ->
            assertTrue(build(NumbersSettings(preset)).groups.flatten().none { it == MoreRow.Widgets }, "in $preset")
        }
    }

    @Test
    fun widgetsFlagOn_theRowSitsLastInTheExtrasCard_afterFasting() {
        val settings = SettingsFixtures.settings().copy(fasting = true)
        assertEquals(
            listOf(MoreRow.Water(glasses = 8), MoreRow.Fasting(on = true, plan = FastingPlan.FourteenTen), MoreRow.Widgets),
            MoreStateBuilder.build(settings, fastingReleased = true, widgetsOn = true).groups[1],
        )
        assertEquals(
            listOf(MoreRow.Water(glasses = 8), MoreRow.Widgets),
            MoreStateBuilder.build(SettingsFixtures.settings(NumbersSettings(PresetId.NoNumbers)), widgetsOn = true).groups[1],
            "in a mode without fasting",
        )
    }

    @Test
    fun theWidgetsRowOpensWidgets() = runUnitTest {
        val vm = MoreViewModel(FakeSettingsRepository(SettingsFixtures.settings()), FakeHealthRepository(), healthFlag(on = false), remindersFlag(), FastingRelease { false }, widgetsFlag(on = true), goalsAndEnergyFlag(), FakeAuthRepository(), FakeDiarySync())
        val events = eventsOf(vm)

        vm.takeAction(MoreAction.RowTapped(vm.state.groups[1].last()))

        assertEquals(listOf<MoreEvent>(MoreEvent.OpenWidgets), events)
    }

    @Test
    fun healthFlagOff_noRow_andTheStoreIsNeverTouched() = runUnitTest {
        val health = FakeHealthRepository(status = HealthStatus.Connected)
        val vm = MoreViewModel(FakeSettingsRepository(), health, healthFlag(on = false), remindersFlag(), FastingRelease { false }, widgetsFlag(), goalsAndEnergyFlag(), FakeAuthRepository(), FakeDiarySync())

        assertTrue(vm.state.groups.flatten().none { it is MoreRow.Health })
        assertEquals(0, health.refreshes)
    }

    @Test
    fun healthFlagOff_comingBackToMore_stillNeverTouchesTheStore() = runUnitTest {
        val health = FakeHealthRepository(status = HealthStatus.Connected)
        val vm = MoreViewModel(FakeSettingsRepository(), health, healthFlag(on = false), remindersFlag(), FastingRelease { false }, widgetsFlag(), goalsAndEnergyFlag(), FakeAuthRepository(), FakeDiarySync())

        vm.takeAction(MoreAction.ScreenResumed)

        assertEquals(0, health.refreshes)
    }

    @Test
    fun healthFlagOn_theRowSaysWhetherItIsConnected() = runUnitTest {
        val health = FakeHealthRepository(status = HealthStatus.NotConnected)
        val vm = MoreViewModel(FakeSettingsRepository(), health, healthFlag(on = true), remindersFlag(), FastingRelease { false }, widgetsFlag(), goalsAndEnergyFlag(), FakeAuthRepository(), FakeDiarySync())
        assertEquals(MoreRow.Health(connected = false), vm.state.groups[0].filterIsInstance<MoreRow.Health>().single())

        health.statusFlow.value = HealthStatus.Connected

        assertEquals(MoreRow.Health(connected = true), vm.state.groups[0].filterIsInstance<MoreRow.Health>().single())
    }

    @Test
    fun screenResumed_rechecksTheConnection() = runUnitTest {
        val health = FakeHealthRepository()
        val vm = MoreViewModel(FakeSettingsRepository(), health, healthFlag(on = true), remindersFlag(), FastingRelease { false }, widgetsFlag(), goalsAndEnergyFlag(), FakeAuthRepository(), FakeDiarySync())
        val before = health.refreshes
        vm.takeAction(MoreAction.ScreenResumed)
        assertEquals(before + 1, health.refreshes)
    }

    @Test
    fun theHealthRow_carriesNoAmount_inAnyMode() {
        PresetId.entries.forEach { preset ->
            val row = MoreStateBuilder.build(SettingsFixtures.settings(NumbersSettings(preset)), healthConnected = true).groups[0].filterIsInstance<MoreRow.Health>().single()
            assertEquals(MoreRow.Health(connected = true), row, "in $preset")
        }
    }

    @Test
    fun withBothFlagsTheFirstCardReadsAsDesignSync2() {
        val rows = MoreStateBuilder.build(SettingsFixtures.settings(), healthConnected = true, remindersOn = true).groups[0]
        assertEquals(
            listOf(
                MoreRow.Display(PresetId.Full, changes = 0), MoreRow.Targets, MoreRow.Reminders(quiet = true),
                MoreRow.Health(connected = true),
            ),
            rows,
        )
    }

    @Test
    fun withTheRemindersFlagTheFirstCardGainsReminders_sayingWhetherQuietModeIsOn() = runUnitTest {
        val settings = SettingsFixtures.settings().copy(quiet = false)
        assertEquals(
            listOf(MoreRow.Display(PresetId.Full, changes = 0), MoreRow.Targets, MoreRow.Reminders(quiet = false)),
            MoreStateBuilder.build(settings, remindersOn = true).groups[0],
        )

        val vm = MoreViewModel(FakeSettingsRepository(settings), FakeHealthRepository(), healthFlag(on = false), remindersFlag(on = true), FastingRelease { false }, widgetsFlag(), goalsAndEnergyFlag(), FakeAuthRepository(), FakeDiarySync())
        val events = eventsOf(vm)
        vm.takeAction(MoreAction.RowTapped(MoreRow.Reminders(quiet = false)))
        assertEquals(listOf<MoreEvent>(MoreEvent.OpenReminders), events)
    }

    @Test
    fun todayScreen_followsTheTargets_onlyWithGoalsAndEnergy() {
        val on = MoreStateBuilder.build(SettingsFixtures.settings(), todayScreenOn = true).groups[0]
        assertEquals(MoreRow.TodayScreen, on[on.indexOf(MoreRow.Targets) + 1])
        assertTrue(MoreRow.TodayScreen !in build().groups.flatten())
    }
}
