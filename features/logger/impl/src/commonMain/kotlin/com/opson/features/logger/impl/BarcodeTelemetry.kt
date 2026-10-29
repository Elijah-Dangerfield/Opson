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

    /** [typed] when the code was typed by hand rather than read by the camera. */
    fun hit(source: FoodSource, slow: Boolean, typed: Boolean)

    fun miss(typed: Boolean)

    /** The code was in [count] databases whose numbers differ, so "Pick a match" listed them. */
    fun matchesShown(count: Int, typed: Boolean)

    /** A row of "Pick a match" was opened: [position] 0 is the suggested one, of [of] rows. */
    fun matchPicked(position: Int, of: Int)

    /** Allowed, but the camera could not run. */
    fun cameraUnavailable()

    fun permissionDenied(canAskAgain: Boolean)
}

@ContributesBinding(AppScope::class)
@Inject
class AppEventsBarcodeTelemetry : BarcodeTelemetry {
    private val logger = KLog.withTag("Barcode")

    override fun scanStarted(asked: Boolean) {
        logger.logEvent("barcode.scan_started", "permission" to if (asked) "asked" else "granted")
    }

    override fun hit(source: FoodSource, slow: Boolean, typed: Boolean) {
        logger.logEvent("barcode.hit", "source" to source.wire, "slow" to slow, "typed" to typed)
    }

    override fun miss(typed: Boolean) {
        logger.logEvent("barcode.miss", "typed" to typed)
    }

    override fun matchesShown(count: Int, typed: Boolean) {
        logger.logEvent("barcode.matches_shown", "count" to count, "typed" to typed)
    }

    override fun matchPicked(position: Int, of: Int) {
        logger.logEvent("barcode.match_picked", "position" to position, "of" to of, "suggested" to (position == 0))
    }

    override fun cameraUnavailable() {
        logger.logEvent("barcode.camera_unavailable")
    }

    override fun permissionDenied(canAskAgain: Boolean) {
        logger.logEvent("barcode.permission_denied", "can_ask_again" to canAskAgain)
    }
}
