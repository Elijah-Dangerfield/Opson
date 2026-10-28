package com.opson.features.trends.impl

import com.opson.features.trends.NutrientsRange
import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The Nutrients detail view model: the range switch re-subscribes, vitamins
 * and minerals link to Modes and numbers (nothing turns on here), and back
 * closes. What each mode shows is `NutrientsStateBuilderTest`.
 */
class NutrientsViewModelTest : CoroutineTest() {

    private val diary = FakeDiaryRepository()

    private fun vm(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        range: NutrientsRange = NutrientsRange.Day,
    ) = NutrientsViewModel(
        day = TrendsFixtures.today,
        initialRange = range,
        fromTrends = false,
        diary = diary,
        settingsRepository = settings,
        policySource = FakePolicySource(),
        clock = TrendsFixtures.clock,
        goalsAndEnergy = goalsAndEnergyFlag(),
        burnedRepository = FakeBurnedRepository(),
        timeZone = TrendsFixtures.timeZone,
    )

    private fun CoroutineScope.events(vm: NutrientsViewModel) = mutableListOf<NutrientsEvent>().also { list ->
        launch { vm.eventFlow.collect { list += it } }
    }

    @Test
    fun opensOnTheDay() = runUnitTest {
        val vm = vm()
        assertEquals(listOf(TrendsFixtures.today), diary.dayRequests)
        assertEquals(NutrientsSubtitle.Day("Sunday, Oct 25"), vm.state.subtitle)
    }

    @Test
    fun rangePicked_resubscribesToTheWeek() = runUnitTest {
        val vm = vm()
        vm.takeAction(NutrientsAction.RangePicked(NutrientsRange.WeekAverage))
        assertEquals(TrendsFixtures.weekWindowRange(), diary.rangeRequests.single())
        assertEquals(NutrientsRange.WeekAverage, vm.state.range)
        assertEquals(NutrientsSubtitle.WeekAverage("Oct 19 to 25"), vm.state.subtitle)

        vm.takeAction(NutrientsAction.RangePicked(NutrientsRange.Day))
        assertEquals(2, diary.dayRequests.size)
        assertEquals(NutrientsRange.Day, vm.state.range)
    }

    @Test
    fun weekAverage_openOverTheMidnightThatStartsAWeek_readsTheNewWeek() = runUnitTest {
        var now = LocalDateTime(LocalDate(2026, 10, 31), LocalTime(23, 50))
        val clock = object : Clock {
            override fun now(): Instant = now.toInstant(TrendsFixtures.timeZone)
        }
        val policy = FakePolicySource(TrendsFixtures.policy(clock = clock))
        val vm = NutrientsViewModel(
            day = LocalDate(2026, 10, 31),
            initialRange = NutrientsRange.WeekAverage,
            fromTrends = true,
            diary = diary,
            settingsRepository = FakeSettingsRepository(),
            policySource = policy,
            clock = clock,
            goalsAndEnergy = goalsAndEnergyFlag(),
            burnedRepository = FakeBurnedRepository(),
            timeZone = TrendsFixtures.timeZone,
        )
        assertEquals(LocalDate(2026, 10, 19) to LocalDate(2026, 10, 25), diary.rangeRequests.last())

        // Sunday: the week it ends is the new window, and the policy source says the day moved.
        now = LocalDateTime(LocalDate(2026, 11, 1), LocalTime(0, 5))
        policy.policy.value = TrendsFixtures.policy(clock = clock)

        assertEquals(LocalDate(2026, 10, 26) to LocalDate(2026, 11, 1), diary.rangeRequests.last())
        assertEquals(NutrientsSubtitle.WeekAverage("Oct 26 to Nov 1"), vm.state.subtitle)
    }

    @Test
    fun rangePicked_same_doesNothing() = runUnitTest {
        val vm = vm()
        vm.takeAction(NutrientsAction.RangePicked(NutrientsRange.Day))
        assertEquals(1, diary.dayRequests.size)
    }

    @Test
    fun modesTapped_opensModesAndWritesNothing() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = vm(settings)
        val events = backgroundScope.events(vm)
        vm.takeAction(NutrientsAction.ModesTapped)
        assertEquals(listOf<NutrientsEvent>(NutrientsEvent.OpenModes), events)
        assertEquals(0, settings.updates, "showing more only happens in Modes and numbers, behind the pause")
    }

    @Test
    fun backTapped_closes() = runUnitTest {
        val vm = vm()
        val events = backgroundScope.events(vm)
        vm.takeAction(NutrientsAction.BackTapped)
        assertEquals(listOf<NutrientsEvent>(NutrientsEvent.Close), events)
    }
}

private fun TrendsFixtures.weekWindowRange() =
    TrendsCalendar.weekWindow(today, DayOfWeek.MONDAY).let { it.start to it.end }
