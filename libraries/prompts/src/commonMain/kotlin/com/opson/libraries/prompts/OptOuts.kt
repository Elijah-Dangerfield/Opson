@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.prompts

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Something on Today a person may simply not want (owner feedback, 2026-10-30): each has its own
 * switch in More (Reminders and celebrations for celebrations, Today screen for the rest), and
 * offers "I'd rather not see these" where it shows, on its first [OptOuts.OFFERED_SHOWINGS]
 * showings. [wire] keys the count on the phone; never rename one.
 */
enum class OptOut(val wire: String) {
    /** A milestone's full-screen celebration. Turning it off turns off the streak and Your progress too. */
    Celebrations("celebrations"),

    /** The streak in Today's header. Counted each time Your progress opens from it, where the offer is. */
    Streak("streak"),

    /** "Your week is ready" on Today, once per week or month. */
    RecapBanner("recap_banner"),

    /** First-run tips. */
    Tips("tips"),

    /** Prompts that offer something optional ([PromptCandidate.optional]). */
    Suggestions("suggestions"),
}

/**
 * The one count of how often each [OptOut] has shown, and the one way to turn one off. A
 * surface calls [showing] as it draws, once per real showing (a celebration, a tip, a week's
 * banner), and offers the way out when it says so; the switches themselves are settings, so
 * More turns them back on.
 *
 * The offer goes after a few showings rather than the first: someone who isn't sure on day one
 * often is by the third time, and five is enough to have seen it without it nagging.
 *
 * Hidden from the iOS header: Swift never uses it.
 */
@HiddenFromObjC
interface OptOuts {
    /** Counts one showing of [optOut] and says whether this one offers "I'd rather not see these". */
    suspend fun showing(optOut: OptOut): Boolean

    /** Whether the showing already counted still offers the way out, without counting another. */
    suspend fun offered(optOut: OptOut): Boolean

    /** Turns [optOut] off in settings, synced like every other. More turns it back on. */
    suspend fun turnOff(optOut: OptOut)

    companion object {
        /** The showings that offer the way out. After these it is only in settings. */
        const val OFFERED_SHOWINGS = 5

        /** For tests, previews, and a default where a constructor must not grow a required argument. */
        val None: OptOuts = object : OptOuts {
            override suspend fun showing(optOut: OptOut): Boolean = false

            override suspend fun offered(optOut: OptOut): Boolean = false

            override suspend fun turnOff(optOut: OptOut) = Unit
        }
    }
}
