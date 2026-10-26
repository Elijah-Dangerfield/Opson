package com.opson.libraries.speech.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.opson.libraries.core.Catching
import com.opson.libraries.speech.Heard
import com.opson.libraries.speech.MicPermission
import com.opson.libraries.speech.MicPermissionPrompt
import com.opson.libraries.speech.SpeechAvailability
import com.opson.libraries.speech.SpeechFailure
import com.opson.libraries.speech.SpeechToText
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import me.tatarka.inject.annotations.Inject
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioApplicationRecordPermissionDenied
import platform.AVFAudio.AVAudioApplicationRecordPermissionGranted
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorDomain
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionResult
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * `SFSpeechRecognizer` for the phone's language, fed by an `AVAudioEngine`
 * input tap. When the phone can recognise the language on the device, the
 * request requires it, so the audio never leaves the phone. When it cannot,
 * iOS sends the audio to Apple's speech service and hands back text; Opson
 * never sees or keeps the audio either way, only the text.
 *
 * Everything that touches the recognizer, the audio session or the engine
 * runs on the main thread: [listen] is collected there, and the recognizer's
 * callbacks hop back to the main queue.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosSpeechToText @Inject constructor() : SpeechToText {

    private var active: AudioSession? = null

    override fun availability(): SpeechAvailability {
        val recognizer = SFSpeechRecognizer.new() ?: return SpeechAvailability.Unavailable(SpeechFailure.NoRecognizer)
        val onDevice = recognizer.supportsOnDeviceRecognition
        return when {
            onDevice -> SpeechAvailability.Ready(onDevice = true)
            // Without an on-device model, recognition is Apple's server, and iOS says when it cannot reach it.
            recognizer.available -> SpeechAvailability.Ready(onDevice = false)
            else -> SpeechAvailability.Unavailable(SpeechFailure.NeedsConnection)
        }
    }

    override fun permission(): MicPermission = micPermission()

    override fun listen(): Flow<Heard> = callbackFlow {
        val session = AudioSession { heard ->
            trySend(heard)
            if (heard !is Heard.Partial) close()
        }
        active = session
        session.start()
        awaitClose {
            session.cancel()
            if (active === session) active = null
        }
    }.flowOn(Dispatchers.Main)

    override fun finishListening() {
        active?.finish()
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }
}

/**
 * iOS asks twice, speech recognition and then the microphone, each once per
 * install. Either refused means only Settings can turn voice on.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosMicPermissionPrompt @Inject constructor() : MicPermissionPrompt {

    @Composable
    override fun rememberPermissionRequest(onAnswer: (MicPermission) -> Unit): () -> Unit {
        val latestOnAnswer by rememberUpdatedState(onAnswer)
        return remember {
            {
                SFSpeechRecognizer.requestAuthorization { status ->
                    // Both answers arrive on an arbitrary queue; state changes belong on the main one.
                    if (status != SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized) {
                        onMain { latestOnAnswer(MicPermission.Denied(canAskAgain = false)) }
                    } else {
                        AVAudioApplication.requestRecordPermissionWithCompletionHandler { granted ->
                            onMain { latestOnAnswer(if (granted) MicPermission.Granted else MicPermission.Denied(canAskAgain = false)) }
                        }
                    }
                }
            }
        }
    }
}

private fun micPermission(): MicPermission {
    val speech = SFSpeechRecognizer.authorizationStatus()
    val mic = AVAudioApplication.sharedInstance().recordPermission
    return when {
        speech == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized &&
            mic == AVAudioApplicationRecordPermissionGranted -> MicPermission.Granted
        // Denied, or Restricted (parental controls, device management): not ours to ask again.
        speech == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusDenied ||
            speech == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusRestricted ||
            mic == AVAudioApplicationRecordPermissionDenied -> MicPermission.Denied(canAskAgain = false)
        else -> MicPermission.NotAsked
    }
}

private fun onMain(block: () -> Unit) = dispatch_async(dispatch_get_main_queue(), block)

/**
 * One listening session: the audio session, the engine's input tap, and the
 * recognition task, all torn down together. Main thread only.
 */
@OptIn(ExperimentalForeignApi::class)
private class AudioSession(private val emit: (Heard) -> Unit) {
    private val engine = AVAudioEngine()
    private val request = SFSpeechAudioBufferRecognitionRequest()
    private var recognizer: SFSpeechRecognizer? = null
    private var task: SFSpeechRecognitionTask? = null
    private var tapped = false
    private var done = false

    fun start() {
        begin()?.let { deliver(Heard.Failed(it)) }
    }

    /** Opens the mic and starts recognising; the reason it could not, or null once it is listening. */
    private fun begin(): SpeechFailure? {
        val recognizer = SFSpeechRecognizer.new() ?: return SpeechFailure.NoRecognizer
        this.recognizer = recognizer
        request.shouldReportPartialResults = true
        if (recognizer.supportsOnDeviceRecognition) request.requiresOnDeviceRecognition = true

        val audio = AVAudioSession.sharedInstance()
        val ready = audio.setCategory(
            AVAudioSessionCategoryRecord,
            mode = AVAudioSessionModeMeasurement,
            options = AVAudioSessionCategoryOptionDuckOthers,
            error = null,
        ) && audio.setActive(true, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
        if (!ready) return SpeechFailure.Busy

        val input = engine.inputNode
        val format = input.outputFormatForBus(0uL)
        // No input (the simulator without a mic) reports a zero format, and installing a tap on it throws an ObjC exception.
        if (format.sampleRate <= 0.0 || format.channelCount == 0u) return SpeechFailure.NoRecognizer
        input.installTapOnBus(0uL, bufferSize = TAP_FRAMES, format = format) { buffer, _ ->
            // Called on the audio thread; the request is safe to feed from there.
            buffer?.let(request::appendAudioPCMBuffer)
        }
        tapped = true
        engine.prepare()
        if (!engine.startAndReturnError(null)) return SpeechFailure.Busy

        task = recognizer.recognitionTaskWithRequest(request) { result, error ->
            onMain { answered(result, error) }
        }
        return null
    }

    /** Stop the mic; the recognizer then settles on its final text. */
    fun finish() {
        if (done) return
        stopAudio()
        request.endAudio()
    }

    fun cancel() {
        done = true
        task?.cancel()
        task = null
        stopAudio()
    }

    private fun answered(result: SFSpeechRecognitionResult?, error: NSError?) {
        when {
            result != null && result.final -> deliver(Heard.Final(result.bestTranscription.formattedString))
            result != null -> deliver(Heard.Partial(result.bestTranscription.formattedString))
            error != null -> deliver(Heard.Failed(failureFor(error)))
        }
    }

    private fun failureFor(error: NSError): SpeechFailure = when {
        error.domain == ASSISTANT_ERROR_DOMAIN && error.code in NO_SPEECH_CODES -> SpeechFailure.NoSpeech
        error.domain == NSURLErrorDomain || recognizer?.available == false -> SpeechFailure.NeedsConnection
        else -> SpeechFailure.Unknown
    }

    private fun deliver(heard: Heard) {
        if (done) return
        if (heard !is Heard.Partial) {
            done = true
            stopAudio()
        }
        emit(heard)
    }

    private fun stopAudio() {
        if (engine.running) engine.stop()
        if (tapped) {
            engine.inputNode.removeTapOnBus(0uL)
            tapped = false
        }
        // Lets music or a podcast the session ducked come back up.
        Catching { AVAudioSession.sharedInstance().setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null) }
    }

    private companion object {
        const val TAP_FRAMES = 1024u

        /** Siri's recognizer reports "no speech detected" in this domain. */
        const val ASSISTANT_ERROR_DOMAIN = "kAFAssistantErrorDomain"
        val NO_SPEECH_CODES = setOf(203L, 1110L)
    }
}
