package com.opson.features.settings.impl.integrations

import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.Toast
import com.opson.features.settings.impl.eventsOf
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.PresetId
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.integrations_toast_connected
import opson.libraries.resources.generated.resources.integrations_toast_disconnected
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Integrations' view model, one test per action. What each state looks like is
 * [IntegrationsStateBuilderTest]; what the store does is `RealHealthRepositoryTest`.
 */
class IntegrationsViewModelTest : CoroutineTest() {

    private val policy = FakePolicySource(SettingsFixtures.policy(PresetId.Full))

    private fun vm(health: FakeHealthRepository) = IntegrationsViewModel(health, policy, FakeCalendar())

    @Test
    fun inputsArrive_forToday() = runUnitTest {
        val health = FakeHealthRepository(status = HealthStatus.Connected, energy = IntegrationsFixtures.energy)
        val vm = vm(health)
        assertEquals(Connection.Connected, vm.state.connection)
        assertIs<ExpendedCard.Shown>(vm.state.expended)
        assertEquals(listOf(IntegrationsFixtures.today), health.energyDays)
    }

    @Test
    fun theOpeningRead_neverPrompts() = runUnitTest {
        val health = FakeHealthRepository()
        vm(health)
        assertEquals(0, health.connects)
    }

    @Test
    fun policyArrives_andTheAmountsFollowIt() = runUnitTest {
        val vm = vm(FakeHealthRepository(status = HealthStatus.Connected, energy = IntegrationsFixtures.energy))
        policy.policy.value = SettingsFixtures.policy(PresetId.Blind)
        assertEquals(ExpendedCard.Hidden, vm.state.expended)
    }

    @Test
    fun connectTapped_granted_connectsAndSaysSo() = runUnitTest {
        val health = FakeHealthRepository(connectOutcome = ConnectOutcome.Connected, energy = IntegrationsFixtures.energy)
        val vm = vm(health)
        val events = eventsOf(vm)

        vm.takeAction(IntegrationsAction.ConnectTapped)

        assertEquals(1, health.connects)
        assertEquals(Connection.Connected, vm.state.connection)
        assertFalse(vm.state.denied)
        assertEquals(listOf(IntegrationsEvent.ShowToast(Toast(Res.string.integrations_toast_connected))), events)
    }

    @Test
    fun connectTapped_denied_saysWhereToChangeIt() = runUnitTest {
        val vm = vm(FakeHealthRepository(connectOutcome = ConnectOutcome.Denied))
        val events = eventsOf(vm)

        vm.takeAction(IntegrationsAction.ConnectTapped)

        assertTrue(vm.state.denied)
        assertEquals(Connection.NotConnected, vm.state.connection)
        assertTrue(events.isEmpty())
    }

    @Test
    fun connectTapped_needsInstall_opensThePlayListing() = runUnitTest {
        val vm = vm(FakeHealthRepository(connectOutcome = ConnectOutcome.NeedsInstall))
        val events = eventsOf(vm)

        vm.takeAction(IntegrationsAction.ConnectTapped)

        assertEquals(listOf(IntegrationsEvent.OpenLink(HEALTH_CONNECT_PLAY_URL)), events)
    }

    @Test
    fun connectTapped_afterADenial_clearsItWhileAsking() = runUnitTest {
        val health = FakeHealthRepository(connectOutcome = ConnectOutcome.Denied)
        val vm = vm(health)
        vm.takeAction(IntegrationsAction.ConnectTapped)
        health.connectOutcome = ConnectOutcome.Connected

        vm.takeAction(IntegrationsAction.ConnectTapped)

        assertFalse(vm.state.denied)
        assertEquals(Connection.Connected, vm.state.connection)
    }

    @Test
    fun disconnectTapped_disconnectsAndSaysSo() = runUnitTest {
        val health = FakeHealthRepository(status = HealthStatus.Connected, energy = IntegrationsFixtures.energy)
        val vm = vm(health)
        val events = eventsOf(vm)

        vm.takeAction(IntegrationsAction.DisconnectTapped)

        assertEquals(1, health.disconnects)
        assertEquals(Connection.NotConnected, vm.state.connection)
        assertEquals(null, vm.state.expended)
        assertEquals(listOf(IntegrationsEvent.ShowToast(Toast(Res.string.integrations_toast_disconnected))), events)
    }

    @Test
    fun getHealthConnectTapped_opensThePlayListing() = runUnitTest {
        val vm = vm(FakeHealthRepository(status = HealthStatus.NeedsInstall))
        val events = eventsOf(vm)
        vm.takeAction(IntegrationsAction.GetHealthConnectTapped)
        assertEquals(listOf(IntegrationsEvent.OpenLink(HEALTH_CONNECT_PLAY_URL)), events)
    }

    @Test
    fun openSourceTapped_opensTheStoresOwnScreen() = runUnitTest {
        val health = FakeHealthRepository()
        vm(health).takeAction(IntegrationsAction.OpenSourceTapped)
        assertEquals(1, health.settingsOpened)
    }

    @Test
    fun screenResumed_rechecksWithoutPrompting() = runUnitTest {
        val health = FakeHealthRepository()
        val vm = vm(health)
        val before = health.refreshes
        vm.takeAction(IntegrationsAction.ScreenResumed)
        assertEquals(before + 1, health.refreshes)
        assertEquals(0, health.connects)
    }

    @Test
    fun backTapped_closes() = runUnitTest {
        val vm = vm(FakeHealthRepository())
        val events = eventsOf(vm)
        vm.takeAction(IntegrationsAction.BackTapped)
        assertEquals(listOf<IntegrationsEvent>(IntegrationsEvent.Close), events)
    }
}
