package com.opson.libraries.navigation

import kotlinx.serialization.Serializable

/**
 * Nothing reads [trackingKey] any more: screen-visit counting was removed because no one
 * read the counts.
 */
// TODO(review): delete once FeedbackRoute (features/home) extends Route directly
// (docs/review/findings/core-libraries.md, deferred cross-slice).
@Serializable
open class TrackableRoute(
    val trackingKey: String,
) : Route()
