package com.opson.features.settings.impl.goals

import androidx.lifecycle.viewModelScope
import com.opson.features.onboarding.OnboardingSetupEnabled
import com.opson.features.settings.FindSupportEnabled
import com.opson.features.settings.GoalEditRoute
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.goals.GoalRepository
import com.opson.libraries.goals.GoalStatuses
import com.opson.libraries.goals.ui.ProjectionChartUi
import com.opson.libraries.nutrition.BaselineInputs
import com.opson.libraries.nutrition.BodyFatFrom
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.GoalKind
import com.opson.libraries.nutrition.GoalTargets
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/**
 * Set a goal of any type, or change one ([GoalEditRoute]). Nothing is written until Save; then the
 * goal goes through [GoalEditor.save] (a goal that sets the target writes it from a typical day
 * now), and the page turns into the reveal: where the goal leads, drawn in, with the success tap.
 * Done closes it.
 *
 * Only with `release.onboardingSetup`, as every goal is; off, it closes at once.
 */
@Inject
class GoalEditViewModel(
    @Assisted private val route: GoalEditRoute,
    private val settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    private val goals: GoalRepository,
    calendar: LocalCalendar,
    private val flag: OnboardingSetupEnabled,
    private val findSupport: FindSupportEnabled,
) : SEAViewModel<GoalEditState, GoalEditEvent, GoalEditAction>(initialStateArg = GoalEditState()) {

    private val logger = KLog.withTag("GoalEdit")
    private val today = calendar.today()
    private var context: GoalEditContext? = null
    private var draft: GoalDraft? = null

    init {
        viewModelScope.launch {
            combine(settingsRepository.observe(), policySource.policy, goals.inputs(), GoalEditAction::Arrived).collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: GoalEditAction) {
        when (action) {
            is GoalEditAction.Arrived -> arrived(action)
            is GoalEditAction.Edit -> {
                val context = context ?: return
                if (state.saved != null || state.saving) return
                draft = draft?.let { GoalEditor.reduce(it, action.edit, context) }
                rebuild(action)
            }
            GoalEditAction.SaveTapped -> save(action)
            GoalEditAction.BackTapped -> back(action)
            GoalEditAction.DoneTapped -> sendEvent(GoalEditEvent.Close)
            GoalEditAction.DetailsTapped -> sendEvent(GoalEditEvent.OpenGoalSetup)
            GoalEditAction.FindSupportTapped -> if (findSupport()) sendEvent(GoalEditEvent.OpenFindSupport)
            GoalEditAction.PaceInfoTapped -> action.updateState { it.copy(paceInfo = paceInfo()) }
            GoalEditAction.PaceInfoDismissed -> action.updateState { it.copy(paceInfo = null) }
        }
    }

    private suspend fun arrived(action: GoalEditAction.Arrived) {
        if (!flag()) {
            sendEvent(GoalEditEvent.Close)
            return
        }
        val existing = route.goalId?.let { id -> GoalBook.goals(action.settings).firstOrNull { it.id == id } }
        val next = GoalEditContext(today, action.settings, action.policy, action.inputs, action.inputs?.weightKg, existing)
        context = next
        if (draft == null) draft = GoalEditor.draft(existing, route.kind?.let(GoalKind::fromKey)?.takeIf { it in GoalEditor.kinds(next) }, next)
        rebuild(action)
    }

    private suspend fun back(action: GoalEditAction) {
        val current = draft
        // A new goal whose type was picked here goes back to the list; anything else closes.
        if (state.saved == null && route.goalId == null && route.kind == null && current?.kind != null) {
            draft = current.copy(kind = null)
            rebuild(action)
        } else {
            sendEvent(GoalEditEvent.Close)
        }
    }

    private suspend fun save(action: GoalEditAction) {
        val context = context ?: return
        val goal = draft?.let { GoalEditor.goal(it, context) } ?: return
        if (state.saving || state.saved != null) return
        action.updateState { it.copy(saving = true) }
        val saved = Catching { settingsRepository.update { GoalEditor.save(it, goal) } }
            .logOnFailure { "Goal edit could not save the goal" }
            .isSuccess
        if (!saved) {
            action.updateState { it.copy(saving = false) }
            return
        }
        logger.logEvent("goal.saved", "kind" to goal.type.kind.key, "editing" to (route.goalId != null), "averaging" to goal.averaging.key)
        val target = GoalTargets.of(goal)?.aimKcal
        action.updateState {
            it.copy(
                saving = false,
                saved = GoalSaved(
                    kind = goal.type.kind,
                    chart = GoalEditor.chart(goal, context),
                    target = if (context.calories && target != null) context.policy.resolve(Metric.Calories, target.toDouble(), Surface.Settings) else Display.Hidden,
                    waiting = goal.type.setsCalories && goal.baseline == null,
                ),
            )
        }
    }

    private fun paceInfo(): PaceInfoView? {
        val context = context ?: return null
        val limit = GoalStatuses.limit(context.settings, context.weightKg, context.today)
        return PaceInfoView(
            limit = if (context.calories && limit != null) {
                context.policy.resolve(Metric.Calories, limit.kcalPerDay.toDouble(), Surface.Settings)
            } else {
                Display.Hidden
            },
            estimated = limit?.bodyFat?.from == BodyFatFrom.Estimated,
            canWorkOut = limit != null,
        )
    }

    private suspend fun rebuild(action: GoalEditAction) {
        val context = context ?: return
        val draft = draft ?: return
        action.updateState { it.copy(loaded = true, view = GoalEditor.view(draft, context), supportLink = findSupport()) }
    }
}

/**
 * The editor's page. [view] is the form (or the type list); [saved] replaces it once the goal is
 * saved, with the reveal. [paceInfo] is the pace note's (i) while open.
 */
data class GoalEditState(
    val loaded: Boolean = false,
    val view: GoalEditView? = null,
    val saving: Boolean = false,
    val saved: GoalSaved? = null,
    val paceInfo: PaceInfoView? = null,
    /** The pace and low-goal notes link to Find support (`release.findSupport`). */
    val supportLink: Boolean = false,
)

/**
 * A goal just saved: its type, where it leads ([chart], where weight shows) and the daily target it
 * set ([target], where calories show). [waiting] says it sets the target once a typical day can be
 * worked out.
 */
data class GoalSaved(val kind: GoalKind, val chart: ProjectionChartUi?, val target: Display, val waiting: Boolean = false)

/** How fast is right: the fat-loss limit where calories show, whether body fat was estimated, and whether one could be worked out. */
data class PaceInfoView(val limit: Display, val estimated: Boolean, val canWorkOut: Boolean)

sealed interface GoalEditEvent {
    data object Close : GoalEditEvent

    /** The goal flow, which asks for the details a target is worked out from. */
    data object OpenGoalSetup : GoalEditEvent
    data object OpenFindSupport : GoalEditEvent
}

sealed interface GoalEditAction {
    data class Arrived(val settings: Settings, val policy: VisibilityPolicy, val inputs: BaselineInputs?) : GoalEditAction
    data class Edit(val edit: GoalEdit) : GoalEditAction
    data object SaveTapped : GoalEditAction
    data object BackTapped : GoalEditAction

    /** Done on the reveal. */
    data object DoneTapped : GoalEditAction

    /** "Add your details", where a target can't be worked out yet. */
    data object DetailsTapped : GoalEditAction
    data object FindSupportTapped : GoalEditAction
    data object PaceInfoTapped : GoalEditAction
    data object PaceInfoDismissed : GoalEditAction
}
