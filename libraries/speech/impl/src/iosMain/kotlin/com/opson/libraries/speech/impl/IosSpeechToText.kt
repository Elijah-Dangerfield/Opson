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
import kotlinx.coroutines.flow.Flow
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
import platform.AVFAudio.availableInputs
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState
import platform.darwin.NSObjectProtocol
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
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
 * second tap from starting a second engine, and runs every audio session,
 * engine and recognizer call on one serial queue, so a CoreAudio call that
 * hangs never freezes the screen.
 *
 * In the simulator CoreAudio can deadlock when the Mac cannot hand it an
 * input, and it then aborts the app itself, from any thread. So the simulator
 * reports no microphone unless the scheme sets `OPSON_VOICE_IN_SIMULATOR=1`.
 * Real devices always take the full path.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosSpeechToText @Inject constructor() : SpeechToText {

    private val sessions = OneSessionAtATime(AudioQueue) { AvAudioSteps() }

    override fun availability(): SpeechAvailability {
        if (simulatorWithoutMic()) {
            log.d("Voice off in the simulator; set OPSON_VOICE_IN_SIMULATOR=1 to try it")
            return SpeechAvailability.Unavailable(SpeechFailure.NoMicrophone)
        }
        // No microphone at all: say so before asking for permission to use one.
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

    override fun listen(): Flow<Heard> = sessions.listen()

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
                            // Trusted, not read back: right after the prompt a read can still say "not determined",
                            // which used to land as no answer at all. The session checks permission again anyway.
                            onMain {
                                // The answer can land before the alert has gone and the app is active again; an audio
                                // session started then is the one that fails, so the answer waits for the app.
                                whenActive { latestOnAnswer(if (granted) MicPermission.Granted else MicPermission.Denied(canAskAgain = false)) }
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

/** The one serial queue every audio session, engine and recognizer call runs on. */
private object AudioQueue : SerialQueue {
    private val queue = dispatch_queue_create("com.opson.speech.audio", null)

    override fun post(block: () -> Unit) = dispatch_async(queue, block)
}

/** The simulator, unless the scheme opts in to trying its microphone. */
private fun simulatorWithoutMic(): Boolean {
    val environment = NSProcessInfo.processInfo.environment
    return environment["SIMULATOR_DEVICE_NAME"] != null && environment["OPSON_VOICE_IN_SIMULATOR"] != "1"
}

/**
 * AVFoundation and Speech behind [AudioSteps]. One instance per session, used
 * only on [AudioQueue]. Each step logs its result at debug, so a stall in a
 * retest shows exactly which call it was.
 */
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
        val made = SFSpeechRecognizer.new()
        recognizer = made
        log.d("Speech recognizer: ${made != null}")
        return made != null
    }

    override fun permitted(): Boolean {
        val permitted = micPermission() == MicPermission.Granted
        log.d("Speech permitted: $permitted")
        return permitted
    }

    /** Category, then mode, then active: each checked, and the engine is not made until all three succeeded. */
    override fun activate(): Boolean {
        activated = checked("setCategory(record)") { error ->
            audio.setCategory(AVAudioSessionCategoryRecord, withOptions = AVAudioSessionCategoryOptionDuckOthers, error = error)
        } &&
            checked("setMode(measurement)") { error -> audio.setMode(AVAudioSessionModeMeasurement, error = error) } &&
            checked("setActive(true)") { error -> audio.setActive(true, error = error) }
        return activated
    }

    /** Read after activation: an input must be available, offered and on the route, with channels and a rate. */
    override fun inputAvailable(): Boolean {
        val available = audio.inputAvailable
        val offered = audio.availableInputs.orEmpty().size
        val routed = audio.currentRoute.inputs.size
        val channels = audio.inputNumberOfChannels
        val rate = audio.sampleRate
        log.d("Audio input: available=$available offered=$offered routed=$routed channels=$channels rate=$rate")
        return !simulatorWithoutMic() && available && offered > 0 && routed > 0 && channels > 0 && rate > 0.0
    }

    override fun inputFormat(): InputFormat {
        // MicSession only gets here after a successful activate; this keeps it so if that ever changes.
        if (!activated) return InputFormat(sampleRate = 0.0, channels = 0)
        log.d("Audio engine: creating")
        val engine = AVAudioEngine().also { engine = it }
        val node = engine.inputNode.also { input = it }
        val format = node.outputFormatForBus(0uL).also { format = it }
        log.d("Audio input format: rate=${format.sampleRate} channels=${format.channelCount}")
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
        // Answers come on the recognizer's queue; OneSessionAtATime hands them back to the audio queue.
        task = recognizer.recognitionTaskWithRequest(request) { result, error -> answered(result, error, onHeard) }
        log.d("Speech recognition: started, on device ${request.requiresOnDeviceRecognition}")
        return true
    }

    override fun endAudio() {
        stopEngine()
        request.endAudio()
    }

    override fun tearDown() {
        log.d("Audio: tearing down")
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
        if (ok) log.d("Audio $step: ok") else log.w("Audio $step failed: ${error.value?.domain} ${error.value?.code}")
        ok
    }

    private companion object {
        const val TAP_FRAMES = 1024u

        /** Siri's recognizer reports "no speech detected" in this domain. */
        const val ASSISTANT_ERROR_DOMAIN = "kAFAssistantErrorDomain"
        val NO_SPEECH_CODES = setOf(203L, 1110L)
    }
}
