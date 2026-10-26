package com.opson.features.settings.impl.targets

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.TargetStyle
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

/**
 * The goal style picker. [options] leaves out "A set deficit" outside Full numbers and Sprint:
 * the row is not drawn there at all (design v3). The pick is local until Done.
 */
data class GoalStyleState(
    val options: List<StyleKind> = listOf(StyleKind.Range, StyleKind.ByDay),
    val selected: StyleKind = StyleKind.Range,
    /** The style that is saved now, so Done can tell whether anything changed. */
    val saved: StyleKind = StyleKind.Range,
)

object GoalStyleStateBuilder {
    fun build(settings: Settings, picked: StyleKind? = null): GoalStyleState {
        val saved = when (settings.targets.style) {
            TargetStyle.Range -> StyleKind.Range
            is TargetStyle.Deficit -> StyleKind.Deficit
            is TargetStyle.ByDay -> StyleKind.ByDay
        }
        val options = StyleKind.entries.filter {
            it != StyleKind.Deficit || TargetStyle.deficitAllowed(settings.numbers.preset)
        }
        return GoalStyleState(options = options, selected = picked?.takeIf { it in options } ?: saved, saved = saved)
    }
}

@Inject
class GoalStyleViewModel(
    private val settingsRepository: SettingsRepository,
) : SEAViewModel<GoalStyleState, GoalStyleEvent, GoalStyleAction>(initialStateArg = GoalStyleState()) {

    private var picked: StyleKind? = null

    init {
        viewModelScope.launch {
            settingsRepository.observe().collect { takeAction(GoalStyleAction.InputsArrived(it)) }
        }
    }

    override suspend fun handleAction(action: GoalStyleAction) {
        when (action) {
            is GoalStyleAction.InputsArrived -> action.updateState { GoalStyleStateBuilder.build(action.settings, picked) }
            is GoalStyleAction.StylePicked -> {
                if (action.kind !in state.options) return
                picked = action.kind
                action.updateState { it.copy(selected = action.kind) }
            }
            GoalStyleAction.DoneTapped -> {
                val kind = state.selected
                if (kind != state.saved) {
                    settingsRepository.update { current ->
                        // Checked again inside the write: a mode change since the sheet opened
                        // must not let a deficit through where it is not offered.
                        val targets = when (kind) {
                            StyleKind.Range -> current.targets.useRange()
                            StyleKind.Deficit -> if (TargetStyle.deficitAllowed(current.numbers.preset)) {
                                // No burn source in 0.1, so the deficit starts at 0.
                                current.targets.useDeficit(burnKcal = null)
                            } else {
                                current.targets
                            }
                            StyleKind.ByDay -> current.targets.useByDay()
                        }
                        current.copy(targets = targets)
                    }
                }
                sendEvent(GoalStyleEvent.Dismiss)
            }
        }
    }
}

sealed interface GoalStyleEvent {
    data object Dismiss : GoalStyleEvent
}

sealed interface GoalStyleAction {
    data class InputsArrived(val settings: Settings) : GoalStyleAction
    data class StylePicked(val kind: StyleKind) : GoalStyleAction
    data object DoneTapped : GoalStyleAction
}
