package com.opson.features.trends

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * Nutrients detail for one day, or the daily average over the Trends week.
 *
 * @param dayIso the day as `LocalDate.toString()`, the day Today was showing.
 * Week average ignores it and uses the Trends week window.
 * @param fromTrends the back link says "Trends" instead of the day.
 *
 * [range] is an enum arg, so the registration passes a typeMap for it.
 */
@Serializable
data class NutrientsRoute(
    val dayIso: String,
    val range: NutrientsRange = NutrientsRange.Day,
    val fromTrends: Boolean = false,
) : Route()

@Serializable
enum class NutrientsRange { Day, WeekAverage }

/**
 * Which foods gave one nutrient (a bottom sheet over Nutrients or Today): the foods ranked, with
 * how much each gave where the mode shows amounts. [nutrient] is `Nutrient.key` (`iron`,
 * `protein`); [dayIso] and [range] are as on [NutrientsRoute]. The sheet itself checks the mode,
 * so a route to it opened in a mode that hides the nutrient shows nothing of it.
 */
@Serializable
data class NutrientFoodsRoute(
    val dayIso: String,
    val nutrient: String,
    val range: NutrientsRange = NutrientsRange.Day,
) : Route()
