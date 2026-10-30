package com.opson.features.onboarding.impl

import com.opson.features.onboarding.OnboardingRoute
import com.opson.libraries.core.logging.EXTRA_APP_EVENT
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.identity.auth.AuthOutcome
import com.opson.libraries.identity.auth.LinkIdentityOutcome
import com.opson.libraries.identity.auth.OAuthProvider
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.opson.AppData
import com.opson.libraries.ui.components.text.DateParts
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/**
 * Covers onboarding as design v3 draws it, with sign-in required up front:
 *
 *  - Entry: a signed-in, onboarded account bounces to Today; a signed-in account
 *    that has not set up lands on question 1; an onboarded guest from an older
 *    build lands on sign-in with nowhere to go back to; everyone else gets
 *    Welcome (or sign-in, with `signInFirst`).
 *  - Welcome's Get started opens sign-in.
 *  - Sign-in: a new account goes to the questions, a returning one to Today.
 *    A guest links instead, and keeps their log. Google never locks the
 *    buttons, so closing the browser can't strand anyone.
 *  - Answers: why toggles, everything else replaces; each answer recomputes
 *    the recommendation and the alternatives.
 *  - Continue and Skip walk to the recommendation; Back walks back, gated.
 *  - Start saves the preset and the answers without stamping `changedAt`,
 *    marks onboarded and goes to Today.
 *  - No event attribute ever carries an answer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest : CoroutineTest() {

    private companion object {
        // FixedCalendar's day is 2026-10-25.
        val ADULT = kotlinx.datetime.LocalDate(1990, 4, 18)
        val SIXTEEN = kotlinx.datetime.LocalDate(2010, 1, 5)
        val FOURTEEN = kotlinx.datetime.LocalDate(2012, 3, 2)
    }

    private val events = RecordingTree()

    @AfterTest
    fun tearDown() {
        KLog.uproot(events)
    }

    // ---------- Entry ----------

    @Test
    fun init_signedInAndOnboarded_immediatelyNavigatesToToday() = runUnitTest {
        val cache = FakeAppCache(initial = AppData(hasUserOnboarded = true))
        val vm = newVm(cache = cache, auth = FakeAuthRepository(initialAuthState = sampleAuthenticated))
        val received = collectEvents(vm)
        runCurrent()

        assertEquals(OnboardingEvent.NavigateToToday, received.firstOrNull())
    }

    @Test
    fun init_signedInNotOnboarded_opensAtQuestionOne_withNoWayBack() = runUnitTest {
        // A new account that closed the app mid-setup, or a just-confirmed
        // email signup routed back from VerifyEmail.
        val vm = newVm(auth = FakeAuthRepository(initialAuthState = sampleAuthenticated), settings = FakeSettingsRepository(Settings().withBirthDate(ADULT)))
        runCurrent()

        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)
        assertFalse(vm.state.canGoBack)
        assertTrue(vm.state.ageAsked, "the profile step doesn't ask it again")
    }

    // ---------- The birthday, kept through sign-in ----------

    @Test
    fun theBirthday_waitsOnThePhone_throughAnEmailSignUp_andIsKeptOnTheNewAccount() = runUnitTest {
        val cache = FakeAppCache()
        val first = newVm(cache = cache)
        first.takeAction(OnboardingAction.GetStartedTapped)
        first.takeAction(OnboardingAction.AgeTyped(DateParts.of(SIXTEEN)))
        first.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(SIXTEEN.toString(), cache.get().pendingBirthDate)

        // Email sign-up and its verification open onboarding afresh, signed in.
        val settings = FakeSettingsRepository()
        val second = newVm(cache = cache, settings = settings, auth = FakeAuthRepository(initialAuthState = sampleAuthenticated), setupSteps = true)
        runCurrent()

        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), second.state.step)
        assertEquals(SIXTEEN, settings.settings.value.knownBirthDate, "on the account before setup ends")
        assertEquals(false, settings.settings.value.celebrationCounts)
        assertNull(cache.get().pendingBirthDate)
        assertTrue(second.state.planContext(OnboardingFixtures.policy(PresetId.Full)).hard, "the gentle path")
    }

    @Test
    fun aNewAccount_keepsTheBirthday_asSoonAsItIsMade() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atSignIn(
            auth = FakeAuthRepository(appleSignInOutcome = SignInOutcome.Success),
            classifier = StubAuthOutcomeClassifier(AuthOutcome.SignedUp),
            appleCoordinator = FakeAppleSignInCoordinator(credential = sampleAppleCredential),
            settings = settings,
        )
        vm.takeAction(OnboardingAction.SignInWithAppleTapped)
        runCurrent()

        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)
        assertEquals(ADULT, settings.settings.value.knownBirthDate, "a restart now can't lose it")
    }

    @Test
    fun anAccountWithNoBirthdayAnywhere_goesBackThroughTheAgeQuestion() = runUnitTest {
        // Killed after sign-in on an older build, or signed in first after a dead session.
        val settings = FakeSettingsRepository()
        val vm = newVm(settings = settings, auth = FakeAuthRepository(initialAuthState = sampleAuthenticated))
        runCurrent()
        assertEquals(OnboardingStep.Age, vm.state.step)
        assertFalse(vm.state.canGoBack, "no Welcome or sign-in behind it")

        vm.takeAction(OnboardingAction.AgeTyped(DateParts.of(ADULT)))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)
        assertEquals(ADULT, settings.settings.value.knownBirthDate)
    }

    @Test
    fun anAccountWithNoBirthday_underSixteen_closesTheGate() = runUnitTest {
        val cache = FakeAppCache()
        val vm = newVm(cache = cache, auth = FakeAuthRepository(initialAuthState = sampleAuthenticated))
        runCurrent()
        vm.takeAction(OnboardingAction.AgeTyped(DateParts.of(FOURTEEN)))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(OnboardingStep.TooYoung, vm.state.step)
        assertEquals("2028-03-02", cache.get().ageGateOpensOn)

        val again = newVm(cache = cache, auth = FakeAuthRepository(initialAuthState = sampleAuthenticated))
        runCurrent()
        assertEquals(OnboardingStep.TooYoung, again.state.step, "an account that isn't set up stays behind the gate")
    }

    @Test
    fun underSixteen_typedOnTheProfileStep_isTreatedAsUnderEighteen() {
        val state = OnboardingState(plan = PlanAnswers(body = BodyAnswer(birthDate = FOURTEEN)))
        assertTrue(state.underEighteen)
        assertTrue(state.planContext(OnboardingFixtures.policy(PresetId.Full)).hard)
        assertEquals(false, Settings().withAgeAnswer(FOURTEEN, FixedCalendar.today()).celebrationCounts)
    }

    @Test
    fun init_onboardedGuest_opensOnSignIn_toKeepTheirLog() = runUnitTest {
        // Someone who started before accounts were required.
        KLog.plant(events)
        val cache = FakeAppCache(initial = AppData(hasUserOnboarded = true))
        val vm = newVm(cache = cache, auth = FakeAuthRepository(initialAuthState = sampleAnonymousGuest))
        val received = collectEvents(vm)
        runCurrent()

        assertEquals(OnboardingStep.SignIn, vm.state.step)
        assertTrue(vm.state.guest)
        assertFalse(vm.state.canGoBack, "there's no Welcome behind it")
        assertTrue(received.isEmpty(), "a guest doesn't get into the app without signing in")
        assertEquals(listOf("sign_in"), events.steps())
    }

    @Test
    fun init_guestWithoutTheOnboardedFlag_getsWelcome_thenSignInLinks() = runUnitTest {
        // A reinstall: the Keychain kept the anonymous session, everything else is new.
        val vm = newVm(auth = FakeAuthRepository(initialAuthState = sampleAnonymousGuest))
        runCurrent()
        assertEquals(OnboardingStep.Welcome, vm.state.step)

        vm.takeAction(OnboardingAction.GetStartedTapped)
        runCurrent()

        assertEquals(OnboardingStep.SignIn, vm.state.step)
        assertTrue(vm.state.guest)
        assertTrue(vm.state.canGoBack)
    }

    @Test
    fun init_newInstall_landsOnWelcome_andLogsTheWelcomeStep() = runUnitTest {
        KLog.plant(events)
        val vm = newVm()
        runCurrent()

        assertEquals(OnboardingStep.Welcome, vm.state.step)
        assertEquals(listOf("welcome"), events.steps())
    }

    @Test
    fun init_signInFirst_opensOnSignIn_withWelcomeBehindIt() = runUnitTest {
        val vm = newVm(route = OnboardingRoute(signInFirst = true))
        runCurrent()

        assertEquals(OnboardingStep.SignIn, vm.state.step)
        assertFalse(vm.state.guest)

        vm.takeAction(OnboardingAction.Back)
        runCurrent()
        assertEquals(OnboardingStep.Welcome, vm.state.step)
    }

    // ---------- Welcome ----------

    @Test
    fun getStarted_asksTheBirthday_thenSignIn_andBackWalksBack() = runUnitTest {
        KLog.plant(events)
        val vm = newVm()

        vm.takeAction(OnboardingAction.GetStartedTapped)
        runCurrent()
        assertEquals(OnboardingStep.Age, vm.state.step)

        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(OnboardingStep.Age, vm.state.step, "Continue waits for a birthday")

        vm.takeAction(OnboardingAction.AgeTyped(DateParts.of(ADULT)))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(OnboardingStep.SignIn, vm.state.step)
        assertTrue(vm.state.canTapSignIn)
        assertEquals(ADULT, vm.state.plan.body.birthDate, "the profile step starts with it")

        vm.takeAction(OnboardingAction.Back)
        runCurrent()
        assertEquals(OnboardingStep.Age, vm.state.step)
        vm.takeAction(OnboardingAction.Back)
        runCurrent()
        assertEquals(OnboardingStep.Welcome, vm.state.step)
        assertEquals(listOf("welcome", "age", "sign_in", "age", "welcome"), events.steps())
    }

    // ---------- The age gate ----------

    @Test
    fun underSixteen_makesNoAccount_hasNoWayBack_andSendsNothing() = runUnitTest {
        KLog.plant(events)
        val cache = FakeAppCache()
        val auth = FakeAuthRepository()
        val vm = newVm(cache = cache, auth = auth)
        vm.takeAction(OnboardingAction.GetStartedTapped)
        vm.takeAction(OnboardingAction.AgeTyped(DateParts.of(FOURTEEN)))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()

        assertEquals(OnboardingStep.TooYoung, vm.state.step)
        assertFalse(vm.state.canGoBack)
        assertNull(vm.state.ageAnswer, "the birthday isn't kept")
        assertEquals("2028-03-02", cache.get().ageGateOpensOn, "only the day the gate opens")

        vm.takeAction(OnboardingAction.Back)
        vm.takeAction(OnboardingAction.SignInWithGoogleTapped)
        runCurrent()
        assertEquals(OnboardingStep.TooYoung, vm.state.step)
        assertEquals(0, auth.oauthSignInCalls, "no account is made")
        assertEquals(listOf("welcome", "age"), events.steps(), "nothing is sent about someone this young")
    }

    @Test
    fun aClosedGate_staysClosedOnThisPhone_untilTheirSixteenthBirthday() = runUnitTest {
        val closed = newVm(cache = FakeAppCache(initial = AppData(ageGateOpensOn = "2026-10-26")))
        runCurrent()
        assertEquals(OnboardingStep.TooYoung, closed.state.step)

        val signInFirst = newVm(cache = FakeAppCache(initial = AppData(ageGateOpensOn = "2026-10-26")), route = OnboardingRoute(signInFirst = true))
        runCurrent()
        assertEquals(OnboardingStep.TooYoung, signInFirst.state.step)

        val open = newVm(cache = FakeAppCache(initial = AppData(ageGateOpensOn = "2026-10-25")))
        runCurrent()
        assertEquals(OnboardingStep.Welcome, open.state.step, "the gate opens on the birthday itself")
    }

    @Test
    fun aGuestFromAnOlderBuild_skipsTheBirthday() = runUnitTest {
        val vm = newVm(auth = FakeAuthRepository(initialAuthState = sampleAnonymousGuest))
        runCurrent()
        vm.takeAction(OnboardingAction.GetStartedTapped)
        runCurrent()
        assertEquals(OnboardingStep.SignIn, vm.state.step)
    }

    @Test
    fun sixteenOrSeventeen_getsTheGentlePath_andCelebrationsWithoutCounts() = runUnitTest {
        val vm = newVm(setupSteps = true)
        vm.takeAction(OnboardingAction.GetStartedTapped)
        vm.takeAction(OnboardingAction.AgeTyped(DateParts.of(SIXTEEN)))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()

        assertEquals(OnboardingStep.SignIn, vm.state.step)
        assertTrue(vm.state.underEighteen)
        assertTrue(vm.state.planContext(OnboardingFixtures.policy(PresetId.Full)).hard, "no weights, goals or deficits")
        val saved = com.opson.libraries.diary.Settings().withAgeAnswer(SIXTEEN, FixedCalendar.today())
        assertEquals(SIXTEEN, saved.knownBirthDate)
        assertEquals(false, saved.celebrationCounts)
        val adult = com.opson.libraries.diary.Settings().withAgeAnswer(ADULT, FixedCalendar.today())
        assertNull(adult.celebrationCounts)
    }

    @Test
    fun continueTapped_onWelcome_isIgnored() = runUnitTest {
        val vm = newVm()
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()

        assertEquals(OnboardingStep.Welcome, vm.state.step)
    }

    // ---------- Sign in ----------

    @Test
    fun apple_newAccount_goesToTheQuestions_notOnboardedYet() = runUnitTest {
        KLog.plant(events)
        val cache = FakeAppCache()
        val auth = FakeAuthRepository(appleSignInOutcome = SignInOutcome.Success)
        val vm = atSignIn(
            cache = cache,
            auth = auth,
            classifier = StubAuthOutcomeClassifier(AuthOutcome.SignedUp),
            appleCoordinator = FakeAppleSignInCoordinator(credential = sampleAppleCredential),
        )

        vm.takeAction(OnboardingAction.SignInWithAppleTapped)
        runCurrent()

        assertEquals(1, auth.appleSignInCalls)
        assertEquals(0, auth.linkAppleCalls, "no guest to link onto")
        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)
        assertFalse(vm.state.canGoBack, "the account exists now; sign-in is behind them")
        assertFalse(cache.get().hasUserOnboarded, "onboarded only once the mode is saved")
        val selected = events.single("onboarding.auth_selected")
        assertEquals("apple", selected["method"])
        assertEquals(false, selected["returning"])
    }

    @Test
    fun apple_returningAccount_goesToToday_andKeepsItsSettings() = runUnitTest {
        val cache = FakeAppCache()
        val settings = FakeSettingsRepository()
        val vm = atSignIn(
            cache = cache,
            settings = settings,
            auth = FakeAuthRepository(appleSignInOutcome = SignInOutcome.Success),
            classifier = StubAuthOutcomeClassifier(AuthOutcome.SignedIn),
            appleCoordinator = FakeAppleSignInCoordinator(credential = sampleAppleCredential),
        )
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.SignInWithAppleTapped)
        runCurrent()

        assertEquals(OnboardingEvent.NavigateToToday, received.single())
        assertTrue(cache.get().hasUserOnboarded)
        assertTrue(settings.stampFlags.isEmpty(), "a returning account's mode syncs down; nothing overwrites it")
    }

    @Test
    fun google_newAccount_goesToTheQuestions() = runUnitTest {
        KLog.plant(events)
        val auth = FakeAuthRepository(oauthSignInOutcome = SignInOutcome.Success)
        val vm = atSignIn(auth = auth, classifier = StubAuthOutcomeClassifier(AuthOutcome.SignedUp))

        vm.takeAction(OnboardingAction.SignInWithGoogleTapped)
        runCurrent()

        assertEquals(OAuthProvider.Google, auth.lastOAuthProvider)
        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)
        assertNull(vm.state.accountInFlight)
        assertEquals("google", events.single("onboarding.auth_selected")["method"])
    }

    @Test
    fun google_returningAccount_goesToToday() = runUnitTest {
        val cache = FakeAppCache()
        val vm = atSignIn(
            cache = cache,
            auth = FakeAuthRepository(oauthSignInOutcome = SignInOutcome.Success),
            classifier = StubAuthOutcomeClassifier(AuthOutcome.SignedIn),
        )
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.SignInWithGoogleTapped)
        runCurrent()

        assertEquals(OnboardingEvent.NavigateToToday, received.single())
        assertTrue(cache.get().hasUserOnboarded)
    }

    @Test
    fun google_cancelled_staysQuietly() = runUnitTest {
        val vm = atSignIn(auth = FakeAuthRepository(oauthSignInOutcome = SignInOutcome.Cancelled))
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.SignInWithGoogleTapped)
        runCurrent()

        assertTrue(received.isEmpty())
        assertEquals(OnboardingStep.SignIn, vm.state.step)
        assertNull(vm.state.accountError)
        assertNull(vm.state.accountInFlight)
    }

    @Test
    fun aGoogleRoundTripThatNeverComesBack_leavesTheButtonsWorking() = runUnitTest {
        // Someone opens Google, closes the browser and comes back to the app.
        // supabase-kt waits minutes for a redirect that isn't coming.
        val auth = FakeAuthRepository(
            googleGate = CompletableDeferred(),
            appleSignInOutcome = SignInOutcome.Success,
        )
        val vm = atSignIn(
            auth = auth,
            classifier = StubAuthOutcomeClassifier(AuthOutcome.SignedUp),
            appleCoordinator = FakeAppleSignInCoordinator(credential = sampleAppleCredential),
        )

        vm.takeAction(OnboardingAction.SignInWithGoogleTapped)
        runCurrent()
        assertEquals(AccountMethod.Google, vm.state.accountInFlight)
        assertTrue(vm.state.canTapSignIn)
        assertTrue(vm.state.canGoBack)

        vm.takeAction(OnboardingAction.SignInWithAppleTapped)
        runCurrent()

        assertEquals(1, auth.appleSignInCalls)
        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)
    }

    @Test
    fun backDuringAGoogleRoundTrip_returnsToTheBirthday_andForgetsIt() = runUnitTest {
        val vm = atSignIn(auth = FakeAuthRepository(googleGate = CompletableDeferred()))
        vm.takeAction(OnboardingAction.SignInWithGoogleTapped)
        runCurrent()

        vm.takeAction(OnboardingAction.Back)
        runCurrent()

        assertEquals(OnboardingStep.Age, vm.state.step)
        assertNull(vm.state.accountInFlight)
    }

    @Test
    fun signInFailures_showTheirError_logIt_andStay() = runUnitTest {
        KLog.plant(events)
        mapOf(
            SignInOutcome.ProviderNotEnabled to OnboardingAccountError.ProviderNotEnabled,
            SignInOutcome.NetworkError(RuntimeException("offline")) to OnboardingAccountError.Offline,
            SignInOutcome.Unknown(RuntimeException("boom")) to OnboardingAccountError.Failed,
        ).forEach { (outcome, error) ->
            val vm = atSignIn(auth = FakeAuthRepository(oauthSignInOutcome = outcome))

            vm.takeAction(OnboardingAction.SignInWithGoogleTapped)
            runCurrent()

            assertEquals(error, vm.state.accountError, "for $outcome")
            assertEquals(OnboardingStep.SignIn, vm.state.step)
            assertTrue(vm.state.canTapSignIn, "the buttons work again after $outcome")
        }
        assertEquals(
            listOf<Any?>("not_enabled", "offline", "failed"),
            events.all("onboarding.sign_in_failed").map { it["reason"] },
        )
    }

    @Test
    fun apple_sheetDismissed_isQuiet() = runUnitTest {
        val auth = FakeAuthRepository()
        val vm = atSignIn(auth = auth, appleCoordinator = FakeAppleSignInCoordinator(credential = null))

        vm.takeAction(OnboardingAction.SignInWithAppleTapped)
        runCurrent()

        assertEquals(0, auth.appleSignInCalls)
        assertNull(vm.state.accountInFlight)
        assertNull(vm.state.accountError)
        assertEquals(OnboardingStep.SignIn, vm.state.step)
    }

    @Test
    fun apple_nativeFailure_showsFailed() = runUnitTest {
        val vm = atSignIn(appleCoordinator = FakeAppleSignInCoordinator(errorMessage = "ASAuthorization error"))

        vm.takeAction(OnboardingAction.SignInWithAppleTapped)
        runCurrent()

        assertEquals(OnboardingAccountError.Failed, vm.state.accountError)
        assertNull(vm.state.accountInFlight)
    }

    @Test
    fun email_handsOffToSignIn_orToSignUpForAGuest() = runUnitTest {
        val vm = atSignIn()
        val received = collectEvents(vm)
        vm.takeAction(OnboardingAction.SignInWithEmailTapped)
        runCurrent()
        assertEquals(OnboardingEvent.NavigateToSignIn, received.single())

        val guest = newVm(
            cache = FakeAppCache(initial = AppData(hasUserOnboarded = true)),
            auth = FakeAuthRepository(initialAuthState = sampleAnonymousGuest),
        )
        val guestReceived = collectEvents(guest)
        guest.takeAction(OnboardingAction.SignInWithEmailTapped)
        runCurrent()
        assertEquals(OnboardingEvent.NavigateToSignUp, guestReceived.single(), "sign-up links an email onto the guest")
    }

    @Test
    fun signInActions_offTheSignInStep_doNothing() = runUnitTest {
        val auth = FakeAuthRepository(oauthSignInOutcome = SignInOutcome.Success)
        val vm = newVm(auth = auth)
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.SignInWithGoogleTapped)
        vm.takeAction(OnboardingAction.SignInWithEmailTapped)
        vm.takeAction(OnboardingAction.UseThatAccountTapped)
        runCurrent()

        assertTrue(received.isEmpty())
        assertEquals(0, auth.oauthSignInCalls)
    }

    // ---------- A guest from an older build ----------

    @Test
    fun guest_appleLinksOntoTheGuest_thenToday() = runUnitTest {
        KLog.plant(events)
        val cache = FakeAppCache(initial = AppData(hasUserOnboarded = true))
        val auth = FakeAuthRepository(
            initialAuthState = sampleAnonymousGuest,
            linkAppleOutcome = LinkIdentityOutcome.Success,
        )
        val vm = newVm(cache = cache, auth = auth, appleCoordinator = FakeAppleSignInCoordinator(credential = sampleAppleCredential))
        val received = collectEvents(vm)
        runCurrent()

        vm.takeAction(OnboardingAction.SignInWithAppleTapped)
        runCurrent()

        assertEquals(1, auth.linkAppleCalls)
        assertEquals(0, auth.appleSignInCalls, "linking keeps the guest's log; signing in would leave it behind")
        assertEquals(OnboardingEvent.NavigateToToday, received.single())
        assertEquals(true, events.single("onboarding.auth_selected")["returning"])
    }

    @Test
    fun guest_googleLinksOntoTheGuest_thenToday() = runUnitTest {
        val auth = FakeAuthRepository(
            initialAuthState = sampleAnonymousGuest,
            linkOAuthOutcome = LinkIdentityOutcome.Success,
        )
        val vm = newVm(cache = FakeAppCache(initial = AppData(hasUserOnboarded = true)), auth = auth)
        val received = collectEvents(vm)
        runCurrent()

        vm.takeAction(OnboardingAction.SignInWithGoogleTapped)
        runCurrent()

        assertEquals(OAuthProvider.Google, auth.lastLinkOAuthProvider)
        assertEquals(0, auth.oauthSignInCalls)
        assertEquals(OnboardingEvent.NavigateToToday, received.single())
    }

    @Test
    fun guest_accountAlreadyInUse_saysSo_andOffersToSignInToIt() = runUnitTest {
        val cache = FakeAppCache(initial = AppData(hasUserOnboarded = true))
        val auth = FakeAuthRepository(
            initialAuthState = sampleAnonymousGuest,
            linkAppleOutcome = LinkIdentityOutcome.AlreadyOnAnotherAccount,
            appleSignInOutcome = SignInOutcome.Success,
        )
        val vm = newVm(
            cache = cache,
            auth = auth,
            classifier = StubAuthOutcomeClassifier(AuthOutcome.SignedIn),
            appleCoordinator = FakeAppleSignInCoordinator(credential = sampleAppleCredential),
        )
        val received = collectEvents(vm)
        runCurrent()

        vm.takeAction(OnboardingAction.SignInWithAppleTapped)
        runCurrent()

        assertEquals(0, auth.appleSignInCalls, "never switches accounts without asking")
        assertEquals(OnboardingAccountError.AccountInUse, vm.state.accountError)
        assertEquals(AccountMethod.Apple, vm.state.conflict)
        assertTrue(received.isEmpty())

        vm.takeAction(OnboardingAction.UseThatAccountTapped)
        runCurrent()

        assertEquals(1, auth.appleSignInCalls)
        assertEquals(OnboardingEvent.NavigateToToday, received.single())
    }

    // ---------- Answers ----------

    @Test
    fun optionTapped_whyQuestion_togglesEachAnswer() = runUnitTest {
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
    fun optionTapped_singleChoiceQuestions_replaceTheAnswer() = runUnitTest {
        val vm = atQuestion(SetupQuestion.Feeling)
        vm.takeAction(OnboardingAction.OptionTapped(NumbersFeel.LoveThem))
        vm.takeAction(OnboardingAction.OptionTapped(NumbersFeel.RatherNot))
        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.OptionTapped(StatementAnswer.Agree))
        vm.takeAction(OnboardingAction.OptionTapped(StatementAnswer.NotReally))
        runCurrent()

        assertEquals(NumbersFeel.RatherNot, vm.state.answers.feel)
        assertEquals(StatementAnswer.NotReally, vm.state.answers.statement)
        assertEquals(1, vm.state.options.count { it.selected })
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
    fun agreeingWithTheStatement_clearsAPickedAlternative_andTakesFullAndSprintAway() = runUnitTest {
        val vm = atQuestion(SetupQuestion.Feeling)
        vm.takeAction(OnboardingAction.OptionTapped(NumbersFeel.LoveThem))
        runCurrent()
        assertEquals(PresetId.Full, vm.state.recommended)

        // Walk to the recommendation, pick an alternative, then come back and
        // agree with the statement: the pick is cleared and Full is gone.
        repeat(2) { vm.takeAction(OnboardingAction.ContinueTapped) }
        vm.takeAction(OnboardingAction.AlternativeTapped(vm.state.alternatives.first()))
        runCurrent()
        assertNotNull(vm.state.picked)

        vm.takeAction(OnboardingAction.Back)
        vm.takeAction(OnboardingAction.OptionTapped(StatementAnswer.Agree))
        runCurrent()

        assertNull(vm.state.picked)
        assertEquals(PresetId.MacrosWithGrams, vm.state.recommended)
        assertFalse(PresetId.Full in vm.state.alternatives)
        assertFalse(PresetId.Full in vm.state.alternatives)
    }

    @Test
    fun talkToSomeone_opensSupport_whicheverAnswerIsPicked_andLeavesTheStepAlone() = runUnitTest {
        listOf(null, StatementAnswer.Agree, StatementAnswer.NotReally).forEach { answer ->
            val vm = atQuestion(SetupQuestion.Statement)
            val received = collectEvents(vm)
            answer?.let { vm.takeAction(OnboardingAction.OptionTapped(it)) }
            runCurrent()
            val before = vm.state

            vm.takeAction(OnboardingAction.TalkToSomeoneTapped)
            runCurrent()

            assertEquals(listOf<OnboardingEvent>(OnboardingEvent.NavigateToSupport), received, "answer $answer")
            assertEquals(before, vm.state, "answer $answer")
        }
    }

    // ---------- Continue, Skip, Back ----------

    @Test
    fun continueAndSkip_walkTheQuestionsAndTheStatement_toTheRecommendation_loggingEachStep() = runUnitTest {
        KLog.plant(events)
        val vm = atQuestion(SetupQuestion.Reason)

        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(OnboardingStep.Question(SetupQuestion.Feeling), vm.state.step)
        vm.takeAction(OnboardingAction.SkipQuestionTapped)
        runCurrent()
        assertEquals(OnboardingStep.Question(SetupQuestion.Statement), vm.state.step)
        assertEquals(3, vm.state.progress)
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()

        assertEquals(OnboardingStep.Recommendation, vm.state.step)
        assertEquals(
            listOf("question_1", "question_2", "statement", "recommendation"),
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
    fun back_walksFromTheRecommendation_toQuestionOne_andNoFurther() = runUnitTest {
        val vm = atRecommendation()

        val seen = mutableListOf<OnboardingStep>()
        repeat(4) {
            vm.takeAction(OnboardingAction.Back)
            runCurrent()
            seen += vm.state.step
        }

        assertEquals(
            listOf<OnboardingStep>(
                OnboardingStep.Question(SetupQuestion.Statement),
                OnboardingStep.Question(SetupQuestion.Feeling),
                OnboardingStep.Question(SetupQuestion.Reason),
                OnboardingStep.Question(SetupQuestion.Reason),
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
    fun back_isBlocked_onceTheModeIsSaved() = runUnitTest {
        val vm = atRecommendation()
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        assertTrue(vm.state.isFinishing)

        vm.takeAction(OnboardingAction.Back)
        runCurrent()

        assertEquals(OnboardingStep.Recommendation, vm.state.step)
    }

    // ---------- Recommendation ----------

    @Test
    fun noAnswers_recommendFullNumbers_withThreeGentlerAlternatives() = runUnitTest {
        val vm = atRecommendation()

        assertEquals(PresetId.Full, vm.state.chosen)
        assertEquals(listOf(PresetId.NoNumbers, PresetId.MacrosOnly, PresetId.MacrosWithGrams), vm.state.alternatives)
        assertEquals(ModePreview.of(Presets.Full), vm.state.preview)
    }

    @Test
    fun theDesignsAnswers_recommendMacrosOnly_andPreviewIt() = runUnitTest {
        val vm = atRecommendation(answers = designAnswers)

        assertEquals(PresetId.MacrosOnly, vm.state.chosen)
        assertEquals(listOf(PresetId.NoNumbers, PresetId.MacrosWithGrams), vm.state.alternatives)
        assertEquals(ModePreview.of(Presets.MacrosOnly), vm.state.preview)
    }

    @Test
    fun alternativeTapped_picksIt_andTheRecommendationBecomesAnAlternative() = runUnitTest {
        val vm = atRecommendation(answers = designAnswers)

        vm.takeAction(OnboardingAction.AlternativeTapped(PresetId.MacrosWithGrams))
        runCurrent()

        assertEquals(PresetId.MacrosWithGrams, vm.state.chosen)
        assertEquals(PresetId.MacrosOnly, vm.state.recommended)
        assertFalse(PresetId.MacrosWithGrams in vm.state.alternatives)
        assertEquals(PresetId.MacrosOnly, vm.state.alternatives.first())
        assertEquals(ModePreview.of(Presets.MacrosWithGrams), vm.state.preview)
    }

    @Test
    fun alternativeTapped_notOnOffer_isIgnored() = runUnitTest {
        val vm = atRecommendation(answers = listOf(StatementAnswer.Agree))

        vm.takeAction(OnboardingAction.AlternativeTapped(PresetId.Full))
        runCurrent()

        assertNull(vm.state.picked)
        assertEquals(PresetId.NoNumbers, vm.state.chosen)
    }

    @Test
    fun allModes_listsEveryModeOnOffer() = runUnitTest {
        val vm = atRecommendation(answers = designAnswers)

        vm.takeAction(OnboardingAction.AllModesTapped)
        runCurrent()

        assertTrue(vm.state.showAllModes)
        assertEquals(
            Presets.all.map { it.id } - setOf(PresetId.MacrosOnly, PresetId.Full),
            vm.state.alternatives,
        )

        vm.takeAction(OnboardingAction.AlternativeTapped(PresetId.MacrosWithGrams))
        runCurrent()
        assertEquals(PresetId.MacrosWithGrams, vm.state.chosen)
        assertTrue(PresetId.MacrosOnly in vm.state.alternatives, "the list stays whole after a pick")
    }

    // ---------- Saving the mode ----------

    @Test
    fun start_writesPresetAndAnswers_unstamped_thenOnboarded_thenToday() = runUnitTest {
        KLog.plant(events)
        val order = mutableListOf<String>()
        val settings = FakeSettingsRepository(initial = Settings().withBirthDate(ADULT), onUpdate = { order += "settings" })
        val cache = FakeAppCache(onSet = { if (it.hasUserOnboarded) order += "onboarded" })
        val vm = atRecommendation(
            answers = listOf(Why.EatingRegularly, NumbersFeel.RatherNot),
            settings = settings,
            cache = cache,
        )
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        assertEquals(listOf("settings", "onboarded"), order)
        assertEquals(OnboardingEvent.NavigateToToday, received.single())
        assertTrue(vm.state.isFinishing, "every button stays off while the screen leaves")
        assertEquals(listOf(false), settings.stampFlags, "onboarding must not stamp changedAt")
        val saved = settings.settings.value
        assertEquals(NumbersSettings(preset = PresetId.NoNumbers), saved.numbers)
        assertNull(saved.numbers.changedAt)
        assertEquals(
            OnboardingAnswers(why = listOf("eating_regularly"), feel = "rather_not"),
            saved.onboarding,
        )
    }

    @Test
    fun start_withAPickedAlternative_savesThePick() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atRecommendation(settings = settings)
        vm.takeAction(OnboardingAction.AlternativeTapped(PresetId.NoNumbers))
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        assertEquals(PresetId.NoNumbers, settings.settings.value.numbers.preset)
    }

    @Test
    fun start_twice_savesOnce() = runUnitTest {
        KLog.plant(events)
        val settings = FakeSettingsRepository(initial = Settings().withBirthDate(ADULT))
        val vm = atRecommendation(settings = settings)
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.StartTapped)
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        assertEquals(1, settings.stampFlags.size)
        assertEquals(1, received.size)
        assertEquals(1, events.all("onboarding.completed").size)
    }

    @Test
    fun completedEvent_saysWhetherTheRecommendationWasTaken() = runUnitTest {
        KLog.plant(events)
        val taken = atRecommendation()
        taken.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        val other = atRecommendation()
        other.takeAction(OnboardingAction.AlternativeTapped(PresetId.NoNumbers))
        other.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        val completed = events.all("onboarding.completed")
        assertEquals(listOf<Any?>(true, false), completed.map { it["took_recommendation"] })
        assertEquals(listOf<Any?>("full", "no_numbers"), completed.map { it["preset"] })
        assertTrue(completed.all { "duration_sec" in it })
    }

    // ---------- Telemetry ----------

    @Test
    fun noEventAttribute_everCarriesAnAnswer() = runUnitTest {
        KLog.plant(events)
        val vm = atRecommendation(
            answers = listOf(
                Why.MedicalNeed,
                Why.ChangingWeight,
                NumbersFeel.RatherNot,
                StatementAnswer.Agree,
            ),
        )
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        val answerWords = (Why.entries + NumbersFeel.entries + StatementAnswer.entries)
            .flatMap { listOf(it.key, (it as Enum<*>).name) }
            .map { it.lowercase() }
        val values = events.entries
            .filter { it.context.extras[EXTRA_APP_EVENT].toString().startsWith("onboarding.") }
            .flatMap { entry -> entry.context.extras.values.map { it.toString().lowercase() } }
        assertTrue(values.isNotEmpty(), "the scan saw no onboarding events, so it proves nothing")
        val leaked = values.filter { value -> answerWords.any { it == value } }
        assertTrue(leaked.isEmpty(), "answers reached telemetry: $leaked")
        assertFalse(
            events.entries.any { entry ->
                entry.context.extras.keys.any { it in setOf("why", "feel", "notice", "hard", "statement") }
            },
            "an event carried an answer key",
        )
    }

    // ---------- Test scaffolding ----------

    private val designAnswers: List<SetupOption> =
        listOf(Why.EatingEnough, Why.EatingRegularly, NumbersFeel.SomeAreFine, StatementAnswer.Agree)

    private fun newVm(
        cache: FakeAppCache = FakeAppCache(),
        auth: FakeAuthRepository = FakeAuthRepository(),
        classifier: StubAuthOutcomeClassifier = StubAuthOutcomeClassifier(),
        appleCoordinator: FakeAppleSignInCoordinator = FakeAppleSignInCoordinator(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        appleSignIn: Boolean = true,
        weights: FakeWeightRepository = FakeWeightRepository(),
        setupSteps: Boolean = false,
        route: OnboardingRoute = OnboardingRoute(),
    ): OnboardingViewModel = OnboardingViewModel(
        appCache = cache,
        authRepository = auth,
        authOutcomeClassifier = classifier,
        appleSignInCoordinator = appleCoordinator,
        settingsRepository = settings,
        weightRepository = weights,
        calendar = FixedCalendar,
        setupEnabled = onboardingSetupFlag(setupSteps),
        health = FakeHealthRepository(),
        healthFlag = healthFlag(false),
        region = { "GB" },
        route = route,
        appleSignIn = appleSignIn,
    )

    private fun TestScope.collectEvents(vm: OnboardingViewModel): MutableList<OnboardingEvent> {
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }
        runCurrent()
        return received
    }

    /** A phone holding an adult's birthday from the age question, as one that just signed up does. */
    private fun heldBirthday() = FakeAppCache(initial = AppData(pendingBirthDate = ADULT.toString()))

    /** A new install that tapped Get started. */
    private fun TestScope.atSignIn(
        cache: FakeAppCache = FakeAppCache(),
        auth: FakeAuthRepository = FakeAuthRepository(),
        classifier: StubAuthOutcomeClassifier = StubAuthOutcomeClassifier(),
        appleCoordinator: FakeAppleSignInCoordinator = FakeAppleSignInCoordinator(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): OnboardingViewModel {
        val vm = newVm(cache = cache, auth = auth, classifier = classifier, appleCoordinator = appleCoordinator, settings = settings)
        vm.takeAction(OnboardingAction.GetStartedTapped)
        vm.takeAction(OnboardingAction.AgeTyped(DateParts.of(ADULT)))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        check(vm.state.step == OnboardingStep.SignIn) { "expected the sign-in step, was ${vm.state.step}" }
        return vm
    }

    /** A new account, just signed in, walked to [question]. */
    private fun TestScope.atQuestion(
        question: SetupQuestion,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): OnboardingViewModel {
        val vm = newVm(settings = settings, cache = heldBirthday(), auth = FakeAuthRepository(initialAuthState = sampleAuthenticated))
        repeat(question.number - 1) { vm.takeAction(OnboardingAction.ContinueTapped) }
        runCurrent()
        return vm
    }

    /** A new account at the recommendation after tapping [answers] on their steps. */
    private fun TestScope.atRecommendation(
        answers: List<SetupOption> = emptyList(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        cache: FakeAppCache = heldBirthday(),
    ): OnboardingViewModel {
        val vm = newVm(
            settings = settings,
            cache = cache,
            auth = FakeAuthRepository(initialAuthState = sampleAuthenticated),
        )
        SetupQuestion.entries.forEach { question ->
            answers.filter { it in question.options }.forEach { vm.takeAction(OnboardingAction.OptionTapped(it)) }
            vm.takeAction(OnboardingAction.ContinueTapped)
        }
        runCurrent()
        check(vm.state.step == OnboardingStep.Recommendation) { "expected the recommendation, was ${vm.state.step}" }
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
