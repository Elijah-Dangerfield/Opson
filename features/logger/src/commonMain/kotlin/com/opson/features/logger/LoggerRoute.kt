package com.opson.features.logger

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The logger, opened by the center + and from a meal's own +. A bottom sheet at
 * `BottomSheetDefaults.FullHeightFraction` of the screen, not a tab.
 *
 * @param slot the meal slot to log into, as the slot's stable string key. A String rather than
 * an enum so the route needs no typeMap entry (an enum arg has to be registered or the graph
 * fails to build on iOS). `null` lets the logger choose.
 */
@Serializable
data class LoggerRoute(val slot: String? = null) : Route()
