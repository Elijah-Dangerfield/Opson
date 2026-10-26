package com.opson.features.logger.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.FoodKind
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealSlotPicker
import com.opson.libraries.diary.SearchDatabase
import com.opson.libraries.diary.SearchSort
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodAmount
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.draftFor
import com.opson.libraries.foods.foodRef
import com.opson.libraries.foods.initialQuantity
import com.opson.libraries.foods.toLogDraft
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.macroLine
import com.opson.libraries.ui.time.RelativeDay

/** A food the logger can put in the basket as-is: a row's default amount, ready to log. */
data class FoodPick(
    val key: String,
    val ref: FoodRef,
    val draft: LogDraft,
    val source: PickSource,
    val food: Food? = null,
    val subtitle: String? = null,
) {
    fun toBasketItem(): BasketItem = BasketItem(key = key, ref = ref, draft = draft, source = source, food = food)
}

/** What the person has done on the sheet so far, as opposed to what it read. */
data class LoggerChoices(
    val slot: MealSlot,
    val pane: LoggerPane = LoggerPane.Search,
    val query: String = "",
    val search: SearchOutcome? = null,
    val describeText: String = "",
    val describeStatus: DescribeStatus = DescribeStatus.Idle,
    val number: String = "",
    val focusSearch: Boolean = false,
    val focusNumber: Boolean = false,
    /** Picking for a meal Create a meal is putting together: search only, nothing written to the diary. */
    val pickingForMeal: Boolean = false,
    /** `release.barcode`, read once when the sheet opens. */
    val scanAvailable: Boolean = false,
    /** `release.photo`, read once when the sheet opens. */
    val photoAvailable: Boolean = false,
    /** `release.voice`, read once when the sheet opens. */
    val voiceEnabled: Boolean = false,
    val voice: VoiceCapture = VoiceCapture.Idle(),
    /** `release.searchOptions`, read once when the sheet opens. */
    val searchOptions: Boolean = false,
    /** How tall the sheet is; it only ever grows. */
    val height: SheetHeight = SheetHeight.Partial,
    /**
     * The meal was given (a meal's own +) or picked from the dropdown, so the picker's answer
     * from this person's logging times no longer moves it.
     */
    val slotChosen: Boolean = true,
    /** The category tab; the sheet always opens on All. */
    val tab: SearchTab = SearchTab.All,
    /** Whether the server offers Open Food Facts as a database. */
    val openFoodFacts: Boolean = false,
)

/**
 * The logger sheet's state as a pure function of what it reads and what the
 * user has done, so previews and the Blind leak test run the same mapping as
 * the app. Every number goes through [VisibilityPolicy] on [Surface.Logger].
 */
object LoggerStateBuilder {

    fun build(inputs: LoggerInputs, policy: VisibilityPolicy, choices: LoggerChoices): LoggerState {
        val groupsOn = inputs.day.mealGroupsOn(inputs.settings)
        val slot = effectiveSlot(inputs, choices.slot)
        val context = inputs.day.formatContext(inputs.today)
        val bare = inputs.day.formatContext(inputs.today, bare = true)
        val typing = choices.query.isNotBlank()
        val canNumber = policy.level(Metric.Calories) != Visibility.Hide && !choices.pickingForMeal
        // The method chips are ways to log; on a tab other than All the person is looking for a food.
        val onAll = !choices.searchOptions || !inputs.settings.searchTabs || choices.tab == SearchTab.All
        return LoggerState(
            loading = false,
            day = RelativeDay.of(inputs.day.date, inputs.today),
            slot = slot,
            slots = if (groupsOn) MealSlot.entries else listOf(MealSlot.Uncategorized),
            pane = choices.pane,
            query = choices.query,
            focusSearch = choices.focusSearch,
            height = choices.height,
            pickingForMeal = choices.pickingForMeal,
            // A scan logs into this meal, so a meal being put together has no scanner.
            canScan = choices.scanAvailable && !choices.pickingForMeal,
            // The method chips all log straight to the diary, so pick mode has none.
            methods = if (typing || choices.pickingForMeal || !onAll) emptyList() else methods(inputs, slot, choices.photoAvailable),
            // Both log at once, which would leave the picks behind, so they wait until nothing is picked.
            quickMethods = if (typing || choices.pickingForMeal || !onAll || inputs.basket.isNotEmpty()) {
                emptyList()
            } else {
                quickMethods(policy, slot)
            },
            body = body(inputs, policy, slot, choices, bare),
            describe = DescribeUi(
                text = choices.describeText,
                status = choices.describeStatus,
                lines = inputs.basket.filter { it.source == PickSource.Describe }.map { item ->
                    DescribedLine(
                        key = item.key,
                        ref = item.ref,
                        name = item.draft.name,
                        amount = item.draft.amountLabel,
                        calories = policy.resolve(Metric.Calories, item.draft.kcal, Surface.Logger, bare),
                    )
                },
                canNumber = canNumber,
                number = if (canNumber) choices.number else "",
                focusNumber = choices.focusNumber && canNumber,
                // A spoken description becomes the same text and the same lines as a typed one.
                voice = VoiceStateBuilder.build(choices.voiceEnabled && !choices.pickingForMeal, choices.voice),
            ),
            saved = SavedUi(
                meals = inputs.savedMeals.map { meal ->
                    SavedMealUi(
                        id = meal.id,
                        name = meal.name,
                        foods = meal.items.joinToString(", ") { shortName(it.name) },
                        calories = policy.resolve(Metric.Calories, meal.totals.kcal.value, Surface.Logger, context),
                    )
                },
            ),
            basket = if (choices.pane == LoggerPane.Search) basket(inputs, policy, slot, context) else null,
            options = if (choices.searchOptions) optionsBar(inputs, choices) else null,
        )
    }

    /** The slot a commit lands in: [slot], or Uncategorized when the day has meal groups off. */
    fun effectiveSlot(inputs: LoggerInputs, slot: MealSlot): MealSlot =
        if (inputs.day.mealGroupsOn(inputs.settings)) slot else MealSlot.Uncategorized

    /**
     * Every food the body can show for these choices, in order. The view
     * model finds a tapped row's draft here, so the state never has to carry
     * one.
     */
    fun picks(inputs: LoggerInputs, choices: LoggerChoices): List<FoodPick> =
        when (val listing = listing(inputs, effectiveSlot(inputs, choices.slot), choices)) {
            Listing.Searching, is Listing.Hint -> emptyList()
            is Listing.Browse -> listing.usual + listing.recent
            is Listing.Matches -> listing.picks
            is Listing.Listed -> listing.picks
        }

    /**
     * What the catalog is asked for with these settings and choices, or null
     * when what is showing needs no server (Favorites, Custom, My foods). With
     * the options off it is always [FoodSearchFilter.None], the call 0.1 makes.
     */
    fun catalogFilter(inputs: LoggerInputs?, choices: LoggerChoices): FoodSearchFilter? = when {
        !choices.searchOptions || inputs == null -> FoodSearchFilter.None
        else -> searchFilterFor(inputs.settings, effective(inputs, choices))
    }

    private fun methods(inputs: LoggerInputs, slot: MealSlot, photo: Boolean): List<LoggerMethod> = buildList {
        if (photo) add(LoggerMethod.Photo)
        add(LoggerMethod.DescribeIt)
        if (inputs.dayBefore.items(slot).isNotEmpty()) add(LoggerMethod.Yesterday(slot))
        add(LoggerMethod.SavedMeals)
    }

    private fun quickMethods(policy: VisibilityPolicy, slot: MealSlot): List<LoggerMethod> = buildList {
        if (slot.isMeal) add(LoggerMethod.AteNoDetails)
        add(if (policy.level(Metric.Calories) != Visibility.Hide) LoggerMethod.JustANumber else LoggerMethod.AddANote)
    }

    private fun body(
        inputs: LoggerInputs,
        policy: VisibilityPolicy,
        slot: MealSlot,
        choices: LoggerChoices,
        bare: FormatContext,
    ): LoggerBody {
        val picked = inputs.basket.mapTo(HashSet()) { it.key }
        val known = if (choices.searchOptions) inputs.known else null
        fun rows(picks: List<FoodPick>) = picks.map { it.toRow(policy, bare, it.key in picked, known) }

        return when (val listing = listing(inputs, slot, choices)) {
            Listing.Searching -> LoggerBody.Searching
            is Listing.Hint -> LoggerBody.TabHint(listing.tab)
            is Listing.Matches -> if (listing.picks.isEmpty()) {
                LoggerBody.NoResults(listing.query, listing.offline)
            } else {
                LoggerBody.Results(
                    rows = rows(listing.picks),
                    offline = listing.offline,
                    openFoodFacts = choices.searchOptions && listing.picks.any { it.food?.isOpenFoodFacts == true },
                )
            }
            is Listing.Browse -> if (listing.usual.isEmpty() && listing.recent.isEmpty()) {
                LoggerBody.FirstRun
            } else {
                LoggerBody.Browse(
                    slot = slot,
                    usual = rows(listing.usual),
                    recent = rows(listing.recent),
                    usualAround = if (slot.isMeal) null else MealSlotPicker.aroundLabelTime(inputs.now),
                )
            }
            is Listing.Listed -> when {
                listing.picks.isNotEmpty() -> LoggerBody.Listed(rows(listing.picks))
                listing.tab == SearchTab.All -> LoggerBody.FirstRun
                else -> LoggerBody.TabEmpty(listing.tab)
            }
        }
    }

    /** What the body lists, before it becomes rows. [picks] and [body] both read it, so a tapped row is always found. */
    private sealed interface Listing {
        data object Searching : Listing
        data class Browse(val usual: List<FoodPick>, val recent: List<FoodPick>) : Listing
        data class Matches(val query: String, val picks: List<FoodPick>, val offline: Boolean) : Listing

        /** The person's own foods before typing: a Favorites or Custom tab, or My foods on All. */
        data class Listed(val tab: SearchTab, val picks: List<FoodPick>) : Listing
        data class Hint(val tab: SearchTab) : Listing
    }

    /** The choices as they apply: with the tabs turned off in the sheet, the tab is All whatever was picked. */
    private fun effective(inputs: LoggerInputs, choices: LoggerChoices): LoggerChoices =
        if (inputs.settings.searchTabs) choices else choices.copy(tab = SearchTab.All)

    private fun listing(inputs: LoggerInputs, slot: MealSlot, given: LoggerChoices): Listing {
        val query = given.query.trim()
        if (!given.searchOptions) {
            // Search as 0.1 shipped it.
            if (query.isEmpty()) return browse(inputs, slot)
            return catalogMatches(query, given.search, FoodSearchFilter.None, mine = customPicks(inputs, query))
        }
        val choices = effective(inputs, given)
        val sort = inputs.settings.searchSort
        val recency = inputs.history.mapNotNull { it.foodRef()?.key }.withIndex().associate { (i, key) -> key to i }
        val ordered = { picks: List<FoodPick> -> sorted(picks, sort, recency) }
        val filter = searchFilterFor(inputs.settings, choices)
        return when (choices.tab) {
            SearchTab.Favorites -> own(SearchTab.Favorites, query, ordered(inputs.favorites.mapNotNull { it.toPick(PickSource.Favorite) }))
            SearchTab.Custom -> own(SearchTab.Custom, query, ordered(inputs.customFoods.map { it.toPick() }))
            SearchTab.Common, SearchTab.Restaurant, SearchTab.Beverages ->
                if (query.isEmpty()) Listing.Hint(choices.tab) else catalogMatches(query, choices.search, filter, emptyList(), ordered)
            SearchTab.All -> when {
                filter == null -> {
                    // My foods: the person's own and what they have logged, no server call.
                    val mine = (inputs.customFoods.map { it.toPick() } + history(inputs)).distinctBy { it.key }
                    own(SearchTab.All, query, ordered(mine))
                }
                query.isEmpty() && sort == SearchSort.MostRecent -> Listing.Browse(usual = emptyList(), recent = history(inputs))
                query.isEmpty() -> browse(inputs, slot)
                else -> {
                    // The person's own foods belong among All's results; USDA or Open Food Facts means just that.
                    val everything = filter.source == null
                    val logged = if (everything && sort == SearchSort.MostRecent) history(inputs).filter { it.matches(query) } else emptyList()
                    val mine = if (everything) logged + customPicks(inputs, query) else emptyList()
                    catalogMatches(query, choices.search, filter, mine, ordered)
                }
            }
        }
    }

    private fun own(tab: SearchTab, query: String, picks: List<FoodPick>): Listing =
        if (query.isEmpty()) Listing.Listed(tab, picks) else Listing.Matches(query, picks.filter { it.matches(query) }, offline = false)

    /**
     * [mine] first, then the catalog's answer, deduplicated by key and put in
     * order by [order]. Searching while the catalog has not answered this
     * query with this filter yet.
     */
    private fun catalogMatches(
        query: String,
        search: SearchOutcome?,
        filter: FoodSearchFilter?,
        mine: List<FoodPick>,
        order: (List<FoodPick>) -> List<FoodPick> = { it },
    ): Listing {
        if (search == null || search.query != query || search.filter != filter) return Listing.Searching
        val picks = (mine + search.foods.map { it.toPick() }).distinctBy { it.key }
        return Listing.Matches(query, order(picks), search.offline)
    }

    /** Best match keeps the order things came in; Most recent puts what was logged first, newest first. */
    private fun sorted(picks: List<FoodPick>, sort: SearchSort, recency: Map<String, Int>): List<FoodPick> = when (sort) {
        SearchSort.BestMatch -> picks
        SearchSort.MostRecent -> picks.sortedBy { recency[it.key] ?: Int.MAX_VALUE }
        SearchSort.AToZ -> picks.sortedBy { it.draft.name.lowercase() }
    }

    private fun browse(inputs: LoggerInputs, slot: MealSlot): Listing {
        // Uncategorized has no usual of its own: what this person logs around now stands in.
        val usualItems = if (slot.isMeal) {
            inputs.usualBySlot[slot].orEmpty()
        } else {
            MealSlotPicker.usualAround(inputs.recentDays, inputs.now, inputs.timeZone, limit = USUAL_AROUND_LIMIT)
        }
        val usual = usualItems.mapNotNull { it.toPick(PickSource.Usual) }
        val usualKeys = usual.mapTo(HashSet()) { it.key }
        // A food already under "Usual at dinner" is not repeated under Recent.
        val recent = inputs.recent.mapNotNull { it.toPick(PickSource.Recent) }.filterNot { it.key in usualKeys }
        return Listing.Browse(usual = usual, recent = recent)
    }

    private fun history(inputs: LoggerInputs): List<FoodPick> = inputs.history.mapNotNull { it.toPick(PickSource.Recent) }

    /** The user's own foods whose name or brand matches; they go before the catalog's. */
    private fun customPicks(inputs: LoggerInputs, query: String): List<FoodPick> =
        inputs.customFoods.map { it.toPick() }.filter { it.matches(query) }

    private fun FoodPick.matches(query: String): Boolean =
        draft.name.contains(query, ignoreCase = true) || subtitle?.contains(query, ignoreCase = true) == true

    private fun optionsBar(inputs: LoggerInputs, choices: LoggerChoices): SearchOptionsBar {
        val settings = inputs.settings
        return SearchOptionsBar(
            tabs = if (settings.searchTabs) SearchTab.entries else null,
            tab = effective(inputs, choices).tab,
            active = settings.searchSort != SearchSort.BestMatch ||
                settings.effectiveDatabase(choices.openFoodFacts) != SearchDatabase.All,
        )
    }

    private fun LogItem.toPick(source: PickSource): FoodPick? {
        val ref = foodRef() ?: return null
        return FoodPick(key = ref.key, ref = ref, draft = againDraft().copy(method = source.methodFor(ref)), source = source)
    }

    private fun Food.toPick(): FoodPick {
        val ref = FoodRef.Catalog(id)
        val draft = toLogDraft(FoodAmount.Servings(1.0), method = PickSource.Search.methodFor(ref))
        return FoodPick(key = ref.key, ref = ref, draft = draft, source = PickSource.Search, food = this, subtitle = brand)
    }

    private fun CustomFood.toPick(): FoodPick {
        val ref = FoodRef.Custom(id)
        return FoodPick(key = ref.key, ref = ref, draft = draftFor(initialQuantity), source = PickSource.Search, subtitle = brand)
    }

    /** [known] is null with the search options off, which draws no badges at all. */
    private fun FoodPick.toRow(policy: VisibilityPolicy, bare: FormatContext, picked: Boolean, known: Map<String, Food>?): FoodRowUi {
        val catalogFood = if (known != null && ref is FoodRef.Catalog) food ?: known[ref.id] else null
        return FoodRowUi(
            key = key,
            ref = ref,
            source = source,
            name = draft.name,
            shortName = shortName(draft.name),
            subtitle = subtitle,
            amount = draft.amountLabel,
            macros = if (draft.hasMacros) {
                policy.macroLine(draft.amounts(), Surface.Logger, bare, MacroLineStyle.Compact)
            } else {
                emptyList()
            },
            calories = policy.resolve(Metric.Calories, draft.kcal, Surface.Logger, bare),
            picked = picked,
            badge = when {
                known == null -> null
                ref is FoodRef.Custom -> SourceBadge.YourFood
                else -> catalogFood?.let { SourceBadge.of(it.source) }
            },
            verified = catalogFood?.verified == true,
            kind = FoodKind.of(draft.name),
        )
    }

    private const val USUAL_AROUND_LIMIT = 3

    private fun basket(inputs: LoggerInputs, policy: VisibilityPolicy, slot: MealSlot, context: FormatContext): BasketFooter? {
        val items = inputs.basket.ifEmpty { return null }
        return BasketFooter(
            count = items.size,
            names = items.joinToString(", ") { shortName(it.draft.name) },
            calories = policy.resolve(Metric.Calories, items.map { it.draft }.totalKcal(), Surface.Logger, context),
            slot = slot,
        )
    }
}
