package com.opson.features.logger.impl

import com.opson.features.logger.impl.FoodPicks.matches
import com.opson.features.logger.impl.FoodPicks.toPick
import com.opson.features.logger.impl.FoodPicks.toRow
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealSlotPicker
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.SearchDatabase
import com.opson.libraries.diary.SearchSort
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.foodRef
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy

/**
 * Search's state as a pure function of what it reads and what the person has done, so previews
 * and the Blind leak test run the same mapping as the app. Every number goes through
 * [VisibilityPolicy] on [Surface.Logger].
 */
object FoodSearchStateBuilder {

    fun build(inputs: SearchInputs, policy: VisibilityPolicy, choices: SearchChoices): FoodSearchState {
        val groupsOn = inputs.day.mealGroupsOn(inputs.settings)
        val slot = effectiveSlot(inputs, choices.slot)
        val picking = choices.pickingForMeal
        // A closed day takes nothing; picking for a meal writes nothing to it, so it goes on.
        val closed = inputs.day.closedByUser && !picking
        return FoodSearchState(
            loading = false,
            slot = slot,
            slots = if (groupsOn) MealSlot.entries else listOf(MealSlot.Uncategorized),
            query = choices.query,
            focusSearch = choices.focusSearch,
            scope = choices.scope,
            // A meal being put together is made of foods, not of other meals.
            scopes = if (picking) listOf(SearchScope.All, SearchScope.YourFoods) else SearchScope.entries,
            // A scan, a photo and Describe all log into this meal, so a meal being put together has none.
            canScan = choices.scanAvailable && !picking && !closed,
            canPhotograph = choices.photoAvailable && !picking && !closed,
            canDescribe = !picking && !closed,
            body = if (closed) SearchBody.DayClosed(isToday = inputs.day.date == inputs.today) else body(inputs, policy, slot, choices),
            pickingForMeal = picking,
            picks = if (picking && choices.picked.isNotEmpty()) {
                PicksFooter(count = choices.picked.size, names = choices.picked.joinToString(", ") { shortName(it.draft.name) })
            } else {
                null
            },
            options = if (choices.searchOptions && choices.scope == SearchScope.All) optionsBar(inputs, choices) else null,
        )
    }

    /** The slot a log lands in: [slot], or Uncategorized when the day has meal groups off. */
    fun effectiveSlot(inputs: SearchInputs, slot: MealSlot): MealSlot =
        if (inputs.day.mealGroupsOn(inputs.settings)) slot else MealSlot.Uncategorized

    /**
     * Every food the body can show for these choices, in order. The view model finds a tapped
     * row's draft here, so the state never has to carry one.
     */
    fun picks(inputs: SearchInputs, choices: SearchChoices): List<FoodPick> =
        when (val listing = listing(inputs, effectiveSlot(inputs, choices.slot), choices)) {
            Listing.Searching, is Listing.Hint, is Listing.Meals -> emptyList()
            is Listing.Browse -> listing.usual + listing.recent
            is Listing.Matches -> listing.picks
            is Listing.Listed -> listing.picks
        }

    /** The saved meals the Saved meals chip lists for [query]: by name, or by a food in them. */
    fun savedMeals(inputs: SearchInputs, query: String): List<SavedMeal> {
        val words = query.trim()
        if (words.isEmpty()) return inputs.savedMeals
        return inputs.savedMeals.filter { meal ->
            meal.name.contains(words, ignoreCase = true) || meal.items.any { it.name.contains(words, ignoreCase = true) }
        }
    }

    /**
     * What the catalog is asked for, or null when what is showing needs no server (Your foods,
     * Saved meals, and with the options on Favorites, Custom and My foods). With the options off
     * All always asks [FoodSearchFilter.None], the call 0.1 makes.
     */
    fun catalogFilter(inputs: SearchInputs?, choices: SearchChoices): FoodSearchFilter? = when {
        choices.scope != SearchScope.All -> null
        !choices.searchOptions || inputs == null -> FoodSearchFilter.None
        else -> searchFilterFor(inputs.settings, effective(inputs, choices))
    }

    private fun body(inputs: SearchInputs, policy: VisibilityPolicy, slot: MealSlot, choices: SearchChoices): SearchBody {
        val bare = inputs.day.formatContext(inputs.today, bare = true)
        val added = if (choices.pickingForMeal) {
            choices.picked.mapTo(HashSet()) { it.key }
        } else {
            FoodPicks.addedSince(inputs.day, slot, choices.seen).keys
        }
        val known = if (choices.searchOptions) inputs.known else null
        fun rows(picks: List<FoodPick>) = picks.map { it.toRow(policy, bare, it.key in added, known) }

        return when (val listing = listing(inputs, slot, choices)) {
            Listing.Searching -> SearchBody.Searching
            is Listing.Hint -> SearchBody.TabHint(listing.tab)
            is Listing.Meals -> SearchBody.SavedMeals(
                listing.meals.map { meal ->
                    SavedMealRowUi(
                        id = meal.id,
                        name = meal.name,
                        foods = meal.items.joinToString(", ") { shortName(it.name) },
                        calories = policy.resolve(Metric.Calories, meal.totals.kcal.value, Surface.Logger, bare),
                        added = savedMealAdded(meal, added),
                    )
                },
            )
            is Listing.Matches -> if (listing.picks.isEmpty()) {
                SearchBody.NoResults(listing.query, listing.offline)
            } else {
                SearchBody.Results(
                    rows = rows(listing.picks),
                    offline = listing.offline,
                    openFoodFacts = choices.searchOptions && listing.picks.any { it.food?.isOpenFoodFacts == true },
                    nevo = listing.picks.any { it.food?.source == FoodSource.Nevo },
                )
            }
            is Listing.Browse -> if (listing.usual.isEmpty() && listing.recent.isEmpty()) {
                SearchBody.FirstRun
            } else {
                SearchBody.Browse(
                    slot = slot,
                    usual = rows(listing.usual),
                    recent = rows(listing.recent),
                    usualAround = if (slot.isMeal) null else MealSlotPicker.aroundLabelTime(inputs.now),
                )
            }
            is Listing.Listed -> when {
                listing.picks.isNotEmpty() -> SearchBody.Listed(rows(listing.picks))
                listing.tab == SearchTab.All -> if (choices.scope == SearchScope.YourFoods) SearchBody.NoFoodsYet else SearchBody.FirstRun
                else -> SearchBody.TabEmpty(listing.tab)
            }
        }
    }

    /** A saved meal reads as added once every food in it that has a food behind it went in since search opened. */
    private fun savedMealAdded(meal: SavedMeal, added: Set<String>): Boolean {
        val keys = meal.items.mapNotNull { it.foodRef()?.key }
        return keys.isNotEmpty() && keys.all { it in added }
    }

    /** What the body lists, before it becomes rows. [picks] and [body] both read it, so a tapped row is always found. */
    private sealed interface Listing {
        data object Searching : Listing
        data class Browse(val usual: List<FoodPick>, val recent: List<FoodPick>) : Listing
        data class Matches(val query: String, val picks: List<FoodPick>, val offline: Boolean) : Listing

        /** The person's own foods before typing: Your foods, a Favorites or Custom tab, or My foods on All. */
        data class Listed(val tab: SearchTab, val picks: List<FoodPick>) : Listing
        data class Hint(val tab: SearchTab) : Listing
        data class Meals(val meals: List<SavedMeal>) : Listing
    }

    /** The choices as they apply: with the tabs turned off in the options, the tab is All whatever was picked. */
    private fun effective(inputs: SearchInputs, choices: SearchChoices): SearchChoices =
        if (inputs.settings.searchTabs) choices else choices.copy(tab = SearchTab.All)

    private fun listing(inputs: SearchInputs, slot: MealSlot, given: SearchChoices): Listing {
        val query = given.query.trim()
        val before = LoggedBefore.of(inputs.history, inputs.frequent)
        return when (given.scope) {
            SearchScope.SavedMeals -> Listing.Meals(savedMeals(inputs, query))
            SearchScope.YourFoods -> own(SearchTab.All, query, yourFoods(inputs, before))
            SearchScope.All -> if (given.searchOptions) {
                withOptions(inputs, slot, given, query, before)
            } else if (query.isEmpty()) {
                browse(inputs, slot, before)
            } else {
                catalogMatches(query, given.search, FoodSearchFilter.None, mine = customPicks(inputs, query, before), before = before)
            }
        }
    }

    /** All with `release.searchOptions` on: the sort, the database and the category tabs apply. */
    private fun withOptions(inputs: SearchInputs, slot: MealSlot, given: SearchChoices, query: String, before: LoggedBefore): Listing {
        val choices = effective(inputs, given)
        val sort = inputs.settings.searchSort
        val recency = inputs.history.mapNotNull { it.foodRef()?.key }.withIndex().associate { (i, key) -> key to i }
        val ordered = { picks: List<FoodPick> -> sorted(picks, sort, recency) }
        val filter = searchFilterFor(inputs.settings, choices)
        return when (choices.tab) {
            SearchTab.Favorites -> own(SearchTab.Favorites, query, ordered(favoritePicks(inputs, before)))
            SearchTab.Custom -> own(SearchTab.Custom, query, ordered(inputs.customFoods.map { it.toPick(before) }))
            SearchTab.Common, SearchTab.Restaurant, SearchTab.Beverages ->
                if (query.isEmpty()) Listing.Hint(choices.tab) else catalogMatches(query, choices.search, filter, emptyList(), before, ordered)
            SearchTab.All -> when {
                // My foods: the person's own and what they have logged, no server call.
                filter == null -> own(SearchTab.All, query, ordered(yourFoods(inputs, before)))
                query.isEmpty() && sort == SearchSort.MostRecent -> Listing.Browse(usual = emptyList(), recent = history(inputs, before))
                query.isEmpty() -> browse(inputs, slot, before)
                else -> {
                    // The person's own foods belong among All's results; USDA or Open Food Facts means just that.
                    val everything = filter.source == null
                    val logged = if (everything && sort == SearchSort.MostRecent) history(inputs, before).filter { it.matches(query) } else emptyList()
                    val mine = if (everything) logged + customPicks(inputs, query, before) else emptyList()
                    catalogMatches(query, choices.search, filter, mine, before, ordered)
                }
            }
        }
    }

    private fun own(tab: SearchTab, query: String, picks: List<FoodPick>): Listing =
        if (query.isEmpty()) Listing.Listed(tab, picks) else Listing.Matches(query, picks.filter { it.matches(query) }, offline = false)

    /**
     * [mine] first, then the catalog's answer, deduplicated by key and put in order by [order].
     * Searching while the catalog has not answered this query with this filter yet.
     */
    @Suppress("LongParameterList")
    private fun catalogMatches(
        query: String,
        search: SearchOutcome?,
        filter: FoodSearchFilter?,
        mine: List<FoodPick>,
        before: LoggedBefore,
        order: (List<FoodPick>) -> List<FoodPick> = { it },
    ): Listing {
        if (search == null || search.query != query || search.filter != filter) return Listing.Searching
        val picks = (mine + search.foods.map { it.toPick(before) }).distinctBy { it.key }
        return Listing.Matches(query, order(picks), search.offline)
    }

    /** Best match keeps the order things came in; Most recent puts what was logged first, newest first. */
    private fun sorted(picks: List<FoodPick>, sort: SearchSort, recency: Map<String, Int>): List<FoodPick> = when (sort) {
        SearchSort.BestMatch -> picks
        SearchSort.MostRecent -> picks.sortedBy { recency[it.key] ?: Int.MAX_VALUE }
        SearchSort.AToZ -> picks.sortedBy { it.draft.name.lowercase() }
    }

    private fun browse(inputs: SearchInputs, slot: MealSlot, before: LoggedBefore): Listing {
        // Uncategorized has no usual of its own: what this person logs around now stands in.
        val usualItems = if (slot.isMeal) {
            inputs.usualBySlot[slot].orEmpty()
        } else {
            MealSlotPicker.usualAround(inputs.recentDays, inputs.now, inputs.timeZone, limit = USUAL_AROUND_LIMIT)
        }
        val usual = usualItems.mapNotNull { it.toPick(PickSource.Usual, before) }
        val usualKeys = usual.mapTo(HashSet()) { it.key }
        // A food already under "Usual at dinner" is not repeated under Recent.
        val recent = inputs.recent.mapNotNull { it.toPick(PickSource.Recent, before) }.filterNot { it.key in usualKeys }
        return Listing.Browse(usual = usual, recent = recent)
    }

    /** Your foods: the person's own, then what they have logged, newest first. */
    private fun yourFoods(inputs: SearchInputs, before: LoggedBefore): List<FoodPick> =
        (inputs.customFoods.map { it.toPick(before) } + history(inputs, before)).distinctBy { it.key }

    /**
     * The foods starred on the food detail, newest star first, then the ones logged most. A star
     * is found wherever the device already has the food: the diary, the catalog cache or the
     * person's own foods; one it cannot find yet waits until it can.
     */
    private fun favoritePicks(inputs: SearchInputs, before: LoggedBefore): List<FoodPick> {
        val logged = (inputs.frequent + inputs.history + inputs.recent).mapNotNull { it.toPick(PickSource.Favorite, before) }.associateBy { it.key }
        val cached = inputs.known.values.map { it.toPick(before).copy(source = PickSource.Favorite) }.associateBy { it.key }
        val own = inputs.customFoods.map { it.toPick(before).copy(source = PickSource.Favorite) }.associateBy { it.key }
        val starred = inputs.settings.favoriteFoods.mapNotNull { key -> logged[key] ?: cached[key] ?: own[key] }
        return (starred + inputs.frequent.mapNotNull { it.toPick(PickSource.Favorite, before) }).distinctBy { it.key }
    }

    private fun history(inputs: SearchInputs, before: LoggedBefore): List<FoodPick> =
        inputs.history.mapNotNull { it.toPick(PickSource.Recent, before) }.distinctBy { it.key }

    /** The person's own foods whose name or brand matches; they go before the catalog's. */
    private fun customPicks(inputs: SearchInputs, query: String, before: LoggedBefore): List<FoodPick> =
        inputs.customFoods.map { it.toPick(before) }.filter { it.matches(query) }

    private fun optionsBar(inputs: SearchInputs, choices: SearchChoices): SearchOptionsBar {
        val settings = inputs.settings
        return SearchOptionsBar(
            tabs = if (settings.searchTabs) SearchTab.entries else null,
            tab = effective(inputs, choices).tab,
            active = settings.searchSort != SearchSort.BestMatch ||
                settings.effectiveDatabase(choices.openFoodFacts) != SearchDatabase.All,
        )
    }

    private const val USUAL_AROUND_LIMIT = 3
}
