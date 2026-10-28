package com.opson.features.trends

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * Patterns (design v3, 5.6): when meals happen, how big each is, hunger before
 * them and new foods, over the last four weeks. Opened from the Recap or the
 * Your week card on Trends. A `screen<>`; Trends registers it.
 */
@Serializable
class PatternsRoute : Route()
