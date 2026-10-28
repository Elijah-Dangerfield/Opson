package com.opson.features.trends.impl

import com.opson.features.trends.NutrientsRange
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.Nutrient
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The foods sheet follows the day or the week it was opened for. What each mode shows is `NutrientFoodsStateBuilderTest`. */
class NutrientFoodsViewModelTest : CoroutineTest() {

    private val diary = FakeDiaryRepository()

    private fun vm(range: NutrientsRange = NutrientsRange.Day) = NutrientFoodsViewModel(
        day = TrendsFixtures.today,
        nutrient = Nutrient.Micro(Micronutrient.Iron),
        range = range,
        diary = diary,
        settingsRepository = FakeSettingsRepository(),
        policySource = FakePolicySource(),
        clock = TrendsFixtures.clock,
        timeZone = TrendsFixtures.timeZone,
    )

    @Test
    fun day_followsThatDay() = runUnitTest {
        val vm = vm()
        assertEquals(listOf(TrendsFixtures.today), diary.dayRequests)
        assertFalse(vm.state.loading)
        assertEquals("Today", vm.state.subtitle)
    }

    @Test
    fun week_followsTheTrendsWeek() = runUnitTest {
        val vm = vm(NutrientsRange.WeekAverage)
        assertEquals(1, diary.rangeRequests.size)
        assertEquals("Oct 19 to 25", vm.state.subtitle)
    }

    @Test
    fun close_dismisses() = runUnitTest {
        val vm = vm()
        val events = mutableListOf<NutrientFoodsEvent>()
        backgroundScope.launch { vm.eventFlow.collect { events += it } }
        vm.takeAction(NutrientFoodsAction.CloseTapped)
        assertEquals(listOf<NutrientFoodsEvent>(NutrientFoodsEvent.Dismiss), events)
    }
}
