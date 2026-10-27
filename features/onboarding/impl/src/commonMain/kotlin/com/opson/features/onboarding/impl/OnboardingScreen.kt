package com.opson.features.onboarding.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.ui.detail
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.testTag
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.ChoiceRow
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.PageHeaderDefaults
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SegmentedBar
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.Stepper
import com.opson.libraries.ui.components.dialog.TimePickerDialog
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.time.DateCopy
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.system.minTouchTarget
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.auth_error_network
import opson.libraries.resources.generated.resources.auth_error_provider_not_enabled
import opson.libraries.resources.generated.resources.common_back
import opson.libraries.resources.generated.resources.onboarding_account_apple
import opson.libraries.resources.generated.resources.onboarding_account_email
import opson.libraries.resources.generated.resources.onboarding_account_export
import opson.libraries.resources.generated.resources.onboarding_account_failed
import opson.libraries.resources.generated.resources.onboarding_account_google
import opson.libraries.resources.generated.resources.onboarding_account_google_mark
import opson.libraries.resources.generated.resources.onboarding_account_private
import opson.libraries.resources.generated.resources.onboarding_account_waiting_for_apple
import opson.libraries.resources.generated.resources.onboarding_all_modes
import opson.libraries.resources.generated.resources.onboarding_continue
import opson.libraries.resources.generated.resources.onboarding_feel_footnote
import opson.libraries.resources.generated.resources.onboarding_finishing
import opson.libraries.resources.generated.resources.onboarding_get_started
import opson.libraries.resources.generated.resources.onboarding_plan_free
import opson.libraries.resources.generated.resources.onboarding_plan_free_includes
import opson.libraries.resources.generated.resources.onboarding_progress
import opson.libraries.resources.generated.resources.onboarding_progress_description
import opson.libraries.resources.generated.resources.onboarding_progress_optional
import opson.libraries.resources.generated.resources.onboarding_reco_change
import opson.libraries.resources.generated.resources.onboarding_reco_kept_out
import opson.libraries.resources.generated.resources.onboarding_reco_kicker
import opson.libraries.resources.generated.resources.onboarding_reco_or
import opson.libraries.resources.generated.resources.onboarding_reco_see
import opson.libraries.resources.generated.resources.onboarding_see_starting_point
import opson.libraries.resources.generated.resources.onboarding_sign_in_body
import opson.libraries.resources.generated.resources.onboarding_sign_in_guest_body
import opson.libraries.resources.generated.resources.onboarding_sign_in_guest_title
import opson.libraries.resources.generated.resources.onboarding_sign_in_in_use
import opson.libraries.resources.generated.resources.onboarding_sign_in_title
import opson.libraries.resources.generated.resources.onboarding_sign_in_use_that
import opson.libraries.resources.generated.resources.onboarding_sign_in_waiting_for_google
import opson.libraries.resources.generated.resources.onboarding_skip
import opson.libraries.resources.generated.resources.onboarding_start_with
import opson.libraries.resources.generated.resources.onboarding_statement_body
import opson.libraries.resources.generated.resources.onboarding_statement_quote
import opson.libraries.resources.generated.resources.onboarding_talk_to_someone
import opson.libraries.resources.generated.resources.onboarding_welcome_body
import opson.libraries.resources.generated.resources.onboarding_welcome_next
import opson.libraries.resources.generated.resources.onboarding_welcome_not_medical
import opson.libraries.resources.generated.resources.onboarding_welcome_subtitle
import opson.libraries.resources.generated.resources.onboarding_welcome_title
import opson.libraries.resources.generated.resources.onboarding_body_activity
import opson.libraries.resources.generated.resources.onboarding_body_body
import opson.libraries.resources.generated.resources.onboarding_body_burned
import opson.libraries.resources.generated.resources.onboarding_body_resting
import opson.libraries.resources.generated.resources.onboarding_body_title
import opson.libraries.resources.generated.resources.onboarding_body_units_imperial
import opson.libraries.resources.generated.resources.onboarding_body_units_metric
import opson.libraries.resources.generated.resources.onboarding_body_weight
import opson.libraries.resources.generated.resources.onboarding_body_weight_hint_kg
import opson.libraries.resources.generated.resources.onboarding_body_weight_hint_lb
import opson.libraries.resources.generated.resources.onboarding_body_year_hint
import opson.libraries.resources.generated.resources.onboarding_body_height_hint_cm
import opson.libraries.resources.generated.resources.onboarding_body_height_hint_in
import opson.libraries.resources.generated.resources.onboarding_goal_burn_note
import opson.libraries.resources.generated.resources.onboarding_goal_change
import opson.libraries.resources.generated.resources.onboarding_goal_title
import opson.libraries.resources.generated.resources.onboarding_meals_body
import opson.libraries.resources.generated.resources.onboarding_meals_title
import opson.libraries.resources.generated.resources.reminders_time_cancel
import opson.libraries.resources.generated.resources.reminders_time_ok
import opson.libraries.resources.generated.resources.reminders_time_title
import opson.libraries.resources.generated.resources.settings_look_weight_kg
import opson.libraries.resources.generated.resources.settings_look_weight_lb
import opson.libraries.resources.generated.resources.settings_resting_birth_year
import opson.libraries.resources.generated.resources.settings_resting_cm
import opson.libraries.resources.generated.resources.settings_resting_height
import opson.libraries.resources.generated.resources.settings_resting_in
import opson.libraries.resources.generated.resources.settings_resting_sex
import opson.libraries.resources.generated.resources.settings_resting_sex_female
import opson.libraries.resources.generated.resources.settings_resting_sex_male
import opson.libraries.resources.generated.resources.settings_resting_sex_unsaid
import opson.libraries.resources.generated.resources.settings_targets_floor_note
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Onboarding (design v3, 1.1 to 1.6, with sign-in moved to the front when accounts became
 * required): Welcome and the plan, sign in, two questions, the optional statement, the
 * starting mode and its setup steps. All routing lives in [OnboardingViewModel]; this is a pure
 * render of the state. The only numbers are the setup steps' goal and resting energy, which the
 * state holds already resolved through the mode's policy.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.canGoBack) { onAction(OnboardingAction.Back) }

    Screen(
        modifier = modifier,
        containerColor = AppTheme.colors.bg.color,
        contentWindowInsets = WindowInsets.systemBars,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val step = state.step) {
                OnboardingStep.Welcome -> WelcomeStep(state, onAction)
                OnboardingStep.SignIn -> SignInStep(state, onAction)
                is OnboardingStep.Question -> QuestionStep(state, step.question, onAction)
                OnboardingStep.Recommendation -> RecommendationStep(state, onAction)
                OnboardingStep.Goal -> GoalStep(state, onAction)
                OnboardingStep.Body -> BodyStep(state, onAction)
                OnboardingStep.Meals -> MealsStep(state, onAction)
            }
        }
    }
}

/** The page inset the onboarding design uses on every step. */
private val PagePadding = Dimension.D900

// ---------- Welcome ----------

/**
 * Scrolls as one column, at least a screen tall, so the actions sit at the bottom on a tall
 * phone and follow the copy on a short one or with large text.
 */
@Composable
private fun WelcomeStep(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
) {
    val busy = state.isFinishing

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = PagePadding),
        ) {
            Spacer(Modifier.height(Dimension.D1100))
            Text(
                text = stringResource(Res.string.onboarding_welcome_title),
                typography = AppTheme.type.bodyBold,
                color = AppTheme.colors.accent,
            )
            Spacer(Modifier.height(Dimension.D900))
            Text(
                text = stringResource(Res.string.onboarding_welcome_subtitle),
                typography = AppTheme.type.h1,
                color = AppTheme.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Dimension.D800))
            Text(
                text = stringResource(Res.string.onboarding_welcome_body),
                typography = AppTheme.type.lead,
                color = AppTheme.colors.inkSoft,
            )
            Spacer(Modifier.height(Dimension.D900))
            PlanCard()
            Spacer(Modifier.height(Dimension.D700))
            Text(
                text = stringResource(Res.string.onboarding_welcome_not_medical),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )

            Spacer(Modifier.height(Dimension.D900))
            Spacer(Modifier.weight(1f))

            ButtonPrimary(
                onClick = { onAction(OnboardingAction.GetStartedTapped) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.onboarding_get_started))
            }
            Spacer(Modifier.height(Dimension.D500))
            Text(
                text = stringResource(Res.string.onboarding_welcome_next),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Dimension.D700))
        }
    }
}

/**
 * The plan before setup. 0.1 sells nothing and shows only Free: a price with no way to buy it is
 * an App Review rejection, and a "Coming later" tier invites the same question. Plus comes back
 * with the purchase that makes it real.
 */
@Composable
private fun PlanCard() {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = PaddingValues(horizontal = Dimension.D850, vertical = Dimension.D700),
        verticalArrangement = Arrangement.spacedBy(Dimension.D700),
    ) {
        PlanRow(
            name = stringResource(Res.string.onboarding_plan_free),
            includes = stringResource(Res.string.onboarding_plan_free_includes),
        )
    }
}

@Composable
private fun PlanRow(name: String, includes: String) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Dimension.D700),
    ) {
        Text(
            text = name,
            typography = AppTheme.type.bodyBold,
            color = AppTheme.colors.ink,
            modifier = Modifier.weight(PLAN_NAME_WEIGHT),
        )
        Text(
            text = includes,
            typography = AppTheme.type.body,
            color = AppTheme.colors.muted,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f - PLAN_NAME_WEIGHT),
        )
    }
}

private const val PLAN_NAME_WEIGHT = 0.36f

// ---------- Questions and the statement ----------

@Composable
private fun ColumnScope.QuestionStep(
    state: OnboardingState,
    question: SetupQuestion,
    onAction: (OnboardingAction) -> Unit,
) {
    val statement = question == SetupQuestion.Statement

    OnboardingTopBar(
        canGoBack = state.canGoBack,
        onAction = onAction,
        progress = state.progress,
        canSkip = !state.isFinishing,
    )

    ScrollingBody {
        Text(
            text = stringResource(
                if (statement) Res.string.onboarding_progress_optional else Res.string.onboarding_progress,
                question.number,
                SetupQuestion.Count,
            ),
            typography = AppTheme.type.secondaryStrong,
            color = AppTheme.colors.muted,
        )
        Spacer(Modifier.height(Dimension.D700))
        Text(
            text = stringResource(question.title),
            typography = AppTheme.type.subH1,
            color = AppTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        question.subtitle?.let {
            Spacer(Modifier.height(Dimension.D300))
            Text(text = stringResource(it), typography = AppTheme.type.body, color = AppTheme.colors.muted)
        }
        if (statement) {
            Spacer(Modifier.height(Dimension.D700))
            StatementCard()
            Spacer(Modifier.height(Dimension.D600))
            Text(
                text = stringResource(Res.string.onboarding_statement_body),
                typography = AppTheme.type.body,
                color = AppTheme.colors.muted,
            )
        }
        Spacer(Modifier.height(Dimension.D700))

        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
            state.options.forEach { choice ->
                ChoiceRow(
                    label = stringResource(choice.option.label),
                    selected = choice.selected,
                    onClick = { onAction(OnboardingAction.OptionTapped(choice.option)) },
                    role = if (question.multiSelect) Role.Checkbox else Role.RadioButton,
                    enabled = !state.isFinishing,
                )
            }
        }

        if (question == SetupQuestion.Feeling) {
            Spacer(Modifier.height(Dimension.D700))
            Text(
                text = stringResource(Res.string.onboarding_feel_footnote),
                typography = AppTheme.type.body,
                color = AppTheme.colors.muted,
            )
        }
    }

    PinnedPrimary(
        label = stringResource(if (statement) Res.string.onboarding_see_starting_point else Res.string.onboarding_continue),
        enabled = !state.isFinishing,
        onClick = { onAction(OnboardingAction.ContinueTapped) },
        above = if (statement) {
            { TalkToSomeoneLink(enabled = !state.isFinishing, onClick = { onAction(OnboardingAction.TalkToSomeoneTapped) }) }
        } else {
            null
        },
    )
}

/** "Talk to someone": Find support, whichever answer is picked. One 48 dp target. */
@Composable
private fun TalkToSomeoneLink(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = MinTouchTarget)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .bounceClick(enabled = enabled, scaleDown = 0.97f, onClick = onClick)
            .padding(horizontal = Dimension.D300),
        horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon = AppIcons.SupportAgent, size = IconGlyph, color = AppTheme.colors.accent)
        Text(
            text = stringResource(Res.string.onboarding_talk_to_someone),
            typography = AppTheme.type.bodyBold,
            color = AppTheme.colors.accent,
        )
    }
}

/** The statement as a quote the person can agree with, never a question about a diagnosis. */
@Composable
private fun StatementCard() {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = PaddingValues(horizontal = Dimension.D800, vertical = Dimension.D850),
    ) {
        Text(
            text = stringResource(Res.string.onboarding_statement_quote),
            typography = AppTheme.type.heroTitle,
            color = AppTheme.colors.ink,
        )
    }
}

// ---------- Recommendation ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.RecommendationStep(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
) {
    val chosenLabel = stringResource(state.chosen.label)
    val preview = state.preview

    OnboardingTopBar(canGoBack = state.canGoBack, onAction = onAction)

    ScrollingBody {
        Text(
            text = stringResource(Res.string.onboarding_reco_kicker),
            typography = AppTheme.type.secondaryStrong,
            color = AppTheme.colors.muted,
        )
        Spacer(Modifier.height(Dimension.D300))
        Text(
            text = chosenLabel,
            typography = AppTheme.type.display,
            color = AppTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Dimension.D200))
        Text(
            text = stringResource(state.chosen.line),
            typography = AppTheme.type.lead,
            color = AppTheme.colors.inkSoft,
        )
        state.reason?.let { reason ->
            Spacer(Modifier.height(Dimension.D300))
            Text(text = stringResource(reason.line), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        Spacer(Modifier.height(Dimension.D700))

        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D600)) {
            PreviewCard(title = stringResource(Res.string.onboarding_reco_see), lines = preview.seen, seen = true)
            if (preview.keptOut.isNotEmpty()) {
                PreviewCard(
                    title = stringResource(Res.string.onboarding_reco_kept_out),
                    lines = preview.keptOut,
                    seen = false,
                )
            }
        }

        if (state.alternatives.isNotEmpty()) {
            Spacer(Modifier.height(Dimension.D700))
            Text(
                text = stringResource(Res.string.onboarding_reco_or),
                typography = AppTheme.type.secondaryStrong,
                color = AppTheme.colors.muted,
            )
            Spacer(Modifier.height(Dimension.D200))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimension.D300)) {
                state.alternatives.forEach { preset ->
                    AlternativeChip(
                        label = stringResource(preset.label),
                        enabled = !state.isFinishing,
                        onClick = { onAction(OnboardingAction.AlternativeTapped(preset)) },
                    )
                }
                if (!state.showAllModes) {
                    AlternativeChip(
                        label = stringResource(Res.string.onboarding_all_modes),
                        enabled = !state.isFinishing,
                        onClick = { onAction(OnboardingAction.AllModesTapped) },
                    )
                }
            }
        }
    }

    PinnedPrimary(
        label = if (state.isFinishing) {
            stringResource(Res.string.onboarding_finishing)
        } else {
            stringResource(Res.string.onboarding_start_with, chosenLabel)
        },
        enabled = !state.isFinishing,
        onClick = { onAction(OnboardingAction.StartTapped) },
        footnote = stringResource(Res.string.onboarding_reco_change),
    )
}

/** "You'll see" or "Kept out of view": one line per subject, the icon saying how it shows. */
@Composable
private fun PreviewCard(title: String, lines: List<PreviewLine>, seen: Boolean) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = EdgeCardDefaults.InsetPadding,
        verticalArrangement = Arrangement.spacedBy(Dimension.D300),
    ) {
        Text(text = title, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
        lines.forEach { line ->
            IconLine(
                icon = line.mark.icon,
                iconColor = if (seen) AppTheme.colors.accent else AppTheme.colors.muted,
                text = stringResource(line.item.label),
            )
        }
    }
}

private val PreviewMark.icon: IconResource
    get() = when (this) {
        PreviewMark.Shown -> AppIcons.VisibilityOutline
        PreviewMark.Softened -> AppIcons.BlurOn
        PreviewMark.Later -> AppIcons.Schedule
        PreviewMark.Hidden -> AppIcons.VisibilityOffOutline
    }

@Composable
private fun AlternativeChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    PillChip(
        label = label,
        selected = false,
        onClick = onClick,
        enabled = enabled,
        role = Role.Button,
    )
}

// ---------- Setup steps ----------

/** The top bar and the pinned Continue every setup step shares. Continue on the last one saves. */
@Composable
private fun ColumnScope.SetupStep(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
    title: String,
    body: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OnboardingTopBar(canGoBack = state.canGoBack, onAction = onAction, canSkip = !state.isFinishing, showSkip = true)

    ScrollingBody {
        Text(
            text = title,
            typography = AppTheme.type.subH1,
            color = AppTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (body != null) {
            Spacer(Modifier.height(Dimension.D300))
            Text(text = body, typography = AppTheme.type.body, color = AppTheme.colors.muted)
        }
        Spacer(Modifier.height(Dimension.D700))
        content()
    }

    PinnedPrimary(
        label = stringResource(if (state.isFinishing) Res.string.onboarding_finishing else Res.string.onboarding_continue),
        enabled = !state.isFinishing,
        onClick = { onAction(OnboardingAction.ContinueTapped) },
    )
}

/**
 * The goal, in Full numbers: the style (with `release.goalsAndEnergy`) and the range or the one
 * number on a stepper. Lower stops at the floor, as on Targets. Every amount is the policy's.
 */
@Composable
private fun ColumnScope.GoalStep(state: OnboardingState, onAction: (OnboardingAction) -> Unit) {
    val view = state.goalView ?: return
    SetupStep(state, onAction, title = stringResource(Res.string.onboarding_goal_title)) {
        if (view.styles.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
                view.styles.forEach { style ->
                    ChoiceRow(
                        label = stringResource(style.label),
                        selected = style == view.style,
                        onClick = { onAction(OnboardingAction.GoalStyleTapped(style)) },
                        role = Role.RadioButton,
                        enabled = !state.isFinishing,
                        detail = style.line?.let { stringResource(it) },
                    )
                }
            }
            Spacer(Modifier.height(Dimension.D700))
        }
        (view.amount as? Display.Shown)?.let { amount ->
            Stepper(
                amount = amount.text,
                onDecrease = { onAction(OnboardingAction.GoalStepped(up = false)) },
                onIncrease = { onAction(OnboardingAction.GoalStepped(up = true)) },
                canDecrease = view.canLower && !state.isFinishing,
                canIncrease = !state.isFinishing,
                amountModifier = Modifier.testTag(Metric.Calories.testTag()),
            )
            if (!view.canLower) {
                Spacer(Modifier.height(Dimension.D500))
                Text(
                    text = stringResource(Res.string.settings_targets_floor_note),
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                )
            }
        }
        if (view.style.needsBurn) {
            Text(
                text = stringResource(Res.string.onboarding_goal_burn_note),
                typography = AppTheme.type.body,
                color = AppTheme.colors.muted,
            )
        }
        Spacer(Modifier.height(Dimension.D700))
        Text(
            text = stringResource(Res.string.onboarding_goal_change),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
    }
}

/**
 * Resting energy's details, for a set deficit or surplus: units, the year born, height, weight
 * and sex. "Rather not say" is a real answer. The weight is logged as the first weigh-in.
 */
@Composable
private fun ColumnScope.BodyStep(state: OnboardingState, onAction: (OnboardingAction) -> Unit) {
    val view = state.bodyView ?: return
    val answer = view.answer
    val imperial = answer.unit == WeightUnit.Lb
    SetupStep(
        state,
        onAction,
        title = stringResource(Res.string.onboarding_body_title),
        body = stringResource(Res.string.onboarding_body_body),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D700)) {
            SegmentedControl(
                options = WeightUnit.entries,
                selected = answer.unit,
                onSelect = { onAction(OnboardingAction.UnitPicked(it)) },
                label = {
                    stringResource(
                        if (it == WeightUnit.Lb) Res.string.onboarding_body_units_imperial else Res.string.onboarding_body_units_metric,
                    )
                },
            )
            HintedField(hint = Res.string.onboarding_body_year_hint.takeIf { view.birthYearInvalid }) {
                NumberField(
                    value = answer.birthYear,
                    onValueChange = { onAction(OnboardingAction.BirthYearTyped(it)) },
                    label = stringResource(Res.string.settings_resting_birth_year),
                    allowDecimal = false,
                    enabled = !state.isFinishing,
                )
            }
            HintedField(
                hint = (if (imperial) Res.string.onboarding_body_height_hint_in else Res.string.onboarding_body_height_hint_cm)
                    .takeIf { view.heightInvalid },
            ) {
                NumberField(
                    value = answer.height,
                    onValueChange = { onAction(OnboardingAction.HeightTyped(it)) },
                    label = stringResource(Res.string.settings_resting_height),
                    unit = stringResource(if (imperial) Res.string.settings_resting_in else Res.string.settings_resting_cm),
                    enabled = !state.isFinishing,
                )
            }
            if (view.weightShown) {
                HintedField(
                    hint = (if (imperial) Res.string.onboarding_body_weight_hint_lb else Res.string.onboarding_body_weight_hint_kg)
                        .takeIf { view.weightInvalid },
                ) {
                    NumberField(
                        value = answer.weight,
                        onValueChange = { onAction(OnboardingAction.WeightTyped(it)) },
                        label = stringResource(Res.string.onboarding_body_weight),
                        unit = stringResource(if (imperial) Res.string.settings_look_weight_lb else Res.string.settings_look_weight_kg),
                        enabled = !state.isFinishing,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dimension.D200)) {
                Text(
                    text = stringResource(Res.string.settings_resting_sex),
                    typography = AppTheme.type.secondarySmallStrong,
                    color = AppTheme.colors.muted,
                )
                SegmentedControl<BodySex?>(
                    options = BodySex.entries,
                    selected = answer.sex,
                    onSelect = { sex -> sex?.let { onAction(OnboardingAction.SexPicked(it)) } },
                    label = { sex ->
                        stringResource(
                            when (sex) {
                                BodySex.Female -> Res.string.settings_resting_sex_female
                                BodySex.Male -> Res.string.settings_resting_sex_male
                                BodySex.Unsaid, null -> Res.string.settings_resting_sex_unsaid
                            },
                        )
                    },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
                Text(
                    text = stringResource(Res.string.onboarding_body_activity),
                    typography = AppTheme.type.secondarySmallStrong,
                    color = AppTheme.colors.muted,
                )
                EverydayActivity.entries.forEach { level ->
                    ChoiceRow(
                        label = stringResource(level.label),
                        selected = level == answer.activity,
                        onClick = { onAction(OnboardingAction.ActivityPicked(level)) },
                        role = Role.RadioButton,
                        enabled = !state.isFinishing,
                        detail = stringResource(level.detail),
                    )
                }
            }
            (view.resting as? Display.Shown)?.let {
                Text(
                    text = stringResource(Res.string.onboarding_body_resting, it.text),
                    typography = AppTheme.type.bodyBold,
                    color = AppTheme.colors.ink,
                    modifier = Modifier.testTag(Metric.EnergyOut.testTag()).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            (view.burned as? Display.Shown)?.let {
                Text(
                    text = stringResource(Res.string.onboarding_body_burned, it.text),
                    typography = AppTheme.type.body,
                    color = AppTheme.colors.muted,
                    modifier = Modifier.testTag(Metric.EnergyOut.testTag()),
                )
            }
        }
    }
}

/** A field with, when [hint] is set, a line under it saying what the field wants. */
@Composable
private fun HintedField(hint: StringResource?, field: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimension.D200)) {
        field()
        if (hint != null) {
            Text(
                text = stringResource(hint),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** When meals usually happen. Today's "around" times and meal reminders read them. */
@Composable
private fun ColumnScope.MealsStep(state: OnboardingState, onAction: (OnboardingAction) -> Unit) {
    var picking by remember { mutableStateOf<MealSlot?>(null) }
    SetupStep(
        state,
        onAction,
        title = stringResource(Res.string.onboarding_meals_title),
        body = stringResource(Res.string.onboarding_meals_body),
    ) {
        EdgeCard(edgeToEdge = false, contentPadding = PaddingValues(horizontal = Dimension.D800, vertical = Dimension.D300)) {
            MealSlot.meals.forEachIndexed { index, slot ->
                val time = state.mealTimes.at(slot)
                MealTimeRow(
                    label = stringResource(slot.label),
                    time = DateCopy.time(time.hour, time.minute),
                    enabled = !state.isFinishing,
                    onClick = { picking = slot },
                )
                if (index < MealSlot.meals.lastIndex) HorizontalDivider()
            }
        }
    }
    picking?.let { slot ->
        TimePickerDialog(
            title = stringResource(Res.string.reminders_time_title, stringResource(slot.label)),
            initial = state.mealTimes.at(slot),
            okLabel = stringResource(Res.string.reminders_time_ok),
            cancelLabel = stringResource(Res.string.reminders_time_cancel),
            onPicked = {
                onAction(OnboardingAction.MealTimePicked(slot, it))
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun MealTimeRow(label: String, time: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouchTarget + Dimension.D300)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .bounceClick(enabled = enabled, scaleDown = 0.98f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        Text(text = time, typography = AppTheme.type.bodyBold, color = AppTheme.colors.accent)
    }
}

// ---------- Sign in ----------

/**
 * Required, straight after Welcome: Apple on iOS, Google, then email when it is on. A guest from
 * an older build reads that signing in keeps what they logged, and, if the account they picked
 * already has its own log, can sign in to it instead.
 */
@Composable
private fun ColumnScope.SignInStep(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
) {
    val enabled = state.canTapSignIn

    OnboardingTopBar(canGoBack = state.canGoBack, onAction = onAction)

    ScrollingBody {
        Text(
            text = stringResource(if (state.guest) Res.string.onboarding_sign_in_guest_title else Res.string.onboarding_sign_in_title),
            typography = AppTheme.type.subH1,
            color = AppTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Dimension.D500))
        Text(
            text = stringResource(if (state.guest) Res.string.onboarding_sign_in_guest_body else Res.string.onboarding_sign_in_body),
            typography = AppTheme.type.lead,
            color = AppTheme.colors.inkSoft,
        )
        Spacer(Modifier.height(Dimension.D900))

        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
            if (state.appleEnabled) {
                AccountButton(
                    label = stringResource(
                        if (state.accountInFlight == AccountMethod.Apple) {
                            Res.string.onboarding_account_waiting_for_apple
                        } else {
                            Res.string.onboarding_account_apple
                        },
                    ),
                    enabled = enabled,
                    onClick = { onAction(OnboardingAction.SignInWithAppleTapped) },
                )
            }
            AccountButton(
                label = stringResource(
                    if (state.accountInFlight == AccountMethod.Google) {
                        Res.string.onboarding_sign_in_waiting_for_google
                    } else {
                        Res.string.onboarding_account_google
                    },
                ),
                enabled = enabled,
                onClick = { onAction(OnboardingAction.SignInWithGoogleTapped) },
                leading = { GoogleMark() },
            )
            if (SignInMethods.EMAIL) {
                AccountButton(
                    label = stringResource(Res.string.onboarding_account_email),
                    enabled = enabled,
                    onClick = { onAction(OnboardingAction.SignInWithEmailTapped) },
                    leading = { Icon(icon = AppIcons.Mail, size = IconGlyph, color = AppTheme.colors.ink) },
                )
            }
            state.accountError?.let { AccountErrorText(it) }
            if (state.conflict != null) {
                ButtonGhost(
                    onClick = { onAction(OnboardingAction.UseThatAccountTapped) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.onboarding_sign_in_use_that))
                }
            }
        }

        Spacer(Modifier.height(Dimension.D700))
        EdgeCard(
            edgeToEdge = false,
            contentPadding = EdgeCardDefaults.InsetPadding,
            verticalArrangement = Arrangement.spacedBy(Dimension.D300),
        ) {
            IconLine(AppIcons.LockOutlined, AppTheme.colors.accent, stringResource(Res.string.onboarding_account_private))
            IconLine(AppIcons.Download, AppTheme.colors.accent, stringResource(Res.string.onboarding_account_export))
            // Sharing with a coach is coming-later work; 0.1 doesn't mention it.
        }
    }
}

@Composable
private fun AccountButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    ButtonSecondary(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D500),
        ) {
            leading?.invoke()
            Text(label)
        }
    }
}

/** Google's "G" on a soft disc, as the design draws it. Decorative: the label says Google. */
@Composable
private fun GoogleMark() {
    Box(
        modifier = Modifier.size(IconGlyph).clip(CircleShape).background(AppTheme.colors.soft).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.onboarding_account_google_mark),
            typography = AppTheme.type.captionStrong,
            color = AppTheme.colors.ink,
        )
    }
}

@Composable
private fun AccountErrorText(error: OnboardingAccountError) {
    Text(
        text = stringResource(
            when (error) {
                OnboardingAccountError.AccountInUse -> Res.string.onboarding_sign_in_in_use
                OnboardingAccountError.ProviderNotEnabled -> Res.string.auth_error_provider_not_enabled
                OnboardingAccountError.Offline -> Res.string.auth_error_network
                OnboardingAccountError.Failed -> Res.string.onboarding_account_failed
            },
        ),
        typography = AppTheme.type.secondary,
        color = AppTheme.colors.ink,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
}

// ---------- Shared pieces ----------

/** Icon glyphs on these steps are 24 dp, as Material draws them. */
private val IconGlyph = Dimension.D900

/** An icon and one line of body text, as the preview cards and the sign-in card list them. */
@Composable
private fun IconLine(icon: IconResource, iconColor: ColorResource, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimension.D500),
    ) {
        Icon(icon = icon, size = IconGlyph, color = iconColor)
        Text(text = text, typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
    }
}

/**
 * The Android top app bar these steps share (design v3): a back arrow, then on the question
 * steps a segmented progress bar and Skip. The setup steps keep Skip without the bar: they are
 * not questions, and how many there are depends on the mode. Laid out like [PageHeaderDefaults]:
 * 64 dp tall, the arrow's glyph 16 dp from the edge.
 */
@Composable
private fun OnboardingTopBar(
    canGoBack: Boolean,
    onAction: (OnboardingAction) -> Unit,
    progress: Int = 0,
    canSkip: Boolean = false,
    showSkip: Boolean = progress > 0,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PageHeaderDefaults.Height)
            .padding(start = Dimension.D100, end = Dimension.D500),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) {
            Box(
                modifier = Modifier
                    .size(MinTouchTarget)
                    .clip(CircleShape)
                    .semantics { role = Role.Button }
                    .bounceClick(scaleDown = 0.9f, onClick = { onAction(OnboardingAction.Back) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon = AppIcons.ArrowBack.described(stringResource(Res.string.common_back)),
                    size = IconGlyph,
                    color = AppTheme.colors.ink,
                )
            }
        } else {
            Spacer(Modifier.width(MinTouchTarget))
        }
        if (progress > 0) {
            val description = stringResource(Res.string.onboarding_progress_description, progress, SetupQuestion.Count)
            Spacer(Modifier.width(Dimension.D500))
            SegmentedBar(
                filled = progress,
                total = SetupQuestion.Count,
                color = AppTheme.colors.accent,
                height = Dimension.D100,
                gap = Dimension.D100,
                modifier = Modifier.weight(1f).semantics { contentDescription = description },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (showSkip) {
            // Skip's 48 dp target reaches into the end padding, so its text lines up with the page.
            Spacer(Modifier.width(Dimension.D500))
            Text(
                text = stringResource(Res.string.onboarding_skip),
                typography = AppTheme.type.bodyBold,
                color = AppTheme.colors.accent,
                modifier = Modifier
                    .minTouchTarget()
                    .semantics { role = Role.Button }
                    .bounceClick(enabled = canSkip, scaleDown = 0.95f, onClick = {
                        onAction(OnboardingAction.SkipQuestionTapped)
                    }),
            )
        }
    }
}

@Composable
private fun ColumnScope.ScrollingBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PagePadding)
            .padding(top = Dimension.D500, bottom = Dimension.D700),
        content = content,
    )
}

/** The primary button stays at the bottom however long the options run. */
@Composable
private fun PinnedPrimary(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    footnote: String? = null,
    above: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PagePadding)
            .padding(top = Dimension.D500, bottom = Dimension.D700),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimension.D500),
    ) {
        above?.invoke()
        ButtonPrimary(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(label)
        }
        if (footnote != null) {
            Text(
                text = footnote,
                typography = AppTheme.type.secondarySmall,
                color = AppTheme.colors.muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------- Previews ----------
// Named so their screen ids (onboarding-screen__onboarding-…) sit next to the design's ids.

@Composable
private fun OnboardingPreview(state: OnboardingState, darkTheme: Boolean = false, largeText: Boolean = false) {
    PreviewContent(darkTheme = darkTheme, largeText = largeText) {
        OnboardingScreen(state = state, onAction = {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingWelcomePreview() = OnboardingPreview(OnboardingFixtures.welcome())

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingWelcomeDarkPreview() = OnboardingPreview(OnboardingFixtures.welcome(), darkTheme = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingWhyPreview() = OnboardingPreview(
    OnboardingFixtures.question(SetupQuestion.Reason, OnboardingFixtures.DesignAnswers.copy(feel = null, statement = null)),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingWhyEmptyPreview() = OnboardingPreview(OnboardingFixtures.question(SetupQuestion.Reason))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingWhyDarkPreview() = OnboardingPreview(
    OnboardingFixtures.question(SetupQuestion.Reason, OnboardingFixtures.DesignAnswers.copy(feel = null, statement = null)),
    darkTheme = true,
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingWhyLargeTextPreview() = OnboardingPreview(
    OnboardingFixtures.question(SetupQuestion.Reason, SetupAnswers(why = setOf(Why.MedicalNeed))),
    largeText = true,
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingNumbersPreview() = OnboardingPreview(
    OnboardingFixtures.question(SetupQuestion.Feeling, OnboardingFixtures.DesignAnswers.copy(statement = null)),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingNumbersEmptyPreview() = OnboardingPreview(OnboardingFixtures.question(SetupQuestion.Feeling))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingStatementPreview() = OnboardingPreview(
    OnboardingFixtures.question(SetupQuestion.Statement, OnboardingFixtures.DesignAnswers),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingStatementEmptyPreview() = OnboardingPreview(
    OnboardingFixtures.question(SetupQuestion.Statement),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingStatementDarkPreview() = OnboardingPreview(
    OnboardingFixtures.question(SetupQuestion.Statement, OnboardingFixtures.DesignAnswers),
    darkTheme = true,
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingRecommendationPreview() =
    OnboardingPreview(OnboardingFixtures.recommendation(OnboardingFixtures.DesignAnswers))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingRecommendationDarkPreview() =
    OnboardingPreview(OnboardingFixtures.recommendation(OnboardingFixtures.DesignAnswers), darkTheme = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingRecommendationFullPreview() = OnboardingPreview(OnboardingFixtures.recommendation())

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingRecommendationBlindPreview() =
    OnboardingPreview(OnboardingFixtures.recommendation(OnboardingFixtures.HardAnswers, picked = PresetId.NoNumbers))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingRecommendationAllModesPreview() = OnboardingPreview(
    OnboardingFixtures.recommendation(OnboardingFixtures.DesignAnswers, allModes = true),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingRecommendationNotSurePreview() = OnboardingPreview(
    OnboardingFixtures.recommendation(SetupAnswers(feel = NumbersFeel.NotSure)),
)

// Setup steps (release.onboardingSetup). No design yet: ids onboarding-goal, onboarding-resting-energy
// and onboarding-meal-times, in the design's visual language.

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingGoalPreview() = OnboardingPreview(OnboardingFixtures.goal())

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingGoalDarkPreview() = OnboardingPreview(OnboardingFixtures.goal(), darkTheme = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingGoalRaisedPreview() = OnboardingPreview(
    OnboardingFixtures.goal(GoalAnswer(style = GoalChoice.OneNumber).stepped(up = true).stepped(up = true)),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingGoalDeficitPreview() =
    OnboardingPreview(OnboardingFixtures.goal(GoalAnswer(style = GoalChoice.Deficit)))

/** With `release.goalsAndEnergy` off: the range alone. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingGoalRangeOnlyPreview() = OnboardingPreview(OnboardingFixtures.goal(goalsAndEnergy = false))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingRestingEnergyPreview() = OnboardingPreview(OnboardingFixtures.body(OnboardingFixtures.Body))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingRestingEnergyEmptyPreview() = OnboardingPreview(OnboardingFixtures.body())

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingRestingEnergyLargeTextPreview() =
    OnboardingPreview(OnboardingFixtures.body(OnboardingFixtures.Body.copy(unit = WeightUnit.Lb, height = "66", weight = "141")), largeText = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingMealTimesPreview() = OnboardingPreview(OnboardingFixtures.meals())

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingMealTimesDarkPreview() = OnboardingPreview(OnboardingFixtures.meals(), darkTheme = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingSignInPreview() = OnboardingPreview(OnboardingFixtures.signIn())

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingSignInDarkPreview() = OnboardingPreview(OnboardingFixtures.signIn(appleEnabled = true), darkTheme = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingSignInIosPreview() = OnboardingPreview(OnboardingFixtures.signIn(appleEnabled = true))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingSignInWaitingForApplePreview() =
    OnboardingPreview(OnboardingFixtures.signIn(appleEnabled = true, inFlight = AccountMethod.Apple))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingSignInWaitingForGooglePreview() =
    OnboardingPreview(OnboardingFixtures.signIn(appleEnabled = true, inFlight = AccountMethod.Google))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingSignInOfflinePreview() =
    OnboardingPreview(OnboardingFixtures.signIn(appleEnabled = true, error = OnboardingAccountError.Offline))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingSignInLargeTextPreview() =
    OnboardingPreview(OnboardingFixtures.signIn(appleEnabled = true), largeText = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingSignInGuestPreview() = OnboardingPreview(OnboardingFixtures.signIn(appleEnabled = true, guest = true))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingSignInGuestInUsePreview() = OnboardingPreview(
    OnboardingFixtures.signIn(appleEnabled = true, guest = true, conflict = AccountMethod.Apple),
)
