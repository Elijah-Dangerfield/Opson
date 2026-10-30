package com.opson.features.onboarding.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.onboarding.OnboardingRoute
import com.opson.features.onboarding.OnboardingSetupEnabled
import com.opson.libraries.core.AppState
import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.Catching
import com.opson.libraries.core.DeviceRegion
import com.opson.libraries.core.isiOS
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.diary.AgeBand
import com.opson.libraries.diary.AgeGate
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.PlanCommitment
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.health.goalReadings
import com.opson.libraries.diary.baselineEnvironment
import com.opson.libraries.nutrition.HealthReadings
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
import com.opson.libraries.core.fixed
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.TodayLayout
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.opson.AppCache
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/**
 * Onboarding (design v3, reworked in docs/plans/onboarding-v2.md and again when
 * accounts became required): Welcome, sign in, two skippable questions, an
 * optional statement, a starting mode and the setup steps that mode needs.
 *
 *   1. **Welcome**: "Get started" opens the sign-in step.
 *   2. **Age** ([OnboardingStep.Age]): a birthday, before any account exists ([AgeGate]).
 *      Under 16 goes to [OnboardingStep.TooYoung], which makes no account and has no way
 *      back, and this phone keeps the gate closed until their sixteenth birthday
 *      (`AppData.ageGateOpensOn`). 16 and 17 go on with the goal flow's gentle path: no
 *      weights, weight goals or deficits, and celebrations without counts. The birthday
 *      fills the profile step's. A guest from an older build skips it.
 *   3. **Sign in** ([OnboardingStep.SignIn]): Apple (iOS), Google, and email
 *      when it is on ([SignInMethods.EMAIL]). There is no way past it without
 *      an account. A new account goes on to the questions; one that already
 *      exists is set up already, so it goes to Today and keeps its mode.
 *   4. **Questions 1 and 2 and the statement**: each answer recomputes the
 *      recommendation ([ModeRecommender]). Every step can be skipped and
 *      nothing asks again later.
 *   5. **Recommendation**: the mode, the answer that picked it, what you'll see
 *      and what stays out of view ([ModePreview]), the other modes and All
 *      modes. All numbers is never offered to someone who agreed numbers have
 *      been hard.
 *   6. **Setup** (`release.onboardingSetup`, [OnboardingState.setupSteps]): meal
 *      times where Today is built around them, then the goal flow ([PlanFlow]):
 *      the profile, a usual day's activity, a weight goal and its pace where the
 *      mode shows weight and numbers have not been hard, an overview of what
 *      each day aims for, and the commitment. Someone who said numbers have been
 *      hard gets the profile without weight, the activity, a gentle overview and
 *      the commitment: no weights and no deficits. Every step but the overview
 *      and the commitment can be skipped.
 *
 * Saving (the commitment's "I'm committed" or "Not now", or Start when the mode
 * needs no setup) writes the preset, the answers and the setup to settings
 * without stamping `changedAt` (so Modes says "Set by you during setup"), logs
 * the weight as the first weigh-in, marks `hasUserOnboarded` and goes to Today.
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
    private val health: HealthRepository,
    private val healthFlag: HealthReleaseFlag,
    /** The phone's region, for the units the profile step starts in. Never sent. */
    region: DeviceRegion,
    @Assisted private val route: OnboardingRoute,
    /** Native Sign in with Apple, which only iOS has. A parameter so tests can run either phone. */
    appleSignIn: Boolean = BuildInfo.isiOS(),
    private val clock: Clock = Clock.System,
    /** Checked before Google or Apple opens, which would otherwise wait on a browser or sheet that can't load. */
    private val connection: AppState = AssumeOnline,
) : SEAViewModel<OnboardingState, OnboardingEvent, OnboardingAction>(
    initialStateArg = OnboardingState(
        appleEnabled = appleSignIn,
        setupSteps = setupEnabled(),
        today = calendar.today(),
        plan = PlanAnswers(body = BodyAnswer.inRegion(region.countryCode())),
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

    /** The weigh-in the profile step logged, so saving again after Back does not log it twice. */
    private var weighedKg: Double? = null

    init {
        // Resolve where onboarding opens through the action loop so state
        // updates run in an action scope, as VerifyEmail's init →
        // ResolveEmailFromSession does.
        takeAction(OnboardingAction.ResolveEntry)
        // The activity step offers the phone's health app, and stands aside once it is connected.
        // With release.health off the store is never asked.
        if (healthFlag()) {
            viewModelScope.launch { health.status.collect { takeAction(OnboardingAction.HealthChanged(it)) } }
        }
    }

    override suspend fun handleAction(action: OnboardingAction) {
        when (action) {
            OnboardingAction.ResolveEntry -> action.handleResolveEntry()
            OnboardingAction.GetStartedTapped -> action.handleGetStarted()
            is OnboardingAction.AgeAnswered -> action.updateState {
                if (it.step == OnboardingStep.Age && AgeGate.ageOn(action.birthDate, it.today) != null) it.copy(ageAnswer = action.birthDate) else it
            }
            OnboardingAction.TalkToSomeoneTapped -> sendEvent(OnboardingEvent.NavigateToSupport)
            is OnboardingAction.OptionTapped -> action.handleOptionTapped(action.option)
            OnboardingAction.ContinueTapped -> action.advance()
            OnboardingAction.SkipQuestionTapped -> action.skip()
            is OnboardingAction.PlanChanged -> action.handlePlan(action.change)
            is OnboardingAction.HealthChanged -> {
                action.updateState { it.copy(health = HealthOffer.from(action.status, health.source, connecting = false)).withViews(policyFor(it.chosen)) }
                // What a goal's typical day reads from the health app, read once it is connected,
                // so the target the plan shows is the one the goal keeps afterwards.
                if (action.status == HealthStatus.Connected) {
                    val readings = Catching { health.goalReadings(calendar.today(), calendar.timeZone()) }.getOrNull() ?: HealthReadings.None
                    action.updateState { it.copy(healthReadings = readings).withViews(policyFor(it.chosen)) }
                }
            }
            is OnboardingAction.CommitTapped -> action.handleCommit(action.committed)
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
        if (isAgeGateClosed()) return updateState { it.copy(step = OnboardingStep.TooYoung) }
        if (route.signInFirst) openSignIn(guest = state.guest, signInOnly = false) else logStepViewed(OnboardingStep.Welcome)
    }

    private suspend fun OnboardingAction.openSignIn(guest: Boolean, signInOnly: Boolean) {
        updateState { it.copy(step = OnboardingStep.SignIn, guest = guest, signInOnly = signInOnly) }
        logStepViewed(OnboardingStep.SignIn)
    }

    /** "Get started": the birthday, or straight to sign-in for a guest from an older build. */
    private suspend fun OnboardingAction.handleGetStarted() {
        if (state.step != OnboardingStep.Welcome) return
        goTo(if (state.guest) OnboardingStep.SignIn else OnboardingStep.Age)
    }

    /** This phone was told someone under 16 is using it, and they haven't turned 16 since. */
    private suspend fun isAgeGateClosed(): Boolean {
        val opensOn = Catching { appCache.get().ageGateOpensOn }.getOrNull() ?: return false
        val day = Catching { LocalDate.parse(opensOn) }.getOrNull() ?: return false
        return state.today < day
    }

    /**
     * Continue on the birthday. Under 16 closes the gate on this phone until their sixteenth
     * birthday and makes no account; the birthday itself is not kept. Anyone else signs in, with
     * the birthday carried to the profile step.
     */
    private suspend fun OnboardingAction.handleAgeContinue() {
        val born = state.ageAnswer ?: return
        if (AgeGate.bandOf(born, state.today) == AgeBand.TooYoung) {
            Catching { appCache.update { it.copy(ageGateOpensOn = AgeGate.opensOn(born).toString()) } }
                .logOnFailure { "Onboarding could not close the age gate" }
            googleAttempt?.cancel()
            // No step event: the app sends nothing more about someone this young.
            updateState { it.copy(step = OnboardingStep.TooYoung, ageAnswer = null) }
            return
        }
        updateState { it.copy(plan = it.plan.copy(body = it.plan.body.copy(birthDate = born))) }
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
        if (connection.isOffline.value) return fail(AccountMethod.Apple, OnboardingAccountError.Offline)
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
        if (connection.isOffline.value) return fail(AccountMethod.Google, OnboardingAccountError.Offline)
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
     * Continue moves on. Off a goal step that is still waiting for an answer it does nothing (the
     * button is greyed out). From the last setup step that is not the commitment it saves, as
     * Start does when the mode needs no setup.
     */
    private suspend fun OnboardingAction.advance() {
        if (state.isFinishing) return
        if (state.step == OnboardingStep.Age) return handleAgeContinue()
        val next = when (val step = state.step) {
            is OnboardingStep.Question -> SetupQuestion.of(step.question.number + 1)
                ?.let { OnboardingStep.Question(it) }
                ?: OnboardingStep.Recommendation
            OnboardingStep.Meals -> state.setupAfter(step) ?: return finish(committed = null)
            is OnboardingStep.Plan -> {
                if (!PlanFlow.canContinue(step.step, state.plan, state.planContext(policyFor(state.chosen)))) return
                state.setupAfter(step) ?: return finish(committed = null)
            }
            else -> return
        }
        goTo(next)
    }

    /**
     * Skip moves on with the answer as it is. On the goal step it means no goal: the direction
     * goes, and the pace step with it, so nothing about a weight is saved.
     */
    private suspend fun OnboardingAction.skip() {
        if (state.isFinishing) return
        val step = state.step
        if (step == OnboardingStep.Plan(PlanStep.Goal)) {
            updateState { it.copy(plan = it.plan.copy(goal = GoalAnswer(target = it.plan.goal.target))) }
        }
        val next = when (step) {
            is OnboardingStep.Question -> SetupQuestion.of(step.question.number + 1)?.let { OnboardingStep.Question(it) }
                ?: OnboardingStep.Recommendation
            OnboardingStep.Meals, is OnboardingStep.Plan -> state.setupAfter(step) ?: return finish(committed = null)
            else -> return
        }
        goTo(next)
    }

    /** A change on one of the goal flow's steps. Connecting the health app is asked for here, from the tap. */
    private suspend fun OnboardingAction.handlePlan(change: PlanAction) {
        val step = (state.step as? OnboardingStep.Plan)?.step ?: return
        if (state.isFinishing) return
        when (change) {
            PlanAction.FindSupportTapped -> sendEvent(OnboardingEvent.NavigateToSupport)
            PlanAction.ConnectHealthTapped -> if (step == PlanStep.Activity) connectHealth()
            else -> updateState { it.copy(plan = PlanFlow.reduce(it.plan, change, it.planContext(policyFor(it.chosen)))).withViews(policyFor(it.chosen)) }
        }
    }

    private suspend fun OnboardingAction.connectHealth() {
        val offer = state.health as? HealthOffer.CanConnect ?: return
        updateState { it.copy(health = offer.copy(connecting = true)).withViews(policyFor(it.chosen)) }
        Catching { health.connect() }.logOnFailure { "Onboarding could not connect the health app" }
        updateState { it.copy(health = HealthOffer.from(health.status.value, health.source, connecting = false)).withViews(policyFor(it.chosen)) }
    }

    /** "I'm committed" or "Not now" on the last step: either way it saves and goes to Today. */
    private suspend fun OnboardingAction.handleCommit(committed: Boolean) {
        if (state.step != OnboardingStep.Plan(PlanStep.Commit) || state.isFinishing) return
        finish(committed = committed)
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
        val first = state.setupAfter(OnboardingStep.Recommendation) ?: return finish(committed = null)
        goTo(first)
    }

    /**
     * Saves the mode and the setup, from Start, the last setup step or the commitment:
     *  1. Save the preset, the answers, the meal times and what the goal flow set
     *     ([PlanFlow.save]), with the commitment when that step was reached. `changedAt`
     *     stays null so Modes can say "Set by you during setup". The account exists
     *     already (sign-in comes first), so the settings row syncs to it.
     *  2. Log the profile's weight as the first weigh-in.
     *  3. Mark `hasUserOnboarded` and go to Today.
     */
    private suspend fun OnboardingAction.finish(committed: Boolean?) {
        if (state.isFinishing) return
        val preset = state.chosen
        val answers = state.answers
        val tookRecommendation = preset == state.recommended
        val context = state.planContext(policyFor(preset))
        val planned = state.setupFlow.any { it is OnboardingStep.Plan }
        val plan = state.plan
        val meals = state.mealTimes.takeIf { state.showsMeals }
        val today = state.today
        updateState { it.copy(isFinishing = true) }

        Catching {
            settingsRepository.update(stampNumbersChange = false) { current ->
                val started = current.startingWith(preset, answers, meals).withAgeAnswer(plan.body.birthDate, today)
                val withPlan = if (planned) PlanFlow.save(started, plan, context) else started
                committed?.let { withPlan.copy(planCommitment = PlanCommitment(today, it)) } ?: withPlan
            }
        }.logOnFailure { "Onboarding could not save the starting mode" }
        if (planned) logWeighIn(context)

        committed?.let { logger.logEvent("onboarding.commitment", "committed" to it) }
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

    /**
     * The profile's weight as a weigh-in, where the field was shown. Weight is never a setting:
     * Burned reads the latest weigh-in.
     */
    private suspend fun logWeighIn(context: PlanContext) {
        if (!PlanViews.profile(state.plan, context).weightShown) return
        val kg = state.plan.body.weightKgOrNull() ?: return
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
            OnboardingStep.Welcome, OnboardingStep.TooYoung -> return
            OnboardingStep.Age -> OnboardingStep.Welcome
            OnboardingStep.SignIn -> if (state.plan.body.birthDate != null && !state.guest) OnboardingStep.Age else OnboardingStep.Welcome
            OnboardingStep.Meals, is OnboardingStep.Plan -> state.setupBefore(step)
            OnboardingStep.Recommendation -> OnboardingStep.Question(SetupQuestion.Statement)
            is OnboardingStep.Question -> SetupQuestion.of(step.question.number - 1)?.let { OnboardingStep.Question(it) } ?: return
        }
        googleAttempt?.cancel()
        updateState {
            it.copy(step = previous, accountInFlight = null, accountError = null, conflict = null).withViews(policyFor(it.chosen))
        }
        logStepViewed(previous)
    }

    override fun onCleared() {
        // Best-effort abandonment marker: the VM outliving the flow without ever
        // saving a mode or reaching Today means the user backed out (system back
        // on Welcome exits the app and clears the entry). A process kill won't
        // reach this; the funnel's step_viewed-without-completed sessions cover
        // that case.
        if (!completed && state.step != OnboardingStep.TooYoung) {
            logger.logEvent("onboarding.abandoned", "step" to state.step.eventName)
        }
        super.onCleared()
    }

    private fun logStepViewed(step: OnboardingStep) {
        logger.logEvent("onboarding.step_viewed", "step" to step.eventName)
    }
}

/** The day previews and tests start on, the fixtures' 2026-10-25. The view model passes the real one. */
internal val DEFAULT_TODAY = LocalDate(2026, 10, 25)

/**
 * [preset]'s levels as a policy, for deciding which steps show. A preset's levels hold no Later,
 * so neither the clock nor the time zone can change an answer; the epoch stands in for both.
 */
internal fun levelsOf(preset: PresetId): VisibilityPolicy =
    VisibilityPolicy(NumbersSettings(preset = preset), Clock.fixed(Instant.fromEpochMilliseconds(0)), TimeZone.UTC)

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

    /** `release.onboardingSetup`: meal times and the goal flow. Off, Start saves straight away. */
    val setupSteps: Boolean = false,
    /** The phone's day: for the birthday picker and when a goal starts. */
    val today: LocalDate = DEFAULT_TODAY,
    val mealTimes: MealTimes = MealTimes(),
    /** The birthday picked on [OnboardingStep.Age], before Continue. Carried on as [PlanAnswers.body]'s. */
    val ageAnswer: LocalDate? = null,
    /** The goal flow's answers ([PlanFlow]). */
    val plan: PlanAnswers = PlanAnswers(),
    /** The phone's health app, as the activity step offers it. */
    val health: HealthOffer = HealthOffer.Unavailable,
    /** What the health app has read, for a goal's typical day; none until it is connected. */
    val healthReadings: HealthReadings = HealthReadings.None,
    /** The goal flow's steps as drawn, through the policy of the mode being started. Null until set up. */
    val planView: PlanView? = null,
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

    /**
     * The meal times step: where Today is built around meal times (No numbers' timeline), or the
     * person is tracking to eat regularly.
     */
    val showsMeals: Boolean
        get() = setupSteps && (Presets[chosen].today == TodayLayout.MealTimeline || Why.EatingRegularly in answers.why)

    /** 16 or 17 by the birthday given ([AgeGate]). */
    val olderTeen: Boolean get() = AgeGate.bandOf(plan.body.birthDate, today) == AgeBand.OlderTeen

    /**
     * The goal flow's context for [policy], the mode being started's. Onboarding is a new account.
     * Someone 16 or 17 gets the gentle path, as after "numbers have been hard": no weights, weight
     * goals or deficits.
     */
    fun planContext(policy: VisibilityPolicy): PlanContext =
        PlanContext(
            today = today,
            hard = answers.hard || olderTeen,
            policy = policy,
            fresh = true,
            health = health,
            environment = Settings().baselineEnvironment(healthReadings),
        )

    /**
     * The setup steps for [chosen], in order: meal times where they shape Today, then the goal
     * flow. Empty with `release.onboardingSetup` off. Which goal steps show depends only on the
     * mode's levels and the answers, so this reads the preset's levels rather than a clock.
     */
    val setupFlow: List<OnboardingStep>
        get() = if (!setupSteps) {
            emptyList()
        } else {
            buildList {
                if (showsMeals) add(OnboardingStep.Meals)
                PlanFlow.steps(plan, planContext(levelsOf(chosen))).forEach { add(OnboardingStep.Plan(it)) }
            }
        }

    /** The setup step after [step] (the recommendation, or a setup step), or null when saving comes next. */
    fun setupAfter(step: OnboardingStep): OnboardingStep? {
        val flow = setupFlow
        return if (step == OnboardingStep.Recommendation) flow.firstOrNull() else flow.getOrNull(flow.indexOf(step) + 1)
    }

    /** The step before setup step [step]: the setup step before it, or the recommendation. */
    fun setupBefore(step: OnboardingStep): OnboardingStep =
        setupFlow.let { flow -> flow.getOrNull(flow.indexOf(step) - 1) } ?: OnboardingStep.Recommendation

    /** The goal flow's views, built through [policy] (the mode being started). */
    internal fun withViews(policy: VisibilityPolicy): OnboardingState =
        copy(planView = if (setupSteps) PlanViews.all(plan, planContext(policy), Settings()) else null)

    /**
     * The sign-in buttons work: on the step, not leaving, and Apple's sheet not up. A Google
     * round trip doesn't block them, since someone who closed the browser has to be able to
     * start again.
     */
    val canTapSignIn: Boolean
        get() = step == OnboardingStep.SignIn && accountInFlight != AccountMethod.Apple && !isFinishing

    val canGoBack: Boolean
        get() = when (step) {
            OnboardingStep.Welcome, OnboardingStep.TooYoung -> false
            OnboardingStep.Age -> !isFinishing
            OnboardingStep.SignIn -> !signInOnly && accountInFlight != AccountMethod.Apple && !isFinishing
            OnboardingStep.Recommendation, OnboardingStep.Meals, is OnboardingStep.Plan -> !isFinishing
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

    /** The birthday, before any account exists ([AgeGate]). */
    data object Age : OnboardingStep {
        override val eventName = "age"
    }

    /** Under 16: no account, and no way back to the question. Never sent as an event. */
    data object TooYoung : OnboardingStep {
        override val eventName = "too_young"
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

    /** One of the goal flow's steps ([PlanStep]): the profile, activity, goal, pace, overview and commitment. */
    data class Plan(val step: PlanStep) : OnboardingStep {
        override val eventName: String get() = step.eventName
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
    /** "Get started" on Welcome: the birthday. */
    data object GetStartedTapped : OnboardingAction

    /** A birthday picked on [OnboardingStep.Age]. Continue then decides. */
    data class AgeAnswered(val birthDate: LocalDate) : OnboardingAction
    data class OptionTapped(val option: SetupOption) : OnboardingAction

    /** "Talk to someone" on the statement, shown whichever answer is picked. */
    data object TalkToSomeoneTapped : OnboardingAction
    /** "Continue", "See my starting point" on the statement, or "Looks good" on the overview. */
    data object ContinueTapped : OnboardingAction
    /** "Skip" on a question or a setup step: on, with the answer as it is. On the goal step, no goal. */
    data object SkipQuestionTapped : OnboardingAction

    /** A change on one of the goal flow's steps. */
    data class PlanChanged(val change: PlanAction) : OnboardingAction

    /** Internal: the health app's status, for the activity step. */
    data class HealthChanged(val status: HealthStatus) : OnboardingAction

    /** "I'm committed" ([committed]) or "Not now" on the last step. Both save and go to Today. */
    data class CommitTapped(val committed: Boolean) : OnboardingAction
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

/**
 * The age gate's answer kept on a new account: the birthday given, and celebrations without
 * counts for someone 16 or 17 unless they had already picked. [birthDate] is null when nobody
 * answered (a guest from an older build).
 */
internal fun Settings.withAgeAnswer(birthDate: LocalDate?, today: LocalDate): Settings {
    val born = birthDate ?: return this
    val teen = AgeGate.bandOf(born, today) == AgeBand.OlderTeen
    return withBirthDate(born).let { if (teen && it.celebrationCounts == null) it.copy(celebrationCounts = false) else it }
}
