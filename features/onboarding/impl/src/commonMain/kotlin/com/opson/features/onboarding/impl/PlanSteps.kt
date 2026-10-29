package com.opson.features.onboarding.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.ui.HeightField
import com.opson.libraries.diary.ui.detail
import com.opson.libraries.diary.ui.label
import com.opson.libraries.health.HealthSource
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.nutrition.ui.testTag
import com.opson.libraries.ui.components.ChevronLink
import com.opson.libraries.ui.components.ChoiceRow
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.Stepper
import com.opson.libraries.ui.components.TapField
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.dialog.DatePickerDialog
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.Dimension
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.everyday_activity_title
import opson.libraries.resources.generated.resources.integrations_apple_health
import opson.libraries.resources.generated.resources.plan_overview_breastfeeding_extra
import opson.libraries.resources.generated.resources.plan_overview_pregnancy
import opson.libraries.resources.generated.resources.plan_overview_working
import opson.libraries.resources.generated.resources.settings_energy_burned
import opson.libraries.resources.generated.resources.settings_energy_resting
import opson.libraries.resources.generated.resources.integrations_health_connect
import opson.libraries.resources.generated.resources.onboarding_body_height_hint_cm
import opson.libraries.resources.generated.resources.onboarding_body_height_hint_ft_in
import opson.libraries.resources.generated.resources.onboarding_body_weight_hint_kg
import opson.libraries.resources.generated.resources.onboarding_body_weight_hint_lb
import opson.libraries.resources.generated.resources.onboarding_continue
import opson.libraries.resources.generated.resources.onboarding_finishing
import opson.libraries.resources.generated.resources.plan_activity_body
import opson.libraries.resources.generated.resources.plan_activity_connect
import opson.libraries.resources.generated.resources.plan_activity_connect_note
import opson.libraries.resources.generated.resources.plan_activity_connected
import opson.libraries.resources.generated.resources.plan_activity_connecting
import opson.libraries.resources.generated.resources.plan_activity_title
import opson.libraries.resources.generated.resources.plan_birthday_dialog
import opson.libraries.resources.generated.resources.plan_birthday_label
import opson.libraries.resources.generated.resources.plan_birthday_note
import opson.libraries.resources.generated.resources.plan_birthday_pick
import opson.libraries.resources.generated.resources.plan_commit_body
import opson.libraries.resources.generated.resources.plan_commit_body_gentle
import opson.libraries.resources.generated.resources.plan_commit_footnote
import opson.libraries.resources.generated.resources.plan_commit_not_now
import opson.libraries.resources.generated.resources.plan_commit_title
import opson.libraries.resources.generated.resources.plan_commit_yes
import opson.libraries.resources.generated.resources.plan_date_cancel
import opson.libraries.resources.generated.resources.plan_date_ok
import opson.libraries.resources.generated.resources.plan_goal_body
import opson.libraries.resources.generated.resources.plan_goal_current
import opson.libraries.resources.generated.resources.plan_goal_find_support
import opson.libraries.resources.generated.resources.plan_goal_gain
import opson.libraries.resources.generated.resources.plan_goal_gain_detail
import opson.libraries.resources.generated.resources.plan_goal_hint_above
import opson.libraries.resources.generated.resources.plan_goal_hint_below
import opson.libraries.resources.generated.resources.plan_goal_hint_kg
import opson.libraries.resources.generated.resources.plan_goal_hint_lb
import opson.libraries.resources.generated.resources.plan_goal_hint_needs_weight
import opson.libraries.resources.generated.resources.plan_goal_lose
import opson.libraries.resources.generated.resources.plan_goal_lose_detail
import opson.libraries.resources.generated.resources.plan_goal_low_note
import opson.libraries.resources.generated.resources.plan_goal_maintain
import opson.libraries.resources.generated.resources.plan_goal_maintain_detail
import opson.libraries.resources.generated.resources.plan_goal_target
import opson.libraries.resources.generated.resources.plan_goal_title
import opson.libraries.resources.generated.resources.plan_goal_track
import opson.libraries.resources.generated.resources.plan_goal_track_detail
import opson.libraries.resources.generated.resources.plan_overview_background
import opson.libraries.resources.generated.resources.plan_overview_body
import opson.libraries.resources.generated.resources.plan_overview_breastfeeding
import opson.libraries.resources.generated.resources.plan_overview_continue
import opson.libraries.resources.generated.resources.plan_overview_daily
import opson.libraries.resources.generated.resources.plan_overview_daily_value
import opson.libraries.resources.generated.resources.plan_overview_estimate
import opson.libraries.resources.generated.resources.plan_overview_forecast
import opson.libraries.resources.generated.resources.plan_overview_forecast_note
import opson.libraries.resources.generated.resources.plan_overview_gentle_body
import opson.libraries.resources.generated.resources.plan_overview_gentle_title
import opson.libraries.resources.generated.resources.plan_overview_goal
import opson.libraries.resources.generated.resources.plan_overview_keep
import opson.libraries.resources.generated.resources.plan_overview_less
import opson.libraries.resources.generated.resources.plan_overview_more
import opson.libraries.resources.generated.resources.plan_overview_needs_details
import opson.libraries.resources.generated.resources.plan_overview_no_goal
import opson.libraries.resources.generated.resources.plan_overview_pregnant
import opson.libraries.resources.generated.resources.plan_overview_same
import opson.libraries.resources.generated.resources.plan_overview_stage_care
import opson.libraries.resources.generated.resources.plan_overview_title
import opson.libraries.resources.generated.resources.plan_overview_unchanged
import opson.libraries.resources.generated.resources.plan_profile_body
import opson.libraries.resources.generated.resources.plan_profile_body_gentle
import opson.libraries.resources.generated.resources.plan_profile_title
import opson.libraries.resources.generated.resources.plan_rate_body
import opson.libraries.resources.generated.resources.plan_rate_estimate
import opson.libraries.resources.generated.resources.plan_rate_estimate_plain
import opson.libraries.resources.generated.resources.plan_rate_fastest
import opson.libraries.resources.generated.resources.plan_rate_forecast
import opson.libraries.resources.generated.resources.plan_rate_gain
import opson.libraries.resources.generated.resources.plan_rate_kcal_gain
import opson.libraries.resources.generated.resources.plan_rate_kcal_lose
import opson.libraries.resources.generated.resources.plan_rate_lose
import opson.libraries.resources.generated.resources.plan_rate_none
import opson.libraries.resources.generated.resources.plan_rate_title
import opson.libraries.resources.generated.resources.plan_sex_breastfeeding
import opson.libraries.resources.generated.resources.plan_sex_female
import opson.libraries.resources.generated.resources.plan_sex_inclusive
import opson.libraries.resources.generated.resources.plan_sex_label
import opson.libraries.resources.generated.resources.plan_sex_male
import opson.libraries.resources.generated.resources.plan_sex_note_binary
import opson.libraries.resources.generated.resources.plan_sex_note_breastfeeding
import opson.libraries.resources.generated.resources.plan_sex_note_pregnant
import opson.libraries.resources.generated.resources.plan_sex_note_unsaid
import opson.libraries.resources.generated.resources.plan_sex_pregnant
import opson.libraries.resources.generated.resources.plan_sex_unsaid
import opson.libraries.resources.generated.resources.plan_weight_label
import opson.libraries.resources.generated.resources.plan_weight_note
import opson.libraries.resources.generated.resources.settings_look_weight_kg
import opson.libraries.resources.generated.resources.settings_look_weight_lb
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Everything the goal flow's steps draw, built once from the answers ([PlanViews.all]). Every
 * amount in it went through the mode's policy.
 */
data class PlanView(
    val steps: List<PlanStep>,
    val profile: ProfileView,
    val activity: ActivityView,
    val goal: GoalView,
    val rate: RateView,
    val overview: OverviewView,
    /** The steps whose Continue waits for an answer: the goal without a usable target. */
    val blocked: Set<PlanStep>,
    val hard: Boolean,
    val today: LocalDate,
)

fun PlanViews.all(answers: PlanAnswers, context: PlanContext, current: Settings): PlanView {
    val steps = PlanFlow.steps(answers, context)
    return PlanView(
        steps = steps,
        profile = profile(answers, context),
        activity = activity(answers, context),
        goal = goal(answers, context),
        rate = rate(answers, context),
        overview = overview(answers, context, current),
        blocked = steps.filterNot { PlanFlow.canContinue(it, answers, context) }.toSet(),
        hard = context.hard,
        today = context.today,
    )
}

/**
 * One of the goal flow's steps, in onboarding's layout: the top bar (Back, and Skip on the steps
 * that can be skipped), the step itself, and the pinned button. The commitment step has its own
 * two buttons and no Skip: "Not now" is the quiet way through.
 */
@Composable
internal fun ColumnScope.PlanStepContent(
    step: PlanStep,
    view: PlanView,
    busy: Boolean,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    onCommit: (Boolean) -> Unit,
    onPlan: (PlanAction) -> Unit,
) {
    val skippable = step != PlanStep.Overview && step != PlanStep.Commit
    OnboardingTopBar(canGoBack = canGoBack, onBack = onBack, canSkip = !busy, showSkip = skippable, onSkip = onSkip)
    ScrollingBody {
        when (step) {
            PlanStep.Profile -> ProfileStep(view.profile, view.hard, view.today, busy, onPlan)
            PlanStep.Activity -> ActivityStep(view.activity, busy, onPlan)
            PlanStep.Goal -> GoalStep(view.goal, busy, onPlan)
            PlanStep.Rate -> RateStep(view.rate, busy, onPlan)
            PlanStep.Overview -> OverviewStep(view.overview)
            PlanStep.Commit -> CommitStep(view.hard)
        }
    }
    if (step == PlanStep.Commit) {
        CommitButtons(busy = busy, onCommit = onCommit)
    } else {
        PinnedPrimary(
            label = stringResource(
                when {
                    busy -> Res.string.onboarding_finishing
                    step == PlanStep.Overview -> Res.string.plan_overview_continue
                    else -> Res.string.onboarding_continue
                },
            ),
            enabled = !busy && step !in view.blocked,
            onClick = onContinue,
        )
    }
}

// ---------- 1. Profile ----------

@Composable
private fun ProfileStep(view: ProfileView, hard: Boolean, today: LocalDate, busy: Boolean, onPlan: (PlanAction) -> Unit) {
    val answer = view.answer
    var pickingDate by remember { mutableStateOf(false) }
    StepTitle(
        title = stringResource(Res.string.plan_profile_title),
        body = stringResource(if (hard) Res.string.plan_profile_body_gentle else Res.string.plan_profile_body),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Dimension.D700)) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
            FieldLabel(stringResource(Res.string.plan_sex_label))
            SexChoice.entries.forEach { choice ->
                ChoiceRow(
                    label = stringResource(choice.label),
                    selected = answer.sex == choice,
                    onClick = { onPlan(PlanAction.SexPicked(choice)) },
                    role = Role.RadioButton,
                    enabled = !busy,
                )
            }
            answer.sex?.let { Note(stringResource(it.note), live = true) }
            Note(stringResource(Res.string.plan_sex_inclusive))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D200)) {
            FieldLabel(stringResource(Res.string.plan_birthday_label))
            TapField(
                text = view.birthDateText ?: stringResource(Res.string.plan_birthday_pick),
                muted = view.birthDateText == null,
                onClick = { if (!busy) pickingDate = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Note(stringResource(Res.string.plan_birthday_note))
        }
        HintedField(
            hint = (if (answer.height.unit == HeightUnit.FtIn) Res.string.onboarding_body_height_hint_ft_in else Res.string.onboarding_body_height_hint_cm)
                .takeIf { view.heightInvalid },
        ) {
            HeightField(
                input = answer.height,
                onUnitPicked = { onPlan(PlanAction.HeightUnitPicked(it)) },
                onCmTyped = { onPlan(PlanAction.HeightCmTyped(it)) },
                onFeetTyped = { onPlan(PlanAction.HeightFeetTyped(it)) },
                onInchesTyped = { onPlan(PlanAction.HeightInchesTyped(it)) },
                enabled = !busy,
                imeAction = if (view.weightShown) ImeAction.Next else ImeAction.Done,
            )
        }
        if (view.weightShown) {
            WeightField(
                label = stringResource(Res.string.plan_weight_label),
                input = answer.weight,
                invalid = view.weightInvalid,
                enabled = !busy,
                onUnit = { onPlan(PlanAction.WeightUnitPicked(it)) },
                onTyped = { onPlan(PlanAction.WeightTyped(it)) },
            )
            Note(stringResource(Res.string.plan_weight_note))
        }
    }
    if (pickingDate) {
        DatePickerDialog(
            title = stringResource(Res.string.plan_birthday_dialog),
            initial = answer.birthDate,
            opensOn = today.minus(DatePeriod(years = OPENS_AT_AGE)),
            range = today.minus(DatePeriod(years = PlanFlow.AGES.last))..today.minus(DatePeriod(years = PlanFlow.AGES.first)),
            okLabel = stringResource(Res.string.plan_date_ok),
            cancelLabel = stringResource(Res.string.plan_date_cancel),
            onPicked = {
                onPlan(PlanAction.BirthDatePicked(it))
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

/** Where the birthday picker opens with nothing picked: thirty years back, a short scroll from most. */
private const val OPENS_AT_AGE = 30

/** Weight, in kilograms or pounds, with the switch between them, and a hint when it is [invalid]. */
@Composable
internal fun WeightField(
    label: String,
    input: WeightInput,
    invalid: Boolean,
    enabled: Boolean,
    onUnit: ((WeightUnit) -> Unit)?,
    onTyped: (String) -> Unit,
) {
    val pounds = input.unit == WeightUnit.Lb
    Column(verticalArrangement = Arrangement.spacedBy(Dimension.D200)) {
        FieldLabel(label)
        if (onUnit != null) {
            SegmentedControl(
                options = WeightUnit.entries,
                selected = input.unit,
                onSelect = { if (enabled) onUnit(it) },
                label = { stringResource(if (it == WeightUnit.Lb) Res.string.settings_look_weight_lb else Res.string.settings_look_weight_kg) },
            )
        }
        HintedField(hint = (if (pounds) Res.string.onboarding_body_weight_hint_lb else Res.string.onboarding_body_weight_hint_kg).takeIf { invalid }) {
            NumberField(
                value = input.text,
                onValueChange = onTyped,
                unit = stringResource(if (pounds) Res.string.settings_look_weight_lb else Res.string.settings_look_weight_kg),
                enabled = enabled,
                modifier = Modifier.testTag(Metric.Weight.testTag()),
            )
        }
    }
}

private val SexChoice.label: StringResource
    get() = when (this) {
        SexChoice.Female -> Res.string.plan_sex_female
        SexChoice.Male -> Res.string.plan_sex_male
        SexChoice.Pregnant -> Res.string.plan_sex_pregnant
        SexChoice.Breastfeeding -> Res.string.plan_sex_breastfeeding
        SexChoice.Unsaid -> Res.string.plan_sex_unsaid
    }

/** What picking it does, honestly: it only changes the estimate, and pregnancy keeps weight change out. */
private val SexChoice.note: StringResource
    get() = when (this) {
        SexChoice.Female, SexChoice.Male -> Res.string.plan_sex_note_binary
        SexChoice.Pregnant -> Res.string.plan_sex_note_pregnant
        SexChoice.Breastfeeding -> Res.string.plan_sex_note_breastfeeding
        SexChoice.Unsaid -> Res.string.plan_sex_note_unsaid
    }

// ---------- 2. Activity ----------

@Composable
private fun ActivityStep(view: ActivityView, busy: Boolean, onPlan: (PlanAction) -> Unit) {
    StepTitle(title = stringResource(Res.string.plan_activity_title), body = stringResource(Res.string.plan_activity_body))
    when (val health = view.health) {
        is HealthOffer.Connected -> EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding) {
            IconLine(AppIcons.Favorite, AppTheme.colors.accent, stringResource(Res.string.plan_activity_connected, health.source.name()))
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
            view.levels.forEach { level ->
                ChoiceRow(
                    label = stringResource(level.label),
                    selected = level == view.selected,
                    onClick = { onPlan(PlanAction.ActivityPicked(level)) },
                    role = Role.RadioButton,
                    enabled = !busy,
                    detail = stringResource(level.detail),
                )
            }
            if (health is HealthOffer.CanConnect) {
                Spacer(Modifier.height(Dimension.D500))
                Note(stringResource(Res.string.plan_activity_connect_note, health.source.name()))
                ButtonSecondary(
                    onClick = { onPlan(PlanAction.ConnectHealthTapped) },
                    enabled = !busy && !health.connecting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (health.connecting) Res.string.plan_activity_connecting else Res.string.plan_activity_connect,
                            health.source.name(),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthSource.name(): String = stringResource(
    when (this) {
        HealthSource.AppleHealth -> Res.string.integrations_apple_health
        HealthSource.HealthConnect -> Res.string.integrations_health_connect
    },
)

// ---------- 3. Goal ----------

@Composable
private fun GoalStep(view: GoalView, busy: Boolean, onPlan: (PlanAction) -> Unit) {
    StepTitle(title = stringResource(Res.string.plan_goal_title), body = stringResource(Res.string.plan_goal_body))
    Column(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
        view.directions.forEach { direction ->
            ChoiceRow(
                label = stringResource(direction.label),
                selected = direction == view.direction,
                onClick = { onPlan(PlanAction.DirectionPicked(direction)) },
                role = Role.RadioButton,
                enabled = !busy,
                detail = stringResource(direction.detail),
            )
        }
    }
    if (view.direction.changesWeight && view.targetShown) {
        Spacer(Modifier.height(Dimension.D700))
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
            WeightField(
                label = stringResource(Res.string.plan_goal_target),
                input = view.target,
                invalid = false,
                enabled = !busy && view.hint != TargetHint.NeedsWeight,
                onUnit = null,
                onTyped = { onPlan(PlanAction.TargetTyped(it)) },
            )
            (view.current as? Display.Shown)?.let {
                Text(
                    text = stringResource(Res.string.plan_goal_current, it.text),
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                    modifier = Modifier.testTag(Metric.Weight.testTag()),
                )
            }
            view.hint?.let { hint -> Note(stringResource(hint.text(view.target.unit)), live = true) }
            if (view.belowHealthy) {
                EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding, verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
                    Text(text = stringResource(Res.string.plan_goal_low_note), typography = AppTheme.type.body, color = AppTheme.colors.ink)
                    ChevronLink(text = stringResource(Res.string.plan_goal_find_support), onClick = { onPlan(PlanAction.FindSupportTapped) })
                }
            }
        }
    }
}

private val GoalDirection.label: StringResource
    get() = when (this) {
        GoalDirection.Lose -> Res.string.plan_goal_lose
        GoalDirection.Maintain -> Res.string.plan_goal_maintain
        GoalDirection.Gain -> Res.string.plan_goal_gain
        GoalDirection.JustTrack, GoalDirection.None -> Res.string.plan_goal_track
    }

private val GoalDirection.detail: StringResource
    get() = when (this) {
        GoalDirection.Lose -> Res.string.plan_goal_lose_detail
        GoalDirection.Maintain -> Res.string.plan_goal_maintain_detail
        GoalDirection.Gain -> Res.string.plan_goal_gain_detail
        GoalDirection.JustTrack, GoalDirection.None -> Res.string.plan_goal_track_detail
    }

private fun TargetHint.text(unit: WeightUnit): StringResource = when (this) {
    TargetHint.NeedsWeight -> Res.string.plan_goal_hint_needs_weight
    TargetHint.NotAWeight -> if (unit == WeightUnit.Lb) Res.string.plan_goal_hint_lb else Res.string.plan_goal_hint_kg
    TargetHint.AboveCurrent -> Res.string.plan_goal_hint_above
    TargetHint.BelowCurrent -> Res.string.plan_goal_hint_below
}

// ---------- 4. Pace ----------

@Composable
private fun RateStep(view: RateView, busy: Boolean, onPlan: (PlanAction) -> Unit) {
    StepTitle(title = stringResource(Res.string.plan_rate_title), body = stringResource(Res.string.plan_rate_body))
    val rate = view.rate
    if (rate == null) {
        if (view.noRoom) Note(stringResource(Res.string.plan_rate_none))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
        FieldLabel(stringResource(if (view.direction == GoalDirection.Gain) Res.string.plan_rate_gain else Res.string.plan_rate_lose))
        Stepper(
            amount = rate,
            onDecrease = { onPlan(PlanAction.RateStepped(faster = false)) },
            onIncrease = { onPlan(PlanAction.RateStepped(faster = true)) },
            canDecrease = view.canSlower && !busy,
            canIncrease = view.canFaster && !busy,
            amountModifier = Modifier.testTag(Metric.Weight.testTag()),
        )
        (view.dailyKcal as? Display.Shown)?.let {
            Text(
                text = stringResource(if (view.direction == GoalDirection.Gain) Res.string.plan_rate_kcal_gain else Res.string.plan_rate_kcal_lose, it.text),
                typography = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag(Metric.EnergyOut.testTag()),
            )
        }
        view.forecast?.let {
            Text(
                text = stringResource(Res.string.plan_rate_forecast, it),
                typography = AppTheme.type.body,
                color = AppTheme.colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (view.atFastest) Note(stringResource(Res.string.plan_rate_fastest))
        // The rule's own numbers are calorie amounts: only where calories show.
        Note(stringResource(if (view.dailyKcal is Display.Shown) Res.string.plan_rate_estimate else Res.string.plan_rate_estimate_plain))
    }
}

// ---------- 5. Overview ----------

@Composable
private fun OverviewStep(view: OverviewView) {
    if (view.kind == OverviewKind.Gentle) {
        StepTitle(title = stringResource(Res.string.plan_overview_gentle_title), body = stringResource(Res.string.plan_overview_gentle_body))
        return
    }
    StepTitle(title = stringResource(Res.string.plan_overview_title), body = stringResource(Res.string.plan_overview_body))
    val energy = view.energy as? Display.Shown
    val goalWeight = view.goalWeight as? Display.Shown
    val rows = buildList {
        goalWeight?.let {
            add(stringResource(if (view.kind == OverviewKind.Maintain) Res.string.plan_overview_keep else Res.string.plan_overview_goal) to (it.text to Metric.Weight))
        }
        view.forecast?.let { add(stringResource(Res.string.plan_overview_forecast) to (it to null)) }
        energy?.let { add(stringResource(Res.string.plan_overview_daily) to (stringResource(Res.string.plan_overview_daily_value, it.text) to Metric.Calories)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Dimension.D600)) {
        if (rows.isNotEmpty()) {
            EdgeCard(edgeToEdge = false, contentPadding = PaddingValues(horizontal = Dimension.D800, vertical = Dimension.D300)) {
                rows.forEachIndexed { index, (label, value) ->
                    OverviewRow(label = label, value = value.first, metric = value.second)
                    if (index < rows.lastIndex) HorizontalDivider()
                }
            }
        }
        if (view.compared) {
            val difference = view.difference as? Display.Shown
            Text(
                text = when {
                    difference == null -> stringResource(Res.string.plan_overview_same)
                    view.differenceSign < 0 -> stringResource(Res.string.plan_overview_less, difference.text)
                    else -> stringResource(Res.string.plan_overview_more, difference.text)
                },
                typography = AppTheme.type.bodyBold,
                color = AppTheme.colors.inkSoft,
                modifier = Modifier.testTag(Metric.EnergyOut.testTag()),
            )
        }
        (view.burned as? Display.Shown)?.let { burned -> WorkingCard(view, burned) }
        when {
            view.kind == OverviewKind.Stage -> {
                Note(stringResource(if (view.stage == BodyStage.Pregnant) Res.string.plan_overview_pregnant else Res.string.plan_overview_breastfeeding))
                Note(stringResource(Res.string.plan_overview_stage_care))
            }
            view.needsDetails -> Note(stringResource(Res.string.plan_overview_needs_details))
            view.kind == OverviewKind.NoGoal && energy != null -> Note(stringResource(Res.string.plan_overview_no_goal))
            view.kind == OverviewKind.NoGoal -> Note(stringResource(Res.string.plan_overview_unchanged))
            energy == null -> Note(stringResource(Res.string.plan_overview_background))
        }
        if (view.forecast != null) Note(stringResource(Res.string.plan_overview_forecast_note))
        if (energy != null) Note(stringResource(Res.string.plan_overview_estimate))
    }
}

/**
 * A typical day's working, in the energy words Opson uses everywhere: resting energy (BMR), plus
 * everyday activity, plus what pregnancy or breastfeeding needs, is the total burned (TDEE).
 */
@Composable
private fun WorkingCard(view: OverviewView, burned: Display.Shown) {
    val rows = buildList {
        (view.resting as? Display.Shown)?.let { add(stringResource(Res.string.settings_energy_resting) to it.text) }
        (view.everyday as? Display.Shown)?.let { add(stringResource(Res.string.everyday_activity_title) to it.text) }
        (view.stageExtra as? Display.Shown)?.let {
            val label = if (view.stage == BodyStage.Pregnant) Res.string.plan_overview_pregnancy else Res.string.plan_overview_breastfeeding_extra
            add(stringResource(label) to it.text)
        }
        add(stringResource(Res.string.settings_energy_burned) to burned.text)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Dimension.D200)) {
        FieldLabel(stringResource(Res.string.plan_overview_working))
        EdgeCard(edgeToEdge = false, contentPadding = PaddingValues(horizontal = Dimension.D800, vertical = Dimension.D300)) {
            rows.forEachIndexed { index, (label, value) ->
                OverviewRow(label = label, value = value, metric = Metric.EnergyOut, strong = index == rows.lastIndex)
                if (index < rows.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun OverviewRow(label: String, value: String, metric: Metric?, strong: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimension.D500).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, typography = AppTheme.type.body, color = AppTheme.colors.muted, modifier = Modifier.weight(1f))
        Text(
            text = value,
            typography = if (strong) AppTheme.type.bodyBold else AppTheme.type.body,
            color = AppTheme.colors.ink,
            modifier = if (metric != null) Modifier.testTag(metric.testTag()) else Modifier,
        )
    }
}

// ---------- 6. Commitment ----------

@Composable
private fun CommitStep(hard: Boolean) {
    Spacer(Modifier.height(Dimension.D900))
    StepTitle(
        title = stringResource(Res.string.plan_commit_title),
        body = stringResource(if (hard) Res.string.plan_commit_body_gentle else Res.string.plan_commit_body),
    )
}

/** A big "I'm committed", with the 3D lip, and a quiet "Not now" that finishes all the same. */
@Composable
private fun CommitButtons(busy: Boolean, onCommit: (Boolean) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PagePadding).padding(top = Dimension.D500, bottom = Dimension.D700),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimension.D300),
    ) {
        ButtonPrimary(
            onClick = { onCommit(true) },
            enabled = !busy,
            size = ButtonSize.Large,
            deep = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (busy) Res.string.onboarding_finishing else Res.string.plan_commit_yes))
        }
        ButtonGhost(onClick = { onCommit(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.plan_commit_not_now))
        }
        Text(
            text = stringResource(Res.string.plan_commit_footnote),
            typography = AppTheme.type.secondarySmall,
            color = AppTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------- Shared ----------

@Composable
private fun FieldLabel(text: String) {
    Text(text = text, typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.muted)
}

@Composable
private fun Note(text: String, live: Boolean = false) {
    Text(
        text = text,
        typography = AppTheme.type.secondary,
        color = AppTheme.colors.muted,
        modifier = if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
    )
}
