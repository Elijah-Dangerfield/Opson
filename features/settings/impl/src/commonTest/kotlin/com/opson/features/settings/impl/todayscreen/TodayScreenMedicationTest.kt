package com.opson.features.settings.impl.todayscreen

import com.opson.features.settings.impl.integrations.healthFlag
import com.opson.libraries.health.ExerciseRelease
import com.opson.features.settings.impl.FakeBurnedRepository
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.goalsAndEnergyFlag
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.features.settings.impl.todaySummaryFlag
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.medication.MedicationTodaySwitch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** More › Today screen's Medication row (`release.medication`), the plan's "Show on Today". */
class TodayScreenMedicationTest : CoroutineTest() {

    private class FakeSwitch(initial: Boolean?) : MedicationTodaySwitch {
        val value = MutableStateFlow(initial)
        override val shown: Flow<Boolean?> = value
        override suspend fun set(on: Boolean) {
            value.value = on
        }
    }

    private fun vm(switch: MedicationTodaySwitch) = TodayScreenSettingsViewModel(
        FakeSettingsRepository(),
        FakeBurnedRepository(),
        FakeCalendar(),
        goalsAndEnergyFlag(true),
        todaySummaryFlag(true),
        FastingRelease { false },
        healthFlag(false),
        ExerciseRelease { false },
        switch,
    )

    @Test
    fun noRowWhileMedicationIsNotTracked() = runUnitTest {
        assertNull(vm(FakeSwitch(null)).state.medication)
        assertNull(vm(MedicationTodaySwitch.None).state.medication)
    }

    @Test
    fun theRowShowsThePlansSwitch_andWritesIt() = runUnitTest {
        val switch = FakeSwitch(true)
        val vm = vm(switch)
        assertEquals(true, vm.state.medication)

        vm.takeAction(TodayScreenSettingsAction.MedicationToggled(false))
        assertEquals(false, switch.value.value)
        assertEquals(false, vm.state.medication)
    }

    @Test
    fun aToggleWithNoRowWritesNothing() = runUnitTest {
        val switch = FakeSwitch(null)
        vm(switch).takeAction(TodayScreenSettingsAction.MedicationToggled(true))
        assertNull(switch.value.value)
    }
}
