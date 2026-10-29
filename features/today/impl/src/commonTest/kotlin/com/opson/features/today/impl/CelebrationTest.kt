package com.opson.features.today.impl

import com.opson.features.today.CelebrationRoute
import com.opson.features.today.impl.celebration.CelebrationAction
import com.opson.features.today.impl.celebration.CelebrationAnswer
import com.opson.features.today.impl.celebration.CelebrationEvent
import com.opson.features.today.impl.celebration.CelebrationPrompt
import com.opson.features.today.impl.celebration.CelebrationStore
import com.opson.features.today.impl.celebration.CelebrationTelemetry
import com.opson.features.today.impl.celebration.CelebrationViewModel
import com.opson.features.today.impl.celebration.Celebrating
import com.opson.features.today.impl.celebration.ProgressStateBuilder
import com.opson.features.today.impl.celebration.ShowingUpCelebrationsSource
import com.opson.features.today.impl.celebration.StreakBadge
import com.opson.features.today.impl.dayclose.DayCloseViewModel
import com.opson.features.today.impl.dayclose.StreakLine
import com.opson.libraries.diary.CelebrationStyle
import com.opson.libraries.diary.DayMark
import com.opson.libraries.diary.DayMarks
import com.opson.libraries.diary.Milestone
import com.opson.libraries.diary.MinuteTicker
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.ShowingUp
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.prompts.PromptPresentation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.celebrate_wrapped_toast
import opson.libraries.resources.generated.resources.celebrate_wrapped_toast_plain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Celebrations: when one is due, what the screen does with each answer, and what Day close, Today and Your progress say. */
class CelebrationTest : CoroutineTest() {

    private val today = TodayFixtures.today
    private val marks = MutableStateFlow<List<DayMark>>(emptyList())
    private val settings = FakeSettingsRepository()
    private val store = CelebrationStore(FakeCacheFactory())

    private fun loggedDays(count: Int, closedToday: Boolean = false) {
        marks.value = (0 until count).map { DayMark(today.minus(it, DateTimeUnit.DAY), logged = true, closed = closedToday && it == 0) }
    }

    private fun source(flagOn: Boolean = true) = ShowingUpCelebrationsSource(
        flag = celebrationsFlag(flagOn),
        settings = settings,
        marks = DayMarks { marks },
        calendar = FakeCalendar(),
        ticker = MinuteTicker { flowOf(TodayFixtures.noon) },
        store = store,
    )

    private fun celebrating(days: Int, style: CelebrationStyle = CelebrationStyle.Counts, closedToday: Boolean = false) = Celebrating(
        ShowingUp.compute((0 until days).map { DayMark(today.minus(it, DateTimeUnit.DAY), logged = true, closed = closedToday && it == 0) }, today),
        style,
    )

    // The source

    @Test
    fun withTheFlagOff_orCelebrationsTurnedOff_thereIsNothing() = runUnitTest {
        loggedDays(3)
        assertNull(source(flagOn = false).observe().first())
        settings.state.value = Settings(celebrationsEnabled = false)
        assertNull(source().observe().first())
    }

    @Test
    fun afterNumbersHaveBeenHard_itIsGentle() = runUnitTest {
        loggedDays(3)
        settings.state.value = Settings(onboarding = OnboardingAnswers(hard = true))
        assertEquals(CelebrationStyle.Gentle, source().observe().first()?.style)
    }

    // The prompt

    @Test
    fun theFirstMilestone_showsOnce_offeringToTurnThemOff_andTheNextDoesNot() = runUnitTest {
        val prompt = CelebrationPrompt(source(), store)
        assertEquals(PromptPresentation.Screen, prompt.presentation)
        assertFalse(prompt.isEligible(), "nothing reached yet")

        loggedDays(3)
        assertTrue(prompt.isEligible())
        val first = assertIs<CelebrationRoute>(prompt.screen())
        assertEquals(Milestone.Days3.wire, first.milestone)
        assertTrue(first.offerTurnOff)
        assertFalse(prompt.isEligible(), "a milestone is celebrated once")

        loggedDays(7)
        val next = assertIs<CelebrationRoute>(prompt.screen())
        assertEquals(Milestone.Week1.wire, next.milestone)
        assertFalse(next.offerTurnOff, "only the very first celebration offers it")
    }

    @Test
    fun celebratingAWeek_coversTheThreeDays_soANewPhoneShowsOnlyTheHighest() = runUnitTest {
        val prompt = CelebrationPrompt(source(), store)
        loggedDays(7)
        assertEquals(Milestone.Week1.wire, assertIs<CelebrationRoute>(prompt.screen()).milestone)
        assertFalse(prompt.isEligible())
    }

    @Test
    fun withCelebrationsOff_theresNothingToShow() = runUnitTest {
        settings.state.value = Settings(celebrationsEnabled = false)
        loggedDays(7)
        val prompt = CelebrationPrompt(source(), store)
        assertFalse(prompt.isEligible())
        assertNull(prompt.screen())
    }

    // The screen

    private class RecordingTelemetry : CelebrationTelemetry {
        val events = mutableListOf<String>()

        override fun shown(milestone: Milestone, style: CelebrationStyle, offeredTurnOff: Boolean) {
            events += "shown:${milestone.wire}:$offeredTurnOff"
        }

        override fun answered(milestone: Milestone, answer: CelebrationAnswer) {
            events += "answered:${milestone.wire}:${answer.wire}"
        }

        override fun progressOpened(style: CelebrationStyle) {
            events += "progress"
        }
    }

    @Test
    fun iCanDoThis_closes_once() = runUnitTest {
        val telemetry = RecordingTelemetry()
        val vm = CelebrationViewModel(Milestone.Week1.wire, offerTurnOff = true, settingsRepository = settings, telemetry = telemetry)
        val events = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }

        vm.takeAction(CelebrationAction.CanDoThisTapped)
        vm.takeAction(CelebrationAction.CanDoThisTapped)

        assertEquals(listOf<CelebrationEvent>(CelebrationEvent.Close), events)
        assertEquals(listOf("shown:week_1:true", "answered:week_1:can_do_this"), telemetry.events)
        assertTrue(settings.state.value.celebrationsEnabled)
    }

    @Test
    fun ratherNot_turnsCelebrationsOff_andSaysWhereToTurnThemBackOn() = runUnitTest {
        val telemetry = RecordingTelemetry()
        val vm = CelebrationViewModel(Milestone.FirstWrapUp.wire, offerTurnOff = true, settingsRepository = settings, telemetry = telemetry)
        val events = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }

        vm.takeAction(CelebrationAction.TurnOffTapped)

        assertFalse(settings.state.value.celebrationsEnabled)
        assertEquals(listOf<CelebrationEvent>(CelebrationEvent.TurnedOff), events)
        assertEquals("answered:first_wrap_up:turned_off", telemetry.events.last())
    }

    @Test
    fun aMilestoneThisBuildDoesNotKnow_closesAtOnce() = runUnitTest {
        val vm = CelebrationViewModel("decade_1", offerTurnOff = false, settingsRepository = settings)
        val events = mutableListOf<CelebrationEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        vm.takeAction(CelebrationAction.CanDoThisTapped)
        assertEquals(CelebrationEvent.Close, events.last())
    }

    @Test
    fun theScreenSpeaksGently_afterNumbersHaveBeenHard() = runUnitTest {
        settings.state.value = Settings(onboarding = OnboardingAnswers(hard = true))
        val vm = CelebrationViewModel(Milestone.Days30.wire, offerTurnOff = false, settingsRepository = settings)
        assertEquals(CelebrationStyle.Gentle, vm.state.style)
    }

    // Today's streak, Your progress, Day close

    @Test
    fun theStreak_showsTheCount_orASparkleWhenGentle_andNothingWithoutARun() {
        assertEquals(StreakBadge.Counts(4), StreakBadge.of(celebrating(4)))
        assertEquals(StreakBadge.Gentle, StreakBadge.of(celebrating(4, CelebrationStyle.Gentle)))
        assertNull(StreakBadge.of(celebrating(0)))
        assertNull(StreakBadge.of(null))
    }

    @Test
    fun yourProgress_hasCountsAndBadges_orNoNumbersAtAllWhenGentle() {
        val counts = ProgressStateBuilder.build(celebrating(8)).counts!!
        assertEquals(8, counts.current)
        assertEquals(Milestone.Weeks2, counts.next)
        assertEquals(6, counts.toNext)
        assertEquals(7, counts.week.size)
        assertEquals(today, counts.week.last().date)
        assertEquals(setOf(Milestone.Days3, Milestone.Week1), counts.badges.filter { it.reached }.map { it.milestone }.toSet())

        val gentle = ProgressStateBuilder.build(celebrating(8, CelebrationStyle.Gentle))
        assertNull(gentle.counts)
        assertFalse(gentle.loading)
    }

    @Test
    fun dayClose_saysTheRun_onlyOnceTodayIsWrappedUp() {
        val full = policy(PresetId.Full)
        val open = TodayFixtures.dayCloseState(full, celebrating = celebrating(4))
        assertNull(open.streak)
        val closed = TodayFixtures.dayCloseState(full, closed = true, celebrating = celebrating(4, closedToday = true))
        assertEquals(StreakLine.Counts(4, Milestone.Week1, 3), closed.streak)
        val gentle = TodayFixtures.dayCloseState(full, closed = true, celebrating = celebrating(4, CelebrationStyle.Gentle, closedToday = true))
        assertEquals(StreakLine.Gentle, gentle.streak)
        val off = TodayFixtures.dayCloseState(full, closed = true)
        assertNull(off.streak)
    }

    @Test
    fun wrappingUp_saysTheRun_unlessACelebrationIsAboutToPlay() {
        // Logged today and the three days before, wrapped up before: four in a row, nothing new reached.
        val logged = Celebrating(
            ShowingUp.compute(
                (0 until 4).map { DayMark(today.minus(it, DateTimeUnit.DAY), logged = true) } + DayMark(today.minus(9, DateTimeUnit.DAY), closed = true),
                today,
            ),
            CelebrationStyle.Counts,
        )
        assertEquals(Toast(Res.string.celebrate_wrapped_toast, listOf(4)), DayCloseViewModel.wrappedToast(logged, waiting = null))
        assertNull(DayCloseViewModel.wrappedToast(logged, waiting = Milestone.Days3), "a waiting celebration says it")
        assertNull(DayCloseViewModel.wrappedToast(celebrating(2), waiting = null), "the first wrap-up is a celebration of its own")
        assertEquals(
            Toast(Res.string.celebrate_wrapped_toast_plain),
            DayCloseViewModel.wrappedToast(logged.copy(style = CelebrationStyle.Gentle), waiting = null),
            "no count after numbers have been hard",
        )
        assertNull(DayCloseViewModel.wrappedToast(null, waiting = null), "nothing with celebrations off")
    }
}
