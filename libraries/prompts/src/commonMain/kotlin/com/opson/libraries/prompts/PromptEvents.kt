package com.opson.libraries.prompts

/**
 * A moment worth following with a prompt. Emitted where it happens ([PromptEvents.emit]); a
 * candidate that lists it in [PromptCandidate.events] may then show as Today next settles, within
 * a few minutes, and skipping the day between prompts. Add a moment here when a prompt needs one;
 * [wire] is what the app events call it.
 */
enum class PromptEvent(val wire: String) {
    /** A bug report or feedback reached Sentry (More › Support, or a shake). */
    ReportSent("report_sent"),

    /** Close the day's Done saved the day as closed. */
    DayClosed("day_closed"),

    /** "Report a problem" on a catalog food reached Opson's reviewers. [ReportSent] fires too. */
    FoodReportSent("food_report_sent"),
}

/** Where a moment is announced. Never throws and never waits: it only notes the moment. */
fun interface PromptEvents {
    fun emit(event: PromptEvent)

    companion object {
        /** For tests and previews, and a default where a constructor must not grow a new required argument. */
        val None: PromptEvents = PromptEvents { }
    }
}
