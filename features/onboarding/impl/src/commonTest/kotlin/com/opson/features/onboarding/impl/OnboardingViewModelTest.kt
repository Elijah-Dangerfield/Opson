package com.opson.features.onboarding.impl

import com.opson.libraries.core.logging.EXTRA_APP_EVENT
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.identity.auth.LinkIdentityOutcome
import com.opson.libraries.identity.auth.OAuthProvider
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.opson.AppData
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

/**
 * Covers onboarding as design v3 draws it, with **deferred account creation**:
 *
 *  - Entry: an onboarded user bounces to Today; a claimed account that has not
 *    set up lands on question 1; a stranded guest is adopted.
 *  - Get started enters the questions without creating an account.
 *  - Answers: why toggles, everything else replaces; each answer recomputes
 *    the recommendation and the alternatives.
 *  - Continue and Skip walk to the recommendation; Back walks back, gated.
 *  - Start saves the preset and the answers without stamping `changedAt`,
 *    creates the guest, marks onboarded and opens the account step. Skip all
 *    saves Full numbers and goes straight to Today.
 *  - The account step links Google or Apple onto the guest, hands email to
 *    sign-up, or keeps it on this phone.
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
        val received = collectEvents(vm)
        runCurrent()

        assertEquals(OnboardingEvent.NavigateToToday, received.firstOrNull())
    }

    @Test
    fun init_authenticatedNotOnboarded_opensAtQuestionOne_claimed() = runUnitTest {
        // A just-confirmed email signup routed back from VerifyEmail: a real
        // (non-anonymous) account that hasn't finished onboarding opens at the
        // first question with the back-to-Welcome control suppressed.
        val auth = FakeAuthRepository(initialAuthState = sampleAuthenticated)
        val vm = newVm(auth = auth)
        runCurrent()

        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)
        assertTrue(vm.state.identityClaimed)
        assertFalse(vm.state.canGoBack)
    }

    @Test
    fun init_returningAnonymousGuest_skipsOnboarding_navigatesToToday() = runUnitTest {
        // A pre-existing anonymous guest session reaching onboarding is a
        // RETURNING guest whose flag was lost, not a new user.
        val cache = FakeAppCache()
        val auth = FakeAuthRepository(initialAuthState = sampleAnonymousGuest)
        val settings = FakeSettingsRepository()
        val vm = newVm(cache = cache, auth = auth, settings = settings)
        val received = collectEvents(vm)
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

    // ---------- Welcome ----------

    @Test
    fun getStarted_entersQuestionOne_withoutCreatingAnAccount() = runUnitTest {
        KLog.plant(events)
        val creator = FakeGuestAccountCreator()
        val vm = newVm(creator = creator)

        vm.takeAction(OnboardingAction.GetStartedTapped)
        runCurrent()

        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)
        assertEquals(1, vm.state.progress)
        assertEquals(0, creator.startCalls, "no account until the mode is saved")
        assertTrue(vm.state.canGoBack, "a guest can still go back to Welcome")
        assertEquals("guest", events.single("onboarding.auth_selected")["method"])
    }

    @Test
    fun signIn_emitsNavigateToSignIn_andStaysOnWelcome() = runUnitTest {
        val vm = newVm()
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.SignInTapped)
        runCurrent()

        assertEquals(OnboardingEvent.NavigateToSignIn, received.firstOrNull())
        assertEquals(OnboardingStep.Welcome, vm.state.step)
    }

    @Test
    fun signIn_onAPhoneWithNoWayToSignIn_goesNowhere() = runUnitTest {
        // Android in 0.1: no Apple, and Google and email are off. Sign-in would open a page
        // with nothing on it, so Welcome hides the link and the action is refused.
        val vm = newVm(appleSignIn = false)
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.SignInTapped)
        runCurrent()

        assertFalse(vm.state.canSignIn)
        assertTrue(received.isEmpty())
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
            listOf("welcome", "question_1", "question_2", "statement", "recommendation"),
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
    fun continueTapped_offTheQuestions_isIgnored() = runUnitTest {
        val vm = newVm()
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()

        assertEquals(OnboardingStep.Welcome, vm.state.step)
    }

    @Test
    fun back_walksFromTheRecommendation_toWelcome() = runUnitTest {
        val vm = atRecommendation()

        val seen = mutableListOf<OnboardingStep>()
        repeat(4) {
            vm.takeAction(OnboardingAction.Back)
            runCurrent()
            seen += vm.state.step
        }

        assertEquals(
            listOf(
                OnboardingStep.Question(SetupQuestion.Statement),
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
    fun back_isBlocked_whileTheModeIsSaving() = runUnitTest {
        val vm = atRecommendation(creator = FakeGuestAccountCreator(hangInProgress = true))
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        assertTrue(vm.state.isFinishing)

        vm.takeAction(OnboardingAction.Back)
        runCurrent()

        assertEquals(OnboardingStep.Recommendation, vm.state.step)
    }

    @Test
    fun back_onQuestionOne_isBlocked_forAClaimedIdentity() = runUnitTest {
        val vm = newVm(auth = FakeAuthRepository(initialAuthState = sampleAuthenticated))
        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.Back)
        runCurrent()
        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)

        vm.takeAction(OnboardingAction.Back)
        runCurrent()

        assertEquals(OnboardingStep.Question(SetupQuestion.Reason), vm.state.step)
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
    fun start_writesPresetAndAnswers_unstamped_thenCreatesGuest_thenOnboarded_thenTheAccountStep() = runUnitTest {
        KLog.plant(events)
        val order = mutableListOf<String>()
        val settings = FakeSettingsRepository(onUpdate = { order += "settings" })
        val creator = FakeGuestAccountCreator(onStart = { order += "guest" })
        val cache = FakeAppCache(onSet = { if (it.hasUserOnboarded) order += "onboarded" })
        val vm = atRecommendation(
            answers = listOf(Why.EatingRegularly, NumbersFeel.RatherNot),
            settings = settings,
            creator = creator,
            cache = cache,
        )
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        assertEquals(listOf("settings", "guest", "onboarded"), order)
        assertEquals(OnboardingStep.Account, vm.state.step)
        assertFalse(vm.state.isFinishing)
        assertTrue(received.isEmpty(), "the account step comes before Today")
        assertEquals("account", events.steps().last())
        assertEquals(listOf(false), settings.stampFlags, "onboarding must not stamp changedAt")
        val saved = settings.settings.value
        assertEquals(NumbersSettings(preset = PresetId.NoNumbers), saved.numbers)
        assertNull(saved.numbers.changedAt)
        assertEquals(
            OnboardingAnswers(why = listOf("eating_regularly"), feel = "rather_not"),
            saved.onboarding,
        )
        assertNotNull(creator.lastIdentity?.displayName, "the guest gets a generated name, never asked for")
        assertFalse(vm.state.creationFailed)
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
        val settings = FakeSettingsRepository()
        val creator = FakeGuestAccountCreator()
        val vm = atRecommendation(settings = settings, creator = creator)

        vm.takeAction(OnboardingAction.StartTapped)
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        assertEquals(1, creator.startCalls)
        assertEquals(1, settings.stampFlags.size)
        assertEquals(OnboardingStep.Account, vm.state.step)
    }

    @Test
    fun backFromTheAccountStep_andStartAgain_savesTheNewMode_withoutASecondGuestOrCompletion() = runUnitTest {
        KLog.plant(events)
        val settings = FakeSettingsRepository()
        val creator = FakeGuestAccountCreator()
        val vm = atAccount(answers = designAnswers, settings = settings, creator = creator)

        vm.takeAction(OnboardingAction.Back)
        runCurrent()
        assertEquals(OnboardingStep.Recommendation, vm.state.step)
        vm.takeAction(OnboardingAction.AlternativeTapped(PresetId.NoNumbers))
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        assertEquals(OnboardingStep.Account, vm.state.step)
        assertEquals(PresetId.NoNumbers, settings.settings.value.numbers.preset)
        assertEquals(listOf(false, false), settings.stampFlags)
        assertEquals(1, creator.startCalls)
        assertEquals(1, events.all("onboarding.completed").size)
    }

    @Test
    fun skipAll_savesFullNumbersWithEmptyAnswers_createsTheGuest_andGoesStraightToToday() = runUnitTest {
        KLog.plant(events)
        val settings = FakeSettingsRepository()
        val creator = FakeGuestAccountCreator()
        val cache = FakeAppCache()
        val vm = newVm(settings = settings, creator = creator, cache = cache)
        val received = collectEvents(vm)
        runCurrent()

        vm.takeAction(OnboardingAction.SkipAllTapped)
        runCurrent()

        assertEquals(NumbersSettings(preset = PresetId.Full), settings.settings.value.numbers)
        assertEquals(OnboardingAnswers(), settings.settings.value.onboarding)
        assertEquals(listOf(false), settings.stampFlags)
        assertEquals(1, creator.startCalls)
        assertTrue(cache.get().hasUserOnboarded)
        assertEquals(OnboardingEvent.NavigateToToday, received.single())
        assertEquals(OnboardingStep.Welcome, vm.state.step, "skip all passes the account step too")
        val completed = events.single("onboarding.completed")
        assertEquals("full", completed["preset"])
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
    fun start_onAPhoneWithNoWayToClaim_goesToToday_pastTheAccountStep() = runUnitTest {
        // The account step would offer only "Keep it on this phone" under a promise to back up.
        val creator = FakeGuestAccountCreator()
        val cache = FakeAppCache()
        val vm = newVm(creator = creator, cache = cache, appleSignIn = false)
        val received = collectEvents(vm)
        vm.takeAction(OnboardingAction.GetStartedTapped)
        repeat(SetupQuestion.Count) { vm.takeAction(OnboardingAction.ContinueTapped) }
        runCurrent()

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        assertEquals(1, creator.startCalls, "the guest is still created, so logs sync")
        assertTrue(cache.get().hasUserOnboarded)
        assertEquals(OnboardingEvent.NavigateToToday, received.single())
        assertEquals(OnboardingStep.Recommendation, vm.state.step)
    }

    @Test
    fun start_guestCreationFails_goesToToday_flaggedDegraded_withNoAccountStep() = runUnitTest {
        val cache = FakeAppCache()
        val vm = atRecommendation(cache = cache, creator = FakeGuestAccountCreator(failCreation = true))
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        assertTrue(cache.get().hasUserOnboarded)
        assertTrue(vm.state.creationFailed)
        assertEquals(OnboardingEvent.NavigateToToday, received.firstOrNull())
        assertEquals(OnboardingStep.Recommendation, vm.state.step, "nothing to claim without a guest")
    }

    @Test
    fun start_guestCreationNeverTerminates_goesOnAfterTheTimeout() = runUnitTest {
        // Onboarding must NEVER trap the user on a backend call.
        val cache = FakeAppCache()
        val vm = atRecommendation(cache = cache, creator = FakeGuestAccountCreator(hangInProgress = true))
        val received = collectEvents(vm)

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
    fun start_withAClaimedAccount_savesSettings_createsNoGuest_andSkipsTheAccountStep() = runUnitTest {
        val cache = FakeAppCache()
        val settings = FakeSettingsRepository()
        val creator = FakeGuestAccountCreator()
        val auth = FakeAuthRepository(initialAuthState = sampleAuthenticated)
        val vm = newVm(cache = cache, auth = auth, settings = settings, creator = creator)
        val received = collectEvents(vm)
        runCurrent()
        repeat(SetupQuestion.Count) { vm.takeAction(OnboardingAction.SkipQuestionTapped) }

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        assertEquals(0, creator.startCalls, "a claimed account must not create a guest")
        assertEquals(PresetId.Full, settings.settings.value.numbers.preset)
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
        other.takeAction(OnboardingAction.AlternativeTapped(PresetId.NoNumbers))
        other.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        val completed = events.all("onboarding.completed")
        assertEquals(listOf<Any?>(true, false), completed.map { it["took_recommendation"] })
        assertEquals(listOf<Any?>("full", "no_numbers"), completed.map { it["preset"] })
        assertEquals(listOf<Any?>(false, false), completed.map { it["skipped_all"] })
        assertTrue(completed.all { "duration_sec" in it && "account_ready" in it })
    }

    // ---------- Account step ----------

    @Test
    fun keepOnPhone_goesToToday_withoutLinkingAnything() = runUnitTest {
        KLog.plant(events)
        val auth = FakeAuthRepository()
        val vm = atAccount(auth = auth)
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.KeepOnPhoneTapped)
        runCurrent()

        assertEquals(OnboardingEvent.NavigateToToday, received.single())
        assertEquals(0, auth.linkOAuthCalls + auth.linkAppleCalls)
        assertEquals("phone", events.single("onboarding.account_choice")["choice"])
    }

    @Test
    fun saveWithGoogle_linksGoogleOntoTheGuest_thenToday() = runUnitTest {
        KLog.plant(events)
        val auth = FakeAuthRepository(linkOAuthOutcome = LinkIdentityOutcome.Success)
        val vm = atAccount(auth = auth)
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.SaveWithGoogleTapped)
        runCurrent()

        assertEquals(OAuthProvider.Google, auth.lastLinkOAuthProvider)
        assertEquals(0, auth.oauthSignInCalls, "claiming links, it never signs into another account")
        assertEquals(OnboardingEvent.NavigateToToday, received.single())
        assertNull(vm.state.accountInFlight)
        assertEquals("google", events.single("onboarding.account_choice")["choice"])
    }

    @Test
    fun saveWithGoogle_cancelled_staysQuietly() = runUnitTest {
        val vm = atAccount(auth = FakeAuthRepository(linkOAuthOutcome = LinkIdentityOutcome.Cancelled))
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.SaveWithGoogleTapped)
        runCurrent()

        assertTrue(received.isEmpty())
        assertEquals(OnboardingStep.Account, vm.state.step)
        assertNull(vm.state.accountError)
        assertNull(vm.state.accountInFlight)
    }

    @Test
    fun saveWithGoogle_failures_showTheirError_andStay() = runUnitTest {
        mapOf(
            LinkIdentityOutcome.AlreadyOnAnotherAccount to OnboardingAccountError.AccountInUse,
            LinkIdentityOutcome.ProviderNotEnabled to OnboardingAccountError.ProviderNotEnabled,
            LinkIdentityOutcome.NetworkError(RuntimeException("offline")) to OnboardingAccountError.Offline,
            LinkIdentityOutcome.NotSignedIn to OnboardingAccountError.Failed,
        ).forEach { (outcome, error) ->
            val vm = atAccount(auth = FakeAuthRepository(linkOAuthOutcome = outcome))

            vm.takeAction(OnboardingAction.SaveWithGoogleTapped)
            runCurrent()

            assertEquals(error, vm.state.accountError, "for $outcome")
            assertEquals(OnboardingStep.Account, vm.state.step)
            assertTrue(vm.state.canClaim, "the buttons work again after $outcome")
        }
    }

    @Test
    fun saveWithApple_linksAppleOntoTheGuest_thenToday() = runUnitTest {
        KLog.plant(events)
        val auth = FakeAuthRepository(linkAppleOutcome = LinkIdentityOutcome.Success)
        val vm = atAccount(auth = auth, appleCoordinator = FakeAppleSignInCoordinator(credential = sampleAppleCredential))
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.SaveWithAppleTapped)
        runCurrent()

        assertEquals(1, auth.linkAppleCalls)
        assertEquals(0, auth.appleSignInCalls)
        assertEquals(OnboardingEvent.NavigateToToday, received.single())
        assertEquals("apple", events.single("onboarding.account_choice")["choice"])
    }

    @Test
    fun saveWithApple_onAnotherAccount_saysSo_andNeverSwitchesSessions() = runUnitTest {
        // Signing in instead would switch users and wipe the settings onboarding just saved.
        val auth = FakeAuthRepository(
            linkAppleOutcome = LinkIdentityOutcome.AlreadyOnAnotherAccount,
            appleSignInOutcome = com.opson.libraries.identity.auth.SignInOutcome.Success,
        )
        val vm = atAccount(auth = auth, appleCoordinator = FakeAppleSignInCoordinator(credential = sampleAppleCredential))
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.SaveWithAppleTapped)
        runCurrent()

        assertEquals(0, auth.appleSignInCalls)
        assertEquals(OnboardingAccountError.AccountInUse, vm.state.accountError)
        assertTrue(received.isEmpty())
    }

    @Test
    fun saveWithApple_sheetDismissed_isQuiet() = runUnitTest {
        val auth = FakeAuthRepository()
        val vm = atAccount(auth = auth, appleCoordinator = FakeAppleSignInCoordinator(credential = null))

        vm.takeAction(OnboardingAction.SaveWithAppleTapped)
        runCurrent()

        assertEquals(0, auth.linkAppleCalls)
        assertNull(vm.state.accountInFlight)
        assertNull(vm.state.accountError)
        assertEquals(OnboardingStep.Account, vm.state.step)
    }

    @Test
    fun saveWithApple_nativeFailure_showsFailed() = runUnitTest {
        val vm = atAccount(appleCoordinator = FakeAppleSignInCoordinator(errorMessage = "ASAuthorization error"))

        vm.takeAction(OnboardingAction.SaveWithAppleTapped)
        runCurrent()

        assertEquals(OnboardingAccountError.Failed, vm.state.accountError)
        assertNull(vm.state.accountInFlight)
    }

    @Test
    fun saveWithEmail_handsOffToSignUp() = runUnitTest {
        KLog.plant(events)
        val vm = atAccount()
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.SaveWithEmailTapped)
        runCurrent()

        assertEquals(OnboardingEvent.NavigateToSignUp, received.single())
        assertEquals("email", events.single("onboarding.account_choice")["choice"])
    }

    @Test
    fun accountActions_offTheAccountStep_doNothing() = runUnitTest {
        val auth = FakeAuthRepository(linkOAuthOutcome = LinkIdentityOutcome.Success)
        val vm = atRecommendation()
        val received = collectEvents(vm)

        vm.takeAction(OnboardingAction.KeepOnPhoneTapped)
        vm.takeAction(OnboardingAction.SaveWithGoogleTapped)
        vm.takeAction(OnboardingAction.SaveWithEmailTapped)
        runCurrent()

        assertTrue(received.isEmpty())
        assertEquals(0, auth.linkOAuthCalls)
    }

    // ---------- Telemetry ----------

    @Test
    fun noEventAttribute_everCarriesAnAnswer() = runUnitTest {
        KLog.plant(events)
        val vm = atAccount(
            answers = listOf(
                Why.MedicalNeed,
                Why.ChangingWeight,
                NumbersFeel.RatherNot,
                StatementAnswer.Agree,
            ),
        )
        vm.takeAction(OnboardingAction.KeepOnPhoneTapped)
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
        creator: FakeGuestAccountCreator = FakeGuestAccountCreator(),
        appleCoordinator: FakeAppleSignInCoordinator = FakeAppleSignInCoordinator(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        appleSignIn: Boolean = true,
        weights: FakeWeightRepository = FakeWeightRepository(),
        setupSteps: Boolean = false,
        goalsAndEnergy: Boolean = false,
    ): OnboardingViewModel = OnboardingViewModel(
        appCache = cache,
        authRepository = auth,
        guestAccountCreator = creator,
        appleSignInCoordinator = appleCoordinator,
        settingsRepository = settings,
        weightRepository = weights,
        calendar = FixedCalendar,
        setupEnabled = onboardingSetupFlag(setupSteps),
        goalsAndEnergy = goalsAndEnergyFlag(goalsAndEnergy),
        appleSignIn = appleSignIn,
    )

    private fun TestScope.collectEvents(vm: OnboardingViewModel): MutableList<OnboardingEvent> {
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }
        runCurrent()
        return received
    }


    /** A guest who tapped Get started and walked to [question]. */
    private fun TestScope.atQuestion(
        question: SetupQuestion,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): OnboardingViewModel {
        val vm = newVm(settings = settings)
        vm.takeAction(OnboardingAction.GetStartedTapped)
        repeat(question.number - 1) { vm.takeAction(OnboardingAction.ContinueTapped) }
        runCurrent()
        return vm
    }

    /** A guest at the recommendation after tapping [answers] on their steps. */
    private fun TestScope.atRecommendation(
        answers: List<SetupOption> = emptyList(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        creator: FakeGuestAccountCreator = FakeGuestAccountCreator(),
        cache: FakeAppCache = FakeAppCache(),
        auth: FakeAuthRepository = FakeAuthRepository(),
        appleCoordinator: FakeAppleSignInCoordinator = FakeAppleSignInCoordinator(),
    ): OnboardingViewModel {
        val vm = newVm(
            settings = settings,
            creator = creator,
            cache = cache,
            auth = auth,
            appleCoordinator = appleCoordinator,
        )
        vm.takeAction(OnboardingAction.GetStartedTapped)
        SetupQuestion.entries.forEach { question ->
            answers.filter { it in question.options }.forEach { vm.takeAction(OnboardingAction.OptionTapped(it)) }
            vm.takeAction(OnboardingAction.ContinueTapped)
        }
        runCurrent()
        return vm
    }

    /** A guest on the account step: answered, started, the guest created. */
    private fun TestScope.atAccount(
        answers: List<SetupOption> = emptyList(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        creator: FakeGuestAccountCreator = FakeGuestAccountCreator(),
        auth: FakeAuthRepository = FakeAuthRepository(),
        appleCoordinator: FakeAppleSignInCoordinator = FakeAppleSignInCoordinator(),
    ): OnboardingViewModel {
        val vm = atRecommendation(
            answers = answers,
            settings = settings,
            creator = creator,
            auth = auth,
            appleCoordinator = appleCoordinator,
        )
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        check(vm.state.step == OnboardingStep.Account) { "expected the account step, was ${vm.state.step}" }
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
