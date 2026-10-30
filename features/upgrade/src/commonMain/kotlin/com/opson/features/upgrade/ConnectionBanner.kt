package com.opson.features.upgrade

import kotlinx.coroutines.flow.StateFlow

/**
 * The offline strip at the top of the app, drawn by the guard layer like the maintenance and
 * update strips. It follows `AppState.isOffline` with a short wait before it appears, so a blip
 * while changing networks shows nothing, and says "You're back online" for a moment before it goes.
 */
interface ConnectionBanner {
    val strip: StateFlow<ConnectionStrip>
}

/** What the offline strip says right now. */
sealed interface ConnectionStrip {
    data object Hidden : ConnectionStrip

    /**
     * Offline for longer than the wait. [loggingWorks] is false before onboarding is done, where
     * there is nothing to log yet and signing in needs a connection.
     */
    data class Offline(val loggingWorks: Boolean) : ConnectionStrip

    /** The connection came back after the strip had shown. Goes away by itself. */
    data object BackOnline : ConnectionStrip
}
