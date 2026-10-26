package com.opson.features.trends

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The Weight screen (`trends.md`), reached from Trends, the logger's Weigh-in
 * chip and Today's Add menu. A `screen<>`; Trends registers it.
 */
@Serializable
class WeightRoute : Route()
