package com.opson.features.today.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.logger.LogTab
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.TodayLayout
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
import kotlin.time.Duration.Companion.seconds

/**
 * The Today tab. Reads the selected day (and the one before it), the settings and the live
 * policy, rebuilds the state through [TodayStateBuilder] on every arrival, and turns taps into
 * diary writes and navigation events.
 *
 * When food lands on the day being shown (from the logger, or copied here), the meal it went
 * into is outlined for a moment and scrolled to (design v3, 3.9).
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
) : SEAViewModel<TodayState, TodayEvent, TodayAction>(
    initialStateArg = TodayState(day = selectedDay.date.value),
) {
    private var model = Model(policy = policySource.policy.value)
    private var highlightJob: Job? = null

    private data class Model(
        val policy: VisibilityPolicy,
        val inputs: TodayInputs? = null,
        val menuOpen: Boolean = false,
        val clearArmed: Boolean = false,
        val highlight: MealSlot? = null,
        /** The items already on screen for this date, to tell new ones apart. */
        val seen: Pair<LocalDate, Set<String>>? = null,
    )

    init {
        viewModelScope.launch {
            inputs(policySource).collect { takeAction(TodayAction.InputsArrived(it)) }
        }
        viewModelScope.launch {
            policySource.policy.collect { takeAction(TodayAction.PolicyArrived(it)) }
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
        action.rebuild {
            copy(inputs = action.inputs, seen = day.date to ids, highlight = slot ?: highlight.takeIf { previous?.first == day.date })
        }
        if (slot != null) {
            highlightJob?.cancel()
            highlightJob = viewModelScope.launch {
                delay(HIGHLIGHT_FOR)
                takeAction(TodayAction.HighlightFaded)
            }
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
        updateState { TodayStateBuilder.build(inputs, current.policy, current.menuOpen, current.clearArmed, current.highlight) }
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
        return combine(days, settingsRepository.settings, explorer) { (d, before), settings, stats ->
            TodayInputs(
                today = calendar.today(),
                timeZone = calendar.timeZone(),
                day = d,
                dayBefore = before,
                settings = settings,
                explorer = stats,
            )
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
