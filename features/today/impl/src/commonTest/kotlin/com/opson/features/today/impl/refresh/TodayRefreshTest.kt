package com.opson.features.today.impl.refresh

import com.opson.features.today.impl.FakeCalendar
import com.opson.features.today.impl.FakeHealthRepository
import com.opson.features.today.impl.Toast
import com.opson.features.today.impl.healthFlag
import com.opson.libraries.diary.DiarySync
import com.opson.libraries.diary.SyncRejection
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.HealthRefresh
import com.opson.libraries.health.HealthSource
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_refresh_health_failed
import opson.libraries.resources.generated.resources.today_refresh_nothing_new_apple_health
import opson.libraries.resources.generated.resources.today_refresh_nothing_new_health_connect
import opson.libraries.resources.generated.resources.today_refresh_sync_failed
import opson.libraries.resources.generated.resources.today_refresh_up_to_date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/** Pull to refresh on Today: what it runs, and the one short line it says when nothing changed. */
class TodayRefreshTest : CoroutineTest() {

    @Test
    fun theMessage_saysWhatThePullFound() {
        val apple = HealthSource.AppleHealth
        assertNull(TodayRefreshMessage.of(synced = true, health = HealthRefresh.NewData, source = apple), "the screen shows what came in")
        assertEquals(Res.string.today_refresh_nothing_new_apple_health, TodayRefreshMessage.of(true, HealthRefresh.NothingNew, apple))
        assertEquals(Res.string.today_refresh_nothing_new_health_connect, TodayRefreshMessage.of(true, HealthRefresh.NothingNew, HealthSource.HealthConnect))
        assertEquals(Res.string.today_refresh_up_to_date, TodayRefreshMessage.of(true, health = null, source = apple))
        assertEquals(Res.string.today_refresh_health_failed, TodayRefreshMessage.of(true, HealthRefresh.Failed, apple))
        assertEquals(Res.string.today_refresh_sync_failed, TodayRefreshMessage.of(false, HealthRefresh.NewData, apple))
        assertEquals(Res.string.today_refresh_sync_failed, TodayRefreshMessage.of(synced = null, health = null, source = apple), "ran out of time")
    }

    @Test
    fun aPull_syncsAndReadsTheHealthAppTogether_thenSaysNothingNew() = runUnitTest {
        val sync = FakeSync()
        val health = FakeHealthRepository(connected = true, refreshAnswer = HealthRefresh.NothingNew)
        val vm = TodayRefreshViewModel(sync, health, health, healthFlag(true), FakeCalendar())
        val events = mutableListOf<TodayRefreshEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }

        vm.takeAction(TodayRefreshAction.Pulled)
        runCurrent()

        assertEquals(1, sync.syncs)
        assertEquals(1, health.refreshes)
        assertFalse(vm.state.refreshing)
        assertEquals(listOf<TodayRefreshEvent>(TodayRefreshEvent.ShowToast(Toast(Res.string.today_refresh_nothing_new_health_connect))), events)
    }

    @Test
    fun withReleaseHealthOff_thePullOnlySyncs() = runUnitTest {
        val health = FakeHealthRepository(connected = true)
        val vm = TodayRefreshViewModel(FakeSync(), health, health, healthFlag(false), FakeCalendar())
        vm.takeAction(TodayRefreshAction.Pulled)
        assertEquals(0, health.refreshes)
    }

    @Test
    fun aSyncThatHangs_letsTheSpinnerGo_andSaysItCouldntSync() = runUnitTest {
        val health = FakeHealthRepository(connected = true)
        val vm = TodayRefreshViewModel(FakeSync(hangs = true), health, health, healthFlag(true), FakeCalendar())
        val events = mutableListOf<TodayRefreshEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }

        vm.takeAction(TodayRefreshAction.Pulled)
        advanceTimeBy(11.seconds)
        runCurrent()

        assertFalse(vm.state.refreshing)
        assertEquals(listOf<TodayRefreshEvent>(TodayRefreshEvent.ShowToast(Toast(Res.string.today_refresh_sync_failed))), events)
    }

    private class FakeSync(private val hangs: Boolean = false) : DiarySync {
        var syncs = 0
        override val rejections: Flow<SyncRejection> = emptyFlow()
        override suspend fun syncNow(): Result<Unit> {
            syncs++
            if (hangs) awaitCancellation()
            return Result.success(Unit)
        }
        override suspend fun pendingChanges(): Int = 0
    }
}
