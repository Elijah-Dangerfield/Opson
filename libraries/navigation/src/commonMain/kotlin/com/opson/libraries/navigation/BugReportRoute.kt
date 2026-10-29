package com.opson.libraries.navigation

import kotlinx.serialization.Serializable

/**
 * @property shakeScreenshot the report was opened from the shake dialog, so it takes the
 * screenshot the shake held (`ScreenCapture.takeHeld`). False everywhere else, so a report opened
 * from More later never picks up a stale picture.
 */
@Serializable
data class BugReportRoute(
	val logId: String? = null,
	val errorCode: Int? = null,
	val contextMessage: String? = null,
	val shakeScreenshot: Boolean = false,
) : Route(), NavigableWhileBlocked
