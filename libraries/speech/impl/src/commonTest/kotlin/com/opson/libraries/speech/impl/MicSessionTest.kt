package com.opson.libraries.speech.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.speech.Heard
import com.opson.libraries.speech.SpeechFailure
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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

    /** A serial queue the test runs by hand, standing in for the audio queue. */
    private class ManualQueue : SerialQueue {
        private val blocks = ArrayDeque<() -> Unit>()
        val pending: Int get() = blocks.size

        override fun post(block: () -> Unit) {
            blocks.addLast(block)
        }

        fun drain() {
            while (blocks.isNotEmpty()) blocks.removeFirst()()
        }
    }

    private fun TestScope.settle(queue: ManualQueue) {
        repeat(4) {
            runCurrent()
            queue.drain()
        }
        runCurrent()
    }

    /** The cause of the iOS freeze: nothing audio may run on the thread that asked for it. */
    @Test
    fun nothingTouchesTheAudioOnTheCallersThread() = runUnitTest {
        val queue = ManualQueue()
        val steps = FakeSteps()
        val gate = OneSessionAtATime(queue) { steps }
        val heard = mutableListOf<Heard>()

        val job = launch { gate.listen().toList(heard) }
        runCurrent()
        assertTrue(steps.calls.isEmpty(), "the caller only queues the work")
        assertTrue(queue.pending > 0)

        settle(queue)
        assertEquals(listOf("recognizer", "permitted", "activate", "inputAvailable", "inputFormat", "start"), steps.calls)

        gate.finish()
        assertFalse("endAudio" in steps.calls, "finish is queued too")
        settle(queue)
        assertTrue("endAudio" in steps.calls)

        // The recognizer answers on its own queue; the session only sees it on the audio queue.
        steps.onHeard!!(Heard.Final("2 eggs"))
        assertTrue(heard.isEmpty())
        settle(queue)
        assertEquals(listOf<Heard>(Heard.Final("2 eggs")), heard)
        assertEquals("tearDown", steps.calls.last())
        job.cancel()
    }

    @Test
    fun aSecondSessionWhileOneIsOpenIsBusyAndTouchesNothing() = runUnitTest {
        val queue = ManualQueue()
        val created = mutableListOf<FakeSteps>()
        val gate = OneSessionAtATime(queue) { FakeSteps().also { created += it } }
        val first = mutableListOf<Heard>()
        val second = mutableListOf<Heard>()

        val firstJob = launch { gate.listen().toList(first) }
        settle(queue)
        launch { gate.listen().toList(second) }
        settle(queue)

        assertEquals(listOf<Heard>(Heard.Failed(SpeechFailure.Busy)), second)
        assertEquals(1, created.size, "no second engine or session")

        firstJob.cancel()
        settle(queue)
        assertEquals("tearDown", created.single().calls.last(), "cancelling the collector tears the first down")

        val third = mutableListOf<Heard>()
        launch { gate.listen().toList(third) }
        settle(queue)
        assertEquals(2, created.size, "once it is down, the next session starts")
        created.last().onHeard!!(Heard.Final("coffee"))
        settle(queue)
        assertEquals(listOf<Heard>(Heard.Final("coffee")), third)
        assertEquals("tearDown", created.last().calls.last())
    }

    /** A queue still busy with the first session keeps a second tap waiting behind it: never two engines at once. */
    @Test
    fun aSecondTapWaitsForTheFirstSessionsTeardown() = runUnitTest {
        val queue = ManualQueue()
        val log = mutableListOf<String>()
        var made = 0
        val gate = OneSessionAtATime(queue) {
            val id = ++made
            object : AudioSteps by FakeSteps() {
                override fun activate(): Boolean = true.also { log += "activate $id" }
                override fun tearDown() {
                    log += "tearDown $id"
                }
            }
        }

        val first = backgroundScope.launch { gate.listen().toList(mutableListOf()) }
        settle(queue)
        first.cancel()
        val second = backgroundScope.launch { gate.listen().toList(mutableListOf()) }
        runCurrent()
        assertEquals(listOf("activate 1"), log, "nothing more runs until the queue does")

        settle(queue)
        assertEquals(listOf("activate 1", "tearDown 1", "activate 2"), log)

        second.cancel()
        settle(queue)
        assertEquals("tearDown 2", log.last())
    }
}
