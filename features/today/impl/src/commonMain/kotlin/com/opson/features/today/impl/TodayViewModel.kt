package com.opson.features.today.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.DiaryItemEditor
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
import com.opson.libraries.diary.TodaySummaryEnabled
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.diary.valueOrNull
import com.opson.libraries.diary.rememberMainNumber
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.GoalsAndEnergyEnabled
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.ui.time.RelativeDay
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
import opson.libraries.resources.generated.resources.today_toast_cleared
import opson.libraries.resources.generated.resources.today_toast_copied_foods_one
import opson.libraries.resources.generated.resources.today_toast_copied_foods_other
import opson.libraries.resources.generated.resources.today_toast_copied_previous
import opson.libraries.resources.generated.resources.today_toast_copied_tomorrow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
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
    private val editor: DiaryItemEditor,
    private val weights: WeightRepository,
    private val summaryEnabled: TodaySummaryEnabled,
    private val selectEnabled: TodaySelectEnabled,
    private val foodActionsEnabled: TodayFoodActionsEnabled,
    private val goalsAndEnergy: GoalsAndEnergyEnabled,
    private val burnedRepository: BurnedRepository,
    private val cardView: TodayCardViewStore,
) : SEAViewModel<TodayState, TodayEvent, TodayAction>(
    initialStateArg = TodayState(day = selectedDay.date.value, dayLabel = RelativeDay.of(selectedDay.date.value, calendar.today())),
) {
    private var model = Model(policy = policySource.policy.value)
    private var highlightJob: Job? = null
    private var undoJob: Job? = null
    private var removedJob: Job? = null
    private var movedJob: Job? = null
    private var removals = 0L
    private var moves = 0L

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
        val fasting: FastingStatus? = null,
        /** The foods being selected; null when not selecting. */
        val selected: Set<String>? = null,
        val step: SelectStep = SelectStep.Actions,
        val swipe: SwipeState = SwipeState(),
        /** What the last removal took, while its Undo is open. */
        val removed: Removal? = null,
        /** What the last move took and from where, while its Undo is open. */
        val moved: Move? = null,
    )

    /** The ids a removal took, what the bar says about them, and which removal it was. */
    private data class Removal(val ids: List<String>, val notice: RemovedNotice, val token: Long)

    /** Each moved food's meal before the move, what the bar says, and which move it was. */
    private data class Move(val from: Map<String, MealSlot>, val notice: MovedNotice, val token: Long)

    init {
        viewModelScope.launch {
            inputs().collect { takeAction(TodayAction.InputsArrived(it)) }
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
            TodayAction.DayLabelTapped -> sendEvent(TodayEvent.OpenCalendar(day()))
            TodayAction.NutrientsTapped -> sendEvent(TodayEvent.OpenNutrients(day()))
            TodayAction.EnergyTapped -> sendEvent(TodayEvent.OpenEnergyToday)
            TodayAction.MainNumberToggled -> {
                val next = ((state.summary as? TodaySummary.Energy)?.card?.next) ?: return
                settingsRepository.update { it.rememberMainNumber(next) }
            }
            TodayAction.CloseDayButtonTapped -> if (!isFuture()) sendEvent(TodayEvent.OpenDayClose(day()))
            TodayAction.ReopenTapped -> action.closeMenuThen { diary.reopenDay(day()) }
            is TodayAction.MealTileTapped -> ifOpen { sendEvent(TodayEvent.OpenLogger(day(), action.slot)) }
            is TodayAction.PlanItTapped -> ifOpen { sendEvent(TodayEvent.OpenLogger(day(), action.slot)) }
            is TodayAction.MealPlusTapped -> ifOpen { sendEvent(TodayEvent.OpenMealSheet(day(), action.slot)) }
            is TodayAction.ItemTapped -> if (model.swipe.open != null) {
                action.rebuild { copy(swipe = SwipeState()) }
            } else {
                sendEvent(TodayEvent.OpenItemSheet(day(), action.entryId))
            }
            is TodayAction.ItemMenuTapped -> if (state.foodMenus) sendEvent(TodayEvent.OpenItemSheet(day(), action.entryId))
            TodayAction.WaterPlusTapped -> ifOpen { sendEvent(TodayEvent.OpenWaterSheet(day())) }
            is TodayAction.WaterGlassTapped -> ifOpen { glassTapped(action.index) }
            TodayAction.HighlightFaded -> action.rebuild { copy(highlight = null) }
            is TodayAction.LoggedArrived -> loggedArrived(action)
            TodayAction.UndoTapped -> model.added?.let { event ->
                undoJob?.cancel()
                action.rebuild { copy(added = null) }
                recentLogEvents.undo(event)
            }
            is TodayAction.UndoExpired -> if (model.added == action.event) action.rebuild { copy(added = null) }
            TodayAction.TrackAgainTapped -> diary.setUntracked(day(), untracked = false)
            // The banner only offers this when the switch has to wait for the pause, so open it.
            is TodayAction.TimedSwitchTapped -> sendEvent(TodayEvent.OpenPause(NumbersChange.PickPreset(action.to)))
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
            TodayAction.NotesMenuTapped -> action.closeMenuThen { sendEvent(TodayEvent.OpenNotes) }
            TodayAction.CopyYesterdayTapped -> action.closeMenuThen {
                if (written(diary.copyDay(from = day().minus(DatePeriod(days = 1)), to = day()))) {
                    sendEvent(TodayEvent.ShowToast(Toast(Res.string.today_toast_copied_previous)))
                }
            }
            TodayAction.CopyToTomorrowTapped -> action.closeMenuThen {
                if (!isFuture()) {
                    if (written(diary.copyDay(from = day(), to = day().plus(DatePeriod(days = 1))))) {
                        sendEvent(TodayEvent.ShowToast(Toast(Res.string.today_toast_copied_tomorrow)))
                    }
                }
            }
            TodayAction.DayNoteTapped -> action.closeMenuThen { ifOpen { sendEvent(TodayEvent.OpenDayNote(day())) } }
            TodayAction.UntrackedMenuTapped -> action.closeMenuThen {
                diary.setUntracked(day(), untracked = model.inputs?.day?.untracked != true)
            }
            TodayAction.EditCardsTapped -> action.closeMenuThen { sendEvent(TodayEvent.OpenTodayScreenSettings) }
            TodayAction.ClearDayTapped -> clearDay(action)
            // Kept on this phone, not in synced Settings: see TodayCardViewStore.
            is TodayAction.SummaryPageShown -> cardView.update { it.copy(page = action.page) }
            TodayAction.SummaryLongPressed -> if (state.summaryTools) sendEvent(TodayEvent.OpenTodayScreenSettings)
            TodayAction.WeightTapped -> sendEvent(TodayEvent.OpenWeight)
            else -> selectAction(action)
        }
    }

    /** Select foods, drag or move them, swipe to delete, and Undo after a removal or a move (`release.todaySelect`). */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun selectAction(action: TodayAction) {
        if (!state.selectTools) return
        when (action) {
            is TodayAction.FoodLongPressed -> action.rebuild {
                copy(selected = setOf(action.entryId), step = SelectStep.Actions, swipe = SwipeState(), menuOpen = false)
            }
            // From the ⋮ menu: selecting with nothing picked yet, a tap on a food picks it.
            TodayAction.SelectMenuTapped -> action.rebuild {
                copy(selected = emptySet(), step = SelectStep.Actions, swipe = SwipeState(), menuOpen = false, clearArmed = false)
            }
            is TodayAction.FoodToggled -> action.rebuild { copy(selected = selected?.toggle(action.entryId)) }
            is TodayAction.MealHeaderToggled -> action.rebuild {
                val ids = inputs?.day?.items(action.slot).orEmpty().map { it.id }
                val current = selected.orEmpty()
                copy(selected = if (ids.all { it in current }) current - ids.toSet() else current + ids)
            }
            TodayAction.SelectAllTapped -> action.rebuild { copy(selected = inputs?.day?.allItems.orEmpty().mapTo(LinkedHashSet()) { it.id }) }
            TodayAction.SelectionClosed -> action.rebuild { copy(selected = null, step = SelectStep.Actions) }
            TodayAction.SaveAsMealTapped -> saveSelectedAsMeal(action)
            is TodayAction.DragStarted -> if (model.selected?.contains(action.entryId) == false) {
                action.rebuild { copy(selected = selected.orEmpty() + action.entryId, step = SelectStep.Actions) }
            }
            is TodayAction.FoodsDropped -> move(action, action.slot)
            is TodayAction.MoveRowRequested -> if (model.selected != null) {
                action.rebuild { copy(selected = selected.orEmpty() + action.entryId, step = SelectStep.MoveTo()) }
            }
            TodayAction.CopyToTapped -> if (picking()) action.rebuild { copy(step = SelectStep.CopyTo(slot = firstSelectedSlot())) }
            is TodayAction.CopyDayPicked -> action.rebuild { copy(step = (step as? SelectStep.CopyTo)?.copy(day = action.day) ?: step) }
            is TodayAction.CopySlotPicked -> action.rebuild { copy(step = (step as? SelectStep.CopyTo)?.copy(slot = action.slot) ?: step) }
            TodayAction.CopyConfirmed -> copySelected(action)
            TodayAction.MoveTapped -> if (picking()) action.rebuild { copy(step = SelectStep.MoveTo()) }
            is TodayAction.MoveSlotPicked -> action.rebuild { copy(step = SelectStep.MoveTo(action.slot)) }
            TodayAction.MoveConfirmed -> (model.step as? SelectStep.MoveTo)?.slot?.let { move(action, it) }
            TodayAction.RemoveSelectedTapped -> if (picking()) action.rebuild { copy(step = SelectStep.ConfirmRemove) }
            TodayAction.RemoveSelectedConfirmed -> {
                val ids = state.selection?.ids.orEmpty().toList()
                action.rebuild { copy(selected = null, step = SelectStep.Actions) }
                remove(action, ids)
            }
            TodayAction.SelectStepBack -> action.rebuild { copy(step = SelectStep.Actions) }
            is TodayAction.MoveUncategorizedTapped -> action.rebuild {
                copy(selected = action.ids.toSet(), step = SelectStep.MoveTo(), swipe = SwipeState(), menuOpen = false)
            }
            is TodayAction.RowSwipedOpen -> action.rebuild { copy(swipe = SwipeState(open = action.entryId)) }
            TodayAction.RowClosed, TodayAction.RowRemoveKept -> action.rebuild { copy(swipe = SwipeState()) }
            TodayAction.ListScrolled -> if (model.swipe.open != null) action.rebuild { copy(swipe = SwipeState()) }
            is TodayAction.DeleteRequested -> action.rebuild { copy(swipe = SwipeState(confirming = action.entryId)) }
            is TodayAction.RowRemoveConfirmed -> {
                action.rebuild { copy(swipe = SwipeState()) }
                remove(action, listOf(action.entryId))
            }
            TodayAction.RemovedUndoTapped -> model.removed?.let { removal ->
                removedJob?.cancel()
                action.rebuild { copy(removed = null) }
                written(editor.restoreItems(removal.ids))
            }
            is TodayAction.RemovedUndoExpired -> if (model.removed?.token == action.token) action.rebuild { copy(removed = null) }
            TodayAction.MovedUndoTapped -> model.moved?.let { moved ->
                movedJob?.cancel()
                action.rebuild { copy(moved = null) }
                // Each food goes back to the meal it came from, however many meals that was.
                moved.from.entries.groupBy({ it.value }, { it.key }).forEach { (slot, ids) -> written(editor.moveItems(ids, slot)) }
            }
            is TodayAction.MovedUndoExpired -> if (model.moved?.token == action.token) action.rebuild { copy(moved = null) }
            else -> Unit
        }
    }

    /**
     * Moves the picked foods into [slot], keeping their times, and stops selecting: a drop, or
     * Move on the bottom bar, which is the same move without dragging. A food already in [slot]
     * stays put; when that is all of them nothing moves and selecting goes on. Undo stays open for
     * six seconds and puts each food back in its own meal.
     */
    private suspend fun move(action: TodayAction, slot: MealSlot) {
        val picked = state.selection?.ids.orEmpty()
        val items = model.inputs?.day?.allItems.orEmpty().filter { it.id in picked && it.slot != slot }
        if (items.isEmpty()) return
        val ids = items.map { it.id }
        if (!written(editor.moveItems(ids, slot))) return
        val token = ++moves
        val notice = MovedNotice(ids.size, items.singleOrNull()?.name, slot)
        action.rebuild {
            copy(selected = null, step = SelectStep.Actions, moved = Move(items.associate { it.id to it.slot }, notice, token))
        }
        movedJob?.cancel()
        movedJob = viewModelScope.launch {
            delay(LoggedToMeal.UNDO_WINDOW)
            takeAction(TodayAction.MovedUndoExpired(token))
        }
    }

    /**
     * Removes [ids] now (totals change only here, after Remove) and offers Undo for six seconds,
     * the same window as after adding. Undo puts back exactly those rows.
     */
    private suspend fun remove(action: TodayAction, ids: List<String>) {
        if (ids.isEmpty()) return
        val items = model.inputs?.day?.allItems.orEmpty().filter { it.id in ids }
        val one = items.singleOrNull()
        if (!written(editor.removeItems(ids))) return
        val token = ++removals
        action.rebuild { copy(removed = Removal(ids, RemovedNotice(ids.size, one?.name, one?.slot), token)) }
        removedJob?.cancel()
        removedJob = viewModelScope.launch {
            delay(LoggedToMeal.UNDO_WINDOW)
            takeAction(TodayAction.RemovedUndoExpired(token))
        }
    }

    /**
     * Create a meal from the picks, which lands in My meals on Foods for logging again. Selecting
     * ends here, as after Copy and Move; the meal is only made once Create a meal saves it.
     */
    private suspend fun saveSelectedAsMeal(action: TodayAction) {
        val ids = state.selection?.ids.orEmpty()
        if (ids.isEmpty()) return
        val slot = model.inputs?.day?.allItems.orEmpty()
            .filter { it.id in ids }
            .map { it.slot }
            .distinct()
            .singleOrNull()
            ?.takeIf { it.isMeal }
        action.rebuild { copy(selected = null, step = SelectStep.Actions) }
        sendEvent(TodayEvent.OpenCreateMeal(day(), ids.toList(), slot))
    }

    private suspend fun copySelected(action: TodayAction) {
        val step = model.step as? SelectStep.CopyTo ?: return
        val slot = step.slot ?: return
        val ids = state.selection?.ids.orEmpty().toList()
        val to = calendar.today().plus(DatePeriod(days = step.day.offset))
        val result = editor.copyItems(ids, to, slot)
        if (!written(result)) return
        val copied = result.valueOrNull().orEmpty()
        action.rebuild { copy(selected = null, step = SelectStep.Actions) }
        sendEvent(
            TodayEvent.ShowToast(
                if (copied.size == 1) {
                    Toast(Res.string.today_toast_copied_foods_one)
                } else {
                    Toast(Res.string.today_toast_copied_foods_other, listOf(copied.size))
                },
            ),
        )
    }

    /** Copy to… starts on the meal the first picked food is in, when that is a meal. */
    private fun firstSelectedSlot(): MealSlot? {
        val first = model.selected?.firstOrNull() ?: return null
        return model.inputs?.day?.allItems?.firstOrNull { it.id == first }?.slot?.takeIf { it.isMeal }
    }

    /** Something is picked: selecting from the ⋮ menu starts with nothing, and the bar waits for a food. */
    private fun picking(): Boolean = !state.selection?.ids.isNullOrEmpty()

    private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

    /** Sets the day's water to the glasses the tap leaves full, so the bar shows exactly what was tapped. */
    private suspend fun glassTapped(index: Int) {
        val inputs = model.inputs ?: return
        val water = state.water ?: return
        if (index !in 0 until water.total) return
        val glasses = WaterTaps.glassesAfter(filled = water.filled, tapped = index)
        written(diary.setWater(inputs.day.date, glasses * inputs.settings.glassMl))
    }

    /** "Clear this day" asks for a second tap. */
    private suspend fun clearDay(action: TodayAction) {
        if (model.clearArmed) {
            action.closeMenuThen {
                if (written(diary.clearDay(day()))) sendEvent(TodayEvent.ShowToast(Toast(Res.string.today_toast_cleared)))
            }
        } else {
            action.rebuild { copy(clearArmed = true) }
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
                // Selecting and swiped rows belong to the date they started on.
                selected = selected.takeIf { sameDate },
                step = if (sameDate) step else SelectStep.Actions,
                swipe = if (sameDate) swipe else SwipeState(),
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

    /** Runs [block] unless the day on show is closed, in which case it says so instead. */
    private suspend fun ifOpen(block: suspend () -> Unit) {
        if (model.inputs?.day?.closedByUser == true) sendEvent(TodayEvent.OpenDayClosed(day())) else block()
    }

    /**
     * Whether [write] went through. When the day turned out closed (another phone closed it a
     * moment ago, past what Today had read) it says so instead.
     */
    private suspend fun written(write: DayWrite<*>): Boolean = when (write) {
        is DayWrite.Written -> true
        is DayWrite.DayClosed -> {
            sendEvent(TodayEvent.OpenDayClosed(write.day))
            false
        }
    }

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
                ui = TodayUi(
                    selected = current.selected,
                    step = current.step,
                    swipe = current.swipe,
                    removed = current.removed?.notice,
                    moved = current.moved?.notice,
                ),
            ).copy(fasting = current.fasting)
        }
    }

    private suspend fun TodayAction.closeMenu() = rebuild { copy(menuOpen = false, clearArmed = false) }

    private suspend fun TodayAction.closeMenuThen(block: suspend () -> Unit) {
        closeMenu()
        block()
    }

    private fun inputs(): Flow<TodayInputs> {
        val days = selectedDay.date.flatMapLatest { date ->
            combine(diary.observeDay(date), diary.observeDay(date.minus(DatePeriod(days = 1)))) { d, before -> d to before }
        }
        val energy = combine(expended(), burned()) { expended, burned -> expended to burned }
        return combine(days, settingsRepository.settings, energy, weighIns(), cardView()) { (d, before), settings, (expended, burned), weighIns, view ->
            TodayInputs(
                today = calendar.today(),
                timeZone = calendar.timeZone(),
                day = d,
                dayBefore = before,
                settings = settings,
                expended = expended,
                summaryOn = summaryEnabled(),
                selectOn = selectEnabled(),
                foodActionsOn = foodActionsEnabled(),
                weighIns = weighIns,
                goalsAndEnergyOn = goalsAndEnergy(),
                burned = burned,
                cardView = view,
            )
        }
    }

    /** How the card was left on this phone. With `release.todaySummary` off there is no page or collapse to read. */
    private fun cardView(): Flow<TodayCardView> {
        if (!summaryEnabled()) return flowOf(TodayCardView())
        return cardView.view
    }

    /** The shown day's Burned (design sync 4). With `release.goalsAndEnergy` off nothing reads it. */
    private fun burned(): Flow<Burned?> {
        if (!goalsAndEnergy()) return flowOf(null)
        return selectedDay.date.flatMapLatest { date -> burnedRepository.burned(date) }
    }

    /** The last few weeks of weigh-ins for the Weight row. With `release.todaySummary` off the store is never read. */
    private fun weighIns(): Flow<List<WeighIn>> {
        if (!summaryEnabled()) return flowOf(emptyList())
        return weights.observeWeighIns(since = clock.now() - WEIGH_IN_LOOKBACK)
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

    companion object {
        /** How long a changed meal stays outlined. */
        val HIGHLIGHT_FOR = 2.seconds

        /** The Weight row's trend reads two weeks; a little more covers a day in the past. */
        private val WEIGH_IN_LOOKBACK = 42.days
    }
}
