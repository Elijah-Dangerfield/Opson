package com.opson.features.builder.impl

import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.BuilderApplicationForm
import com.opson.libraries.foods.BuilderProgram
import com.opson.libraries.foods.BuilderResult
import com.opson.libraries.foods.JoinBlock
import com.opson.libraries.foods.LabelRegion
import com.opson.libraries.foods.ShoppingFrequency
import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/**
 * The application, a sheet over More › Builders: two quick choices, an optional line about why,
 * the rules, and the optional photo consent. [via] is where the page was opened from.
 */
@Serializable
data class BuilderApplyRoute(val via: String) : Route()

/** The rules in full, a sheet over the page. The application shows the same list in place. */
@Serializable
class BuilderRulesRoute : Route()

data class BuilderApplyState(
    val shopping: ShoppingFrequency? = null,
    val region: LabelRegion? = null,
    val motivation: String = "",
    val agreed: Boolean = false,
    /** The optional two-year photo consent, off unless they turn it on. */
    val retention: Boolean = false,
    val sending: Boolean = false,
    val problem: ApplyProblem? = null,
    /** The rules, opened in place under the checkbox. */
    val rulesOpen: Boolean = false,
) {
    /** Both questions answered and the rules accepted. The line about why is optional. */
    val canSend: Boolean get() = shopping != null && region != null && agreed && !sending
}

/** Why the application didn't go: the server's reason, or the connection. */
sealed interface ApplyProblem {
    data class Blocked(val block: JoinBlock) : ApplyProblem
    data object Offline : ApplyProblem
    data object Failed : ApplyProblem
    data object Unavailable : ApplyProblem
}

sealed interface BuilderApplyAction {
    data class ShoppingPicked(val shopping: ShoppingFrequency) : BuilderApplyAction
    data class RegionPicked(val region: LabelRegion) : BuilderApplyAction
    data class MotivationTyped(val text: String) : BuilderApplyAction
    data class AgreeChanged(val agreed: Boolean) : BuilderApplyAction
    data class RetentionChanged(val keep: Boolean) : BuilderApplyAction
    data object RulesTapped : BuilderApplyAction
    data object SendTapped : BuilderApplyAction
}

sealed interface BuilderApplyEvent {
    /** It went. The page underneath shows it as received. */
    data object Sent : BuilderApplyEvent
}

/** Sends one application. Nothing is sent until both questions are answered and the rules accepted. */
@Inject
class BuilderApplyViewModel(
    @Assisted private val route: BuilderApplyRoute,
    private val program: BuilderProgram,
) : SEAViewModel<BuilderApplyState, BuilderApplyEvent, BuilderApplyAction>(initialStateArg = BuilderApplyState()) {

    override suspend fun handleAction(action: BuilderApplyAction) {
        when (action) {
            is BuilderApplyAction.ShoppingPicked -> action.updateState { it.copy(shopping = action.shopping) }
            is BuilderApplyAction.RegionPicked -> action.updateState { it.copy(region = action.region) }
            is BuilderApplyAction.MotivationTyped -> action.updateState { it.copy(motivation = action.text.take(MAX_MOTIVATION)) }
            is BuilderApplyAction.AgreeChanged -> action.updateState { it.copy(agreed = action.agreed) }
            is BuilderApplyAction.RetentionChanged -> action.updateState { it.copy(retention = action.keep) }
            BuilderApplyAction.RulesTapped -> action.updateState { it.copy(rulesOpen = !it.rulesOpen) }
            BuilderApplyAction.SendTapped -> send(action)
        }
    }

    private suspend fun send(action: BuilderApplyAction) {
        val state = stateFlow.value
        if (!state.canSend) return
        val form = BuilderApplicationForm(
            shopping = checkNotNull(state.shopping),
            region = checkNotNull(state.region),
            motivation = state.motivation.trim().ifEmpty { null },
            photoRetention = state.retention,
            via = route.via,
        )
        action.updateState { it.copy(sending = true, problem = null) }
        val result = program.sendApplication(form)
        action.updateState {
            it.copy(
                sending = false,
                problem = when (result) {
                    is BuilderResult.Done -> null
                    is BuilderResult.Blocked -> ApplyProblem.Blocked(result.block)
                    BuilderResult.Unavailable -> ApplyProblem.Unavailable
                    is BuilderResult.Failed -> if (result.offline) ApplyProblem.Offline else ApplyProblem.Failed
                },
            )
        }
        if (result is BuilderResult.Done) sendEvent(BuilderApplyEvent.Sent)
    }

    companion object {
        /** The server keeps 500 characters; the field stops there rather than failing on send. */
        const val MAX_MOTIVATION = 500
    }
}
