@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.today.impl.celebration

import com.opson.features.today.CelebrationRoute
import com.opson.libraries.navigation.Route
import com.opson.libraries.prompts.PromptAccept
import com.opson.libraries.prompts.PromptCandidate
import com.opson.libraries.prompts.PromptContent
import com.opson.libraries.prompts.PromptEvent
import com.opson.libraries.prompts.PromptFrequency
import com.opson.libraries.prompts.PromptPresentation
import com.opson.libraries.prompts.PromptPriority
import com.opson.libraries.ui.components.icon.AppIcons
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.celebrate_can_do_this
import opson.libraries.resources.generated.resources.celebrate_prompt_body
import opson.libraries.resources.generated.resources.celebrate_prompt_title
import opson.libraries.resources.generated.resources.celebrate_rather_not
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration

/**
 * A milestone's celebration, full screen, through the prompt processor like every other unasked
 * thing over Today: so it never lands on top of another prompt, takes the session's one showing,
 * and waits for a quieter session when one already showed. Wrapping up the day is a moment
 * ([PromptEvent.DayClosed]), so a milestone reached that way plays as Today settles right after;
 * one reached by logging plays the next time Today settles, from a few days in.
 *
 * Its own counts are no limit: each milestone celebrates once ([CelebrationStore]) and only while
 * it is fresh ([com.opson.libraries.diary.ShowingUp.FRESH_DAYS]). Offered after numbers have been
 * hard too, in the gentle style, which has no counts at all.
 */
@HiddenFromObjC
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class CelebrationPrompt(
    private val source: CelebrationsSource,
    private val store: CelebrationStore,
) : PromptCandidate {
    override val id = ID
    override val priority = PromptPriority.CELEBRATION
    override val presentation = PromptPresentation.Screen
    override val content = CONTENT
    override val frequency = PromptFrequency(maxShows = Int.MAX_VALUE, maxDismissals = Int.MAX_VALUE, minGap = Duration.ZERO)
    override val events = setOf(PromptEvent.DayClosed)
    override val offerWhenNumbersHard = true

    override suspend fun isEligible(): Boolean = source.pending() != null

    override suspend fun accept(): PromptAccept = PromptAccept.Done

    /** Writes the milestone down as shown before it is drawn, so a crash costs a celebration rather than repeating one. */
    override suspend fun screen(): Route? {
        val milestone = source.pending() ?: return null
        val offerTurnOff = !store.offeredTurnOff()
        store.shown(milestone)
        return CelebrationRoute(milestone.wire, offerTurnOff = offerTurnOff)
    }

    companion object {
        /** Never rename: it keys the prompt counts and the `prompt` attribute. */
        const val ID = "celebration"

        val CONTENT = PromptContent(
            icon = AppIcons.AutoAwesome,
            title = Res.string.celebrate_prompt_title,
            body = Res.string.celebrate_prompt_body,
            accept = Res.string.celebrate_can_do_this,
            dismiss = Res.string.celebrate_rather_not,
        )
    }
}
