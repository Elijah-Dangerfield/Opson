package com.opson.features.onboarding.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.onboarding.OnboardingRoute
import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.Catching
import com.opson.libraries.core.isiOS
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.health.GoalsAndEnergyEnabled
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.identity.auth.AppleSignInCoordinator
import com.opson.libraries.identity.auth.AuthOutcome
import com.opson.libraries.identity.auth.AuthOutcomeClassifier
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.LinkIdentityOutcome
import com.opson.libraries.identity.auth.OAuthProvider
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.identity.auth.awaitCredential
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.TodayLayout
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.opson.AppCache
import kotlin.time.Clock
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/**
 * Onboarding (design v3, reworked in docs/plans/onboarding-v2.md and again when
 * accounts became required): Welcome, sign in, two skippable questions, an
 * optional statement, a starting mode and the setup steps that mode needs.
 *
 *   1. **Welcome**: "Get started" opens the sign-in step.
 *   2. **Sign in** ([OnboardingStep.SignIn]): Apple (iOS), Google, and email
 *      when it is on ([SignInMethods.EMAIL]). There is no way past it without
 *      an account. A new account goes on to the questions; one that already
 *      exists is set up already, so it goes to Today and keeps its mode.
 *   3. **Questions 1 and 2 and the statement**: each answer recomputes the
 *      recommendation ([ModeRecommender]). Every step can be skipped and
 *      nothing asks again later.
 *   4. **Recommendation**: the mode, the answer that picked it, what you'll see
 *      and what stays out of view ([ModePreview]), the other modes and All
 *      modes. Full numbers is never offered to someone who agreed numbers have
 *      been hard.
 *   5. **Setup** (`release.onboardingSetup`, [OnboardingState.setupSteps]): the
 *      goal where the mode shows calories, resting energy details for a set
 *      deficit or surplus (`release.goalsAndEnergy`), and meal times where Today
 *      is built around them. Each is skippable and keeps the defaults.
 *
 * Saving writes the preset, the answers and the setup to settings without
 * stamping `changedAt` (so Modes says "Set by you during setup"), marks
 * `hasUserOnboarded` and goes to Today.
 *
 * **Guests from older builds.** Anyone who still has an anonymous session gets
 * the sign-in step with [OnboardingState.guest] set: Apple or Google is linked
 * onto the guest, so the account keeps everything they logged. If that Apple ID
 * or Google account already has its own Opson account, they can sign in to it
 * instead, and the guest's log stays behind.
 *
 * Where it opens ([OnboardingAction.ResolveEntry]): a signed-in, onboarded
 * account bounces to Today; a signed-in account that isn't set up yet resumes at
 * the questions; an onboarded guest opens on sign-in with no Welcome to go back
 * to; [OnboardingRoute.signInFirst] opens on sign-in; everyone else gets Welcome.
 *
 * Telemetry carries step names, the preset key, the sign-in method and
 * booleans. Never an answer: agreeing that numbers have been hard is close to
 * a clinical disclosure.
 */
@Inject
class OnboardingViewModel(
    private val appCache: AppCache,
    private val authRepository: AuthRepository,
    private val authOutcomeClassifier: AuthOutcomeClassifier,
    private val appleSignInCoordinator: AppleSignInCoordinator,
    private val settingsRepository: SettingsRepository,
    private val weightRepository: WeightRepository,
    private val calendar: LocalCalendar,
    setupEnabled: OnboardingSetupEnabled,
    goalsAndEnergy: GoalsAndEnergyEnabled,
    @Assisted private val route: OnboardingRoute,
    /** Native Sign in with Apple, which only iOS has. A parameter so tests can run either phone. */
    appleSignIn: Boolean = BuildInfo.isiOS(),
    private val clock: Clock = Clock.System,
) : SEAViewModel<OnboardingState, OnboardingEvent, OnboardingAction>(
    initialStateArg = OnboardingState(
        appleEnabled = appleSignIn,
        setupSteps = setupEnabled(),
        goalsAndEnergy = goalsAndEnergy(),
        currentYear = calendar.today().year,
    ),
) {

    private val logger = KLog.withTag("OnboardingFlow")

    private val onboardingStartedAt = TimeSource.Monotonic.markNow()

    /**
     * True once the mode was saved (`onboarding.completed` fired) or the person
     * left for Today. Suppresses a second `onboarding.completed` and the
     * best-effort `onboarding.abandoned` fired from [onCleared].
     */
    private var completed = false

    /**
     * The Google round trip, off the action loop. supabase-kt only hears back
     * when the browser redirects to the app, so someone who closes the browser
     * leaves the call waiting minutes for its timeout. Running it here keeps the
     * sign-in buttons working: another tap starts over and cancels this one.
     */
    private var googleAttempt: Job? = null

    /** The weigh-in the resting energy step logged, so saving again after Back does not log it twice. */
    private var weighedKg: Double? = null

    init {
        // Resolve where onboarding opens through the action loop so state
        // updates run in an action scope, as VerifyEmail's init →
        // ResolveEmailFromSession does.
        takeAction(OnboardingAction.ResolveEntry)
    }

    override suspend fun handleAction(action: OnboardingAction) {
        when (action) {
            OnboardingAction.ResolveEntry -> action.handleResolveEntry()
            OnboardingAction.GetStartedTapped -> action.handleGetStarted()
            OnboardingAction.TalkToSomeoneTapped -> sendEvent(OnboardingEvent.NavigateToSupport)
            is OnboardingAction.OptionTapped -> action.handleOptionTapped(action.option)
            OnboardingAction.ContinueTapped, OnboardingAction.SkipQuestionTapped -> action.advance()
            is OnboardingAction.GoalStyleTapped -> action.updateSetup(OnboardingStep.Goal) {
                if (action.style in it.goalStyles) it.copy(goal = it.goal.copy(style = action.style)) else it
            }
            is OnboardingAction.GoalStepped -> action.updateSetup(OnboardingStep.Goal) { it.copy(goal = it.goal.stepped(action.up)) }
            is OnboardingAction.BirthYearTyped -> action.updateSetup(OnboardingStep.Body) { it.copy(body = it.body.withBirthYear(action.text)) }
            is OnboardingAction.HeightTyped -> action.updateSetup(OnboardingStep.Body) { it.copy(body = it.body.withHeight(action.text)) }
            is OnboardingAction.WeightTyped -> action.updateSetup(OnboardingStep.Body) { it.copy(body = it.body.withWeight(action.text)) }
            is OnboardingAction.SexPicked -> action.updateSetup(OnboardingStep.Body) { it.copy(body = it.body.copy(sex = action.sex)) }
            is OnboardingAction.UnitPicked -> action.updateSetup(OnboardingStep.Body) { it.copy(body = it.body.withUnit(action.unit)) }
            is OnboardingAction.ActivityPicked -> action.updateSetup(OnboardingStep.Body) { it.copy(body = it.body.copy(activity = action.level)) }
            is OnboardingAction.MealTimePicked -> action.updateSetup(OnboardingStep.Meals) {
                it.copy(mealTimes = it.mealTimes.with(action.slot, action.time))
            }
            is OnboardingAction.AlternativeTapped -> action.handleAlternativeTapped(action.preset)
            OnboardingAction.AllModesTapped -> action.handleAllModes()
            OnboardingAction.StartTapped -> action.handleStart()
            OnboardingAction.SignInWithAppleTapped -> action.handleApple(link = state.guest)
            OnboardingAction.SignInWithGoogleTapped -> action.handleGoogle(link = state.guest)
            OnboardingAction.SignInWithEmailTapped -> action.handleEmail()
            OnboardingAction.UseThatAccountTapped -> action.handleUseThatAccount()
            OnboardingAction.Back -> action.handleBack()
        }
    }

    private suspend fun OnboardingAction.handleResolveEntry() {
        Catching {
            val authed = authRepository.current() as? AuthState.Authenticated
            val onboarded = appCache.get().hasUserOnboarded
            when {
                authed != null && !authed.isAnonymous && onboarded -> leaveForToday()
                // A real account that hasn't finished onboarding: a new account
                // that closed the app mid-setup, or a just-verified email signup
                // bounced back here from VerifyEmail. Into the questions.
                authed != null && !authed.isAnonymous -> enterQuestions()
                // A guest from a build where accounts were optional, already
                // using the app. Sign-in is the only way on.
                authed != null && onboarded -> openSignIn(guest = true, signInOnly = true)
                // A guest whose onboarded flag is gone: a reinstall, since the
                // Keychain keeps an anonymous session across one. Welcome first,
                // then sign-in links onto it.
                authed != null -> {
                    updateState { it.copy(guest = true) }
                    openWelcomeOrSignIn()
                }
                else -> openWelcomeOrSignIn()
            }
        }.logOnFailure { "Onboarding entry resolution failed" }
    }

    private suspend fun OnboardingAction.openWelcomeOrSignIn() {
        if (route.signInFirst) openSignIn(guest = state.guest, signInOnly = false) else logStepViewed(OnboardingStep.Welcome)
    }

    private suspend fun OnboardingAction.openSignIn(guest: Boolean, signInOnly: Boolean) {
        updateState { it.copy(step = OnboardingStep.SignIn, guest = guest, signInOnly = signInOnly) }
        logStepViewed(OnboardingStep.SignIn)
    }

    /** "Get started": the sign-in step. */
    private suspend fun OnboardingAction.handleGetStarted() {
        if (state.step != OnboardingStep.Welcome) return
        goTo(OnboardingStep.SignIn)
    }

    private suspend fun OnboardingAction.enterQuestions() {
        val first = OnboardingStep.Question(SetupQuestion.Reason)
        updateState {
            it.copy(step = first, accountInFlight = null, accountError = null, conflict = null).withRecommendation()
        }
        logStepViewed(first)
    }

    // ---------- Sign in ----------

    /**
     * "Continue with Apple" (iOS). [link] (a guest) attaches the Apple ID to the
     * guest so their log stays; otherwise it signs in, which makes the account
     * when the Apple ID has none. A dismissed sheet is a quiet no-op. Either way
     * the authorization code goes to the server, which keeps it so deleting the
     * account can revoke Apple's token.
     */
    private suspend fun OnboardingAction.handleApple(link: Boolean) {
        if (!state.canTapSignIn) return
        googleAttempt?.cancel()
        updateState { it.copy(accountInFlight = AccountMethod.Apple, accountError = null, conflict = null) }
        val request = Catching { appleSignInCoordinator.awaitCredential() }
            .logOnFailure { "Apple credential request failed" }
        val credential = request.getOrNull()
        when {
            request.isFailure -> fail(AccountMethod.Apple, OnboardingAccountError.Failed)
            credential == null -> updateState { it.copy(accountInFlight = null) }
            link -> settleLink(AccountMethod.Apple, authRepository.linkAppleIdentity(credential))
            else -> settleSignIn(AccountMethod.Apple, authRepository.signInWithApple(credential))
        }
    }

    /** "Continue with Google", through the browser. See [googleAttempt] for why it runs off the loop. */
    private suspend fun OnboardingAction.handleGoogle(link: Boolean) {
        if (!state.canTapSignIn) return
        googleAttempt?.cancel()
        updateState { it.copy(accountInFlight = AccountMethod.Google, accountError = null, conflict = null) }
        val action = this
        googleAttempt = viewModelScope.launch {
            if (link) {
                action.settleLink(AccountMethod.Google, authRepository.linkOAuthIdentity(OAuthProvider.Google))
            } else {
                action.settleSignIn(AccountMethod.Google, authRepository.signInWithOAuth(OAuthProvider.Google))
            }
        }
    }

    /**
     * "Continue with email" when email is on: the sign-up screen links an email
     * onto a guest; the sign-in screen, which can also make an account, for
     * everyone else.
     */
    private suspend fun OnboardingAction.handleEmail() {
        if (!state.canTapSignIn) return
        googleAttempt?.cancel()
        updateState { it.copy(accountInFlight = null, accountError = null, conflict = null) }
        sendEvent(if (state.guest) OnboardingEvent.NavigateToSignUp else OnboardingEvent.NavigateToSignIn)
    }

    /**
     * "Sign in to that account": the guest's Apple ID or Google account already
     * has an Opson account, and they chose it over keeping the guest's log.
     */
    private suspend fun OnboardingAction.handleUseThatAccount() {
        when (state.conflict) {
            AccountMethod.Apple -> handleApple(link = false)
            AccountMethod.Google -> handleGoogle(link = false)
            null -> Unit
        }
    }

    private suspend fun OnboardingAction.settleSignIn(method: AccountMethod, outcome: SignInOutcome) {
        when (outcome) {
            SignInOutcome.Success -> {
                val result = Catching { authOutcomeClassifier.classify() }.getOrNull() ?: AuthOutcome.SignedIn
                val returning = result != AuthOutcome.SignedUp
                logger.logEvent("onboarding.auth_selected", "method" to method.eventName, "returning" to returning)
                // A new account has nothing to restore: it goes through setup.
                // One that exists is set up already, and its mode syncs down.
                if (returning) finishSignedIn() else enterQuestions()
            }
            SignInOutcome.Cancelled -> updateState { it.copy(accountInFlight = null) }
            SignInOutcome.ProviderNotEnabled -> fail(method, OnboardingAccountError.ProviderNotEnabled)
            is SignInOutcome.NetworkError -> fail(method, OnboardingAccountError.Offline)
            SignInOutcome.InvalidCredentials, is SignInOutcome.EmailNotConfirmed, is SignInOutcome.Unknown ->
                fail(method, OnboardingAccountError.Failed)
        }
    }

    private suspend fun OnboardingAction.settleLink(method: AccountMethod, outcome: LinkIdentityOutcome) {
        when (outcome) {
            LinkIdentityOutcome.Success -> {
                // The guest set up its mode when it started, so it goes to Today.
                logger.logEvent("onboarding.auth_selected", "method" to method.eventName, "returning" to true)
                finishSignedIn()
            }
            LinkIdentityOutcome.Cancelled -> updateState { it.copy(accountInFlight = null) }
            LinkIdentityOutcome.AlreadyOnAnotherAccount -> {
                fail(method, OnboardingAccountError.AccountInUse)
                updateState { it.copy(conflict = method) }
            }
            LinkIdentityOutcome.ProviderNotEnabled -> fail(method, OnboardingAccountError.ProviderNotEnabled)
            is LinkIdentityOutcome.NetworkError -> fail(method, OnboardingAccountError.Offline)
            LinkIdentityOutcome.NotSignedIn, is LinkIdentityOutcome.Unknown -> fail(method, OnboardingAccountError.Failed)
        }
    }

    private suspend fun OnboardingAction.fail(method: AccountMethod, error: OnboardingAccountError) {
        logger.logEvent("onboarding.sign_in_failed", "method" to method.eventName, "reason" to error.eventName)
        updateState { it.copy(accountInFlight = null, accountError = error) }
    }

    /** Signed in to an account that is already set up: Today. */
    private suspend fun OnboardingAction.finishSignedIn() {
        appCache.update { it.copy(hasUserOnboarded = true) }
        updateState { it.copy(accountInFlight = null, isFinishing = true) }
        leaveForToday()
    }

    private fun leaveForToday() {
        completed = true
        sendEvent(OnboardingEvent.NavigateToToday)
    }

    // ---------- Questions, the mode and setup ----------

    private suspend fun OnboardingAction.handleOptionTapped(option: SetupOption) {
        updateState {
            val question = it.question ?: return@updateState it
            if (option !in question.options) return@updateState it
            it.copy(answers = it.answers.tap(option), picked = null, showAllModes = false).withRecommendation()
        }
    }

    /**
     * Continue and Skip both move on; Skip leaves the answer as it is. Off the last setup step
     * they save, as Start does when the mode needs no setup.
     */
    private suspend fun OnboardingAction.advance() {
        if (state.isFinishing) return
        val next = when (val step = state.step) {
            is OnboardingStep.Question -> SetupQuestion.of(step.question.number + 1)
                ?.let { OnboardingStep.Question(it) }
                ?: OnboardingStep.Recommendation
            OnboardingStep.Goal, OnboardingStep.Body, OnboardingStep.Meals -> state.setupAfter(step)
                ?: return finish(state.chosen, state.answers)
            else -> return
        }
        goTo(next)
    }

    private suspend fun OnboardingAction.goTo(step: OnboardingStep) {
        if (step != OnboardingStep.SignIn) googleAttempt?.cancel()
        updateState {
            it.copy(step = step, accountInFlight = null, accountError = null, conflict = null).withViews(policyFor(it.chosen))
        }
        logStepViewed(step)
    }

    /** A change on [step], ignored anywhere else or while saving. The views follow. */
    private suspend fun OnboardingAction.updateSetup(step: OnboardingStep, transform: (OnboardingState) -> OnboardingState) {
        updateState {
            if (it.step != step || it.isFinishing) it else transform(it).withViews(policyFor(it.chosen))
        }
    }

    /** The policy for [preset] as onboarding starts it, no overrides, as [startingWith] saves it. */
    private fun policyFor(preset: PresetId): VisibilityPolicy =
        VisibilityPolicy(NumbersSettings(preset = preset), clock, calendar.timeZone())

    private suspend fun OnboardingAction.handleAlternativeTapped(preset: PresetId) {
        updateState {
            if (it.step != OnboardingStep.Recommendation || preset !in it.alternatives) return@updateState it
            it.copy(picked = preset).withRecommendation()
        }
    }

    private suspend fun OnboardingAction.handleAllModes() {
        updateState {
            if (it.step != OnboardingStep.Recommendation) return@updateState it
            it.copy(showAllModes = true).withRecommendation()
        }
    }

    /** "Start with ...": the setup steps the mode needs or, with none, save the mode. */
    private suspend fun OnboardingAction.handleStart() {
        if (state.step != OnboardingStep.Recommendation || state.isFinishing) return
        val first = state.setupAfter(OnboardingStep.Recommendation)
            ?: return finish(state.chosen, state.answers)
        goTo(first)
    }

    /**
     * Saves [preset], from Start or the last setup step:
     *  1. Save the preset and the answers. `changedAt` stays null so Modes can
     *     say "Set by you during setup". The account exists already (sign-in
     *     comes first), so the settings row syncs to it.
     *  2. Log the resting energy step's weight as the first weigh-in.
     *  3. Mark `hasUserOnboarded` and go to Today.
     */
    private suspend fun OnboardingAction.finish(preset: PresetId, answers: SetupAnswers) {
        if (state.isFinishing) return
        val tookRecommendation = preset == state.recommended
        val setup = state.startingSetup()
        updateState { it.copy(isFinishing = true) }

        Catching {
            settingsRepository.update(stampNumbersChange = false) { it.startingWith(preset, answers, setup) }
        }.logOnFailure { "Onboarding could not save the starting mode" }
        logWeighIn()

        logger.logEvent(
            "onboarding.completed",
            "duration_sec" to onboardingStartedAt.elapsedNow().inWholeSeconds,
            "preset" to preset.key,
            "took_recommendation" to tookRecommendation,
        )
        appCache.update { it.copy(hasUserOnboarded = true) }
        // isFinishing stays true: the screen is leaving, and a second tap
        // queued behind this one must not finish again.
        leaveForToday()
    }

    /** The resting energy step's weight as a weigh-in. Weight is never a setting: Burned reads the latest weigh-in. */
    private suspend fun logWeighIn() {
        if (!state.showsBody) return
        val kg = state.body.weightKgOrNull() ?: return
        if (kg == weighedKg) return
        Catching { weightRepository.addWeighIn(kg) }
            .logOnFailure { "Onboarding could not log the first weigh-in" }
            .onSuccess { weighedKg = kg }
    }

    /**
     * The setup steps → Recommendation → Statement → Question 1, and Sign in → Welcome. Gated by
     * [OnboardingState.canGoBack]: nothing moves while saving or while Apple's sheet is up,
     * question 1 never returns to sign-in (the account exists by then), and an onboarded guest
     * has no Welcome to return to.
     */
    private suspend fun OnboardingAction.handleBack() {
        if (!state.canGoBack) return
        val previous = when (val step = state.step) {
            OnboardingStep.Welcome -> return
            OnboardingStep.SignIn -> OnboardingStep.Welcome
            OnboardingStep.Goal, OnboardingStep.Body, OnboardingStep.Meals -> state.setupBefore(step)
            OnboardingStep.Recommendation -> OnboardingStep.Question(SetupQuestion.Statement)
            is OnboardingStep.Question -> SetupQuestion.of(step.question.number - 1)?.let { OnboardingStep.Question(it) } ?: return
        }
        googleAttempt?.cancel()
        updateState { it.copy(step = previous, accountInFlight = null, accountError = null, conflict = null) }
        logStepViewed(previous)
    }

    override fun onCleared() {
        // Best-effort abandonment marker: the VM outliving the flow without ever
        // saving a mode or reaching Today means the user backed out (system back
        // on Welcome exits the app and clears the entry). A process kill won't
        // reach this; the funnel's step_viewed-without-completed sessions cover
        // that case.
        if (!completed) {
            logger.logEvent("onboarding.abandoned", "step" to state.step.eventName)
        }
        super.onCleared()
    }

    private fun logStepViewed(step: OnboardingStep) {
        logger.logEvent("onboarding.step_viewed", "step" to step.eventName)
    }
}

/** The year previews and tests start in, the fixtures' 2026. The view model passes the real one. */
internal const val DEFAULT_YEAR = 2026

/** A way to sign in on the sign-in step. Email hands off to its own screens. */
enum class AccountMethod(val eventName: String) {
    Apple("apple"),
    Google("google"),
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val answers: SetupAnswers = SetupAnswers(),
    /** [ModeRecommender.recommend] of [answers]. */
    val recommended: PresetId = ModeRecommender.recommend(SetupAnswers()),
    /** An alternative the user tapped on the recommendation step. Cleared by any new answer. */
    val picked: PresetId? = null,
    /** Three other starting points, or every offered mode once [showAllModes]. */
    val alternatives: List<PresetId> = ModeRecommender.alternatives(SetupAnswers(), recommended),
    /** "All modes" was tapped: [alternatives] lists every mode on offer. */
    val showAllModes: Boolean = false,

    /** The Apple or Google sign-in running on the sign-in step. */
    val accountInFlight: AccountMethod? = null,
    val accountError: OnboardingAccountError? = null,

    /**
     * The session is an anonymous guest from a build where accounts were
     * optional. Sign-in links onto it, so their log stays, and the copy says so.
     */
    val guest: Boolean = false,

    /** An onboarded guest, sent here to sign in: no Welcome behind the sign-in step. */
    val signInOnly: Boolean = false,

    /**
     * The guest's Apple ID or Google account already has its own Opson account.
     * The sign-in step offers to sign in to that one instead.
     */
    val conflict: AccountMethod? = null,

    /** True while the mode is being saved, and from leaving until the screen goes. Disables every button. */
    val isFinishing: Boolean = false,

    /** Native "Sign in with Apple" is iOS-only; hides the button elsewhere. */
    val appleEnabled: Boolean = false,

    /** `release.onboardingSetup`: the goal, resting energy and meal times steps. Off, Start saves straight away. */
    val setupSteps: Boolean = false,
    /** `release.goalsAndEnergy`: one number, a set deficit and surplus on the goal step, and the resting energy step. */
    val goalsAndEnergy: Boolean = false,
    /** For the birth year field: who could be 13 to 120. */
    val currentYear: Int = DEFAULT_YEAR,
    val goal: GoalAnswer = GoalAnswer(),
    val body: BodyAnswer = BodyAnswer(),
    val mealTimes: MealTimes = MealTimes(),
    /** The goal step as drawn, through the policy of the mode being started. Null until it opens. */
    val goalView: GoalView? = null,
    /** The resting energy step as drawn. Null until it opens. */
    val bodyView: BodyView? = null,
) {
    /** The mode "Start with" will save. */
    val chosen: PresetId get() = picked ?: recommended

    val question: SetupQuestion? get() = (step as? OnboardingStep.Question)?.question

    /** The current question's options, with what is already picked. */
    val options: List<SetupChoice> get() = question?.let(answers::choices).orEmpty()

    /** How far through the questions, 1 to [SetupQuestion.Count]. 0 off the question steps. */
    val progress: Int get() = question?.number ?: 0

    /** What [chosen] shows and keeps out of view. */
    val preview: ModePreview get() = ModePreview.of(Presets[chosen])

    /** The answer that picked the recommendation, while it is the one chosen. */
    val reason: RecommendationReason? get() = ModeRecommender.reason(answers).takeIf { chosen == recommended }

    /** The goal step: where the mode starts with calorie amounts on show, which is Full numbers. */
    val showsGoal: Boolean get() = setupSteps && Presets[chosen].level(Metric.Calories) == Visibility.Show

    /** The goal styles on offer, Range first. */
    val goalStyles: List<GoalChoice> get() = GoalChoice.offered(goalsAndEnergy)

    /** The resting energy step: a set deficit or surplus has to know what the person burns. */
    val showsBody: Boolean get() = showsGoal && goalsAndEnergy && goal.style.needsBurn

    /**
     * The meal times step: where Today is built around meal times (No numbers' timeline), or the
     * person is tracking to eat regularly.
     */
    val showsMeals: Boolean
        get() = setupSteps && (Presets[chosen].today == TodayLayout.MealTimeline || Why.EatingRegularly in answers.why)

    /** The setup steps for [chosen], in order. Empty with `release.onboardingSetup` off. */
    val setupFlow: List<OnboardingStep>
        get() = buildList {
            if (showsGoal) add(OnboardingStep.Goal)
            if (showsBody) add(OnboardingStep.Body)
            if (showsMeals) add(OnboardingStep.Meals)
        }

    /** The setup step after [step] (the recommendation, or a setup step), or null when saving comes next. */
    fun setupAfter(step: OnboardingStep): OnboardingStep? {
        val flow = setupFlow
        return if (step == OnboardingStep.Recommendation) flow.firstOrNull() else flow.getOrNull(flow.indexOf(step) + 1)
    }

    /** The step before setup step [step]: the setup step before it, or the recommendation. */
    fun setupBefore(step: OnboardingStep): OnboardingStep =
        setupFlow.let { flow -> flow.getOrNull(flow.indexOf(step) - 1) } ?: OnboardingStep.Recommendation

    /**
     * What the setup steps the person saw will save. Only those steps: a goal is never written
     * for someone who never saw one, so their range stays the app's starting one. A deficit or
     * surplus starts from an ordinary day's burn, resting energy plus everyday activity, as
     * Burned works it out with no health app.
     */
    fun startingSetup(): StartingSetup {
        val body = body.takeIf { showsBody }
        return StartingSetup(
            targets = goal.takeIf { showsGoal }?.resolve(burnKcal = body?.burnKcal(currentYear)),
            birthYear = body?.birthYearOrNull(currentYear),
            heightCm = body?.heightCmOrNull(),
            sex = body?.sex,
            weightUnit = body?.unit,
            mealTimes = mealTimes.takeIf { showsMeals },
            everydayActivity = body?.activity,
        )
    }

    /** The goal and resting energy views, built through [policy] (the mode being started). */
    internal fun withViews(policy: VisibilityPolicy): OnboardingState = copy(
        goalView = SetupViews.goal(goal, goalStyles, policy),
        bodyView = SetupViews.body(body, currentYear, policy),
    )

    /**
     * The sign-in buttons work: on the step, not leaving, and Apple's sheet not up. A Google
     * round trip doesn't block them, since someone who closed the browser has to be able to
     * start again.
     */
    val canTapSignIn: Boolean
        get() = step == OnboardingStep.SignIn && accountInFlight != AccountMethod.Apple && !isFinishing

    val canGoBack: Boolean
        get() = when (step) {
            OnboardingStep.Welcome -> false
            OnboardingStep.SignIn -> !signInOnly && accountInFlight != AccountMethod.Apple && !isFinishing
            OnboardingStep.Recommendation, OnboardingStep.Goal, OnboardingStep.Body, OnboardingStep.Meals -> !isFinishing
            is OnboardingStep.Question -> !isFinishing && step.question != SetupQuestion.Reason
        }

    /** Recomputes the recommendation and the alternatives from [answers]. */
    internal fun withRecommendation(): OnboardingState {
        val next = ModeRecommender.recommend(answers)
        val kept = picked?.takeIf { ModeRecommender.isOffered(it, answers.hard) }
        return copy(
            recommended = next,
            picked = kept,
            alternatives = ModeRecommender.alternatives(
                answers = answers,
                chosen = kept ?: next,
                recommended = next,
                all = showAllModes,
            ),
        )
    }
}

sealed interface OnboardingStep {
    /** The stable name `onboarding.step_viewed` and `onboarding.abandoned` carry. Never an index. */
    val eventName: String

    data object Welcome : OnboardingStep {
        override val eventName = "welcome"
    }

    /** Apple, Google or email. Required: there is no way past it without an account. */
    data object SignIn : OnboardingStep {
        override val eventName = "sign_in"
    }

    data class Question(val question: SetupQuestion) : OnboardingStep {
        override val eventName: String get() = question.eventName
    }

    data object Recommendation : OnboardingStep {
        override val eventName = "recommendation"
    }

    /** The goal style and range, in Full numbers. */
    data object Goal : OnboardingStep {
        override val eventName = "goal"
    }

    /** Resting energy's details, for a set deficit or surplus. */
    data object Body : OnboardingStep {
        override val eventName = "resting_energy"
    }

    /** When meals usually happen. */
    data object Meals : OnboardingStep {
        override val eventName = "meal_times"
    }
}

sealed interface OnboardingEvent {
    data object NavigateToToday : OnboardingEvent
    data object NavigateToSignIn : OnboardingEvent
    data object NavigateToSignUp : OnboardingEvent

    /** Find support (design 10.3), from "Talk to someone" on the statement. */
    data object NavigateToSupport : OnboardingEvent
}

/**
 * Inline error under the sign-in step's buttons. Typed so the VM holds no
 * user-facing copy: `OnboardingScreen.kt` resolves each one at render time.
 */
sealed interface OnboardingAccountError {
    /** What `onboarding.sign_in_failed` calls it. */
    val eventName: String

    /** The guest's Apple ID or Google account already belongs to another Opson account. */
    data object AccountInUse : OnboardingAccountError {
        override val eventName = "in_use"
    }

    /** The provider isn't enabled in the auth backend yet. */
    data object ProviderNotEnabled : OnboardingAccountError {
        override val eventName = "not_enabled"
    }

    /** The network was unreachable. */
    data object Offline : OnboardingAccountError {
        override val eventName = "offline"
    }

    /** Anything else. */
    data object Failed : OnboardingAccountError {
        override val eventName = "failed"
    }
}

sealed interface OnboardingAction {
    /** Internal: fired once on init to resolve the opening step / bounce. */
    data object ResolveEntry : OnboardingAction
    /** "Get started" on Welcome: the sign-in step. */
    data object GetStartedTapped : OnboardingAction
    data class OptionTapped(val option: SetupOption) : OnboardingAction

    /** "Talk to someone" on the statement, shown whichever answer is picked. */
    data object TalkToSomeoneTapped : OnboardingAction
    /** "Continue", "See my starting point" on the statement, or "Start logging" on the last setup step. */
    data object ContinueTapped : OnboardingAction
    /** "Skip" on a question or a setup step: on, with the answer as it is. */
    data object SkipQuestionTapped : OnboardingAction
    data class GoalStyleTapped(val style: GoalChoice) : OnboardingAction
    /** The goal step's stepper, 50 kcal a tap. Lower stops at the floor. */
    data class GoalStepped(val up: Boolean) : OnboardingAction
    data class BirthYearTyped(val text: String) : OnboardingAction
    data class HeightTyped(val text: String) : OnboardingAction
    data class WeightTyped(val text: String) : OnboardingAction
    data class SexPicked(val sex: BodySex) : OnboardingAction

    /** How active an ordinary day is, on the resting energy step. */
    data class ActivityPicked(val level: EverydayActivity) : OnboardingAction
    /** Kilograms and centimetres, or pounds and inches. */
    data class UnitPicked(val unit: WeightUnit) : OnboardingAction
    data class MealTimePicked(val slot: MealSlot, val time: LocalTime) : OnboardingAction
    data class AlternativeTapped(val preset: PresetId) : OnboardingAction
    data object AllModesTapped : OnboardingAction
    /** "Start with ..." on the recommendation step. */
    data object StartTapped : OnboardingAction
    data object SignInWithAppleTapped : OnboardingAction
    data object SignInWithGoogleTapped : OnboardingAction
    data object SignInWithEmailTapped : OnboardingAction
    /** "Sign in to that account", after a guest's link hit an account that already exists. */
    data object UseThatAccountTapped : OnboardingAction
    /** One step back, when [OnboardingState.canGoBack]. */
    data object Back : OnboardingAction
}
