package com.opson.features.onboarding.impl

import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.Catching
import com.opson.libraries.core.isiOS
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.identity.auth.AccountCreationState
import com.opson.libraries.identity.auth.AppleSignInCoordinator
import com.opson.libraries.identity.auth.AppleSignInCredential
import com.opson.libraries.identity.auth.AuthOutcome
import com.opson.libraries.identity.auth.AuthOutcomeClassifier
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.GuestAccountCreator
import com.opson.libraries.identity.auth.LinkIdentityOutcome
import com.opson.libraries.identity.auth.OAuthProvider
import com.opson.libraries.identity.auth.PendingIdentity
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.identity.auth.awaitCredential
import com.opson.libraries.identity.profile.DisplayNameRules
import com.opson.libraries.identity.profile.ProfileRepository
import com.opson.libraries.identity.profile.UpdateProfileOutcome
import com.opson.libraries.opson.AppCache
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.withTimeoutOrNull
import me.tatarka.inject.annotations.Inject

/**
 * Drives the two-step onboarding flow with **deferred account creation** —
 * no account exists on launch; one is minted only when the user commits.
 *   1. **Welcome** — "Continue as guest" advances to step 2 (no auth yet);
 *      "Apple"/"Google" sign-in: a returning account goes straight to Home, a
 *      brand-new one runs through the rest of onboarding (PickIdentity) like a
 *      guest. New-vs-returning comes from the server's new-account signal via
 *      [AuthOutcomeClassifier].
 *   2. **PickIdentity** — edit the display name (prefilled from a client
 *      suggestion). "Continue" kicks off guest-account creation
 *      ([GuestAccountCreator], app-scoped so it survives the ViewModel), joins
 *      on the result with a bounded wait, marks `hasUserOnboarded`, and goes
 *      Home. From here back is blocked — creation is in flight.
 *
 * **Why creation is deferred:** minting an anonymous account on launch leaves
 * an orphan whenever the user then signs into a real account. Onboarding runs
 * entirely unauthenticated, and the account is created at the point of no
 * return.
 *
 * Hard guard on init: if `AppData.hasUserOnboarded` is already true, fire
 * [OnboardingEvent.NavigateToHome] immediately so a returning user that
 * lands on the route bounces to Home.
 */
@Inject
class OnboardingViewModel(
    private val appCache: AppCache,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val authOutcomeClassifier: AuthOutcomeClassifier,
    private val guestAccountCreator: GuestAccountCreator,
    private val appleSignInCoordinator: AppleSignInCoordinator,
) : SEAViewModel<OnboardingState, OnboardingEvent, OnboardingAction>(
    initialStateArg = OnboardingState(
        displayName = DisplayNameSuggester.next(),
        appleEnabled = BuildInfo.isiOS(),
    ),
) {

    private val logger = KLog.withTag("OnboardingFlow")

    private val onboardingStartedAt = TimeSource.Monotonic.markNow()

    /**
     * True once this instance routed to Home (completed, or a returning user
     * bounced/signed in) — suppresses the best-effort `onboarding.abandoned`
     * fired from [onCleared].
     */
    private var exitedToHome = false

    init {
        // Resolve where onboarding opens (Home bounce / identity re-entry /
        // Welcome landing) through the action loop so state updates run in an
        // action scope — mirrors VerifyEmail's init → ResolveEmailFromSession.
        takeAction(OnboardingAction.ResolveEntry)
    }

    private suspend fun OnboardingAction.handleResolveEntry() {
        Catching {
            val authed = authRepository.current() as? AuthState.Authenticated
            when {
                appCache.get().hasUserOnboarded -> {
                    exitedToHome = true
                    sendEvent(OnboardingEvent.NavigateToHome)
                }
                // A real (non-anonymous) account that hasn't finished onboarding
                // is a just-verified email signup bounced back here from
                // VerifyEmail. Drop them straight into identity setup, skipping
                // the guest/OAuth landing they don't need. identityClaimed
                // suppresses the back-to-Welcome control.
                authed != null && !authed.isAnonymous -> {
                    logStepViewed(OnboardingStep.PickIdentity)
                    updateState {
                        it.copy(step = OnboardingStep.PickIdentity, identityClaimed = true)
                    }
                }
                // An anonymous guest session already exists at launch. Guest
                // creation is deferred (a new user is Unauthenticated here), so a
                // live anonymous session can only be a PRIOR guest whose
                // hasUserOnboarded flag was lost — not a new user. Treat them as
                // returning: adopt the onboarded flag and go Home.
                authed != null -> {
                    logger.logEvent("onboarding.auth_selected", "method" to "guest", "returning" to true)
                    appCache.update { it.copy(hasUserOnboarded = true) }
                    exitedToHome = true
                    sendEvent(OnboardingEvent.NavigateToHome)
                }
                else -> logger.logEvent("onboarding.step_viewed", "step" to "welcome")
            }
        }.logOnFailure { "Onboarding entry resolution failed" }
    }

    override suspend fun handleAction(action: OnboardingAction) {
        when (action) {
            OnboardingAction.ResolveEntry -> action.handleResolveEntry()
            OnboardingAction.ContinueAsGuest -> action.handleContinueAsGuest()
            OnboardingAction.SignIn -> sendEvent(OnboardingEvent.NavigateToSignIn)
            OnboardingAction.SignUp -> sendEvent(OnboardingEvent.NavigateToSignUp)
            OnboardingAction.Back -> action.handleBack()
            is OnboardingAction.SignInWithOAuth -> action.handleOAuth(action.provider)
            OnboardingAction.SignInWithApple -> action.handleAppleSignIn()
            is OnboardingAction.DisplayNameChanged -> action.updateState {
                // Editing the name dismisses any stale "taken / invalid"
                // notice from a previous save attempt.
                it.copy(
                    displayName = action.value.take(MAX_DISPLAY_NAME_LENGTH),
                    userEditedName = true,
                    saveError = null,
                )
            }
            OnboardingAction.RegenerateDisplayName -> action.updateState {
                it.copy(displayName = DisplayNameSuggester.next(), userEditedName = true)
            }
            OnboardingAction.ContinueFromPickIdentity -> action.handleContinueFromPickIdentity()
        }
    }

    private suspend fun OnboardingAction.handleContinueAsGuest() {
        // No auth here — the guest account is created later, when the user
        // commits their identity (PickIdentity → Continue). Tapping
        // "Continue as guest" just enters the identity step.
        logger.logEvent("onboarding.auth_selected", "method" to "guest", "returning" to false)
        logStepViewed(OnboardingStep.PickIdentity)
        updateState { it.copy(authError = null, step = OnboardingStep.PickIdentity) }
    }

    private suspend fun OnboardingAction.handleOAuth(provider: OAuthProvider) {
        updateState { it.copy(oauthInFlight = provider, authError = null) }
        when (val outcome = authRepository.signInWithOAuth(provider)) {
            is SignInOutcome.Success -> {
                val authOutcome = authOutcomeClassifier.classify()
                logger.logEvent(
                    "onboarding.auth_selected",
                    "method" to provider.name.lowercase(),
                    "returning" to (authOutcome != AuthOutcome.SignedUp),
                )
                routeAfterSignIn(authOutcome)
            }
            SignInOutcome.Cancelled -> updateState { it.copy(oauthInFlight = null) }
            SignInOutcome.ProviderNotEnabled -> updateState {
                it.copy(oauthInFlight = null, authError = OnboardingAuthError.OAuthProviderNotEnabled)
            }
            is SignInOutcome.NetworkError -> updateState {
                it.copy(oauthInFlight = null, authError = OnboardingAuthError.OAuthNetworkError)
            }
            SignInOutcome.InvalidCredentials,
            is SignInOutcome.EmailNotConfirmed,
            is SignInOutcome.Unknown,
            -> updateState {
                it.copy(oauthInFlight = null, authError = OnboardingAuthError.OAuthFailed)
            }
        }
    }

    /**
     * Route a fresh (non-link) sign-in by its typed [AuthOutcome]:
     *  - [AuthOutcome.SignedUp] — first-ever sign-in for this identity: run
     *    them through the rest of onboarding (PickIdentity) like a guest, so
     *    they pick a name instead of landing cold on Home. `identityClaimed`
     *    suppresses the back-to-Welcome control (the sign-in options no
     *    longer apply).
     *  - [AuthOutcome.SignedIn] — returning account already has a profile, so
     *    skip onboarding straight to Home.
     *
     * [AuthOutcome.Linked] can't reach here (Welcome has no anonymous guest to
     * link onto — guest creation is deferred), so it routes Home like any
     * already-established account.
     */
    private suspend fun OnboardingAction.routeAfterSignIn(outcome: AuthOutcome) {
        when (outcome) {
            AuthOutcome.SignedUp -> {
                logStepViewed(OnboardingStep.PickIdentity)
                updateState {
                    it.copy(
                        oauthInFlight = null,
                        step = OnboardingStep.PickIdentity,
                        identityClaimed = true,
                    )
                }
            }
            AuthOutcome.SignedIn, AuthOutcome.Linked -> {
                appCache.update { it.copy(hasUserOnboarded = true) }
                updateState { it.copy(oauthInFlight = null) }
                exitedToHome = true
                sendEvent(OnboardingEvent.NavigateToHome)
            }
        }
    }

    /**
     * Native "Sign in with Apple". Runs the iOS coordinator for the id token,
     * then either **links** the Apple identity to the current anonymous guest
     * (preserving any progress made as a guest) or, if there's no anonymous
     * session, signs in fresh. A dismissed sheet (`null` credential) is a quiet
     * no-op; only a real failure surfaces an error. Reuses [OnboardingState.oauthInFlight]
     * so the button shows the in-flight state like the OAuth buttons.
     */
    private suspend fun OnboardingAction.handleAppleSignIn() {
        updateState { it.copy(oauthInFlight = OAuthProvider.Apple, authError = null) }
        Catching { appleSignInCoordinator.awaitCredential() }
            .logOnFailure { "Apple credential request failed" }
            .fold(
                onSuccess = { credential ->
                    if (credential == null) {
                        // User dismissed the sheet — quiet no-op.
                        updateState { it.copy(oauthInFlight = null) }
                    } else {
                        finishAppleSignIn(credential)
                    }
                },
                onFailure = {
                    updateState {
                        it.copy(oauthInFlight = null, authError = OnboardingAuthError.OAuthFailed)
                    }
                },
            )
    }

    private suspend fun OnboardingAction.finishAppleSignIn(credential: AppleSignInCredential) {
        // Two shapes of "Continue with Apple", decided by whether we're holding an
        // anonymous guest to link onto:
        //   1. Anonymous guest present → LINK Apple to it (keeps progress) and
        //      carry on through onboarding like any new signup.
        //   2. No anonymous guest (e.g. right after account deletion, which tears
        //      the session down) → SIGN IN. That sign-in may hit a pre-existing
        //      account OR mint a net-new one, so we don't assume — [signInApple]
        //      classifies via the brand-new signal.
        val isAnonymousGuest =
            (authRepository.current() as? AuthState.Authenticated)?.isAnonymous == true
        if (isAnonymousGuest) {
            when (authRepository.linkAppleIdentity(credential)) {
                LinkIdentityOutcome.Success -> {
                    logger.logEvent("onboarding.auth_selected", "method" to "apple", "returning" to false)
                    logStepViewed(OnboardingStep.PickIdentity)
                    updateState {
                        it.copy(
                            oauthInFlight = null,
                            step = OnboardingStep.PickIdentity,
                            identityClaimed = true,
                        )
                    }
                }
                LinkIdentityOutcome.AlreadyOnAnotherAccount -> signInApple(credential)
                else -> failAppleSignIn()
            }
        } else {
            signInApple(credential)
        }
    }

    /**
     * Apple sign-in with no guest to link onto. `signInWithApple` either signs
     * into a pre-existing Apple account OR mints a net-new one (Supabase
     * creates on first Apple OIDC — e.g. after an account deletion left no
     * session). We must NOT assume "existing": classify via
     * [authOutcomeClassifier] exactly like [handleOAuth], so a net-new account
     * runs through onboarding (PickIdentity) instead of getting dumped cold on
     * Home.
     */
    private suspend fun OnboardingAction.signInApple(credential: AppleSignInCredential) {
        if (authRepository.signInWithApple(credential) !is SignInOutcome.Success) {
            failAppleSignIn()
            return
        }
        val outcome = authOutcomeClassifier.classify()
        logger.logEvent(
            "onboarding.auth_selected",
            "method" to "apple",
            "returning" to (outcome != AuthOutcome.SignedUp),
        )
        routeAfterSignIn(outcome)
    }

    private suspend fun OnboardingAction.failAppleSignIn() =
        updateState { it.copy(oauthInFlight = null, authError = OnboardingAuthError.OAuthFailed) }

    /**
     * "Continue" off the identity step — the point of no return. The chosen
     * name is committed:
     *  - A real account already exists (claimed via OAuth/Apple or the
     *    verify-email bounce) → patch the profile; a taken/invalid name
     *    surfaces on the field and keeps the user here to fix it.
     *  - Guest path → kick off guest-account creation ([GuestAccountCreator],
     *    app-scoped) and join on the result with a bounded wait. Success or
     *    failure both complete onboarding — a failed (offline) creation is
     *    flagged degraded and the creator keeps retrying in the background.
     * Either way completion marks `hasUserOnboarded` and navigates Home. We
     * never trap the user on a backend call.
     */
    private suspend fun OnboardingAction.handleContinueFromPickIdentity() {
        val identity = PendingIdentity(
            displayName = state.displayName.trim().takeIf { it.isNotEmpty() },
        )
        updateState { it.copy(saveError = null, isFinishing = true, creationStarted = true) }

        if (authRepository.current() is AuthState.Authenticated) {
            val outcome = Catching { profileRepository.update(displayName = identity.displayName) }
                .logOnFailure { "Onboarding profile update failed" }
                .getOrNull()
            when (outcome) {
                UpdateProfileOutcome.DisplayNameTaken -> {
                    updateState {
                        it.copy(
                            saveError = OnboardingSaveError.DisplayNameTaken,
                            isFinishing = false,
                            creationStarted = false,
                        )
                    }
                    return
                }
                UpdateProfileOutcome.InvalidDisplayName -> {
                    updateState {
                        it.copy(
                            saveError = OnboardingSaveError.InvalidDisplayName,
                            isFinishing = false,
                            creationStarted = false,
                        )
                    }
                    return
                }
                // Success / Queued / transient failures all proceed — the
                // server already has a usable generated name either way.
                else -> Unit
            }
        } else {
            guestAccountCreator.start(identity)
            // Bounded wait — never trap the user on this screen. If creation
            // hasn't reached a terminal state in time (offline / wedged), go
            // Home degraded; the creator keeps the identity and retries in the
            // background.
            val terminal = withTimeoutOrNull(FINISH_CREATION_AWAIT_TIMEOUT) {
                guestAccountCreator.awaitTerminal()
            }
            logger.d { "finish: guest-creation terminal=${terminal?.let { it::class.simpleName } ?: "timed-out"}" }
            if (terminal is AccountCreationState.Failed || terminal == null) {
                updateState { it.copy(creationFailed = true) }
            }
        }

        exitedToHome = true
        logger.logEvent(
            "onboarding.completed",
            "duration_sec" to onboardingStartedAt.elapsedNow().inWholeSeconds,
            "account_ready" to !state.creationFailed,
        )
        appCache.update { it.copy(hasUserOnboarded = true) }
        updateState { it.copy(isFinishing = false) }
        sendEvent(OnboardingEvent.NavigateToHome)
    }

    /**
     * The only reachable back transition is PickIdentity → Welcome: leaving
     * PickIdentity sets [OnboardingState.creationStarted] (and the OAuth entry
     * sets [OnboardingState.identityClaimed]), both of which pin the flow
     * forward, and Welcome is the entry step (system back exits the app).
     * Clears the Welcome-step [OnboardingState.authError] so the landing page
     * comes back fresh.
     */
    private suspend fun OnboardingAction.handleBack() {
        val stepBefore = state.step
        updateState {
            if (it.step != OnboardingStep.PickIdentity || it.creationStarted || it.identityClaimed) {
                return@updateState it
            }
            it.copy(step = OnboardingStep.Welcome, authError = null)
        }
        if (state.step != stepBefore) logStepViewed(state.step)
    }

    override fun onCleared() {
        // Best-effort abandonment marker: the VM outliving the flow without ever
        // routing Home means the user backed out (system back on Welcome exits
        // the app and clears the entry). A process kill won't reach this — the
        // funnel's step_viewed-without-completed sessions cover that case.
        if (!exitedToHome) {
            logger.logEvent("onboarding.abandoned", "step" to state.step.eventName())
        }
        super.onCleared()
    }

    private fun logStepViewed(step: OnboardingStep) {
        logger.logEvent("onboarding.step_viewed", "step" to step.eventName())
    }

    private fun OnboardingStep.eventName(): String = when (this) {
        OnboardingStep.Welcome -> "welcome"
        OnboardingStep.PickIdentity -> "pick_identity"
    }

    companion object {
        /** Max display-name length; mirrors the shared [DisplayNameRules] cap so
         *  onboarding and any edit-profile surface agree. Stricter than the
         *  server limit (UX clamp). */
        internal const val MAX_DISPLAY_NAME_LENGTH = DisplayNameRules.MAX_LENGTH

        /**
         * Upper bound on how long "Continue" waits for the in-flight guest
         * account to finish before going Home anyway. Onboarding must never
         * trap the user on a backend call — if creation hasn't settled by now
         * (offline / slow), we proceed degraded and the creator keeps retrying
         * in the background.
         */
        private val FINISH_CREATION_AWAIT_TIMEOUT = 5_000.milliseconds
    }
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val oauthInFlight: OAuthProvider? = null,
    val authError: OnboardingAuthError? = null,

    /**
     * True once the user has claimed a real identity mid-onboarding (e.g. a new
     * Apple account) and is finishing setup. Suppresses the "back to landing
     * page" affordance — the Welcome sign-in options are meaningless once you're
     * signed in, and re-running them would be confusing.
     */
    val identityClaimed: Boolean = false,

    /**
     * True once guest-account creation has been kicked off (PickIdentity →
     * Continue). Blocks back-navigation from there — the chosen name is
     * committed and the Welcome sign-in options no longer apply.
     */
    val creationStarted: Boolean = false,

    /** True while the final step is joining on the in-flight account creation. */
    val isFinishing: Boolean = false,

    /**
     * True if guest-account creation failed (offline) by the time the user
     * finished onboarding. They still land on Home; the account is retried in
     * the background.
     */
    val creationFailed: Boolean = false,

    val displayName: String = "",
    /** True once the user has typed in the name field — gates profile prefill. */
    val userEditedName: Boolean = false,

    val saveError: OnboardingSaveError? = null,

    /** Native "Sign in with Apple" is iOS-only; hides the button elsewhere. */
    val appleEnabled: Boolean = false,
)

sealed interface OnboardingStep {
    data object Welcome : OnboardingStep
    data object PickIdentity : OnboardingStep
}

sealed interface OnboardingEvent {
    data object NavigateToHome : OnboardingEvent
    data object NavigateToSignIn : OnboardingEvent
    data object NavigateToSignUp : OnboardingEvent
}

/**
 * Inline error surfaced under the Welcome step's primary CTAs. Typed so
 * the VM doesn't hold raw user-facing copy — `OnboardingScreen.kt`
 * resolves each variant at render time. Only the OAuth/Apple entries can
 * fail here; the guest path defers account creation to the app-scoped
 * [GuestAccountCreator], which retries in the background instead of
 * surfacing an error on Welcome.
 */
sealed interface OnboardingAuthError {
    /** OAuth provider isn't enabled in the auth backend yet. */
    data object OAuthProviderNotEnabled : OnboardingAuthError
    /** OAuth network unreachable. */
    data object OAuthNetworkError : OnboardingAuthError
    /** OAuth invalid credentials / email-not-confirmed / unknown. */
    data object OAuthFailed : OnboardingAuthError
}

/**
 * Inline error surfaced under the PickIdentity step's display-name field.
 * Both variants come from the profile-update outcome; everything else is
 * intentionally swallowed so the user isn't dead-ended.
 */
sealed interface OnboardingSaveError {
    data object DisplayNameTaken : OnboardingSaveError
    data object InvalidDisplayName : OnboardingSaveError
}

sealed interface OnboardingAction {
    /** Internal: fired once on init to resolve the opening step / bounce. */
    data object ResolveEntry : OnboardingAction
    data object ContinueAsGuest : OnboardingAction
    /** Welcome-step entry into the email/password sign-in flow. */
    data object SignIn : OnboardingAction
    /** Welcome-step entry into the email/password sign-up flow. */
    data object SignUp : OnboardingAction
    /** Steps back from PickIdentity to Welcome (when not pinned forward). */
    data object Back : OnboardingAction
    data class SignInWithOAuth(val provider: OAuthProvider) : OnboardingAction
    /** Welcome-step native "Sign in with Apple" (iOS only). */
    data object SignInWithApple : OnboardingAction
    data class DisplayNameChanged(val value: String) : OnboardingAction
    data object RegenerateDisplayName : OnboardingAction
    /** Commits the chosen identity, creates/patches the account, finishes. */
    data object ContinueFromPickIdentity : OnboardingAction
}
