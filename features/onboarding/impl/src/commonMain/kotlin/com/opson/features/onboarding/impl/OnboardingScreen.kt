package com.opson.features.onboarding.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.opson.libraries.identity.auth.OAuthProvider
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.icon.Icons
import com.opson.libraries.ui.components.text.OutlinedTextField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.Dimension
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.auth_continue_with_google
import opson.libraries.resources.generated.resources.auth_error_network
import opson.libraries.resources.generated.resources.auth_error_provider_not_enabled
import opson.libraries.resources.generated.resources.auth_sign_in_with_apple
import opson.libraries.resources.generated.resources.common_back
import opson.libraries.resources.generated.resources.onboarding_continue
import opson.libraries.resources.generated.resources.onboarding_continue_as_guest
import opson.libraries.resources.generated.resources.onboarding_create_an_account
import opson.libraries.resources.generated.resources.onboarding_display_name_invalid
import opson.libraries.resources.generated.resources.onboarding_display_name_label
import opson.libraries.resources.generated.resources.onboarding_display_name_taken
import opson.libraries.resources.generated.resources.onboarding_finishing
import opson.libraries.resources.generated.resources.onboarding_oauth_failed
import opson.libraries.resources.generated.resources.onboarding_opening_browser
import opson.libraries.resources.generated.resources.onboarding_pick_display_name_subtitle
import opson.libraries.resources.generated.resources.onboarding_pick_display_name_title
import opson.libraries.resources.generated.resources.onboarding_sign_in_with_email
import opson.libraries.resources.generated.resources.onboarding_suggest_another
import opson.libraries.resources.generated.resources.onboarding_waiting_for_apple
import opson.libraries.resources.generated.resources.onboarding_welcome_subtitle
import opson.libraries.resources.generated.resources.onboarding_welcome_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Two-step onboarding UI. [OnboardingStep.Welcome] offers the entry paths
 * (guest / OAuth / email); [OnboardingStep.PickIdentity] commits a display
 * name and finishes the flow. All routing decisions live in
 * [OnboardingViewModel] — this composable is a pure render of the state.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
) {
    Screen(
        contentWindowInsets = WindowInsets.systemBars,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimension.D800),
            ) {
                when (state.step) {
                    OnboardingStep.Welcome -> WelcomeStep(state, onAction)
                    OnboardingStep.PickIdentity -> PickIdentityStep(state, onAction)
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
) {
    val busy = state.oauthInFlight != null

    Spacer(modifier = Modifier.height(Dimension.D1200))
    Text(
        text = stringResource(Res.string.onboarding_welcome_title),
        typography = AppTheme.typography.Heading.H800,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(Dimension.D400))
    Text(
        text = stringResource(Res.string.onboarding_welcome_subtitle),
        typography = AppTheme.typography.Body.B500,
        color = AppTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(Dimension.D1200))

    ButtonPrimary(
        onClick = { onAction(OnboardingAction.ContinueAsGuest) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(Res.string.onboarding_continue_as_guest))
    }

    Spacer(modifier = Modifier.height(Dimension.D500))

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
        Spacer(modifier = Modifier.height(Dimension.D400))
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
        Spacer(modifier = Modifier.height(Dimension.D400))
        Text(
            text = stringResource(
                when (it) {
                    OnboardingAuthError.OAuthProviderNotEnabled ->
                        Res.string.auth_error_provider_not_enabled
                    OnboardingAuthError.OAuthNetworkError -> Res.string.auth_error_network
                    OnboardingAuthError.OAuthFailed -> Res.string.onboarding_oauth_failed
                },
            ),
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.danger,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(modifier = Modifier.height(Dimension.D700))

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
    Spacer(modifier = Modifier.height(Dimension.D800))
}

@Composable
private fun PickIdentityStep(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
) {
    val canGoBack = !state.creationStarted && !state.identityClaimed

    Spacer(modifier = Modifier.height(Dimension.D200))
    if (canGoBack) {
        ButtonGhost(
            onClick = { onAction(OnboardingAction.Back) },
            icon = Icons.ChevronLeft(null),
        ) {
            Text(stringResource(Res.string.common_back))
        }
    }
    Spacer(modifier = Modifier.height(Dimension.D700))

    Text(
        text = stringResource(Res.string.onboarding_pick_display_name_title),
        typography = AppTheme.typography.Heading.H700,
    )
    Spacer(modifier = Modifier.height(Dimension.D300))
    Text(
        text = stringResource(Res.string.onboarding_pick_display_name_subtitle),
        typography = AppTheme.typography.Body.B500,
        color = AppTheme.colors.textSecondary,
    )

    Spacer(modifier = Modifier.height(Dimension.D700))

    OutlinedTextField(
        value = state.displayName,
        onValueChange = { onAction(OnboardingAction.DisplayNameChanged(it)) },
        enabled = !state.isFinishing,
        singleLine = true,
        isError = state.saveError != null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(
            onGo = { onAction(OnboardingAction.ContinueFromPickIdentity) },
        ),
        label = { Text(stringResource(Res.string.onboarding_display_name_label)) },
        supportingText = state.saveError?.let {
            {
                Text(
                    text = stringResource(
                        when (it) {
                            OnboardingSaveError.DisplayNameTaken ->
                                Res.string.onboarding_display_name_taken
                            OnboardingSaveError.InvalidDisplayName ->
                                Res.string.onboarding_display_name_invalid
                        },
                    ),
                    typography = AppTheme.typography.Body.B400,
                    color = AppTheme.colors.danger,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(Dimension.D300))

    ButtonGhost(
        onClick = { onAction(OnboardingAction.RegenerateDisplayName) },
        enabled = !state.isFinishing,
    ) {
        Text(stringResource(Res.string.onboarding_suggest_another))
    }

    Spacer(modifier = Modifier.height(Dimension.D800))

    ButtonPrimary(
        onClick = { onAction(OnboardingAction.ContinueFromPickIdentity) },
        enabled = !state.isFinishing,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (state.isFinishing) {
                stringResource(Res.string.onboarding_finishing)
            } else {
                stringResource(Res.string.onboarding_continue)
            },
        )
    }
    Spacer(modifier = Modifier.height(Dimension.D800))
}

@Preview
@Composable
private fun OnboardingScreenPreview_Welcome() {
    PreviewContent {
        OnboardingScreen(
            state = OnboardingState(appleEnabled = true),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun OnboardingScreenPreview_PickIdentity() {
    PreviewContent {
        OnboardingScreen(
            state = OnboardingState(
                step = OnboardingStep.PickIdentity,
                displayName = "QuietFox72",
            ),
            onAction = {},
        )
    }
}
