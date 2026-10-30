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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.GoalKind
import com.opson.libraries.nutrition.HeightInput
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.SexChoice
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
import com.opson.libraries.ui.components.dialog.TimePickerDialog
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
import opson.libraries.resources.generated.resources.onboarding_beta_body
import opson.libraries.resources.generated.resources.onboarding_beta_title
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
import opson.libraries.resources.generated.resources.onboarding_meals_body
import opson.libraries.resources.generated.resources.onboarding_meals_title
import opson.libraries.resources.generated.resources.reminders_time_cancel
import opson.libraries.resources.generated.resources.reminders_time_ok
import opson.libraries.resources.generated.resources.reminders_time_title
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
        // Above the keyboard, so a step's pinned button stays in reach while someone types.
        Column(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            when (val step = state.step) {
                OnboardingStep.Welcome -> WelcomeStep(state, onAction)
                OnboardingStep.Age -> AgeStep(state, onAction)
                OnboardingStep.TooYoung -> TooYoungStep(onAction)
                OnboardingStep.SignIn -> SignInStep(state, onAction)
                is OnboardingStep.Question -> QuestionStep(state, step.question, onAction)
                OnboardingStep.Recommendation -> RecommendationStep(state, onAction)
                OnboardingStep.Meals -> MealsStep(state, onAction)
                is OnboardingStep.Plan -> state.planView?.let { view ->
                    PlanStepContent(
                        step = step.step,
                        view = view,
                        busy = state.isFinishing,
                        canGoBack = state.canGoBack,
                        onBack = { onAction(OnboardingAction.Back) },
                        onSkip = { onAction(OnboardingAction.SkipQuestionTapped) },
                        onContinue = { onAction(OnboardingAction.ContinueTapped) },
                        onCommit = { onAction(OnboardingAction.CommitTapped(it)) },
                        onPlan = { onAction(OnboardingAction.PlanChanged(it)) },
                    )
                }
            }
        }
    }
}

/** The page inset the onboarding design uses on every step. */
internal val PagePadding = Dimension.D900

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
            if (state.betaNotice) {
                Spacer(Modifier.height(Dimension.D600))
                BetaCard()
            }
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

/**
 * "You're early" (`release.betaNotice`, owner 2026-10-30): Opson is in beta, Pro isn't here yet,
 * and everyone who joins now gets a lifetime discount on it. No price and no way to buy, as the
 * plan card above: a price with nothing to buy is an App Review rejection. Calling it a beta
 * also sets expectations; whether App Review accepts the word in a store build is a risk the
 * flag lets the owner turn off (docs/plans/onboarding-v4.md).
 */
@Composable
private fun BetaCard() {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = PaddingValues(horizontal = Dimension.D850, vertical = Dimension.D700),
        verticalArrangement = Arrangement.spacedBy(Dimension.D300),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Text(text = stringResource(Res.string.onboarding_beta_title), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
        Text(text = stringResource(Res.string.onboarding_beta_body), typography = AppTheme.type.body, color = AppTheme.colors.inkSoft)
    }
}

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
        onBack = { onAction(OnboardingAction.Back) },
        progress = state.progress,
        canSkip = !state.isFinishing,
        onSkip = { onAction(OnboardingAction.SkipQuestionTapped) },
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

    OnboardingTopBar(canGoBack = state.canGoBack, onBack = { onAction(OnboardingAction.Back) })

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

/** "You'll see" or "Hidden": one line per subject, the icon saying how it shows. */
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

/**
 * The top bar and the pinned Continue a setup step shares: Back, Skip, the title and a line, and
 * Continue, which saves on the last step. The goal flow's steps draw the same pieces
 * (`PlanSteps.kt`).
 */
@Composable
private fun ColumnScope.SetupStep(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
    title: String,
    body: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OnboardingTopBar(
        canGoBack = state.canGoBack,
        onBack = { onAction(OnboardingAction.Back) },
        canSkip = !state.isFinishing,
        showSkip = true,
        onSkip = { onAction(OnboardingAction.SkipQuestionTapped) },
    )
    ScrollingBody {
        StepTitle(title = title, body = body)
        content()
    }
    PinnedPrimary(
        label = stringResource(if (state.isFinishing) Res.string.onboarding_finishing else Res.string.onboarding_continue),
        enabled = !state.isFinishing,
        onClick = { onAction(OnboardingAction.ContinueTapped) },
    )
}

/** A setup step's title and the muted line under it. */
@Composable
internal fun StepTitle(title: String, body: String? = null) {
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
}

/** A field with, when [hint] is set, a line under it saying what the field wants. */
@Composable
internal fun HintedField(hint: StringResource?, field: @Composable () -> Unit) {
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

    OnboardingTopBar(canGoBack = state.canGoBack, onBack = { onAction(OnboardingAction.Back) })

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
internal val IconGlyph = Dimension.D900

/** An icon and one line of body text, as the preview cards and the sign-in card list them. */
@Composable
internal fun IconLine(icon: IconResource, iconColor: ColorResource, text: String) {
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
internal fun OnboardingTopBar(
    canGoBack: Boolean,
    onBack: () -> Unit,
    progress: Int = 0,
    canSkip: Boolean = false,
    showSkip: Boolean = progress > 0,
    onSkip: () -> Unit = {},
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
                    .bounceClick(scaleDown = 0.9f, onClick = onBack),
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
                    .bounceClick(enabled = canSkip, scaleDown = 0.95f, onClick = onSkip),
            )
        }
    }
}

@Composable
internal fun ColumnScope.ScrollingBody(content: @Composable ColumnScope.() -> Unit) {
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
internal fun PinnedPrimary(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    footnote: String? = null,
    above: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
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
        below?.invoke()
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

// The goal flow (PlanFlow.kt), behind release.onboardingSetup. No design yet: ids onboarding-goal-ask,
// onboarding-goal-kind, onboarding-profile, onboarding-activity, onboarding-weight-goal,
// onboarding-goal-rate, onboarding-energy-balance, onboarding-burn-goal, onboarding-meals-goal,
// onboarding-goal-overview, onboarding-streak and onboarding-commitment, in the questions' visual
// language.

private val Fixtures = OnboardingFixtures

/** Welcome with `release.betaNotice` on: Opson is in beta, and Pro isn't here yet. */
@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun OnboardingWelcomeBetaPreview() = OnboardingPreview(Fixtures.welcome(beta = true))

@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun OnboardingWelcomeBetaDarkPreview() = OnboardingPreview(Fixtures.welcome(beta = true), darkTheme = true)

/** "Want to set a goal?", asked before anything about the body. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingGoalAskPreview() = OnboardingPreview(Fixtures.plan(PlanStep.Ask))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingGoalAskDarkPreview() = OnboardingPreview(Fixtures.plan(PlanStep.Ask), darkTheme = true)

/** After "numbers have been hard": a goal can be as simple as showing up. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingGoalAskHardPreview() = OnboardingPreview(Fixtures.plan(PlanStep.Ask, answers = Fixtures.HardAnswers))

/** Every kind All numbers offers, none picked yet. */
@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun OnboardingGoalKindPreview() = OnboardingPreview(Fixtures.plan(PlanStep.Kind, PlanAnswers(wants = true)))

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun OnboardingGoalKindPickedDarkPreview() =
    OnboardingPreview(Fixtures.plan(PlanStep.Kind, PlanAnswers(wants = true, pick = GoalKind.EnergyBalance)), darkTheme = true)

/** Macros with grams: no energy balance, as a deficit gives away what is eaten. */
@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun OnboardingGoalKindMacrosPreview() =
    OnboardingPreview(Fixtures.plan(PlanStep.Kind, PlanAnswers(wants = true), picked = PresetId.MacrosWithGrams))

/** After "numbers have been hard", or 16 and 17: showing up and regular meals only. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingGoalKindHardPreview() =
    OnboardingPreview(Fixtures.plan(PlanStep.Kind, PlanAnswers(wants = true, pick = GoalKind.TrackEveryDay), answers = Fixtures.HardAnswers))

@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun OnboardingEnergyBalancePreview() = OnboardingPreview(Fixtures.plan(PlanStep.Balance, Fixtures.balance()))

@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun OnboardingEnergyBalanceWeeklyDarkPreview() =
    OnboardingPreview(Fixtures.plan(PlanStep.Balance, Fixtures.balance(weekly = true)), darkTheme = true)

/** A surplus, for gaining. */
@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun OnboardingEnergyBalanceOverPreview() = OnboardingPreview(
    Fixtures.plan(PlanStep.Balance, Fixtures.balance().copy(balance = BalanceAnswer(under = false, kcal = 300))),
)

@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun OnboardingGoalOverviewBalancePreview() = OnboardingPreview(Fixtures.plan(PlanStep.Overview, Fixtures.balance(weekly = true)))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingBurnGoalPreview() = OnboardingPreview(Fixtures.plan(PlanStep.Burn, PlanAnswers(pick = GoalKind.Burn)))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingMealsGoalPreview() = OnboardingPreview(Fixtures.plan(PlanStep.MealCount, PlanAnswers(pick = GoalKind.Meals)))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingMealsGoalDarkPreview() =
    OnboardingPreview(Fixtures.plan(PlanStep.MealCount, PlanAnswers(pick = GoalKind.Meals, meals = 4)), darkTheme = true)

/** Steps, with Apple Health still to connect. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingStepsGoalPreview() =
    OnboardingPreview(Fixtures.plan(PlanStep.StepCount, PlanAnswers(pick = GoalKind.Steps), health = Fixtures.appleHealth))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingStepsGoalConnectedDarkPreview() = OnboardingPreview(
    Fixtures.plan(PlanStep.StepCount, PlanAnswers(pick = GoalKind.Steps), health = HealthOffer.Connected(com.opson.libraries.health.HealthSource.AppleHealth)),
    darkTheme = true,
)

/** "Keep a streak?", after any goal: it counts showing up, never an amount. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingStreakPreview() = OnboardingPreview(Fixtures.plan(PlanStep.Streak, PlanAnswers(pick = GoalKind.TrackEveryDay)))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingStreakDarkPreview() =
    OnboardingPreview(Fixtures.plan(PlanStep.Streak, PlanAnswers(pick = GoalKind.Meals)), darkTheme = true)

/** After a weight goal: only the showing-up streak, never one for the goal itself. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingStreakWeightPreview() = OnboardingPreview(Fixtures.plan(PlanStep.Streak, Fixtures.LoseTen))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingStreakLargeTextPreview() =
    OnboardingPreview(Fixtures.plan(PlanStep.Streak, PlanAnswers(pick = GoalKind.TrackEveryDay)), largeText = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingProfilePreview() = OnboardingPreview(Fixtures.plan(PlanStep.Profile, PlanAnswers(BodyAnswer.inRegion("GB"))))

@Preview(widthDp = 390, heightDp = 1400)
@Composable
private fun OnboardingProfileFilledPreview() = OnboardingPreview(Fixtures.plan(PlanStep.Profile, PlanAnswers(Fixtures.ImperialBody)))

@Preview(widthDp = 390, heightDp = 1400)
@Composable
private fun OnboardingProfileDarkPreview() =
    OnboardingPreview(Fixtures.plan(PlanStep.Profile, PlanAnswers(Fixtures.Body)), darkTheme = true)

/** Pregnant: the note says what it changes, and the goal steps step aside. */
@Preview(widthDp = 390, heightDp = 1400)
@Composable
private fun OnboardingProfilePregnantPreview() =
    OnboardingPreview(Fixtures.plan(PlanStep.Profile, PlanAnswers(Fixtures.Body.copy(sex = SexChoice.Pregnant))))

@Preview(widthDp = 390, heightDp = 1400)
@Composable
private fun OnboardingProfileLargeTextPreview() =
    OnboardingPreview(Fixtures.plan(PlanStep.Profile, PlanAnswers(Fixtures.ImperialBody)), largeText = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingActivityPreview() = OnboardingPreview(Fixtures.plan(PlanStep.Activity, PlanAnswers(Fixtures.ImperialBody)))

@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun OnboardingActivityConnectPreview() = OnboardingPreview(Fixtures.activityConnect())

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingActivityConnectedPreview() = OnboardingPreview(
    Fixtures.plan(PlanStep.Activity, PlanAnswers(Fixtures.ImperialBody), health = HealthOffer.Connected(com.opson.libraries.health.HealthSource.HealthConnect)),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingWeightGoalPreview() = OnboardingPreview(Fixtures.plan(PlanStep.Goal, PlanAnswers(Fixtures.ImperialBody)))

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun OnboardingWeightGoalLosePreview() = OnboardingPreview(Fixtures.plan(PlanStep.Goal, Fixtures.LoseTen))

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun OnboardingWeightGoalDarkPreview() = OnboardingPreview(Fixtures.plan(PlanStep.Goal, Fixtures.LoseTen), darkTheme = true)

/** A goal weight under a healthy range for their height: a soft note and Find support, never a block. */
@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun OnboardingWeightGoalBelowHealthyPreview() = OnboardingPreview(
    Fixtures.plan(PlanStep.Goal, Fixtures.LoseTen.copy(goal = Fixtures.LoseTen.goal.copy(target = WeightInput("120", WeightUnit.Lb)))),
)

/** "Lose" with a goal above today's weight says which one to pick. */
@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun OnboardingWeightGoalWrongWayPreview() = OnboardingPreview(
    Fixtures.plan(PlanStep.Goal, Fixtures.LoseTen.copy(goal = Fixtures.LoseTen.goal.copy(target = WeightInput("190", WeightUnit.Lb)))),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingGoalRatePreview() = OnboardingPreview(Fixtures.plan(PlanStep.Rate, Fixtures.LoseTen))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingGoalRateDarkPreview() = OnboardingPreview(Fixtures.plan(PlanStep.Rate, Fixtures.LoseTen), darkTheme = true)

/** Gaining, in kilograms. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingGoalRateGainPreview() = OnboardingPreview(
    Fixtures.plan(
        PlanStep.Rate,
        PlanAnswers(Fixtures.Body, GoalAnswer(com.opson.libraries.nutrition.GoalDirection.Gain, target = WeightInput("68", WeightUnit.Kg))),
    ),
)

/** At the fastest pace Opson plans, which the step says plainly. */
@Preview(widthDp = 390, heightDp = 900)
@Composable
private fun OnboardingGoalRateFastestPreview() = OnboardingPreview(
    Fixtures.plan(PlanStep.Rate, Fixtures.LoseTen.copy(goal = Fixtures.LoseTen.goal.copy(kgPerWeek = 5.0))),
)

/** Macros with grams: the pace in weight, and no calorie amount. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingGoalRateMacrosPreview() =
    OnboardingPreview(Fixtures.plan(PlanStep.Rate, Fixtures.LoseTen, picked = PresetId.MacrosWithGrams))

@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun OnboardingGoalOverviewPreview() = OnboardingPreview(Fixtures.plan(PlanStep.Overview, Fixtures.LoseTen))

@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun OnboardingGoalOverviewDarkPreview() =
    OnboardingPreview(Fixtures.plan(PlanStep.Overview, Fixtures.LoseTen), darkTheme = true)

@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun OnboardingGoalOverviewMaintainPreview() = OnboardingPreview(
    Fixtures.plan(PlanStep.Overview, PlanAnswers(Fixtures.ImperialBody, GoalAnswer(com.opson.libraries.nutrition.GoalDirection.Maintain))),
)

@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun OnboardingGoalOverviewPregnantPreview() = OnboardingPreview(
    Fixtures.plan(PlanStep.Overview, PlanAnswers(Fixtures.Body.copy(sex = SexChoice.Pregnant))),
)

/** Macros with grams: the goal and the date in words, the target kept in the background. */
@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun OnboardingGoalOverviewMacrosPreview() =
    OnboardingPreview(Fixtures.plan(PlanStep.Overview, Fixtures.LoseTen, picked = PresetId.MacrosWithGrams))

/** Details missing (the profile skipped): no target can be worked out yet, and the step says where to add them. */
@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun OnboardingGoalOverviewNeedsDetailsPreview() = OnboardingPreview(
    Fixtures.plan(PlanStep.Overview, Fixtures.LoseTen.copy(body = Fixtures.ImperialBody.copy(height = HeightInput(HeightUnit.FtIn)))),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingCommitmentPreview() = OnboardingPreview(Fixtures.plan(PlanStep.Commit, Fixtures.LoseTen))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingCommitmentDarkPreview() = OnboardingPreview(Fixtures.plan(PlanStep.Commit, Fixtures.LoseTen), darkTheme = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingCommitmentHardPreview() = OnboardingPreview(
    Fixtures.plan(PlanStep.Commit, PlanAnswers(pick = GoalKind.TrackEveryDay), answers = Fixtures.HardAnswers),
)
