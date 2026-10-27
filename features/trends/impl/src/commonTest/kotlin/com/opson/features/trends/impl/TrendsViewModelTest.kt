package com.opson.features.trends.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.Tone
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The Trends view model: inputs become state through the builder, the range
 * is written to settings and re-subscribes, and every other tap is an event. What the state says for each mode
 * is `TrendsStateBuilderTest`.
 */
class TrendsViewModelTest : CoroutineTest() {

    private val diary = FakeDiaryRepository()
    private val settings = FakeSettingsRepository()
    private val policy = FakePolicySource()

    private val health = FakeHealthRepository()

    private fun vm(healthOn: Boolean = false) =
        TrendsViewModel(diary, FakeWeightRepository(), settings, policy, TrendsFixtures.clock, health, healthFlag(healthOn), TrendsFixtures.timeZone)

    @Test
    fun healthFlagOff_noExpendedCard_andTheStoreIsNeverRead() = runUnitTest {
        val vm = vm(healthOn = false)
        assertNull(vm.state.expended)
        assertEquals(emptyList(), health.ranges)
    }

    @Test
    fun healthFlagOn_readsTheRangeUpToYesterday_andDrawsTheCard() = runUnitTest {
        val vm = vm(healthOn = true)
        assertNotNull(vm.state.expended)
        assertEquals(listOf(LocalDate(2026, 10, 19) to LocalDate(2026, 10, 24)), health.ranges)

        vm.takeAction(TrendsAction.RangePicked(TrendsRange.FourWeeks))
        assertEquals(LocalDate(2026, 9, 28) to LocalDate(2026, 10, 24), health.ranges.last(), "the new range is read")
    }

    @Test
    fun healthFlagOn_butNothingConnected_noCard() = runUnitTest {
        val notConnected = FakeHealthRepository(initial = HealthStatus.NotConnected)
        val vm = TrendsViewModel(
            diary, FakeWeightRepository(), settings, policy, TrendsFixtures.clock, notConnected, healthFlag(true), TrendsFixtures.timeZone,
        )
        assertNull(vm.state.expended)
        assertNotNull(vm.state.intake, "the rest of the page is there")
    }

    private fun CoroutineScope.events(vm: TrendsViewModel) = mutableListOf<TrendsEvent>().also { list ->
        launch { vm.eventFlow.collect { list += it } }
    }

    @Test
    fun inputsArrived_buildsTheWeek() = runUnitTest {
        val vm = vm()
        assertEquals(false, vm.state.loading)
        assertEquals(RangeLabel.Week("Oct 19 to 25"), vm.state.rangeLabel)
        assertEquals(7, vm.state.intake?.bars?.size)
        // Last week (Coast's last check) starts Oct 12, and new foods look 90 days before that.
        assertEquals(LocalDate(2026, 7, 14) to TrendsFixtures.today, diary.rangeRequests.single())
    }

    @Test
    fun rangePicked_writesTheRange_andThePageFollows() = runUnitTest {
        val vm = vm()
        vm.takeAction(TrendsAction.RangePicked(TrendsRange.FourWeeks))
        assertEquals(TrendsRange.FourWeeks, settings.settings.value.trendsRange)
        assertEquals(Settings(trendsRange = TrendsRange.FourWeeks), settings.settings.value, "nothing else is written")
        assertEquals(TrendsRange.FourWeeks, vm.state.range)
        assertEquals(4, vm.state.intake?.bars?.size)
        assertEquals(LocalDate(2026, 6, 30) to TrendsFixtures.today, diary.rangeRequests.last(), "four weeks back from Sep 28, plus the lookback")
    }

    @Test
    fun rangePicked_theSameRange_writesNothing() = runUnitTest {
        val vm = vm()
        vm.takeAction(TrendsAction.RangePicked(TrendsRange.Week))
        assertEquals(0, settings.updates)
    }

    @Test
    fun unrelatedSettingChange_doesNotResubscribe() = runUnitTest {
        vm()
        settings.settings.value = Settings(tone = Tone.Warm)
        assertEquals(1, diary.rangeRequests.size)
    }

    @Test
    fun weekStartChange_resubscribesToTheNewSpan() = runUnitTest {
        val vm = vm()
        settings.settings.value = Settings(weekStart = DayOfWeek.SATURDAY)
        assertEquals(LocalDate(2026, 7, 12) to TrendsFixtures.today, diary.rangeRequests.last())
        assertEquals(RangeLabel.Week("Oct 17 to 23"), vm.state.rangeLabel)
    }

    @Test
    fun policyChange_rebuilds() = runUnitTest {
        val vm = vm()
        assertNotNull(vm.state.intake)
        policy.policy.value = TrendsFixtures.policy(PresetId.Blind)
        assertNull(vm.state.intake)
    }

    @Test
    fun settingsTapped_opensTheSheet() = runUnitTest {
        val vm = vm()
        val events = backgroundScope.events(vm)
        vm.takeAction(TrendsAction.SettingsTapped)
        assertEquals(listOf<TrendsEvent>(TrendsEvent.OpenTrendsSettings), events)
    }

    @Test
    fun nutrientsCardTapped_opensTodaysWeekAverage() = runUnitTest {
        val vm = vm()
        val events = backgroundScope.events(vm)
        vm.takeAction(TrendsAction.NutrientsCardTapped)
        assertEquals(listOf<TrendsEvent>(TrendsEvent.OpenNutrients("2026-10-25")), events)
    }

    @Test
    fun patternsTapped_opensPatterns() = runUnitTest {
        val vm = vm()
        val events = backgroundScope.events(vm)
        vm.takeAction(TrendsAction.PatternsTapped)
        assertEquals(listOf<TrendsEvent>(TrendsEvent.OpenPatterns), events)
    }

    @Test
    fun weightCardTapped_opensWeight() = runUnitTest {
        val vm = vm()
        val events = backgroundScope.events(vm)
        vm.takeAction(TrendsAction.WeightCardTapped)
        assertEquals(listOf<TrendsEvent>(TrendsEvent.OpenWeight), events)
    }
}
