package com.opson.features.onboarding.impl

import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.Catching
import com.opson.libraries.core.isiOS
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.diary.SettingsRepository
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
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.opson.AppCache
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import me.tatarka.inject.annotations.Inject

/**
 * Onboarding: a welcome, four skippable questions and a starting mode, with
 * **deferred account creation**. No account exists on launch; one is minted
 * only when the user commits.
 *
 *   1. **Welcome**: "Set it up" enters the questions (no auth yet); "Skip,
 *      start logging" finishes at once in Delayed reveal. Apple and Google
 *      sign-in: a returning account goes straight to Today, a brand-new one
 *      runs through the questions like a guest. New-vs-returning comes from
 *      the server's new-account signal via [AuthOutcomeClassifier].
 *   2. **Questions 1 to 4**: each answer recomputes the recommendation
 *      ([ModeRecommender]). Every question can be skipped.
 *   3. **Recommendation**: the recommended mode with up to four alternatives.
 *      Full numbers and Sprint are never offered to someone who said numbers
 *      have been hard. "Start with ..." finishes.
 *
 * Finishing writes the preset and the answers to settings without stamping
 * `changedAt` (so Modes says "Set by you during setup"), then kicks off
 * guest-account creation ([GuestAccountCreator], app-scoped so it survives the
 * ViewModel) with a bounded wait, marks `hasUserOnboarded` and goes to Today.
 *
 * **Why creation is deferred:** minting an anonymous account on launch leaves
 * an orphan whenever the user then signs into a real account. Onboarding runs
 * entirely unauthenticated, and the account is created at the point of no
 * return.
 *
 * Hard guard on init: if `AppData.hasUserOnboarded` is already true, fire
 * [OnboardingEvent.NavigateToToday] immediately so a returning user that
 * lands on the route bounces to Today.
 *
 * Telemetry carries step names, the preset key and booleans. Never an answer:
 * "numbers have been hard for me before" is close to a clinical disclosure.
 */
@Inject
class OnboardingViewModel(
    private val appCache: AppCache,
    private val authRepository: AuthRepository,
    private val authOutcomeClassifier: AuthOutcomeClassifier,
    private val guestAccountCreator: GuestAccountCreator,
    private val appleSignInCoordinator: AppleSignInCoordinator,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : SEAViewModel<OnboardingState, OnboardingEvent, OnboardingAction>(
    initialStateArg = OnboardingState(appleEnabled = BuildInfo.isiOS()),
) {

    private val logger = KLog.withTag("OnboardingFlow")

    private val onboardingStartedAt = TimeSource.Monotonic.markNow()

    /**
     * True once this instance routed to Today (completed, or a returning user
     * bounced/signed in). Suppresses the best-effort `onboarding.abandoned`
     * fired from [onCleared].
     */
    private var exitedToToday = false

    init {
        // Resolve where onboarding opens (Today bounce / questions re-entry /
        // Welcome landing) through the action loop so state updates run in an
        // action scope, as VerifyEmail's init → ResolveEmailFromSession does.
        takeAction(OnboardingAction.ResolveEntry)
    }

    override suspend fun handleAction(action: OnboardingAction) {
        when (action) {
            OnboardingAction.ResolveEntry -> action.handleResolveEntry()
            OnboardingAction.ContinueAsGuest -> action.handleContinueAsGuest()
            OnboardingAction.SkipAllTapped -> action.handleSkipAll()
            is OnboardingAction.OptionTapped -> action.handleOptionTapped(action.option)
            OnboardingAction.NextTapped, OnboardingAction.SkipQuestionTapped -> action.advance()
            is OnboardingAction.AlternativeTapped -> action.handleAlternativeTapped(action.preset)
            OnboardingAction.StartTapped -> action.finish(state.chosen, state.answers, skippedAll = false)
            OnboardingAction.SignIn -> sendEvent(OnboardingEvent.NavigateToSignIn)
            OnboardingAction.SignUp -> sendEvent(OnboardingEvent.NavigateToSignUp)
            OnboardingAction.Back -> action.handleBack()
            is OnboardingAction.SignInWithOAuth -> action.handleOAuth(action.provider)
            OnboardingAction.SignInWithApple -> action.handleAppleSignIn()
        }
    }

    private suspend fun OnboardingAction.handleResolveEntry() {
        Catching {
            val authed = authRepository.current() as? AuthState.Authenticated
            when {
                appCache.get().hasUserOnboarded -> {
                    exitedToToday = true
                    sendEvent(OnboardingEvent.NavigateToToday)
                }
                // A real (non-anonymous) account that hasn't finished onboarding
                // is a just-verified email signup bounced back here from
                // VerifyEmail. Drop them straight into the questions, skipping
                // the guest/OAuth landing they don't need. identityClaimed
                // suppresses the back-to-Welcome control.
                authed != null && !authed.isAnonymous -> enterQuestions(identityClaimed = true)
                // An anonymous guest session already exists at launch. Guest
                // creation is deferred (a new user is Unauthenticated here), so a
                // live anonymous session can only be a PRIOR guest whose
                // hasUserOnboarded flag was lost, not a new user. Treat them as
                // returning: adopt the onboarded flag and go to Today.
                authed != null -> {
                    logger.logEvent("onboarding.auth_selected", "method" to "guest", "returning" to true)
                    appCache.update { it.copy(hasUserOnboarded = true) }
                    exitedToToday = true
                    sendEvent(OnboardingEvent.NavigateToToday)
                }
                else -> logStepViewed(OnboardingStep.Welcome)
            }
        }.logOnFailure { "Onboarding entry resolution failed" }
    }

    /**
     * "Set it up". No auth here: the guest account is created when the user
     * finishes, so tapping this only enters the questions.
     */
    private suspend fun OnboardingAction.handleContinueAsGuest() {
        logger.logEvent("onboarding.auth_selected", "method" to "guest", "returning" to false)
        updateState { it.copy(authError = null) }
        enterQuestions(identityClaimed = false)
    }

    /** "Skip, start logging": a guest with no answers, in Delayed reveal. */
    private suspend fun OnboardingAction.handleSkipAll() {
        if (state.step != OnboardingStep.Welcome) return
        logger.logEvent("onboarding.auth_selected", "method" to "guest", "returning" to false)
        val answers = SetupAnswers()
        finish(ModeRecommender.recommend(answers), answers, skippedAll = true)
    }

    private suspend fun OnboardingAction.enterQuestions(identityClaimed: Boolean) {
        val first = OnboardingStep.Question(SetupQuestion.Reason)
        updateState {
            it.copy(
                step = first,
                oauthInFlight = null,
                identityClaimed = it.identityClaimed || identityClaimed,
            ).withRecommendation()
        }
        logStepViewed(first)
    }

    private suspend fun OnboardingAction.handleOptionTapped(option: SetupOption) {
        updateState {
            val question = it.question ?: return@updateState it
            if (option !in question.options) return@updateState it
            it.copy(answers = it.answers.tap(option), picked = null).withRecommendation()
        }
    }

    /** Next and Skip both move on; Skip leaves the answer as it is. */
    private suspend fun OnboardingAction.advance() {
        val question = state.question ?: return
        val next = SetupQuestion.of(question.number + 1)
            ?.let { OnboardingStep.Question(it) }
            ?: OnboardingStep.Recommendation
        updateState { it.copy(step = next) }
        logStepViewed(next)
    }

    private suspend fun OnboardingAction.handleAlternativeTapped(preset: PresetId) {
        updateState {
            if (it.step != OnboardingStep.Recommendation || preset !in it.alternatives) return@updateState it
            it.copy(
                picked = preset,
                alternatives = ModeRecommender.alternatives(preset, it.answers.hard),
            )
        }
    }

    /**
     * The point of no return, from Start or Skip all:
     *  1. Save the preset and the answers. `changedAt` stays null so Modes can
     *     say "Set by you during setup". Safe before the account exists: the
     *     guest is the first user on this install, so no user-scoped clear
     *     runs, and the settings row syncs once the account is live.
     *  2. Guest path (no session yet): start guest-account creation and join on
     *     it with a bounded wait. Success or failure both complete onboarding;
     *     a failed (offline) creation is flagged degraded and the creator keeps
     *     retrying in the background. A claimed account (OAuth, Apple, a
     *     verified email) needs nothing more.
     *  3. Mark `hasUserOnboarded` and go to Today. Never trap the user on a
     *     backend call.
     */
    private suspend fun OnboardingAction.finish(preset: PresetId, answers: SetupAnswers, skippedAll: Boolean) {
        if (state.isFinishing) return
        val tookRecommendation = !skippedAll && preset == state.recommended
        updateState { it.copy(isFinishing = true, creationStarted = true) }

        val today = clock.todayIn(TimeZone.currentSystemDefault())
        Catching {
            settingsRepository.update(stampNumbersChange = false) { it.startingWith(preset, answers, today) }
        }.logOnFailure { "Onboarding could not save the starting mode" }

        if (authRepository.current() !is AuthState.Authenticated) {
            guestAccountCreator.start(PendingIdentity(displayName = DisplayNameSuggester.next()))
            // Bounded wait. If creation hasn't reached a terminal state in time
            // (offline / wedged), go on degraded; the creator keeps the identity
            // and retries in the background.
            val terminal = withTimeoutOrNull(FINISH_CREATION_AWAIT_TIMEOUT) {
                guestAccountCreator.awaitTerminal()
            }
            logger.d { "finish: guest-creation terminal=${terminal?.let { it::class.simpleName } ?: "timed-out"}" }
            if (terminal is AccountCreationState.Failed || terminal == null) {
                updateState { it.copy(creationFailed = true) }
            }
        }

        exitedToToday = true
        logger.logEvent(
            "onboarding.completed",
            "duration_sec" to onboardingStartedAt.elapsedNow().inWholeSeconds,
            "account_ready" to !state.creationFailed,
            "preset" to preset.key,
            "took_recommendation" to tookRecommendation,
            "skipped_all" to skippedAll,
        )
        appCache.update { it.copy(hasUserOnboarded = true) }
        // isFinishing stays true: the screen is leaving, and a second tap queued
        // behind this one must not finish again.
        sendEvent(OnboardingEvent.NavigateToToday)
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
     *  - [AuthOutcome.SignedUp]: first-ever sign-in for this identity, so run
     *    them through the questions like a guest. `identityClaimed` suppresses
     *    the back-to-Welcome control (the sign-in options no longer apply).
     *  - [AuthOutcome.SignedIn]: a returning account already has its settings,
     *    which arrive by sync, so skip onboarding straight to Today. Until they
     *    land the visibility policy stays Blind, so a new phone never shows Full
     *    numbers first.
     *
     * [AuthOutcome.Linked] can't reach here (Welcome has no anonymous guest to
     * link onto, since guest creation is deferred), so it routes to Today like
     * any already-established account.
     */
    private suspend fun OnboardingAction.routeAfterSignIn(outcome: AuthOutcome) {
        when (outcome) {
            AuthOutcome.SignedUp -> enterQuestions(identityClaimed = true)
            AuthOutcome.SignedIn, AuthOutcome.Linked -> {
                appCache.update { it.copy(hasUserOnboarded = true) }
                updateState { it.copy(oauthInFlight = null) }
                exitedToToday = true
                sendEvent(OnboardingEvent.NavigateToToday)
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
                        // User dismissed the sheet: quiet no-op.
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
        //      account OR mint a net-new one, so we don't assume; [signInApple]
        //      classifies via the brand-new signal.
        val isAnonymousGuest =
            (authRepository.current() as? AuthState.Authenticated)?.isAnonymous == true
        if (isAnonymousGuest) {
            when (authRepository.linkAppleIdentity(credential)) {
                LinkIdentityOutcome.Success -> {
                    logger.logEvent("onboarding.auth_selected", "method" to "apple", "returning" to false)
                    enterQuestions(identityClaimed = true)
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
     * creates on first Apple OIDC, e.g. after an account deletion left no
     * session). We must NOT assume "existing": classify via
     * [authOutcomeClassifier] exactly like [handleOAuth], so a net-new account
     * runs through the questions instead of landing cold on Today.
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
     * Recommendation → Question 4, Question n → n − 1, Question 1 → Welcome.
     * Gated by [OnboardingState.canGoBack]: nothing moves while finishing, and
     * a claimed identity cannot return to the Welcome sign-in options.
     */
    private suspend fun OnboardingAction.handleBack() {
        if (!state.canGoBack) return
        val previous = when (val step = state.step) {
            OnboardingStep.Welcome -> return
            OnboardingStep.Recommendation -> OnboardingStep.Question(SetupQuestion.Hard)
            is OnboardingStep.Question ->
                SetupQuestion.of(step.question.number - 1)?.let { OnboardingStep.Question(it) }
                    ?: OnboardingStep.Welcome
        }
        updateState { it.copy(step = previous) }
        logStepViewed(previous)
    }

    override fun onCleared() {
        // Best-effort abandonment marker: the VM outliving the flow without ever
        // routing to Today means the user backed out (system back on Welcome
        // exits the app and clears the entry). A process kill won't reach this;
        // the funnel's step_viewed-without-completed sessions cover that case.
        if (!exitedToToday) {
            logger.logEvent("onboarding.abandoned", "step" to state.step.eventName)
        }
        super.onCleared()
    }

    private fun logStepViewed(step: OnboardingStep) {
        logger.logEvent("onboarding.step_viewed", "step" to step.eventName)
    }

    companion object {
        /**
         * Upper bound on how long finishing waits for the in-flight guest
         * account before going to Today anyway. Onboarding must never trap the
         * user on a backend call: if creation hasn't settled by now (offline /
         * slow), we proceed degraded and the creator keeps retrying in the
         * background.
         */
        private val FINISH_CREATION_AWAIT_TIMEOUT = 5_000.milliseconds
    }
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val answers: SetupAnswers = SetupAnswers(),
    /** [ModeRecommender.recommend] of [answers]. */
    val recommended: PresetId = ModeRecommender.recommend(SetupAnswers()),
    /** An alternative the user tapped on the recommendation step. Cleared by any new answer. */
    val picked: PresetId? = null,
    val alternatives: List<PresetId> = ModeRecommender.alternatives(recommended, hard = false),

    val oauthInFlight: OAuthProvider? = null,
    val authError: OnboardingAuthError? = null,

    /**
     * True once the user has claimed a real identity mid-onboarding (e.g. a new
     * Apple account) and is finishing setup. Suppresses the "back to landing
     * page" affordance: the Welcome sign-in options are meaningless once you're
     * signed in, and re-running them would be confusing.
     */
    val identityClaimed: Boolean = false,

    /** True once finishing has begun. Pins the flow forward. */
    val creationStarted: Boolean = false,

    /** True from Start (or Skip all) until the screen leaves. Disables every button. */
    val isFinishing: Boolean = false,

    /**
     * True if guest-account creation failed (offline) by the time the user
     * finished onboarding. They still land on Today; the account is retried in
     * the background.
     */
    val creationFailed: Boolean = false,

    /** Native "Sign in with Apple" is iOS-only; hides the button elsewhere. */
    val appleEnabled: Boolean = false,
) {
    /** The mode "Start with" will save. */
    val chosen: PresetId get() = picked ?: recommended

    val question: SetupQuestion? get() = (step as? OnboardingStep.Question)?.question

    /** The current question's options, with what is already picked. */
    val options: List<SetupChoice> get() = question?.let(answers::choices).orEmpty()

    val canGoBack: Boolean
        get() = when (step) {
            OnboardingStep.Welcome -> false
            OnboardingStep.Recommendation -> !isFinishing
            is OnboardingStep.Question ->
                !isFinishing && (step.question != SetupQuestion.Reason || (!identityClaimed && !creationStarted))
        }

    /** Recomputes the recommendation and the alternatives from [answers]. */
    internal fun withRecommendation(): OnboardingState {
        val next = ModeRecommender.recommend(answers)
        val chosen = picked?.takeIf { ModeRecommender.isOffered(it, answers.hard) }
        return copy(
            recommended = next,
            picked = chosen,
            alternatives = ModeRecommender.alternatives(chosen ?: next, answers.hard),
        )
    }
}

sealed interface OnboardingStep {
    /** The stable name `onboarding.step_viewed` and `onboarding.abandoned` carry. Never an index. */
    val eventName: String

    data object Welcome : OnboardingStep {
        override val eventName = "welcome"
    }

    data class Question(val question: SetupQuestion) : OnboardingStep {
        override val eventName: String get() = "question_${question.number}"
    }

    data object Recommendation : OnboardingStep {
        override val eventName = "recommendation"
    }
}

sealed interface OnboardingEvent {
    data object NavigateToToday : OnboardingEvent
    data object NavigateToSignIn : OnboardingEvent
    data object NavigateToSignUp : OnboardingEvent
}

/**
 * Inline error surfaced under the Welcome step's sign-in buttons. Typed so
 * the VM doesn't hold raw user-facing copy: `OnboardingScreen.kt` resolves
 * each variant at render time. Only the OAuth/Apple entries can fail here; the
 * guest path defers account creation to the app-scoped [GuestAccountCreator],
 * which retries in the background instead of surfacing an error on Welcome.
 */
sealed interface OnboardingAuthError {
    /** OAuth provider isn't enabled in the auth backend yet. */
    data object OAuthProviderNotEnabled : OnboardingAuthError
    /** OAuth network unreachable. */
    data object OAuthNetworkError : OnboardingAuthError
    /** OAuth invalid credentials / email-not-confirmed / unknown. */
    data object OAuthFailed : OnboardingAuthError
}

sealed interface OnboardingAction {
    /** Internal: fired once on init to resolve the opening step / bounce. */
    data object ResolveEntry : OnboardingAction
    /** "Set it up" on Welcome: the guest path, into the questions. */
    data object ContinueAsGuest : OnboardingAction
    /** "Skip, start logging" on Welcome. */
    data object SkipAllTapped : OnboardingAction
    data class OptionTapped(val option: SetupOption) : OnboardingAction
    data object NextTapped : OnboardingAction
    data object SkipQuestionTapped : OnboardingAction
    data class AlternativeTapped(val preset: PresetId) : OnboardingAction
    /** "Start with ..." on the recommendation step. */
    data object StartTapped : OnboardingAction
    /** Welcome-step entry into the email/password sign-in flow. */
    data object SignIn : OnboardingAction
    /** Welcome-step entry into the email/password sign-up flow. */
    data object SignUp : OnboardingAction
    /** One step back, when [OnboardingState.canGoBack]. */
    data object Back : OnboardingAction
    data class SignInWithOAuth(val provider: OAuthProvider) : OnboardingAction
    /** Welcome-step native "Sign in with Apple" (iOS only). */
    data object SignInWithApple : OnboardingAction
}
