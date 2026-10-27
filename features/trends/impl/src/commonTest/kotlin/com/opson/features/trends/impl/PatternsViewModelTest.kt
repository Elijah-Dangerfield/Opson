package com.opson.features.trends.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The Patterns view model: four weeks plus the new-food lookback come in, a mode change rebuilds, back closes. */
class PatternsViewModelTest : CoroutineTest() {

    private val diary = FakeDiaryRepository()
    private val policy = FakePolicySource()

    private fun vm(checkIn: Boolean = false, settings: Settings = Settings()) =
        PatternsViewModel(diary, FakeSettingsRepository(settings), policy, TrendsFixtures.clock, checkInFlag(checkIn), TrendsFixtures.timeZone)

    @Test
    fun hunger_fromTheCheckIns_onlyWithTheFlagAndTheSettingOn() = runUnitTest {
        assertEquals(HungerSource.MealTags, vm(checkIn = false).state.hunger?.source)
        assertEquals(HungerSource.CheckIns, vm(checkIn = true).state.hunger?.source)
        assertEquals(HungerSource.MealTags, vm(checkIn = true, settings = Settings(checkIn = false)).state.hunger?.source)
    }

    @Test
    fun inputsArrive_fourWeeksAndTheLookbackBeforeThem() = runUnitTest {
        val vm = vm()
        assertEquals(false, vm.state.loading)
        assertNotNull(vm.state.timing)
        // Sep 28 to Oct 25, and 90 days before Sep 28 for new foods.
        assertEquals(LocalDate(2026, 6, 30) to TrendsFixtures.today, diary.rangeRequests.single())
    }

    @Test
    fun policyChange_rebuilds() = runUnitTest {
        val vm = vm()
        assertNotNull(vm.state.sizes)
        policy.policy.value = TrendsFixtures.policy(PresetId.NoNumbers)
        assertNull(vm.state.sizes)
        assertNotNull(vm.state.often)
    }

    @Test
    fun backTapped_closes() = runUnitTest {
        val vm = vm()
        val events = mutableListOf<PatternsEvent>()
        backgroundScope.launch { vm.eventFlow.collect { events += it } }
        vm.takeAction(PatternsAction.BackTapped)
        assertEquals(listOf<PatternsEvent>(PatternsEvent.Close), events)
    }
}
