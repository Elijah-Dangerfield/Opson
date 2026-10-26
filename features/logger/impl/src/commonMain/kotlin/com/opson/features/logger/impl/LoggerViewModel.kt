package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.logger.LogTab
import com.opson.features.logger.LoggerRoute
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
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
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
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
import opson.libraries.resources.generated.resources.logger_toast_added_many
import opson.libraries.resources.generated.resources.logger_toast_added_one
import opson.libraries.resources.generated.resources.logger_toast_added_to
import opson.libraries.resources.generated.resources.logger_toast_ate
import opson.libraries.resources.generated.resources.logger_toast_water
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * The logger sheet. Reads the day, the basket and the pick lists, searches the
 * catalog as the user types, and logs the basket in one write.
 *
 * The state is rebuilt from [model] by [LoggerStateBuilder] after every
 * action; [model] holds what was read and what the user chose, and only
 * [handleAction] changes it.
 */
@Inject
class LoggerViewModel(
    @Assisted route: LoggerRoute,
    private val diary: DiaryRepository,
    private val customFoods: CustomFoodRepository,
    private val savedMeals: SavedMealRepository,
    private val foodCatalog: FoodCatalog,
    settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    private val basketStore: BasketStore,
    calendar: LocalCalendar,
    selectedDay: SelectedDay,
    clock: Clock,
    private val telemetry: LoggerTelemetry,
) : SEAViewModel<LoggerState, LoggerEvent, LoggerAction>(
    initialStateArg = LoggerState(focusSearch = route.focusSearch),
) {
    private val day: LocalDate = route.dayIso?.let(LocalDate::parse) ?: selectedDay.date.value

    private var model = Model(
        slot = route.slot ?: MealSlot.defaultFor(
            time = clock.now().toLocalDateTime(calendar.timeZone()).time,
            mealGroups = settingsRepository.settings.value.mealGroups,
        ),
        policy = policySource.policy.value,
        focusSearch = route.focusSearch,
    )

    private var searchJob: Job? = null

    private data class Model(
        val slot: MealSlot,
        val policy: VisibilityPolicy,
        val focusSearch: Boolean,
        val inputs: LoggerInputs? = null,
        val query: String = "",
        val search: SearchOutcome? = null,
    )

    init {
        viewModelScope.launch {
            inputs(calendar, settingsRepository).collect { takeAction(LoggerAction.InputsArrived(it)) }
        }
        viewModelScope.launch {
            policySource.policy.collect { takeAction(LoggerAction.PolicyArrived(it)) }
        }
        // Scoped to this back stack entry, so a pre-pick from Foods lands once however often the sheet recomposes.
        viewModelScope.launch { prePick(route) }
    }

    override suspend fun handleAction(action: LoggerAction) {
        when (action) {
            is LoggerAction.InputsArrived -> action.rebuild { copy(inputs = action.inputs) }
            is LoggerAction.PolicyArrived -> action.rebuild { copy(policy = action.policy) }
            is LoggerAction.SlotPicked -> action.rebuild { copy(slot = action.slot) }
            is LoggerAction.SearchTyped -> searchTyped(action)
            is LoggerAction.SearchResultsArrived -> {
                if (action.outcome.query == model.query.trim()) action.rebuild { copy(search = action.outcome) }
            }
            is LoggerAction.FoodNameTapped -> sendEvent(LoggerEvent.OpenAmount(action.row.ref, action.row.source))
            is LoggerAction.FoodToggleTapped -> toggle(action.row)
            LoggerAction.DescribeItTapped -> sendEvent(LoggerEvent.OpenLog(day, slot(), LogTab.Describe))
            LoggerAction.SavedMealsTapped -> sendEvent(LoggerEvent.OpenLog(day, slot(), LogTab.Saved))
            LoggerAction.JustANumberTapped -> sendEvent(LoggerEvent.OpenLog(day, slot(), LogTab.Describe, focusNumber = true))
            LoggerAction.WaterTapped -> addWater()
            LoggerAction.YesterdayTapped -> copyYesterday()
            LoggerAction.AteNoDetailsTapped -> ateNoDetails()
            LoggerAction.WeighInTapped -> {
                telemetry.methodPicked(LogVia.WeighIn, items = 0)
                sendEvent(LoggerEvent.OpenWeight)
            }
            LoggerAction.AddANoteTapped -> {
                telemetry.methodPicked(LogVia.Note, items = 0)
                sendEvent(LoggerEvent.OpenMealNote(day, slot()))
            }
            LoggerAction.AddToMealTapped -> commit()
            LoggerAction.CloseTapped -> sendEvent(LoggerEvent.Dismiss)
        }
    }

    /** The slot writes go to: the one picked, or Uncategorized on a day with meal groups off. */
    private fun slot(): MealSlot = model.inputs?.let { LoggerStateBuilder.effectiveSlot(it, model.slot) } ?: model.slot

    private suspend fun LoggerAction.rebuild(change: Model.() -> Model) {
        model = model.change()
        val current = model
        val inputs = current.inputs ?: return
        updateState {
            LoggerStateBuilder.build(inputs, current.policy, current.slot, current.query, current.search, current.focusSearch)
        }
    }

    private suspend fun searchTyped(action: LoggerAction.SearchTyped) {
        action.rebuild { copy(query = action.text, search = null) }
        if (model.inputs == null) updateQueryOnly(action)
        searchJob?.cancel()
        val query = action.text.trim()
        if (query.isEmpty()) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            val result = foodCatalog.search(query, SEARCH_LIMIT).getOrNull()
            val outcome = SearchOutcome(query = query, foods = result?.foods.orEmpty(), offline = result?.fromCache ?: true)
            takeAction(LoggerAction.SearchResultsArrived(outcome))
        }
    }

    // Before the first inputs arrive there is nothing to build from, but the field still has to echo what was typed.
    private suspend fun updateQueryOnly(action: LoggerAction) {
        action.updateState { it.copy(query = model.query) }
    }

    private fun toggle(row: FoodRowUi) {
        val inputs = model.inputs ?: return
        val pick = LoggerStateBuilder.picks(inputs, model.slot, model.query, model.search)
            .firstOrNull { it.key == row.key && it.source == row.source }
            ?: return
        basketStore.toggle(pick.toBasketItem())
    }

    private suspend fun addWater() {
        val settings = model.inputs?.settings ?: return
        diary.addWater(day, settings.glassMl)
        telemetry.methodPicked(LogVia.Water, items = 1)
        sendEvent(LoggerEvent.ShowToast(Toast(Res.string.logger_toast_water)))
    }

    private suspend fun copyYesterday() {
        val slot = slot()
        val copied = diary.copySlot(day.minus(1, DateTimeUnit.DAY), day, slot)
        telemetry.methodPicked(LogVia.Yesterday, copied.size)
        sendEvent(LoggerEvent.ShowToast(Toast(Res.string.logger_toast_added_to, listOf(slot.sentenceName))))
        sendEvent(LoggerEvent.Dismiss)
    }

    private suspend fun ateNoDetails() {
        val slot = slot()
        diary.setAteNoDetails(day, slot, ate = true)
        telemetry.methodPicked(LogVia.AteNoDetails, items = 0)
        sendEvent(LoggerEvent.ShowToast(Toast(Res.string.logger_toast_ate, listOf(slot.sentenceName))))
        sendEvent(LoggerEvent.Dismiss)
    }

    private suspend fun commit() {
        val inputs = model.inputs ?: return
        val basket = basketStore.basket.value.ifEmpty { return }
        val slot = slot()
        diary.addItems(day, slot, basket.map { it.draft }, LogMethod.Search)

        val hunger = basket.firstNotNullOfOrNull { it.hunger }
        val fullness = basket.firstNotNullOfOrNull { it.fullness }
        if (hunger != null || fullness != null) {
            val existing = inputs.day.feelings[slot] ?: MealFeeling()
            diary.setFeeling(day, slot, existing.copy(hunger = hunger ?: existing.hunger, fullness = fullness ?: existing.fullness))
        }
        // Foods picked from the diary were cached when they were first logged; only fresh ones need it.
        basket.mapNotNull { it.food }.forEach { foodCatalog.markUsed(it) }

        basket.groupingBy { it.source }.eachCount().forEach { (source, count) -> telemetry.methodPicked(LogVia.of(source), count) }
        basketStore.clear()

        val toast = if (basket.size == 1) {
            Toast(Res.string.logger_toast_added_one, listOf(shortName(basket.single().draft.name), slot.sentenceName))
        } else {
            Toast(Res.string.logger_toast_added_many, listOf(basket.size, slot.sentenceName))
        }
        sendEvent(LoggerEvent.ShowToast(toast))
        sendEvent(LoggerEvent.Dismiss)
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
            savedMeals.get(id)?.items?.forEachIndexed { index, draft ->
                val ref = draft.foodRef()
                basketStore.upsert(
                    BasketItem(
                        key = ref?.key ?: "saved:$id:$index",
                        ref = ref,
                        draft = draft.copy(method = LogMethod.Saved),
                        source = PickSource.Saved,
                    ),
                )
            }
        }
    }

    private fun inputs(calendar: LocalCalendar, settingsRepository: SettingsRepository): Flow<LoggerInputs> {
        val usualBySlot: Flow<Map<MealSlot, List<LogItem>>> =
            combine(MealSlot.meals.map { slot -> diary.usualAt(slot, limit = USUAL_LIMIT) }) { lists ->
                MealSlot.meals.zip(lists.toList()).toMap()
            }
        val days = combine(diary.observeDay(day), diary.observeDay(day.minus(1, DateTimeUnit.DAY))) { d, before -> d to before }
        val lists = combine(diary.frequentFoods(limit = FAVORITES_LIMIT), usualBySlot, diary.recentFoods(limit = RECENT_LIMIT)) {
                favorites, usual, recent ->
            Triple(favorites, usual, recent)
        }
        return combine(days, settingsRepository.settings, basketStore.basket, lists, customFoods.observeAll()) {
                (d, before), settings, basket, (favorites, usual, recent), custom ->
            LoggerInputs(
                today = calendar.today(),
                day = d,
                dayBefore = before,
                settings = settings,
                basket = basket,
                favorites = favorites,
                usualBySlot = usual,
                recent = recent,
                customFoods = custom,
            )
        }
    }

    companion object {
        val SEARCH_DEBOUNCE = 250.milliseconds
        const val SEARCH_LIMIT = 25
        const val FAVORITES_LIMIT = 5
        const val USUAL_LIMIT = 3
        const val RECENT_LIMIT = 6
    }
}
