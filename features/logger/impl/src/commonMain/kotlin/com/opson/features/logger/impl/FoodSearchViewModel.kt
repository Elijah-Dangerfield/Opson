package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.logger.FoodSearchRoute
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealPicks
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealSlotPicker
import com.opson.libraries.diary.PickedForMeal
import com.opson.libraries.diary.SavedMealRepository
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.foodRef
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Full-screen search (design sync 4, 13.4c). A + logs the food at once, at the person's last
 * amount of it, and search stays: there is no pick list. A ✓ marks what went into this meal
 * since search opened, and takes it back out.
 *
 * Opened with [FoodSearchRoute.pickForMeal] it logs nothing: Create a meal asked for foods, so +
 * picks into a list of this screen's own that goes back through [MealPicks].
 */
@Inject
@Suppress("TooManyFunctions", "LongParameterList")
class FoodSearchViewModel(
    @Assisted route: FoodSearchRoute,
    private val diary: DiaryRepository,
    private val customFoods: CustomFoodRepository,
    savedMeals: SavedMealRepository,
    private val foodCatalog: FoodCatalog,
    settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    private val logger: FoodLogger,
    calendar: LocalCalendar,
    selectedDay: SelectedDay,
    clock: Clock,
    private val mealPicks: MealPicks,
    scanEnabled: BarcodeScanEnabled,
    submitFoodEnabled: SubmitFoodEnabled,
    searchOptionsEnabled: SearchOptionsEnabled,
    private val optionsTelemetry: SearchOptionsTelemetry,
) : SEAViewModel<FoodSearchState, SearchEvent, SearchAction>(
    initialStateArg = FoodSearchState(pickingForMeal = route.pickForMeal != null),
) {
    private val pickForMeal: String? = route.pickForMeal
    private val day: LocalDate = route.dayIso?.let(LocalDate::parse) ?: selectedDay.date.value

    private var model = Model(
        choices = SearchChoices(
            slot = route.slot ?: MealSlot.defaultFor(
                time = clock.now().toLocalDateTime(calendar.timeZone()).time,
                mealGroups = settingsRepository.settings.value.mealGroups,
            ),
            slotChosen = route.slot != null,
            scanAvailable = scanEnabled(),
            addFoodAvailable = submitFoodEnabled(),
            searchOptions = searchOptionsEnabled(),
            pickingForMeal = pickForMeal != null,
        ),
        policy = policySource.policy.value,
    )

    private var searchJob: Job? = null

    /** The query and filter the running or last search asked for, so a rebuild only searches again when one changed. */
    private var requested: Pair<String, FoodSearchFilter>? = null

    /** Create a meal's picks went back; a second tap queued behind the first would hand them back twice. */
    private var handedBack = false

    private data class Model(
        val choices: SearchChoices,
        val policy: VisibilityPolicy,
        val inputs: SearchInputs? = null,
    )

    init {
        viewModelScope.launch {
            inputs(calendar, settingsRepository, savedMeals, clock).collect { takeAction(SearchAction.InputsArrived(it)) }
        }
        // Only with release.searchOptions on: off, search makes exactly the calls it made in 0.1.
        if (model.choices.searchOptions) {
            viewModelScope.launch {
                foodCatalog.searchOptions().getOrNull()?.let { takeAction(SearchAction.SearchOptionsArrived(it.openFoodFacts)) }
            }
        }
        viewModelScope.launch {
            policySource.policy.collect { takeAction(SearchAction.PolicyArrived(it)) }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun handleAction(action: SearchAction) {
        when (action) {
            is SearchAction.InputsArrived -> {
                action.rebuild { copy(inputs = action.inputs, choices = choices.withPickedSlot(action.inputs).withSeen(action.inputs)) }
                // A sort or database picked in the options sheet arrives here, with the settings.
                searchIfStale()
            }
            is SearchAction.PolicyArrived -> action.rebuild { copy(policy = action.policy) }
            is SearchAction.SlotPicked -> action.choose { copy(slot = action.slot, slotChosen = true) }
            is SearchAction.SearchTyped -> searchTyped(action, action.text)
            SearchAction.SearchCleared -> searchTyped(action, "")
            is SearchAction.SearchResultsArrived -> {
                val current = model.choices.query.trim() to FoodSearchStateBuilder.catalogFilter(model.inputs, model.choices)
                if (action.outcome.query to action.outcome.filter == current) action.choose { copy(search = action.outcome) }
            }
            is SearchAction.ScopePicked -> if (action.scope != model.choices.scope) {
                action.choose { copy(scope = action.scope) }
                searchIfStale()
            }
            is SearchAction.FoodNameTapped -> if (pickForMeal != null) {
                // A meal being put together takes the row's amount; the detail logs, so it is not for picking.
                pick(action, action.row)
            } else {
                action.choose { copy(focusSearch = false) }
                sendEvent(SearchEvent.OpenFoodDetail(action.row.ref, action.row.source, day, slot()))
            }
            is SearchAction.AddTapped -> if (pickForMeal != null) pick(action, action.row) else add(action.row)
            is SearchAction.SavedMealAddTapped -> addSavedMeal(action.id)
            SearchAction.BarcodeTapped -> if (model.choices.scanAvailable && pickForMeal == null) sendEvent(SearchEvent.OpenScanner(day, slot()))
            SearchAction.AddFoodTapped -> if (model.choices.addFoodAvailable && pickForMeal == null) sendEvent(SearchEvent.OpenSubmitFood(day, slot()))
            SearchAction.DescribeTapped -> if (pickForMeal == null) sendEvent(SearchEvent.OpenDescribe(day, slot(), model.choices.query.trim()))
            SearchAction.SaveNameTapped -> saveName(action)
            SearchAction.AddPicksTapped -> pickForMeal?.let(::handBack)
            SearchAction.BackTapped -> sendEvent(SearchEvent.Close)
            SearchAction.SearchOptionsTapped -> if (model.choices.searchOptions) {
                sendEvent(SearchEvent.OpenSearchOptions(model.choices.openFoodFacts))
            }
            is SearchAction.TabPicked -> if (model.choices.searchOptions && action.tab != model.choices.tab) {
                optionsTelemetry.tabPicked(action.tab)
                action.choose { copy(tab = action.tab) }
                searchIfStale()
            }
            is SearchAction.SearchOptionsArrived -> {
                action.choose { copy(openFoodFacts = action.openFoodFacts) }
                searchIfStale()
            }
        }
    }

    /** The slot writes go to: the one picked, or Uncategorized on a day with meal groups off. */
    private fun slot(): MealSlot =
        model.inputs?.let { FoodSearchStateBuilder.effectiveSlot(it, model.choices.slot) } ?: model.choices.slot

    private suspend fun SearchAction.rebuild(change: Model.() -> Model) {
        model = model.change()
        val current = model
        val inputs = current.inputs
        if (inputs == null) {
            // Nothing to build from yet, but the field still has to echo what was typed.
            updateState { it.copy(query = current.choices.query, scope = current.choices.scope) }
            return
        }
        updateState { FoodSearchStateBuilder.build(inputs, current.policy, current.choices) }
    }

    private suspend fun SearchAction.choose(change: SearchChoices.() -> SearchChoices) = rebuild { copy(choices = choices.change()) }

    private suspend fun searchTyped(action: SearchAction, text: String) {
        action.choose { copy(query = text, search = null, focusSearch = false) }
        // Every keystroke restarts the debounce, even back to a query asked for before.
        searchJob?.cancel()
        requested = null
        searchIfStale()
    }

    /**
     * Searches when the query, or what it is filtered by, differs from what was last asked for.
     * A scope or a tab that needs no server cancels any search still waiting.
     */
    private fun searchIfStale() {
        val query = model.choices.query.trim()
        val filter = FoodSearchStateBuilder.catalogFilter(model.inputs, model.choices)
        val wanted = filter?.takeIf { query.isNotEmpty() }?.let { query to it }
        if (wanted == requested) return
        requested = wanted
        searchJob?.cancel()
        if (wanted == null) return
        searchJob = viewModelScope.launch {
            // Asked a moment ago (backspacing, a chip and back): no debounce, no round trip.
            val result = foodCatalog.recentSearch(query, SEARCH_LIMIT, wanted.second) ?: run {
                delay(SEARCH_DEBOUNCE)
                foodCatalog.search(query, SEARCH_LIMIT, wanted.second).getOrNull()
            }
            val outcome = SearchOutcome(
                query = query,
                foods = result?.foods.orEmpty(),
                offline = result?.fromCache ?: true,
                filter = wanted.second,
            )
            takeAction(SearchAction.SearchResultsArrived(outcome))
        }
    }

    /**
     * A row's +: logged now at the amount the row shows. On a row already added since search
     * opened, it takes those items back out instead.
     */
    private suspend fun add(row: FoodRowUi) {
        val inputs = model.inputs ?: return
        val slot = slot()
        val added = FoodPicks.addedSince(inputs.day, slot, model.choices.seen)[row.key]
        if (added != null) {
            added.forEach { diary.removeItem(it) }
            return
        }
        val pick = FoodSearchStateBuilder.picks(inputs, model.choices).firstOrNull { it.key == row.key } ?: return
        val committed = logger.log(day, slot, listOf(pick.toLogPick())) ?: return
        committed.event?.let { sendEvent(SearchEvent.Added(row.shortName, it)) }
    }

    private suspend fun addSavedMeal(id: String) {
        if (pickForMeal != null) return
        val meal = model.inputs?.savedMeals?.firstOrNull { it.id == id } ?: return
        val picks = meal.items.mapIndexed { index, draft ->
            val ref = draft.foodRef()
            LogPick(key = ref?.key ?: "saved:$id:$index", ref = ref, draft = draft.copy(method = LogMethod.Saved), source = PickSource.Saved)
        }
        val committed = logger.log(day, slot(), picks) ?: return
        committed.event?.let { sendEvent(SearchEvent.Added(meal.name, it)) }
    }

    /** Create a meal: + puts the food on this screen's own list, or takes it off. */
    private suspend fun pick(action: SearchAction, row: FoodRowUi) {
        val inputs = model.inputs ?: return
        val picked = model.choices.picked
        val on = picked.none { it.key == row.key }
        val next = if (on) {
            val pick = FoodSearchStateBuilder.picks(inputs, model.choices).firstOrNull { it.key == row.key } ?: return
            picked + pick.toLogPick()
        } else {
            picked.filterNot { it.key == row.key }
        }
        action.choose { copy(picked = next) }
        sendEvent(SearchEvent.Picked(on))
    }

    /** "Save the name": a name-only food made (or reused) and logged now, or picked for a meal. */
    private suspend fun saveName(action: SearchAction) {
        val name = model.choices.query.trim().ifEmpty { return }
        val pick = nameOnlyPick(name, model.inputs?.customFoods.orEmpty(), customFoods)
        searchJob?.cancel()
        if (pickForMeal != null) {
            action.choose { copy(picked = picked.filterNot { it.key == pick.key } + pick, query = "", search = null) }
            return
        }
        action.choose { copy(query = "", search = null) }
        val committed = logger.log(day, slot(), listOf(pick)) ?: return
        committed.event?.let { sendEvent(SearchEvent.Added(name, it)) }
    }

    /** Create a meal's "Add to meal": the picks go back, and nothing is logged. */
    private fun handBack(requestId: String) {
        if (handedBack) return
        val drafts = model.choices.picked.map { it.draft }.ifEmpty { return }
        handedBack = true
        mealPicks.picked(PickedForMeal(requestId, drafts))
        sendEvent(SearchEvent.Close)
    }

    /** The meal as this person's logging times pick it, while nobody has chosen one. */
    private fun SearchChoices.withPickedSlot(inputs: SearchInputs): SearchChoices {
        if (slotChosen) return this
        val picked = MealSlotPicker.pick(inputs.recentDays, inputs.now, inputs.timeZone, inputs.day.mealGroupsOn(inputs.settings))
        return copy(slot = picked)
    }

    /** The day as it stood when first read: what the ✓ compares against. */
    private fun SearchChoices.withSeen(inputs: SearchInputs): SearchChoices =
        if (seen != null) this else copy(seen = inputs.day.allItems.mapTo(HashSet()) { it.id })

    private fun inputs(
        calendar: LocalCalendar,
        settingsRepository: SettingsRepository,
        savedMeals: SavedMealRepository,
        clock: Clock,
    ): Flow<SearchInputs> {
        val usualBySlot: Flow<Map<MealSlot, List<LogItem>>> =
            combine(MealSlot.meals.map { slot -> diary.usualAt(slot, limit = USUAL_LIMIT) }) { lists ->
                MealSlot.meals.zip(lists.toList()).toMap()
            }
        val today = calendar.today()
        val recentDays = diary.observeDays(today.minus(MealSlotPicker.HISTORY_DAYS, DateTimeUnit.DAY), today)
        val logged = combine(diary.recentFoods(limit = RECENT_LIMIT), diary.recentFoods(limit = HISTORY_LIMIT), diary.frequentFoods(limit = FREQUENT_LIMIT), ::Triple)
        val mine = combine(customFoods.observeAll(), savedMeals.observeAll(), ::Pair)
        val base = combine(diary.observeDay(day), settingsRepository.settings, usualBySlot, logged, combine(mine, recentDays, ::Pair)) {
                d, settings, usual, (recent, history, frequent), (own, days) ->
            val zone = calendar.timeZone()
            SearchInputs(
                today = calendar.today(),
                day = d,
                settings = settings,
                usualBySlot = usual,
                recent = recent,
                customFoods = own.first,
                savedMeals = own.second,
                history = history,
                frequent = frequent,
                recentDays = days,
                now = clock.now().toLocalDateTime(zone),
                timeZone = zone,
            )
        }
        // Source badges need the catalog foods the device knows; with the options off nothing more is collected.
        if (!model.choices.searchOptions) return base
        return combine(base, foodCatalog.observeRecent(limit = KNOWN_LIMIT)) { inputs, known ->
            inputs.copy(known = known.associateBy { it.id })
        }
    }

    companion object {
        val SEARCH_DEBOUNCE = 250.milliseconds
        const val SEARCH_LIMIT = 25
        const val USUAL_LIMIT = 3
        const val RECENT_LIMIT = 6

        /** How far back a food's last amount, Your foods and Most recent reach. */
        const val HISTORY_LIMIT = 100

        /** The foods logged most over the last month: "Your usual", and Favorites. */
        const val FREQUENT_LIMIT = 20

        /** Catalog foods kept on the device, read so a row from the diary can name its source. */
        const val KNOWN_LIMIT = 100
    }
}
