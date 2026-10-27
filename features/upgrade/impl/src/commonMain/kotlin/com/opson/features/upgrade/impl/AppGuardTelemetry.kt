package com.opson.features.upgrade.impl

import com.opson.features.upgrade.AppGuardState
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * The guard's app events (`docs/practices/app-events.md`). They say which state someone saw and
 * what they did about it, never the operator's message.
 */
interface AppGuardTelemetry {
    fun shown(state: AppGuardState)

    fun storeOpened(from: AppGuardState)

    fun updateDismissed()
}

@ContributesBinding(AppScope::class)
@Inject
class AppEventsAppGuardTelemetry : AppGuardTelemetry {
    private val logger = KLog.withTag("AppGuard")

    override fun shown(state: AppGuardState) {
        val wire = state.wire ?: return
        logger.logEvent("guard.shown", "state" to wire)
    }

    override fun storeOpened(from: AppGuardState) {
        logger.logEvent("guard.store_opened", "from" to from.wire)
    }

    override fun updateDismissed() {
        logger.logEvent("guard.update_dismissed")
    }
}

/** The state as the events spell it; null for [AppGuardState.Normal], which is not an event. */
internal val AppGuardState.wire: String?
    get() = when (this) {
        AppGuardState.Normal -> null
        is AppGuardState.MaintenanceBanner -> if (loggingContinues) "maintenance_offline" else "maintenance_banner"
        is AppGuardState.MaintenanceBlocking -> "maintenance_blocking"
        AppGuardState.UpgradeRequired -> "upgrade_required"
        AppGuardState.UpdateAvailable -> "update_available"
    }
