package com.opson.features.today

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The check-in (`release.checkIn`) for [dayIso] (`LocalDate.toString()`), today when null:
 * hunger right now, energy, mood, and last night's sleep in the morning. A `bottomSheet<>`; Today
 * registers it, and the + sheet, Close the day and More's Journal card open it.
 */
@Serializable
data class CheckInRoute(val dayIso: String? = null) : Route()
