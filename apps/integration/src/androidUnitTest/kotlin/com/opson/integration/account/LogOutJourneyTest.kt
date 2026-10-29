package com.opson.integration.account

import com.opson.features.settings.impl.more.LogOutStep
import com.opson.features.settings.impl.more.MoreAction
import com.opson.features.settings.impl.more.MoreRow
import com.opson.features.settings.impl.more.MoreViewModel
import com.opson.integration.helpers.FaultProxy.Fault
import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.TestClient
import com.opson.integration.helpers.awaitState
import com.opson.libraries.config.impl.model.BasicMapAppConfig
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.identity.auth.AppleSignInCredential
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.reminders.RemindersEnabled
import com.opson.libraries.widgets.WidgetsEnabled
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * **Log out keeps what never left the phone.** Signing out wipes the outbox with the rest of the
 * account's data, so More's log out sends it first, and when anything is still queued afterwards
 * it asks again instead of wiping. Driven with the real sync engine and auth.
 */
class LogOutJourneyTest : IntegrationTest() {

    @Test
    fun theServerAway_logOutAsksAgain_andOnceItIsBackTheMealGoesUpBeforeTheSignOut() = integration {
        val proxy = faultProxy()
        val phone = client(userId = null, through = proxy, requestTimeoutMillis = 1_000)
        val user = phone.signInToAnAccount()
        proxy.failAll("/v1/sync", Fault.Hang)
        val meal = phone.log("Ramen on the subway", kcal = 600.0)
        val vm = phone.moreVm()

        vm.confirmLogOut()
        vm.stateFlow.awaitState(timeoutMs = 30_000) { it.logOut == LogOutStep.Unsynced }

        assertIs<AuthState.Authenticated>(phone.auth.current(), "nothing is signed out before the second yes")
        assertEquals(1, phone.pendingChanges(), "and the meal is still queued")

        vm.takeAction(MoreAction.LogOutCancelled) // Stay
        proxy.heal()
        vm.confirmLogOut()
        phone.auth.observe().first { it is AuthState.Unauthenticated }

        assertEquals(1L, server.countFor("log_entries", user), "the meal reached the account before the sign-out")
        assertEquals(meal.id, server.query("SELECT id::text AS id FROM log_entries WHERE user_id = ?::uuid", user).single()["id"])
    }

    /** A day further ahead than the server takes yet stays queued however well the sync goes. */
    @Test
    fun aSyncThatLeavesAFutureDayQueued_logOutAsksAgain() = integration {
        val phone = client(userId = null)
        phone.signInToAnAccount()
        phone.log("Birthday cake", kcal = 400.0, day = phone.today().plus(DatePeriod(days = 30)))
        val vm = phone.moreVm()

        vm.confirmLogOut()
        vm.stateFlow.awaitState(timeoutMs = 30_000) { it.logOut == LogOutStep.Unsynced }

        assertIs<AuthState.Authenticated>(phone.auth.current())
        assertEquals(1, phone.pendingChanges())
    }

    /**
     * Signs a fresh phone in with Apple, which makes a claimed account: a guest's log out sends
     * nothing, since no account can reach a guest's log again. Returns the account's id.
     */
    private suspend fun TestClient.signInToAnAccount(): String {
        assertEquals(SignInOutcome.Success, auth.signInWithApple(AppleSignInCredential(identityToken = "id-token", nonce = "nonce", authorizationCode = null)))
        syncNow().getOrThrow()
        return checkNotNull(userId)
    }

    private suspend fun TestClient.moreVm(): MoreViewModel {
        val config = BasicMapAppConfig(emptyMap<String, Any>())
        val vm = MoreViewModel(
            settingsRepository = settings,
            health = NoHealth,
            healthFlag = HealthReleaseFlag(config),
            remindersEnabled = RemindersEnabled(config),
            fastingRelease = FastingRelease { false },
            widgetsEnabled = WidgetsEnabled(config),
            auth = auth,
            diarySync = sync,
        )
        // The Account card, and with it Log out, joins the list once auth has answered.
        vm.stateFlow.awaitState { state -> state.groups.flatten().any { it is MoreRow.LogOut } }
        return vm
    }

    private fun MoreViewModel.confirmLogOut() {
        takeAction(MoreAction.RowTapped(state.groups.flatten().filterIsInstance<MoreRow.LogOut>().single()))
        takeAction(MoreAction.LogOutConfirmed)
    }

    /** The health flag is off, so More never reads this. */
    private object NoHealth : HealthRepository {
        override val source: HealthSource get() = error("unused")
        override val status: StateFlow<HealthStatus> = MutableStateFlow(HealthStatus.Unsupported)
        override suspend fun refresh() = Unit
        override suspend fun connect(): ConnectOutcome = error("unused")
        override suspend fun disconnect() = Unit
        override fun energy(day: LocalDate, timeZone: TimeZone): Flow<EnergyExpended?> = error("unused")
        override suspend fun energyBetween(from: LocalDate, to: LocalDate, timeZone: TimeZone): Map<LocalDate, EnergyExpended>? = null
        override fun openSourceSettings(): Boolean = false
    }
}
