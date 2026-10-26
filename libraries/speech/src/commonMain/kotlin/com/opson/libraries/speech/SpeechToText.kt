package com.opson.libraries.speech

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

/** Where the microphone permission stands. On iOS it also covers speech recognition, which is asked for alongside. */
sealed interface MicPermission {
    data object Granted : MicPermission

    /** Not decided yet, so the system prompt can still be shown. */
    data object NotAsked : MicPermission

    /** Refused. [canAskAgain] is false once only Settings can change it, which on iOS is after the first answer. */
    data class Denied(val canAskAgain: Boolean) : MicPermission
}

/** Why speech could not become text. */
enum class SpeechFailure {
    /** The mic ran but nothing was said, or nothing that read as words. */
    NoSpeech,

    /** No recognizer on this phone for its language, or no microphone input at all (the iOS simulator). */
    NoRecognizer,

    /** The on-device recognizer has no model for the language, and there is nothing to fall back to. */
    NoModel,

    /** The recognizer works through a server and could not reach it. */
    NeedsConnection,

    /** Another app holds the microphone, or the recognizer is already in use. */
    Busy,
    Unknown,
}

sealed interface SpeechAvailability {
    /** [onDevice] when the words are worked out on the phone, so the audio never leaves it. */
    data class Ready(val onDevice: Boolean) : SpeechAvailability

    data class Unavailable(val why: SpeechFailure) : SpeechAvailability
}

/** What the recognizer has heard so far. A session is any number of [Partial]s, then one [Final] or one [Failed]. */
sealed interface Heard {
    /** The best reading so far of everything said in this session, not just the latest words. */
    data class Partial(val text: String) : Heard

    /** The settled text. Blank when nothing was said. */
    data class Final(val text: String) : Heard

    data class Failed(val why: SpeechFailure) : Heard
}

/**
 * Speech to text on the device. Audio goes from the microphone to the
 * platform recognizer and nowhere else: nothing here records it, keeps it, or
 * sends it to Opson. Which recognizer runs, and whether the platform's own
 * service sees the audio, is each binding's KDoc.
 *
 * Nothing touches the platform until a method is called, so binding this
 * costs nothing at launch.
 */
interface SpeechToText {
    fun availability(): SpeechAvailability

    fun permission(): MicPermission

    /**
     * Starts the microphone and listens until [finishListening], a failure,
     * or the collector cancels. Cancelling abandons the session: the mic
     * stops and nothing more is delivered. Only one session runs at a time;
     * the permission must already be granted.
     */
    fun listen(): Flow<Heard>

    /** Stops the microphone. The running [listen] then delivers its [Heard.Final] and completes. */
    fun finishListening()

    /** The app's page in the system Settings, where a refused microphone is turned back on. */
    fun openSettings()
}

/** The system microphone prompt, which on Android has to be launched from composition. */
interface MicPermissionPrompt {
    /** A function that shows the prompt; [onAnswer] gets the result on the main thread. */
    @Composable
    fun rememberPermissionRequest(onAnswer: (MicPermission) -> Unit): () -> Unit
}
