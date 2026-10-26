package com.opson.features.logger.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.speech.SpeechFailure
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** Why voice handed over to typing, as `voice.fallback` names it. */
enum class VoiceFallback(val wire: String) {
    NoRecognizer("no_recognizer"),
    NoModel("no_model"),
    NoMicrophone("no_microphone"),
    NeedsConnection("needs_connection"),
    NoSpeech("no_speech"),
    Busy("busy"),
    Error("error"),
    ;

    companion object {
        fun of(failure: SpeechFailure): VoiceFallback = when (failure) {
            SpeechFailure.NoSpeech -> NoSpeech
            SpeechFailure.NoRecognizer -> NoRecognizer
            SpeechFailure.NoModel -> NoModel
            SpeechFailure.NoMicrophone -> NoMicrophone
            // Reported as permission_denied instead; this is never sent.
            SpeechFailure.NotAllowed -> Error
            SpeechFailure.NeedsConnection -> NeedsConnection
            SpeechFailure.Busy -> Busy
            SpeechFailure.Unknown -> Error
        }
    }
}

/**
 * Voice log's app events (`docs/practices/app-events.md`). Typed so no call
 * site can pass what was said: a transcript is a meal in someone's own words.
 */
interface VoiceTelemetry {
    /** [asked] when the system prompt was answered yes just before. */
    fun started(onDevice: Boolean, asked: Boolean)

    fun transcribed(words: WordBucket)

    fun permissionDenied(canAskAgain: Boolean)

    fun fallback(reason: VoiceFallback)
}

@ContributesBinding(AppScope::class)
@Inject
class AppEventsVoiceTelemetry : VoiceTelemetry {
    private val logger = KLog.withTag("Voice")

    override fun started(onDevice: Boolean, asked: Boolean) {
        logger.logEvent("voice.started", "on_device" to onDevice, "permission" to if (asked) "asked" else "granted")
    }

    override fun transcribed(words: WordBucket) {
        logger.logEvent("voice.transcribed", "words" to words.wire)
    }

    override fun permissionDenied(canAskAgain: Boolean) {
        logger.logEvent("voice.permission_denied", "can_ask_again" to canAskAgain)
    }

    override fun fallback(reason: VoiceFallback) {
        logger.logEvent("voice.fallback", "reason" to reason.wire)
    }
}
