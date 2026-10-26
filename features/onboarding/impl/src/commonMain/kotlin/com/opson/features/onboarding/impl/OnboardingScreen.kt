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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.ChoiceRow
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.PageHeaderDefaults
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SegmentedBar
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
import opson.libraries.resources.generated.resources.onboarding_account_body
import opson.libraries.resources.generated.resources.onboarding_account_coach
import opson.libraries.resources.generated.resources.onboarding_account_email
import opson.libraries.resources.generated.resources.onboarding_account_export
import opson.libraries.resources.generated.resources.onboarding_account_failed
import opson.libraries.resources.generated.resources.onboarding_account_google
import opson.libraries.resources.generated.resources.onboarding_account_google_mark
import opson.libraries.resources.generated.resources.onboarding_account_in_use
import opson.libraries.resources.generated.resources.onboarding_account_keep
import opson.libraries.resources.generated.resources.onboarding_account_later
import opson.libraries.resources.generated.resources.onboarding_account_opening_browser
import opson.libraries.resources.generated.resources.onboarding_account_private
import opson.libraries.resources.generated.resources.onboarding_account_title
import opson.libraries.resources.generated.resources.onboarding_account_waiting_for_apple
import opson.libraries.resources.generated.resources.onboarding_all_modes
import opson.libraries.resources.generated.resources.onboarding_continue
import opson.libraries.resources.generated.resources.onboarding_feel_footnote
import opson.libraries.resources.generated.resources.onboarding_finishing
import opson.libraries.resources.generated.resources.onboarding_get_started
import opson.libraries.resources.generated.resources.onboarding_have_account
import opson.libraries.resources.generated.resources.onboarding_plan_free
import opson.libraries.resources.generated.resources.onboarding_plan_free_includes
import opson.libraries.resources.generated.resources.onboarding_plan_plus
import opson.libraries.resources.generated.resources.onboarding_plan_plus_includes
import opson.libraries.resources.generated.resources.onboarding_plan_plus_later
import opson.libraries.resources.generated.resources.onboarding_progress
import opson.libraries.resources.generated.resources.onboarding_progress_description
import opson.libraries.resources.generated.resources.onboarding_progress_optional
import opson.libraries.resources.generated.resources.onboarding_reco_change
import opson.libraries.resources.generated.resources.onboarding_reco_kept_out
import opson.libraries.resources.generated.resources.onboarding_reco_kicker
import opson.libraries.resources.generated.resources.onboarding_reco_or
import opson.libraries.resources.generated.resources.onboarding_reco_see
import opson.libraries.resources.generated.resources.onboarding_see_starting_point
import opson.libraries.resources.generated.resources.onboarding_sign_in
import opson.libraries.resources.generated.resources.onboarding_skip
import opson.libraries.resources.generated.resources.onboarding_skip_all
import opson.libraries.resources.generated.resources.onboarding_start_with
import opson.libraries.resources.generated.resources.onboarding_statement_body
import opson.libraries.resources.generated.resources.onboarding_statement_quote
import opson.libraries.resources.generated.resources.onboarding_welcome_body
import opson.libraries.resources.generated.resources.onboarding_welcome_subtitle
import opson.libraries.resources.generated.resources.onboarding_welcome_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Onboarding (design v3, 1.1 to 1.6): Welcome and the plan, three questions, the optional
 * statement, the starting mode and the optional account step. All routing lives in
 * [OnboardingViewModel]; this is a pure render of the state. It shows no nutrition numbers.
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
                is OnboardingStep.Question -> QuestionStep(state, step.question, onAction)
                OnboardingStep.Recommendation -> RecommendationStep(state, onAction)
                OnboardingStep.Account -> AccountStep(state, onAction)
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
                typography = AppTheme.type.body,
                color = AppTheme.colors.muted,
            )
            Spacer(Modifier.height(Dimension.D900))
            PlanCard()

            Spacer(Modifier.height(Dimension.D900))
            Spacer(Modifier.weight(1f))

            ButtonPrimary(
                onClick = { onAction(OnboardingAction.GetStartedTapped) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.onboarding_get_started))
            }
            Spacer(Modifier.height(Dimension.D100))
            ButtonGhost(
                onClick = { onAction(OnboardingAction.SkipAllTapped) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.isFinishing) Res.string.onboarding_finishing else Res.string.onboarding_skip_all,
                    ),
                )
            }
            SignInLink(enabled = !busy, onClick = { onAction(OnboardingAction.SignInTapped) })
            Spacer(Modifier.height(Dimension.D300))
        }
    }
}

/**
 * The plan before setup. 0.1 sells nothing, so Plus says "Coming later" and shows no price (a
 * price with no way to buy it is an App Review rejection).
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
            note = null,
            includes = stringResource(Res.string.onboarding_plan_free_includes),
        )
        HorizontalDivider(color = AppTheme.colors.line)
        PlanRow(
            name = stringResource(Res.string.onboarding_plan_plus),
            note = stringResource(Res.string.onboarding_plan_plus_later),
            includes = stringResource(Res.string.onboarding_plan_plus_includes),
        )
    }
}

@Composable
private fun PlanRow(name: String, note: String?, includes: String) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Dimension.D700),
    ) {
        Column(modifier = Modifier.weight(PLAN_NAME_WEIGHT)) {
            Text(text = name, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
            if (note != null) {
                Text(text = note, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
            }
        }
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

/** "Have an account? Sign in", one 48 dp target. Returning users take the existing sign-in flow. */
@Composable
private fun SignInLink(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouchTarget)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .bounceClick(enabled = enabled, scaleDown = 0.97f, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(Dimension.D100, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.onboarding_have_account),
            typography = AppTheme.type.body,
            color = AppTheme.colors.muted,
        )
        Text(
            text = stringResource(Res.string.onboarding_sign_in),
            typography = AppTheme.type.bodyBold,
            color = AppTheme.colors.accent,
        )
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
        onAction = onAction,
        progress = state.progress,
        canSkip = !state.isFinishing,
    )

    ScrollingBody {
        Text(
            text = stringResource(
                if (statement) Res.string.onboarding_progress_optional else Res.string.onboarding_progress,
                question.number,
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
                typography = AppTheme.type.secondary,
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
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
        }
    }

    PinnedPrimary(
        label = stringResource(if (statement) Res.string.onboarding_see_starting_point else Res.string.onboarding_continue),
        enabled = !state.isFinishing,
        onClick = { onAction(OnboardingAction.ContinueTapped) },
    )
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
            typography = AppTheme.type.sheetTitle,
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
            typography = AppTheme.type.h1,
            color = AppTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Dimension.D200))
        Text(
            text = stringResource(state.chosen.line),
            typography = AppTheme.type.body,
            color = AppTheme.colors.muted,
        )
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
        PreviewMark.Shown -> AppIcons.Visibility
        PreviewMark.Softened -> AppIcons.BlurOn
        PreviewMark.Later -> AppIcons.Schedule
        PreviewMark.Hidden -> AppIcons.VisibilityOff
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

// ---------- Account ----------

/**
 * The optional last step: claim the guest so logs and settings survive a new phone. Apple on iOS,
 * Google, then email; "Keep it on this phone" is a normal answer.
 */
@Composable
private fun ColumnScope.AccountStep(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
) {
    val enabled = state.canClaim

    OnboardingTopBar(canGoBack = state.canGoBack, onAction = onAction)

    ScrollingBody {
        Text(
            text = stringResource(Res.string.onboarding_account_title),
            typography = AppTheme.type.subH1,
            color = AppTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Dimension.D500))
        Text(
            text = stringResource(Res.string.onboarding_account_body),
            typography = AppTheme.type.body,
            color = AppTheme.colors.muted,
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
                    onClick = { onAction(OnboardingAction.SaveWithAppleTapped) },
                )
            }
            AccountButton(
                label = stringResource(
                    if (state.accountInFlight == AccountMethod.Google) {
                        Res.string.onboarding_account_opening_browser
                    } else {
                        Res.string.onboarding_account_google
                    },
                ),
                enabled = enabled,
                onClick = { onAction(OnboardingAction.SaveWithGoogleTapped) },
                leading = { GoogleMark() },
            )
            AccountButton(
                label = stringResource(Res.string.onboarding_account_email),
                enabled = enabled,
                onClick = { onAction(OnboardingAction.SaveWithEmailTapped) },
                leading = { Icon(icon = AppIcons.Mail, size = IconGlyph, color = AppTheme.colors.ink) },
            )
            state.accountError?.let { AccountErrorText(it) }
        }

        Spacer(Modifier.height(Dimension.D700))
        EdgeCard(
            edgeToEdge = false,
            contentPadding = EdgeCardDefaults.InsetPadding,
            verticalArrangement = Arrangement.spacedBy(Dimension.D300),
        ) {
            IconLine(AppIcons.LockOutlined, AppTheme.colors.accent, stringResource(Res.string.onboarding_account_private))
            IconLine(AppIcons.Download, AppTheme.colors.accent, stringResource(Res.string.onboarding_account_export))
            IconLine(AppIcons.Group, AppTheme.colors.accent, stringResource(Res.string.onboarding_account_coach))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PagePadding)
            .padding(top = Dimension.D300, bottom = Dimension.D700),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ButtonGhost(
            onClick = { onAction(OnboardingAction.KeepOnPhoneTapped) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.onboarding_account_keep))
        }
        Text(
            text = stringResource(Res.string.onboarding_account_later),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
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
                OnboardingAccountError.AccountInUse -> Res.string.onboarding_account_in_use
                OnboardingAccountError.ProviderNotEnabled -> Res.string.auth_error_provider_not_enabled
                OnboardingAccountError.Offline -> Res.string.auth_error_network
                OnboardingAccountError.Failed -> Res.string.onboarding_account_failed
            },
        ),
        typography = AppTheme.type.secondary,
        color = AppTheme.colors.ink,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------- Shared pieces ----------

/** Icon glyphs on these steps are 24 dp, as Material draws them. */
private val IconGlyph = Dimension.D900

/** An icon and one line of body text, as the preview cards and the account card list them. */
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
 * steps a segmented progress bar and Skip. Laid out like [PageHeaderDefaults]: 64 dp tall, the
 * arrow's glyph 16 dp from the edge.
 */
@Composable
private fun OnboardingTopBar(
    canGoBack: Boolean,
    onAction: (OnboardingAction) -> Unit,
    progress: Int = 0,
    canSkip: Boolean = false,
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
            val description = stringResource(Res.string.onboarding_progress_description, progress)
            Spacer(Modifier.width(Dimension.D500))
            SegmentedBar(
                filled = progress,
                total = SetupQuestion.Count,
                color = AppTheme.colors.accent,
                height = Dimension.D100,
                gap = Dimension.D100,
                modifier = Modifier.weight(1f).semantics { contentDescription = description },
            )
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PagePadding)
            .padding(top = Dimension.D500, bottom = Dimension.D700),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimension.D500),
    ) {
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
private fun OnboardingNoticePreview() = OnboardingPreview(
    OnboardingFixtures.question(SetupQuestion.Focus, SetupAnswers(notice = Notice.Patterns)),
)

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
    OnboardingPreview(OnboardingFixtures.recommendation(OnboardingFixtures.HardAnswers, picked = PresetId.Blind))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingRecommendationAllModesPreview() = OnboardingPreview(
    OnboardingFixtures.recommendation(OnboardingFixtures.DesignAnswers, allModes = true),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingAccountPreview() = OnboardingPreview(OnboardingFixtures.account())

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingAccountDarkPreview() = OnboardingPreview(OnboardingFixtures.account(), darkTheme = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingAccountIosPreview() = OnboardingPreview(OnboardingFixtures.account(appleEnabled = true))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingAccountInUsePreview() =
    OnboardingPreview(OnboardingFixtures.account(error = OnboardingAccountError.AccountInUse))
