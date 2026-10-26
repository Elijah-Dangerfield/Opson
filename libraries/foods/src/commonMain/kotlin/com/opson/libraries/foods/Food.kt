package com.opson.libraries.foods

import com.opson.libraries.core.Catching
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where a catalog food came from. Open Food Facts data is ODbL, so the Foods screen credits it. */
@Serializable
enum class FoodSource(val wire: String) {
    @SerialName("usda_foundation")
    UsdaFoundation("usda_foundation"),

    @SerialName("usda_sr")
    UsdaSr("usda_sr"),

    @SerialName("usda_fndds")
    UsdaFndds("usda_fndds"),

    @SerialName("off")
    OpenFoodFacts("off"),

    @SerialName("unknown")
    Unknown("unknown"),
    ;

    companion object {
        fun fromWire(value: String): FoodSource = entries.firstOrNull { it.wire == value } ?: Unknown
    }
}

/** Energy and macros per 100 g. Any of them can be unknown, which is never the same as zero. */
@Serializable
data class NutrientProfile(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
)

/** A household measure: "1 cup" is 158 g of cooked rice. */
@Serializable
data class Portion(val label: String, val grams: Double)

/** A catalog food. [micros] is per 100 g, keyed with the unit in the key (`sodium_mg`). */
@Serializable
data class Food(
    val id: String,
    val source: FoodSource,
    val name: String,
    val per100g: NutrientProfile,
    val brand: String? = null,
    val barcode: String? = null,
    val micros: Map<String, Double> = emptyMap(),
    val portions: List<Portion> = emptyList(),
) {
    val isOpenFoodFacts: Boolean get() = source == FoodSource.OpenFoodFacts
}

enum class Confidence(val wire: String) {
    High("high"),
    Medium("medium"),
    Low("low"),
    ;

    companion object {
        fun fromWire(value: String): Confidence = entries.firstOrNull { it.wire == value } ?: Low
    }
}

/**
 * One phrase from "Describe it" ("2 eggs"), already matched to a food and
 * scaled to the whole amount. Always an estimate. [foodId] and the nutrients
 * are null when nothing in the catalog matched.
 */
data class ParsedItem(
    val text: String,
    val name: String,
    val quantity: Double,
    val amountText: String,
    val confidence: Confidence,
    val foodId: String? = null,
    val unit: String? = null,
    val portionLabel: String? = null,
    val grams: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val micros: Map<String, Double>? = null,
)

data class FoodSearchResult(
    /** The query the results are for, normalized by the server when they came from it. */
    val query: String,
    val foods: List<Food>,
    /** True when the server was out of reach and these are only foods picked before. */
    val fromCache: Boolean,
)

/**
 * The food catalog: USDA FoodData Central plus Open Food Facts, through the
 * server. Foods the user has picked are kept on the device, so Favorites and
 * Recent work offline and search falls back to them when the server cannot be
 * reached.
 *
 * Favorites are device-local: the server has no table for them.
 */
interface FoodCatalog {
    /** Online search; offline, a name match over foods picked before, with `fromCache` set. */
    suspend fun search(query: String, limit: Int = 25): Catching<FoodSearchResult>

    /** One food with portions and micros. Falls back to the cached copy offline. */
    suspend fun detail(id: String): Catching<Food>

    /** The food for a barcode, or null when neither the catalog nor Open Food Facts knows it. */
    suspend fun barcode(code: String): Catching<Food?>

    /** "2 eggs and toast" to items. Online only. */
    suspend fun describe(text: String): Catching<List<ParsedItem>>

    fun observeFavorites(): Flow<List<Food>>

    /** Foods picked most recently first. */
    fun observeRecent(limit: Int = 10): Flow<List<Food>>

    /** Call when the user logs a catalog food: it is cached and goes to the top of Recent. */
    suspend fun markUsed(food: Food)

    suspend fun setFavorite(food: Food, favorite: Boolean)
}
