@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.builder.impl

import com.opson.features.builder.BuilderProgramRoute
import com.opson.features.builder.BuildersEnabled
import com.opson.libraries.foods.ApplicationStatus
import com.opson.libraries.foods.BuilderApplication
import com.opson.libraries.foods.BuilderProgram
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.foods.SubmittedFoods
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
import opson.libraries.resources.generated.resources.builder_accepted_prompt_accept
import opson.libraries.resources.generated.resources.builder_accepted_prompt_body
import opson.libraries.resources.generated.resources.builder_accepted_prompt_title
import opson.libraries.resources.generated.resources.builder_declined_prompt_accept
import opson.libraries.resources.generated.resources.builder_declined_prompt_body
import opson.libraries.resources.generated.resources.builder_declined_prompt_title
import opson.libraries.resources.generated.resources.builder_prompt_accept
import opson.libraries.resources.generated.resources.builder_prompt_body
import opson.libraries.resources.generated.resources.builder_prompt_title
import opson.libraries.resources.generated.resources.prompt_not_now
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * The invitation to apply, once, ever (the owner's rule: never seen more than once). Two moments
 * can call it up, and whichever comes first uses it: right after someone reports a problem with
 * a food, and as Today opens once their first food from a pack has been approved.
 */
@HiddenFromObjC
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class JoinBuilderPrompt(
    private val enabled: BuildersEnabled,
    private val program: BuilderProgram,
    private val submitted: SubmittedFoods,
) : PromptCandidate {
    override val id = "join_builder"
    override val priority = PromptPriority.MOMENT
    override val presentation = PromptPresentation.Sheet
    override val content = PromptContent(
        icon = AppIcons.Science,
        title = Res.string.builder_prompt_title,
        body = Res.string.builder_prompt_body,
        accept = Res.string.builder_prompt_accept,
        dismiss = Res.string.prompt_not_now,
    )
    override val events = setOf(PromptEvent.FoodReportSent)
    override val frequency = PromptFrequency(maxShows = 1, maxDismissals = 1)

    /** As Today opens: only once a food they added has been approved. */
    override suspend fun isEligible(): Boolean = isEligible(moment = null)

    override suspend fun isEligible(moment: PromptEvent?): Boolean {
        if (!enabled.value) return false
        val standing = program.standing.value ?: return false
        if (!standing.canApply) return false
        return moment == PromptEvent.FoodReportSent || submitted.reviews.value.values.any { it == ReviewStatus.Approved }
    }

    override suspend fun accept(): PromptAccept = PromptAccept.Open(BuilderProgramRoute(via = BuilderProgramRoute.VIA_PROMPT))
}

/**
 * Opson's answer to an application, told once on Today: accepted ([BuilderAcceptedPrompt]) or
 * declined ([BuilderDecisionPrompt]'s two). Only while the decision is recent and this phone
 * hasn't drawn it on the Builders page yet; the prompt's own record shows it once, and accepting
 * marks it seen so the page doesn't tell it again.
 */
@HiddenFromObjC
abstract class BuilderDecisionPrompt(
    private val enabled: BuildersEnabled,
    private val program: BuilderProgram,
    private val clock: Clock,
    private val decision: ApplicationStatus,
) : PromptCandidate {
    override val frequency = PromptFrequency(maxShows = 1, maxDismissals = 1)

    override suspend fun isEligible(): Boolean = news() != null

    override suspend fun accept(): PromptAccept {
        news()?.let { program.markDecisionSeen(it.id) }
        return PromptAccept.Open(BuilderProgramRoute(via = BuilderProgramRoute.VIA_PROMPT))
    }

    /** The decision to tell, if there is one. */
    private suspend fun news(): BuilderApplication? {
        if (!enabled.value) return null
        val standing = program.standing.value ?: return null
        val application = standing.application?.takeIf { it.status == decision } ?: return null
        val decidedAt = application.decidedAt ?: return null
        if (clock.now() - decidedAt > RECENT) return null
        if (decision == ApplicationStatus.Accepted && !standing.isActive) return null
        return application.takeUnless { program.decisionSeen(it.id) }
    }

    private companion object {
        /** Past this the news is old: someone accepted months ago and opening a new phone needn't hear it. */
        val RECENT = 14.days
    }
}

/** "You're in. Welcome to Builders." with Start, which opens their Builder home. */
@HiddenFromObjC
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class BuilderAcceptedPrompt(
    enabled: BuildersEnabled,
    program: BuilderProgram,
    clock: Clock,
) : BuilderDecisionPrompt(enabled, program, clock, ApplicationStatus.Accepted) {
    override val id = "builder_accepted"
    override val priority = PromptPriority.MOMENT
    override val presentation = PromptPresentation.Sheet
    override val content = PromptContent(
        icon = AppIcons.AutoAwesome,
        title = Res.string.builder_accepted_prompt_title,
        body = Res.string.builder_accepted_prompt_body,
        accept = Res.string.builder_accepted_prompt_accept,
        dismiss = Res.string.prompt_not_now,
    )
}

/** A gentle word that the answer was no, and where to read Opson's note. */
@HiddenFromObjC
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class BuilderDeclinedPrompt(
    enabled: BuildersEnabled,
    program: BuilderProgram,
    clock: Clock,
) : BuilderDecisionPrompt(enabled, program, clock, ApplicationStatus.Declined) {
    override val id = "builder_declined"
    override val priority = PromptPriority.NORMAL
    override val presentation = PromptPresentation.Dialog
    override val content = PromptContent(
        icon = AppIcons.Science,
        title = Res.string.builder_declined_prompt_title,
        body = Res.string.builder_declined_prompt_body,
        accept = Res.string.builder_declined_prompt_accept,
        dismiss = Res.string.prompt_not_now,
    )
}
