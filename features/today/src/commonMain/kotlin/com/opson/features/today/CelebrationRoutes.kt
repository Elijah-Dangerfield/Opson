package com.opson.features.today

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * A milestone's celebration (`release.celebrations`), full screen: [milestone] is a
 * `Milestone.wire`, kept a string so the route needs no type map. [offerTurnOff] is the very
 * first celebration, which offers to turn them off. A `screen<>`; Today registers it, and only the
 * prompt host opens it, so it never stacks on another prompt.
 */
@Serializable
data class CelebrationRoute(val milestone: String, val offerTurnOff: Boolean = false) : Route()

/**
 * Your progress: the run, the longest run, the milestones reached and the days showed up this
 * month. A `bottomSheet<>`; Today registers it and opens it from the streak in its header.
 */
@Serializable
data class ProgressRoute(val fromToday: Boolean = true) : Route()
