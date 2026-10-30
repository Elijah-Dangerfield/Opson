@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.trends.impl.recap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opson.features.today.TodayCardContribution
import com.opson.features.trends.RecapRoute
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MinuteTicker
import com.opson.libraries.diary.RecapPeriod
import com.opson.libraries.diary.RecapsRelease
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.navigation.Router
import com.opson.libraries.opson.UserScopedClearer
import com.opson.libraries.prompts.OptOut
import com.opson.libraries.prompts.OptOuts
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import com.opson.libraries.ui.snackbar.showSnackBar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.recap_banner_off
import org.jetbrains.compose.resources.getString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * When Today says "Your week is ready" (owner feedback, 2026-10-30): from the first open after a
 * week or a month ends, for [SHOWN_DAYS] days, until the person opens that recap or says Not now.
 * Once per period. A month that just ended goes first; its week can follow once it is answered.
 * Only a period with [MIN_LOGGED_DAYS] logged days in it: a recap of a week someone was away is
 * not news, and Trends still has it.
 */
object RecapBanners {
    /** The first days after a period ends; after that it waits in Trends. */
    const val SHOWN_DAYS = 3

    const val MIN_LOGGED_DAYS = 2

    /** The recap to offer on [today], or null. [handled] are the periods opened or dismissed, by [RecapPeriod.key]. */
    fun pending(today: LocalDate, weekStart: DayOfWeek, handled: Set<String>, days: List<Day>): RecapPeriod? =
        listOf(RecapPeriod.lastMonth(today), RecapPeriod.lastWeek(today, weekStart)).firstOrNull { period ->
            today <= period.end.plus(SHOWN_DAYS, DateTimeUnit.DAY) &&
                period.key !in handled &&
                days.count { it.date >= period.start && it.date <= period.end && it.countsInTrends } >= MIN_LOGGED_DAYS
        }

    /** The days [pending] reads: the month before today's, which holds the week before too. */
    fun from(today: LocalDate, weekStart: DayOfWeek): LocalDate =
        minOf(RecapPeriod.lastMonth(today).start, RecapPeriod.lastWeek(today, weekStart).start)
}

/** Which recaps this phone has offered and the person answered, by [RecapPeriod.key]. */
@Serializable
data class RecapBannerLedger(
    /** Opened (here or from anywhere) or dismissed: never offered again. */
    val handled: Set<String> = emptySet(),
    /** Counted as a showing of [OptOut.RecapBanner] once, however many times Today redraws it. */
    val counted: Set<String> = emptySet(),
)

/**
 * The banner's memory, on this phone and never synced: a second phone may offer a week once more,
 * which costs nothing. A user change wipes it. Keys only; a period's key is a date.
 */
@HiddenFromObjC
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = UserScopedClearer::class, multibinding = true)
@Inject
class RecapBannerStore(cacheFactory: CacheFactory) : UserScopedClearer {
    private val cache: Cache<RecapBannerLedger> = cacheFactory.persistent(
        name = "recap_banner",
        serializer = versionedJsonSerializer(defaultValue = { RecapBannerLedger() }),
    )

    val handled: Flow<Set<String>> = cache.updates.map { it.handled }.distinctUntilChanged()

    /** Opened from anywhere, or dismissed: the banner for it goes. */
    suspend fun seen(period: RecapPeriod) {
        cache.update { it.copy(handled = it.handled + period.key) }
    }

    /** True the first time [period] is shown, so a showing counts once. */
    suspend fun firstShowing(period: RecapPeriod): Boolean {
        var first = false
        cache.update { ledger ->
            first = period.key !in ledger.counted
            if (first) ledger.copy(counted = ledger.counted + period.key) else ledger
        }
        return first
    }

    override suspend fun clear(previousUserId: String) {
        cache.clear()
    }
}

/** The banner: the period it offers, and whether this showing offers "I'd rather not see these". */
data class RecapBannerState(val period: RecapPeriod? = null, val offersOptOut: Boolean = false)

sealed interface RecapBannerAction {
    data class Pending(val period: RecapPeriod?) : RecapBannerAction
    data object OpenTapped : RecapBannerAction
    data object NotNowTapped : RecapBannerAction
    data object TurnOffTapped : RecapBannerAction
}

sealed interface RecapBannerEvent {
    data class Open(val period: RecapPeriod) : RecapBannerEvent

    /** The card is off; a snackbar says where it comes back. */
    data object TurnedOff : RecapBannerEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class RecapBannerViewModel(
    private val store: RecapBannerStore,
    settings: SettingsRepository,
    diary: DiaryRepository,
    private val calendar: LocalCalendar,
    ticker: MinuteTicker,
    recaps: RecapsRelease,
    private val optOuts: OptOuts = OptOuts.None,
) : SEAViewModel<RecapBannerState, RecapBannerEvent, RecapBannerAction>(initialStateArg = RecapBannerState()) {

    init {
        if (recaps.isOn()) {
            val today = ticker.ticks().map { calendar.today() }.distinctUntilChanged()
            val on = settings.settings.map { it.recapBanner to it.weekStart }.distinctUntilChanged()
            viewModelScope.launch {
                combine(today, on) { date, setting -> date to setting }
                    .flatMapLatest { (date, setting) ->
                        val (shown, weekStart) = setting
                        if (!shown) {
                            flowOf(null)
                        } else {
                            combine(diary.observeDays(RecapBanners.from(date, weekStart), date), store.handled) { days, handled ->
                                RecapBanners.pending(date, weekStart, handled, days)
                            }
                        }
                    }
                    .distinctUntilChanged()
                    .collect { takeAction(RecapBannerAction.Pending(it)) }
            }
        }
    }

    override suspend fun handleAction(action: RecapBannerAction) {
        when (action) {
            is RecapBannerAction.Pending -> {
                val period = action.period
                val offers = when {
                    period == null -> false
                    store.firstShowing(period) -> optOuts.showing(OptOut.RecapBanner)
                    else -> optOuts.offered(OptOut.RecapBanner)
                }
                action.updateState { RecapBannerState(period, offers) }
            }
            RecapBannerAction.OpenTapped -> state.period?.let {
                store.seen(it)
                sendEvent(RecapBannerEvent.Open(it))
            }
            RecapBannerAction.NotNowTapped -> state.period?.let { store.seen(it) }
            RecapBannerAction.TurnOffTapped -> state.period?.let {
                store.seen(it)
                optOuts.turnOff(OptOut.RecapBanner)
                sendEvent(RecapBannerEvent.TurnedOff)
            }
        }
    }
}

/** "Your week is ready" in Today's list, on today only (`release.recaps`). */
@HiddenFromObjC
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class RecapTodayCardContribution(
    private val viewModelFactory: () -> RecapBannerViewModel,
    private val calendar: LocalCalendar,
) : TodayCardContribution {
    override val key = "recap"

    @Composable
    override fun card(day: LocalDate, router: Router): (@Composable () -> Unit)? {
        val viewModel: RecapBannerViewModel = viewModel { viewModelFactory() }
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        viewModel.ObserveEvents { event ->
            when (event) {
                is RecapBannerEvent.Open -> router.navigate(RecapRoute(event.period.kind.wire, event.period.start.toString()))
                RecapBannerEvent.TurnedOff -> showSnackBar(message = getString(Res.string.recap_banner_off))
            }
        }
        val period = state.period ?: return null
        if (day != calendar.today()) return null
        val offersOptOut = state.offersOptOut
        return {
            RecapBannerCard(
                period = period,
                onOpen = { viewModel.takeAction(RecapBannerAction.OpenTapped) },
                onNotNow = { viewModel.takeAction(RecapBannerAction.NotNowTapped) },
                onTurnOff = if (offersOptOut) ({ viewModel.takeAction(RecapBannerAction.TurnOffTapped) }) else null,
            )
        }
    }
}
