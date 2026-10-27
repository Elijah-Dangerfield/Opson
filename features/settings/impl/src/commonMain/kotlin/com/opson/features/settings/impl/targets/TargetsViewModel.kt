package com.opson.features.settings.impl.targets

import androidx.lifecycle.viewModelScope
import com.opson.features.settings.impl.Toast
import com.opson.features.settings.impl.todayscreen.TodayScreenSettingsStateBuilder
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MacroMode
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.diary.forgetMainNumber
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.GoalsAndEnergyEnabled
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.GroupShape
import com.opson.libraries.nutrition.RangeEnd
import com.opson.libraries.nutrition.StepBlock
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_targets_deficit_cap_toast
import opson.libraries.resources.generated.resources.settings_targets_floor_toast
import opson.libraries.resources.generated.resources.settings_targets_gap_toast
import opson.libraries.resources.generated.resources.settings_targets_surplus_cap

/**
 * Targets. Every change runs inside [SettingsRepository.update] against the settings it is
 * about to replace, so the floor and the deficit and surplus caps are checked in the same step as
 * the write: a refused step writes nothing and the toast says why. Picking a goal style writes it
 * at once (design sync 4, 13.2e).
 *
 * With `release.goalsAndEnergy` on, today's Burned ([BurnedRepository]) is what a set deficit or
 * surplus is aimed at, and what one starts from when picked. Off, nothing reads it: the deficit
 * waits on the range, and a surplus goes on top of the usual target.
 */
@Inject
class TargetsViewModel(
    private val settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    private val health: HealthRepository,
    healthFlag: HealthReleaseFlag,
    private val goalsAndEnergy: GoalsAndEnergyEnabled,
    burnedRepository: BurnedRepository,
    calendar: LocalCalendar,
) : SEAViewModel<TargetsState, TargetsEvent, TargetsAction>(initialStateArg = TargetsState()) {

    private var inputs: TargetsAction.InputsArrived? = null

    // What is open on the page. Kept here rather than read back from state, because a write
    // (a new group) lands as new inputs after the action that opened it.
    private var editingGroup: Int? = null
    private var editingMacros: Boolean = false

    init {
        // With release.health off the store is never asked, so the exercise row can't show.
        val connected = if (healthFlag()) health.status.map { it == HealthStatus.Connected } else flowOf(false)
        val burned = if (goalsAndEnergy()) burnedRepository.burned(calendar.today()) else flowOf(null)
        viewModelScope.launch {
            combine(settingsRepository.observe(), policySource.policy, connected, burned, TargetsAction::InputsArrived)
                .collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: TargetsAction) {
        when (action) {
            is TargetsAction.InputsArrived -> {
                inputs = action
                rebuild(action)
            }
            is TargetsAction.StylePicked -> pickStyle(action.kind)
            TargetsAction.RestingEnergyTapped -> sendEvent(TargetsEvent.OpenRestingEnergy)
            TargetsAction.ActivityTapped -> sendEvent(TargetsEvent.OpenIntegrations)
            is TargetsAction.StepTapped -> step(action)
            is TargetsAction.DeficitStepped -> {
                var refused = false
                change { current -> current.targets.stepDeficit(action.up).also { refused = it == null } }
                if (refused && action.up) toast(Toast(Res.string.settings_targets_deficit_cap_toast))
            }
            is TargetsAction.NumberStepped -> {
                var refused = false
                change { current ->
                    current.targets.stepNumber(action.up, action.group, scaleMacros = current.macroMode == MacroMode.Ratios)
                        .also { refused = it == null }
                }
                if (refused) toast(Toast(Res.string.settings_targets_floor_toast))
            }
            is TargetsAction.SurplusStepped -> {
                var refused = false
                change { current -> current.targets.stepSurplus(action.up, action.group).also { refused = it == null } }
                if (refused && action.up) toast(Toast(Res.string.settings_targets_surplus_cap))
            }
            is TargetsAction.GroupShapePicked -> change { current ->
                val shape = when (action.shape) {
                    ShapeKind.Range -> GroupShape.Range
                    ShapeKind.Exact -> GroupShape.Exact
                    ShapeKind.Surplus -> if (TargetStyle.surplusAllowed(current.numbers.preset)) GroupShape.Surplus(0, 0) else null
                }
                shape?.let { current.targets.shapeGroup(action.group, it, burnKcal()) }
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
            TargetsAction.ExerciseAdjustmentTapped -> sendEvent(TargetsEvent.OpenIntegrations)
            TargetsAction.BackTapped -> sendEvent(TargetsEvent.Close)
        }
    }

    /**
     * Writes [kind] unless it is already the style. Checked again inside the write: a mode change
     * since the page drew must not let a deficit or surplus through where it is not offered, nor
     * one number or a surplus with the flag off. A new style starts on its own main number on
     * Today, and a card on Rings goes back to In and out once the goal is a range (13.6c).
     */
    private suspend fun pickStyle(kind: StyleKind) {
        settingsRepository.update { current ->
            val offered = TargetsStateBuilder.offered(StyleKind.of(current.targets.style), current.numbers.preset, goalsAndEnergy())
            if (kind !in offered || kind == StyleKind.of(current.targets.style)) return@update current
            val targets = when (kind) {
                StyleKind.Range -> current.targets.useRange()
                StyleKind.Exact -> current.targets.useExact()
                StyleKind.Deficit -> current.targets.useDeficit(burnKcal())
                StyleKind.Surplus -> current.targets.useSurplus(burnKcal())
                StyleKind.ByDay -> current.targets.useByDay()
            }
            val rings = current.todayCard == SummaryCardPreset.Rings && !TodayScreenSettingsStateBuilder.singleTarget(targets.style)
            current.copy(targets = targets, todayCard = if (rings) SummaryCardPreset.InOut else current.todayCard).forgetMainNumber()
        }
        editingGroup = null
    }

    private fun burnKcal(): Int? = inputs?.burned?.totalKcal

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
        val (settings, policy, healthConnected, burned) = inputs ?: return
        action.updateState {
            TargetsStateBuilder.build(
                settings,
                policy,
                burned = burned,
                editingGroup = editingGroup,
                editingMacros = editingMacros,
                healthConnected = healthConnected,
                goalsAndEnergy = goalsAndEnergy(),
                healthSource = health.source.takeIf { healthConnected },
            )
        }
    }

    private suspend fun toast(toast: Toast) = sendEvent(TargetsEvent.ShowToast(toast))
}

sealed interface TargetsEvent {
    data class ShowToast(val toast: Toast) : TargetsEvent
    data object OpenRestingEnergy : TargetsEvent
    data object OpenModes : TargetsEvent
    data object OpenIntegrations : TargetsEvent
    data object Close : TargetsEvent
}

sealed interface TargetsAction {
    /**
     * [healthConnected]: `release.health` on and a store connected, which is when exercise can move
     * the range. [burned]: today's, with `release.goalsAndEnergy` on and a resting source that answers.
     */
    data class InputsArrived(
        val settings: Settings,
        val policy: VisibilityPolicy,
        val healthConnected: Boolean = false,
        val burned: Burned? = null,
    ) : TargetsAction

    /** A row in the goal style list: written at once. */
    data class StylePicked(val kind: StyleKind) : TargetsAction
    data object RestingEnergyTapped : TargetsAction
    data object ActivityTapped : TargetsAction

    /** One step on [end] of the range, or of day group [group]. */
    data class StepTapped(val end: RangeEnd, val up: Boolean, val group: Int? = null) : TargetsAction
    data class DeficitStepped(val up: Boolean) : TargetsAction

    /** One step on one number: the goal under "One number", or day group [group]'s. */
    data class NumberStepped(val up: Boolean, val group: Int? = null) : TargetsAction

    /** One step on a surplus: the style's, or day group [group]'s. */
    data class SurplusStepped(val up: Boolean, val group: Int? = null) : TargetsAction
    data class GroupShapePicked(val group: Int, val shape: ShapeKind) : TargetsAction
    data class DayTapped(val group: Int, val day: DayOfWeek) : TargetsAction
    data object AddGroupTapped : TargetsAction
    data class EditGroupTapped(val group: Int) : TargetsAction
    data class RemoveGroupTapped(val group: Int) : TargetsAction
    data object MacroCardTapped : TargetsAction
    data class MacroModePicked(val mode: MacroMode) : TargetsAction
    data object ModesTapped : TargetsAction
    data object ExerciseAdjustmentTapped : TargetsAction
    data object BackTapped : TargetsAction
}
