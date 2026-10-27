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
import com.opson.libraries.diary.MealSlotPicker
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
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.draftFor
import com.opson.libraries.foods.foodRef
import com.opson.libraries.foods.initialQuantity
import com.opson.libraries.foods.toLogDraft
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.speech.Heard
import com.opson.libraries.speech.MicPermission
import com.opson.libraries.speech.SpeechAvailability
import com.opson.libraries.speech.SpeechFailure
import com.opson.libraries.ui.components.text.NumberInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
    photoEnabled: PhotoEstimateEnabled,
    private val voice: DescribeVoice,
    searchOptionsEnabled: SearchOptionsEnabled,
    private val optionsTelemetry: SearchOptionsTelemetry,
) : SEAViewModel<LoggerState, LoggerEvent, LoggerAction>(
    initialStateArg = LoggerState(
        focusSearch = route.focusSearch,
        pane = route.start.pane,
        pickingForMeal = route.pickForMeal != null,
        height = route.openingHeight,
    ),
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
            height = route.openingHeight,
            // The nav + names no meal: the picker chooses from this person's logging times.
            slotChosen = route.slot != null,
            focusNumber = route.start == LoggerStart.Number,
            pickingForMeal = route.pickForMeal != null,
            scanAvailable = scanEnabled(),
            photoAvailable = photoEnabled(),
            voiceEnabled = voice.enabled(),
            searchOptions = searchOptionsEnabled(),
        ),
        policy = policySource.policy.value,
    )

    private var searchJob: Job? = null

    /** The query and filter the running or last search asked for, so a rebuild only searches again when one changed. */
    private var requested: Pair<String, FoodSearchFilter>? = null
    private var describeJob: Job? = null
    private var findJob: Job? = null
    private var listenJob: Job? = null
    private var voiceTimer: Job? = null
    private var voiceCap: Job? = null

    /** Whether the running session recognises on the phone, for `voice.started`. */
    private var onDevice = false

    /**
     * Something was written and the sheet is on its way down. A second tap on the same button
     * while it animates is still queued behind the first, and would write it all again.
     */
    private var written = false

    private data class Model(
        val choices: LoggerChoices,
        val policy: VisibilityPolicy,
        val inputs: LoggerInputs? = null,
    )

    init {
        viewModelScope.launch {
            inputs(calendar, settingsRepository, model.choices.searchOptions, clock).collect { takeAction(LoggerAction.InputsArrived(it)) }
        }
        // Only with release.searchOptions on: off, the sheet makes exactly the calls it made in 0.1.
        if (model.choices.searchOptions) {
            viewModelScope.launch {
                foodCatalog.searchOptions().getOrNull()?.let { takeAction(LoggerAction.SearchOptionsArrived(it.openFoodFacts)) }
            }
        }
        viewModelScope.launch {
            policySource.policy.collect { takeAction(LoggerAction.PolicyArrived(it)) }
        }
        // Scoped to this back stack entry, so a pre-pick from Foods lands once however often the sheet recomposes.
        if (pickForMeal == null) viewModelScope.launch { prePick(route) }
    }

    override suspend fun handleAction(action: LoggerAction) {
        when (action) {
            is LoggerAction.InputsArrived -> {
                action.rebuild { copy(inputs = action.inputs, choices = choices.withPickedSlot(action.inputs)) }
                // A sort or database picked in the options sheet arrives here, with the settings.
                searchIfStale()
            }
            is LoggerAction.PolicyArrived -> action.rebuild { copy(policy = action.policy) }
            is LoggerAction.SlotPicked -> action.choose { copy(slot = action.slot, slotChosen = true) }
            LoggerAction.SheetExpanded, LoggerAction.SearchFocused -> if (model.choices.height != SheetHeight.Full) {
                action.choose { copy(height = SheetHeight.Full) }
            }
            is LoggerAction.SearchTyped -> searchTyped(action, action.text)
            LoggerAction.SearchCleared -> searchTyped(action, "")
            is LoggerAction.SearchResultsArrived -> {
                val current = model.choices.query.trim() to LoggerStateBuilder.catalogFilter(model.inputs, model.choices)
                if (action.outcome.query to action.outcome.filter == current) action.choose { copy(search = action.outcome) }
            }
            LoggerAction.SearchOptionsTapped -> if (model.choices.searchOptions) {
                sendEvent(LoggerEvent.OpenSearchOptions(model.choices.openFoodFacts))
            }
            is LoggerAction.TabPicked -> if (model.choices.searchOptions && action.tab != model.choices.tab) {
                optionsTelemetry.tabPicked(action.tab)
                action.choose { copy(tab = action.tab) }
                searchIfStale()
            }
            is LoggerAction.SearchOptionsArrived -> {
                action.choose { copy(openFoodFacts = action.openFoodFacts) }
                searchIfStale()
            }
            // The amount sheet logs, so a meal being put together takes the row's default amount.
            is LoggerAction.FoodNameTapped -> if (pickForMeal != null) {
                toggle(action.row)
            } else {
                sendEvent(LoggerEvent.OpenAmount(action.row.ref, action.row.source, day, slot()))
            }
            is LoggerAction.FoodToggleTapped -> toggle(action.row)
            LoggerAction.DescribeItTapped -> action.choose { copy(pane = LoggerPane.Describe, focusNumber = false, height = SheetHeight.Full) }
            LoggerAction.DescribeTermTapped -> {
                val term = model.choices.query.trim()
                action.choose { copy(pane = LoggerPane.Describe, focusNumber = false, height = SheetHeight.Full) }
                describeTyped(action, term)
            }
            LoggerAction.SaveNameTapped -> saveName(action)
            LoggerAction.SavedMealsTapped -> action.choose { copy(pane = LoggerPane.Saved, height = SheetHeight.Full) }
            is LoggerAction.SavedMealPicked -> pickSavedMeal(action, action.id)
            LoggerAction.YesterdayTapped -> copyYesterday()
            LoggerAction.AteNoDetailsTapped -> ateNoDetails()
            LoggerAction.JustANumberTapped -> action.choose { copy(pane = LoggerPane.Describe, focusNumber = true, height = SheetHeight.Full) }
            LoggerAction.AddANoteTapped -> {
                telemetry.methodPicked(LogVia.Note, items = 0)
                sendEvent(LoggerEvent.OpenMealNote(day, slot()))
            }
            is LoggerAction.DescribeTyped -> {
                // A field can echo its own value back (iOS does around system prompts); only a real edit counts as typing.
                val edited = action.text != model.choices.describeText
                if (edited) typedOverVoice(action)
                if (edited || !model.choices.voice.busy) describeTyped(action, action.text)
            }
            is LoggerAction.DescribeArrived -> describeArrived(action)
            is LoggerAction.DescribeFailed -> {
                if (action.text == model.choices.describeText.trim()) {
                    val status = if (appState.isOffline.value) DescribeStatus.Offline else DescribeStatus.Failed
                    reviewed(action, DescribeRows.cleared(model.choices.review)) { copy(describeStatus = status) }
                }
            }
            is DescribeReviewAction -> reviewAction(action)
            is LoggerAction.NumberTyped -> action.choose { copy(number = NumberInput.sanitize(action.text, allowDecimal = false)) }
            LoggerAction.AddNumberTapped -> addNumber()
            LoggerAction.BackTapped -> back(action)
            LoggerAction.AddToMealTapped -> pickForMeal?.let(::handBack) ?: commit()
            LoggerAction.CloseTapped -> sendEvent(LoggerEvent.Dismiss)
            is VoiceAction -> voiceAction(action)
            LoggerAction.BarcodeTapped -> if (model.choices.scanAvailable && pickForMeal == null) {
                sendEvent(LoggerEvent.OpenScanner(day, slot()))
            }
            LoggerAction.PhotoTapped -> if (model.choices.photoAvailable && pickForMeal == null) {
                sendEvent(LoggerEvent.OpenPhoto(day, slot()))
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
        // Typing goes full height so the results have room; clearing never shrinks it back.
        action.choose {
            copy(query = text, search = null, focusSearch = false, height = if (text.isNotBlank()) SheetHeight.Full else height)
        }
        // Every keystroke restarts the debounce, even back to a query asked for before.
        searchJob?.cancel()
        requested = null
        searchIfStale()
    }

    /**
     * Searches when the query, or what it is filtered by, differs from what was
     * last asked for. A tab or a database that needs no server cancels any
     * search still waiting.
     */
    private fun searchIfStale() {
        val query = model.choices.query.trim()
        val filter = LoggerStateBuilder.catalogFilter(model.inputs, model.choices)
        val wanted = filter?.takeIf { query.isNotEmpty() }?.let { query to it }
        if (wanted == requested) return
        requested = wanted
        searchJob?.cancel()
        if (wanted == null) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            val result = foodCatalog.search(query, SEARCH_LIMIT, wanted.second).getOrNull()
            val outcome = SearchOutcome(
                query = query,
                foods = result?.foods.orEmpty(),
                offline = result?.fromCache ?: true,
                filter = wanted.second,
            )
            takeAction(LoggerAction.SearchResultsArrived(outcome))
        }
    }

    private suspend fun describeTyped(action: LoggerAction, text: String, debounce: Duration = DESCRIBE_DEBOUNCE) {
        val parsing = text.isNotBlank()
        action.choose { copy(describeText = text, describeStatus = if (parsing) DescribeStatus.Parsing else DescribeStatus.Idle) }
        describeJob?.cancel()
        if (!parsing) {
            reviewed(action, DescribeRows.cleared(model.choices.review))
            return
        }
        val trimmed = text.trim()
        describeJob = viewModelScope.launch {
            delay(debounce)
            val items = foodCatalog.describe(trimmed).getOrNull()
            takeAction(if (items == null) LoggerAction.DescribeFailed(trimmed) else LoggerAction.DescribeArrived(trimmed, items))
        }
    }

    private suspend fun describeArrived(action: LoggerAction.DescribeArrived) {
        if (action.text != model.choices.describeText.trim()) return
        // A new parse replaces the last one's lines, except those the person changed or added; picks from search stay.
        val review = DescribeRows.read(model.choices.review, action.items, householdOnly())
        reviewed(action, review) { copy(describeStatus = if (action.items.isEmpty()) DescribeStatus.Failed else DescribeStatus.Parsed) }
    }

    /** Calories hidden: a line's amount is a household measure, never grams. */
    private fun householdOnly(): Boolean = model.policy.level(Metric.Calories) == Visibility.Hide

    /** Describe's lines changed: the state shows them and the basket holds exactly them. */
    private suspend fun reviewed(action: LoggerAction, review: DescribeReview, also: LoggerChoices.() -> LoggerChoices = { this }) {
        action.choose { copy(review = review).also() }
        val keys = review.rows.map { it.key }.toSet()
        basketStore.removeWhere { it.source == PickSource.Describe && it.key !in keys }
        review.rows.forEach { basketStore.upsert(it.toBasketItem()) }
    }

    // Describe's review (docs/plans/screens/describe.md): every change is to one line, and none touches the others.

    private suspend fun reviewAction(action: DescribeReviewAction) {
        val review = model.choices.review
        val householdOnly = householdOnly()
        fun change(key: String, edit: (DescribeRow) -> DescribeRow) =
            review.copy(rows = review.rows.map { if (it.key == key) edit(it) else it })
        when (action) {
            is DescribeReviewAction.LessTapped -> reviewed(action, change(action.key, DescribeRows::less))
            is DescribeReviewAction.MoreTapped -> reviewed(action, change(action.key, DescribeRows::more))
            is DescribeReviewAction.UnitPicked -> reviewed(action, change(action.key) { DescribeRows.unitPicked(it, action.index, householdOnly) })
            is DescribeReviewAction.AlternativePicked -> {
                val alternative = review.rows.firstOrNull { it.key == action.key }?.alternatives?.getOrNull(action.index) ?: return
                reviewed(action, change(action.key) { DescribeRows.swapped(it, alternative, householdOnly) })
            }
            is DescribeReviewAction.RemoveTapped -> {
                val row = review.rows.firstOrNull { it.key == action.key } ?: return
                val dismissed = if (row.added) review.dismissed else review.dismissed + row.said
                reviewed(action, review.copy(rows = review.rows - row, dismissed = dismissed))
            }
            is DescribeReviewAction.FindTapped -> {
                findJob?.cancel()
                // Swapping a line the catalog didn't know starts from its words.
                val words = action.replacing?.let { key -> review.rows.firstOrNull { it.key == key }?.takeIf { it.food == null }?.name }.orEmpty()
                action.choose { copy(review = review.copy(find = DescribeFind(replacing = action.replacing, query = words))) }
                if (words.isNotBlank()) findFoods(words)
            }
            is DescribeReviewAction.FindTyped -> {
                val find = review.find ?: return
                action.choose { copy(review = review.copy(find = find.copy(query = action.text))) }
                findFoods(action.text)
            }
            is DescribeReviewAction.FindResultsArrived -> {
                val find = review.find?.takeIf { it.query.trim() == action.query } ?: return
                action.choose { copy(review = review.copy(find = find.copy(answer = SwapAnswer(action.query, action.foods)))) }
            }
            is DescribeReviewAction.FindPicked -> {
                val find = review.find ?: return
                val food = find.answer?.foods?.firstOrNull { it.id == action.id } ?: return
                findJob?.cancel()
                val replacing = find.replacing
                val next = if (replacing != null) {
                    change(replacing) { DescribeRows.swapped(it, food, householdOnly) }
                } else {
                    val row = DescribeRows.added("describe:${review.nextKey}", find.query.trim(), food, householdOnly)
                    review.copy(rows = review.rows + row, nextKey = review.nextKey + 1)
                }
                reviewed(action, next.copy(find = null))
            }
            DescribeReviewAction.FindClosed -> {
                findJob?.cancel()
                action.choose { copy(review = review.copy(find = null)) }
            }
        }
    }

    private fun findFoods(text: String) {
        findJob?.cancel()
        val query = text.trim()
        if (query.isEmpty()) return
        findJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            val foods = foodCatalog.search(query, FIND_LIMIT).getOrNull()?.foods.orEmpty()
            takeAction(DescribeReviewAction.FindResultsArrived(query, foods))
        }
    }

    private suspend fun back(action: LoggerAction) {
        when (model.choices.pane) {
            LoggerPane.Search -> sendEvent(LoggerEvent.Dismiss)
            LoggerPane.Describe -> {
                // Describe's lines are only picks while Describe is open.
                describeJob?.cancel()
                findJob?.cancel()
                endVoice()
                basketStore.removeWhere { it.source == PickSource.Describe }
                action.choose {
                    copy(
                        pane = LoggerPane.Search,
                        describeText = "",
                        describeStatus = DescribeStatus.Idle,
                        review = DescribeReview(),
                        number = "",
                        focusNumber = false,
                        voice = VoiceCapture.Idle(),
                    )
                }
            }
            LoggerPane.Saved -> action.choose { copy(pane = LoggerPane.Search) }
        }
    }

    private fun toggle(row: FoodRowUi) {
        val inputs = model.inputs ?: return
        val pick = LoggerStateBuilder.picks(inputs, model.choices)
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
        findJob?.cancel()
        endVoice()
        basketStore.removeWhere { it.source == PickSource.Describe }
        searchJob?.cancel()
        action.choose {
            copy(
                pane = LoggerPane.Search,
                query = "",
                search = null,
                describeText = "",
                describeStatus = DescribeStatus.Idle,
                review = DescribeReview(),
                voice = VoiceCapture.Idle(),
            )
        }
    }

    private suspend fun pickSavedMeal(action: LoggerAction, id: String) {
        val meal = model.inputs?.savedMeals?.firstOrNull { it.id == id } ?: savedMeals.get(id) ?: return
        meal.items.forEachIndexed { index, draft -> basketStore.upsert(savedItem(id, index, draft)) }
        action.choose { copy(pane = LoggerPane.Search) }
    }

    private suspend fun copyYesterday() {
        if (model.inputs == null || written) return
        val slot = slot()
        val copied = diary.copySlot(day.minus(1, DateTimeUnit.DAY), day, slot)
        telemetry.methodPicked(LogVia.Yesterday, copied.size)
        committer.announce(day, slot, copied)
        finish()
    }

    private suspend fun ateNoDetails() {
        if (written) return
        written = true
        val slot = slot()
        diary.setAteNoDetails(day, slot, ate = true)
        telemetry.methodPicked(LogVia.AteNoDetails, items = 0)
        sendEvent(LoggerEvent.ShowToast(Toast(Res.string.logger_toast_ate, listOf(slot.sentenceName))))
        sendEvent(LoggerEvent.Dismiss)
    }

    private suspend fun addNumber() {
        if (model.inputs == null || written) return
        // Typing a number is a calorie entry, so the policy has the last word even if the field was showing.
        if (model.policy.level(Metric.Calories) == Visibility.Hide) return
        val kcal = model.choices.number.toIntOrNull()?.takeIf { it > 0 } ?: return
        val slot = slot()
        val draft = LogDraft(name = QUICK_ADD_NAME, precision = Precision.Named, kcal = kcal.toDouble(), method = LogMethod.QuickNumber)
        val items: List<LogItem> = diary.addItems(day, slot, listOf(draft), LogMethod.QuickNumber)
        telemetry.methodPicked(LogVia.QuickNumber, items.size)
        committer.announce(day, slot, items)
        finish()
    }

    private suspend fun commit() {
        if (model.inputs == null || written) return
        committer.commit(day, slot()) ?: return
        finish()
    }

    /** Pick-for-this-meal mode's "Add to this meal": the picks go back to Create a meal, and nothing is logged. */
    private fun handBack(requestId: String) {
        if (written) return
        val drafts = basketStore.basket.value.map { it.draft }.ifEmpty { return }
        written = true
        mealPicks.picked(PickedForMeal(requestId, drafts))
        basketStore.clear()
        sendEvent(LoggerEvent.Dismiss)
    }

    /** Logged: the sheet closes and Today takes it from `RecentLogEvents`. How it felt no longer follows (design sync 2). */
    private fun finish() {
        written = true
        sendEvent(LoggerEvent.Dismiss)
    }

    /**
     * The meal as this person's logging times pick it, while nobody has chosen one: the nav +
     * opened the sheet and the dropdown has not been touched.
     */
    private fun LoggerChoices.withPickedSlot(inputs: LoggerInputs): LoggerChoices {
        if (slotChosen) return this
        val picked = MealSlotPicker.pick(inputs.recentDays, inputs.now, inputs.timeZone, inputs.day.mealGroupsOn(inputs.settings))
        return copy(slot = picked)
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

    private fun inputs(calendar: LocalCalendar, settingsRepository: SettingsRepository, searchOptions: Boolean, clock: Clock): Flow<LoggerInputs> {
        val usualBySlot: Flow<Map<MealSlot, List<LogItem>>> =
            combine(MealSlot.meals.map { slot -> diary.usualAt(slot, limit = USUAL_LIMIT) }) { lists ->
                MealSlot.meals.zip(lists.toList()).toMap()
            }
        val days = combine(diary.observeDay(day), diary.observeDay(day.minus(1, DateTimeUnit.DAY))) { d, before -> d to before }
        val lists = combine(usualBySlot, diary.recentFoods(limit = RECENT_LIMIT), ::Pair)
        val today = calendar.today()
        val history = diary.observeDays(today.minus(MealSlotPicker.HISTORY_DAYS, DateTimeUnit.DAY), today)
        val mine = combine(customFoods.observeAll(), savedMeals.observeAll(), history, ::Triple)
        val base = combine(days, settingsRepository.settings, basketStore.basket, lists, mine) {
                (d, before), settings, basket, (usual, recent), (custom, saved, recentDays) ->
            val zone = calendar.timeZone()
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
                recentDays = recentDays,
                now = clock.now().toLocalDateTime(zone),
                timeZone = zone,
            )
        }
        // The search options read three more lists; with the flag off nothing more is collected.
        if (!searchOptions) return base
        val more = combine(
            diary.frequentFoods(limit = FAVORITES_LIMIT),
            diary.recentFoods(limit = HISTORY_LIMIT),
            foodCatalog.observeRecent(limit = KNOWN_LIMIT),
        ) { favorites, history, known -> Triple(favorites, history, known.associateBy { it.id }) }
        return combine(base, more) { inputs, (favorites, history, known) ->
            inputs.copy(favorites = favorites, history = history, known = known)
        }
    }

    // Voice log (docs/plans/screens/voice.md). Speech only ever fills describeText;
    // from the final transcript on it is the typed path, through describeTyped.

    private suspend fun voiceAction(action: VoiceAction) {
        if (!model.choices.voiceEnabled) return
        val capture = model.choices.voice
        when (action) {
            VoiceAction.MicTapped -> when (capture) {
                is VoiceCapture.Listening -> stopListening(action, capture)
                VoiceCapture.Asking, is VoiceCapture.Transcribing -> Unit
                is VoiceCapture.Idle, is VoiceCapture.Notice -> micTapped(action)
            }
            // A yes starts listening even if something moved the capture on while the prompt was up: the person asked.
            is VoiceAction.MicPermissionAnswered -> if (capture !is VoiceCapture.Listening && capture !is VoiceCapture.Transcribing) {
                when (val answer = action.permission) {
                    MicPermission.Granted -> startListening(action, asked = true)
                    is MicPermission.Denied -> denied(action, answer.canAskAgain)
                    // Neither yes nor no: say what voice needs and offer to ask again, never a silent return to idle.
                    MicPermission.NotAsked -> denied(action, canAskAgain = true)
                }
            }
            is VoiceAction.SpeechArrived -> speechArrived(action, capture, action.heard)
            VoiceAction.WentQuiet -> if (capture is VoiceCapture.Listening) stopListening(action, capture)
            VoiceAction.TranscriptOverdue -> if (capture is VoiceCapture.Transcribing) transcribed(action, capture.prefix, capture.heard)
            VoiceAction.TypeInsteadTapped -> action.choose { copy(voice = VoiceCapture.Idle(focusText = true)) }
            VoiceAction.OpenSettingsTapped -> {
                voice.speech.openSettings()
                action.choose { copy(voice = VoiceCapture.Idle()) }
            }
            // Keep what was heard: the mic cannot run in the background, and the words are the person's.
            VoiceAction.ScreenPaused -> when (capture) {
                is VoiceCapture.Listening -> transcribed(action, capture.prefix, capture.heard)
                is VoiceCapture.Transcribing -> transcribed(action, capture.prefix, capture.heard)
                // The permission prompt itself pauses the screen on Android.
                is VoiceCapture.Idle, VoiceCapture.Asking, is VoiceCapture.Notice -> Unit
            }
        }
    }

    private suspend fun micTapped(action: LoggerAction) {
        when (val availability = voice.speech.availability()) {
            is SpeechAvailability.Unavailable -> fallBack(action, availability.why, restore = null)
            is SpeechAvailability.Ready -> {
                onDevice = availability.onDevice
                when (val permission = voice.speech.permission()) {
                    MicPermission.Granted -> startListening(action, asked = false)
                    MicPermission.NotAsked -> askForMic(action)
                    // A tap on the mic is the person asking, so a prompt Android will still show is shown.
                    is MicPermission.Denied -> if (permission.canAskAgain) askForMic(action) else denied(action, canAskAgain = false)
                }
            }
        }
    }

    private suspend fun askForMic(action: LoggerAction) {
        action.choose { copy(voice = VoiceCapture.Asking) }
        sendEvent(RequestMicPermission)
    }

    private suspend fun startListening(action: LoggerAction, asked: Boolean) {
        // A parse of typed text still waiting would be overtaken by what is said; the final text parses it all.
        describeJob?.cancel()
        val typed = model.choices.describeText.trim()
        val prefix = if (typed.isEmpty()) "" else "$typed "
        action.choose {
            copy(
                voice = VoiceCapture.Listening(prefix, heard = ""),
                describeStatus = if (describeStatus == DescribeStatus.Parsing) DescribeStatus.Idle else describeStatus,
            )
        }
        voice.telemetry.started(onDevice, asked)
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            voice.speech.listen()
                // However the platform fails, the person sees why and can type instead.
                .catch { emit(Heard.Failed(SpeechFailure.Unknown)) }
                .collect { takeAction(VoiceAction.SpeechArrived(it)) }
        }
        armQuiet(NOTHING_SAID)
        voiceCap?.cancel()
        voiceCap = viewModelScope.launch {
            delay(LISTEN_LIMIT)
            takeAction(VoiceAction.WentQuiet)
        }
    }

    private suspend fun speechArrived(action: LoggerAction, capture: VoiceCapture, heard: Heard) {
        val (prefix, sofar) = when (capture) {
            is VoiceCapture.Listening -> capture.prefix to capture.heard
            is VoiceCapture.Transcribing -> capture.prefix to capture.heard
            // A late answer from a session already ended.
            is VoiceCapture.Idle, VoiceCapture.Asking, is VoiceCapture.Notice -> return
        }
        when (heard) {
            is Heard.Partial -> {
                val next = when (capture) {
                    is VoiceCapture.Listening -> capture.copy(heard = heard.text)
                    else -> VoiceCapture.Transcribing(prefix, heard.text)
                }
                action.choose { copy(voice = next, describeText = prefix + heard.text) }
                if (capture is VoiceCapture.Listening) armQuiet(QUIET_AFTER_SPEECH)
            }
            is Heard.Final -> transcribed(action, prefix, heard.text)
            // Words already heard are worth more than the error that ended the session.
            is Heard.Failed -> if (sofar.isNotBlank()) {
                transcribed(action, prefix, sofar)
            } else {
                fallBack(action, heard.why, restore = prefix)
            }
        }
    }

    private suspend fun stopListening(action: LoggerAction, capture: VoiceCapture.Listening) {
        voice.speech.finishListening()
        voiceCap?.cancel()
        action.choose { copy(voice = VoiceCapture.Transcribing(capture.prefix, capture.heard)) }
        voiceTimer?.cancel()
        voiceTimer = viewModelScope.launch {
            delay(TRANSCRIBE_TIMEOUT)
            takeAction(VoiceAction.TranscriptOverdue)
        }
    }

    /** The session is over with [text] heard after [prefix]: from here it is exactly the typed path. */
    private suspend fun transcribed(action: LoggerAction, prefix: String, text: String) {
        endVoice()
        if (text.isBlank()) {
            fallBack(action, SpeechFailure.NoSpeech, restore = prefix)
            return
        }
        voice.telemetry.transcribed(WordBucket.of(text))
        action.choose { copy(voice = VoiceCapture.Idle(afterSession = true)) }
        describeTyped(action, prefix + text.trim(), debounce = Duration.ZERO)
    }

    /** Voice cannot help this time; typing can. [restore] puts back what was typed before the session, if there was one. */
    private suspend fun fallBack(action: LoggerAction, why: SpeechFailure, restore: String?) {
        endVoice()
        // Turned off in Settings while the sheet was open: the same notice, and the same event, as a refusal.
        if (why == SpeechFailure.NotAllowed) {
            voice.telemetry.permissionDenied(canAskAgain = false)
        } else {
            voice.telemetry.fallback(VoiceFallback.of(why))
        }
        action.choose {
            copy(voice = VoiceCapture.Notice(why.toNotice()), describeText = restore?.trimEnd() ?: describeText)
        }
    }

    private suspend fun denied(action: LoggerAction, canAskAgain: Boolean) {
        voice.telemetry.permissionDenied(canAskAgain)
        action.choose { copy(voice = VoiceCapture.Notice(VoiceNotice.Denied(canAskAgain))) }
    }

    /**
     * Typing while the mic is on means the person took over: stop listening and keep what is in
     * the field. Typing is also the way past any notice, so that goes too.
     */
    private suspend fun typedOverVoice(action: LoggerAction) {
        val next = when (model.choices.voice) {
            is VoiceCapture.Listening, is VoiceCapture.Transcribing -> VoiceCapture.Idle(afterSession = true)
            is VoiceCapture.Idle, is VoiceCapture.Notice -> VoiceCapture.Idle()
            VoiceCapture.Asking -> return
        }
        endVoice()
        if (next != model.choices.voice) action.choose { copy(voice = next) }
    }

    private fun armQuiet(after: Duration) {
        voiceTimer?.cancel()
        voiceTimer = viewModelScope.launch {
            delay(after)
            takeAction(VoiceAction.WentQuiet)
        }
    }

    /** Stops the mic without waiting for a transcript. */
    private fun endVoice() {
        listenJob?.cancel()
        listenJob = null
        voiceTimer?.cancel()
        voiceCap?.cancel()
    }

    companion object {
        val SEARCH_DEBOUNCE = 250.milliseconds

        /** Longer than search: a parse is a heavier call, and people pause mid-sentence. */
        val DESCRIBE_DEBOUNCE = 700.milliseconds

        /** A pause this long after speech means the person is done. */
        val QUIET_AFTER_SPEECH = 2.seconds

        /** Nothing said at all for this long after the mic opens: stop rather than listen to the room. */
        val NOTHING_SAID = 8.seconds

        /** iOS stops server recognition at about a minute; a meal never needs that long. */
        val LISTEN_LIMIT = 55.seconds

        /** How long to wait for the recognizer to settle after the mic stops before keeping what was heard. */
        val TRANSCRIBE_TIMEOUT = 4.seconds
        const val SEARCH_LIMIT = 25

        /** Describe's find-a-food search: a short list, it is looking for one food. */
        const val FIND_LIMIT = 15
        const val USUAL_LIMIT = 3
        const val RECENT_LIMIT = 6

        /** Favorites: the foods logged most over the last month. */
        const val FAVORITES_LIMIT = 20

        /** Most recent and My foods: how far back the person's own foods go. */
        const val HISTORY_LIMIT = 30

        /** Catalog foods kept on the device, read so a row from the diary can name its source. */
        const val KNOWN_LIMIT = 100

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

/**
 * How tall the sheet opens. From a + it opens at 60% so Today stays in view; opened to type, on
 * Describe or Saved, or to pick for Create a meal, it opens full.
 */
private val LoggerRoute.openingHeight: SheetHeight
    get() = if (focusSearch || start != LoggerStart.Search || pickForMeal != null) SheetHeight.Full else SheetHeight.Partial

private val LoggerStart.pane: LoggerPane
    get() = when (this) {
        LoggerStart.Search -> LoggerPane.Search
        LoggerStart.Describe, LoggerStart.Number -> LoggerPane.Describe
        LoggerStart.Saved -> LoggerPane.Saved
    }
