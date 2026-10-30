package com.opson.features.settings.impl.targets

import androidx.lifecycle.viewModelScope
import com.opson.features.onboarding.OnboardingSetupEnabled
import com.opson.features.settings.FindSupportEnabled
import com.opson.features.settings.impl.Toast
import com.opson.features.settings.impl.energy.EnergyTopic
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
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.GroupShape
import com.opson.libraries.nutrition.MAX_GOAL_KCAL
import com.opson.libraries.nutrition.MAX_MACRO_G
import com.opson.libraries.nutrition.MacroPreset
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.RangeEnd
import com.opson.libraries.nutrition.StepBlock
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.goals.GoalRepository
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalTargets
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.BaselineInputs
import com.opson.libraries.nutrition.Recalculation
import com.opson.libraries.nutrition.fitMacrosTo
import com.opson.libraries.nutrition.stepMacro
import com.opson.libraries.nutrition.useMacroEnergy
import com.opson.libraries.nutrition.withMacroGrams
import com.opson.libraries.nutrition.withMacroPercent
import com.opson.libraries.nutrition.withPreset
import com.opson.libraries.ui.components.text.NumberInput
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_targets_ceiling_toast
import opson.libraries.resources.generated.resources.settings_targets_deficit_cap_toast
import opson.libraries.resources.generated.resources.settings_targets_floor_toast
import opson.libraries.resources.generated.resources.settings_targets_gap_toast
import opson.libraries.resources.generated.resources.settings_targets_macros_goal_floor_toast
import opson.libraries.resources.generated.resources.settings_targets_surplus_cap

/**
 * Targets. Every change runs inside [SettingsRepository.update] against the settings it is
 * about to replace, so the lowest and highest a goal goes and the deficit and surplus caps are
 * checked in the same step as the write: a refused step writes nothing and the toast says why.
 * Picking a goal style writes it at once (design sync 4, 13.2e). A low goal is never refused; the
 * state carries a note instead ([TargetsState.safety]).
 *
 * Different by day starts with two groups, every day in the first and none in the second, so the
 * days can be picked straight away. A tap on a day moves it into that group, or back to the first
 * ([Targets.toggleDay]); no tap can leave a day in no group or in two.
 *
 * With `release.goalsAndEnergy` on, today's Burned ([BurnedRepository]) is what a set deficit or
 * surplus is aimed at, and what one starts from when picked; without a health app it is resting
 * energy plus everyday activity. Off, nothing reads it, and the deficit and surplus are not
 * offered.
 *
 * The low-goal note links to Find support only with `release.findSupport`, as the pause sheet's
 * link does; without it the note stands alone.
 *
 * Typed fields (one number, a macro) keep what is typed on screen and write only once it is a
 * value the step rules allow, so typing "2150" writes once and "21" never reaches sync.
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
    private val findSupport: FindSupportEnabled,
    private val weightGoals: OnboardingSetupEnabled,
    private val goals: GoalRepository,
) : SEAViewModel<TargetsState, TargetsEvent, TargetsAction>(initialStateArg = TargetsState()) {

    private var inputs: TargetsAction.InputsArrived? = null
    private val today = calendar.today()

    // What is open on the page. Kept here rather than read back from state, because a write
    // lands as new inputs after the action that opened it.
    private var editingMacros: Boolean = false
    private var choosingActivity: Boolean = false
    private var drafts = TargetsDrafts()

    // A goal's dialogs: the one open, the edit waiting on its answer, and a recalculation the
    // person closed without answering, which isn't offered again until they come back.
    private var dialog: TargetsDialogKind? = null
    private var pending: ((Settings) -> Settings?)? = null
    private var recalculationClosed: Recalculation? = null

    init {
        // With release.health off the store is never asked, so the exercise row can't show.
        val connected = if (healthFlag()) health.status.map { it == HealthStatus.Connected } else flowOf(false)
        val burned = if (goalsAndEnergy()) burnedRepository.burned(today) else flowOf(null)
        val goal = if (weightGoals()) {
            combine(goals.recalculation(), goals.inputs()) { offer, inputs -> GoalSignals(offer, inputs?.weightKg, inputs) }
        } else {
            flowOf(GoalSignals())
        }
        viewModelScope.launch {
            combine(settingsRepository.observe(), policySource.policy, connected, burned, goal, TargetsAction::InputsArrived)
                .collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: TargetsAction) {
        forgetDrafts(action)
        when (action) {
            is TargetsAction.InputsArrived -> {
                inputs = action
                rebuild(action)
            }
            is TargetsAction.StylePicked -> pickStyle(action.kind)
            TargetsAction.RestingEnergyTapped -> sendEvent(TargetsEvent.OpenRestingEnergy)
            TargetsAction.ChangeGoalTapped -> changeGoal()
            TargetsAction.SeeGoalTapped -> sendEvent(TargetsEvent.OpenGoals(GoalBook.calorieGoal(settingsRepository.settings.value)?.id))
            TargetsAction.UseGoalTapped -> settingsRepository.update { GoalBook.resume(it) }
            TargetsAction.ChangeTargetTapped -> openDialog(TargetsDialogKind.ChangeTarget, action)
            TargetsAction.EditGoalChosen -> {
                closeDialog(action)
                sendEvent(TargetsEvent.OpenGoalEdit(GoalBook.calorieGoal(settingsRepository.settings.value)?.id))
            }
            TargetsAction.OwnTargetChosen -> {
                val edit = pending
                closeDialog(action)
                settingsRepository.update { GoalBook.override(it) }
                edit?.let { write(it) }
            }
            TargetsAction.PauseGoalChosen -> {
                val edit = pending
                closeDialog(action)
                settingsRepository.update { current ->
                    val goal = GoalBook.calorieGoal(current) ?: return@update current
                    val next = edit?.invoke(current) ?: current
                    GoalBook.pause(next, goal.id)
                }
            }
            TargetsAction.DialogDismissed -> {
                (shownDialog() as? TargetsDialogKind.Recalculate)?.let { recalculationClosed = it.recalculation }
                closeDialog(action)
            }
            TargetsAction.RecalculationAccepted -> answerRecalculation(action) { current, offer -> GoalBook.accept(current, offer, today) }
            TargetsAction.RecalculationKept -> answerRecalculation(action) { current, offer -> GoalBook.keep(current, offer, today) }
            TargetsAction.PaceInfoTapped -> openDialog(TargetsDialogKind.PaceInfo, action)
            is TargetsAction.ExplainTapped -> sendEvent(TargetsEvent.OpenExplainer(action.topic))
            TargetsAction.MicrosTapped -> sendEvent(TargetsEvent.OpenMicroTargets)
            TargetsAction.ActivityTapped -> if (inputs?.healthConnected == true) {
                sendEvent(TargetsEvent.OpenIntegrations)
            } else {
                choosingActivity = !choosingActivity
                rebuild(action)
            }
            is TargetsAction.EverydayActivityPicked -> {
                choosingActivity = false
                settingsRepository.update { it.copy(everydayActivity = action.level) }
                rebuild(action)
            }
            TargetsAction.ConnectHealthTapped -> sendEvent(TargetsEvent.OpenIntegrations)
            is TargetsAction.StepTapped -> step(action)
            is TargetsAction.DeficitStepped -> {
                var refused = false
                change { current -> current.targets.stepDeficit(action.up, action.group).also { refused = it == null } }
                if (refused && action.up) toast(Toast(Res.string.settings_targets_deficit_cap_toast))
            }
            is TargetsAction.NumberStepped -> {
                var refused = false
                change { current ->
                    current.targets.stepNumber(action.up, action.group, scaleMacros = current.macroMode == MacroMode.Ratios)
                        .also { refused = it == null }
                }
                if (refused) toast(Toast(if (action.up) Res.string.settings_targets_ceiling_toast else Res.string.settings_targets_floor_toast))
                rebuild(action)
            }
            is TargetsAction.NumberTyped -> typeNumber(action)
            is TargetsAction.FieldTyped -> typeField(action)
            is TargetsAction.FieldDone -> fieldDone(action)
            TargetsAction.NumberDone -> {
                drafts = drafts.copy(numberDone = drafts.number != null)
                rebuild(action)
            }
            is TargetsAction.SurplusStepped -> {
                var refused = false
                change { current -> current.targets.stepSurplus(action.up, action.group).also { refused = it == null } }
                if (refused && action.up) toast(Toast(Res.string.settings_targets_surplus_cap))
            }
            TargetsAction.StartFromBurnTapped -> change { current -> startFromBurn(current) }
            is TargetsAction.GroupShapePicked -> change { current ->
                val preset = current.numbers.preset
                val shape = when (action.shape) {
                    ShapeKind.Range -> GroupShape.Range
                    ShapeKind.Exact -> GroupShape.Exact
                    ShapeKind.Deficit -> GroupShape.Deficit(0, 0).takeIf { TargetStyle.deficitAllowed(preset) && goalsAndEnergy() }
                    ShapeKind.Surplus -> GroupShape.Surplus(0, 0).takeIf { TargetStyle.surplusAllowed(preset) && goalsAndEnergy() }
                }
                shape?.let { current.targets.shapeGroup(action.group, it, burnKcal()) }
            }
            is TargetsAction.DayTapped -> change { it.targets.toggleDay(action.day, action.group) }
            TargetsAction.AddGroupTapped -> change { it.targets.addDayGroup() }
            is TargetsAction.RemoveGroupTapped -> change { it.targets.removeDayGroup(action.group) }
            TargetsAction.FindSupportTapped -> if (findSupport()) sendEvent(TargetsEvent.OpenFindSupport)
            TargetsAction.MacroCardTapped -> {
                editingMacros = !editingMacros
                rebuild(action)
            }
            is TargetsAction.MacroModePicked -> {
                settingsRepository.update { it.copy(macroMode = action.mode) }
                rebuild(action)
            }
            is TargetsAction.MacroPresetPicked -> {
                change { current -> current.targets.withPreset(action.preset, macroGoalKcal(current)) }
                rebuild(action)
            }
            is TargetsAction.MacroStepped -> {
                change { current -> current.targets.stepMacro(action.metric, action.up, inPercent(current), macroGoalKcal(current)) }
                rebuild(action)
            }
            is TargetsAction.MacroTyped -> typeMacro(action)
            TargetsAction.MacrosFitTapped -> {
                change { current -> current.targets.fitMacrosTo(macroGoalKcal(current)) }
                rebuild(action)
            }
            TargetsAction.MacrosAsGoalTapped -> {
                var refused = false
                change { current -> current.targets.useMacroEnergy().also { refused = it == null } }
                if (refused) toast(Toast(Res.string.settings_targets_macros_goal_floor_toast))
                rebuild(action)
            }
            TargetsAction.ModesTapped -> sendEvent(TargetsEvent.OpenModes)
            TargetsAction.ExerciseAdjustmentTapped -> sendEvent(TargetsEvent.OpenIntegrations)
            TargetsAction.BackTapped -> sendEvent(TargetsEvent.Close)
        }
    }

    /**
     * Writes [kind] unless it is already the style. Checked again inside the write: a mode change
     * since the page drew must not let a deficit or surplus through where it is not offered, nor
     * one of the flag's styles with the flag off. A new style starts on its own main number on
     * Today, and a card on Rings goes back to In and out once the goal is a range (13.6c).
     */
    private suspend fun pickStyle(kind: StyleKind) {
        write { current ->
            val offered = TargetsStateBuilder.offered(StyleKind.of(current.targets.style), current.numbers.preset, goalsAndEnergy())
            if (kind !in offered || kind == StyleKind.of(current.targets.style)) return@write null
            val targets = when (kind) {
                StyleKind.Range -> current.targets.useRange()
                StyleKind.Exact -> current.targets.useExact()
                StyleKind.Deficit -> current.targets.useDeficit(burnKcal())
                StyleKind.Surplus -> current.targets.useSurplus(burnKcal())
                StyleKind.ByDay -> current.targets.useByDay().let { it.addDayGroup() ?: it }
            }
            current.withStyle(targets)
        }
    }

    /**
     * A typed field keeps its draft only while it is being typed in. Anything else the person
     * does can move the number under it (a step, a preset, a ratio-mode scale), so the field goes
     * back to showing what is saved.
     */
    private fun forgetDrafts(action: TargetsAction) {
        if (action is TargetsAction.InputsArrived) return
        if (action !is TargetsAction.MacroTyped) drafts = drafts.copy(macros = emptyMap())
        if (action !is TargetsAction.NumberTyped && action !is TargetsAction.NumberDone) drafts = drafts.copy(number = null, numberDone = false)
        if (action !is TargetsAction.FieldTyped && action !is TargetsAction.FieldDone) drafts = drafts.copy(fields = emptyMap())
    }

    /**
     * The goal card's button: a goal waiting on details opens the flow that asks for them; with no
     * goal yet, the editor's list of goals; otherwise the goals page.
     */
    private suspend fun changeGoal() {
        val settings = settingsRepository.settings.value
        val goal = GoalBook.calorieGoal(settings)
        when {
            goal == null -> sendEvent(TargetsEvent.OpenGoalEdit(null))
            goal.isActive && goal.setsTarget && goal.baseline == null -> sendEvent(TargetsEvent.OpenGoalSetup)
            else -> sendEvent(TargetsEvent.OpenGoals(goal.id))
        }
    }

    /**
     * A range end, a set amount or a day group's number, typed. The draft stays on screen; the
     * targets change only once the number is one a step could reach ([Targets.withEnd] and the
     * rest), so "18" on the way to "1875" writes nothing.
     */
    private suspend fun typeField(action: TargetsAction.FieldTyped) {
        val text = NumberInput.sanitize(action.text, allowDecimal = false).take(MAX_GOAL_DIGITS)
        drafts = drafts.copy(fields = mapOf(action.field to text))
        rebuild(action)
        val kcal = text.toIntOrNull() ?: return
        change { current -> typed(current, action.field, kcal) }
    }

    private fun typed(current: Settings, field: TypedField, kcal: Int): Targets? {
        val targets = current.targets
        val scale = current.macroMode == MacroMode.Ratios
        return when (field.kind) {
            TypedField.Kind.Low -> targets.withEnd(RangeEnd.Low, kcal, field.group, scale)
            TypedField.Kind.High -> targets.withEnd(RangeEnd.High, kcal, field.group, scale)
            TypedField.Kind.Deficit -> targets.withDeficit(kcal, field.group)
            TypedField.Kind.Surplus -> targets.withSurplus(kcal, field.group)
            TypedField.Kind.Number -> field.group?.let { targets.withGroupNumber(kcal, it) }
        }?.takeIf { it != targets }
    }

    /**
     * The keyboard's Done on a typed field: a number that couldn't be used says why, as a step that
     * can't go further does, and the field goes back to what is saved.
     */
    private suspend fun fieldDone(action: TargetsAction.FieldDone) {
        val text = drafts.fields[action.field]
        drafts = drafts.copy(fields = emptyMap())
        rebuild(action)
        val kcal = text?.toIntOrNull() ?: return
        val targets = settingsRepository.settings.value.targets
        val reason = when (action.field.kind) {
            TypedField.Kind.Low -> targets.endBlock(RangeEnd.Low, kcal, action.field.group)?.toast()
            TypedField.Kind.High -> targets.endBlock(RangeEnd.High, kcal, action.field.group)?.toast()
            TypedField.Kind.Deficit -> Res.string.settings_targets_deficit_cap_toast.takeIf { targets.withDeficit(kcal, action.field.group) == null }
            TypedField.Kind.Surplus -> Res.string.settings_targets_surplus_cap.takeIf { targets.withSurplus(kcal, action.field.group) == null }
            TypedField.Kind.Number -> when {
                kcal < targets.floorKcal -> Res.string.settings_targets_floor_toast
                kcal > MAX_GOAL_KCAL -> Res.string.settings_targets_ceiling_toast
                else -> null
            }
        }
        reason?.let { toast(Toast(it)) }
    }

    private fun StepBlock.toast() = when (this) {
        StepBlock.Floor -> Res.string.settings_targets_floor_toast
        StepBlock.Ceiling -> Res.string.settings_targets_ceiling_toast
        StepBlock.Gap -> Res.string.settings_targets_gap_toast
    }

    private fun Settings.withStyle(targets: Targets): Settings {
        val rings = todayCard == SummaryCardPreset.Rings && !TodayScreenSettingsStateBuilder.singleTarget(targets.style)
        return copy(targets = targets, todayCard = if (rings) SummaryCardPreset.InOut else todayCard).forgetMainNumber()
    }

    /**
     * A deficit or surplus that started at 0 with nothing known about the burn, started again now
     * that Burned is known: exactly what picking the style afresh would do, so it can't go past
     * what the goal already implies against today's burn.
     */
    private fun startFromBurn(current: Settings): Targets? {
        val burn = burnKcal() ?: return null
        return when (val style = current.targets.style) {
            is TargetStyle.Deficit -> current.targets.takeIf { style.startingKcal == 0 }?.useRange()?.useDeficit(burn)
            is TargetStyle.Surplus -> current.targets.takeIf { style.startingKcal == 0 }?.useRange()?.useSurplus(burn)
            else -> null
        }
    }

    private suspend fun typeNumber(action: TargetsAction.NumberTyped) {
        val text = NumberInput.sanitize(action.text, allowDecimal = false).take(MAX_GOAL_DIGITS)
        drafts = drafts.copy(number = text, numberDone = false)
        rebuild(action)
        val kcal = text.toIntOrNull() ?: return
        change { current ->
            val targets = current.targets
            if (targets.style != TargetStyle.Exact || kcal !in targets.floorKcal..MAX_GOAL_KCAL || kcal == targets.energyKcal) return@change null
            targets.withEnergy(kcal, scaleMacros = current.macroMode == MacroMode.Ratios)
        }
    }

    private suspend fun typeMacro(action: TargetsAction.MacroTyped) {
        val text = NumberInput.sanitize(action.text, allowDecimal = false).take(MAX_MACRO_DIGITS)
        drafts = drafts.copy(macros = drafts.macros + (action.metric to text))
        rebuild(action)
        val value = text.toIntOrNull() ?: return
        change { current ->
            if (inPercent(current)) {
                current.targets.takeIf { value <= PERCENT }?.withMacroPercent(action.metric, value, macroGoalKcal(current))
            } else {
                current.targets.takeIf { value <= MAX_MACRO_G }?.withMacroGrams(action.metric, value)
            }
        }
    }

    /** Percents are offered where calories show; otherwise a macro is always set in grams. */
    private fun inPercent(current: Settings): Boolean =
        current.macroMode == MacroMode.Ratios && inputs?.policy?.let(TargetsStateBuilder::percentsShown) == true

    private fun macroGoalKcal(current: Settings): Int = TargetsStateBuilder.macroGoalKcal(current, burnKcal(), today, goalsAndEnergy())

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
            StepBlock.Ceiling -> toast(Toast(Res.string.settings_targets_ceiling_toast))
            StepBlock.Gap -> toast(Toast(Res.string.settings_targets_gap_toast))
            null -> Unit
        }
    }

    /** Writes [transform]'s targets; null keeps what is there. Goes through [write]'s check. */
    private suspend fun change(transform: (Settings) -> Targets?) {
        write { current -> transform(current)?.let { current.copy(targets = it) } }
    }

    /**
     * Every change to the calorie target comes through here (docs/plans/goals-v2.md, "Locking").
     * With a goal setting the target, nothing moves until the person picks "Set my own target";
     * with a goal kept beside their own target, an edit that points against it asks first. The
     * edit waits in [pending] and runs on the answer, against the settings as they are then.
     */
    private suspend fun write(transform: (Settings) -> Settings?) {
        val current = settingsRepository.settings.value
        val next = transform(current) ?: return
        val ask = if (weightGoals()) gate(current, next) else null
        if (ask == null) {
            settingsRepository.update { transform(it) ?: it }
            return
        }
        pending = transform
        dialog = ask
        inputs?.let { rebuild(it) }
    }

    /** The dialog an edit from [current] to [next] needs first, or null when it can go ahead. */
    private fun gate(current: Settings, next: Settings): TargetsDialogKind? {
        if (energyOf(current.targets) == energyOf(next.targets)) return null
        // No goal card there, so nothing to ask about (TargetsStateBuilder.goalCard).
        if (current.onboarding.hard) return null
        val goal = GoalBook.calorieGoal(current)?.takeIf { it.isActive } ?: return null
        if (goal.drivesTarget) return TargetsDialogKind.ChangeTarget
        val preset = current.numbers.preset
        if (!GoalTargets.contradicts(goal, next.targets, preset) || GoalTargets.contradicts(goal, current.targets, preset)) return null
        val delta = goal.type.deltaKcal ?: 0
        val direction = (goal.type as? GoalType.Weight)?.direction ?: when {
            delta < 0 -> GoalDirection.Lose
            delta > 0 -> GoalDirection.Gain
            else -> GoalDirection.Maintain
        }
        return TargetsDialogKind.AgainstGoal(direction)
    }

    /** What a calorie target is made of; macros alone never need a goal's say. */
    private fun energyOf(targets: Targets) = listOf(targets.style, targets.energyKcal, targets.minKcal, targets.maxKcal)

    private suspend fun openDialog(kind: TargetsDialogKind, action: TargetsAction) {
        dialog = kind
        rebuild(action)
    }

    private suspend fun closeDialog(action: TargetsAction) {
        dialog = null
        pending = null
        rebuild(action)
    }

    /** The dialog on show: the one opened here, else a recalculation not yet answered or closed. */
    private fun shownDialog(): TargetsDialogKind? {
        dialog?.let { return it }
        val offer = inputs?.goal?.recalculation?.takeIf { weightGoals() && it != recalculationClosed } ?: return null
        return TargetsDialogKind.Recalculate(offer)
    }

    private suspend fun answerRecalculation(action: TargetsAction, answer: (Settings, Recalculation) -> Settings) {
        val offer = (shownDialog() as? TargetsDialogKind.Recalculate)?.recalculation ?: return
        recalculationClosed = offer
        settingsRepository.update { answer(it, offer) }
        rebuild(action)
    }

    private suspend fun rebuild(action: TargetsAction) {
        val (settings, policy, healthConnected, burned, goal) = inputs ?: return
        action.updateState {
            TargetsStateBuilder.build(
                settings,
                policy,
                burned = burned,
                editingMacros = editingMacros,
                healthConnected = healthConnected,
                goalsAndEnergy = goalsAndEnergy(),
                healthSource = health.source.takeIf { healthConnected },
                today = today,
                choosingActivity = choosingActivity,
                drafts = drafts,
                findSupport = findSupport(),
                weightGoals = weightGoals(),
                weightKg = goal.weightKg,
                dialog = shownDialog(),
                inputs = goal.inputs,
            )
        }
    }

    private suspend fun toast(toast: Toast) = sendEvent(TargetsEvent.ShowToast(toast))

    private companion object {
        const val PERCENT = 100
        const val MAX_MACRO_DIGITS = 3
        const val MAX_GOAL_DIGITS = 4
    }
}

/**
 * What Targets hears from the goal repository: a recalculation to offer, the latest weigh-in, and a
 * typical day right now, for where the person's own target leads.
 */
data class GoalSignals(val recalculation: Recalculation? = null, val weightKg: Double? = null, val inputs: BaselineInputs? = null)

sealed interface TargetsEvent {
    data class ShowToast(val toast: Toast) : TargetsEvent
    data object OpenRestingEnergy : TargetsEvent

    /** An (i) sheet: Burned (TDEE), or how a deficit or surplus works. */
    data class OpenExplainer(val topic: EnergyTopic) : TargetsEvent

    /** Vitamins and minerals: their references and the four on Today. */
    data object OpenMicroTargets : TargetsEvent
    data object OpenModes : TargetsEvent
    data object OpenIntegrations : TargetsEvent

    /** Find support, from the note under a low goal or a fast pace. */
    data object OpenFindSupport : TargetsEvent

    /** The goal flow, which asks for the details a goal's target is worked out from. */
    data object OpenGoalSetup : TargetsEvent

    /** The goals page, with [goalId] first. */
    data class OpenGoals(val goalId: String?) : TargetsEvent

    /** The goal editor: the goal with [goalId], or a new goal for null. */
    data class OpenGoalEdit(val goalId: String?) : TargetsEvent
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
        val goal: GoalSignals = GoalSignals(),
    ) : TargetsAction

    /** A row in the goal style list: written at once. */
    data class StylePicked(val kind: StyleKind) : TargetsAction
    data object RestingEnergyTapped : TargetsAction
    data class ExplainTapped(val topic: EnergyTopic) : TargetsAction
    data object MicrosTapped : TargetsAction

    /** "Change goal", or "Set a goal", on the goal's card. */
    data object ChangeGoalTapped : TargetsAction

    /** The goal card's row while it sets the target: the goals page. */
    data object SeeGoalTapped : TargetsAction

    /** "Use my goal": the goal sets the target again, from the typical day it already has. */
    data object UseGoalTapped : TargetsAction

    /** "Change" on the goal's locked target: asks first. */
    data object ChangeTargetTapped : TargetsAction

    /** In the Change dialog: the goal flow, so the target follows the goal. */
    data object EditGoalChosen : TargetsAction

    /** In the Change dialog: the goal stays and stops setting the target. */
    data object OwnTargetChosen : TargetsAction

    /** An edit pointing against the goal, confirmed: it is made and the goal is paused. */
    data object PauseGoalChosen : TargetsAction

    /** A dialog closed without a choice. A recalculation closed this way waits for the next visit. */
    data object DialogDismissed : TargetsAction
    data object RecalculationAccepted : TargetsAction
    data object RecalculationKept : TargetsAction

    /** The (i) on the goal's pace note. */
    data object PaceInfoTapped : TargetsAction

    /** The Activity row: the health app's page when one is connected, else everyday activity's choices. */
    data object ActivityTapped : TargetsAction
    data class EverydayActivityPicked(val level: EverydayActivity) : TargetsAction
    data object ConnectHealthTapped : TargetsAction

    /** One step on [end] of the range, or of day group [group]. */
    data class StepTapped(val end: RangeEnd, val up: Boolean, val group: Int? = null) : TargetsAction

    /** One step on a deficit: the style's, or day group [group]'s. */
    data class DeficitStepped(val up: Boolean, val group: Int? = null) : TargetsAction

    /** One step on one number: the goal under "One number", or day group [group]'s. */
    data class NumberStepped(val up: Boolean, val group: Int? = null) : TargetsAction

    /** The one number's field, as typed. */
    data class NumberTyped(val text: String) : TargetsAction

    /** The keyboard's Done on the one number's field: a number under the minimum now says so. */
    data object NumberDone : TargetsAction

    /** A range end, a set amount or a day group's number, as typed. */
    data class FieldTyped(val field: TypedField, val text: String) : TargetsAction

    /** The keyboard's Done on [field]: a number that couldn't be used says why. */
    data class FieldDone(val field: TypedField) : TargetsAction

    /** One step on a surplus: the style's, or day group [group]'s. */
    data class SurplusStepped(val up: Boolean, val group: Int? = null) : TargetsAction

    /** "Start from today's burn", for a deficit or surplus that began at 0 with nothing known. */
    data object StartFromBurnTapped : TargetsAction
    data class GroupShapePicked(val group: Int, val shape: ShapeKind) : TargetsAction

    /** [day] in group [group]'s card: into that group, or back to the first ([Targets.toggleDay]). */
    data class DayTapped(val group: Int, val day: DayOfWeek) : TargetsAction
    data object AddGroupTapped : TargetsAction

    /** Group [group] goes, its days back to the first. The first group can't be removed. */
    data class RemoveGroupTapped(val group: Int) : TargetsAction

    /** The link in the low-goal or fast-pace note. */
    data object FindSupportTapped : TargetsAction
    data object MacroCardTapped : TargetsAction
    data class MacroModePicked(val mode: MacroMode) : TargetsAction
    data class MacroPresetPicked(val preset: MacroPreset) : TargetsAction

    /** One step on [metric]: five grams, or five percent of the goal in percent mode. */
    data class MacroStepped(val metric: Metric, val up: Boolean) : TargetsAction
    data class MacroTyped(val metric: Metric, val text: String) : TargetsAction

    /** The calorie goal drives: the macros scale to fit it. */
    data object MacrosFitTapped : TargetsAction

    /** The macros drive: the calorie goal becomes what they add up to. */
    data object MacrosAsGoalTapped : TargetsAction
    data object ModesTapped : TargetsAction
    data object ExerciseAdjustmentTapped : TargetsAction
    data object BackTapped : TargetsAction
}
