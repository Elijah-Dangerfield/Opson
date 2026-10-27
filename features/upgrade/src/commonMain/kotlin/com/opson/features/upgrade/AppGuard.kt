package com.opson.features.upgrade

import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.Platform
import kotlinx.coroutines.flow.StateFlow

/**
 * The app guard: maintenance, the version floor and the update offer, for the shell to draw.
 * Cheap at launch: it combines the config stream, which already starts from the cached or
 * shipped config, with the offline signal, and does no I/O of its own until someone asks it to.
 */
interface AppGuard {
    val state: StateFlow<AppGuardState>

    /** Fetch the config now, ignoring the refresh throttle. "Try again" on the maintenance screen. */
    suspend fun retry()

    /** Put the update banner away until the next launch. */
    fun dismissUpdate()

    /** The store was opened from the stop or the banner. For the app event only. */
    fun storeOpened()

    /** The store page that updates this app. */
    val storeUrl: String
}

/** Opson's record in App Store Connect. */
private const val APP_STORE_ID = "6827143148"

/**
 * The store page for this app on [platform]. Debug builds carry a `.debug` suffix on Android that
 * the Play listing does not have.
 */
fun storeUrl(platform: Platform = BuildInfo.platform, applicationId: String = BuildInfo.applicationId): String =
    when (platform) {
        Platform.iOS -> "https://apps.apple.com/app/id$APP_STORE_ID"
        Platform.Android -> "https://play.google.com/store/apps/details?id=${applicationId.removeSuffix(".debug")}"
    }
