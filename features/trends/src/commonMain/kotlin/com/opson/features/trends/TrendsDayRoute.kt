package com.opson.features.trends

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * One day from Trends' on-track calendar, as a small sheet: how it went against the plan, what
 * was eaten and burned where the mode shows them, and a way to open the day on Today.
 *
 * @param dayIso the day, `2026-10-22`. A string, so the route needs no type map.
 */
@Serializable
class TrendsDayRoute(val dayIso: String) : Route()
