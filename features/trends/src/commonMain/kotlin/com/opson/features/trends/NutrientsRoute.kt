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
