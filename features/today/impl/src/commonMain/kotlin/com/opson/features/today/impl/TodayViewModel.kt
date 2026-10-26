package com.opson.features.today.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.logger.LogTab
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.FastingRepository
import com.opson.libraries.diary.FastingStatus
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MinuteTicker
import com.opson.libraries.diary.RecentLogEvents
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.nutrition.TodayLayout
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_toast_added_to
import opson.libraries.resources.generated.resources.today_toast_cleared
import opson.libraries.resources.generated.resources.today_toast_copied_previous
import opson.libraries.resources.generated.resources.today_toast_copied_tomorrow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * The Today tab. Reads the selected day (and the one before it), the settings and the live
 * policy, rebuilds the state through [TodayStateBuilder] on every arrival, and turns taps into
 * diary writes and navigation events.
 *
 * When food lands on the day being shown (from the logger, or copied here), the meal it went
 * into is outlined for a moment and scrolled to (design v3, 3.9). When the logger says what it
 * added ([RecentLogEvents]), Today also offers Undo for what is left of its six seconds,
 * measured on [clock] from when the logger wrote, not from when Today noticed.
 *
 * [model] holds what was read and whether the menu is open; only [handleAction] changes it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class TodayViewModel(
    private val diary: DiaryRepository,
    private val settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    private val selectedDay: SelectedDay,
    private val calendar: LocalCalendar,
    private val recentLogEvents: RecentLogEvents,
    private val clock: Clock,
    private val health: HealthRepository,
    private val healthFlag: HealthReleaseFlag,
    private val fasting: FastingRepository,
    private val fastingRelease: FastingRelease,
    private val ticker: MinuteTicker,
) : SEAViewModel<TodayState, TodayEvent, TodayAction>(
    initialStateArg = TodayState(day = selectedDay.date.value),
) {
    private var model = Model(policy = policySource.policy.value)
    private var highlightJob: Job? = null
    private var undoJob: Job? = null

    private data class Model(
        val policy: VisibilityPolicy,
        val inputs: TodayInputs? = null,
        val menuOpen: Boolean = false,
        val clearArmed: Boolean = false,
        val highlight: MealSlot? = null,
        /** The items already on screen for this date, to tell new ones apart. */
        val seen: Pair<LocalDate, Set<String>>? = null,
        /** What the logger just added, while its Undo is open. */
        val added: LoggedToMeal? = null,
        /** "Not today" or "I tried it" on the food bridge, for this date. Kept nowhere else. */
        val bridgeSkippedOn: LocalDate? = null,
        val fasting: FastingStatus? = null,
    )

    init {
        viewModelScope.launch {
            inputs(policySource).collect { takeAction(TodayAction.InputsArrived(it)) }
        }
        viewModelScope.launch {
            policySource.policy.collect { takeAction(TodayAction.PolicyArrived(it)) }
        }
        viewModelScope.launch {
            // Paired with the date so an add for another day is offered when that day comes up.
            combine(recentLogEvents.latest, selectedDay.date) { event, shown -> event?.let { TodayAction.LoggedArrived(it, shown) } }
                .filterNotNull()
                .collect { takeAction(it) }
        }
        viewModelScope.launch {
            fastingCard().collect { takeAction(TodayAction.FastingArrived(it)) }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override suspend fun handleAction(action: TodayAction) {
        when (action) {
            is TodayAction.InputsArrived -> arrived(action)
            is TodayAction.PolicyArrived -> action.rebuild { copy(policy = action.policy) }
            TodayAction.PreviousDayTapped -> {
                action.rebuild { copy(menuOpen = false, clearArmed = false) }
                selectedDay.select(day().minus(DatePeriod(days = 1)))
            }
            TodayAction.NextDayTapped -> if (canGoForward()) selectedDay.select(day().plus(DatePeriod(days = 1)))
            TodayAction.DayLabelTapped, TodayAction.CalendarTapped -> sendEvent(TodayEvent.OpenCalendar(day()))
            TodayAction.NutrientsTapped -> sendEvent(TodayEvent.OpenNutrients(day()))
            TodayAction.RevealedTapped, TodayAction.CloseDayCardTapped -> if (!isFuture()) sendEvent(TodayEvent.OpenDayClose(day()))
            is TodayAction.MealTileTapped -> sendEvent(TodayEvent.OpenLogger(day(), action.slot))
            is TodayAction.RhythmLogTapped -> sendEvent(TodayEvent.OpenLogger(day(), action.slot))
            is TodayAction.PlanItTapped -> sendEvent(TodayEvent.OpenLogger(day(), action.slot))
            is TodayAction.MealPlusTapped -> sendEvent(TodayEvent.OpenMealSheet(day(), action.slot))
            is TodayAction.CopyYesterdayMealTapped -> {
                diary.copySlot(fromDay = day().minus(DatePeriod(days = 1)), toDay = day(), slot = action.slot)
                sendEvent(TodayEvent.ShowToast(Toast(Res.string.today_toast_added_to, listOf(action.slot.sentenceName))))
            }
            is TodayAction.SavedMealsTapped -> sendEvent(TodayEvent.OpenLog(day(), action.slot, LogTab.Saved))
            is TodayAction.FeelPromptTapped -> sendEvent(TodayEvent.OpenMealFeel(day(), action.slot))
            is TodayAction.ItemTapped -> sendEvent(TodayEvent.OpenItemSheet(day(), action.entryId))
            TodayAction.WaterPlusTapped -> sendEvent(TodayEvent.OpenWaterSheet(day()))
            is TodayAction.AlreadyAteTapped -> diary.setAteNoDetails(day(), action.slot, ate = true)
            TodayAction.HighlightFaded -> action.rebuild { copy(highlight = null) }
            is TodayAction.LoggedArrived -> loggedArrived(action)
            TodayAction.UndoTapped -> model.added?.let { event ->
                undoJob?.cancel()
                action.rebuild { copy(added = null) }
                recentLogEvents.undo(event)
            }
            is TodayAction.UndoExpired -> if (model.added == action.event) action.rebuild { copy(added = null) }
            TodayAction.TrackAgainTapped -> diary.setUntracked(day(), untracked = false)
            TodayAction.BridgeTriedTapped -> {
                val bridge = model.inputs?.explorer?.bridge
                action.rebuild { copy(bridgeSkippedOn = day()) }
                val foodId = bridge?.foodId ?: bridge?.customFoodId
                sendEvent(
                    if (foodId == null) {
                        TodayEvent.OpenLogger(day(), slot = null)
                    } else {
                        TodayEvent.OpenLoggerWithFood(day(), foodId, isCustom = bridge?.foodId == null)
                    },
                )
            }
            TodayAction.BridgeNotTodayTapped -> action.rebuild { copy(bridgeSkippedOn = day()) }
            is TodayAction.FastingArrived -> {
                action.rebuild { copy(fasting = action.status) }
                // A card on show means the timer is on: start the evening's fast once the window closes.
                if (action.status != null) fasting.startDue()
            }
            TodayAction.FastingCardTapped -> sendEvent(TodayEvent.OpenFasting)
            TodayAction.EndFastTapped -> fasting.end()

            TodayAction.DayMenuTapped -> action.rebuild { copy(menuOpen = true, clearArmed = false) }
            TodayAction.MenuDismissed -> action.closeMenu()
            TodayAction.DayCloseMenuTapped -> action.closeMenuThen { if (!isFuture()) sendEvent(TodayEvent.OpenDayClose(day())) }
            TodayAction.DayNutrientsTapped -> action.closeMenuThen { sendEvent(TodayEvent.OpenNutrients(day())) }
            TodayAction.CopyYesterdayTapped -> action.closeMenuThen {
                diary.copyDay(from = day().minus(DatePeriod(days = 1)), to = day())
                sendEvent(TodayEvent.ShowToast(Toast(Res.string.today_toast_copied_previous)))
            }
            TodayAction.CopyToTomorrowTapped -> action.closeMenuThen {
                if (!isFuture()) {
                    diary.copyDay(from = day(), to = day().plus(DatePeriod(days = 1)))
                    sendEvent(TodayEvent.ShowToast(Toast(Res.string.today_toast_copied_tomorrow)))
                }
            }
            TodayAction.DayNoteTapped -> action.closeMenuThen { sendEvent(TodayEvent.OpenDayNote(day())) }
            TodayAction.UntrackedMenuTapped -> action.closeMenuThen {
                diary.setUntracked(day(), untracked = model.inputs?.day?.untracked != true)
            }
            TodayAction.EditCardsTapped -> action.closeMenuThen { sendEvent(TodayEvent.OpenTodayCards) }
            TodayAction.ClearDayTapped -> if (model.clearArmed) {
                action.closeMenuThen {
                    diary.clearDay(day())
                    sendEvent(TodayEvent.ShowToast(Toast(Res.string.today_toast_cleared)))
                }
            } else {
                action.rebuild { copy(clearArmed = true) }
            }
        }
    }

    private suspend fun arrived(action: TodayAction.InputsArrived) {
        val day = action.inputs.day
        val ids = day.allItems.mapTo(HashSet()) { it.id }
        val previous = model.seen
        val added = if (previous != null && previous.first == day.date) day.allItems.filter { it.id !in previous.second } else emptyList()
        val slot = added.maxByOrNull { it.loggedAt }?.slot
        val sameDate = previous?.first == day.date
        action.rebuild {
            copy(
                inputs = action.inputs,
                seen = day.date to ids,
                highlight = slot ?: highlight.takeIf { sameDate },
                // Another date drops the Undo bar (the add stays in the diary), unless the add was for it.
                added = this.added?.takeIf { it.day == day.date },
            )
        }
        if (slot != null) fadeHighlight()
    }

    /**
     * Offers Undo for [action]'s add when it is for the day on show and its window is still open;
     * an add whose window has closed is consumed and shows nothing. An add for another day is
     * left alone for that day.
     */
    private suspend fun loggedArrived(action: TodayAction.LoggedArrived) {
        val event = action.event
        val now = clock.now()
        if (!event.undoOpenAt(now)) {
            recentLogEvents.consumed(event)
            return
        }
        if (event.day != action.shown) return
        recentLogEvents.consumed(event)
        action.rebuild { copy(added = event, highlight = event.slot) }
        fadeHighlight()
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            delay(LoggedToMeal.UNDO_WINDOW - (now - event.at))
            takeAction(TodayAction.UndoExpired(event))
        }
    }

    private fun fadeHighlight() {
        highlightJob?.cancel()
        highlightJob = viewModelScope.launch {
            delay(HIGHLIGHT_FOR)
            takeAction(TodayAction.HighlightFaded)
        }
    }

    private fun day(): LocalDate = model.inputs?.day?.date ?: state.day

    private fun isFuture(): Boolean = model.inputs?.let { it.day.date > it.today } ?: false

    private fun canGoForward(): Boolean = model.inputs?.let {
        it.day.date < it.today.plus(DatePeriod(days = TodayStateBuilder.MAX_DAYS_AHEAD))
    } ?: false

    private suspend fun TodayAction.rebuild(change: Model.() -> Model) {
        model = model.change()
        val current = model
        val inputs = current.inputs ?: return
        updateState {
            TodayStateBuilder.build(
                inputs = inputs,
                policy = current.policy,
                menuOpen = current.menuOpen,
                clearArmed = current.clearArmed,
                highlight = current.highlight,
                added = current.added,
                bridgeSkipped = current.bridgeSkippedOn == inputs.day.date,
            ).copy(fasting = current.fasting)
        }
    }

    private suspend fun TodayAction.closeMenu() = rebuild { copy(menuOpen = false, clearArmed = false) }

    private suspend fun TodayAction.closeMenuThen(block: suspend () -> Unit) {
        closeMenu()
        block()
    }

    private fun inputs(policySource: VisibilityPolicySource): Flow<TodayInputs> {
        val days = selectedDay.date.flatMapLatest { date ->
            combine(diary.observeDay(date), diary.observeDay(date.minus(DatePeriod(days = 1)))) { d, before -> d to before }
        }
        val explorer = policySource.policy
            .map { it.preset.today == TodayLayout.Explorer }
            .distinctUntilChanged()
            .flatMapLatest { on -> if (on) explorerStats() else flowOf(null) }
        return combine(days, settingsRepository.settings, explorer, expended()) { (d, before), settings, stats, expended ->
            TodayInputs(
                today = calendar.today(),
                timeZone = calendar.timeZone(),
                day = d,
                dayBefore = before,
                settings = settings,
                explorer = stats,
                expended = expended,
            )
        }
    }

    /** The fasting card, ticking once a minute only while it can show. */
    private fun fastingCard(): Flow<FastingStatus?> =
        combine(settingsRepository.settings, selectedDay.date) { settings, day -> settings to day }
            .flatMapLatest { (settings, day) ->
                val released = fastingRelease.isOn()
                if (!TodayFastingCard.shows(settings, released, day, calendar.today())) {
                    flowOf(null)
                } else {
                    combine(fasting.observeLatest(), ticker.ticks()) { latest, now ->
                        TodayFastingCard.build(settings, released, day, calendar.today(), latest, now, calendar.timeZone())
                    }
                }
            }
            .distinctUntilChanged()

    /** Today's energy while today is on show. With `release.health` off the store is never touched. */
    private fun expended(): Flow<EnergyExpended?> {
        if (!healthFlag()) return flowOf(null)
        return selectedDay.date.flatMapLatest { date ->
            val today = calendar.today()
            if (date == today) health.energy(today, calendar.timeZone()) else flowOf(null)
        }
    }

    private fun explorerStats(): Flow<ExplorerStats> {
        val today = calendar.today()
        return combine(
            diary.frequentFoods(limit = ExplorerStatsBuilder.SAFE_FOOD_COUNT, days = SAFE_FOOD_DAYS),
            diary.observeDays(today.minus(DatePeriod(days = ExplorerStatsBuilder.LOOKBACK_DAYS)), today),
        ) { frequent, days -> ExplorerStatsBuilder.from(frequent, days, today) }
    }

    companion object {
        /** How long a changed meal stays outlined. */
        val HIGHLIGHT_FOR = 2.seconds
        private const val SAFE_FOOD_DAYS = 30
    }
}
