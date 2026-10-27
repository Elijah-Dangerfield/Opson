package com.opson.features.trends

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/*
 * Progress photos (`progress-photos.md`), behind `release.progressPhotos`.
 * Only the Weight page's row opens the grid, and only the grid opens the rest,
 * so with the flag off none of these is reachable. All `screen<>`; Trends
 * registers them.
 */

/** The month grid. Asks for the phone's lock every time it opens. */
@Serializable
class ProgressPhotosRoute : Route()

/** The in-app camera, front then side. */
@Serializable
class ProgressPhotoCaptureRoute : Route()

/** Two photos side by side or on a slider. `null` ids pick the defaults. */
@Serializable
data class ProgressPhotosCompareRoute(val leftId: String? = null, val rightId: String? = null) : Route()

/** One photo, with delete. */
@Serializable
data class ProgressPhotoRoute(val id: String = "") : Route()
