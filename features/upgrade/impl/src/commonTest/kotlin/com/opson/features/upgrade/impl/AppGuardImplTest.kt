package com.opson.features.upgrade.impl

import com.opson.features.upgrade.AppGuardState
import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.AppConfigRefresher
import com.opson.libraries.config.AppConfigRepository
import com.opson.libraries.core.AppState
import com.opson.libraries.core.BuildInfo
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.opson.AppEvent
import com.opson.libraries.opson.AppEventBus
import com.opson.libraries.opson.AppEvents
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppGuardImplTest : CoroutineTest() {

    private val config = MutableStateFlow(configOf())
    private val offline = MutableStateFlow(false)
    private val foregrounds = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
    private val telemetry = RecordingTelemetry()
    private var refreshes = 0
    private val scope by lazy { AppCoroutineScope(dispatchers) }

    private val guard by lazy {
        AppGuardImpl(
            appConfigFlow = AppConfigFlow(FakeRepository(config)),
            appState = object : AppState {
                override val isOffline: StateFlow<Boolean> = offline
            },
            appEvents = AppEvents(FakeBus(foregrounds)),
            refresher = AppConfigRefresher { refreshes++ },
            telemetry = telemetry,
            appScope = scope,
        )
    }

    @AfterTest
    fun stopScope() = scope.cancel()

    @Test
    fun startsNormal_andFollowsTheConfigLive() = runUnitTest {
        assertEquals(AppGuardState.Normal, guard.state.value)

        config.value = upgrade("maintenanceMode" to "blocking", "maintenanceMessage" to "Back soon.")
        runCurrent()
        assertEquals(AppGuardState.MaintenanceBlocking("Back soon."), guard.state.value)

        config.value = upgrade("maintenanceMode" to "off")
        runCurrent()
        assertEquals(AppGuardState.Normal, guard.state.value, "lifting maintenance lifts the stop")
    }

    @Test
    fun goingOffline_turnsTheStopIntoTheBanner_andBack() = runUnitTest {
        config.value = upgrade("maintenanceMode" to "blocking", "maintenanceMessage" to "Back soon.")
        runCurrent()

        offline.value = true
        runCurrent()
        assertEquals(AppGuardState.MaintenanceBanner("Back soon.", loggingContinues = true), guard.state.value)

        offline.value = false
        runCurrent()
        assertEquals(AppGuardState.MaintenanceBlocking("Back soon."), guard.state.value)
    }

    @Test
    fun theVersionFloor_isThisBuildsVersionCode() = runUnitTest {
        config.value = upgrade("minSupportedVersionCode" to BuildInfo.versionCode)
        runCurrent()
        assertEquals(AppGuardState.Normal, guard.state.value, "at the floor is supported")

        config.value = upgrade("minSupportedVersionCode" to BuildInfo.versionCode + 1)
        runCurrent()
        assertEquals(AppGuardState.UpgradeRequired, guard.state.value)
    }

    @Test
    fun dismissingTheUpdate_putsItAway_once() = runUnitTest {
        config.value = upgrade("latestVersionCode" to BuildInfo.versionCode + 1)
        runCurrent()
        assertEquals(AppGuardState.UpdateAvailable, guard.state.value)

        guard.dismissUpdate()
        guard.dismissUpdate()
        runCurrent()
        assertEquals(AppGuardState.Normal, guard.state.value)
        assertEquals(1, telemetry.dismissed)
    }

    @Test
    fun aForeground_refetches_onlyWhileStopped() = runUnitTest {
        guard.state.value // start collecting
        foregrounds.emit(AppEvent.OnForeground(isColdBoot = false))
        runCurrent()
        assertEquals(0, refreshes, "an unblocked app leaves the timing to the config repository")

        config.value = upgrade("maintenanceMode" to "blocking")
        runCurrent()
        foregrounds.emit(AppEvent.OnForeground(isColdBoot = false))
        runCurrent()
        assertEquals(1, refreshes, "a stopped app checks again every time it is opened")
    }

    @Test
    fun eachStateSeenIsReportedOnce() = runUnitTest {
        guard.state.value
        config.value = upgrade("maintenanceMode" to "banner", "maintenanceMessage" to "m")
        runCurrent()
        config.value = upgrade("maintenanceMode" to "banner", "maintenanceMessage" to "m", "unrelated" to 1)
        runCurrent()
        assertEquals(listOf("maintenance_banner"), telemetry.shown)
    }

    private class RecordingTelemetry : AppGuardTelemetry {
        val shown = mutableListOf<String>()
        var dismissed = 0

        override fun shown(state: AppGuardState) {
            state.wire?.let(shown::add)
        }

        override fun storeOpened(from: AppGuardState) = Unit

        override fun updateDismissed() {
            dismissed++
        }
    }

    private class FakeRepository(private val flow: MutableStateFlow<AppConfigMap>) : AppConfigRepository {
        override fun config(): AppConfigMap = flow.value
        override fun configStream(): Flow<AppConfigMap> = flow
    }

    private class FakeBus(private val live: MutableSharedFlow<AppEvent>) : AppEventBus {
        override fun dispatch(event: AppEvent) = Unit
        override fun eventStream(): Flow<AppEvent> = live
        override fun liveEventStream(): Flow<AppEvent> = live
    }

    private fun upgrade(vararg entries: Pair<String, Any>) = configOf("upgrade" to entries.toMap())

    private fun configOf(vararg entries: Pair<String, Any>): AppConfigMap = object : AppConfigMap() {
        override val map: Map<String, *> = entries.toMap()

        // Two configs with the same entries are the same config, as the repository's decoded maps are.
        override fun equals(other: Any?) = other is AppConfigMap && other.map == map
        override fun hashCode() = map.hashCode()
    }
}
