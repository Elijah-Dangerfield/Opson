package com.opson.libraries.widgets

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent

/**
 * The widget app events (`docs/practices/app-events.md`, Widgets). Never a number, a meal name or
 * any text: the snapshot is health data.
 */
object WidgetEvents {
    private val logger = KLog.withTag("Widgets")

    /** A widget's + opened the Logger. [nextMeal] is whether a meal was still to come. */
    fun logTapped(nextMeal: Boolean) {
        logger.logEvent("widget.log_tapped", "next_meal" to nextMeal)
    }

    /** Any other widget tap opened the app. [target] is `today` or `water`. */
    fun opened(target: String) {
        logger.logEvent("widget.opened", "target" to target)
    }

    /** The water widget's add wrote a glass, or [added] false when it couldn't. */
    fun waterAdded(added: Boolean) {
        logger.logEvent("widget.water_added", "added" to added)
    }
}
