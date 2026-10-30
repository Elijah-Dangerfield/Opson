package com.opson.features.trends.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsCards
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Visibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Steps card: which state it is in, what the chart and facts say, and how a steps goal reads on it. */
class TrendsStepsTest : CoroutineTest() {

    private val today = TrendsFixtures.today
    private val steps = TrendsFixtures.steps()

    private fun card(
        stepsInputs: TrendsStepsInputs? = TrendsFixtures.stepsInputs(),
        settings: Settings = Settings(),
        ui: TrendsUi = TrendsUi(),
    ): StepsCard? = TrendsStateBuilder.build(TrendsFixtures.trendsInputs(settings = settings, steps = stepsInputs), TrendsFixtures.policy(), ui).steps

    private fun chart(settings: Settings = Settings()): StepsCard.Chart = assertIs<StepsCard.Chart>(card(settings = settings))

    // Which card

    @Test
    fun healthReleaseOff_noCard() = assertNull(card(stepsInputs = null))

    @Test
    fun switchedOff_noCard() = assertNull(card(settings = Settings(trendsCards = TrendsCards(steps = false))))

    @Test
    fun energyOutHidden_noCard() {
        val hidden = TrendsFixtures.policy(overrides = mapOf(Metric.EnergyOut to Visibility.Hide))
        val state = TrendsStateBuilder.build(TrendsFixtures.trendsInputs(settings = TrendsFixtures.settings(hidden), steps = TrendsFixtures.stepsInputs()), hidden)
        assertNull(state.steps)
    }

    @Test
    fun notConnected_offersConnect() {
        val connect = assertIs<StepsCard.Connect>(card(TrendsFixtures.stepsInputs(status = HealthStatus.NotConnected)))
        assertEquals(false, connect.install)
        assertEquals(true, assertIs<StepsCard.Connect>(card(TrendsFixtures.stepsInputs(status = HealthStatus.NeedsInstall))).install)
    }

    @Test
    fun aPhoneWithNoHealthStore_noCard() = assertNull(card(TrendsFixtures.stepsInputs(status = HealthStatus.Unsupported)))

    @Test
    fun connectedWithoutSteps_asksToAllowThem_orSaysThereAreNone() {
        assertIs<StepsCard.Allow>(card(TrendsFixtures.stepsInputs(days = emptyMap(), canAskForMore = true)))
        assertIs<StepsCard.Empty>(card(TrendsFixtures.stepsInputs(days = emptyMap())))
        assertIs<StepsCard.Empty>(card(TrendsFixtures.stepsInputs(days = null)), "a store that couldn't be read")
    }

    // The chart

    @Test
    fun aWeek_isABarADay_todayStriped_andLabelled() {
        val chart = chart()
        assertEquals(7, chart.bars.size)
        assertEquals(listOf(false, false, false, false, false, false, true), chart.bars.map { it.isCurrent })
        assertTrue(chart.bars.all { it.label.isNotEmpty() })
        assertNull(chart.axis)
        assertNull(chart.band, "no goal, no line")
    }

    @Test
    fun theAverage_leavesTodayOut_andTheMostIsAnyDay() {
        val chart = chart()
        val week = (1..6).map { steps.getValue(today.minus(it, DateTimeUnit.DAY)) }
        assertEquals(week.average().roundToInt(), chart.average)
        assertEquals(4120, chart.today)
        val most = (0..6).map { today.minus(it, DateTimeUnit.DAY) }.maxBy { steps.getValue(it) }
        assertEquals(steps.getValue(most), chart.best?.steps)
    }

    @Test
    fun aMonth_hasADayPerBar_andTheEndsUnderneath() {
        val chart = chart(Settings(trendsRange = TrendsRange.FourWeeks))
        assertEquals(28, chart.bars.size)
        assertTrue(chart.bars.all { it.label.isEmpty() })
        assertEquals("Today", chart.axis?.end)
        assertTrue(chart.bars.last().isCurrent)
    }

    @Test
    fun aDayWithNoSteps_isAGap_notAZero() {
        val missing = today.minus(2, DateTimeUnit.DAY)
        val chart = assertIs<StepsCard.Chart>(card(TrendsFixtures.stepsInputs(days = steps - missing)))
        assertNull(chart.bars[4].fraction)
        assertNull(chart.readouts[4])
    }

    @Test
    fun theScale_reachesTheGoalOrTheMost_withRoom() {
        assertEquals(14_000.0, TrendsStepsBuilder.scale(most = 12_000, goal = 8_000))
        assertEquals(12_000.0, TrendsStepsBuilder.scale(most = 6_000, goal = 10_000))
        assertEquals(5_000.0, TrendsStepsBuilder.scale(most = 300, goal = null), "a quiet day stays a short bar")
    }

    // A steps goal

    @Test
    fun aStepsGoal_drawsItsLine_andCountsTheDaysAtIt() {
        val chart = chart(TrendsFixtures.stepsGoalSettings())
        val goal = assertTrueNotNull(chart.goal)
        val scale = TrendsStepsBuilder.scale(chart.bars.indices.mapNotNull { steps[today.minus(6 - it, DateTimeUnit.DAY)] }.max(), TrendsFixtures.STEPS_GOAL)
        val line = (TrendsFixtures.STEPS_GOAL / scale).toFloat()
        assertEquals(line..line, chart.band)
        val past = (1..6).map { steps.getValue(today.minus(it, DateTimeUnit.DAY)) }
        val met = past.count { it >= TrendsFixtures.STEPS_GOAL }
        assertEquals(met, goal.met, "today's 4,120 is still going, so it isn't counted")
        assertEquals(6, goal.counted)
    }

    @Test
    fun theStreak_runsBackPastTheWeek_andSkipsToday() {
        // Every past day well over the goal: the run is every day since the goal began.
        val days = (0 until TrendsStepsBuilder.HISTORY_DAYS).associate { back -> today.minus(back, DateTimeUnit.DAY) to (if (back == 0) 100 else 9_000) }
        val chart = assertIs<StepsCard.Chart>(card(TrendsFixtures.stepsInputs(days = days), settings = TrendsFixtures.stepsGoalSettings()))
        assertEquals(60, chart.goal?.streak)
    }

    @Test
    fun theStreak_forgivesOneDayUnder_andTwoInARowEndIt() {
        val one = (0..10).associate { back -> today.minus(back, DateTimeUnit.DAY) to (if (back == 4) 1_000 else 9_000) }
        val forgiven = assertIs<StepsCard.Chart>(card(TrendsFixtures.stepsInputs(days = one), settings = TrendsFixtures.stepsGoalSettings()))
        assertEquals(10, forgiven.goal?.streak, "one day under between met ones is forgiven")
        val two = (0..10).associate { back -> today.minus(back, DateTimeUnit.DAY) to (if (back == 4 || back == 5) 1_000 else 9_000) }
        val ended = assertIs<StepsCard.Chart>(card(TrendsFixtures.stepsInputs(days = two), settings = TrendsFixtures.stepsGoalSettings()))
        assertEquals(4, ended.goal?.streak, "today met counts, and so do the three days before it")
    }

    @Test
    fun theStreak_isOnlyThereWithGoalStreaksOn() {
        val off = TrendsFixtures.stepsGoalSettings(Settings(goalStreakEnabled = false))
        val chart = assertIs<StepsCard.Chart>(card(settings = off))
        assertNull(chart.goal?.streak)
        assertTrue(chart.goal != null, "the line and the days at it stay")
    }

    @Test
    fun theHelp_opensAndCloses() = assertEquals(true, card(ui = TrendsUi(stepsExplained = true))?.explained)

    // The view model

    private val health = FakeHealthRepository()
    private val stepsRepository = FakeStepsRepository()

    private fun vm(healthOn: Boolean = true, health: FakeHealthRepository = this.health) = TrendsViewModel(
        FakeDiaryRepository(), FakeWeightRepository(), FakeSettingsRepository(), FakePolicySource(), TrendsFixtures.clock, health, healthFlag(healthOn),
        goalsAndEnergyFlag(), FakeBurnedRepository(), checkInFlag(false), TrendsFixtures.timeZone, steps = stepsRepository,
    )

    private fun CoroutineScope.events(vm: TrendsViewModel) = mutableListOf<TrendsEvent>().also { list ->
        launch { vm.eventFlow.collect { list += it } }
    }

    @Test
    fun healthReleaseOff_stepsAreNeverRead() = runUnitTest {
        val vm = vm(healthOn = false)
        assertNull(vm.state.steps)
        assertTrue(stepsRepository.spans.isEmpty())
    }

    @Test
    fun healthReleaseOn_readsFourMonthsToToday_whateverTheRange() = runUnitTest {
        val vm = vm()
        assertIs<StepsCard.Chart>(vm.state.steps)
        assertEquals(today.minus(TrendsStepsBuilder.HISTORY_DAYS - 1, DateTimeUnit.DAY) to today, stepsRepository.spans.single())
    }

    @Test
    fun connect_thatIsRefused_opensWatchAndHealthApps() = runUnitTest {
        val refused = FakeHealthRepository(initial = HealthStatus.NotConnected).apply { connectOutcome = ConnectOutcome.Denied }
        val vm = vm(health = refused)
        val events = events(vm)
        assertIs<StepsCard.Connect>(vm.state.steps)
        vm.takeAction(TrendsAction.StepsConnectTapped)
        assertEquals(1, refused.connects)
        assertEquals(listOf<TrendsEvent>(TrendsEvent.OpenIntegrations), events)
    }

    @Test
    fun connect_thatWorks_staysOnTrends() = runUnitTest {
        val vm = vm(health = FakeHealthRepository(initial = HealthStatus.NotConnected))
        val events = events(vm)
        vm.takeAction(TrendsAction.StepsConnectTapped)
        assertTrue(events.isEmpty())
    }

    private fun <T : Any> assertTrueNotNull(value: T?): T {
        assertTrue(value != null)
        return value
    }
}
