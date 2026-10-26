package com.opson.features.settings.impl.look

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.PaletteChoice
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.TextSize
import com.opson.libraries.diary.ThemeMode
import com.opson.libraries.diary.Tone
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.reminders.RemindersEnabled
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

/** None of these touch visibility, so each is a plain settings write. The app root re-themes from them. */
data class LookAndFeelState(
    val palette: PaletteChoice = PaletteChoice.Pine,
    val theme: ThemeMode = ThemeMode.System,
    val textSize: TextSize = TextSize.Normal,
    val weightUnit: WeightUnit = WeightUnit.Kg,
    val tone: Tone = Tone.Plain,
    val askTags: Boolean = true,
    /** Tone and the hunger questions. They move to Reminders when `release.reminders` is on. */
    val showsTalk: Boolean = true,
)

object LookAndFeelStateBuilder {
    fun build(settings: Settings, remindersOn: Boolean = false): LookAndFeelState = LookAndFeelState(
        palette = settings.palette,
        theme = settings.theme,
        textSize = settings.textSize,
        weightUnit = settings.weightUnit,
        tone = settings.tone,
        askTags = settings.askTags,
        showsTalk = !remindersOn,
    )
}

@Inject
class LookAndFeelViewModel(
    private val settingsRepository: SettingsRepository,
    private val remindersEnabled: RemindersEnabled,
) : SEAViewModel<LookAndFeelState, LookAndFeelEvent, LookAndFeelAction>(initialStateArg = LookAndFeelState()) {

    init {
        viewModelScope.launch {
            settingsRepository.observe().collect { takeAction(LookAndFeelAction.InputsArrived(it)) }
        }
    }

    override suspend fun handleAction(action: LookAndFeelAction) {
        when (action) {
            is LookAndFeelAction.InputsArrived -> action.updateState { LookAndFeelStateBuilder.build(action.settings, remindersEnabled()) }
            is LookAndFeelAction.PalettePicked -> settingsRepository.update { it.copy(palette = action.palette) }
            is LookAndFeelAction.ThemePicked -> settingsRepository.update { it.copy(theme = action.theme) }
            is LookAndFeelAction.TextSizePicked -> settingsRepository.update { it.copy(textSize = action.size) }
            is LookAndFeelAction.WeightUnitPicked -> settingsRepository.update { it.copy(weightUnit = action.unit) }
            is LookAndFeelAction.TonePicked -> settingsRepository.update { it.copy(tone = action.tone) }
            LookAndFeelAction.HungerQuestionsToggled -> settingsRepository.update { it.copy(askTags = !it.askTags) }
            LookAndFeelAction.BackTapped -> sendEvent(LookAndFeelEvent.Close)
        }
    }
}

sealed interface LookAndFeelEvent {
    data object Close : LookAndFeelEvent
}

sealed interface LookAndFeelAction {
    data class InputsArrived(val settings: Settings) : LookAndFeelAction
    data class PalettePicked(val palette: PaletteChoice) : LookAndFeelAction
    data class ThemePicked(val theme: ThemeMode) : LookAndFeelAction
    data class TextSizePicked(val size: TextSize) : LookAndFeelAction
    data class WeightUnitPicked(val unit: WeightUnit) : LookAndFeelAction
    data class TonePicked(val tone: Tone) : LookAndFeelAction
    data object HungerQuestionsToggled : LookAndFeelAction
    data object BackTapped : LookAndFeelAction
}
