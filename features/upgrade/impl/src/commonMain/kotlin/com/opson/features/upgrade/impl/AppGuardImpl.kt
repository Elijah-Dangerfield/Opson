package com.opson.features.upgrade.impl

import com.opson.features.upgrade.AppGuard
import com.opson.features.upgrade.AppGuardState
import com.opson.features.upgrade.blocksApp
import com.opson.features.upgrade.storeUrl
import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.config.AppConfigRefresher
import com.opson.libraries.core.AppState
import com.opson.libraries.core.BuildInfo
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.opson.AppEvent
import com.opson.libraries.opson.AppEvents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The guard over the live config.
 *
 * Starts [AppGuardState.Normal] and stays there until the config stream says otherwise, so the
 * first frame never waits on it. The config repository already fetches on launch and on
 * foreground (throttled); on top of that, a phone held behind a stop fetches on every foreground
 * whatever the throttle, because the operator lifting maintenance should reach someone the moment
 * they look again, not five minutes later.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AppGuardImpl(
    appConfigFlow: AppConfigFlow,
    appState: AppState,
    appEvents: AppEvents,
    private val refresher: AppConfigRefresher,
    private val telemetry: AppGuardTelemetry,
    appScope: AppCoroutineScope,
) : AppGuard {

    private val updateDismissed = MutableStateFlow(false)

    override val state: StateFlow<AppGuardState> = combine(
        appConfigFlow,
        appState.isOffline,
        updateDismissed,
    ) { config, offline, dismissed ->
        AppGuardState.from(config, BuildInfo.versionCode, serverReachable = !offline, updateDismissed = dismissed)
    }
        .distinctUntilChanged()
        .onEach(telemetry::shown)
        .stateIn(appScope, SharingStarted.Eagerly, AppGuardState.Normal)

    override val storeUrl: String = storeUrl()

    init {
        appEvents.live()
            .filterIsInstance<AppEvent.OnForeground>()
            .filter { state.value.blocksApp }
            .onEach { refresher.refresh() }
            .launchIn(appScope)
    }

    override suspend fun retry() = refresher.refresh()

    override fun dismissUpdate() {
        if (updateDismissed.value) return
        updateDismissed.value = true
        telemetry.updateDismissed()
    }

    override fun storeOpened() = telemetry.storeOpened(state.value)
}
