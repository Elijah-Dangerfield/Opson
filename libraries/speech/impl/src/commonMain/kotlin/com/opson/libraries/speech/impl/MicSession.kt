package com.opson.libraries.speech.impl

import com.opson.libraries.speech.Heard
import com.opson.libraries.speech.SpeechFailure
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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
 * Every call is made on the main thread.
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

/**
 * At most one session in the process. A second [listen] while one is still
 * open (starting, listening or settling) answers [SpeechFailure.Busy] without
 * touching the platform, so a quick second tap can never start a second
 * engine on top of the first. Main thread only.
 */
internal class OneSessionAtATime(private val steps: () -> AudioSteps) {
    private var current: MicSession? = null

    fun listen(): Flow<Heard> = callbackFlow {
        if (current != null) {
            trySend(Heard.Failed(SpeechFailure.Busy))
            close()
            awaitClose()
            return@callbackFlow
        }
        val session = MicSession(steps()) { heard ->
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
    }

    fun finish() {
        current?.finish()
    }
}
