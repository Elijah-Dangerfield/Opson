package com.opson.features.settings.impl.targets

import androidx.lifecycle.viewModelScope
import com.opson.features.settings.impl.Toast
import com.opson.libraries.diary.MacroMode
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.RangeEnd
import com.opson.libraries.nutrition.StepBlock
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_targets_deficit_cap_toast
import opson.libraries.resources.generated.resources.settings_targets_floor_toast
import opson.libraries.resources.generated.resources.settings_targets_gap_toast

/**
 * Targets. Every change runs inside [SettingsRepository.update] against the settings it is
 * about to replace, so the floor and the deficit cap are checked in the same step as the write:
 * a refused step writes nothing and the toast says why.
 *
 * What the user burns is not known in 0.1 (the watch is 0.2, and there is no intake and weight
 * estimate yet), so the deficit card is built without a burn.
 */
@Inject
class TargetsViewModel(
    private val settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
) : SEAViewModel<TargetsState, TargetsEvent, TargetsAction>(initialStateArg = TargetsState()) {

    private var inputs: Pair<Settings, VisibilityPolicy>? = null

    // What is open on the page. Kept here rather than read back from state, because a write
    // (a new group) lands as new inputs after the action that opened it.
    private var editingGroup: Int? = null
    private var editingMacros: Boolean = false

    init {
        viewModelScope.launch {
            combine(settingsRepository.observe(), policySource.policy, TargetsAction::InputsArrived)
                .collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: TargetsAction) {
        when (action) {
            is TargetsAction.InputsArrived -> {
                inputs = action.settings to action.policy
                rebuild(action)
            }
            TargetsAction.GoalStyleTapped -> sendEvent(TargetsEvent.OpenGoalStyle)
            is TargetsAction.StepTapped -> step(action)
            is TargetsAction.DeficitStepped -> {
                var refused = false
                change { current -> current.targets.stepDeficit(action.up).also { refused = it == null } }
                if (refused && action.up) toast(Toast(Res.string.settings_targets_deficit_cap_toast))
            }
            is TargetsAction.DayTapped -> change { it.targets.moveDay(action.day, action.group) }
            TargetsAction.AddGroupTapped -> {
                val added = change { it.targets.addDayGroup() }
                (added.style as? TargetStyle.ByDay)?.groups?.let { editingGroup = it.lastIndex }
                rebuild(action)
            }
            is TargetsAction.EditGroupTapped -> {
                editingGroup = if (editingGroup == action.group) null else action.group
                rebuild(action)
            }
            is TargetsAction.RemoveGroupTapped -> {
                editingGroup = null
                change { it.targets.removeDayGroup(action.group) }
                rebuild(action)
            }
            TargetsAction.MacroCardTapped -> {
                editingMacros = !editingMacros
                rebuild(action)
            }
            is TargetsAction.MacroModePicked -> settingsRepository.update { it.copy(macroMode = action.mode) }
            TargetsAction.ModesTapped -> sendEvent(TargetsEvent.OpenModes)
            TargetsAction.BackTapped -> sendEvent(TargetsEvent.Close)
        }
    }

    private suspend fun step(action: TargetsAction.StepTapped) {
        var block: StepBlock? = null
        change { current ->
            val targets = current.targets
            targets.step(action.end, action.up, action.group, scaleMacros = current.macroMode == MacroMode.Ratios)
                .also { if (it == null) block = targets.stepBlock(action.end, action.up, action.group) }
        }
        when (block) {
            StepBlock.Floor -> toast(Toast(Res.string.settings_targets_floor_toast))
            StepBlock.Gap -> toast(Toast(Res.string.settings_targets_gap_toast))
            null -> Unit
        }
    }

    /** Writes [transform]'s targets; null keeps what is there. Returns the targets now saved. */
    private suspend fun change(transform: (Settings) -> Targets?): Targets =
        settingsRepository.update { current -> transform(current)?.let { current.copy(targets = it) } ?: current }
            .targets

    private suspend fun rebuild(action: TargetsAction) {
        val (settings, policy) = inputs ?: return
        action.updateState {
            TargetsStateBuilder.build(settings, policy, burnKcal = null, editingGroup = editingGroup, editingMacros = editingMacros)
        }
    }

    private suspend fun toast(toast: Toast) = sendEvent(TargetsEvent.ShowToast(toast))
}

sealed interface TargetsEvent {
    data class ShowToast(val toast: Toast) : TargetsEvent
    data object OpenGoalStyle : TargetsEvent
    data object OpenModes : TargetsEvent
    data object Close : TargetsEvent
}

sealed interface TargetsAction {
    data class InputsArrived(val settings: Settings, val policy: VisibilityPolicy) : TargetsAction
    data object GoalStyleTapped : TargetsAction

    /** One step on [end] of the range, or of day group [group]. */
    data class StepTapped(val end: RangeEnd, val up: Boolean, val group: Int? = null) : TargetsAction
    data class DeficitStepped(val up: Boolean) : TargetsAction
    data class DayTapped(val group: Int, val day: DayOfWeek) : TargetsAction
    data object AddGroupTapped : TargetsAction
    data class EditGroupTapped(val group: Int) : TargetsAction
    data class RemoveGroupTapped(val group: Int) : TargetsAction
    data object MacroCardTapped : TargetsAction
    data class MacroModePicked(val mode: MacroMode) : TargetsAction
    data object ModesTapped : TargetsAction
    data object BackTapped : TargetsAction
}
