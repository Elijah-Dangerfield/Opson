package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.logger.LoggerRoute
import com.opson.features.logger.LoggerStart
import com.opson.libraries.core.AppState
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealPicks
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.PickedForMeal
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.SavedMealRepository
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.FoodAmount
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.draftFor
import com.opson.libraries.foods.foodRef
import com.opson.libraries.foods.initialQuantity
import com.opson.libraries.foods.toLogDraft
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.ui.components.text.NumberInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_toast_ate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * The logger sheet: search, Describe it and Saved meals, all picking into one
 * basket, and the meal it all goes to. Logging is [BasketCommitter]'s; this
 * decides what to show and when to write.
 *
 * The state is rebuilt from [model] by [LoggerStateBuilder] after every
 * action; [model] holds what was read and what the user chose, and only
 * [handleAction] changes it.
 *
 * Opened with [LoggerRoute.pickForMeal] it logs nothing: Create a meal asked
 * for foods, so the picks go into a basket of the sheet's own and back through
 * [MealPicks]. The person's real basket is left as it was.
 */
@Inject
@Suppress("TooManyFunctions")
class LoggerViewModel(
    @Assisted route: LoggerRoute,
    private val diary: DiaryRepository,
    private val customFoods: CustomFoodRepository,
    private val savedMeals: SavedMealRepository,
    private val foodCatalog: FoodCatalog,
    settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    basketStore: BasketStore,
    private val committer: BasketCommitter,
    calendar: LocalCalendar,
    selectedDay: SelectedDay,
    clock: Clock,
    private val appState: AppState,
    private val telemetry: LoggerTelemetry,
    private val mealPicks: MealPicks,
    scanEnabled: BarcodeScanEnabled,
) : SEAViewModel<LoggerState, LoggerEvent, LoggerAction>(
    initialStateArg = LoggerState(focusSearch = route.focusSearch, pane = route.start.pane, pickingForMeal = route.pickForMeal != null),
) {
    private val pickForMeal: String? = route.pickForMeal

    /** The shared basket, or in pick-for-this-meal mode one of this sheet's own. */
    private val basketStore: BasketStore = if (pickForMeal != null) BasketStore() else basketStore

    private val day: LocalDate = route.dayIso?.let(LocalDate::parse) ?: selectedDay.date.value

    private var model = Model(
        choices = LoggerChoices(
            slot = route.slot ?: MealSlot.defaultFor(
                time = clock.now().toLocalDateTime(calendar.timeZone()).time,
                mealGroups = settingsRepository.settings.value.mealGroups,
            ),
            pane = route.start.pane,
            focusSearch = route.focusSearch,
            focusNumber = route.start == LoggerStart.Number,
            pickingForMeal = route.pickForMeal != null,
            scanAvailable = scanEnabled(),
        ),
        policy = policySource.policy.value,
    )

    private var searchJob: Job? = null
    private var describeJob: Job? = null

    private data class Model(
        val choices: LoggerChoices,
        val policy: VisibilityPolicy,
        val inputs: LoggerInputs? = null,
    )

    init {
        viewModelScope.launch {
            inputs(calendar, settingsRepository).collect { takeAction(LoggerAction.InputsArrived(it)) }
        }
        viewModelScope.launch {
            policySource.policy.collect { takeAction(LoggerAction.PolicyArrived(it)) }
        }
        // Scoped to this back stack entry, so a pre-pick from Foods lands once however often the sheet recomposes.
        if (pickForMeal == null) viewModelScope.launch { prePick(route) }
    }

    override suspend fun handleAction(action: LoggerAction) {
        when (action) {
            is LoggerAction.InputsArrived -> action.rebuild { copy(inputs = action.inputs) }
            is LoggerAction.PolicyArrived -> action.rebuild { copy(policy = action.policy) }
            is LoggerAction.SlotPicked -> action.choose { copy(slot = action.slot) }
            is LoggerAction.SearchTyped -> searchTyped(action, action.text)
            LoggerAction.SearchCleared -> searchTyped(action, "")
            is LoggerAction.SearchResultsArrived -> {
                if (action.outcome.query == model.choices.query.trim()) action.choose { copy(search = action.outcome) }
            }
            // The amount sheet logs, so a meal being put together takes the row's default amount.
            is LoggerAction.FoodNameTapped -> if (pickForMeal != null) {
                toggle(action.row)
            } else {
                sendEvent(LoggerEvent.OpenAmount(action.row.ref, action.row.source, day, slot()))
            }
            is LoggerAction.FoodToggleTapped -> toggle(action.row)
            LoggerAction.DescribeItTapped -> action.choose { copy(pane = LoggerPane.Describe, focusNumber = false) }
            LoggerAction.DescribeTermTapped -> {
                val term = model.choices.query.trim()
                action.choose { copy(pane = LoggerPane.Describe, focusNumber = false) }
                describeTyped(action, term)
            }
            LoggerAction.SaveNameTapped -> saveName(action)
            LoggerAction.SavedMealsTapped -> action.choose { copy(pane = LoggerPane.Saved) }
            is LoggerAction.SavedMealPicked -> pickSavedMeal(action, action.id)
            LoggerAction.YesterdayTapped -> copyYesterday()
            LoggerAction.AteNoDetailsTapped -> ateNoDetails()
            LoggerAction.JustANumberTapped -> action.choose { copy(pane = LoggerPane.Describe, focusNumber = true) }
            LoggerAction.AddANoteTapped -> {
                telemetry.methodPicked(LogVia.Note, items = 0)
                sendEvent(LoggerEvent.OpenMealNote(day, slot()))
            }
            is LoggerAction.DescribeTyped -> describeTyped(action, action.text)
            is LoggerAction.DescribeArrived -> describeArrived(action)
            is LoggerAction.DescribeFailed -> {
                if (action.text == model.choices.describeText.trim()) {
                    val status = if (appState.isOffline.value) DescribeStatus.Offline else DescribeStatus.Failed
                    basketStore.removeWhere { it.source == PickSource.Describe }
                    action.choose { copy(describeStatus = status) }
                }
            }
            is LoggerAction.DescribedLineEditTapped -> action.line.ref?.let {
                sendEvent(LoggerEvent.OpenAmount(it, PickSource.Describe, day, slot()))
            }
            is LoggerAction.NumberTyped -> action.choose { copy(number = NumberInput.sanitize(action.text, allowDecimal = false)) }
            LoggerAction.AddNumberTapped -> addNumber()
            LoggerAction.BackTapped -> back(action)
            LoggerAction.AddToMealTapped -> pickForMeal?.let(::handBack) ?: commit()
            LoggerAction.CloseTapped -> sendEvent(LoggerEvent.Dismiss)
            LoggerAction.BarcodeTapped -> if (model.choices.scanAvailable && pickForMeal == null) {
                sendEvent(LoggerEvent.OpenScanner(day, slot()))
            }
        }
    }

    /** The slot writes go to: the one picked, or Uncategorized on a day with meal groups off. */
    private fun slot(): MealSlot =
        model.inputs?.let { LoggerStateBuilder.effectiveSlot(it, model.choices.slot) } ?: model.choices.slot

    private suspend fun LoggerAction.rebuild(change: Model.() -> Model) {
        model = model.change()
        val current = model
        val inputs = current.inputs
        if (inputs == null) {
            // Nothing to build from yet, but the fields still have to echo what was typed.
            updateState { it.copy(query = current.choices.query, pane = current.choices.pane) }
            return
        }
        updateState { LoggerStateBuilder.build(inputs, current.policy, current.choices) }
    }

    private suspend fun LoggerAction.choose(change: LoggerChoices.() -> LoggerChoices) = rebuild { copy(choices = choices.change()) }

    private suspend fun searchTyped(action: LoggerAction, text: String) {
        action.choose { copy(query = text, search = null, focusSearch = false) }
        searchJob?.cancel()
        val query = text.trim()
        if (query.isEmpty()) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            val result = foodCatalog.search(query, SEARCH_LIMIT).getOrNull()
            val outcome = SearchOutcome(query = query, foods = result?.foods.orEmpty(), offline = result?.fromCache ?: true)
            takeAction(LoggerAction.SearchResultsArrived(outcome))
        }
    }

    private suspend fun describeTyped(action: LoggerAction, text: String) {
        val parsing = text.isNotBlank()
        action.choose { copy(describeText = text, describeStatus = if (parsing) DescribeStatus.Parsing else DescribeStatus.Idle) }
        describeJob?.cancel()
        if (!parsing) {
            basketStore.removeWhere { it.source == PickSource.Describe }
            return
        }
        val trimmed = text.trim()
        describeJob = viewModelScope.launch {
            delay(DESCRIBE_DEBOUNCE)
            val items = foodCatalog.describe(trimmed).getOrNull()
            takeAction(if (items == null) LoggerAction.DescribeFailed(trimmed) else LoggerAction.DescribeArrived(trimmed, items))
        }
    }

    private suspend fun describeArrived(action: LoggerAction.DescribeArrived) {
        if (action.text != model.choices.describeText.trim()) return
        // A new parse replaces the last one's lines; picks from search stay.
        basketStore.removeWhere { it.source == PickSource.Describe }
        action.items.forEachIndexed { index, item -> basketStore.upsert(item.toDescribedItem(index)) }
        action.choose { copy(describeStatus = if (action.items.isEmpty()) DescribeStatus.Failed else DescribeStatus.Parsed) }
    }

    private suspend fun back(action: LoggerAction) {
        when (model.choices.pane) {
            LoggerPane.Search -> sendEvent(LoggerEvent.Dismiss)
            LoggerPane.Describe -> {
                // Describe's lines are only picks while Describe is open.
                describeJob?.cancel()
                basketStore.removeWhere { it.source == PickSource.Describe }
                action.choose {
                    copy(pane = LoggerPane.Search, describeText = "", describeStatus = DescribeStatus.Idle, number = "", focusNumber = false)
                }
            }
            LoggerPane.Saved -> action.choose { copy(pane = LoggerPane.Search) }
        }
    }

    private fun toggle(row: FoodRowUi) {
        val inputs = model.inputs ?: return
        val pick = LoggerStateBuilder.picks(inputs, model.choices.slot, model.choices.query, model.choices.search)
            .firstOrNull { it.key == row.key && it.source == row.source }
            ?: return
        basketStore.toggle(pick.toBasketItem())
    }

    /** "Save the name only" ([pickNameOnly]) for what was typed, then back to an empty search. */
    private suspend fun saveName(action: LoggerAction) {
        val name = when (model.choices.pane) {
            LoggerPane.Describe -> model.choices.describeText
            else -> model.choices.query
        }.trim().ifEmpty { return }
        pickNameOnly(name, model.inputs?.customFoods.orEmpty(), customFoods, basketStore)
        describeJob?.cancel()
        basketStore.removeWhere { it.source == PickSource.Describe }
        searchJob?.cancel()
        action.choose {
            copy(pane = LoggerPane.Search, query = "", search = null, describeText = "", describeStatus = DescribeStatus.Idle)
        }
    }

    private suspend fun pickSavedMeal(action: LoggerAction, id: String) {
        val meal = model.inputs?.savedMeals?.firstOrNull { it.id == id } ?: savedMeals.get(id) ?: return
        meal.items.forEachIndexed { index, draft -> basketStore.upsert(savedItem(id, index, draft)) }
        action.choose { copy(pane = LoggerPane.Search) }
    }

    private suspend fun copyYesterday() {
        val settings = model.inputs?.settings ?: return
        val slot = slot()
        val copied = diary.copySlot(day.minus(1, DateTimeUnit.DAY), day, slot)
        telemetry.methodPicked(LogVia.Yesterday, copied.size)
        finish(committer.announce(day, slot, copied, settings))
    }

    private suspend fun ateNoDetails() {
        val slot = slot()
        diary.setAteNoDetails(day, slot, ate = true)
        telemetry.methodPicked(LogVia.AteNoDetails, items = 0)
        sendEvent(LoggerEvent.ShowToast(Toast(Res.string.logger_toast_ate, listOf(slot.sentenceName))))
        sendEvent(LoggerEvent.Dismiss)
    }

    private suspend fun addNumber() {
        val settings = model.inputs?.settings ?: return
        // Typing a number is a calorie entry, so the policy has the last word even if the field was showing.
        if (model.policy.level(Metric.Calories) == Visibility.Hide) return
        val kcal = model.choices.number.toIntOrNull()?.takeIf { it > 0 } ?: return
        val slot = slot()
        val draft = LogDraft(name = QUICK_ADD_NAME, precision = Precision.Named, kcal = kcal.toDouble(), method = LogMethod.QuickNumber)
        val items: List<LogItem> = diary.addItems(day, slot, listOf(draft), LogMethod.QuickNumber)
        telemetry.methodPicked(LogVia.QuickNumber, items.size)
        finish(committer.announce(day, slot, items, settings))
    }

    private suspend fun commit() {
        val settings = model.inputs?.settings ?: return
        val committed = committer.commit(day, slot(), settings) ?: return
        finish(committed)
    }

    /** Pick-for-this-meal mode's "Add to this meal": the picks go back to Create a meal, and nothing is logged. */
    private fun handBack(requestId: String) {
        val drafts = basketStore.basket.value.map { it.draft }.ifEmpty { return }
        mealPicks.picked(PickedForMeal(requestId, drafts))
        basketStore.clear()
        sendEvent(LoggerEvent.Dismiss)
    }

    private fun finish(committed: Committed) {
        sendEvent(if (committed.askHowItFelt) LoggerEvent.OpenHowItFelt(day, committed.slot) else LoggerEvent.Dismiss)
    }

    private suspend fun prePick(route: LoggerRoute) {
        route.prePickFoodId?.let { id ->
            val item = if (route.prePickIsCustom) {
                customFoods.get(id)?.let { food ->
                    val ref = FoodRef.Custom(id)
                    BasketItem(ref.key, ref, food.draftFor(food.initialQuantity), PickSource.Foods)
                }
            } else {
                foodCatalog.detail(id).getOrNull()?.let { food ->
                    val ref = FoodRef.Catalog(id)
                    val draft = food.toLogDraft(FoodAmount.Servings(1.0), method = PickSource.Foods.methodFor(ref))
                    BasketItem(ref.key, ref, draft, PickSource.Foods, food = food)
                }
            }
            item?.let(basketStore::upsert)
        }
        route.prePickSavedMealId?.let { id ->
            savedMeals.get(id)?.items?.forEachIndexed { index, draft -> basketStore.upsert(savedItem(id, index, draft)) }
        }
    }

    private fun savedItem(mealId: String, index: Int, draft: LogDraft): BasketItem {
        val ref = draft.foodRef()
        return BasketItem(
            key = ref?.key ?: "saved:$mealId:$index",
            ref = ref,
            draft = draft.copy(method = LogMethod.Saved),
            source = PickSource.Saved,
        )
    }

    private fun inputs(calendar: LocalCalendar, settingsRepository: SettingsRepository): Flow<LoggerInputs> {
        val usualBySlot: Flow<Map<MealSlot, List<LogItem>>> =
            combine(MealSlot.meals.map { slot -> diary.usualAt(slot, limit = USUAL_LIMIT) }) { lists ->
                MealSlot.meals.zip(lists.toList()).toMap()
            }
        val days = combine(diary.observeDay(day), diary.observeDay(day.minus(1, DateTimeUnit.DAY))) { d, before -> d to before }
        val lists = combine(usualBySlot, diary.recentFoods(limit = RECENT_LIMIT), ::Pair)
        val mine = combine(customFoods.observeAll(), savedMeals.observeAll(), ::Pair)
        return combine(days, settingsRepository.settings, basketStore.basket, lists, mine) {
                (d, before), settings, basket, (usual, recent), (custom, saved) ->
            LoggerInputs(
                today = calendar.today(),
                day = d,
                dayBefore = before,
                settings = settings,
                basket = basket,
                usualBySlot = usual,
                recent = recent,
                customFoods = custom,
                savedMeals = saved,
            )
        }
    }

    companion object {
        val SEARCH_DEBOUNCE = 250.milliseconds

        /** Longer than search: a parse is a heavier call, and people pause mid-sentence. */
        val DESCRIBE_DEBOUNCE = 700.milliseconds
        const val SEARCH_LIMIT = 25
        const val USUAL_LIMIT = 3
        const val RECENT_LIMIT = 6

        /**
         * The stored name of a "just a number" entry, as the prototype logs it.
         * It is the item's name in the diary (and on the server), not screen
         * copy, so it is a constant rather than a resource.
         */
        const val QUICK_ADD_NAME = "Quick add"

        /** A saved name's serving: one of whatever it is. Data, not copy. */
        const val NAME_ONLY_UNIT = "serving"
    }
}

private val LoggerStart.pane: LoggerPane
    get() = when (this) {
        LoggerStart.Search -> LoggerPane.Search
        LoggerStart.Describe, LoggerStart.Number -> LoggerPane.Describe
        LoggerStart.Saved -> LoggerPane.Saved
    }
