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
}
