package com.opson.features.trends.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.Tone
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.flowroutines.testing.CoroutineTest
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
 * re-subscribes, and every tap is an event. What the state says for each mode
 * is `TrendsStateBuilderTest`.
 */
class TrendsViewModelTest : CoroutineTest() {

    private val diary = FakeDiaryRepository()
    private val settings = FakeSettingsRepository()
    private val policy = FakePolicySource()

    private fun vm() = TrendsViewModel(diary, FakeWeightRepository(), settings, policy, TrendsFixtures.clock, TrendsFixtures.timeZone)

    private fun CoroutineScope.events(vm: TrendsViewModel) = mutableListOf<TrendsEvent>().also { list ->
        launch { vm.eventFlow.collect { list += it } }
    }

    @Test
    fun inputsArrived_buildsTheWeek() = runUnitTest {
        val vm = vm()
        assertEquals(false, vm.state.loading)
        assertEquals(RangeLabel.Week("Oct 19 to 25"), vm.state.rangeLabel)
        assertEquals(7, vm.state.intake?.bars?.size)
        assertEquals(LocalDate(2026, 7, 21) to TrendsFixtures.today, diary.rangeRequests.single())
    }

    @Test
    fun rangeChange_rebuilds_withoutResubscribingInsideTheLookback() = runUnitTest {
        val vm = vm()
        settings.settings.value = Settings(trendsRange = TrendsRange.FourWeeks)
        assertEquals(4, vm.state.intake?.bars?.size)
        settings.settings.value = Settings(trendsRange = TrendsRange.FourWeeks, tone = Tone.Warm)
        assertEquals(1, diary.rangeRequests.size, "4 weeks sits inside the lookback, and a tone change moves nothing")
    }

    @Test
    fun weekStartChange_resubscribesToTheNewSpan() = runUnitTest {
        val vm = vm()
        settings.settings.value = Settings(weekStart = DayOfWeek.SATURDAY)
        assertEquals(LocalDate(2026, 7, 19) to TrendsFixtures.today, diary.rangeRequests.last())
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
    fun weightCardTapped_opensWeight() = runUnitTest {
        val vm = vm()
        val events = backgroundScope.events(vm)
        vm.takeAction(TrendsAction.WeightCardTapped)
        assertEquals(listOf<TrendsEvent>(TrendsEvent.OpenWeight), events)
    }
}
