package com.opson.features.onboarding.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.onboarding.GoalSetupRoute
import com.opson.libraries.core.Catching
import com.opson.libraries.core.DeviceRegion
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.diary.weightPlansOff
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.PlanCommitment
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.RegionUnits
import com.opson.libraries.diary.baselineEnvironment
import com.opson.libraries.health.goalReadings
import com.opson.libraries.nutrition.HealthReadings
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.ui.components.text.DateOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import kotlin.math.abs

/**
 * The goal flow for an account already set up ([GoalSetupRoute]): the same steps onboarding walks
 * ([PlanFlow]), starting from what the account has (settings, the latest weigh-in, a goal set
 * before), in the account's own mode. Nothing is written until the last step, so leaving part way
 * changes nothing. Saving writes what the steps set ([PlanFlow.save]), the commitment, and a
 * weigh-in only when the weight was changed.
 *
 * It is not onboarding: it never starts a range for someone with no goal ([PlanContext.fresh] is
 * false), so "just track" leaves Targets as it is.
 */
@Inject
@Suppress("LongParameterList")
class GoalSetupViewModel(
    @Assisted private val route: GoalSetupRoute,
    private val settingsRepository: SettingsRepository,
    private val weightRepository: WeightRepository,
    private val policySource: VisibilityPolicySource,
    private val calendar: LocalCalendar,
    private val region: DeviceRegion,
    private val health: HealthRepository,
    private val healthFlag: HealthReleaseFlag,
) : SEAViewModel<GoalSetupState, GoalSetupEvent, GoalSetupAction>(initialStateArg = GoalSetupState()) {

    private val logger = KLog.withTag("GoalSetup")

    /** The latest weigh-in when the flow opened, so an unchanged weight logs nothing. */
    private var startingKg: Double? = null

    init {
        viewModelScope.launch {
            val settings = settingsRepository.observe().first()
            val latest = weightRepository.observeWeighIns().first().maxByOrNull { it.measuredAt }?.kg
            takeAction(GoalSetupAction.Loaded(settings, latest))
        }
        viewModelScope.launch { policySource.policy.collect { takeAction(GoalSetupAction.PolicyChanged(it)) } }
        if (healthFlag()) {
            viewModelScope.launch { health.status.collect { takeAction(GoalSetupAction.HealthChanged(it)) } }
        }
    }

    override suspend fun handleAction(action: GoalSetupAction) {
        when (action) {
            is GoalSetupAction.Loaded -> {
                startingKg = action.latestKg
                action.updateState {
                    val regionHeight = RegionUnits.height(region.countryCode())
                    it.copy(
                        loaded = true,
                        settings = action.settings,
                        answers = PlanFlow.answersFrom(action.settings, action.latestKg, regionHeight),
                    ).rebuilt()
                }
                logStep(PlanStep.Profile)
            }
            is GoalSetupAction.PolicyChanged -> action.updateState { it.copy(policy = action.policy).rebuilt() }
            is GoalSetupAction.HealthChanged -> {
                action.updateState { it.copy(health = HealthOffer.from(action.status, health.source, connecting = false)).rebuilt() }
                // What a goal's typical day reads from the health app, read once it is connected.
                if (action.status == HealthStatus.Connected) {
                    val readings = Catching { health.goalReadings(calendar.today(), calendar.timeZone()) }.getOrNull() ?: HealthReadings.None
                    action.updateState { it.copy(readings = readings).rebuilt() }
                }
            }
            is GoalSetupAction.Plan -> action.handlePlan(action.change)
            GoalSetupAction.ContinueTapped -> action.move(forward = true, skip = false)
            GoalSetupAction.SkipTapped -> action.move(forward = true, skip = true)
            GoalSetupAction.BackTapped -> action.move(forward = false, skip = false)
            is GoalSetupAction.CommitTapped -> action.save(action.committed)
        }
    }

    private suspend fun GoalSetupAction.handlePlan(change: PlanAction) {
        val state = state
        val context = state.context ?: return
        if (state.saving) return
        when (change) {
            PlanAction.FindSupportTapped -> sendEvent(GoalSetupEvent.OpenSupport)
            PlanAction.ConnectHealthTapped -> connectHealth()
            else -> updateState { it.copy(answers = PlanFlow.reduce(it.answers, change, context)).rebuilt() }
        }
    }

    private suspend fun GoalSetupAction.connectHealth() {
        val offer = state.health as? HealthOffer.CanConnect ?: return
        updateState { it.copy(health = offer.copy(connecting = true)).rebuilt() }
        Catching { health.connect() }.logOnFailure { "Goal setup could not connect the health app" }
        updateState { it.copy(health = HealthOffer.from(health.status.value, health.source, connecting = false)).rebuilt() }
    }

    /**
     * Continue, Skip or Back. Continue waits on a goal step with no usable answer; Skip there means
     * no goal. Back from the first step closes the flow, and Continue from the overview opens the
     * commitment.
     */
    private suspend fun GoalSetupAction.move(forward: Boolean, skip: Boolean) {
        val current = state
        val context = current.context ?: return
        if (current.saving) return
        if (forward && !skip && !PlanFlow.canContinue(current.step, current.answers, context)) return
        if (skip && current.step == PlanStep.Goal) {
            updateState { it.copy(answers = it.answers.copy(goal = GoalAnswer(target = it.answers.goal.target))).rebuilt() }
        }
        val steps = PlanFlow.steps(state.answers, context)
        val index = steps.indexOf(current.step)
        val next = steps.getOrNull(if (forward) index + 1 else index - 1)
        if (next == null) {
            if (!forward) sendEvent(GoalSetupEvent.Close)
            return
        }
        updateState { it.copy(step = next).rebuilt() }
        logStep(next)
    }

    private suspend fun GoalSetupAction.save(committed: Boolean) {
        val current = state
        val context = current.context ?: return
        if (current.saving || current.step != PlanStep.Commit) return
        updateState { it.copy(saving = true) }
        Catching {
            settingsRepository.update { settings ->
                PlanFlow.save(settings, current.answers, context).copy(planCommitment = PlanCommitment(context.today, committed))
            }
        }.logOnFailure { "Goal setup could not save the goal" }
        val kg = current.answers.body.weightKgOrNull()
        val changed = kg != null && startingKg?.let { abs(it - kg) >= WEIGHT_CHANGED_KG } != false
        if (kg != null && changed && PlanViews.profile(current.answers, context).weightShown) {
            Catching { weightRepository.addWeighIn(kg) }.logOnFailure { "Goal setup could not log the weigh-in" }
        }
        logger.logEvent("goal_setup.completed", "from" to route.from, "committed" to committed)
        sendEvent(GoalSetupEvent.Close)
    }

    private fun logStep(step: PlanStep) {
        logger.logEvent("goal_setup.step_viewed", "step" to step.eventName, "from" to route.from)
    }

    /** The context and the views again, after any change. */
    private fun GoalSetupState.rebuilt(): GoalSetupState {
        val settings = settings ?: return this
        val policy = policy ?: return this
        val context = PlanContext(
            today = calendar.today(),
            hard = settings.weightPlansOff(calendar.today()),
            policy = policy,
            fresh = false,
            health = health,
            environment = settings.baselineEnvironment(readings),
            bodyFatPercent = settings.bodyFat,
            dateOrder = DateOrder.forRegion(region.countryCode()),
        )
        return copy(context = context, view = PlanViews.all(answers, context, settings))
    }

    private companion object {
        /** A weight retyped to the same tenth is the same weigh-in, not a new one. */
        const val WEIGHT_CHANGED_KG = 0.05
    }
}

data class GoalSetupState(
    val loaded: Boolean = false,
    val step: PlanStep = PlanStep.Profile,
    val answers: PlanAnswers = PlanAnswers(),
    val saving: Boolean = false,
    val health: HealthOffer = HealthOffer.Unavailable,
    /** What the health app has read, for a goal's typical day; none until it is connected. */
    val readings: HealthReadings = HealthReadings.None,
    /** The account's settings when the flow opened: what saving builds on and what the views compare with. */
    val settings: Settings? = null,
    val policy: VisibilityPolicy? = null,
    val context: PlanContext? = null,
    val view: PlanView? = null,
)

sealed interface GoalSetupEvent {
    data object Close : GoalSetupEvent

    /** Find support, from the note under a goal weight below a healthy range. */
    data object OpenSupport : GoalSetupEvent
}

sealed interface GoalSetupAction {
    data class Loaded(val settings: Settings, val latestKg: Double?) : GoalSetupAction
    data class PolicyChanged(val policy: VisibilityPolicy) : GoalSetupAction
    data class HealthChanged(val status: HealthStatus) : GoalSetupAction
    data class Plan(val change: PlanAction) : GoalSetupAction
    data object ContinueTapped : GoalSetupAction
    data object SkipTapped : GoalSetupAction
    data object BackTapped : GoalSetupAction
    data class CommitTapped(val committed: Boolean) : GoalSetupAction
}
