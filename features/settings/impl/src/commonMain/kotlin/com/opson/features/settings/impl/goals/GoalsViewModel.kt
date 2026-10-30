@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.settings.impl.goals

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import androidx.lifecycle.viewModelScope
import com.opson.features.onboarding.OnboardingSetupEnabled
import com.opson.features.settings.FindSupportEnabled
import com.opson.features.settings.GoalsRoute
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.goals.GoalRepository
import com.opson.libraries.nutrition.GoalStatus
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.nutrition.WeeklyBudgets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/**
 * The goals page ([GoalsRoute]): every goal from [GoalRepository], this week's days for each, and
 * where it leads. Pause and pick up again are written here; changing a goal opens the editor.
 * Only with `release.onboardingSetup`; off, it closes at once.
 */
@HiddenFromObjC
@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class GoalsViewModel(
    @Assisted private val route: GoalsRoute,
    private val settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    private val goals: GoalRepository,
    calendar: LocalCalendar,
    private val flag: OnboardingSetupEnabled,
    private val findSupport: FindSupportEnabled,
) : SEAViewModel<GoalsState, GoalsEvent, GoalsAction>(initialStateArg = GoalsState()) {

    private val today = calendar.today()
    private var inputs: GoalsInputs? = null
    private var paceInfo: PaceInfoView? = null

    init {
        val settings = settingsRepository.observe()
        // This week, from the person's week start, read again if it changes.
        val week = settings.map { WeeklyBudgets.weekStartOf(today, it.weekStart) }.distinctUntilChanged()
            .flatMapLatest { start -> goals.days(start, start.plus(DatePeriod(days = DAYS_A_WEEK - 1))) }
        viewModelScope.launch {
            combine(settings, policySource.policy, goals.today(), week, goals.inputs()) { s, policy, statuses, days, baseline ->
                GoalsAction.Arrived(GoalsInputs(s, policy, statuses, days, baseline?.weightKg, today))
            }.collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: GoalsAction) {
        when (action) {
            is GoalsAction.Arrived -> {
                if (!flag()) {
                    sendEvent(GoalsEvent.Close)
                    return
                }
                inputs = action.inputs
                rebuild(action)
            }
            is GoalsAction.EditTapped -> sendEvent(GoalsEvent.OpenEdit(action.id))
            GoalsAction.AddTapped -> sendEvent(GoalsEvent.OpenEdit(null))
            is GoalsAction.PauseTapped -> settingsRepository.update { GoalBook.pause(it, action.id) }
            is GoalsAction.ResumeTapped -> settingsRepository.update { resume(it, action.id) }
            GoalsAction.PaceInfoTapped -> {
                paceInfo = inputs?.let(GoalsStateBuilder::paceInfo)
                rebuild(action)
            }
            GoalsAction.PaceInfoDismissed -> {
                paceInfo = null
                rebuild(action)
            }
            GoalsAction.BurnInfoTapped -> sendEvent(GoalsEvent.OpenBurnedInfo)
            GoalsAction.FindSupportTapped -> if (findSupport()) sendEvent(GoalsEvent.OpenFindSupport)
            GoalsAction.BackTapped -> sendEvent(GoalsEvent.Close)
        }
    }

    /**
     * Picks a goal up again. A calorie goal goes through [GoalBook.resume], which sets the target
     * from the typical day it already has (and pauses any other calorie goal); any other is made
     * active as it was.
     */
    private fun resume(settings: Settings, id: String): Settings {
        val goal = GoalBook.goals(settings).firstOrNull { it.id == id } ?: return settings
        if (!goal.type.setsCalories) return GoalBook.withGoal(settings, goal.copy(status = GoalStatus.Active))
        val lead = GoalBook.withGoal(settings, goal.copy(status = GoalStatus.Active))
        return GoalBook.resume(lead)
    }

    private suspend fun rebuild(action: GoalsAction) {
        val inputs = inputs ?: return
        action.updateState { GoalsStateBuilder.build(inputs, route.goalId, findSupport(), paceInfo) }
    }

    private companion object {
        const val DAYS_A_WEEK = 7
    }
}

@HiddenFromObjC
sealed interface GoalsEvent {
    /** The editor: the goal with this id, or a new goal for null. */
    data class OpenEdit(val id: String?) : GoalsEvent

    /** Total burned (TDEE)'s (i) sheet: what a calorie goal's typical day is made of. */
    data object OpenBurnedInfo : GoalsEvent
    data object OpenFindSupport : GoalsEvent
    data object Close : GoalsEvent
}

@HiddenFromObjC
sealed interface GoalsAction {
    data class Arrived(val inputs: GoalsInputs) : GoalsAction
    data class EditTapped(val id: String) : GoalsAction
    data object AddTapped : GoalsAction
    data class PauseTapped(val id: String) : GoalsAction
    data class ResumeTapped(val id: String) : GoalsAction
    data object PaceInfoTapped : GoalsAction
    data object PaceInfoDismissed : GoalsAction
    data object BurnInfoTapped : GoalsAction
    data object FindSupportTapped : GoalsAction
    data object BackTapped : GoalsAction
}
