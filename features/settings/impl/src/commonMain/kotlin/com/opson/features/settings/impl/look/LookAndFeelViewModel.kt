package com.opson.features.settings.impl.look

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.TextSize
import com.opson.libraries.diary.ThemeMode
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

/**
 * The Appearance half of More › Display. None of these touch visibility, so each is a plain
 * settings write. The app root re-themes from them.
 */
data class LookAndFeelState(
    /** Theme: System, Light or Dark. */
    val theme: ThemeMode = ThemeMode.System,
    val textSize: TextSize = TextSize.Normal,
    val weightUnit: WeightUnit = WeightUnit.Kg,
)

object LookAndFeelStateBuilder {
    fun build(settings: Settings): LookAndFeelState = LookAndFeelState(
        theme = settings.theme,
        textSize = settings.textSize,
        weightUnit = settings.weightUnit,
    )
}

/** Display's back button and its Numbers half belong to `ModesViewModel`, so this one sends no events. */
@Inject
class LookAndFeelViewModel(
    private val settingsRepository: SettingsRepository,
) : SEAViewModel<LookAndFeelState, LookAndFeelEvent, LookAndFeelAction>(initialStateArg = LookAndFeelState()) {

    init {
        viewModelScope.launch {
            settingsRepository.observe().collect { takeAction(LookAndFeelAction.InputsArrived(it)) }
        }
    }

    override suspend fun handleAction(action: LookAndFeelAction) {
        when (action) {
            is LookAndFeelAction.InputsArrived -> action.updateState { LookAndFeelStateBuilder.build(action.settings) }
            is LookAndFeelAction.ThemePicked -> settingsRepository.update { it.copy(theme = action.theme) }
            is LookAndFeelAction.TextSizePicked -> settingsRepository.update { it.copy(textSize = action.size) }
            is LookAndFeelAction.WeightUnitPicked -> settingsRepository.update { it.copy(weightUnit = action.unit) }
        }
    }
}

sealed interface LookAndFeelEvent

sealed interface LookAndFeelAction {
    data class InputsArrived(val settings: Settings) : LookAndFeelAction
    data class ThemePicked(val theme: ThemeMode) : LookAndFeelAction
    data class TextSizePicked(val size: TextSize) : LookAndFeelAction
    data class WeightUnitPicked(val unit: WeightUnit) : LookAndFeelAction
}
