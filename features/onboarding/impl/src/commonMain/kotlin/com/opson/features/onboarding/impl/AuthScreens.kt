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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import opson.libraries.resources.generated.resources.auth_back_to_sign_in
import opson.libraries.resources.generated.resources.auth_checking
import opson.libraries.resources.generated.resources.auth_confirm_password_label
import opson.libraries.resources.generated.resources.auth_continue_with_google
import opson.libraries.resources.generated.resources.auth_create_account
import opson.libraries.resources.generated.resources.auth_creating_account
import opson.libraries.resources.generated.resources.auth_email_label
import opson.libraries.resources.generated.resources.auth_error_email_already_registered
import opson.libraries.resources.generated.resources.auth_error_generic
import opson.libraries.resources.generated.resources.auth_error_invalid_credentials
import opson.libraries.resources.generated.resources.auth_error_invalid_email
import opson.libraries.resources.generated.resources.auth_error_network
import opson.libraries.resources.generated.resources.auth_error_provider_not_enabled
import opson.libraries.resources.generated.resources.auth_error_rate_limited
import opson.libraries.resources.generated.resources.auth_error_timeout
import opson.libraries.resources.generated.resources.auth_error_weak_password
import opson.libraries.resources.generated.resources.auth_forgot_password
import opson.libraries.resources.generated.resources.auth_forgot_password_body
import opson.libraries.resources.generated.resources.auth_forgot_password_sent_body
import opson.libraries.resources.generated.resources.auth_forgot_password_sent_title
import opson.libraries.resources.generated.resources.auth_forgot_password_title
import opson.libraries.resources.generated.resources.auth_have_account_sign_in
import opson.libraries.resources.generated.resources.auth_i_clicked_the_link
import opson.libraries.resources.generated.resources.auth_no_account_create_one
import opson.libraries.resources.generated.resources.auth_or_use_email
import opson.libraries.resources.generated.resources.auth_password_label
import opson.libraries.resources.generated.resources.auth_password_min_length_helper
import opson.libraries.resources.generated.resources.auth_passwords_do_not_match
import opson.libraries.resources.generated.resources.auth_resend_email
import opson.libraries.resources.generated.resources.auth_resending
import opson.libraries.resources.generated.resources.auth_send_reset_link
import opson.libraries.resources.generated.resources.auth_sign_in
import opson.libraries.resources.generated.resources.auth_sign_in_subtitle
import opson.libraries.resources.generated.resources.auth_sign_in_title
import opson.libraries.resources.generated.resources.auth_sign_in_with_apple
import opson.libraries.resources.generated.resources.auth_sign_up_subtitle
import opson.libraries.resources.generated.resources.auth_sign_up_title
import opson.libraries.resources.generated.resources.auth_signing_in
import opson.libraries.resources.generated.resources.auth_verify_email_body
import opson.libraries.resources.generated.resources.auth_verify_email_body_no_address
import opson.libraries.resources.generated.resources.auth_verify_email_title
import opson.libraries.resources.generated.resources.auth_verify_resent
import opson.libraries.resources.generated.resources.auth_verify_still_pending
import opson.libraries.resources.generated.resources.common_back
import opson.libraries.resources.generated.resources.common_sending
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Shared shell for the three email/password screens. Same vertical layout
 * everywhere: scrollable content, IME padding so the keyboard doesn't cover
 * the focused field.
 *
 * The intentional repetition between SignIn / SignUp screens (email +
 * password + a single big CTA) doesn't justify a deeper abstraction —
 * each screen's strings, validation, and footer link are different, and
 * extracting an `AuthFormScreen` would mostly move conditionals to a
 * config object.
 */
@Composable
private fun AuthShell(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
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
                Spacer(modifier = Modifier.height(Dimension.D200))
                ButtonGhost(
                    onClick = onBack,
                    icon = Icons.ChevronLeft(null),
                ) {
                    Text(stringResource(Res.string.common_back))
                }
                Spacer(modifier = Modifier.height(Dimension.D700))
                content()
                Spacer(modifier = Modifier.height(Dimension.D800))
            }
        }
    }
}

@Composable
fun SignInScreen(
    state: SignInState,
    onAction: (SignInAction) -> Unit,
    onBack: () -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    // Dropping focus, not hiding the keyboard controller: only that closes the keyboard on iOS.
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onAction(SignInAction.Submit)
    }
    AuthShell(onBack = onBack) {
        Text(
            text = stringResource(Res.string.auth_sign_in_title),
            typography = AppTheme.typography.Heading.H700,
        )
        Spacer(modifier = Modifier.height(Dimension.D300))
        Text(
            text = stringResource(Res.string.auth_sign_in_subtitle),
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(Dimension.D800))
        ButtonSecondary(
            onClick = { onAction(SignInAction.SignInWithOAuth(OAuthProvider.Google)) },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.auth_continue_with_google))
        }
        Spacer(modifier = Modifier.height(Dimension.D400))
        if (state.appleEnabled) {
            // Native sign-in — runs the system sheet via the coordinator, not
            // the web flow. After the sheet, the sign-in itself is a network
            // call, so the button says so rather than just greying out.
            ButtonSecondary(
                onClick = { onAction(SignInAction.SignInWithApple) },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.isSubmitting) Res.string.auth_signing_in else Res.string.auth_sign_in_with_apple,
                    ),
                )
            }
        }
        if (SignInMethods.EMAIL) {
            EmailSignIn(state, onAction, submit, onCreateAccount, onForgotPassword)
        } else {
            // The email form shows errors itself; without it, Apple's still need a place.
            state.error?.let {
                Spacer(modifier = Modifier.height(Dimension.D400))
                ErrorText(it.message())
            }
        }
    }
}

@Composable
private fun EmailSignIn(
    state: SignInState,
    onAction: (SignInAction) -> Unit,
    submit: () -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    Column {
        Spacer(modifier = Modifier.height(Dimension.D700))
        Text(
            text = stringResource(Res.string.auth_or_use_email),
            typography = AppTheme.typography.Body.B400,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Dimension.D700))

        EmailField(
            value = state.email,
            enabled = !state.isSubmitting,
            onChange = { onAction(SignInAction.EmailChanged(it)) },
        )
        Spacer(modifier = Modifier.height(Dimension.D500))
        PasswordField(
            value = state.password,
            onValueChange = { onAction(SignInAction.PasswordChanged(it)) },
            label = stringResource(Res.string.auth_password_label),
            enabled = !state.isSubmitting,
            imeAction = ImeAction.Go,
            onImeAction = { submit() },
        )

        state.error?.let {
            Spacer(modifier = Modifier.height(Dimension.D400))
            ErrorText(it.message())
        }

        Spacer(modifier = Modifier.height(Dimension.D300))

        ButtonGhost(
            onClick = onForgotPassword,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.auth_forgot_password))
        }

        Spacer(modifier = Modifier.height(Dimension.D500))

        ButtonPrimary(
            onClick = { submit() },
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.isSubmitting) {
                    stringResource(Res.string.auth_signing_in)
                } else {
                    stringResource(Res.string.auth_sign_in)
                },
            )
        }

        Spacer(modifier = Modifier.height(Dimension.D400))

        ButtonGhost(
            onClick = onCreateAccount,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.auth_no_account_create_one))
        }
    }
}

@Composable
fun ForgotPasswordScreen(
    state: ForgotPasswordState,
    onAction: (ForgotPasswordAction) -> Unit,
    onBack: () -> Unit,
    onBackToSignIn: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onAction(ForgotPasswordAction.Submit)
    }
    AuthShell(onBack = onBack) {
        Text(
            text = if (state.sent) {
                stringResource(Res.string.auth_forgot_password_sent_title)
            } else {
                stringResource(Res.string.auth_forgot_password_title)
            },
            typography = AppTheme.typography.Heading.H700,
        )
        Spacer(modifier = Modifier.height(Dimension.D300))
        Text(
            text = if (state.sent) {
                stringResource(Res.string.auth_forgot_password_sent_body, state.email.trim())
            } else {
                stringResource(Res.string.auth_forgot_password_body)
            },
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
        )

        if (!state.sent) {
            Spacer(modifier = Modifier.height(Dimension.D700))
            EmailField(
                value = state.email,
                enabled = !state.isSubmitting,
                onChange = { onAction(ForgotPasswordAction.EmailChanged(it)) },
                imeAction = ImeAction.Go,
                onSubmitImeAction = { submit() },
            )
        }

        state.banner?.let { banner ->
            Spacer(modifier = Modifier.height(Dimension.D500))
            ErrorText(
                stringResource(
                    when (banner) {
                        ForgotPasswordState.Banner.RateLimited -> Res.string.auth_error_rate_limited
                        ForgotPasswordState.Banner.NetworkError -> Res.string.auth_error_network
                        ForgotPasswordState.Banner.GenericError -> Res.string.auth_error_generic
                    },
                ),
            )
        }

        Spacer(modifier = Modifier.height(Dimension.D800))

        if (state.sent) {
            ButtonPrimary(
                onClick = onBackToSignIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.auth_back_to_sign_in))
            }
        } else {
            ButtonPrimary(
                onClick = { submit() },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.isSubmitting) {
                        stringResource(Res.string.common_sending)
                    } else {
                        stringResource(Res.string.auth_send_reset_link)
                    },
                )
            }
        }
    }
}

@Composable
fun SignUpScreen(
    state: SignUpState,
    onAction: (SignUpAction) -> Unit,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onAction(SignUpAction.Submit)
    }
    AuthShell(onBack = onBack) {
        Text(
            text = stringResource(Res.string.auth_sign_up_title),
            typography = AppTheme.typography.Heading.H700,
        )
        Spacer(modifier = Modifier.height(Dimension.D300))
        Text(
            text = stringResource(Res.string.auth_sign_up_subtitle),
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(Dimension.D700))

        EmailField(
            value = state.email,
            enabled = !state.isSubmitting,
            onChange = { onAction(SignUpAction.EmailChanged(it)) },
        )
        Spacer(modifier = Modifier.height(Dimension.D500))
        PasswordField(
            value = state.password,
            onValueChange = { onAction(SignUpAction.PasswordChanged(it)) },
            label = stringResource(Res.string.auth_password_label),
            enabled = !state.isSubmitting,
            imeAction = ImeAction.Next,
            helper = stringResource(
                Res.string.auth_password_min_length_helper,
                SignUpState.MIN_PASSWORD_LENGTH,
            ),
        )
        Spacer(modifier = Modifier.height(Dimension.D500))
        PasswordField(
            value = state.confirmPassword,
            onValueChange = { onAction(SignUpAction.ConfirmPasswordChanged(it)) },
            label = stringResource(Res.string.auth_confirm_password_label),
            enabled = !state.isSubmitting,
            imeAction = ImeAction.Go,
            onImeAction = { submit() },
            helper = if (state.passwordMismatch) {
                stringResource(Res.string.auth_passwords_do_not_match)
            } else {
                null
            },
            isError = state.passwordMismatch,
        )

        state.error?.let {
            Spacer(modifier = Modifier.height(Dimension.D400))
            ErrorText(it.message())
        }

        Spacer(modifier = Modifier.height(Dimension.D800))

        ButtonPrimary(
            onClick = { submit() },
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.isSubmitting) {
                    stringResource(Res.string.auth_creating_account)
                } else {
                    stringResource(Res.string.auth_create_account)
                },
            )
        }

        Spacer(modifier = Modifier.height(Dimension.D400))

        ButtonGhost(
            onClick = onSignIn,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.auth_have_account_sign_in))
        }
    }
}

@Composable
fun VerifyEmailScreen(
    state: VerifyEmailState,
    onAction: (VerifyEmailAction) -> Unit,
    onBack: () -> Unit,
) {
    LifecycleResumeEffect(Unit) {
        onAction(VerifyEmailAction.AppResumed)
        onPauseOrDispose { }
    }
    AuthShell(onBack = onBack) {
        Text(
            text = stringResource(Res.string.auth_verify_email_title),
            typography = AppTheme.typography.Heading.H700,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(Dimension.D400))

        Text(
            text = if (state.email.isEmpty()) {
                stringResource(Res.string.auth_verify_email_body_no_address)
            } else {
                stringResource(Res.string.auth_verify_email_body, state.email)
            },
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Dimension.D800))

        state.banner?.let { banner ->
            VerifyEmailBanner(banner)
            Spacer(modifier = Modifier.height(Dimension.D400))
        }

        ButtonPrimary(
            onClick = { onAction(VerifyEmailAction.IClickedTheLink) },
            enabled = !state.isRefreshing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.isRefreshing) {
                    stringResource(Res.string.auth_checking)
                } else {
                    stringResource(Res.string.auth_i_clicked_the_link)
                },
            )
        }

        Spacer(modifier = Modifier.height(Dimension.D400))

        ButtonGhost(
            onClick = { onAction(VerifyEmailAction.Resend) },
            enabled = !state.isResending && !state.isRefreshing && state.email.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.isResending) {
                    stringResource(Res.string.auth_resending)
                } else {
                    stringResource(Res.string.auth_resend_email)
                },
            )
        }
    }
}

// ---- Field helpers ----

@Composable
private fun EmailField(
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    imeAction: ImeAction = ImeAction.Next,
    onSubmitImeAction: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = imeAction,
        ),
        keyboardActions = submitOrMoveOn(onSubmitImeAction),
        label = { Text(stringResource(Res.string.auth_email_label)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    imeAction: ImeAction,
    onImeAction: (() -> Unit)? = null,
    helper: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = submitOrMoveOn(onImeAction),
        label = { Text(label) },
        supportingText = helper?.let {
            {
                Text(
                    text = it,
                    typography = AppTheme.typography.Body.B400,
                    color = if (isError) AppTheme.colors.danger else AppTheme.colors.textSecondary,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Go or Next runs [submit] when the field has one. Without one, Next is left to its default and
 * moves to the next field: a Next handler that does nothing would leave the keyboard stuck.
 */
private fun submitOrMoveOn(submit: (() -> Unit)?): KeyboardActions =
    if (submit == null) KeyboardActions.Default else KeyboardActions(onGo = { submit() }, onNext = { submit() })

/** Announced when it appears: the error arrives after the tap, away from the focused button. */
@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        typography = AppTheme.typography.Body.B500,
        color = AppTheme.colors.danger,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun VerifyEmailBanner(banner: VerifyEmailState.Banner) {
    val (copy, color) = when (banner) {
        VerifyEmailState.Banner.StillPending ->
            Res.string.auth_verify_still_pending to AppTheme.colors.textSecondary
        VerifyEmailState.Banner.ResendSent ->
            Res.string.auth_verify_resent to AppTheme.colors.textSecondary
        VerifyEmailState.Banner.ResendRateLimited ->
            Res.string.auth_error_rate_limited to AppTheme.colors.danger
        VerifyEmailState.Banner.NetworkError ->
            Res.string.auth_error_network to AppTheme.colors.danger
        VerifyEmailState.Banner.GenericError ->
            Res.string.auth_error_generic to AppTheme.colors.danger
    }
    Text(
        text = stringResource(copy),
        typography = AppTheme.typography.Body.B500,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun SignInError.message(): String = when (this) {
    SignInError.InvalidCredentials -> stringResource(Res.string.auth_error_invalid_credentials)
    SignInError.NetworkError -> stringResource(Res.string.auth_error_network)
    SignInError.ProviderNotEnabled -> stringResource(Res.string.auth_error_provider_not_enabled)
    SignInError.Unknown -> stringResource(Res.string.auth_error_generic)
}

@Composable
private fun SignUpError.message(): String = when (this) {
    SignUpError.EmailAlreadyRegistered ->
        stringResource(Res.string.auth_error_email_already_registered)
    is SignUpError.WeakPassword -> stringResource(Res.string.auth_error_weak_password, minLength)
    SignUpError.InvalidEmail -> stringResource(Res.string.auth_error_invalid_email)
    SignUpError.NetworkError -> stringResource(Res.string.auth_error_network)
    SignUpError.Timeout -> stringResource(Res.string.auth_error_timeout)
    SignUpError.Unknown -> stringResource(Res.string.auth_error_generic)
}

@Preview
@Composable
private fun SignInScreenPreview() {
    PreviewContent {
        SignInScreen(
            state = SignInState(email = "person@example.com", password = "••••••••"),
            onAction = {},
            onBack = {},
            onCreateAccount = {},
            onForgotPassword = {},
        )
    }
}

@Preview
@Composable
private fun SignUpScreenPreview_PasswordMismatch() {
    PreviewContent {
        SignUpScreen(
            state = SignUpState(
                email = "person@example.com",
                password = "hunter22ish",
                confirmPassword = "hunter22is",
            ),
            onAction = {},
            onBack = {},
            onSignIn = {},
        )
    }
}

@Preview
@Composable
private fun ForgotPasswordScreenPreview_Sent() {
    PreviewContent {
        ForgotPasswordScreen(
            state = ForgotPasswordState(email = "person@example.com", sent = true),
            onAction = {},
            onBack = {},
            onBackToSignIn = {},
        )
    }
}

@Preview
@Composable
private fun VerifyEmailScreenPreview() {
    PreviewContent {
        VerifyEmailScreen(
            state = VerifyEmailState(
                email = "person@example.com",
                banner = VerifyEmailState.Banner.StillPending,
            ),
            onAction = {},
            onBack = {},
        )
    }
}
