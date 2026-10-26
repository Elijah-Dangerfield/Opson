package com.opson.features.trends.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.TrendsCards
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.collectIn
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DayOfWeek
import me.tatarka.inject.annotations.Inject

/** The cards a user can switch on or off. Expended is 0.2 (no watch data), so it is not one. */
enum class TrendsCard { Intake, Recap, Nutrients, Weight }

data class CardToggle(val card: TrendsCard, val on: Boolean)

data class TrendsSettingsState(
    val range: TrendsRange = TrendsRange.Week,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    /** Only the cards that could show in the current mode. */
    val toggles: List<CardToggle> = emptyList(),
)

object TrendsSettingsStateBuilder {

    /** The days a week can start on, as the prototype offers them. */
    val WEEK_STARTS = listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /**
     * Intake is offered only when the chart could show (calories effectively
     * shown on the week view), Nutrients only when a macro or the micros could;
     * offering a switch for a card the mode will never draw is a switch that
     * does nothing. Recap and Weight always.
     */
    fun build(settings: Settings, policy: VisibilityPolicy): TrendsSettingsState {
        val cards = settings.trendsCards
        return TrendsSettingsState(
            range = settings.trendsRange,
            weekStart = settings.weekStart,
            toggles = buildList {
                if (TrendsStateBuilder.showsIntake(policy)) add(CardToggle(TrendsCard.Intake, cards.intake))
                add(CardToggle(TrendsCard.Recap, cards.recap))
                if (TrendsStateBuilder.showsNutrients(policy)) add(CardToggle(TrendsCard.Nutrients, cards.nutrients))
                add(CardToggle(TrendsCard.Weight, cards.weight))
            },
        )
    }
}

@Inject
class TrendsSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
) : SEAViewModel<TrendsSettingsState, TrendsSettingsEvent, TrendsSettingsAction>(
    initialStateArg = TrendsSettingsState(),
) {

    init {
        combine(settingsRepository.settings, policySource.policy) { settings, policy -> TrendsSettingsAction.InputsArrived(settings, policy) }
            .collectIn(viewModelScope) { takeAction(it) }
    }

    // None of these change what numbers are visible, so none goes through the pause.
    override suspend fun handleAction(action: TrendsSettingsAction) {
        when (action) {
            is TrendsSettingsAction.InputsArrived ->
                action.updateState { TrendsSettingsStateBuilder.build(action.settings, action.policy) }
            is TrendsSettingsAction.RangePicked -> settingsRepository.update { it.copy(trendsRange = action.range) }
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
    TrendsCard.Recap -> copy(recap = !recap)
    TrendsCard.Nutrients -> copy(nutrients = !nutrients)
    TrendsCard.Weight -> copy(weight = !weight)
}

sealed interface TrendsSettingsAction {
    data class InputsArrived(val settings: Settings, val policy: VisibilityPolicy) : TrendsSettingsAction
    data class RangePicked(val range: TrendsRange) : TrendsSettingsAction
    data class WeekStartPicked(val day: DayOfWeek) : TrendsSettingsAction
    data class CardToggled(val card: TrendsCard) : TrendsSettingsAction
    data object CloseTapped : TrendsSettingsAction
}

sealed interface TrendsSettingsEvent {
    data object Dismiss : TrendsSettingsEvent
}
