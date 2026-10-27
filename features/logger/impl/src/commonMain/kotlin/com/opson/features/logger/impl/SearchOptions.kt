package com.opson.features.logger.impl

import com.opson.libraries.diary.SearchDatabase
import com.opson.libraries.diary.SearchSort
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.SearchCategory
import com.opson.libraries.foods.SearchOrder
import com.opson.libraries.foods.SearchSource

/**
 * The category tabs under the search bar (`release.searchOptions`). Favorites
 * and Custom are the person's own foods; Common, Restaurant and Beverages ask
 * the catalog for one [category].
 */
enum class SearchTab(val wire: String, val category: SearchCategory?) {
    All("all", null),
    Favorites("favorites", null),
    Custom("custom", null),
    Common("common", SearchCategory.Common),
    Restaurant("restaurant", SearchCategory.Restaurant),
    Beverages("beverages", SearchCategory.Beverage),
}

/** Where a row's food came from, as a short badge on the right. No numbers, so every mode shows it. */
enum class SourceBadge {
    Usda,
    UsdaBranded,
    OpenFoodFacts,
    YourFood,
    ;

    companion object {
        /** Nutritionix and anything unknown get no badge: there's no short honest name for them yet. */
        fun of(source: FoodSource): SourceBadge? = when (source) {
            FoodSource.UsdaFoundation, FoodSource.UsdaSr, FoodSource.UsdaFndds -> Usda
            FoodSource.UsdaBranded -> UsdaBranded
            FoodSource.OpenFoodFacts -> OpenFoodFacts
            FoodSource.Nutritionix, FoodSource.Unknown -> null
        }
    }
}

/**
 * The search options as search draws them. [tabs] is null with the
 * Category tabs setting off. [active] lights the options button when the sort
 * or database is not the default, so a narrowed list never passes for a
 * short one.
 */
data class SearchOptionsBar(val tabs: List<SearchTab>?, val tab: SearchTab, val active: Boolean)

/** The database actually searched: Open Food Facts reads as All once the server stops offering it. */
internal fun Settings.effectiveDatabase(openFoodFacts: Boolean): SearchDatabase =
    if (searchDatabase == SearchDatabase.OpenFoodFacts && !openFoodFacts) SearchDatabase.All else searchDatabase

/**
 * What search asks the catalog for, or null when what is showing needs no
 * server at all (Favorites, Custom, My foods). With the options off it is
 * always [FoodSearchFilter.None], the call 0.1 makes.
 */
internal fun searchFilterFor(settings: Settings, choices: SearchChoices): FoodSearchFilter? {
    if (!choices.searchOptions) return FoodSearchFilter.None
    val order = if (settings.searchSort == SearchSort.AToZ) SearchOrder.Name else SearchOrder.BestMatch
    return when (choices.tab) {
        SearchTab.Favorites, SearchTab.Custom -> null
        SearchTab.Common, SearchTab.Restaurant, SearchTab.Beverages -> FoodSearchFilter(category = choices.tab.category, order = order)
        SearchTab.All -> when (settings.effectiveDatabase(choices.openFoodFacts)) {
            SearchDatabase.All -> FoodSearchFilter(order = order)
            SearchDatabase.Usda -> FoodSearchFilter(source = SearchSource.Usda, order = order)
            SearchDatabase.OpenFoodFacts -> FoodSearchFilter(source = SearchSource.OpenFoodFacts, order = order)
            SearchDatabase.MyFoods -> null
        }
    }
}
