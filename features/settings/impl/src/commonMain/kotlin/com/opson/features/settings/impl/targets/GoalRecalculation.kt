@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.settings.impl.targets

import androidx.lifecycle.viewModelScope
import com.opson.features.onboarding.OnboardingSetupEnabled
import com.opson.features.settings.GoalRecalculationRoute
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.goals.GoalRepository
import com.opson.libraries.navigation.Route
import com.opson.libraries.nutrition.Recalculation
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.prompts.PromptAccept
import com.opson.libraries.prompts.PromptCandidate
import com.opson.libraries.prompts.PromptContent
import com.opson.libraries.prompts.PromptFrequency
import com.opson.libraries.prompts.PromptPresentation
import com.opson.libraries.prompts.PromptPriority
import com.opson.libraries.ui.components.icon.AppIcons
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.prompt_goal_recalc_accept
import opson.libraries.resources.generated.resources.prompt_goal_recalc_body
import opson.libraries.resources.generated.resources.prompt_goal_recalc_title
import opson.libraries.resources.generated.resources.prompt_not_now
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration.Companion.days

/**
 * "Your goal's target can be updated", over Today (owner feedback, 2026-10-30: connecting Apple
 * Health changes a goal's math, and people should know everything is taken into account). What
 * changed, the target now and with the update, and the choice; nothing changes until they make
 * it. Closing it without a choice asks again another day, through the prompt's own rules.
 */
@Inject
class GoalRecalculationViewModel(
    private val settingsRepository: SettingsRepository,
    goals: GoalRepository,
    policySource: VisibilityPolicySource,
    private val calendar: LocalCalendar,
) : SEAViewModel<GoalRecalculationState, GoalRecalculationEvent, GoalRecalculationAction>(initialStateArg = GoalRecalculationState()) {

    init {
        viewModelScope.launch {
            combine(goals.recalculation(), policySource.policy, GoalRecalculationAction::Arrived).collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: GoalRecalculationAction) {
        when (action) {
            is GoalRecalculationAction.Arrived -> {
                val offer = action.recalculation
                // Answered on another screen or phone since the prompt picked it: nothing left to ask.
                if (offer == null) {
                    sendEvent(GoalRecalculationEvent.Close)
                    return
                }
                action.updateState { GoalRecalculationState(offer = offer, dialog = TargetsStateBuilder.recalculated(offer, action.policy)) }
            }
            GoalRecalculationAction.Accepted -> answer { settings, offer -> GoalBook.accept(settings, offer, calendar.today()) }
            GoalRecalculationAction.Kept -> answer { settings, offer -> GoalBook.keep(settings, offer, calendar.today()) }
            GoalRecalculationAction.Dismissed -> sendEvent(GoalRecalculationEvent.Close)
        }
    }

    private suspend fun answer(write: (com.opson.libraries.diary.Settings, Recalculation) -> com.opson.libraries.diary.Settings) {
        val offer = state.offer ?: return
        settingsRepository.update { write(it, offer) }
        sendEvent(GoalRecalculationEvent.Close)
    }
}

data class GoalRecalculationState(val offer: Recalculation? = null, val dialog: TargetsDialog.Recalculated? = null)

sealed interface GoalRecalculationEvent {
    data object Close : GoalRecalculationEvent
}

sealed interface GoalRecalculationAction {
    data class Arrived(val recalculation: Recalculation?, val policy: VisibilityPolicy) : GoalRecalculationAction
    data object Accepted : GoalRecalculationAction
    data object Kept : GoalRecalculationAction
    data object Dismissed : GoalRecalculationAction
}

/**
 * The prompt that opens [GoalRecalculationRoute] when the goal setting the target has a
 * recalculation to offer. A screen prompt, because each recalculation is its own question: a
 * dialog prompt would end for good after the first answer. It still takes the session's one
 * prompt and waits a day between showings.
 */
@HiddenFromObjC
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class GoalRecalculationPrompt(
    private val flag: OnboardingSetupEnabled,
    private val goals: GoalRepository,
) : PromptCandidate {
    override val id = ID
    override val priority = PromptPriority.HIGH
    override val presentation = PromptPresentation.Screen
    override val content = PromptContent(
        icon = AppIcons.Favorite,
        title = Res.string.prompt_goal_recalc_title,
        body = Res.string.prompt_goal_recalc_body,
        accept = Res.string.prompt_goal_recalc_accept,
        dismiss = Res.string.prompt_not_now,
    )
    override val frequency = PromptFrequency(maxShows = Int.MAX_VALUE, maxDismissals = Int.MAX_VALUE, minGap = 1.days)

    override suspend fun isEligible(): Boolean = flag() && goals.recalculation().first() != null

    override suspend fun accept(): PromptAccept = PromptAccept.Done

    override suspend fun screen(): Route? = GoalRecalculationRoute().takeIf { isEligible() }

    companion object {
        /** Never rename: it keys the prompt counts and the `prompt` attribute. */
        const val ID = "goal_recalculated"
    }
}
