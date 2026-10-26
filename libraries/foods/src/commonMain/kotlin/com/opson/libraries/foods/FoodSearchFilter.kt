package com.opson.libraries.foods

/**
 * What the Logger's search options ask the catalog for: `category`, `source`
 * and `sort` on `/v1/foods/search`. [None] sends none of them, which is the
 * search 0.1 makes.
 */
data class FoodSearchFilter(
    val category: SearchCategory? = null,
    val source: SearchSource? = null,
    val order: SearchOrder = SearchOrder.BestMatch,
) {
    companion object {
        val None = FoodSearchFilter()
    }
}

/** The server's `category` values. [Common] is the generic and label foods (tiers 1 and 2). */
enum class SearchCategory(val wire: String) {
    Restaurant("restaurant"),
    Beverage("beverage"),
    Common("common"),
}

/** The server's `source` values. */
enum class SearchSource(val wire: String) {
    Usda("usda"),
    OpenFoodFacts("off"),
    ;

    companion object {
        fun fromWire(value: String): SearchSource? = entries.firstOrNull { it.wire == value }
    }
}

/** The server's `sort` values. [Name] orders the best matches A to Z. */
enum class SearchOrder(val wire: String) {
    BestMatch("best"),
    Name("name"),
}

/** `GET /v1/foods/search/options`: whether this server can search Open Food Facts. */
data class SearchOptions(val openFoodFacts: Boolean)

/** USDA's generic and Branded sources; the `usda` filter keeps these. */
val FoodSource.isUsda: Boolean
    get() = this == FoodSource.UsdaFoundation || this == FoodSource.UsdaSr || this == FoodSource.UsdaFndds ||
        this == FoodSource.UsdaBranded

/**
 * Whether a food the device already has passes [filter], for search offline.
 * A cached food has no category, so Restaurant and Beverages never pass.
 */
fun Food.passesOffline(filter: FoodSearchFilter): Boolean {
    val category = when (filter.category) {
        null -> true
        // Tiers 1 and 2 are exactly USDA's sources, and a food cached before tiers existed still has its source.
        SearchCategory.Common -> source.isUsda
        SearchCategory.Restaurant, SearchCategory.Beverage -> false
    }
    val source = when (filter.source) {
        null -> true
        SearchSource.Usda -> source.isUsda
        SearchSource.OpenFoodFacts -> isOpenFoodFacts
    }
    return category && source
}
