package com.opson.libraries.speech.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.speech.Heard
import com.opson.libraries.speech.SpeechFailure
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [MicSession] and [OneSessionAtATime] over a fake [AudioSteps]: the order the
 * platform is touched in, that the engine's input is never read before the
 * session is active and has an input, that a zero format is "no microphone",
 * that every way out tears down exactly once, and that a second session cannot
 * start while one is open. What AVFoundation itself does is device behaviour
 * and not covered here.
 */
class MicSessionTest : CoroutineTest() {

    private class FakeSteps(
        var recognizer: Boolean = true,
        var permitted: Boolean = true,
        var activates: Boolean = true,
        var hasInput: Boolean = true,
        var format: InputFormat = InputFormat(sampleRate = 48_000.0, channels = 1),
        var starts: Boolean = true,
    ) : AudioSteps {
        val calls = mutableListOf<String>()
        var onHeard: ((Heard) -> Unit)? = null

        override fun recognizerReady() = recognizer.also { calls += "recognizer" }
        override fun permitted() = permitted.also { calls += "permitted" }
        override fun activate() = activates.also { calls += "activate" }
        override fun inputAvailable() = hasInput.also { calls += "inputAvailable" }
        override fun inputFormat() = format.also { calls += "inputFormat" }
        override fun start(onHeard: (Heard) -> Unit): Boolean {
            calls += "start"
            this.onHeard = onHeard
            return starts
        }
        override fun endAudio() {
            calls += "endAudio"
        }
        override fun tearDown() {
            calls += "tearDown"
        }
    }

    private fun session(steps: FakeSteps): Pair<MicSession, MutableList<Heard>> {
        val heard = mutableListOf<Heard>()
        return MicSession(steps) { heard += it } to heard
    }

    @Test
    fun aHealthySessionTouchesTheInputOnlyAfterTheSessionIsActiveAndHasOne() {
        val steps = FakeSteps()
        session(steps).first.start()

        assertEquals(listOf("recognizer", "permitted", "activate", "inputAvailable", "inputFormat", "start"), steps.calls)
    }

    @Test
    fun eachFailureStopsBeforeTheEngineAndTearsDownOnce() {
        val cases = listOf(
            FakeSteps(recognizer = false) to SpeechFailure.NoRecognizer,
            FakeSteps(permitted = false) to SpeechFailure.NotAllowed,
            FakeSteps(activates = false) to SpeechFailure.Busy,
            FakeSteps(hasInput = false) to SpeechFailure.NoMicrophone,
        )
        cases.forEach { (steps, failure) ->
            val (session, heard) = session(steps)
            session.start()

            assertEquals(listOf<Heard>(Heard.Failed(failure)), heard, "$failure")
            assertFalse("inputFormat" in steps.calls, "$failure: the input node is never read")
            assertEquals(1, steps.calls.count { it == "tearDown" }, "$failure: torn down once")
            assertEquals("tearDown", steps.calls.last())
        }
    }

    @Test
    fun aZeroFormatIsNoMicrophone() {
        listOf(InputFormat(0.0, 1), InputFormat(44_100.0, 0)).forEach { format ->
            val steps = FakeSteps(format = format)
            val (session, heard) = session(steps)
            session.start()

            assertEquals(listOf<Heard>(Heard.Failed(SpeechFailure.NoMicrophone)), heard)
            assertFalse("start" in steps.calls, "no tap on a format with nothing in it")
            assertEquals("tearDown", steps.calls.last())
        }
    }

    @Test
    fun anEngineThatWillNotStartIsBusyAndTornDown() {
        val steps = FakeSteps(starts = false)
        val (session, heard) = session(steps)
        session.start()

        assertEquals(listOf<Heard>(Heard.Failed(SpeechFailure.Busy)), heard)
        assertEquals("tearDown", steps.calls.last())
    }

    @Test
    fun finishingStopsTheMicAndTheFinalTextEndsTheSession() {
        val steps = FakeSteps()
        val (session, heard) = session(steps)
        session.start()
        steps.onHeard!!(Heard.Partial("2 eggs"))

        session.finish()
        steps.onHeard!!(Heard.Final("2 eggs and toast"))
        steps.onHeard!!(Heard.Partial("late"))
        session.cancel()

        assertEquals(listOf(Heard.Partial("2 eggs"), Heard.Final("2 eggs and toast")), heard)
        assertEquals(listOf("endAudio", "tearDown"), steps.calls.drop(6))
    }

    @Test
    fun aRecognitionErrorTearsDownAndCancellingAfterwardsDoesNothing() {
        val steps = FakeSteps()
        val (session, heard) = session(steps)
        session.start()

        steps.onHeard!!(Heard.Failed(SpeechFailure.NoSpeech))
        session.cancel()
        session.finish()

        assertEquals(listOf<Heard>(Heard.Failed(SpeechFailure.NoSpeech)), heard)
        assertEquals(1, steps.calls.count { it == "tearDown" })
        assertFalse("endAudio" in steps.calls)
    }

    @Test
    fun cancellingTearsDownOnce() {
        val steps = FakeSteps()
        val (session, heard) = session(steps)
        session.start()

        session.cancel()
        session.cancel()
        steps.onHeard!!(Heard.Final("too late"))

        assertTrue(heard.isEmpty())
        assertEquals(1, steps.calls.count { it == "tearDown" })
    }

    @Test
    fun aSecondSessionWhileOneIsOpenIsBusyAndTouchesNothing() = runUnitTest {
        val created = mutableListOf<FakeSteps>()
        val gate = OneSessionAtATime { FakeSteps().also { created += it } }
        val first = mutableListOf<Heard>()
        val second = mutableListOf<Heard>()

        val firstJob = launch { gate.listen().toList(first) }
        runCurrent()
        launch { gate.listen().toList(second) }
        runCurrent()

        assertEquals(listOf<Heard>(Heard.Failed(SpeechFailure.Busy)), second)
        assertEquals(1, created.size, "no second engine or session")

        firstJob.cancel()
        runCurrent()
        assertEquals("tearDown", created.single().calls.last(), "cancelling the collector tears the first down")

        val third = mutableListOf<Heard>()
        launch { gate.listen().toList(third) }
        runCurrent()
        assertEquals(2, created.size, "once it is down, the next session starts")
        created.last().onHeard!!(Heard.Final("coffee"))
        runCurrent()
        assertEquals(listOf<Heard>(Heard.Final("coffee")), third)
        assertEquals("tearDown", created.last().calls.last())
    }

    @Test
    fun finishReachesTheOpenSession() = runUnitTest {
        val steps = FakeSteps()
        val gate = OneSessionAtATime { steps }
        val job = launch { gate.listen().toList(mutableListOf()) }
        runCurrent()

        gate.finish()

        assertTrue("endAudio" in steps.calls)
        job.cancel()
    }
}
