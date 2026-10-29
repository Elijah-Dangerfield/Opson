package com.opson.features.trends

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The Weight screen (`trends.md`), reached from Trends, the logger's Weigh-in
 * tile, Today's weight card and Profile. A `screen<>`; Trends registers it.
 *
 * @param focusInput the weigh-in field takes focus and the keyboard comes up
 *   once the screen is in, for the places that open Weight to log one (the
 *   + sheet's Weigh-in, Profile's Current weight, the exercise sheet). A plain
 *   Boolean, so the route needs no type map.
 */
@Serializable
class WeightRoute(val focusInput: Boolean = false) : Route()
