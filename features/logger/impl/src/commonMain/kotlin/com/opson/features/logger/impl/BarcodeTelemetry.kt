package com.opson.features.logger.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.foods.FoodSource
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * The scanner's app events (`docs/practices/app-events.md`). Typed so no call
 * site can pass the barcode: what someone scans is what they eat.
 */
interface BarcodeTelemetry {
    /** The viewfinder went live. [asked] when the system prompt was shown first. */
    fun scanStarted(asked: Boolean)

    fun hit(source: FoodSource, slow: Boolean)

    fun miss()

    fun permissionDenied(canAskAgain: Boolean)
}

@ContributesBinding(AppScope::class)
@Inject
class AppEventsBarcodeTelemetry : BarcodeTelemetry {
    private val logger = KLog.withTag("Barcode")

    override fun scanStarted(asked: Boolean) {
        logger.logEvent("barcode.scan_started", "permission" to if (asked) "asked" else "granted")
    }

    override fun hit(source: FoodSource, slow: Boolean) {
        logger.logEvent("barcode.hit", "source" to source.wire, "slow" to slow)
    }

    override fun miss() {
        logger.logEvent("barcode.miss")
    }

    override fun permissionDenied(canAskAgain: Boolean) {
        logger.logEvent("barcode.permission_denied", "can_ask_again" to canAskAgain)
    }
}
