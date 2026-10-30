package com.opson.features.foods.impl

import com.opson.features.foods.ReportRecipeRoute
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.PublicRecipes
import com.opson.libraries.foods.RecipeActionResult
import com.opson.libraries.foods.RecipeReportReason
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.recipes_public_not_shared_body
import opson.libraries.resources.generated.resources.recipes_report_restricted
import opson.libraries.resources.generated.resources.recipes_report_sent
import opson.libraries.resources.generated.resources.recipes_report_too_many
import opson.libraries.resources.generated.resources.recipes_share_failed
import opson.libraries.resources.generated.resources.recipes_share_offline
import org.jetbrains.compose.resources.StringResource
import kotlin.uuid.Uuid

data class ReportRecipeState(
    val name: String,
    val reason: RecipeReportReason? = null,
    val note: String = "",
    val sending: Boolean = false,
    val problem: StringResource? = null,
) {
    val canSend: Boolean get() = reason != null && !sending
}

sealed interface ReportRecipeAction {
    data class ReasonPicked(val reason: RecipeReportReason) : ReportRecipeAction
    data class NoteTyped(val text: String) : ReportRecipeAction
    data object SendTapped : ReportRecipeAction
}

sealed interface ReportRecipeEvent {
    data class Sent(val toast: Toast) : ReportRecipeEvent
}

/** A report on a shared recipe. Online only; one that can't go keeps the sheet and what was typed. */
@Inject
class ReportRecipeViewModel(
    @Assisted private val route: ReportRecipeRoute,
    private val publicRecipes: PublicRecipes,
) : SEAViewModel<ReportRecipeState, ReportRecipeEvent, ReportRecipeAction>(initialStateArg = ReportRecipeState(name = route.name)) {

    /** Made once, so a retry is the same report. */
    private val reportId = Uuid.random().toString()

    override suspend fun handleAction(action: ReportRecipeAction) {
        when (action) {
            is ReportRecipeAction.ReasonPicked -> action.updateState { it.copy(reason = action.reason, problem = null) }
            is ReportRecipeAction.NoteTyped -> action.updateState { it.copy(note = action.text.take(MAX_NOTE)) }
            ReportRecipeAction.SendTapped -> {
                val current = state
                val reason = current.reason ?: return
                if (current.sending) return
                action.updateState { it.copy(sending = true, problem = null) }
                val problem = when (val result = publicRecipes.report(route.code, reportId, reason, current.note)) {
                    RecipeActionResult.Done -> return sendEvent(ReportRecipeEvent.Sent(Toast(Res.string.recipes_report_sent)))
                    RecipeActionResult.NotFound -> Res.string.recipes_public_not_shared_body
                    RecipeActionResult.Restricted -> Res.string.recipes_report_restricted
                    RecipeActionResult.TooMany -> Res.string.recipes_report_too_many
                    is RecipeActionResult.Failed -> if (result.offline) Res.string.recipes_share_offline else Res.string.recipes_share_failed
                }
                action.updateState { it.copy(sending = false, problem = problem) }
            }
        }
    }

    private companion object {
        const val MAX_NOTE = 1000
    }
}
