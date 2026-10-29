package com.opson.features.today.impl.refresh

import com.opson.features.today.impl.Toast
import com.opson.libraries.diary.DiarySync
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.HealthRecords
import com.opson.libraries.health.HealthRefresh
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_refresh_health_failed
import opson.libraries.resources.generated.resources.today_refresh_nothing_new_apple_health
import opson.libraries.resources.generated.resources.today_refresh_nothing_new_health_connect
import opson.libraries.resources.generated.resources.today_refresh_sync_failed
import opson.libraries.resources.generated.resources.today_refresh_up_to_date
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/*
 * Pull to refresh on Today: one gesture for "is this current?". It syncs the diary (another
 * phone's logs) and reads the health app again, together, then says something short when
 * nothing changed, because a watch that hasn't synced to the health app yet looks exactly like
 * a pull that did nothing. Its own view model, so Today's state and builder stay as they are.
 */

data class TodayRefreshState(val refreshing: Boolean = false)

sealed interface TodayRefreshAction {
    data object Pulled : TodayRefreshAction
}

sealed interface TodayRefreshEvent {
    data class ShowToast(val toast: Toast) : TodayRefreshEvent
}

/** What a pull says when it is done. Null says nothing: something new came in, and the screen shows it. */
object TodayRefreshMessage {
    /**
     * [synced] is the diary sync's answer, null when it ran out of time. [health] is the health
     * read's, null with no health app to read (not connected, or `release.health` off).
     */
    fun of(synced: Boolean?, health: HealthRefresh?, source: HealthSource): StringResource? = when {
        synced != true -> Res.string.today_refresh_sync_failed
        health == HealthRefresh.Failed -> Res.string.today_refresh_health_failed
        health == HealthRefresh.NewData -> null
        health == HealthRefresh.NothingNew -> when (source) {
            HealthSource.AppleHealth -> Res.string.today_refresh_nothing_new_apple_health
            HealthSource.HealthConnect -> Res.string.today_refresh_nothing_new_health_connect
        }
        else -> Res.string.today_refresh_up_to_date
    }
}

@Inject
class TodayRefreshViewModel(
    private val sync: DiarySync,
    private val records: HealthRecords,
    private val health: HealthRepository,
    private val healthFlag: HealthReleaseFlag,
    private val calendar: LocalCalendar,
) : SEAViewModel<TodayRefreshState, TodayRefreshEvent, TodayRefreshAction>(initialStateArg = TodayRefreshState()) {

    /** How long the spinner may hold. Past it the pull stops waiting, and the next foreground or pull runs the sync again. */
    internal var timeout: Duration = TIMEOUT

    override suspend fun handleAction(action: TodayRefreshAction) {
        when (action) {
            TodayRefreshAction.Pulled -> {
                if (state.refreshing) return
                action.updateState { it.copy(refreshing = true) }
                val result = withTimeoutOrNull(timeout) {
                    coroutineScope {
                        val synced = async { sync.syncNow().isSuccess }
                        val read = async { if (healthFlag()) records.refreshNow(calendar.today(), calendar.timeZone()) else null }
                        synced.await() to read.await()
                    }
                }
                action.updateState { it.copy(refreshing = false) }
                val health = result?.second?.takeIf { it != HealthRefresh.NotConnected }
                TodayRefreshMessage.of(result?.first, health, this.health.source)?.let { sendEvent(TodayRefreshEvent.ShowToast(Toast(it))) }
            }
        }
    }

    private companion object {
        val TIMEOUT: Duration = 10.seconds
    }
}
