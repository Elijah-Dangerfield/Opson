@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.prompts

import androidx.compose.runtime.Composable
import com.opson.libraries.navigation.Router
import org.jetbrains.compose.resources.StringResource
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Drawn once, at the app root beside the nav host. While [onToday] (Today is the top of the back
 * stack, so nothing is open over it) and the app is in front, it asks the processor for a prompt
 * after a short settle, and again after each [PromptEvent]; a dialog or a sheet opens as its own
 * destination, so it can never stack on another sheet. Draws nothing itself.
 */
@HiddenFromObjC
interface PromptHost {
    @Composable
    fun Host(onToday: Boolean, router: Router)
}

/** What the QA menu sees and resets. Acts on the signed-in account on this phone only. */
@HiddenFromObjC
interface PromptDebug {
    /** Every candidate, highest priority first, as it stands for Today opening right now. */
    suspend fun inspect(): List<PromptInspection>

    /** Forgets what [id] showed, or every prompt when null, and ends the waits between prompts. */
    suspend fun reset(id: String? = null)

    /** Shows [id] the next time Today settles, past every rule and its own [PromptCandidate.isEligible]. */
    fun showNext(id: String)
}

@HiddenFromObjC
data class PromptInspection(
    val id: String,
    val title: StringResource,
    val presentation: PromptPresentation,
    val priority: Int,
    /** The first global or per-prompt rule holding it back on Today opening, or null when none is. */
    val blockedBy: PromptBlock?,
    /** Its own answer, asked whatever [blockedBy] says. */
    val eligible: Boolean,
    val shows: Int,
    val dismissals: Int,
    val accepted: Boolean,
)

/** Why a prompt would not show, in the order the processor checks. */
enum class PromptBlock {
    NotSignedIn,
    NotOnboarded,
    Accepted,
    Dismissed,
    ShownEnough,
    ShownRecently,
    NotThisMoment,
    NumbersHard,

    /** An optional suggestion, and they turned suggestions off ([PromptCandidate.optional]). */
    TurnedOff,
    ShownThisSession,
    SettlingIn,
    PromptedRecently,
}
