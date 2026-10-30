package com.opson.features.trends.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.TrendsCards
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.collectIn
import com.opson.libraries.health.GoalsAndEnergyEnabled
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import me.tatarka.inject.annotations.Inject

/** The cards a user can switch on or off, in page order. */
enum class TrendsCard { Intake, Calendar, Recap, Nutrients, Expended, Steps, Running, Weight, Journal }

data class CardToggle(val card: TrendsCard, val on: Boolean)

data class TrendsSettingsState(
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    /** Only the cards that could show in the current mode. */
    val toggles: List<CardToggle> = emptyList(),
)

object TrendsSettingsStateBuilder {

    /** The days a week can start on, as the prototype offers them. */
    val WEEK_STARTS = listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /**
     * Intake is offered only when the chart could show (calories effectively
     * shown on the week view), Nutrients only when a macro could, Expended only
     * with a health app connected and energy out shown, Weight only when weight
     * is not hidden; offering a switch for a card the mode will never draw is a
     * switch that does nothing. Steps with `release.health` on ([healthOn]), connected or not,
     * since the card offers Connect, and wherever energy out isn't hidden. Recap, the calendar, and Hunger and notes, always: where amounts
     * are hidden the calendar only says which days have something logged. The running total only
     * with Burned on ([goalsAndEnergy]) and where it could show ([RunningTotals.card]).
     */
    fun build(
        settings: Settings,
        policy: VisibilityPolicy,
        healthConnected: Boolean = false,
        goalsAndEnergy: Boolean = false,
        healthOn: Boolean = false,
    ): TrendsSettingsState {
        val cards = settings.trendsCards
        return TrendsSettingsState(
            weekStart = settings.weekStart,
            toggles = buildList {
                if (TrendsStateBuilder.showsIntake(policy)) add(CardToggle(TrendsCard.Intake, cards.intake))
                add(CardToggle(TrendsCard.Calendar, cards.calendar))
                add(CardToggle(TrendsCard.Recap, cards.recap))
                if (TrendsStateBuilder.showsNutrients(policy)) add(CardToggle(TrendsCard.Nutrients, cards.nutrients))
                if (healthConnected && TrendsStateBuilder.showsExpended(policy)) add(CardToggle(TrendsCard.Expended, cards.expended))
                if (healthOn && policy.effectiveLevel(Metric.EnergyOut, Surface.Week) != Visibility.Hide) add(CardToggle(TrendsCard.Steps, cards.steps))
                if (goalsAndEnergy && !settings.onboarding.hard && TrendsStateBuilder.showsIntake(policy) && TrendsStateBuilder.showsExpended(policy)) {
                    add(CardToggle(TrendsCard.Running, cards.runningTotal))
                }
                if (TrendsStateBuilder.showsWeight(policy)) add(CardToggle(TrendsCard.Weight, cards.weight))
                add(CardToggle(TrendsCard.Journal, cards.journal))
            },
        )
    }
}

@Inject
class TrendsSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    health: HealthRepository,
    healthFlag: HealthReleaseFlag,
    goalsAndEnergy: GoalsAndEnergyEnabled,
) : SEAViewModel<TrendsSettingsState, TrendsSettingsEvent, TrendsSettingsAction>(
    initialStateArg = TrendsSettingsState(),
) {

    init {
        // The status Trends' own read last left; looking at it never touches the store.
        val connected = if (healthFlag()) health.status.map { it == HealthStatus.Connected } else flowOf(false)
        val burnedOn = goalsAndEnergy()
        val healthOn = healthFlag()
        combine(settingsRepository.settings, policySource.policy, connected) { settings, policy, connected ->
            TrendsSettingsAction.InputsArrived(settings, policy, connected, burnedOn, healthOn)
        }.collectIn(viewModelScope) { takeAction(it) }
    }

    // None of these change what numbers are visible, so none goes through the pause.
    override suspend fun handleAction(action: TrendsSettingsAction) {
        when (action) {
            is TrendsSettingsAction.InputsArrived ->
                action.updateState { TrendsSettingsStateBuilder.build(action.settings, action.policy, action.healthConnected, action.goalsAndEnergy, action.healthOn) }
            is TrendsSettingsAction.WeekStartPicked -> {
                if (action.day in TrendsSettingsStateBuilder.WEEK_STARTS) {
                    settingsRepository.update { it.copy(weekStart = action.day) }
                }
            }
            is TrendsSettingsAction.CardToggled ->
                settingsRepository.update { it.copy(trendsCards = it.trendsCards.flipped(action.card)) }
            TrendsSettingsAction.CloseTapped -> sendEvent(TrendsSettingsEvent.Dismiss)
        }
    }
}

private fun TrendsCards.flipped(card: TrendsCard): TrendsCards = when (card) {
    TrendsCard.Intake -> copy(intake = !intake)
    TrendsCard.Calendar -> copy(calendar = !calendar)
    TrendsCard.Running -> copy(runningTotal = !runningTotal)
    TrendsCard.Recap -> copy(recap = !recap)
    TrendsCard.Nutrients -> copy(nutrients = !nutrients)
    TrendsCard.Expended -> copy(expended = !expended)
    TrendsCard.Steps -> copy(steps = !steps)
    TrendsCard.Weight -> copy(weight = !weight)
    TrendsCard.Journal -> copy(journal = !journal)
}

sealed interface TrendsSettingsAction {
    data class InputsArrived(
        val settings: Settings,
        val policy: VisibilityPolicy,
        val healthConnected: Boolean = false,
        val goalsAndEnergy: Boolean = false,
        val healthOn: Boolean = false,
    ) : TrendsSettingsAction
    data class WeekStartPicked(val day: DayOfWeek) : TrendsSettingsAction
    data class CardToggled(val card: TrendsCard) : TrendsSettingsAction
    data object CloseTapped : TrendsSettingsAction
}

sealed interface TrendsSettingsEvent {
    data object Dismiss : TrendsSettingsEvent
}
