package com.opson.features.onboarding.impl

import com.opson.libraries.core.logging.EXTRA_APP_EVENT
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.LinkIdentityOutcome
import com.opson.libraries.identity.auth.OAuthProvider
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.opson.AppData
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalDate

/**
 * Covers the onboarding flow with **deferred account creation**:
 *
 *  - Entry: an onboarded user bounces to Today; a claimed account that has not
 *    set up lands on Question 1; a stranded guest is adopted.
 *  - Set it up enters the questions without creating an account.
 *  - Answers: the feel question replaces, the others toggle; each answer
 *    recomputes the recommendation and the alternatives.
 *  - Next and Skip walk to the recommendation; Back walks back, gated.
 *  - Start and Skip all write the preset and the answers without stamping
 *    `changedAt`, then create the guest, mark onboarded and go to Today.
 *  - OAuth and Apple: returning accounts go to Today, new ones answer the
 *    questions with Back to Welcome blocked.
 *  - No event attribute ever carries an answer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest : CoroutineTest() {

    private val events = RecordingTree()

    @AfterTest
    fun tearDown() {
        KLog.uproot(events)
    }

    // ---------- Entry ----------

    @Test
    fun init_alreadyOnboarded_immediatelyNavigatesToToday() = runUnitTest {
        val cache = FakeAppCache(initial = AppData(hasUserOnboarded = true))
        val vm = newVm(cache = cache)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }
        runCurrent()

        assertEquals(OnboardingEvent.NavigateToToday, received.firstOrNull())
    }

    @Test
    fun init_authenticatedNotOnboarded_opensAtQuestionOne_claimed() = runUnitTest {
        // A just-confirmed email signup routed back from VerifyEmail: a real
        // (non-anonymous) account that hasn't finished onboarding opens at the
        // first question with the back-to-Welcome control suppressed.
        val cache = FakeAppCache(initial = AppData(hasUserOnboarded = false))
        val auth = FakeAuthRepository(initialAuthState = sampleAuthenticated)
        val vm = newVm(cache = cache, auth = auth)
        runCurrent()

        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)
        assertTrue(vm.state.identityClaimed)
        assertFalse(vm.state.canGoBack)
    }

    @Test
    fun init_returningAnonymousGuest_skipsOnboarding_navigatesToToday() = runUnitTest {
        // An install with a pre-existing anonymous guest session that reaches
        // onboarding-not-onboarded is a RETURNING guest whose flag was lost,
        // not a new user. It skips the questions and adopts the onboarded flag
        // so it can't bounce back here.
        val cache = FakeAppCache(initial = AppData(hasUserOnboarded = false))
        val auth = FakeAuthRepository(initialAuthState = sampleAnonymousGuest)
        val settings = FakeSettingsRepository()
        val vm = newVm(cache = cache, auth = auth, settings = settings)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }
        runCurrent()

        assertEquals(OnboardingEvent.NavigateToToday, received.firstOrNull())
        assertTrue(cache.get().hasUserOnboarded)
        assertEquals(OnboardingStep.Welcome, vm.state.step, "must not drop into the new-user questions")
        assertTrue(settings.stampFlags.isEmpty(), "a returning guest keeps the settings it already has")
    }

    @Test
    fun init_newUser_landsOnWelcome_andLogsTheWelcomeStep() = runUnitTest {
        KLog.plant(events)
        val vm = newVm()
        runCurrent()

        assertEquals(OnboardingStep.Welcome, vm.state.step)
        assertEquals(listOf("welcome"), events.steps())
    }

    // ---------- Set it up ----------

    @Test
    fun continueAsGuest_entersQuestionOne_withoutCreatingAnAccount() = runUnitTest {
        val auth = FakeAuthRepository() // Unauthenticated by default
        val creator = FakeGuestAccountCreator()
        val vm = newVm(auth = auth, creator = creator)

        vm.takeAction(OnboardingAction.ContinueAsGuest)
        runCurrent()

        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)
        assertNull(vm.state.authError)
        assertEquals(0, creator.startCalls, "no account until the user finishes")
        assertTrue(vm.state.canGoBack, "a guest can still go back to Welcome")
    }

    // ---------- Answers ----------

    @Test
    fun optionTapped_multiSelectQuestion_togglesEachAnswer() = runUnitTest {
        val vm = atQuestion(SetupQuestion.Reason)

        vm.takeAction(OnboardingAction.OptionTapped(Why.Curious))
        vm.takeAction(OnboardingAction.OptionTapped(Why.EatingEnough))
        runCurrent()
        assertEquals(setOf(Why.Curious, Why.EatingEnough), vm.state.answers.why)
        assertEquals(
            listOf(Why.Curious, Why.EatingEnough),
            vm.state.options.filter { it.selected }.map { it.option },
        )

        vm.takeAction(OnboardingAction.OptionTapped(Why.Curious))
        runCurrent()
        assertEquals(setOf(Why.EatingEnough), vm.state.answers.why)
    }

    @Test
    fun optionTapped_feelQuestion_replacesTheAnswer() = runUnitTest {
        val vm = atQuestion(SetupQuestion.Feeling)

        vm.takeAction(OnboardingAction.OptionTapped(NumbersFeel.LoveThem))
        vm.takeAction(OnboardingAction.OptionTapped(NumbersFeel.RatherNot))
        runCurrent()

        assertEquals(NumbersFeel.RatherNot, vm.state.answers.feel)
        assertEquals(1, vm.state.options.count { it.selected })
    }

    @Test
    fun optionTapped_hardQuestion_togglesTheHardAnswer() = runUnitTest {
        val vm = atQuestion(SetupQuestion.Hard)

        vm.takeAction(OnboardingAction.OptionTapped(HardBefore))
        runCurrent()
        assertTrue(vm.state.answers.hard)

        vm.takeAction(OnboardingAction.OptionTapped(HardBefore))
        runCurrent()
        assertFalse(vm.state.answers.hard)
    }

    @Test
    fun optionTapped_forAnotherQuestion_isIgnored() = runUnitTest {
        val vm = atQuestion(SetupQuestion.Reason)

        vm.takeAction(OnboardingAction.OptionTapped(NumbersFeel.LoveThem))
        runCurrent()

        assertNull(vm.state.answers.feel)
    }

    @Test
    fun optionTapped_recomputesTheRecommendation_andClearsAPickedAlternative() = runUnitTest {
        val vm = atQuestion(SetupQuestion.Feeling)
        vm.takeAction(OnboardingAction.OptionTapped(NumbersFeel.LoveThem))
        runCurrent()
        assertEquals(PresetId.Full, vm.state.recommended)

        // Walk to the recommendation, pick an alternative, then come back and
        // say numbers have been hard: the pick is cleared and Full is gone.
        repeat(3) { vm.takeAction(OnboardingAction.NextTapped) }
        runCurrent()
        vm.takeAction(OnboardingAction.AlternativeTapped(PresetId.Blind))
        runCurrent()
        assertEquals(PresetId.Blind, vm.state.chosen)

        vm.takeAction(OnboardingAction.Back)
        vm.takeAction(OnboardingAction.OptionTapped(HardBefore))
        runCurrent()

        assertNull(vm.state.picked)
        assertEquals(PresetId.Delayed, vm.state.recommended)
        assertFalse(PresetId.Full in vm.state.alternatives)
        assertFalse(PresetId.Sprint in vm.state.alternatives)
    }

    // ---------- Next, Skip, Back ----------

    @Test
    fun nextAndSkip_walkTheQuestions_toTheRecommendation_loggingEachStep() = runUnitTest {
        KLog.plant(events)
        val vm = atQuestion(SetupQuestion.Reason)

        vm.takeAction(OnboardingAction.NextTapped)
        runCurrent()
        assertEquals(OnboardingStep.Question(SetupQuestion.Feeling), vm.state.step)
        vm.takeAction(OnboardingAction.SkipQuestionTapped)
        runCurrent()
        assertEquals(OnboardingStep.Question(SetupQuestion.Focus), vm.state.step)
        vm.takeAction(OnboardingAction.SkipQuestionTapped)
        vm.takeAction(OnboardingAction.NextTapped)
        runCurrent()

        assertEquals(OnboardingStep.Recommendation, vm.state.step)
        assertEquals(
            listOf("welcome", "question_1", "question_2", "question_3", "question_4", "recommendation"),
            events.steps(),
        )
    }

    @Test
    fun skipQuestion_leavesTheAnswerAsItIs() = runUnitTest {
        val vm = atQuestion(SetupQuestion.Reason)
        vm.takeAction(OnboardingAction.OptionTapped(Why.Performance))
        vm.takeAction(OnboardingAction.SkipQuestionTapped)
        runCurrent()

        assertEquals(setOf(Why.Performance), vm.state.answers.why)
    }

    @Test
    fun nextTapped_offTheQuestions_isIgnored() = runUnitTest {
        val vm = newVm()
        vm.takeAction(OnboardingAction.NextTapped)
        runCurrent()

        assertEquals(OnboardingStep.Welcome, vm.state.step)
    }

    @Test
    fun back_walksFromTheRecommendation_toWelcome() = runUnitTest {
        val vm = atQuestion(SetupQuestion.Reason)
        repeat(4) { vm.takeAction(OnboardingAction.NextTapped) }
        runCurrent()
        assertEquals(OnboardingStep.Recommendation, vm.state.step)

        val seen = mutableListOf<OnboardingStep>()
        repeat(5) {
            vm.takeAction(OnboardingAction.Back)
            runCurrent()
            seen += vm.state.step
        }

        assertEquals(
            listOf(
                OnboardingStep.Question(SetupQuestion.Hard),
                OnboardingStep.Question(SetupQuestion.Focus),
                OnboardingStep.Question(SetupQuestion.Feeling),
                OnboardingStep.Question(SetupQuestion.Reason),
                OnboardingStep.Welcome,
            ),
            seen,
        )
        assertFalse(vm.state.canGoBack)
    }

    @Test
    fun back_fromWelcome_isNoOp() = runUnitTest {
        val vm = newVm()

        vm.takeAction(OnboardingAction.Back)
        runCurrent()

        assertEquals(OnboardingStep.Welcome, vm.state.step)
    }

    @Test
    fun back_isBlocked_onceFinishingStarted() = runUnitTest {
        val vm = atRecommendation(creator = FakeGuestAccountCreator(hangInProgress = true))
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        assertTrue(vm.state.isFinishing)

        vm.takeAction(OnboardingAction.Back)
        runCurrent()

        assertEquals(OnboardingStep.Recommendation, vm.state.step)
    }

    // ---------- Recommendation ----------

    @Test
    fun noAnswers_recommendDelayed_withFourAlternatives() = runUnitTest {
        val vm = atRecommendation()

        assertEquals(PresetId.Delayed, vm.state.chosen)
        assertEquals(
            listOf(PresetId.Full, PresetId.Blind, PresetId.Nutrient, PresetId.Rhythm),
            vm.state.alternatives,
        )
    }

    @Test
    fun alternativeTapped_picksIt_andTheRecommendationBecomesAnAlternative() = runUnitTest {
        val vm = atRecommendation()

        vm.takeAction(OnboardingAction.AlternativeTapped(PresetId.Nutrient))
        runCurrent()

        assertEquals(PresetId.Nutrient, vm.state.chosen)
        assertEquals(PresetId.Delayed, vm.state.recommended)
        assertFalse(PresetId.Nutrient in vm.state.alternatives)
        assertTrue(PresetId.Delayed in vm.state.alternatives)
    }

    @Test
    fun alternativeTapped_notOnOffer_isIgnored() = runUnitTest {
        val vm = atRecommendation(answers = listOf(HardBefore))

        vm.takeAction(OnboardingAction.AlternativeTapped(PresetId.Full))
        runCurrent()

        assertNull(vm.state.picked)
        assertEquals(PresetId.Blind, vm.state.chosen)
    }

    // ---------- Finishing ----------

    @Test
    fun start_writesPresetAndAnswers_unstamped_thenCreatesGuest_thenOnboarded_thenToday() = runUnitTest {
        val order = mutableListOf<String>()
        val settings = FakeSettingsRepository(onUpdate = { order += "settings" })
        val creator = FakeGuestAccountCreator(onStart = { order += "guest" })
        val cache = FakeAppCache(onSet = { if (it.hasUserOnboarded) order += "onboarded" })
        val vm = atRecommendation(
            answers = listOf(Why.EatingRegularly, NumbersFeel.RatherNot, Notice.Patterns),
            settings = settings,
            creator = creator,
            cache = cache,
        )
        backgroundScope.launch { vm.eventFlow.collect { order += it.toString() } }
        runCurrent()

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        assertEquals(listOf("settings", "guest", "onboarded", "NavigateToToday"), order)
        assertEquals(listOf(false), settings.stampFlags, "onboarding must not stamp changedAt")
        val saved = settings.settings.value
        assertEquals(NumbersSettings(preset = PresetId.Rhythm), saved.numbers)
        assertNull(saved.numbers.changedAt)
        assertEquals(
            OnboardingAnswers(why = listOf("eating_regularly"), feel = "rather_not", notice = listOf("patterns")),
            saved.onboarding,
        )
        assertNull(saved.sprintStartedOn)
        assertNotNull(creator.lastIdentity?.displayName, "the guest gets a generated name, never asked for")
        assertFalse(vm.state.creationFailed)
    }

    @Test
    fun start_withAPickedAlternative_savesThePick() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atRecommendation(settings = settings)
        vm.takeAction(OnboardingAction.AlternativeTapped(PresetId.Blind))
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        assertEquals(PresetId.Blind, settings.settings.value.numbers.preset)
    }

    @Test
    fun start_twice_finishesOnce() = runUnitTest {
        val settings = FakeSettingsRepository()
        val creator = FakeGuestAccountCreator()
        val vm = atRecommendation(settings = settings, creator = creator)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.StartTapped)
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        assertEquals(1, creator.startCalls)
        assertEquals(1, settings.stampFlags.size)
        assertEquals(listOf<OnboardingEvent>(OnboardingEvent.NavigateToToday), received)
    }

    @Test
    fun skipAll_savesDelayedWithEmptyAnswers_andCreatesTheGuest() = runUnitTest {
        KLog.plant(events)
        val settings = FakeSettingsRepository()
        val creator = FakeGuestAccountCreator()
        val cache = FakeAppCache()
        val vm = newVm(settings = settings, creator = creator, cache = cache)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }
        runCurrent()

        vm.takeAction(OnboardingAction.SkipAllTapped)
        runCurrent()

        assertEquals(NumbersSettings(preset = PresetId.Delayed), settings.settings.value.numbers)
        assertEquals(OnboardingAnswers(), settings.settings.value.onboarding)
        assertEquals(listOf(false), settings.stampFlags)
        assertEquals(1, creator.startCalls)
        assertTrue(cache.get().hasUserOnboarded)
        assertEquals(OnboardingEvent.NavigateToToday, received.single())
        val completed = events.single("onboarding.completed")
        assertEquals("delayed", completed["preset"])
        assertEquals(true, completed["skipped_all"])
        assertEquals(false, completed["took_recommendation"])
    }

    @Test
    fun skipAll_offWelcome_isIgnored() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atQuestion(SetupQuestion.Reason, settings = settings)

        vm.takeAction(OnboardingAction.SkipAllTapped)
        runCurrent()

        assertTrue(settings.stampFlags.isEmpty())
    }

    @Test
    fun start_guestCreationFails_stillNavigatesToToday_flaggedDegraded() = runUnitTest {
        val cache = FakeAppCache()
        val vm = atRecommendation(cache = cache, creator = FakeGuestAccountCreator(failCreation = true))
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        assertTrue(cache.get().hasUserOnboarded)
        assertTrue(vm.state.creationFailed)
        assertEquals(OnboardingEvent.NavigateToToday, received.firstOrNull())
    }

    @Test
    fun start_guestCreationNeverTerminates_finishesAfterTimeout() = runUnitTest {
        // Onboarding must NEVER trap the user on a backend call. If guest
        // creation is wedged/offline and never reaches a terminal state, Start
        // still completes after the bounded wait; the creator keeps retrying in
        // the background.
        val cache = FakeAppCache()
        val vm = atRecommendation(cache = cache, creator = FakeGuestAccountCreator(hangInProgress = true))
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        assertTrue(received.isEmpty())
        advanceTimeBy(6_000)
        runCurrent()

        assertTrue(cache.get().hasUserOnboarded, "onboarding completes even when creation never terminates")
        assertTrue(vm.state.creationFailed, "degraded flag set when the wait times out")
        assertEquals(OnboardingEvent.NavigateToToday, received.firstOrNull())
    }

    @Test
    fun start_withAClaimedAccount_savesSettings_butCreatesNoGuest() = runUnitTest {
        val cache = FakeAppCache()
        val settings = FakeSettingsRepository()
        val creator = FakeGuestAccountCreator()
        val auth = FakeAuthRepository(initialAuthState = sampleAuthenticated)
        val vm = newVm(cache = cache, auth = auth, settings = settings, creator = creator)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }
        runCurrent()
        repeat(4) { vm.takeAction(OnboardingAction.SkipQuestionTapped) }

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        assertEquals(0, creator.startCalls, "a claimed account must not create a guest")
        assertEquals(PresetId.Delayed, settings.settings.value.numbers.preset)
        assertTrue(cache.get().hasUserOnboarded)
        assertEquals(OnboardingEvent.NavigateToToday, received.single())
    }

    @Test
    fun completedEvent_saysWhetherTheRecommendationWasTaken() = runUnitTest {
        KLog.plant(events)
        val taken = atRecommendation()
        taken.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        val other = atRecommendation()
        other.takeAction(OnboardingAction.AlternativeTapped(PresetId.Blind))
        other.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        val completed = events.all("onboarding.completed")
        assertEquals(listOf<Any?>(true, false), completed.map { it["took_recommendation"] })
        assertEquals(listOf<Any?>("delayed", "blind"), completed.map { it["preset"] })
        assertEquals(listOf<Any?>(false, false), completed.map { it["skipped_all"] })
        assertTrue(completed.all { "duration_sec" in it && "account_ready" in it })
    }

    @Test
    fun noEventAttribute_everCarriesAnAnswer() = runUnitTest {
        KLog.plant(events)
        val vm = atRecommendation(
            answers = listOf(Why.MedicalNeed, Why.ChangingWeight, NumbersFeel.RatherNot, Notice.HowEatingFeels, HardBefore),
        )
        vm.takeAction(OnboardingAction.Back)
        vm.takeAction(OnboardingAction.NextTapped)
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        val answerWords = (Why.entries + NumbersFeel.entries + Notice.entries + HardBefore)
            .flatMap { listOf(it.key, (it as? Enum<*>)?.name ?: "HardBefore") }
            .map { it.lowercase() }
        val values = events.entries
            .filter { it.context.extras[EXTRA_APP_EVENT].toString().startsWith("onboarding.") }
            .flatMap { entry -> entry.context.extras.values.map { it.toString().lowercase() } }
        assertTrue(values.isNotEmpty(), "the scan saw no onboarding events, so it proves nothing")
        val leaked = values.filter { value -> answerWords.any { it == value } }
        assertTrue(leaked.isEmpty(), "answers reached telemetry: $leaked")
        assertFalse(
            events.entries.any { entry -> entry.context.extras.keys.any { it in setOf("why", "feel", "notice", "hard") } },
            "an event carried an answer key",
        )
    }

    // ---------- Sign-in paths (unchanged except where they land) ----------

    @Test
    fun signInWithOAuth_providerNotEnabled_surfacesOAuthProviderNotEnabled() = runUnitTest {
        val auth = FakeAuthRepository(oauthSignInOutcome = SignInOutcome.ProviderNotEnabled)
        val vm = newVm(auth = auth)

        vm.takeAction(OnboardingAction.SignInWithOAuth(OAuthProvider.Google))
        runCurrent()

        assertEquals(OnboardingAuthError.OAuthProviderNotEnabled, vm.state.authError)
    }

    @Test
    fun signInWithOAuth_networkError_surfacesOAuthNetworkError() = runUnitTest {
        val auth = FakeAuthRepository(
            oauthSignInOutcome = SignInOutcome.NetworkError(RuntimeException("offline")),
        )
        val vm = newVm(auth = auth)

        vm.takeAction(OnboardingAction.SignInWithOAuth(OAuthProvider.Apple))
        runCurrent()

        assertEquals(OnboardingAuthError.OAuthNetworkError, vm.state.authError)
    }

    @Test
    fun signInWithOAuth_returningAccount_marksOnboarded_navigatesToToday_withoutTouchingSettings() = runUnitTest {
        // isNewAccount=false (the FakeProfileRepository default): a returning
        // sign-in. Its settings arrive by sync, so onboarding writes none.
        val cache = FakeAppCache()
        val settings = FakeSettingsRepository()
        val auth = FakeAuthRepository(oauthSignInOutcome = SignInOutcome.Success)
        val vm = newVm(cache = cache, auth = auth, settings = settings)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.SignInWithOAuth(OAuthProvider.Apple))
        runCurrent()

        assertEquals(OAuthProvider.Apple, auth.lastOAuthProvider)
        assertTrue(cache.get().hasUserOnboarded)
        assertEquals(OnboardingEvent.NavigateToToday, received.firstOrNull())
        assertNull(vm.state.oauthInFlight)
        assertTrue(settings.stampFlags.isEmpty())
    }

    @Test
    fun signInWithOAuth_brandNewAccount_answersTheQuestions_notStraightToToday() = runUnitTest {
        val cache = FakeAppCache()
        val auth = FakeAuthRepository(oauthSignInOutcome = SignInOutcome.Success)
        val profile = FakeProfileRepository().apply { isNewAccount = true }
        val vm = newVm(cache = cache, auth = auth, profile = profile)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.SignInWithOAuth(OAuthProvider.Google))
        runCurrent()

        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)
        assertTrue(vm.state.identityClaimed)
        assertFalse(cache.get().hasUserOnboarded, "brand-new sign-up isn't onboarded until they finish")
        assertTrue(received.isEmpty(), "must not navigate from the brand-new path")
        assertNull(vm.state.oauthInFlight)
    }

    @Test
    fun signInWithOAuth_cancelled_silentlyClearsInFlightFlag() = runUnitTest {
        val cache = FakeAppCache()
        val auth = FakeAuthRepository(oauthSignInOutcome = SignInOutcome.Cancelled)
        val vm = newVm(cache = cache, auth = auth)

        vm.takeAction(OnboardingAction.SignInWithOAuth(OAuthProvider.Google))
        runCurrent()

        assertFalse(cache.get().hasUserOnboarded)
        assertNull(vm.state.oauthInFlight)
        assertNull(vm.state.authError)
    }

    @Test
    fun signIn_emitsNavigateToSignInEvent() = runUnitTest {
        val vm = newVm()
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.SignIn)
        runCurrent()

        assertEquals(OnboardingEvent.NavigateToSignIn, received.firstOrNull())
        assertEquals(OnboardingStep.Welcome, vm.state.step)
    }

    @Test
    fun signUp_emitsNavigateToSignUpEvent() = runUnitTest {
        val vm = newVm()
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.SignUp)
        runCurrent()

        assertEquals(OnboardingEvent.NavigateToSignUp, received.firstOrNull())
    }

    @Test
    fun back_isBlockedOnQuestionOne_afterOAuthIdentityClaimed() = runUnitTest {
        // A brand-new OAuth account enters the questions with identityClaimed:
        // the Welcome sign-in options no longer apply, so Back on Question 1 is
        // a no-op. Later questions still step back.
        val auth = FakeAuthRepository(oauthSignInOutcome = SignInOutcome.Success)
        val profile = FakeProfileRepository().apply { isNewAccount = true }
        val vm = newVm(auth = auth, profile = profile)
        vm.takeAction(OnboardingAction.SignInWithOAuth(OAuthProvider.Google))
        vm.takeAction(OnboardingAction.NextTapped)
        vm.takeAction(OnboardingAction.Back)
        runCurrent()
        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)

        vm.takeAction(OnboardingAction.Back)
        runCurrent()

        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)
    }

    @Test
    fun appleSignIn_asAnonymousGuest_linkSuccess_entersTheQuestions_claimed() = runUnitTest {
        // Brand-new Apple identity gets LINKED to the current anonymous guest
        // (keeps progress) and continues onboarding like a new signup. The
        // cache starts onboarded so init's resolve takes the plain Today bounce
        // instead of the returning-guest adoption; the link behavior under
        // test is unaffected either way.
        val cache = FakeAppCache(initial = AppData(hasUserOnboarded = true))
        val auth = FakeAuthRepository(
            linkAppleOutcome = LinkIdentityOutcome.Success,
            initialAuthState = sampleAnonymousGuest,
        )
        val vm = newVm(
            cache = cache,
            auth = auth,
            appleCoordinator = FakeAppleSignInCoordinator(credential = sampleAppleCredential),
        )

        vm.takeAction(OnboardingAction.SignInWithApple)
        runCurrent()

        assertEquals(1, auth.linkAppleCalls, "anonymous guest must take the link path")
        assertEquals(0, auth.appleSignInCalls)
        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)
        assertTrue(vm.state.identityClaimed)
        assertNull(vm.state.oauthInFlight)
    }

    @Test
    fun appleSignIn_identityOnAnotherAccount_signsIn_andNavigatesToToday() = runUnitTest {
        // The Apple identity already belongs to an existing account: the link
        // is rejected, so we switch sessions and skip onboarding entirely.
        val cache = FakeAppCache(initial = AppData(hasUserOnboarded = true))
        val auth = FakeAuthRepository(
            linkAppleOutcome = LinkIdentityOutcome.AlreadyOnAnotherAccount,
            appleSignInOutcome = SignInOutcome.Success,
            initialAuthState = sampleAnonymousGuest,
        )
        val vm = newVm(
            cache = cache,
            auth = auth,
            appleCoordinator = FakeAppleSignInCoordinator(credential = sampleAppleCredential),
        )
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.SignInWithApple)
        runCurrent()

        assertEquals(1, auth.appleSignInCalls, "rejected link must fall back to sign-in")
        assertTrue(cache.get().hasUserOnboarded)
        assertEquals(OnboardingEvent.NavigateToToday, received.firstOrNull())
        assertNull(vm.state.oauthInFlight)
    }

    @Test
    fun appleSignIn_noAnonSession_brandNewAccount_answersTheQuestions() = runUnitTest {
        // After an account deletion there's NO anonymous session, so the link
        // path is skipped and we sign in. The backend mints a NET-NEW account
        // (server isNewAccount=true). It must run through the questions.
        val cache = FakeAppCache()
        val auth = FakeAuthRepository(
            appleSignInOutcome = SignInOutcome.Success,
            initialAuthState = AuthState.Unauthenticated(),
        )
        val profile = FakeProfileRepository().apply { isNewAccount = true }
        val vm = newVm(
            cache = cache,
            auth = auth,
            profile = profile,
            appleCoordinator = FakeAppleSignInCoordinator(credential = sampleAppleCredential),
        )
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.SignInWithApple)
        runCurrent()

        assertEquals(0, auth.linkAppleCalls, "no anonymous guest to link onto")
        assertEquals(1, auth.appleSignInCalls)
        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)
        assertTrue(vm.state.identityClaimed)
        assertFalse(cache.get().hasUserOnboarded, "net-new account isn't onboarded until they finish")
        assertTrue(received.isEmpty(), "must not navigate from the net-new Apple path")
        assertNull(vm.state.oauthInFlight)
    }

    @Test
    fun appleSignIn_noAnonSession_existingAccount_navigatesToToday() = runUnitTest {
        val cache = FakeAppCache()
        val auth = FakeAuthRepository(
            appleSignInOutcome = SignInOutcome.Success,
            initialAuthState = AuthState.Unauthenticated(),
        )
        val vm = newVm(
            cache = cache,
            auth = auth,
            appleCoordinator = FakeAppleSignInCoordinator(credential = sampleAppleCredential),
        )
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.SignInWithApple)
        runCurrent()

        assertEquals(1, auth.appleSignInCalls)
        assertTrue(cache.get().hasUserOnboarded)
        assertEquals(OnboardingEvent.NavigateToToday, received.firstOrNull())
        assertNull(vm.state.oauthInFlight)
    }

    @Test
    fun appleSignIn_sheetDismissed_isQuietNoOp() = runUnitTest {
        val vm = newVm(appleCoordinator = FakeAppleSignInCoordinator(credential = null))

        vm.takeAction(OnboardingAction.SignInWithApple)
        runCurrent()

        assertNull(vm.state.oauthInFlight)
        assertNull(vm.state.authError)
        assertEquals(OnboardingStep.Welcome, vm.state.step)
    }

    @Test
    fun appleSignIn_nativeFailure_surfacesOAuthFailed() = runUnitTest {
        val vm = newVm(
            appleCoordinator = FakeAppleSignInCoordinator(errorMessage = "ASAuthorization error"),
        )

        vm.takeAction(OnboardingAction.SignInWithApple)
        runCurrent()

        assertEquals(OnboardingAuthError.OAuthFailed, vm.state.authError)
        assertNull(vm.state.oauthInFlight)
    }

    // ---------- Test scaffolding ----------

    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-25T12:00:00Z")
    }

    private fun newVm(
        cache: FakeAppCache = FakeAppCache(),
        auth: FakeAuthRepository = FakeAuthRepository(),
        profile: FakeProfileRepository = FakeProfileRepository(),
        creator: FakeGuestAccountCreator = FakeGuestAccountCreator(),
        appleCoordinator: FakeAppleSignInCoordinator = FakeAppleSignInCoordinator(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): OnboardingViewModel = OnboardingViewModel(
        appCache = cache,
        authRepository = auth,
        authOutcomeClassifier = FakeAuthOutcomeClassifier(profile),
        guestAccountCreator = creator,
        appleSignInCoordinator = appleCoordinator,
        settingsRepository = settings,
        clock = fixedClock,
    )

    /** A guest who tapped Set it up and walked to [question]. */
    private fun kotlinx.coroutines.test.TestScope.atQuestion(
        question: SetupQuestion,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): OnboardingViewModel {
        val vm = newVm(settings = settings)
        vm.takeAction(OnboardingAction.ContinueAsGuest)
        repeat(question.number - 1) { vm.takeAction(OnboardingAction.NextTapped) }
        runCurrent()
        return vm
    }

    /** A guest at the recommendation after tapping [answers] on their questions. */
    private fun kotlinx.coroutines.test.TestScope.atRecommendation(
        answers: List<SetupOption> = emptyList(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        creator: FakeGuestAccountCreator = FakeGuestAccountCreator(),
        cache: FakeAppCache = FakeAppCache(),
    ): OnboardingViewModel {
        val vm = newVm(settings = settings, creator = creator, cache = cache)
        vm.takeAction(OnboardingAction.ContinueAsGuest)
        SetupQuestion.entries.forEach { question ->
            answers.filter { it in question.options }.forEach { vm.takeAction(OnboardingAction.OptionTapped(it)) }
            vm.takeAction(OnboardingAction.NextTapped)
        }
        runCurrent()
        return vm
    }

    private class RecordingTree : LogTree() {
        val entries = mutableListOf<LogEntry>()

        override fun log(entry: LogEntry): LogId? {
            entries += entry
            return null
        }

        fun all(event: String): List<Map<String, Any?>> =
            entries.filter { it.context.extras[EXTRA_APP_EVENT] == event }.map { it.context.extras }

        fun single(event: String): Map<String, Any?> = all(event).single()

        fun steps(): List<Any?> = all("onboarding.step_viewed").map { it["step"] }
    }
}
