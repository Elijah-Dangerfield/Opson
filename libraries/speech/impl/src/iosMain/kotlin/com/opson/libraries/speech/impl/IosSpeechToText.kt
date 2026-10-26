package com.opson.libraries.speech.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.speech.Heard
import com.opson.libraries.speech.MicPermission
import com.opson.libraries.speech.MicPermissionPrompt
import com.opson.libraries.speech.SpeechAvailability
import com.opson.libraries.speech.SpeechFailure
import com.opson.libraries.speech.SpeechToText
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import me.tatarka.inject.annotations.Inject
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioApplicationRecordPermissionDenied
import platform.AVFAudio.AVAudioApplicationRecordPermissionGranted
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioInputNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.currentRoute
import platform.AVFAudio.inputAvailable
import platform.AVFAudio.inputNumberOfChannels
import platform.AVFAudio.sampleRate
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorDomain
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionResult
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState
import platform.darwin.NSObjectProtocol
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
 * The order of a session lives in [MicSession]: the audio session is active
 * and has an input before the engine's input node is touched, because
 * reading it without one aborts the process. [OneSessionAtATime] keeps a
 * second tap from starting a second engine. Everything runs on the main
 * thread; the recognizer's callbacks hop back to the main queue.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosSpeechToText @Inject constructor() : SpeechToText {

    private val sessions = OneSessionAtATime { AvAudioSteps() }

    override fun availability(): SpeechAvailability {
        // No microphone at all (some simulators): say so before asking for permission to use one.
        if (!AVAudioSession.sharedInstance().inputAvailable) return SpeechAvailability.Unavailable(SpeechFailure.NoMicrophone)
        val recognizer = SFSpeechRecognizer.new() ?: return SpeechAvailability.Unavailable(SpeechFailure.NoRecognizer)
        return when {
            recognizer.supportsOnDeviceRecognition -> SpeechAvailability.Ready(onDevice = true)
            // Without an on-device model, recognition is Apple's server, and iOS says when it cannot reach it.
            recognizer.available -> SpeechAvailability.Ready(onDevice = false)
            else -> SpeechAvailability.Unavailable(SpeechFailure.NeedsConnection)
        }
    }

    override fun permission(): MicPermission = micPermission()

    override fun listen(): Flow<Heard> = sessions.listen().flowOn(Dispatchers.Main)

    override fun finishListening() {
        sessions.finish()
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
                        onMain { whenActive { latestOnAnswer(MicPermission.Denied(canAskAgain = false)) } }
                    } else {
                        AVAudioApplication.requestRecordPermissionWithCompletionHandler { granted ->
                            // Read back rather than trusted: the answer is the state both prompts left behind.
                            onMain {
                                // The answer can land before the alert has gone and the app is active again; an audio
                                // session started then is the one that fails, so the answer waits for the app.
                                whenActive { latestOnAnswer(if (granted) micPermission() else MicPermission.Denied(canAskAgain = false)) }
                            }
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

/** Runs [block] now if the app is active, else once it next becomes active. Main thread only. */
private fun whenActive(block: () -> Unit) {
    if (UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive) {
        block()
        return
    }
    val center = NSNotificationCenter.defaultCenter
    var observer: NSObjectProtocol? = null
    observer = center.addObserverForName(UIApplicationDidBecomeActiveNotification, `object` = null, queue = NSOperationQueue.mainQueue) { _ ->
        observer?.let(center::removeObserver)
        observer = null
        block()
    }
}

private val log = KLog.withTag("Speech")

/** AVFoundation and Speech behind [AudioSteps]. One instance per session. Main thread only. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class AvAudioSteps : AudioSteps {
    private val audio = AVAudioSession.sharedInstance()
    private val request = SFSpeechAudioBufferRecognitionRequest()
    private var recognizer: SFSpeechRecognizer? = null
    private var engine: AVAudioEngine? = null
    private var input: AVAudioInputNode? = null
    private var format: AVAudioFormat? = null
    private var task: SFSpeechRecognitionTask? = null
    private var activated = false
    private var tapped = false

    override fun recognizerReady(): Boolean {
        recognizer = SFSpeechRecognizer.new()
        return recognizer != null
    }

    override fun permitted(): Boolean = micPermission() == MicPermission.Granted

    override fun activate(): Boolean {
        val categorised = checked("setCategory") { error ->
            audio.setCategory(
                AVAudioSessionCategoryRecord,
                mode = AVAudioSessionModeMeasurement,
                options = AVAudioSessionCategoryOptionDuckOthers,
                error = error,
            )
        }
        activated = categorised && checked("setActive") { error -> audio.setActive(true, error = error) }
        return activated
    }

    override fun inputAvailable(): Boolean =
        audio.inputAvailable &&
            audio.currentRoute.inputs.isNotEmpty() &&
            audio.inputNumberOfChannels > 0 &&
            audio.sampleRate > 0.0

    override fun inputFormat(): InputFormat {
        val engine = AVAudioEngine().also { engine = it }
        val node = engine.inputNode.also { input = it }
        val format = node.outputFormatForBus(0uL).also { format = it }
        return InputFormat(format.sampleRate, format.channelCount.toInt())
    }

    override fun start(onHeard: (Heard) -> Unit): Boolean {
        val recognizer = recognizer ?: return false
        val engine = engine ?: return false
        val node = input ?: return false
        request.shouldReportPartialResults = true
        if (recognizer.supportsOnDeviceRecognition) request.requiresOnDeviceRecognition = true
        node.installTapOnBus(0uL, bufferSize = TAP_FRAMES, format = format) { buffer, _ ->
            // Called on the audio thread; the request is safe to feed from there.
            buffer?.let(request::appendAudioPCMBuffer)
        }
        tapped = true
        engine.prepare()
        if (!checked("engine start") { error -> engine.startAndReturnError(error) }) return false
        task = recognizer.recognitionTaskWithRequest(request) { result, error ->
            onMain { answered(result, error, onHeard) }
        }
        return true
    }

    override fun endAudio() {
        stopEngine()
        request.endAudio()
    }

    override fun tearDown() {
        task?.cancel()
        task = null
        stopEngine()
        request.endAudio()
        if (activated) {
            activated = false
            // Lets music or a podcast the session ducked come back up.
            checked("setActive(false)") { error ->
                audio.setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = error)
            }
        }
    }

    private fun stopEngine() {
        engine?.let { if (it.running) it.stop() }
        if (tapped) {
            // The node kept from inputFormat: asking the engine for it again is what aborts.
            input?.removeTapOnBus(0uL)
            tapped = false
        }
    }

    private fun answered(result: SFSpeechRecognitionResult?, error: NSError?, onHeard: (Heard) -> Unit) {
        when {
            result != null && result.final -> onHeard(Heard.Final(result.bestTranscription.formattedString))
            result != null -> onHeard(Heard.Partial(result.bestTranscription.formattedString))
            error != null -> onHeard(Heard.Failed(failureFor(error)))
        }
    }

    private fun failureFor(error: NSError): SpeechFailure = when {
        error.domain == ASSISTANT_ERROR_DOMAIN && error.code in NO_SPEECH_CODES -> SpeechFailure.NoSpeech
        error.domain == NSURLErrorDomain || recognizer?.available == false -> SpeechFailure.NeedsConnection
        else -> SpeechFailure.Unknown
    }

    /** Runs an NSError-returning call; logs the failure (no audio, no words, just the step and the code). */
    private fun checked(step: String, call: (kotlinx.cinterop.CPointer<ObjCObjectVar<NSError?>>) -> Boolean): Boolean = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val ok = call(error.ptr)
        if (!ok) log.w("Audio $step failed: ${error.value?.domain} ${error.value?.code}")
        ok
    }

    private companion object {
        const val TAP_FRAMES = 1024u

        /** Siri's recognizer reports "no speech detected" in this domain. */
        const val ASSISTANT_ERROR_DOMAIN = "kAFAssistantErrorDomain"
        val NO_SPEECH_CODES = setOf(203L, 1110L)
    }
}
