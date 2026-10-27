package com.opson.features.settings.impl.fasting

import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.Toast
import com.opson.features.settings.impl.eventsOf
import com.opson.libraries.diary.Fast
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.FastingRepository
import com.opson.libraries.diary.FastingStatus
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MinuteTicker
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.FastingPlan
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.fasting_toast_ended
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The Fasting page: who gets it at all (every mode, the statement, the flag), what the status
 * card says, and each action. The schedule's own rules are `FastsTest` in `:libraries:diary`.
 */
class FastingTest : CoroutineTest() {

    // ---------- builder ----------

    @Test
    fun offeredInFullAndSprint_notInThisModeEverywhereElse() {
        PresetId.entries.forEach { preset ->
            val page = FastingFixtures.state(FastingFixtures.settings(preset = preset)).page
            if (preset == PresetId.Full || preset == PresetId.Full) {
                assertIs<FastingPage.Offered>(page, preset.name)
            } else {
                assertEquals(FastingPage.NotInThisMode, page, preset.name)
            }
        }
    }

    @Test
    fun hiddenInEveryModeAfterTheStatement() {
        PresetId.entries.forEach { preset ->
            val settings = FastingFixtures.settings(preset = preset).copy(onboarding = OnboardingAnswers(hard = true))
            assertEquals(FastingPage.Hidden, FastingFixtures.state(settings).page, preset.name)
        }
    }

    @Test
    fun hiddenWithTheFlagOff() {
        val inputs = FastingInputs(FastingFixtures.settings(), released = false, latest = null, now = FastingFixtures.noon, zone = TimeZone.UTC)
        assertEquals(FastingPage.Hidden, FastingStateBuilder.build(inputs).page)
    }

    @Test
    fun offTheTimerShowsNoStatus() {
        val page = assertIs<FastingPage.Offered>(FastingFixtures.state(FastingFixtures.settings(timerOn = false)).page)
        assertNull(page.status)
        assertEquals(false, page.timerOn)
    }

    @Test
    fun theWindowAndPlansFollowThePlan() {
        val page = assertIs<FastingPage.Offered>(FastingFixtures.state(FastingFixtures.settings(plan = FastingPlan.SixteenEight)).page)
        assertEquals(listOf("12:12", "14:10", "16:8"), page.plans.map { it.key })
        assertEquals(FastingPlan.SixteenEight, page.plan)
        assertEquals(LocalTime(11, 0) to LocalTime(19, 0), page.opens to page.closes)
    }

    @Test
    fun statusFollowsTheStoredStart() {
        assertEquals(FastingStatus.WindowOpen(until = LocalTime(19, 0)), offered().status)
        assertEquals(
            FastingStatus.Running(since = LocalTime(19, 0), elapsed = 13.hours + 40.minutes),
            offered(latest = FastingFixtures.lastNight, now = FastingFixtures.morning).status,
        )
        val ended = FastingFixtures.lastNight.copy(endedAt = Instant.parse("2026-10-25T20:10:00Z"))
        assertEquals(
            FastingStatus.Ended(at = LocalTime(20, 10), nextStart = LocalTime(19, 0)),
            offered(latest = ended, now = FastingFixtures.night).status,
        )
    }

    // ---------- view model ----------

    private val settings = FakeSettingsRepository(FastingFixtures.settings())
    private val fasting = FakeFastingRepository()
    private var released = true

    private fun vm() = FastingViewModel(settings, fasting, FastingRelease { released }, UtcCalendar(), MinuteTicker { flowOf(FastingFixtures.morning) })

    @Test
    fun theTimerIsComputedFromTheStoredStart() = runUnitTest {
        fasting.latest.value = FastingFixtures.lastNight
        val vm = vm()

        val page = assertIs<FastingPage.Offered>(vm.state.page)
        assertEquals(FastingStatus.Running(LocalTime(19, 0), 13.hours + 40.minutes), page.status)
    }

    @Test
    fun arrivalsStartADueFastOnlyWhileTheTimerIsOn() = runUnitTest {
        vm()
        assertEquals(1, fasting.startDueCalls)

        settings.state.value = FastingFixtures.settings(timerOn = false)
        assertEquals(1, fasting.startDueCalls)

        settings.state.value = FastingFixtures.settings(preset = PresetId.NoNumbers)
        assertEquals(1, fasting.startDueCalls)
    }

    @Test
    fun thePageClosesForSomeoneWhoAgreedNumbersHaveBeenHard() = runUnitTest {
        settings.state.value = FastingFixtures.settings().copy(onboarding = OnboardingAnswers(hard = true))
        val vm = vm()
        val events = eventsOf(vm)

        settings.state.value = settings.state.value.copy(waterGlasses = 9)

        assertEquals(FastingPage.Hidden, vm.state.page)
        assertEquals(0, fasting.startDueCalls)
        assertEquals(listOf(FastingEvent.Close), events, "closes once, however often the inputs arrive")
    }

    @Test
    fun thePageClosesWhenTheFlagIsOff() = runUnitTest {
        released = false
        val vm = vm()
        assertEquals(FastingPage.Hidden, vm.state.page)
        assertEquals(0, fasting.startDueCalls)
    }

    @Test
    fun timerToggled_turnsItOnAndOff_andOffEndsARunningFast() = runUnitTest {
        settings.state.value = FastingFixtures.settings(timerOn = false)
        val vm = vm()

        vm.takeAction(FastingAction.TimerToggled)
        assertEquals(true, settings.state.value.fasting)
        assertEquals(0, fasting.timerOffCalls)

        vm.takeAction(FastingAction.TimerToggled)
        assertEquals(false, settings.state.value.fasting)
        assertEquals(1, fasting.timerOffCalls)
    }

    @Test
    fun showOnTodayToggled_onlyWhileTheTimerIsOn() = runUnitTest {
        val vm = vm()
        vm.takeAction(FastingAction.ShowOnTodayToggled)
        assertEquals(false, settings.state.value.fastToday)

        settings.state.value = FastingFixtures.settings(timerOn = false)
        vm.takeAction(FastingAction.ShowOnTodayToggled)
        assertEquals(true, settings.state.value.fastToday, "ignored while the timer is off")
    }

    @Test
    fun planPicked_onlyWhileTheTimerIsOn() = runUnitTest {
        val vm = vm()
        vm.takeAction(FastingAction.PlanPicked(FastingPlan.TwelveTwelve))
        assertEquals(FastingPlan.TwelveTwelve, settings.state.value.fastPlan)

        settings.state.value = FastingFixtures.settings(timerOn = false)
        vm.takeAction(FastingAction.PlanPicked(FastingPlan.SixteenEight))
        assertEquals(FastingPlan.FourteenTen, settings.state.value.fastPlan)
    }

    @Test
    fun endFastTapped_endsItAndSaysSo() = runUnitTest {
        val vm = vm()
        val events = eventsOf(vm)

        vm.takeAction(FastingAction.EndFastTapped)

        assertEquals(1, fasting.endCalls)
        assertEquals(listOf<FastingEvent>(FastingEvent.ShowToast(Toast(Res.string.fasting_toast_ended))), events)
    }

    @Test
    fun backTapped_closes() = runUnitTest {
        val vm = vm()
        val events = eventsOf(vm)
        vm.takeAction(FastingAction.BackTapped)
        assertEquals(listOf<FastingEvent>(FastingEvent.Close), events)
    }

    private fun offered(latest: Fast? = null, now: Instant = FastingFixtures.noon) =
        assertIs<FastingPage.Offered>(FastingFixtures.state(latest = latest, now = now).page)
}

class FakeFastingRepository : FastingRepository {
    val latest = MutableStateFlow<Fast?>(null)
    var startDueCalls = 0
        private set
    var endCalls = 0
        private set
    var timerOffCalls = 0
        private set

    override fun observeLatest(): Flow<Fast?> = latest

    override suspend fun startDue() {
        startDueCalls++
    }

    override suspend fun end() {
        endCalls++
    }

    override suspend fun endForTimerOff() {
        timerOffCalls++
    }
}

private class UtcCalendar : LocalCalendar {
    override fun timeZone(): TimeZone = TimeZone.UTC

    override fun today(): LocalDate = LocalDate(2026, 10, 26)
}

