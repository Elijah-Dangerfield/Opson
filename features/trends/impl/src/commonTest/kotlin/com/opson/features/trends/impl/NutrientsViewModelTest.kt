package com.opson.features.trends.impl

import com.opson.features.trends.NutrientsRange
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The Nutrients detail view model: the range switch re-subscribes, "Show
 * vitamins and minerals" goes through the numbers rule (pause included), and
 * back closes. What each mode shows is `NutrientsStateBuilderTest`.
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
        numbers = FakeNumbersChanger(settings),
        clock = TrendsFixtures.clock,
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
    fun rangePicked_same_doesNothing() = runUnitTest {
        val vm = vm()
        vm.takeAction(NutrientsAction.RangePicked(NutrientsRange.Day))
        assertEquals(1, diary.dayRequests.size)
    }

    @Test
    fun showVitamins_pauseOff_turnsMicrosOn() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = vm(settings)
        val events = backgroundScope.events(vm)
        vm.takeAction(NutrientsAction.ShowVitaminsTapped)
        assertEquals(Visibility.Show, settings.settings.value.numbers.overrides[Metric.Micros])
        assertNotNull(settings.settings.value.numbers.changedAt)
        assertEquals(emptyList(), events)
    }

    @Test
    fun showVitamins_pauseOn_opensThePauseAndWritesNothing() = runUnitTest {
        val paused = Settings(numbers = NumbersSettings(preset = PresetId.Full, pauseBeforeMore = true))
        val settings = FakeSettingsRepository(paused)
        val vm = vm(settings)
        val events = backgroundScope.events(vm)
        vm.takeAction(NutrientsAction.ShowVitaminsTapped)
        assertEquals(listOf<NutrientsEvent>(NutrientsEvent.OpenPause(NumbersChange.Override(Metric.Micros, Visibility.Show))), events)
        assertEquals(paused, settings.settings.value)
        assertEquals(0, settings.updates)
        assertNull(settings.settings.value.numbers.overrides[Metric.Micros])
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
