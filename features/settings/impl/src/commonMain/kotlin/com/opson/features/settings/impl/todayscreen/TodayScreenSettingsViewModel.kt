package com.opson.features.settings.impl.todayscreen

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.MainNumberChoice
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.flowroutines.SEAViewModel
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/** More › Today screen: every change is one settings write, so Today redraws as it lands. */
@Inject
class TodayScreenSettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : SEAViewModel<TodayScreenSettingsState, TodayScreenSettingsEvent, TodayScreenSettingsAction>(
    initialStateArg = TodayScreenSettingsState(),
) {
    init {
        viewModelScope.launch {
            settingsRepository.observe().collect { takeAction(TodayScreenSettingsAction.InputsArrived(it)) }
        }
    }

    override suspend fun handleAction(action: TodayScreenSettingsAction) {
        when (action) {
            is TodayScreenSettingsAction.InputsArrived -> action.updateState { TodayScreenSettingsStateBuilder.build(action.settings) }
            is TodayScreenSettingsAction.PresetPicked -> {
                // Rings with a range is not offered; the card would only fall back.
                val offered = state.presets.firstOrNull { it.preset == action.preset }?.enabled == true
                if (offered && state.cardArranged) settingsRepository.update { it.copy(todayCard = action.preset) }
            }
            TodayScreenSettingsAction.MainNumberTapped -> sendEvent(TodayScreenSettingsEvent.OpenChoice(TodayScreenChoice.MainNumber))
            TodayScreenSettingsAction.CardPagesTapped -> sendEvent(TodayScreenSettingsEvent.OpenChoice(TodayScreenChoice.CardPages))
            is TodayScreenSettingsAction.Toggled -> settingsRepository.update { action.row.set(it, action.on) }
            TodayScreenSettingsAction.BackTapped -> sendEvent(TodayScreenSettingsEvent.Close)
        }
    }
}

/** A switch on the page, and the setting it writes. */
enum class TodaySwitch(val set: (Settings, Boolean) -> Settings) {
    BurnedBreakdown({ s, on -> s.copy(todayBurnedBreakdown = on) }),
    MealsCount({ s, on -> s.copy(todayMealsCount = on) }),
    NutrientsLink({ s, on -> s.copy(todayNutrientsLink = on) }),
    Water({ s, on -> s.copy(todayWater = on) }),
    Weight({ s, on -> s.copy(todayWeight = on) }),
    CloseDay({ s, on -> s.copy(todayCloseDay = on) }),
}

/** The two choices that open a sheet: the main number (13.6b) and the card's pages. */
enum class TodayScreenChoice(val wire: String) {
    MainNumber("main_number"),
    CardPages("card_pages"),
    ;

    companion object {
        fun fromWire(value: String): TodayScreenChoice = entries.firstOrNull { it.wire == value } ?: MainNumber
    }
}

sealed interface TodayScreenSettingsEvent {
    data class OpenChoice(val choice: TodayScreenChoice) : TodayScreenSettingsEvent
    data object Close : TodayScreenSettingsEvent
}

sealed interface TodayScreenSettingsAction {
    data class InputsArrived(val settings: Settings) : TodayScreenSettingsAction
    data class PresetPicked(val preset: SummaryCardPreset) : TodayScreenSettingsAction
    data object MainNumberTapped : TodayScreenSettingsAction
    data object CardPagesTapped : TodayScreenSettingsAction
    data class Toggled(val row: TodaySwitch, val on: Boolean) : TodayScreenSettingsAction
    data object BackTapped : TodayScreenSettingsAction
}

/**
 * The main number (13.6b) or card pages sheet. Picking writes at once and closes. A new main
 * number also forgets what the card's toggle remembered for every goal style: this sets what the
 * toggle resets to.
 */
@Inject
class TodayScreenChoiceViewModel(
    @Assisted private val choice: TodayScreenChoice,
    private val settingsRepository: SettingsRepository,
) : SEAViewModel<TodayScreenChoiceState, TodayScreenChoiceEvent, TodayScreenChoiceAction>(
    initialStateArg = TodayScreenChoiceState(choice),
) {
    init {
        viewModelScope.launch {
            settingsRepository.observe().collect { takeAction(TodayScreenChoiceAction.InputsArrived(it)) }
        }
    }

    override suspend fun handleAction(action: TodayScreenChoiceAction) {
        when (action) {
            is TodayScreenChoiceAction.InputsArrived -> action.updateState { TodayScreenChoiceState.of(choice, action.settings) }
            is TodayScreenChoiceAction.MainNumberPicked -> {
                settingsRepository.update { it.copy(todayMainNumber = action.choice, todayMainNumberByStyle = emptyMap()) }
                sendEvent(TodayScreenChoiceEvent.Dismiss)
            }
            is TodayScreenChoiceAction.PagesPicked -> {
                settingsRepository.update { it.copy(todayMacrosPage = action.macros) }
                sendEvent(TodayScreenChoiceEvent.Dismiss)
            }
        }
    }
}

data class TodayScreenChoiceState(
    val choice: TodayScreenChoice,
    val mainNumber: MainNumberChoice = MainNumberChoice.Follow,
    val follows: FollowStyle = FollowStyle.Range,
    val macrosPage: Boolean = true,
) {
    companion object {
        fun of(choice: TodayScreenChoice, settings: Settings): TodayScreenChoiceState {
            val page = TodayScreenSettingsStateBuilder.build(settings)
            return TodayScreenChoiceState(choice, page.mainNumber, page.follows, page.macrosPage)
        }
    }
}

sealed interface TodayScreenChoiceEvent {
    data object Dismiss : TodayScreenChoiceEvent
}

sealed interface TodayScreenChoiceAction {
    data class InputsArrived(val settings: Settings) : TodayScreenChoiceAction
    data class MainNumberPicked(val choice: MainNumberChoice) : TodayScreenChoiceAction
    data class PagesPicked(val macros: Boolean) : TodayScreenChoiceAction
}
