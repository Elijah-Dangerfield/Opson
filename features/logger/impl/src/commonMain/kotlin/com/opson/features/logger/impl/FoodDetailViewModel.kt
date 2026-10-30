package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.FoodUnit
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealSlotPicker
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.RepeatsRelease
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.AmountUnit
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.PortionAmount
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.foods.SubmittedFoods
import com.opson.libraries.foods.draftFor
import com.opson.libraries.foods.foodRef
import com.opson.libraries.foods.isMeasure
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.ui.components.text.NumberInput
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

sealed interface FoodDetailAction {
    data class FoodArrived(val food: AmountFood, val last: LogItem?) : FoodDetailAction
    data object FoodMissing : FoodDetailAction

    /** The entry the detail was opened on, the food it came from while that is still here, and its meal's hunger answer. */
    data class EntryArrived(val item: LogItem, val food: AmountFood?, val hunger: Hunger?) : FoodDetailAction
    data class InputsArrived(val settings: Settings, val policy: VisibilityPolicy, val day: Day) : FoodDetailAction
    data class ReviewArrived(val status: ReviewStatus?) : FoodDetailAction
    data class HistoryArrived(val days: List<Day>) : FoodDetailAction

    /** Whether the food is on the Recent row now. */
    data class RecentsArrived(val inRecents: Boolean) : FoodDetailAction

    /** The meal this person logs around now, for a detail opened with no meal. */
    data class SlotSuggested(val slot: MealSlot) : FoodDetailAction
    data class AmountTyped(val text: String) : FoodDetailAction
    data class ServingPicked(val index: Int) : FoodDetailAction
    data class UnitPicked(val index: Int) : FoodDetailAction
    data class PrecisionPicked(val precision: Precision) : FoodDetailAction
    data object CustomSizeTapped : FoodDetailAction
    data class CustomSizeTyped(val name: String, val grams: String) : FoodDetailAction
    data object CustomSizeSaved : FoodDetailAction
    data object CustomSizeDismissed : FoodDetailAction
    data object FavoriteTapped : FoodDetailAction
    data class SlotPicked(val slot: MealSlot) : FoodDetailAction
    data class TimePicked(val time: LocalTime) : FoodDetailAction
    data object AllNutrientsTapped : FoodDetailAction

    /** The source's name: what the source is opens under it, or closes. */
    data object SourceTapped : FoodDetailAction

    /** The Verified or Unverified badge: what it means opens under it, or closes. */
    data object CheckBadgeTapped : FoodDetailAction

    /** Credits, under what the source is: every source's attribution. */
    data object CreditsTapped : FoodDetailAction
    data object RemoveFromRecentsTapped : FoodDetailAction
    data object ReportTapped : FoodDetailAction

    /** A Builder's Verify this food, on a catalog food nobody has checked. */
    data object VerifyTapped : FoodDetailAction
    data object EditCopyTapped : FoodDetailAction

    /** No numbers: a hunger answer, or the same one again to clear it. */
    data class HungerPicked(val hunger: Hunger) : FoodDetailAction

    /** "Add to {meal}", or on an entry "Save changes". */
    data object AddToMealTapped : FoodDetailAction

    /** An entry's Remove from log. */
    data object RemoveTapped : FoodDetailAction

    /** An entry's Repeat this, or Edit repeat for one a repeat logged. */
    data object RepeatTapped : FoodDetailAction
    data object BackTapped : FoodDetailAction
}

sealed interface FoodDetailEvent {
    /** Back to where the detail was opened. */
    data object Dismiss : FoodDetailEvent

    /** Logged: the detail closes. Search draws ✓, Today takes it from `RecentLogEvents`. */
    data class Logged(val committed: Committed) : FoodDetailEvent

    /** The entry's changes are saved: the detail closes back to Today. */
    data object Saved : FoodDetailEvent

    /** The entry is off the log: the detail closes, and the snackbar's Undo puts back [entryId] alone. */
    data class Removed(val entryId: String, val name: String) : FoodDetailEvent

    /** The day is closed (Close the day, maybe on another phone): nothing was logged. */
    data class DayClosed(val day: LocalDate) : FoodDetailEvent

    /**
     * Report a problem with this food. [refKey] names it by id only, never by its name or
     * numbers, for the bug report's captured details. [target] is a catalog food and the amount on screen, for
     * "Report a problem" itself (`release.foodReports`); null for the person's own food.
     */
    data class OpenReport(val refKey: String, val target: ReportTarget? = null) : FoodDetailEvent

    /** Verify a catalog food against the pack in hand. */
    data class OpenVerify(val foodId: String, val name: String) : FoodDetailEvent

    /** The star changed: a snackbar says so, and its Undo sets [key] back to not [starred]. */
    data class FavoriteChanged(val key: String, val starred: Boolean) : FoodDetailEvent

    /** Edit a copy of this food, which logs to [day] and [slot] once it is saved. */
    data class OpenEditCopy(val ref: FoodRef, val day: LocalDate, val slot: MealSlot) : FoodDetailEvent

    /** Edit a copy of the entry [entryId] on [day], which can switch the entry to the copy: the detail gives way to it. */
    data class OpenEntryCopy(val day: LocalDate, val entryId: String) : FoodDetailEvent

    /** Repeat the entry: [ruleId]'s editor for one a repeat logged, else a new repeat from it. The detail gives way. */
    data class OpenRepeat(val day: LocalDate, val entryId: String, val ruleId: String?) : FoodDetailEvent

    data object OpenCredits : FoodDetailEvent
}

/**
 * The food detail: a typed amount and its unit, the meal and the time, what it holds and what the
 * day comes to with it. How sure is worked out from how the amount was given (typed in a weight
 * is Weighed) until the person picks one. "Add to {meal}" logs this one food. The favorite, a
 * custom size and taking it off Recent are the person's own settings, so they save as they are
 * tapped.
 *
 * Opened on an entry already on the log ([DetailOpen.Entry]), it edits that entry and no other:
 * it reads the entry by its id, starts from its amount, meal, time and how sure, shows the
 * entry's own numbers (the snapshot it was logged with, scaled if the amount changes), and Today
 * with this is the day without the entry as it was. Save changes writes that one entry, only
 * when something differs; Remove from log tombstones it alone. If the entry changes under the
 * page (another phone, a sync) it starts again from the new one, and if it is gone the page
 * closes.
 */
@Inject
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
class FoodDetailViewModel(
    @Assisted open: DetailOpen,
    private val foodCatalog: FoodCatalog,
    private val customFoods: CustomFoodRepository,
    private val settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    private val diary: DiaryRepository,
    private val calendar: LocalCalendar,
    selectedDay: SelectedDay,
    private val clock: Clock,
    private val logger: FoodLogger,
    private val submittedFoods: SubmittedFoods,
    private val repeats: RepeatsRelease,
) : SEAViewModel<FoodDetailState, FoodDetailEvent, FoodDetailAction>(
    initialStateArg = FoodDetailState(slot = (open as? DetailOpen.Pick)?.route?.slot ?: MealSlot.Dinner),
) {
    private val pick: FoodDetailRoute? = (open as? DetailOpen.Pick)?.route
    private val entryId: String? = (open as? DetailOpen.Entry)?.entryId

    /** The food: the one picked, or the entry's once it is read (null for an entry that is only a name or a number). */
    private var ref: FoodRef? = pick?.ref

    /** Where it was found, for the method and how sure. An entry keeps its own, so this only matters to a pick. */
    private val source: PickSource = pick?.source ?: PickSource.Recent
    private val day: LocalDate = when (open) {
        is DetailOpen.Pick -> open.route.dayIso?.let(LocalDate::parse) ?: selectedDay.date.value
        is DetailOpen.Entry -> open.day
    }

    /** Logged, saved or removed and closing: a second tap queued behind the first would write it twice. */
    private var done = false
    private var watchingReview = false

    private var model = Model(
        settings = settingsRepository.settings.value,
        policy = policySource.policy.value,
        choice = DetailChoice(
            slot = pick?.slot ?: MealSlot.defaultFor(
                time = clock.now().toLocalDateTime(calendar.timeZone()).time,
                mealGroups = settingsRepository.settings.value.mealGroups,
            ),
        ),
    )

    private data class Model(
        val settings: Settings,
        val policy: VisibilityPolicy,
        val choice: DetailChoice,
        val food: AmountFood? = null,
        val day: Day? = null,
        val history: List<Day> = emptyList(),
        val inRecents: Boolean = false,
        val review: ReviewStatus? = null,
        /** The entry as it is on the log, opened on one. */
        val entry: LogItem? = null,
        /** The entry's time and its meal's hunger answer as they were, to tell a change from none. */
        val loggedTime: LocalTime? = null,
        val loggedHunger: Hunger? = null,
    )

    init {
        if (pick != null) loadPick(pick) else loadEntry()
        viewModelScope.launch {
            combine(settingsRepository.settings, policySource.policy, diary.observeDay(day), ::Triple).collect { (settings, policy, today) ->
                takeAction(FoodDetailAction.InputsArrived(settings, policy, today))
            }
        }
        // The last two weeks, read once: the ± sizes a meal eaten with no details from them, and with no
        // meal given the picker chooses from them.
        viewModelScope.launch {
            val today = calendar.today()
            val days = diary.observeDays(today.minus(MealSlotPicker.HISTORY_DAYS, DateTimeUnit.DAY), today).first()
            takeAction(FoodDetailAction.HistoryArrived(days))
            if (pick != null && pick.slot == null) {
                val now = clock.now().toLocalDateTime(calendar.timeZone())
                takeAction(FoodDetailAction.SlotSuggested(MealSlotPicker.pick(days, now, calendar.timeZone(), settingsRepository.settings.value.mealGroups)))
            }
        }
    }

    private fun loadPick(route: FoodDetailRoute) {
        val ref = route.ref
        viewModelScope.launch {
            val food = load(ref)
            // The person's last amount of it, as search's + would log it.
            val last = diary.recentFoods(limit = LAST_USED_LIMIT).first().firstOrNull { it.foodRef() == ref }
            takeAction(if (food == null) FoodDetailAction.FoodMissing else FoodDetailAction.FoodArrived(food, last))
        }
        viewModelScope.launch {
            diary.recentFoods(limit = RECENT_SHOWN).map { recent -> recent.any { it.foodRef() == ref } }.distinctUntilChanged().collect {
                takeAction(FoodDetailAction.RecentsArrived(it))
            }
        }
        watchReview(ref)
    }

    /** The entry by its id, never by its food: the same food twice on a day is two entries. */
    private fun loadEntry() {
        viewModelScope.launch {
            val loaded = diary.observeDay(day).first()
            val item = loaded.allItems.firstOrNull { it.id == entryId }
            // Removed before the page opened (on another phone, say): there is nothing to show.
            if (item == null) {
                done = true
                sendEvent(FoodDetailEvent.Dismiss)
            } else {
                takeAction(entryArrived(item, loaded))
            }
        }
    }

    private suspend fun entryArrived(item: LogItem, on: Day): FoodDetailAction.EntryArrived =
        FoodDetailAction.EntryArrived(item, item.foodRef()?.let { load(it) }, on.feelings[item.slot]?.hunger)

    /** Offline and never cached, or deleted: null. */
    private suspend fun load(ref: FoodRef): AmountFood? = when (ref) {
        is FoodRef.Catalog -> foodCatalog.detail(ref.id).getOrNull()?.let(AmountFood::Catalog)
        is FoodRef.Custom -> customFoods.get(ref.id)?.let(AmountFood::Custom)
    }

    /** The person's own food says whether it is still waiting for review in Opson's food database. */
    private fun watchReview(ref: FoodRef?) {
        if (ref !is FoodRef.Custom || watchingReview) return
        watchingReview = true
        viewModelScope.launch {
            submittedFoods.reviews.map { it[ref.id] }.distinctUntilChanged().collect { takeAction(FoodDetailAction.ReviewArrived(it)) }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun handleAction(action: FoodDetailAction) {
        when (action) {
            is FoodDetailAction.FoodArrived -> foodArrived(action)
            is FoodDetailAction.EntryArrived -> {
                ref = action.item.foodRef()
                val logged = AmountFood.Logged(action.item, action.food)
                val time = action.item.loggedAt.toLocalDateTime(calendar.timeZone()).time
                action.rebuild {
                    copy(
                        food = logged,
                        entry = action.item,
                        loggedTime = time,
                        loggedHunger = action.hunger,
                        choice = DetailChoice.forEntry(logged, time, action.hunger),
                    )
                }
                watchReview(ref)
            }
            FoodDetailAction.FoodMissing -> action.updateState { it.copy(loading = false, missing = true) }
            is FoodDetailAction.InputsArrived -> {
                action.rebuild { copy(settings = action.settings, policy = action.policy, day = action.day) }
                entryMoved(action.day)
            }
            is FoodDetailAction.ReviewArrived -> action.rebuild { copy(review = action.status) }
            is FoodDetailAction.HistoryArrived -> action.rebuild { copy(history = action.days) }
            is FoodDetailAction.RecentsArrived -> action.rebuild { copy(inRecents = action.inRecents) }
            is FoodDetailAction.SlotSuggested -> action.choose { copy(slot = action.slot) }
            is FoodDetailAction.AmountTyped -> action.choose { typed(NumberInput.sanitize(action.text, allowDecimal = true)) }
            is FoodDetailAction.HungerPicked -> action.choose { copy(hunger = action.hunger.takeUnless { it == hunger }) }
            is FoodDetailAction.ServingPicked -> {
                val (unit, count) = FoodDetailStateBuilder.servingAmounts(units()).getOrNull(action.index) ?: return
                action.choose { copy(unit = unit, quantity = count, typed = null) }
            }
            is FoodDetailAction.UnitPicked -> unitPicked(action)
            is FoodDetailAction.PrecisionPicked -> action.choose { copy(picked = action.precision) }
            FoodDetailAction.CustomSizeTapped -> if (model.food?.takesSizes == true) action.choose { copy(customSize = CustomSizeUi()) }
            is FoodDetailAction.CustomSizeTyped -> if (model.choice.customSize != null) {
                action.choose { copy(customSize = CustomSizeUi(action.name, action.grams.filter { it.isDigit() || it == '.' })) }
            }
            FoodDetailAction.CustomSizeSaved -> customSizeSaved(action)
            FoodDetailAction.CustomSizeDismissed -> action.choose { copy(customSize = null) }
            FoodDetailAction.FavoriteTapped -> favorite()
            is FoodDetailAction.SlotPicked -> action.choose { copy(slot = action.slot) }
            is FoodDetailAction.TimePicked -> action.choose { copy(time = action.time) }
            FoodDetailAction.AllNutrientsTapped -> action.choose { copy(nutrientsOpen = !nutrientsOpen) }
            FoodDetailAction.SourceTapped -> action.choose { copy(sourceOpen = !sourceOpen) }
            FoodDetailAction.CheckBadgeTapped -> action.choose { copy(checkOpen = !checkOpen) }
            FoodDetailAction.CreditsTapped -> sendEvent(FoodDetailEvent.OpenCredits)
            FoodDetailAction.RemoveFromRecentsTapped -> ref?.let { food ->
                settingsRepository.update { it.hideFromRecent(food.key, clock.now().toEpochMilliseconds()) }
            }
            // Ids only: a report is read by people, and the food's name or numbers are this person's log.
            FoodDetailAction.ReportTapped -> ref?.let { sendEvent(FoodDetailEvent.OpenReport(it.key, reportTarget())) }
            FoodDetailAction.VerifyTapped -> model.food?.let(FoodDetailStateBuilder::catalogFood)?.takeIf { it.canBeVerified }?.let {
                sendEvent(FoodDetailEvent.OpenVerify(it.id, it.name))
            }
            FoodDetailAction.EditCopyTapped -> editCopy()
            FoodDetailAction.AddToMealTapped -> if (entryId != null) saveChanges() else addToMeal()
            FoodDetailAction.RemoveTapped -> remove()
            FoodDetailAction.RepeatTapped -> repeat()
            FoodDetailAction.BackTapped -> sendEvent(FoodDetailEvent.Dismiss)
        }
    }

    private suspend fun foodArrived(action: FoodDetailAction.FoodArrived) {
        val saved = saved()
        val slot = model.choice.slot
        val ounces = FoodDetailStateBuilder.prefersOunces(model.settings)
        val shown = FoodDetailStateBuilder.shownUnits(action.food, model.settings, ref, noNumbers())
        val initial = DetailChoice.initial(action.food, slot, saved, ounces).let { first ->
            first.takeIf { it.unit in shown } ?: DetailChoice(unit = shown.first(), quantity = action.food.initialQuantity(shown.first()), slot = slot)
        }
        // A last amount in a unit No numbers does not offer (grams) starts from a household measure instead.
        val choice = action.last?.let { DetailChoice.lastUsed(action.food, it, slot, saved, ounces) }?.takeIf { it.unit in shown } ?: initial
        action.rebuild { copy(food = action.food, choice = choice.copy(slot = model.choice.slot)) }
    }

    /**
     * The entry on the day as it is now. Gone (removed on another phone): the page closes. Changed
     * by something else (a sync, Edit a copy switching it): the page starts again from it, so a
     * Save never writes back the entry as it was before.
     */
    private fun entryMoved(on: Day) {
        val id = entryId ?: return
        val shown = model.entry ?: return
        if (done) return
        val now = on.allItems.firstOrNull { it.id == id }
        when {
            now == null -> {
                done = true
                sendEvent(FoodDetailEvent.Dismiss)
            }
            now != shown -> {
                model = model.copy(entry = now)
                viewModelScope.launch { takeAction(entryArrived(now, on)) }
            }
        }
    }

    private fun saved(): List<FoodUnit> = ref?.let { model.settings.unitsFor(it.key) }.orEmpty()

    private fun noNumbers(policy: VisibilityPolicy = model.policy): Boolean = FoodDetailStateBuilder.isNoNumbers(policy)

    /** The units as the menu lists them, so a picked index means the same unit here. */
    private fun units(current: Model = model): List<AmountUnit> =
        current.food?.let { FoodDetailStateBuilder.shownUnits(it, current.settings, ref, noNumbers(current.policy)) }.orEmpty()

    private fun groupsOn(current: Model = model): Boolean = current.day?.mealGroupsOn(current.settings) ?: current.settings.mealGroups

    /** With meal groups off for the day, everything goes to Uncategorized, as the detail shows. */
    private fun logSlot(): MealSlot = if (groupsOn()) model.choice.slot else MealSlot.Uncategorized

    private fun unit(): AmountUnit = units().takeIf { it.isNotEmpty() }?.let(model.choice::unitIn) ?: AmountUnit.Grams

    /**
     * The amount converts to the new unit when both have a weight. A typed weight stays typed in
     * another weight or volume (32 g weighed is still weighed in ounces); a household unit is not.
     */
    private suspend fun unitPicked(action: FoodDetailAction.UnitPicked) {
        val to = units().getOrNull(action.index) ?: return
        val from = unit()
        action.choose {
            val converted = PortionAmount.switch(quantity, from, to)
            val stillTyped = typed != null && from.isMeasure && to.isMeasure
            copy(unit = to, quantity = converted, typed = if (stillTyped) FoodDetailStateBuilder.number(converted) else null)
        }
    }

    /** Saves the size to this food in settings, then counts in it at the same count. */
    private suspend fun customSizeSaved(action: FoodDetailAction) {
        val key = ref?.key ?: return
        val typed = model.choice.customSize?.takeIf { it.canSave } ?: return
        val size = FoodUnit(typed.name.trim(), typed.grams.toDouble())
        val settings = settingsRepository.update { it.withUnit(key, size) }
        val from = unit()
        val to = AmountUnit.Saved(size)
        model = model.copy(settings = settings)
        action.choose { copy(unit = to, quantity = PortionAmount.switch(quantity, from, to), customSize = null, typed = null) }
    }

    /** A catalog food and the amount on screen, which suggested numbers are read for. */
    /**
     * Stars the food or takes the star off, and says so. A catalog food is also kept on the device
     * as a favorite, so the Foods tab can show it offline before it is ever logged.
     */
    private suspend fun favorite() {
        val ref = ref ?: return
        val starred = settingsRepository.update { it.toggleFavorite(ref.key) }.isFavorite(ref.key)
        model.food?.let(FoodDetailStateBuilder::catalogFood)?.let { foodCatalog.setFavorite(it, starred) }
        sendEvent(FoodDetailEvent.FavoriteChanged(ref.key, starred))
    }

    private fun reportTarget(): ReportTarget? {

        val food = (model.food as? AmountFood.Catalog)?.food ?: return null
        val draft = food.draftFor(unit(), model.choice.quantity)
        return ReportTarget(food.id, food.name, draft.amountText, draft.grams?.takeIf { it > 0.0 })
    }

    private suspend fun addToMeal() {
        val food = model.food ?: return
        val ref = ref ?: return
        val choice = model.choice
        if (done || choice.quantity <= 0.0) return
        done = true
        val slot = logSlot()
        val pick = LogPick(
            key = ref.key,
            ref = ref,
            draft = food.draftFor(choice, unit(), source, ref),
            source = source,
            food = (food as? AmountFood.Catalog)?.food,
            loggedAt = choice.time?.let { LocalDateTime(day, it).toInstant(calendar.timeZone()) },
        )
        val committed = when (val result = logger.log(day, slot, listOf(pick)) ?: return) {
            is DayWrite.Written -> result.value
            is DayWrite.DayClosed -> {
                done = false
                sendEvent(FoodDetailEvent.DayClosed(result.day))
                return
            }
        }
        // No numbers' hunger answer goes to the meal, beside whatever else the meal's feeling holds.
        choice.hunger?.let { hunger ->
            val feeling = model.day?.feelings?.get(slot) ?: MealFeeling()
            diary.setFeeling(day, slot, feeling.copy(hunger = hunger))
        }
        sendEvent(FoodDetailEvent.Logged(committed))
    }

    /**
     * The entry as the page has it now: its amount and numbers from the draft, its meal, time
     * and how sure. Equal to the entry when nothing was changed. With meal groups off for the day
     * the meal stays as logged; a time left alone keeps the exact instant it was logged at.
     */
    private fun edited(current: Model = model): LogItem? {
        val item = current.entry ?: return null
        val food = current.food ?: return null
        val unit = FoodDetailStateBuilder.unitFor(food, current.choice, units(current))
        val draft = FoodDetailStateBuilder.draft(food, current.choice, unit, source, ref)
        val time = current.choice.time
        val loggedAt = if (time == null || time == current.loggedTime) item.loggedAt else LocalDateTime(item.day, time).toInstant(calendar.timeZone())
        return item.copy(
            slot = if (groupsOn(current)) current.choice.slot else item.slot,
            loggedAt = loggedAt,
            precision = draft.precision,
            amountText = draft.amountText,
            quantity = draft.quantity,
            unit = draft.unit,
            grams = draft.grams,
            kcal = draft.kcal,
            protein = draft.protein,
            carbs = draft.carbs,
            fat = draft.fat,
            fiber = draft.fiber,
            micros = draft.micros,
        )
    }

    private fun hungerChanged(current: Model = model): Boolean = current.entry != null && current.choice.hunger != current.loggedHunger

    /** Writes the entry, and only it, when something differs from how it was logged. */
    private suspend fun saveChanges() {
        val item = model.entry ?: return
        val changed = edited() ?: return
        val hunger = hungerChanged()
        if (done || model.choice.quantity <= 0.0 || (changed == item && !hunger)) return
        done = true
        if (changed != item) {
            val result = diary.updateItem(changed)
            if (result is DayWrite.DayClosed) {
                done = false
                sendEvent(FoodDetailEvent.DayClosed(result.day))
                return
            }
        }
        // No numbers' hunger answer belongs to the meal the entry is in now.
        if (hunger) {
            val feeling = model.day?.feelings?.get(changed.slot) ?: MealFeeling()
            diary.setFeeling(day, changed.slot, feeling.copy(hunger = model.choice.hunger))
        }
        sendEvent(FoodDetailEvent.Saved)
    }

    /** Takes this entry off the log, a tombstone that syncs. Another entry of the same food stays. */
    private suspend fun remove() {
        val item = model.entry ?: return
        if (done) return
        done = true
        when (val result = diary.removeItem(item.id)) {
            is DayWrite.Written -> sendEvent(FoodDetailEvent.Removed(item.id, item.name))
            is DayWrite.DayClosed -> {
                done = false
                sendEvent(FoodDetailEvent.DayClosed(result.day))
            }
        }
    }

    private fun repeat() {
        val item = model.entry ?: return
        if (done || !repeats.isOn()) return
        done = true
        sendEvent(FoodDetailEvent.OpenRepeat(day, item.id, item.repeatRuleId))
    }

    /**
     * On an entry, a copy of the entry (its own numbers, with "Use for this entry"); the page
     * gives way to it. Otherwise a copy of the food, logged to this meal once saved.
     */
    private fun editCopy() {
        val item = model.entry
        when {
            item != null -> if (!done) {
                done = true
                sendEvent(FoodDetailEvent.OpenEntryCopy(day, item.id))
            }
            model.food != null -> ref?.let { sendEvent(FoodDetailEvent.OpenEditCopy(it, day, logSlot())) }
        }
    }

    private suspend fun FoodDetailAction.choose(change: DetailChoice.() -> DetailChoice) = rebuild { copy(choice = choice.change()) }

    private suspend fun FoodDetailAction.rebuild(change: Model.() -> Model) {
        model = model.change()
        val current = model
        val food = current.food ?: return
        val entry = current.entry
        val inputs = DetailInputs(
            settings = current.settings,
            policy = current.policy,
            // Today with this counts the entry as it would be saved, not twice.
            day = if (entry == null) current.day else current.day?.without(entry.id),
            history = current.history,
            inRecents = current.inRecents,
            review = current.review,
            today = calendar.today(),
            now = clock.now().toLocalDateTime(calendar.timeZone()).time,
            entry = entry?.let {
                EntryUi(
                    changed = edited(current) != it || hungerChanged(current),
                    repeat = when {
                        !repeats.isOn() -> null
                        it.repeatRuleId != null -> EntryRepeat.Edit
                        else -> EntryRepeat.New
                    },
                )
            },
        )
        updateState { FoodDetailStateBuilder.build(food, current.choice, inputs, ref, source) }
    }

    private companion object {
        /** How far back the person's last amount of a food is looked for, as search's + does. */
        const val LAST_USED_LIMIT = 100

        /** As many as the Recent rows show (the add sheet, search and Foods all show six). */
        const val RECENT_SHOWN = 6
    }
}
