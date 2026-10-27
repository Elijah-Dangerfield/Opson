package com.opson.features.upgrade

import com.opson.libraries.config.AppConfigMap

/**
 * What the app shell draws over everything else, worked out from the remote config.
 *
 * Built by [from], a pure function of the config map, the running build and whether the server
 * answers, and recomputed on every config emission, so a change made in the admin console lands
 * on the next config refresh with no restart.
 */
sealed interface AppGuardState {

    /** Nothing to show. */
    data object Normal : AppGuardState

    /**
     * A strip above the app with the operator's [message]; the app works underneath.
     * [loggingContinues] is true when this is a blocking maintenance the phone could not confirm
     * (see [from]), so the strip says that logging still saves on the phone.
     */
    data class MaintenanceBanner(val message: String, val loggingContinues: Boolean) : AppGuardState

    /** A full-screen stop with the operator's [message]. Nothing else can be reached. */
    data class MaintenanceBlocking(val message: String) : AppGuardState

    /** This build is below the supported floor. A full-screen stop that sends people to the store. */
    data object UpgradeRequired : AppGuardState

    /** A newer build is out. A strip that can be put away until the next launch. */
    data object UpdateAvailable : AppGuardState

    companion object {
        /**
         * The guard for [clientVersionCode] under [config].
         *
         * Fails open. A config that never loaded is the shipped fallback, whose defaults are a
         * floor of 1, maintenance off and no newer build, so a phone that has never reached us is
         * never stopped. And a blocking maintenance is only a stop while [serverReachable]: offline,
         * or with the server down, the phone could not tell that maintenance has ended, and
         * nothing it does offline reaches the server anyway, because writes wait in the outbox. So
         * it gets the banner and keeps logging. The version floor is not relaxed offline: it is a
         * fact about this build, not about the server, and the store fixes it.
         *
         * Precedence: the version floor, then maintenance, then the update offer.
         */
        fun from(
            config: AppConfigMap,
            clientVersionCode: Int,
            serverReachable: Boolean,
            updateDismissed: Boolean,
        ): AppGuardState {
            if (clientVersionCode < MinSupportedVersionCode(config).value) return UpgradeRequired

            val message = MaintenanceMessage(config).value
            when (MaintenanceMode(config).value.trim().lowercase()) {
                MaintenanceMode.MODE_BLOCKING -> return if (serverReachable) {
                    MaintenanceBlocking(message)
                } else {
                    MaintenanceBanner(message, loggingContinues = true)
                }
                MaintenanceMode.MODE_BANNER -> return MaintenanceBanner(message, loggingContinues = false)
            }

            val latest = LatestVersionCode(config).value
            return if (!updateDismissed && clientVersionCode < latest) UpdateAvailable else Normal
        }
    }
}

/** True for the states that cover the whole app. */
val AppGuardState.blocksApp: Boolean
    get() = this is AppGuardState.MaintenanceBlocking || this is AppGuardState.UpgradeRequired
