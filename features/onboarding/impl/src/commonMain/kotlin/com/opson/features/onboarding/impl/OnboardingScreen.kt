package com.opson.features.onboarding.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.opson.libraries.identity.auth.OAuthProvider
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.ChoiceRow
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.Dimension
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.auth_continue_with_google
import opson.libraries.resources.generated.resources.auth_error_network
import opson.libraries.resources.generated.resources.auth_error_provider_not_enabled
import opson.libraries.resources.generated.resources.auth_sign_in_with_apple
import opson.libraries.resources.generated.resources.common_back
import opson.libraries.resources.generated.resources.onboarding_create_an_account
import opson.libraries.resources.generated.resources.onboarding_finishing
import opson.libraries.resources.generated.resources.onboarding_next
import opson.libraries.resources.generated.resources.onboarding_oauth_failed
import opson.libraries.resources.generated.resources.onboarding_opening_browser
import opson.libraries.resources.generated.resources.onboarding_progress
import opson.libraries.resources.generated.resources.onboarding_reco_change
import opson.libraries.resources.generated.resources.onboarding_reco_kicker
import opson.libraries.resources.generated.resources.onboarding_reco_or
import opson.libraries.resources.generated.resources.onboarding_reco_see
import opson.libraries.resources.generated.resources.onboarding_set_it_up
import opson.libraries.resources.generated.resources.onboarding_sign_in_with_email
import opson.libraries.resources.generated.resources.onboarding_skip
import opson.libraries.resources.generated.resources.onboarding_skip_all
import opson.libraries.resources.generated.resources.onboarding_start_with
import opson.libraries.resources.generated.resources.onboarding_waiting_for_apple
import opson.libraries.resources.generated.resources.onboarding_welcome_questions
import opson.libraries.resources.generated.resources.onboarding_welcome_subtitle
import opson.libraries.resources.generated.resources.onboarding_welcome_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Onboarding: Welcome, four skippable questions, then a starting mode. All
 * routing decisions live in [OnboardingViewModel]; this is a pure render of
 * the state. It shows no nutrition numbers.
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
            }
        }
    }
}

/**
 * Scrolls as one column, at least a screen tall, so the buttons sit at the
 * bottom on a tall phone and follow the copy on a short one or with large text.
 */
@Composable
private fun WelcomeStep(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
) {
    val busy = state.oauthInFlight != null || state.isFinishing

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = AppTheme.spacing.page),
        ) {
            Spacer(Modifier.height(Dimension.D1000))
            Column(verticalArrangement = Arrangement.spacedBy(Dimension.D700)) {
                Text(
                    text = stringResource(Res.string.onboarding_welcome_title),
                    typography = AppTheme.type.secondaryStrong,
                    color = AppTheme.colors.muted,
                )
                Text(
                    text = stringResource(Res.string.onboarding_welcome_subtitle),
                    typography = AppTheme.type.subH1,
                    color = AppTheme.colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.onboarding_welcome_questions),
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                )
            }

            Spacer(Modifier.height(Dimension.D1000))
            Spacer(Modifier.weight(1f))

            ButtonPrimary(
                onClick = { onAction(OnboardingAction.ContinueAsGuest) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.onboarding_set_it_up))
            }
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

            Spacer(Modifier.height(Dimension.D700))
            SignInOptions(state, busy, onAction)
            Spacer(Modifier.height(Dimension.D800))
        }
    }
}

/** The existing account paths, unchanged: Google, Apple on iOS, then email. */
@Composable
private fun SignInOptions(
    state: OnboardingState,
    busy: Boolean,
    onAction: (OnboardingAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
        ButtonSecondary(
            onClick = { onAction(OnboardingAction.SignInWithOAuth(OAuthProvider.Google)) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.oauthInFlight == OAuthProvider.Google) {
                    stringResource(Res.string.onboarding_opening_browser)
                } else {
                    stringResource(Res.string.auth_continue_with_google)
                },
            )
        }

        if (state.appleEnabled) {
            ButtonSecondary(
                onClick = { onAction(OnboardingAction.SignInWithApple) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.oauthInFlight == OAuthProvider.Apple) {
                        stringResource(Res.string.onboarding_waiting_for_apple)
                    } else {
                        stringResource(Res.string.auth_sign_in_with_apple)
                    },
                )
            }
        }

        state.authError?.let {
            Text(
                text = stringResource(
                    when (it) {
                        OnboardingAuthError.OAuthProviderNotEnabled -> Res.string.auth_error_provider_not_enabled
                        OnboardingAuthError.OAuthNetworkError -> Res.string.auth_error_network
                        OnboardingAuthError.OAuthFailed -> Res.string.onboarding_oauth_failed
                    },
                ),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.danger,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ButtonGhost(
            onClick = { onAction(OnboardingAction.SignIn) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.onboarding_sign_in_with_email))
        }
        ButtonGhost(
            onClick = { onAction(OnboardingAction.SignUp) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.onboarding_create_an_account))
        }
    }
}

@Composable
private fun ColumnScope.QuestionStep(
    state: OnboardingState,
    question: SetupQuestion,
    onAction: (OnboardingAction) -> Unit,
) {
    ScrollingBody {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
        ) {
            if (state.canGoBack) BackButton(onAction)
            Text(
                text = stringResource(Res.string.onboarding_progress, question.number),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
                modifier = Modifier.weight(1f),
            )
            ButtonGhost(
                onClick = { onAction(OnboardingAction.SkipQuestionTapped) },
                enabled = !state.isFinishing,
            ) {
                Text(stringResource(Res.string.onboarding_skip))
            }
        }

        Text(
            text = stringResource(question.title),
            typography = AppTheme.type.subH1,
            color = AppTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        question.subtitle?.let {
            Text(
                text = stringResource(it),
                typography = AppTheme.type.body,
                color = AppTheme.colors.muted,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
            state.options.forEach { choice ->
                ChoiceRow(
                    label = stringResource(choice.option.label),
                    selected = choice.selected,
                    onClick = { onAction(OnboardingAction.OptionTapped(choice.option)) },
                    role = if (question.multiSelect) Role.Checkbox else Role.RadioButton,
                )
            }
        }
    }

    PinnedPrimary(
        label = stringResource(Res.string.onboarding_next),
        enabled = !state.isFinishing,
        onClick = { onAction(OnboardingAction.NextTapped) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.RecommendationStep(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
) {
    val chosenLabel = stringResource(state.chosen.label)

    ScrollingBody {
        if (state.canGoBack) BackButton(onAction)

        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
            Text(
                text = stringResource(Res.string.onboarding_reco_kicker),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
            Text(
                text = chosenLabel,
                typography = AppTheme.type.subH1,
                color = AppTheme.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }

        EdgeCard(edgeToEdge = false) {
            Text(
                text = stringResource(Res.string.onboarding_reco_see),
                typography = AppTheme.type.secondarySmallStrong,
                color = AppTheme.colors.muted,
            )
            Text(
                text = stringResource(state.chosen.line),
                typography = AppTheme.type.body,
                color = AppTheme.colors.ink,
            )
        }

        if (state.alternatives.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
                Text(
                    text = stringResource(Res.string.onboarding_reco_or),
                    typography = AppTheme.type.secondarySmallStrong,
                    color = AppTheme.colors.muted,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
                    verticalArrangement = Arrangement.spacedBy(Dimension.D300),
                ) {
                    state.alternatives.forEach { preset ->
                        AlternativeChip(preset, enabled = !state.isFinishing, onAction = onAction)
                    }
                }
            }
        }

        Text(
            text = stringResource(Res.string.onboarding_reco_change),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
    }

    PinnedPrimary(
        label = if (state.isFinishing) {
            stringResource(Res.string.onboarding_finishing)
        } else {
            stringResource(Res.string.onboarding_start_with, chosenLabel)
        },
        enabled = !state.isFinishing,
        onClick = { onAction(OnboardingAction.StartTapped) },
    )
}

@Composable
private fun AlternativeChip(
    preset: PresetId,
    enabled: Boolean,
    onAction: (OnboardingAction) -> Unit,
) {
    PillChip(
        label = stringResource(preset.label),
        selected = false,
        onClick = { onAction(OnboardingAction.AlternativeTapped(preset)) },
        enabled = enabled,
        role = Role.Button,
        modifier = Modifier.defaultMinSize(minHeight = AppTheme.spacing.touchTarget),
    )
}

@Composable
private fun ColumnScope.ScrollingBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppTheme.spacing.page)
            .padding(top = Dimension.D500, bottom = Dimension.D700),
        verticalArrangement = Arrangement.spacedBy(Dimension.D700),
        content = content,
    )
}

/** The primary button stays at the bottom however long the options run. */
@Composable
private fun PinnedPrimary(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ButtonPrimary(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.page)
            .padding(top = Dimension.D500, bottom = Dimension.D700),
    ) {
        Text(label)
    }
}

@Composable
private fun BackButton(onAction: (OnboardingAction) -> Unit) {
    RoundIconButton(
        icon = AppIcons.ChevronLeft.described(stringResource(Res.string.common_back)),
        onClick = { onAction(OnboardingAction.Back) },
        size = AppTheme.spacing.touchTarget,
        containerColor = AppTheme.colors.surface,
        contentColor = AppTheme.colors.ink,
    )
}

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
private fun OnboardingWhyEmptyPreview() = OnboardingPreview(OnboardingFixtures.question(SetupQuestion.Reason))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingWhyPickedPreview() = OnboardingPreview(
    OnboardingFixtures.question(
        SetupQuestion.Reason,
        SetupAnswers(why = setOf(Why.Curious, Why.EatingRegularly)),
    ),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingWhyPickedDarkPreview() = OnboardingPreview(
    OnboardingFixtures.question(
        SetupQuestion.Reason,
        SetupAnswers(why = setOf(Why.Curious, Why.EatingRegularly)),
    ),
    darkTheme = true,
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingFeelEmptyPreview() = OnboardingPreview(OnboardingFixtures.question(SetupQuestion.Feeling))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingFeelPickedPreview() = OnboardingPreview(
    OnboardingFixtures.question(SetupQuestion.Feeling, SetupAnswers(feel = NumbersFeel.RatherNot)),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingNoticeEmptyPreview() = OnboardingPreview(OnboardingFixtures.question(SetupQuestion.Focus))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingNoticePickedPreview() = OnboardingPreview(
    OnboardingFixtures.question(SetupQuestion.Focus, SetupAnswers(notice = setOf(Notice.HowEatingFeels))),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingHardEmptyPreview() = OnboardingPreview(OnboardingFixtures.question(SetupQuestion.Hard))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingHardPickedPreview() = OnboardingPreview(
    OnboardingFixtures.question(SetupQuestion.Hard, SetupAnswers(hard = true)),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingWhyLargeTextPreview() = OnboardingPreview(
    OnboardingFixtures.question(SetupQuestion.Reason, SetupAnswers(why = setOf(Why.MedicalNeed))),
    largeText = true,
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingRecommendationPreview() = OnboardingPreview(OnboardingFixtures.recommendation())

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingRecommendationDarkPreview() =
    OnboardingPreview(OnboardingFixtures.recommendation(), darkTheme = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingRecommendationHardPreview() =
    OnboardingPreview(OnboardingFixtures.recommendation(OnboardingFixtures.HardAnswers))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingRecommendationPickedPreview() =
    OnboardingPreview(OnboardingFixtures.recommendation(picked = PresetId.Blind))
