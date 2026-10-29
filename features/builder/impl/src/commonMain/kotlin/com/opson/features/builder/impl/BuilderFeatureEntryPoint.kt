@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.builder.impl

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.builder.BuilderProgramRoute
import com.opson.features.builder.BuildersEnabled
import com.opson.features.logger.SubmitEntry
import com.opson.features.logger.SubmitFoodRoute
import com.opson.features.settings.MoreRowContribution
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.foods.BuilderProgram
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Route
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.screen
import com.opson.libraries.prompts.PromptAccept
import com.opson.libraries.prompts.PromptCandidate
import com.opson.libraries.prompts.PromptContent
import com.opson.libraries.prompts.PromptEvent
import com.opson.libraries.prompts.PromptFrequency
import com.opson.libraries.prompts.PromptPresentation
import com.opson.libraries.prompts.PromptPriority
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.foods.SubmittedFoods
import com.opson.libraries.ui.components.icon.AppIcons
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.builder_more_row
import opson.libraries.resources.generated.resources.builder_more_row_sub
import opson.libraries.resources.generated.resources.builder_more_row_sub_member
import opson.libraries.resources.generated.resources.builder_more_section
import opson.libraries.resources.generated.resources.builder_prompt_accept
import opson.libraries.resources.generated.resources.builder_prompt_body
import opson.libraries.resources.generated.resources.builder_prompt_title
import opson.libraries.resources.generated.resources.prompt_not_now
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * More › Builders. Registered whatever `release.builders` says, as other flagged pages are: only
 * the More row and the join prompt link here, and both check the flag. View models send events;
 * only this class navigates.
 */
@HiddenFromObjC
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class BuilderFeatureEntryPoint(
    private val viewModel: (BuilderProgramRoute) -> BuilderProgramViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<BuilderProgramRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<BuilderProgramRoute>()
            val model: BuilderProgramViewModel = viewModel { viewModel(route) }
            val state by model.stateFlow.collectAsStateWithLifecycle()
            model.ObserveEvents { event ->
                when (event) {
                    BuilderProgramEvent.Close -> router.goBack()
                    BuilderProgramEvent.AddFood -> router.navigate(SubmitFoodRoute(entry = SubmitEntry.Builders))
                    is BuilderProgramEvent.OpenLink -> router.openWebLink(event.url)
                }
            }
            BuilderProgramScreen(state = state, onAction = model::takeAction)
        }
    }
}

/** More › Opson Builders › Builders, while `release.builders` is on. */
@HiddenFromObjC
@Inject
@ContributesBinding(AppScope::class, multibinding = true)
class BuilderMoreRow(
    private val enabled: BuildersEnabled,
    private val program: BuilderProgram,
) : MoreRowContribution {
    override val section = Res.string.builder_more_section
    override val title = Res.string.builder_more_row
    override val subtitle get() = if (program.isBuilder) Res.string.builder_more_row_sub_member else Res.string.builder_more_row_sub
    override val icon = AppIcons.Science

    override fun route(): Route = BuilderProgramRoute()

    override fun visible(): Boolean = enabled.value
}

/**
 * The invitation to join, once, ever (the owner's rule: never seen more than once). Two moments
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
        if (!standing.canJoin) return false
        return moment == PromptEvent.FoodReportSent || submitted.reviews.value.values.any { it == ReviewStatus.Approved }
    }

    override suspend fun accept(): PromptAccept = PromptAccept.Open(BuilderProgramRoute(via = BuilderProgramRoute.VIA_PROMPT))
}
