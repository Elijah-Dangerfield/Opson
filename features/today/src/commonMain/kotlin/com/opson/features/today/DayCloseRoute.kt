package com.opson.features.today

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * Day close (`day-close.md`) for [dayIso] (`LocalDate.toString()`): the day's
 * totals or a soft line, the big-day card, a note and Done. A `screen<>`;
 * Today registers it and opens it from the header check and the Day menu.
 */
@Serializable
data class DayCloseRoute(val dayIso: String) : Route()

/**
 * "Today is closed. Reopen it to add more." for [dayIso], with Reopen and Not now: where an add or
 * change that a closed day refused lands, from Today's sheets and the logger alike. A
 * `bottomSheet<>`; Today registers it.
 */
@Serializable
data class DayClosedRoute(val dayIso: String) : Route()
