package com.opson.libraries.speech.impl

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.opson.libraries.core.Catching
import com.opson.libraries.opson.ActivityProvider
import com.opson.libraries.speech.Heard
import com.opson.libraries.speech.MicPermission
import com.opson.libraries.speech.MicPermissionPrompt
import com.opson.libraries.speech.SpeechAvailability
import com.opson.libraries.speech.SpeechFailure
import com.opson.libraries.speech.SpeechToText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Android's `SpeechRecognizer`. On API 31 and up, when the phone has an
 * on-device recognizer, that one runs and the audio stays on the phone.
 * Otherwise the phone's default recognition service runs (usually Google's),
 * asked to prefer offline; whether it sends audio to its own servers is the
 * service's choice, not Opson's. Opson only ever receives text.
 *
 * `SpeechRecognizer` must be created and called on the main thread, so
 * [listen] runs there.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidSpeechToText @Inject constructor(
    private val context: Context,
    private val activityProvider: ActivityProvider,
) : SpeechToText {

    private var active: RecognizerSession? = null

    override fun availability(): SpeechAvailability = when {
        onDeviceAvailable() -> SpeechAvailability.Ready(onDevice = true)
        SpeechRecognizer.isRecognitionAvailable(context) -> SpeechAvailability.Ready(onDevice = false)
        else -> SpeechAvailability.Unavailable(SpeechFailure.NoRecognizer)
    }

    /** "Never asked" and "denied for good" both read as not asked until the prompt says otherwise, as for the camera. */
    override fun permission(): MicPermission = when {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
            MicPermission.Granted
        activityProvider.currentActivity()?.canAskAgain() == true -> MicPermission.Denied(canAskAgain = true)
        else -> MicPermission.NotAsked
    }

    override fun listen(): Flow<Heard> = callbackFlow {
        val session = RecognizerSession(
            context = context,
            onDevice = onDeviceAvailable(),
            canFallBack = SpeechRecognizer.isRecognitionAvailable(context),
        ) { heard ->
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
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Catching { context.startActivity(intent) }
    }

    private fun onDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidMicPermissionPrompt @Inject constructor(
    private val activityProvider: ActivityProvider,
) : MicPermissionPrompt {

    @Composable
    override fun rememberPermissionRequest(onAnswer: (MicPermission) -> Unit): () -> Unit {
        val latestOnAnswer by rememberUpdatedState(onAnswer)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            latestOnAnswer(
                if (granted) {
                    MicPermission.Granted
                } else {
                    MicPermission.Denied(canAskAgain = activityProvider.currentActivity()?.canAskAgain() == true)
                },
            )
        }
        return remember(launcher) { { launcher.launch(Manifest.permission.RECORD_AUDIO) } }
    }
}

private fun Activity.canAskAgain(): Boolean =
    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)

/**
 * One listening session. An on-device recognizer that turns out to have no
 * model for the language says so straight away, before anything is heard; the
 * session then starts again once on the default recognizer, so the person
 * just sees it listening.
 */
private class RecognizerSession(
    private val context: Context,
    onDevice: Boolean,
    private val canFallBack: Boolean,
    private val emit: (Heard) -> Unit,
) : RecognitionListener {
    private var onDevice = onDevice
    private var recognizer: SpeechRecognizer? = null
    private var heardAnything = false
    private var done = false

    fun start() {
        recognizer = create().also {
            it.setRecognitionListener(this)
            it.startListening(intent())
        }
    }

    fun finish() {
        if (!done) recognizer?.stopListening()
    }

    fun cancel() {
        done = true
        recognizer?.let {
            Catching { it.cancel() }
            it.destroy()
        }
        recognizer = null
    }

    private fun create(): SpeechRecognizer =
        if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

    private fun deliver(heard: Heard) {
        if (done) return
        if (heard !is Heard.Partial) done = true
        emit(heard)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults.bestText() ?: return
        heardAnything = true
        deliver(Heard.Partial(text))
    }

    override fun onResults(results: Bundle?) {
        deliver(Heard.Final(results.bestText().orEmpty()))
    }

    override fun onError(error: Int) {
        if (done) return
        val noModel = error == ERROR_LANGUAGE_NOT_SUPPORTED || error == ERROR_LANGUAGE_UNAVAILABLE
        if (noModel && onDevice && canFallBack && !heardAnything) {
            recognizer?.destroy()
            onDevice = false
            start()
            return
        }
        deliver(Heard.Failed(failureFor(error)))
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

private fun Bundle?.bestText(): String? =
    this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

// Added in API 31; the values are fixed, so they are spelled out rather than read from a newer SDK's fields.
private const val ERROR_TOO_MANY_REQUESTS = 10
private const val ERROR_SERVER_DISCONNECTED = 11
private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
private const val ERROR_LANGUAGE_UNAVAILABLE = 13

private fun failureFor(error: Int): SpeechFailure = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechFailure.NoSpeech
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
    ERROR_SERVER_DISCONNECTED, ERROR_TOO_MANY_REQUESTS,
    -> SpeechFailure.NeedsConnection
    ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> SpeechFailure.NoModel
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_AUDIO -> SpeechFailure.Busy
    else -> SpeechFailure.Unknown
}
