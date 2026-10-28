package com.opson.features.onboarding.impl

import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.Catching
import com.opson.libraries.core.isiOS
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.identity.auth.AppleSignInCoordinator
import com.opson.libraries.identity.auth.AuthOutcome
import com.opson.libraries.identity.auth.AuthOutcomeClassifier
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.OAuthProvider
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.identity.auth.awaitCredential
import com.opson.libraries.opson.AppCache
import me.tatarka.inject.annotations.Inject

/**
 * Drives the sign-in screen. On submit:
 *  - validate inputs locally (non-empty email + ≥ 6 char password)
 *  - call `authRepository.signInWithEmail(...)`
 *  - on success: an account that existed before is set up already, so mark
 *    onboarded + emit [SignInEvent.NavigateToToday]; a brand-new one (Sign in
 *    with Apple makes the account on the spot) emits [SignInEvent.NavigateToSetup]
 *    and stays not onboarded, so setup runs for it
 *  - on failure: surface a specific error message
 *
 * No try/catch — the repository returns sealed outcome types so we just
 * pattern-match on the result. `Success` doesn't carry an identity
 * payload — the new auth state is on `AuthRepository.observe()` and the
 * profile follows via `ProfileRepository` automatically.
 */
@Inject
class SignInViewModel(
    private val authRepository: AuthRepository,
    private val appCache: AppCache,
    private val appleSignInCoordinator: AppleSignInCoordinator,
    private val authOutcomeClassifier: AuthOutcomeClassifier,
) : SEAViewModel<SignInState, SignInEvent, SignInAction>(
    initialStateArg = SignInState(
        // Apple is the native flow only, which is iOS-only.
        appleEnabled = BuildInfo.isiOS(),
    ),
) {

    // Login flow is bug-prone enough to want a paper trail. Logs are non-PII —
    // never the raw email or password, only shape (length / has-@) and the
    // typed outcome — so they're safe to ship and to attach to a bug report.
    private val logger = KLog.withTag("SignInViewModel")

    override suspend fun handleAction(action: SignInAction) {
        when (action) {
            is SignInAction.EmailChanged -> action.updateState {
                it.copy(email = action.value, error = null)
            }
            is SignInAction.PasswordChanged -> action.updateState {
                it.copy(password = action.value, error = null)
            }
            is SignInAction.Submit -> action.run {
                val current = state
                logger.d {
                    "Submit: emailLen=${current.email.trim().length}, hasAt=${current.email.contains('@')}, " +
                        "pwLen=${current.password.length}, canSubmit=${current.canSubmit}"
                }
                if (!current.canSubmit) {
                    logger.w { "Submit ignored — canSubmit=false (in-flight or failed local validation)" }
                    return@run
                }

                updateState { it.copy(isSubmitting = true, error = null) }
                logger.i { "Submit: calling signInWithEmail" }
                handleSignInOutcome(
                    authRepository.signInWithEmail(
                        email = current.email.trim(),
                        password = current.password,
                    ),
                )
            }

            is SignInAction.SignInWithOAuth -> action.run {
                logger.i { "SignInWithOAuth(${action.provider}): starting" }
                updateState { it.copy(isSubmitting = true, error = null) }
                // Returns once the browser has redirected back with a session, so a new Google
                // account is told apart from a returning one like any other.
                handleSignInOutcome(authRepository.signInWithOAuth(action.provider))
            }

            is SignInAction.SignInWithApple -> action.run {
                logger.i { "SignInWithApple: requesting native credential" }
                updateState { it.copy(isSubmitting = true, error = null) }
                Catching { appleSignInCoordinator.awaitCredential() }
                    .logOnFailure { "Apple credential request failed (sign-in)" }
                    .fold(
                        onSuccess = { credential ->
                            if (credential == null) {
                                logger.i { "SignInWithApple: credential null — user dismissed" }
                                updateState { it.copy(isSubmitting = false) } // dismissed
                            } else {
                                logger.i { "SignInWithApple: credential obtained — calling signInWithApple" }
                                handleSignInOutcome(authRepository.signInWithApple(credential))
                            }
                        },
                        // logOnFailure above already reported it.
                        onFailure = {
                            updateState { it.copy(isSubmitting = false, error = SignInError.Unknown) }
                        },
                    )
            }
        }
    }

    /** Receiver is required so [updateState] (defined on `A`) resolves. */
    private suspend fun SignInAction.handleSignInOutcome(outcome: SignInOutcome) {
        logger.i { "Received SignInOutcome=${outcome::class.simpleName}" }
        when (outcome) {
            is SignInOutcome.Success -> {
                // Apple and Google make a new account when the ID has none, so "Sign in" is not
                // always "I have an account". A new account has nothing to restore: it goes
                // through setup rather than landing on Today in Full numbers.
                val isNew = authOutcomeClassifier.classify() == AuthOutcome.SignedUp
                updateState { it.copy(isSubmitting = false) }
                if (isNew) {
                    logger.i { "Success, new account → NavigateToSetup" }
                    sendEvent(SignInEvent.NavigateToSetup)
                } else {
                    appCache.update { it.copy(hasUserOnboarded = true) }
                    logger.i { "Success → marked onboarded, NavigateToToday" }
                    sendEvent(SignInEvent.NavigateToToday)
                }
            }
            is SignInOutcome.InvalidCredentials -> updateState {
                it.copy(isSubmitting = false, error = SignInError.InvalidCredentials)
            }
            is SignInOutcome.EmailNotConfirmed -> {
                updateState { it.copy(isSubmitting = false) }
                logger.i { "EmailNotConfirmed → NavigateToVerifyEmail" }
                sendEvent(SignInEvent.NavigateToVerifyEmail(outcome.email))
            }
            is SignInOutcome.NetworkError -> updateState {
                it.copy(isSubmitting = false, error = SignInError.NetworkError)
            }
            is SignInOutcome.Cancelled -> updateState {
                it.copy(isSubmitting = false)
            }
            is SignInOutcome.ProviderNotEnabled -> updateState {
                it.copy(isSubmitting = false, error = SignInError.ProviderNotEnabled)
            }
            is SignInOutcome.Unknown -> updateState {
                it.copy(isSubmitting = false, error = SignInError.Unknown)
            }
        }
    }
}

data class SignInState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: SignInError? = null,
    /** Native "Sign in with Apple" is iOS-only; hides the button elsewhere. */
    val appleEnabled: Boolean = false,
) {
    /** Cheap client-side gate. Server is the canonical validator. */
    val canSubmit: Boolean
        get() = !isSubmitting && email.contains('@') && password.length >= MIN_PASSWORD_LENGTH

    companion object {
        const val MIN_PASSWORD_LENGTH = 6
    }
}

sealed interface SignInEvent {
    data object NavigateToToday : SignInEvent

    /** A brand-new account: onboarding's questions, with nothing left to claim. */
    data object NavigateToSetup : SignInEvent
    data class NavigateToVerifyEmail(val email: String) : SignInEvent
}

/**
 * Inline error surfaced under the sign-in form. Typed so the VM doesn't
 * hold raw user-facing copy — `AuthScreens.kt` resolves each variant at
 * render time.
 */
sealed interface SignInError {
    data object InvalidCredentials : SignInError
    data object NetworkError : SignInError
    data object ProviderNotEnabled : SignInError
    data object Unknown : SignInError
}

sealed interface SignInAction {
    data class EmailChanged(val value: String) : SignInAction
    data class PasswordChanged(val value: String) : SignInAction
    data object Submit : SignInAction
    data class SignInWithOAuth(val provider: OAuthProvider) : SignInAction
    /** Native "Sign in with Apple" (iOS) — runs the coordinator, then signs in. */
    data object SignInWithApple : SignInAction
}
