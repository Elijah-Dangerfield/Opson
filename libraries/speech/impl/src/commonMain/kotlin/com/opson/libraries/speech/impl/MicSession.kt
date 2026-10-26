package com.opson.libraries.speech.impl

import com.opson.libraries.speech.Heard
import com.opson.libraries.speech.SpeechFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.CoroutineContext

/** The input's format as the engine reports it. Zero in either means there is no usable input. */
internal data class InputFormat(val sampleRate: Double, val channels: Int) {
    val usable: Boolean get() = sampleRate > 0.0 && channels > 0
}

/**
 * The platform steps of one listening session, which [MicSession] runs in
 * this order and no other. The order is the point: on iOS, reading the
 * engine's input node before the audio session is active, or when there is
 * no input, aborts the process (an RPC timeout inside AudioToolbox, which
 * nothing can catch). So every check that touches no audio hardware comes
 * first, and [inputFormat] is the first call allowed to touch the engine.
 *
 * Every call is made on one serial queue, never the main thread (see
 * [OneSessionAtATime]).
 */
internal interface AudioSteps {
    /** A recognizer exists for the phone's language. Touches no audio. */
    fun recognizerReady(): Boolean

    /** Speech recognition and the microphone are both allowed. Touches no audio. */
    fun permitted(): Boolean

    /** Configure and activate the audio session, each step error-checked. */
    fun activate(): Boolean

    /** After [activate]: the session reports an input, its route has one, and it has a rate and channels. */
    fun inputAvailable(): Boolean

    /** The first touch of the engine's input node. */
    fun inputFormat(): InputFormat

    /** Install the tap, start the engine, start recognising. False if any of it failed. */
    fun start(onHeard: (Heard) -> Unit): Boolean

    /** Stop the mic so the recognizer settles on its final text. */
    fun endAudio()

    /**
     * Remove the tap, stop the engine, cancel recognition, and deactivate
     * the session telling others (so ducked audio comes back). Safe after any
     * partial [start] and safe twice.
     */
    fun tearDown()
}

/**
 * One session over [AudioSteps]. It ends exactly once, and every way it can
 * end (a failure at any step, a final text, an error, a cancel) tears the
 * platform down before anything else happens.
 */
internal class MicSession(private val steps: AudioSteps, private val emit: (Heard) -> Unit) {
    private var ended = false

    fun start() {
        val failure = when {
            !steps.recognizerReady() -> SpeechFailure.NoRecognizer
            !steps.permitted() -> SpeechFailure.NotAllowed
            !steps.activate() -> SpeechFailure.Busy
            !steps.inputAvailable() -> SpeechFailure.NoMicrophone
            !steps.inputFormat().usable -> SpeechFailure.NoMicrophone
            !steps.start(::heard) -> SpeechFailure.Busy
            else -> null
        }
        failure?.let { end(Heard.Failed(it)) }
    }

    fun finish() {
        if (!ended) steps.endAudio()
    }

    fun cancel() {
        if (ended) return
        ended = true
        steps.tearDown()
    }

    private fun heard(heard: Heard) {
        if (ended) return
        if (heard is Heard.Partial) emit(heard) else end(heard)
    }

    private fun end(last: Heard) {
        if (ended) return
        ended = true
        steps.tearDown()
        emit(last)
    }
}

/** Runs blocks one at a time, in order, off the main thread: a serial dispatch queue on iOS. */
fun interface SerialQueue {
    fun post(block: () -> Unit)
}

/** Coroutines on a [SerialQueue], so a flow's body runs where the audio work belongs. */
internal class SerialQueueDispatcher(private val queue: SerialQueue) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = queue.post { block.run() }
}

/**
 * At most one session in the process, and every call into the platform made
 * on [queue], never the caller's thread. A blocking audio call (CoreAudio can
 * hang for seconds before it gives up) then stalls only the queue: the main
 * thread keeps drawing, so "Listening" shows and the person can still leave.
 *
 * A second [listen] while one is open (starting, listening or settling)
 * answers [SpeechFailure.Busy] without touching the platform, so a quick
 * second tap can never start a second engine. [current] is only read and
 * written on [queue].
 */
internal class OneSessionAtATime(private val queue: SerialQueue, private val steps: () -> AudioSteps) {
    private var current: MicSession? = null

    fun listen(): Flow<Heard> = callbackFlow {
        if (current != null) {
            trySend(Heard.Failed(SpeechFailure.Busy))
            close()
            awaitClose()
            return@callbackFlow
        }
        // The recognizer answers on its own queue; the session is only ever touched on this one.
        val session = MicSession(HoppingSteps(steps(), queue)) { heard ->
            trySend(heard)
            if (heard !is Heard.Partial) close()
        }
        current = session
        try {
            session.start()
            awaitClose()
        } finally {
            session.cancel()
            current = null
        }
    }.flowOn(SerialQueueDispatcher(queue))

    fun finish() {
        queue.post { current?.finish() }
    }
}

/** [steps], with what the recognizer hears handed on through [queue] rather than wherever it arrived. */
private class HoppingSteps(private val steps: AudioSteps, private val queue: SerialQueue) : AudioSteps by steps {
    override fun start(onHeard: (Heard) -> Unit): Boolean = steps.start { heard -> queue.post { onHeard(heard) } }
}
