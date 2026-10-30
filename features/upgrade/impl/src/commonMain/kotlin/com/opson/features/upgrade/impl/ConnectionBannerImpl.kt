package com.opson.features.upgrade.impl

import com.opson.features.upgrade.ConnectionBanner
import com.opson.features.upgrade.ConnectionStrip
import com.opson.libraries.core.AppState
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.opson.AppCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** The strip's three phases, before the copy is chosen. */
internal enum class OfflinePhase { Hidden, Offline, BackOnline }

/**
 * [offline] as the strip shows it. Going offline shows nothing until it has lasted [showAfter],
 * so switching from wifi to cellular never flashes the strip. Coming back after the strip showed
 * says [OfflinePhase.BackOnline] for [backOnlineFor], then hides; coming back before it showed
 * hides at once. Dropping again while "back online" is up goes straight back to offline, since it
 * was offline a moment ago.
 */
internal fun offlinePhases(
    offline: Flow<Boolean>,
    showAfter: Duration = ConnectionBannerImpl.SHOW_AFTER,
    backOnlineFor: Duration = ConnectionBannerImpl.BACK_ONLINE_FOR,
): Flow<OfflinePhase> = channelFlow {
    var phase = OfflinePhase.Hidden
    suspend fun go(next: OfflinePhase) {
        if (next != phase) {
            phase = next
            send(next)
        }
    }
    send(phase)
    offline.distinctUntilChanged().collectLatest { isOffline ->
        if (isOffline) {
            if (phase == OfflinePhase.Hidden) delay(showAfter)
            go(OfflinePhase.Offline)
        } else {
            if (phase != OfflinePhase.Hidden) {
                go(OfflinePhase.BackOnline)
                delay(backOnlineFor)
            }
            go(OfflinePhase.Hidden)
        }
    }
}

/**
 * The offline strip, from `AppState.isOffline` (no route to the internet, or our server not
 * answering). Its app events are `net.offline_banner_shown` and `net.offline_banner_cleared`; the
 * raw signal underneath keeps its own `net.offline_banner`.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class ConnectionBannerImpl(
    appState: AppState,
    appCache: AppCache,
    appScope: AppCoroutineScope,
) : ConnectionBanner {

    private val logger = KLog.withTag("OfflineBanner")
    private var shownAt: TimeMark? = null

    override val strip: StateFlow<ConnectionStrip> = combine(
        offlinePhases(appState.isOffline).onEach(::record),
        appCache.updates.map { it.hasUserOnboarded }.onStart { emit(true) }.distinctUntilChanged(),
    ) { phase, onboarded ->
        when (phase) {
            OfflinePhase.Hidden -> ConnectionStrip.Hidden
            OfflinePhase.Offline -> ConnectionStrip.Offline(loggingWorks = onboarded)
            OfflinePhase.BackOnline -> ConnectionStrip.BackOnline
        }
    }
        .distinctUntilChanged()
        .stateIn(appScope, SharingStarted.Eagerly, ConnectionStrip.Hidden)

    private fun record(phase: OfflinePhase) {
        when (phase) {
            OfflinePhase.Offline -> {
                shownAt = TimeSource.Monotonic.markNow()
                logger.logEvent("net.offline_banner_shown")
            }
            OfflinePhase.BackOnline -> {
                val shown = shownAt ?: return
                shownAt = null
                logger.logEvent("net.offline_banner_cleared", "shown_ms" to shown.elapsedNow().inWholeMilliseconds)
            }
            OfflinePhase.Hidden -> Unit
        }
    }

    internal companion object {
        /** Long enough to ride out a network handover, short enough that a real outage is named. */
        val SHOW_AFTER: Duration = 3.seconds

        val BACK_ONLINE_FOR: Duration = 2.seconds
    }
}
