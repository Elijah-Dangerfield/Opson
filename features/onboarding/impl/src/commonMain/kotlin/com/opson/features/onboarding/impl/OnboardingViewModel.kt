package com.opson.features.onboarding.impl

import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.Catching
import com.opson.libraries.core.isiOS
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.features.settings.GoalsAndEnergyEnabled
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.identity.auth.AccountCreationState
import com.opson.libraries.identity.auth.AppleSignInCoordinator
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.GuestAccountCreator
import com.opson.libraries.identity.auth.LinkIdentityOutcome
import com.opson.libraries.identity.auth.OAuthProvider
import com.opson.libraries.identity.auth.PendingIdentity
import com.opson.libraries.identity.auth.awaitCredential
import com.opson.libraries.nutrition.BodySex
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.datetime.LocalTime
import kotlinx.coroutines.withTimeoutOrNull
import me.tatarka.inject.annotations.Inject

/**
 * Onboarding (design v3, reworked in docs/plans/onboarding-v2.md): Welcome with
 * the plan, two skippable questions, an optional statement, a starting mode, the
 * setup steps that mode needs, then an optional account step. No account exists
 * on launch; the anonymous guest is minted when the setup is saved, and the
 * account step only claims it.
 *
 *   1. **Welcome**: "Get started" enters the questions; "Skip and start
 *      logging" saves Full numbers and goes straight to Today, past the account
 *      step; "Sign in" opens the existing sign-in flow for returning users.
 *   2. **Questions 1 and 2 and the statement**: each answer recomputes the
 *      recommendation ([ModeRecommender]). Every step can be skipped and
 *      nothing asks again later.
 *   3. **Recommendation**: the mode, the answer that picked it, what you'll see
 *      and what stays out of view ([ModePreview]), the other modes and All
 *      modes. Full numbers is never offered to someone who agreed numbers have
 *      been hard.
 *   4. **Setup** (`release.onboardingSetup`, [OnboardingState.setupSteps]): the
 *      goal where the mode shows calories, resting energy details for a set
 *      deficit or surplus (`release.goalsAndEnergy`), and meal times where Today
 *      is built around them. Each is skippable and keeps the defaults.
 *   5. **Account**: claim the guest with Apple, Google or email, or keep it on
 *      this phone. Skipping is normal. Shown only where one of those is on
 *      ([OnboardingState.canSignIn]): in 0.1 that is Apple, so iOS.
 *
 * Saving writes the preset, the answers and the setup to settings without stamping
 * `changedAt` (so Modes says "Set by you during setup"), creates the guest
 * ([GuestAccountCreator], app-scoped so it survives the ViewModel) with a
 * bounded wait, and marks `hasUserOnboarded`. A claimed identity, or a guest
 * that could not be created in time, has nothing to claim and goes to Today.
 *
 * Hard guard on init: if `AppData.hasUserOnboarded` is already true, fire
 * [OnboardingEvent.NavigateToToday] immediately so a returning user that
 * lands on the route bounces to Today.
 *
 * Telemetry carries step names, the preset key, the account choice and
 * booleans. Never an answer: agreeing that numbers have been hard is close to
 * a clinical disclosure.
 */
@Inject
class OnboardingViewModel(
    private val appCache: AppCache,
    private val authRepository: AuthRepository,
    private val guestAccountCreator: GuestAccountCreator,
    private val appleSignInCoordinator: AppleSignInCoordinator,
    private val settingsRepository: SettingsRepository,
    private val weightRepository: WeightRepository,
    private val calendar: LocalCalendar,
    setupEnabled: OnboardingSetupEnabled,
    goalsAndEnergy: GoalsAndEnergyEnabled,
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
     * True once the mode was saved (`onboarding.completed` fired) or a returning
     * user bounced to Today. Suppresses a second `onboarding.completed` and the
     * best-effort `onboarding.abandoned` fired from [onCleared].
     */
    private var completed = false

    /** True once this ViewModel created the guest the account step can claim. */
    private var guestCreated = false

    /** The weigh-in the resting energy step logged, so saving again after Back does not log it twice. */
    private var weighedKg: Double? = null

    init {
        // Resolve where onboarding opens (Today bounce / questions re-entry /
        // Welcome landing) through the action loop so state updates run in an
        // action scope, as VerifyEmail's init → ResolveEmailFromSession does.
        takeAction(OnboardingAction.ResolveEntry)
    }

    override suspend fun handleAction(action: OnboardingAction) {
        when (action) {
            OnboardingAction.ResolveEntry -> action.handleResolveEntry()
            OnboardingAction.GetStartedTapped -> action.handleGetStarted()
            OnboardingAction.SkipAllTapped -> action.handleSkipAll()
            OnboardingAction.SignInTapped -> if (state.canSignIn) sendEvent(OnboardingEvent.NavigateToSignIn)
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
            is OnboardingAction.UnitPicked -> action.updateSetup(OnboardingStep.Body) { it.copy(body = it.body.copy(unit = action.unit)) }
            is OnboardingAction.MealTimePicked -> action.updateSetup(OnboardingStep.Meals) {
                it.copy(mealTimes = it.mealTimes.with(action.slot, action.time))
            }
            is OnboardingAction.AlternativeTapped -> action.handleAlternativeTapped(action.preset)
            OnboardingAction.AllModesTapped -> action.handleAllModes()
            OnboardingAction.StartTapped -> action.handleStart()
            OnboardingAction.SaveWithAppleTapped -> action.handleSaveWithApple()
            OnboardingAction.SaveWithGoogleTapped -> action.handleSaveWithGoogle()
            OnboardingAction.SaveWithEmailTapped -> action.handleSaveWithEmail()
            OnboardingAction.KeepOnPhoneTapped -> action.leaveAccountStep(AccountChoice.Phone)
            OnboardingAction.Back -> action.handleBack()
        }
    }

    private suspend fun OnboardingAction.handleResolveEntry() {
        Catching {
            val authed = authRepository.current() as? AuthState.Authenticated
            when {
                appCache.get().hasUserOnboarded -> {
                    completed = true
                    sendEvent(OnboardingEvent.NavigateToToday)
                }
                // A real (non-anonymous) account that hasn't finished onboarding
                // is a just-verified email signup bounced back here from
                // VerifyEmail. Drop them straight into the questions, skipping
                // Welcome. identityClaimed suppresses the back-to-Welcome control,
                // and the account step has nothing left to claim.
                authed != null && !authed.isAnonymous -> enterQuestions(identityClaimed = true)
                // An anonymous guest session already exists at launch. Guest
                // creation is deferred (a new user is Unauthenticated here), so a
                // live anonymous session can only be a PRIOR guest whose
                // hasUserOnboarded flag was lost, not a new user. Treat them as
                // returning: adopt the onboarded flag and go to Today.
                authed != null -> {
                    logger.logEvent("onboarding.auth_selected", "method" to "guest", "returning" to true)
                    appCache.update { it.copy(hasUserOnboarded = true) }
                    completed = true
                    sendEvent(OnboardingEvent.NavigateToToday)
                }
                else -> logStepViewed(OnboardingStep.Welcome)
            }
        }.logOnFailure { "Onboarding entry resolution failed" }
    }

    /** "Get started". No auth here: the guest is created when the mode is saved. */
    private suspend fun OnboardingAction.handleGetStarted() {
        if (state.step != OnboardingStep.Welcome || state.isFinishing) return
        logger.logEvent("onboarding.auth_selected", "method" to "guest", "returning" to false)
        enterQuestions(identityClaimed = false)
    }

    /** "Skip and start logging": a guest with no answers, in Full numbers, past the account step. */
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
                identityClaimed = it.identityClaimed || identityClaimed,
            ).withRecommendation()
        }
        logStepViewed(first)
    }

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
                ?: return finish(state.chosen, state.answers, skippedAll = false)
            else -> return
        }
        goTo(next)
    }

    private suspend fun OnboardingAction.goTo(step: OnboardingStep) {
        updateState { it.copy(step = step, accountError = null).withViews(policyFor(it.chosen)) }
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

    /**
     * "Start with ...": the setup steps the mode needs or, with none, save the mode and go on to
     * the account step when there is a guest to claim.
     */
    private suspend fun OnboardingAction.handleStart() {
        if (state.step != OnboardingStep.Recommendation || state.isFinishing) return
        val first = state.setupAfter(OnboardingStep.Recommendation)
            ?: return finish(state.chosen, state.answers, skippedAll = false)
        goTo(first)
    }

    /**
     * Saves [preset], from Start or Skip all:
     *  1. Save the preset and the answers. `changedAt` stays null so Modes can
     *     say "Set by you during setup". Safe before the account exists: the
     *     guest is the first user on this install, so no user-scoped clear
     *     runs, and the settings row syncs once the account is live.
     *  2. No session yet: create the guest and join on it with a bounded wait.
     *     A failed (offline) or slow creation is flagged degraded and the
     *     creator keeps retrying in the background. Never trap the user on a
     *     backend call.
     *  3. Mark `hasUserOnboarded`.
     *  4. Skip all, a claimed identity, a guest that is not there yet, or no
     *     way to claim it on this phone: Today. Otherwise the account step.
     *
     * Runs again when the person comes back from the account step and starts
     * with another mode: the settings are saved again, nothing else repeats.
     */
    private suspend fun OnboardingAction.finish(preset: PresetId, answers: SetupAnswers, skippedAll: Boolean) {
        if (state.isFinishing) return
        val tookRecommendation = !skippedAll && preset == state.recommended
        updateState { it.copy(isFinishing = true, creationStarted = true, accountError = null) }

        val setup = if (skippedAll) StartingSetup() else state.startingSetup()
        Catching {
            settingsRepository.update(stampNumbersChange = false) { it.startingWith(preset, answers, setup) }
        }.logOnFailure { "Onboarding could not save the starting mode" }

        val session = authRepository.current() as? AuthState.Authenticated
        if (session == null && !guestCreated) createGuest()
        if (!skippedAll) logWeighIn()

        if (!completed) {
            completed = true
            logger.logEvent(
                "onboarding.completed",
                "duration_sec" to onboardingStartedAt.elapsedNow().inWholeSeconds,
                "account_ready" to !state.creationFailed,
                "preset" to preset.key,
                "took_recommendation" to tookRecommendation,
                "skipped_all" to skippedAll,
            )
        }
        appCache.update { it.copy(hasUserOnboarded = true) }

        val claimable = state.canSignIn && (guestCreated || session?.isAnonymous == true)
        if (skippedAll || !claimable) {
            // isFinishing stays true: the screen is leaving, and a second tap
            // queued behind this one must not finish again.
            sendEvent(OnboardingEvent.NavigateToToday)
        } else {
            updateState { it.copy(step = OnboardingStep.Account, isFinishing = false) }
            logStepViewed(OnboardingStep.Account)
        }
    }

    /**
     * The resting energy step's weight as a weigh-in, once the account it belongs to exists or
     * is being retried. Weight is never a setting: Burned reads the latest weigh-in.
     */
    private suspend fun logWeighIn() {
        if (!state.showsBody) return
        val kg = state.body.weightKgOrNull() ?: return
        if (kg == weighedKg) return
        Catching { weightRepository.addWeighIn(kg) }
            .logOnFailure { "Onboarding could not log the first weigh-in" }
            .onSuccess { weighedKg = kg }
    }

    private suspend fun OnboardingAction.createGuest() {
        guestAccountCreator.start(PendingIdentity(displayName = DisplayNameSuggester.next()))
        // Bounded wait. If creation hasn't reached a terminal state in time
        // (offline / wedged), go on degraded; the creator keeps the identity
        // and retries in the background.
        val terminal = withTimeoutOrNull(FINISH_CREATION_AWAIT_TIMEOUT) {
            guestAccountCreator.awaitTerminal()
        }
        logger.d { "finish: guest-creation terminal=${terminal?.let { it::class.simpleName } ?: "timed-out"}" }
        if (terminal is AccountCreationState.Succeeded) {
            guestCreated = true
        } else {
            updateState { it.copy(creationFailed = true) }
        }
    }

    /**
     * "Continue with Apple" (iOS): link the native Apple identity onto the
     * guest, keeping everything just saved. A dismissed sheet is a quiet no-op.
     * Never falls back to signing in: an Apple ID already on another account
     * would switch sessions and wipe the settings this flow just wrote.
     */
    private suspend fun OnboardingAction.handleSaveWithApple() {
        if (!state.canClaim) return
        updateState { it.copy(accountInFlight = AccountMethod.Apple, accountError = null) }
        val request = Catching { appleSignInCoordinator.awaitCredential() }
            .logOnFailure { "Apple credential request failed" }
        val credential = request.getOrNull()
        when {
            request.isFailure -> updateState {
                it.copy(accountInFlight = null, accountError = OnboardingAccountError.Failed)
            }
            credential == null -> updateState { it.copy(accountInFlight = null) }
            else -> settleLink(AccountMethod.Apple, authRepository.linkAppleIdentity(credential))
        }
    }

    /** "Continue with Google": link Google onto the guest through the browser round trip. */
    private suspend fun OnboardingAction.handleSaveWithGoogle() {
        if (!state.canClaim) return
        updateState { it.copy(accountInFlight = AccountMethod.Google, accountError = null) }
        settleLink(AccountMethod.Google, authRepository.linkOAuthIdentity(OAuthProvider.Google))
    }

    private suspend fun OnboardingAction.settleLink(method: AccountMethod, outcome: LinkIdentityOutcome) {
        val error = when (outcome) {
            LinkIdentityOutcome.Success -> null
            LinkIdentityOutcome.Cancelled -> null
            LinkIdentityOutcome.AlreadyOnAnotherAccount -> OnboardingAccountError.AccountInUse
            LinkIdentityOutcome.ProviderNotEnabled -> OnboardingAccountError.ProviderNotEnabled
            is LinkIdentityOutcome.NetworkError -> OnboardingAccountError.Offline
            LinkIdentityOutcome.NotSignedIn, is LinkIdentityOutcome.Unknown -> OnboardingAccountError.Failed
        }
        updateState { it.copy(accountInFlight = null, accountError = error) }
        if (outcome == LinkIdentityOutcome.Success) {
            leaveAccountStep(if (method == AccountMethod.Apple) AccountChoice.Apple else AccountChoice.Google)
        }
    }

    /**
     * "Continue with email": the sign-up screen links an email and password onto
     * the anonymous guest, and email verification lands on Today.
     */
    private suspend fun OnboardingAction.handleSaveWithEmail() {
        if (!state.canClaim) return
        updateState { it.copy(accountError = null) }
        logAccountChoice(AccountChoice.Email)
        sendEvent(OnboardingEvent.NavigateToSignUp)
    }

    private suspend fun OnboardingAction.leaveAccountStep(choice: AccountChoice) {
        if (state.step != OnboardingStep.Account || state.isFinishing) return
        logAccountChoice(choice)
        updateState { it.copy(isFinishing = true) }
        sendEvent(OnboardingEvent.NavigateToToday)
    }

    private fun logAccountChoice(choice: AccountChoice) {
        logger.logEvent("onboarding.account_choice", "choice" to choice.eventName)
    }

    /**
     * Account → the setup steps → Recommendation → Statement → Question n − 1 → Welcome. Gated by
     * [OnboardingState.canGoBack]: nothing moves while saving or linking, and question 1 cannot
     * return to Welcome once the mode was saved or an identity is claimed.
     */
    private suspend fun OnboardingAction.handleBack() {
        if (!state.canGoBack) return
        val previous = when (val step = state.step) {
            OnboardingStep.Welcome -> return
            OnboardingStep.Account, OnboardingStep.Goal, OnboardingStep.Body, OnboardingStep.Meals -> state.setupBefore(step)
            OnboardingStep.Recommendation -> OnboardingStep.Question(SetupQuestion.Statement)
            is OnboardingStep.Question ->
                SetupQuestion.of(step.question.number - 1)?.let { OnboardingStep.Question(it) }
                    ?: OnboardingStep.Welcome
        }
        updateState { it.copy(step = previous, accountError = null) }
        logStepViewed(previous)
    }

    override fun onCleared() {
        // Best-effort abandonment marker: the VM outliving the flow without ever
        // saving a mode means the user backed out (system back on Welcome exits
        // the app and clears the entry). A process kill won't reach this; the
        // funnel's step_viewed-without-completed sessions cover that case.
        if (!completed) {
            logger.logEvent("onboarding.abandoned", "step" to state.step.eventName)
        }
        super.onCleared()
    }

    private fun logStepViewed(step: OnboardingStep) {
        logger.logEvent("onboarding.step_viewed", "step" to step.eventName)
    }

    companion object {
        /**
         * Upper bound on how long saving waits for the in-flight guest account
         * before going on anyway. Onboarding must never trap the user on a
         * backend call: if creation hasn't settled by now (offline / slow), we
         * proceed degraded and the creator keeps retrying in the background.
         */
        private val FINISH_CREATION_AWAIT_TIMEOUT = 5_000.milliseconds
    }
}

/** The year previews and tests start in, the fixtures' 2026. The view model passes the real one. */
internal const val DEFAULT_YEAR = 2026

/** How the account step claims the guest in place. Email hands off to the sign-up screen. */
enum class AccountMethod { Apple, Google }

/** How someone left the account step, as `onboarding.account_choice` names it. */
private enum class AccountChoice(val eventName: String) {
    Phone("phone"),
    Google("google"),
    Apple("apple"),
    Email("email"),
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

    /** The Apple or Google link running on the account step. Disables its buttons. */
    val accountInFlight: AccountMethod? = null,
    val accountError: OnboardingAccountError? = null,

    /**
     * True when the person arrived with a real identity (a verified email
     * signup). Suppresses the "back to Welcome" affordance and the account
     * step: there is nothing left to claim.
     */
    val identityClaimed: Boolean = false,

    /** True once the mode was saved. Pins question 1 so Welcome cannot run again. */
    val creationStarted: Boolean = false,

    /** True while the mode is being saved, and from leaving until the screen goes. Disables every button. */
    val isFinishing: Boolean = false,

    /**
     * True if guest-account creation failed (offline) by the time the mode was
     * saved. They still land on Today; the account is retried in the
     * background.
     */
    val creationFailed: Boolean = false,

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
    /**
     * This phone offers a way to sign in or claim the guest. Without one, Welcome hides
     * "Sign in" and saving skips the account step: both would be screens with nothing to tap.
     */
    val canSignIn: Boolean get() = appleEnabled || SignInMethods.GOOGLE || SignInMethods.EMAIL

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

    /** The step before [step] (a setup step or the account step): the setup step before it, or the recommendation. */
    fun setupBefore(step: OnboardingStep): OnboardingStep {
        val flow = setupFlow
        val index = if (step == OnboardingStep.Account) flow.size else flow.indexOf(step)
        return flow.getOrNull(index - 1) ?: OnboardingStep.Recommendation
    }

    /**
     * What the setup steps the person saw will save. Only those steps: a goal is never written
     * for someone who never saw one, so their range stays the app's starting one. A deficit or
     * surplus is aimed at resting energy for now; activity joins Burned once it syncs.
     */
    fun startingSetup(): StartingSetup {
        val body = body.takeIf { showsBody }
        return StartingSetup(
            targets = goal.takeIf { showsGoal }?.resolve(burnKcal = body?.restingKcal(currentYear)),
            birthYear = body?.birthYearOrNull(currentYear),
            heightCm = body?.heightCmOrNull(),
            sex = body?.sex,
            weightUnit = body?.unit,
            mealTimes = mealTimes.takeIf { showsMeals },
        )
    }

    /** The goal and resting energy views, built through [policy] (the mode being started). */
    internal fun withViews(policy: VisibilityPolicy): OnboardingState = copy(
        goalView = SetupViews.goal(goal, goalStyles, policy),
        bodyView = SetupViews.body(body, currentYear, policy),
    )

    /** The account step's buttons work: on the step, with nothing running. */
    val canClaim: Boolean
        get() = step == OnboardingStep.Account && accountInFlight == null && !isFinishing

    val canGoBack: Boolean
        get() = when (step) {
            OnboardingStep.Welcome -> false
            OnboardingStep.Recommendation, OnboardingStep.Goal, OnboardingStep.Body, OnboardingStep.Meals -> !isFinishing
            OnboardingStep.Account -> accountInFlight == null && !isFinishing
            is OnboardingStep.Question ->
                !isFinishing && (step.question != SetupQuestion.Reason || (!identityClaimed && !creationStarted))
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

    data object Account : OnboardingStep {
        override val eventName = "account"
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
 * Inline error under the account step's buttons. Typed so the VM holds no
 * user-facing copy: `OnboardingScreen.kt` resolves each one at render time.
 */
sealed interface OnboardingAccountError {
    /** The Apple ID or Google account already belongs to another Opson account. */
    data object AccountInUse : OnboardingAccountError
    /** The provider isn't enabled in the auth backend yet. */
    data object ProviderNotEnabled : OnboardingAccountError
    /** The network was unreachable. */
    data object Offline : OnboardingAccountError
    /** Anything else. */
    data object Failed : OnboardingAccountError
}

sealed interface OnboardingAction {
    /** Internal: fired once on init to resolve the opening step / bounce. */
    data object ResolveEntry : OnboardingAction
    /** "Get started" on Welcome: the guest path, into the questions. */
    data object GetStartedTapped : OnboardingAction
    /** "Skip and start logging" on Welcome. */
    data object SkipAllTapped : OnboardingAction
    /** "Have an account? Sign in" on Welcome. */
    data object SignInTapped : OnboardingAction
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
    /** Kilograms and centimetres, or pounds and inches. */
    data class UnitPicked(val unit: WeightUnit) : OnboardingAction
    data class MealTimePicked(val slot: MealSlot, val time: LocalTime) : OnboardingAction
    data class AlternativeTapped(val preset: PresetId) : OnboardingAction
    data object AllModesTapped : OnboardingAction
    /** "Start with ..." on the recommendation step. */
    data object StartTapped : OnboardingAction
    data object SaveWithAppleTapped : OnboardingAction
    data object SaveWithGoogleTapped : OnboardingAction
    data object SaveWithEmailTapped : OnboardingAction
    /** "Keep it on this phone" on the account step. */
    data object KeepOnPhoneTapped : OnboardingAction
    /** One step back, when [OnboardingState.canGoBack]. */
    data object Back : OnboardingAction
}
