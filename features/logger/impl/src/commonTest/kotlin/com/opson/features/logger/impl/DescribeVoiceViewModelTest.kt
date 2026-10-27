package com.opson.features.logger.impl

import com.opson.features.logger.LoggerRoute
import com.opson.features.logger.LoggerStart
import com.opson.libraries.core.Catching
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.speech.Heard
import com.opson.libraries.speech.MicPermission
import com.opson.libraries.speech.SpeechAvailability
import com.opson.libraries.speech.SpeechFailure
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Voice log in [LoggerViewModel] with [FakeSpeechToText]: the permission
 * branches, partials into the field, the final transcript going through the
 * same Describe parse as typing, the silence and overdue timers, everything
 * that cancels a session, and telemetry that never carries words. The typed
 * Describe path itself is [LoggerViewModelTest]'s; the state mapping is
 * [VoiceStateBuilderTest]'s. The platform recognizers are not covered here;
 * they are device code behind [com.opson.libraries.speech.SpeechToText].
 */
class DescribeVoiceViewModelTest : CoroutineTest() {
    private val diary = FakeDiaryRepository(listOf(LoggerFixtures.day(), LoggerFixtures.dayBefore()))
    private val catalog = FakeFoodCatalog().apply { describeResult = Catching.success(LoggerFixtures.parsed) }
    private val loggerTelemetry = RecordingTelemetry()
    private val speech = FakeSpeechToText()
    private val telemetry = RecordingVoiceTelemetry()

    private fun vm(voiceOn: Boolean = true) = LoggerViewModel(
        route = LoggerRoute(slot = MealSlot.Breakfast, start = LoggerStart.Describe),
        diary = diary,
        customFoods = FakeCustomFoodRepository(),
        savedMeals = FakeSavedMealRepository(),
        foodCatalog = catalog,
        settingsRepository = FakeSettingsRepository(Settings(askTags = false)),
        policySource = FakePolicySource(),
        logger = FoodLogger(diary, catalog, FakeRecentLogEvents(), loggerTelemetry, Clock.fixed(NOON)),
        calendar = FakeCalendar(),
        selectedDay = FakeSelectedDay(),
        clock = Clock.fixed(NOON),
        appState = FakeAppState(),
        telemetry = loggerTelemetry,
        scanEnabled = scanFlag(false),
        photoEnabled = photoFlag(false),
        voice = DescribeVoice(speech, voiceFlag(voiceOn), telemetry),
        fastingRelease = fastingFlag(false),
    )

    private fun TestScope.events(vm: LoggerViewModel): List<LoggerEvent> {
        val events = mutableListOf<LoggerEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    private val LoggerViewModel.voice: VoiceUi? get() = state.describe.voice

    @Test
    fun withTheFlagOffThereIsNoMicAndATapDoesNothing() = runUnitTest {
        val vm = vm(voiceOn = false)
        val events = events(vm)
        settle()
        assertNull(vm.voice)

        vm.takeAction(VoiceAction.MicTapped)
        settle()

        assertEquals(0, speech.sessions)
        assertTrue(events.isEmpty())
        assertTrue(telemetry.events.isEmpty())
    }

    @Test
    fun aTapListensStraightAwayWhenTheMicIsAllowed() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()
        assertEquals(VoiceUi(), vm.voice)

        vm.takeAction(VoiceAction.MicTapped)

        assertTrue(speech.listening)
        assertEquals(VoicePhase.Listening, vm.voice?.phase)
        assertEquals(VoiceAnnouncement.Listening, vm.voice?.announcement)
        assertTrue(events.isEmpty(), "no prompt when already allowed")
        assertEquals(listOf("started on_device=true asked=false"), telemetry.events)
    }

    @Test
    fun partialsFillTheFieldAsTheyAreHeardWithoutParsing() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)

        speech.say(Heard.Partial("2 eggs"))
        speech.say(Heard.Partial("2 eggs, toast with"))

        assertEquals("2 eggs, toast with", vm.state.describe.text)
        assertEquals(DescribeStatus.Idle, vm.state.describe.status)
        assertTrue(catalog.described.isEmpty())
    }

    /** The goal: saying it gets the same Describe result as typing it. */
    @Test
    fun theFinalTranscriptIsReadExactlyAsTypedTextIs() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)
        speech.say(Heard.Partial("2 eggs, toast"))
        speech.say(Heard.Final(VoiceFixtures.SPOKEN))
        runCurrent()

        assertFalse(speech.listening)
        assertEquals(listOf(VoiceFixtures.SPOKEN), catalog.described)
        assertEquals(DescribeStatus.Parsed, vm.state.describe.status)
        val spoken = vm.state.describe.lines
        assertEquals(VoiceAnnouncement.Stopped, vm.voice?.announcement)
        assertEquals(VoicePhase.Ready, vm.voice?.phase)

        vm.takeAction(LoggerAction.BackTapped)
        vm.takeAction(LoggerAction.DescribeItTapped)
        vm.takeAction(LoggerAction.DescribeTyped(VoiceFixtures.SPOKEN))
        settle()

        assertEquals(listOf(VoiceFixtures.SPOKEN, VoiceFixtures.SPOKEN), catalog.described)
        assertEquals(spoken, vm.state.describe.lines)
        assertEquals(listOf("Egg, large", "Toast, white"), spoken.map { it.name })
    }

    @Test
    fun speakingAfterTypingAddsToWhatIsThere() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(LoggerAction.DescribeTyped("2 eggs"))
        vm.takeAction(VoiceAction.MicTapped)
        assertEquals(DescribeStatus.Idle, vm.state.describe.status, "the typed parse waits for the whole sentence")

        speech.say(Heard.Partial("and toast"))
        assertEquals("2 eggs and toast", vm.state.describe.text)
        speech.say(Heard.Final("and toast"))
        runCurrent()

        assertEquals(listOf("2 eggs and toast"), catalog.described)
    }

    @Test
    fun tappingWhileListeningStopsAndWaitsForTheLastWords() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)
        speech.say(Heard.Partial("2 eggs"))

        vm.takeAction(VoiceAction.MicTapped)

        assertEquals(1, speech.finished)
        assertEquals(VoicePhase.Transcribing, vm.voice?.phase)
        assertEquals(VoiceAnnouncement.Stopped, vm.voice?.announcement)

        vm.takeAction(VoiceAction.MicTapped)
        assertEquals(1, speech.finished, "a tap while settling does nothing")

        speech.say(Heard.Final("2 eggs and toast"))
        runCurrent()
        assertEquals(listOf("2 eggs and toast"), catalog.described)
    }

    @Test
    fun aPauseAfterSpeakingStopsListening() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)
        speech.say(Heard.Partial("2 eggs"))

        advanceTimeBy(LoggerViewModel.QUIET_AFTER_SPEECH.inWholeMilliseconds - 100)
        speech.say(Heard.Partial("2 eggs and toast"))
        advanceTimeBy(LoggerViewModel.QUIET_AFTER_SPEECH.inWholeMilliseconds - 100)
        assertEquals(0, speech.finished, "each new word restarts the wait")

        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, speech.finished)
        assertEquals(VoicePhase.Transcribing, vm.voice?.phase)
    }

    @Test
    fun sayingNothingStopsAndOffersToTypeOrTryAgain() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(LoggerAction.DescribeTyped("2 eggs"))
        vm.takeAction(VoiceAction.MicTapped)

        advanceTimeBy(LoggerViewModel.NOTHING_SAID.inWholeMilliseconds + 1)
        runCurrent()
        assertEquals(1, speech.finished)
        speech.say(Heard.Final(""))
        runCurrent()

        assertEquals(VoiceNotice.NoSpeech, vm.voice?.notice)
        assertEquals("2 eggs", vm.state.describe.text, "what was typed stays")
        assertTrue(catalog.described.isEmpty())
        assertEquals("fallback no_speech", telemetry.events.last())

        vm.takeAction(VoiceAction.MicTapped)
        assertTrue(speech.listening, "Try again listens again")
        assertNull(vm.voice?.notice)
    }

    @Test
    fun aRecognizerThatNeverSettlesKeepsWhatWasHeard() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)
        speech.say(Heard.Partial("2 eggs and toast"))
        vm.takeAction(VoiceAction.MicTapped)

        advanceTimeBy(LoggerViewModel.TRANSCRIBE_TIMEOUT.inWholeMilliseconds + 1)
        runCurrent()

        assertFalse(speech.listening, "the session is abandoned")
        assertEquals(listOf("2 eggs and toast"), catalog.described)
        assertEquals(VoicePhase.Ready, vm.voice?.phase)
    }

    @Test
    fun theLengthCapStopsListening() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)
        // Words keep coming, so the silence wait never runs out.
        repeat((LoggerViewModel.LISTEN_LIMIT.inWholeSeconds).toInt()) {
            speech.say(Heard.Partial("and more $it"))
            advanceTimeBy(1_000)
        }
        runCurrent()

        assertEquals(1, speech.finished)
        assertEquals(VoicePhase.Transcribing, vm.voice?.phase)
    }

    @Test
    fun notAskedYetShowsTheSystemPromptThenListens() = runUnitTest {
        speech.permission = MicPermission.NotAsked
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(VoiceAction.MicTapped)
        assertEquals(listOf<LoggerEvent>(RequestMicPermission), events)
        assertFalse(speech.listening)

        vm.takeAction(VoiceAction.ScreenPaused)
        assertEquals(listOf<LoggerEvent>(RequestMicPermission), events, "the prompt pausing the screen changes nothing")

        vm.takeAction(VoiceAction.MicPermissionAnswered(MicPermission.Granted))
        assertTrue(speech.listening)
        assertEquals(listOf("started on_device=true asked=true"), telemetry.events)
    }

    @Test
    fun refusedForGoodOffersSettingsAndTyping() = runUnitTest {
        speech.permission = MicPermission.Denied(canAskAgain = false)
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(VoiceAction.MicTapped)

        assertTrue(events.isEmpty())
        assertEquals(VoiceNotice.Denied(canAskAgain = false), vm.voice?.notice)
        assertEquals(listOf("permission_denied can_ask_again=false"), telemetry.events)

        vm.takeAction(VoiceAction.OpenSettingsTapped)
        assertEquals(1, speech.settingsOpened)
        assertNull(vm.voice?.notice)
    }

    @Test
    fun refusedOnceOnAndroidAsksAgainOnTheNextTap() = runUnitTest {
        speech.permission = MicPermission.Denied(canAskAgain = true)
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(VoiceAction.MicTapped)
        assertEquals(listOf<LoggerEvent>(RequestMicPermission), events)

        vm.takeAction(VoiceAction.MicPermissionAnswered(MicPermission.Denied(canAskAgain = true)))
        assertEquals(VoiceNotice.Denied(canAskAgain = true), vm.voice?.notice)
        assertEquals(listOf("permission_denied can_ask_again=true"), telemetry.events)
        assertEquals(0, speech.sessions)
    }

    @Test
    fun noRecognizerFallsBackToTypingWithoutAskingForTheMic() = runUnitTest {
        speech.availability = SpeechAvailability.Unavailable(SpeechFailure.NoRecognizer)
        speech.permission = MicPermission.NotAsked
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(VoiceAction.MicTapped)

        assertTrue(events.isEmpty())
        assertEquals(VoiceNotice.Unavailable, vm.voice?.notice)
        assertEquals(listOf("fallback no_recognizer"), telemetry.events)

        vm.takeAction(VoiceAction.TypeInsteadTapped)
        assertNull(vm.voice?.notice)
        assertTrue(vm.voice?.focusText == true)
    }

    @Test
    fun typingPutsANoticeAway() = runUnitTest {
        speech.availability = SpeechAvailability.Unavailable(SpeechFailure.NoModel)
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)
        assertEquals(VoiceNotice.Unavailable, vm.voice?.notice)
        assertEquals(listOf("fallback no_model"), telemetry.events)

        vm.takeAction(LoggerAction.DescribeTyped("2 eggs"))

        assertNull(vm.voice?.notice)
    }

    @Test
    fun aFailureWithNothingHeardSaysWhy() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)

        speech.say(Heard.Failed(SpeechFailure.NeedsConnection))
        runCurrent()

        assertEquals(VoiceNotice.NeedsConnection, vm.voice?.notice)
        assertEquals("fallback needs_connection", telemetry.events.last())
    }

    @Test
    fun aFailureAfterWordsKeepsTheWords() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)
        speech.say(Heard.Partial("2 eggs and toast"))

        speech.say(Heard.Failed(SpeechFailure.Unknown))
        runCurrent()

        assertNull(vm.voice?.notice)
        assertEquals(listOf("2 eggs and toast"), catalog.described)
    }

    @Test
    fun typingWhileListeningHandsOverToTheKeyboard() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)
        speech.say(Heard.Partial("2 eggs"))

        vm.takeAction(LoggerAction.DescribeTyped("2 eggs and juice"))

        assertFalse(speech.listening)
        assertEquals(VoiceAnnouncement.Stopped, vm.voice?.announcement)
        settle()
        assertEquals(listOf("2 eggs and juice"), catalog.described)

        vm.takeAction(LoggerAction.DescribeTyped("2 eggs and tea"))
        assertNull(vm.voice?.announcement, "the prompt comes back once typing carries on")
    }

    @Test
    fun goingBackWhileListeningDropsIt() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)
        speech.say(Heard.Partial("2 eggs"))

        vm.takeAction(LoggerAction.BackTapped)
        settle()

        assertFalse(speech.listening)
        assertEquals(LoggerPane.Actions, vm.state.pane)
        assertEquals("", vm.state.describe.text)
        assertTrue(catalog.described.isEmpty())
    }

    @Test
    fun leavingTheAppWhileListeningKeepsWhatWasHeard() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)
        speech.say(Heard.Partial("2 eggs and toast"))

        vm.takeAction(VoiceAction.ScreenPaused)
        runCurrent()

        assertFalse(speech.listening)
        assertEquals(listOf("2 eggs and toast"), catalog.described)
    }

    @Test
    fun aLateAnswerFromAnEndedSessionIsIgnored() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.SpeechArrived(Heard.Final("2 eggs")))
        runCurrent()

        assertEquals("", vm.state.describe.text)
        assertTrue(catalog.described.isEmpty())
    }

    @Test
    fun noMicrophoneOnThePhoneFallsBackBeforeAnyPrompt() = runUnitTest {
        speech.availability = SpeechAvailability.Unavailable(SpeechFailure.NoMicrophone)
        speech.permission = MicPermission.NotAsked
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(VoiceAction.MicTapped)

        assertTrue(events.isEmpty())
        assertEquals(VoiceNotice.Unavailable, vm.voice?.notice)
        assertEquals(listOf("fallback no_microphone"), telemetry.events)
    }

    @Test
    fun aSessionThatFindsNoMicrophoneSaysVoiceIsUnavailable() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)

        speech.say(Heard.Failed(SpeechFailure.NoMicrophone))
        runCurrent()

        assertEquals(VoiceNotice.Unavailable, vm.voice?.notice)
        assertEquals("fallback no_microphone", telemetry.events.last())
    }

    @Test
    fun permissionTurnedOffBeforeListeningIsTheDeniedNotice() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)

        speech.say(Heard.Failed(SpeechFailure.NotAllowed))
        runCurrent()

        assertEquals(VoiceNotice.Denied(canAskAgain = false), vm.voice?.notice)
        assertEquals("permission_denied can_ask_again=false", telemetry.events.last())
    }

    @Test
    fun aSecondSessionRefusedAsBusyOffersTryAgain() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)

        speech.say(Heard.Failed(SpeechFailure.Busy))
        runCurrent()

        assertEquals(VoiceNotice.Failed, vm.voice?.notice)
        assertEquals("fallback busy", telemetry.events.last())
    }

    @Test
    fun aPlatformExceptionStillEndsInANotice() = runUnitTest {
        speech.failure = IllegalStateException("audio server")
        val vm = vm()
        settle()

        vm.takeAction(VoiceAction.MicTapped)
        runCurrent()

        assertEquals(VoiceNotice.Failed, vm.voice?.notice)
        assertEquals("fallback error", telemetry.events.last())
    }

    /** The first tap after granting both permissions listens, whatever happened while the prompts were up. */
    @Test
    fun aYesStartsListeningEvenIfTheCaptureMovedOn() = runUnitTest {
        speech.permission = MicPermission.NotAsked
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)
        vm.takeAction(VoiceAction.TypeInsteadTapped)

        vm.takeAction(VoiceAction.MicPermissionAnswered(MicPermission.Granted))

        assertTrue(speech.listening)
        assertEquals(VoicePhase.Listening, vm.voice?.phase)
    }

    @Test
    fun aFieldEchoingItsOwnValueNeitherStopsListeningNorClearsANotice() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)
        speech.say(Heard.Partial("2 eggs"))

        vm.takeAction(LoggerAction.DescribeTyped("2 eggs"))
        assertTrue(speech.listening)
        assertEquals(VoicePhase.Listening, vm.voice?.phase)

        speech.say(Heard.Failed(SpeechFailure.NeedsConnection))
        speech.availability = SpeechAvailability.Unavailable(SpeechFailure.NoRecognizer)
        vm.takeAction(VoiceAction.MicTapped)
        assertEquals(VoiceNotice.Unavailable, vm.voice?.notice)
        vm.takeAction(LoggerAction.DescribeTyped(vm.state.describe.text))
        assertEquals(VoiceNotice.Unavailable, vm.voice?.notice)
    }

    /**
     * The iOS report: after the yes, the pane looked idle and nothing came. The state was Listening,
     * but the platform call that followed held the main thread, so it was never drawn. Listening is
     * now state before any platform answer, and a platform that never answers still ends in a notice.
     */
    @Test
    fun aYesShowsListeningAtOnceAndAPlatformThatNeverAnswersEndsInANotice() = runUnitTest {
        speech.permission = MicPermission.NotAsked
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)

        vm.takeAction(VoiceAction.MicPermissionAnswered(MicPermission.Granted))
        assertEquals(VoicePhase.Listening, vm.voice?.phase, "before the recognizer has said anything")

        advanceTimeBy(LoggerViewModel.NOTHING_SAID.inWholeMilliseconds + 1)
        runCurrent()
        assertEquals(VoicePhase.Transcribing, vm.voice?.phase)
        advanceTimeBy(LoggerViewModel.TRANSCRIBE_TIMEOUT.inWholeMilliseconds + 1)
        runCurrent()

        assertEquals(VoiceNotice.NoSpeech, vm.voice?.notice)
        assertFalse(speech.listening, "the session is abandoned")
    }

    @Test
    fun anAnswerThatIsNeitherYesNorNoStillShowsANotice() = runUnitTest {
        speech.permission = MicPermission.NotAsked
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)

        vm.takeAction(VoiceAction.MicPermissionAnswered(MicPermission.NotAsked))

        assertEquals(VoiceNotice.Denied(canAskAgain = true), vm.voice?.notice)
        assertEquals(0, speech.sessions)
    }

    @Test
    fun telemetryNeverCarriesWhatWasSaid() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(VoiceAction.MicTapped)
        speech.say(Heard.Partial("2 eggs"))
        speech.say(Heard.Final(VoiceFixtures.SPOKEN))
        runCurrent()

        assertEquals(listOf("started on_device=true asked=false", "transcribed words=8-15"), telemetry.events)
        val words = VoiceFixtures.SPOKEN.split(" ", ",").filter { it.length > 2 }
        assertTrue(telemetry.events.none { event -> words.any { event.contains(it) } })
    }
}
