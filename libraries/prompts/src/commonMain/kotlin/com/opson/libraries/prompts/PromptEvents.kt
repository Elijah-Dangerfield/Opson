package com.opson.libraries.prompts

/**
 * A moment worth following with a prompt. Emitted where it happens ([PromptEvents.emit]); a
 * candidate that lists it in [PromptCandidate.events] may then show as Today next settles, within
 * a few minutes, and skipping the day between prompts. Add a moment here when a prompt needs one;
 * [wire] is what the app events call it.
 *
 * [answersPrompt] marks a moment that is someone's own answer to a prompt that just showed (a
 * celebration's "I can do this"). One prompt may follow such a moment in the same session; any
 * other moment waits for the next session once a prompt has shown.
 */
enum class PromptEvent(val wire: String, val answersPrompt: Boolean = false) {
    /** A bug report or feedback reached Sentry (More › Support, or a shake). */
    ReportSent("report_sent"),

    /** Close the day's Done saved the day as closed. */
    DayClosed("day_closed"),

    /** "Report a problem" on a catalog food reached Opson's reviewers. [ReportSent] fires too. */
    FoodReportSent("food_report_sent"),

    /** "I can do this" on a milestone celebration, the celebration prompt's own answer. */
    CelebrationCheered("celebration_cheered", answersPrompt = true),

    /**
     * Today became a day they showed up (a first log, wrap-up or check-in), seen as it happened on
     * this phone rather than arriving later by sync.
     */
    ShowedUp("showed_up"),

    /** A food they sent from a pack was just approved for everyone. */
    FoodApproved("food_approved"),
}

/** Where a moment is announced. Never throws and never waits: it only notes the moment. */
fun interface PromptEvents {
    fun emit(event: PromptEvent)

    companion object {
        /** For tests and previews, and a default where a constructor must not grow a new required argument. */
        val None: PromptEvents = PromptEvents { }
    }
}
