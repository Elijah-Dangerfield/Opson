package com.opson.features.logger.impl

import com.opson.libraries.speech.Heard
import com.opson.libraries.speech.MicPermission
import com.opson.libraries.speech.SpeechFailure
import com.opson.libraries.speech.SpeechToText
import me.tatarka.inject.annotations.Inject

// Voice log in Describe it (docs/plans/screens/voice.md). The voice types live
// here rather than in LoggerState.kt: VoiceAction and RequestMicPermission
// extend the logger's sealed types from this file, which Kotlin allows within
// the package, so the logger's own files carry only the seams.

/** What the logger needs to listen: the recognizer, `release.voice`, and the events. */
@Inject
class DescribeVoice(
    val speech: SpeechToText,
    val enabled: VoiceLogEnabled,
    val telemetry: VoiceTelemetry,
)

/** Where a voice session stands, as the view model tracks it. */
sealed interface VoiceCapture {
    /** [afterSession] once a session has ended, so "Stopped listening" stays readable; [focusText] after "Type instead". */
    data class Idle(val afterSession: Boolean = false, val focusText: Boolean = false) : VoiceCapture

    /** The system prompt is up. */
    data object Asking : VoiceCapture

    /** [prefix] is what was already typed, kept in front of what is [heard]. */
    data class Listening(val prefix: String, val heard: String) : VoiceCapture

    /** The mic is off and the recognizer is settling on its final text. */
    data class Transcribing(val prefix: String, val heard: String) : VoiceCapture

    data class Notice(val notice: VoiceNotice) : VoiceCapture

    /** The prompt is up, or the mic is open or settling. */
    val busy: Boolean get() = this == Asking || this is Listening || this is Transcribing
}

/** Why voice stepped aside for typing. Each one offers "Type instead". */
sealed interface VoiceNotice {
    /** The microphone (on iOS, or speech recognition) was refused. [canAskAgain] asks again instead of opening Settings. */
    data class Denied(val canAskAgain: Boolean) : VoiceNotice

    /** No recognizer, or no model for the language. */
    data object Unavailable : VoiceNotice
    data object NeedsConnection : VoiceNotice
    data object NoSpeech : VoiceNotice
    data object Failed : VoiceNotice
}

enum class VoicePhase { Ready, Listening, Transcribing }

/** What the status line under the field says, as a live region, so TalkBack and VoiceOver read it out. */
enum class VoiceAnnouncement { Listening, Stopped }

/** The mic in the Describe box and what it says. Null in [DescribeUi.voice] when `release.voice` is off. */
data class VoiceUi(
    val phase: VoicePhase = VoicePhase.Ready,
    val notice: VoiceNotice? = null,
    /** Null shows the design's prompt, "Write it the way you'd say it". */
    val announcement: VoiceAnnouncement? = null,
    val focusText: Boolean = false,
)

object VoiceStateBuilder {
    fun build(enabled: Boolean, capture: VoiceCapture): VoiceUi? {
        if (!enabled) return null
        return when (capture) {
            is VoiceCapture.Idle -> VoiceUi(
                announcement = if (capture.afterSession) VoiceAnnouncement.Stopped else null,
                focusText = capture.focusText,
            )
            VoiceCapture.Asking -> VoiceUi()
            is VoiceCapture.Listening -> VoiceUi(phase = VoicePhase.Listening, announcement = VoiceAnnouncement.Listening)
            is VoiceCapture.Transcribing -> VoiceUi(phase = VoicePhase.Transcribing, announcement = VoiceAnnouncement.Stopped)
            // The notice is its own live region; the prompt comes back under the field.
            is VoiceCapture.Notice -> VoiceUi(notice = capture.notice)
        }
    }
}

/** The voice actions. Part of [LoggerAction], so the one view model takes them. */
sealed interface VoiceAction : LoggerAction {
    /** The mic, "Try again" and "Allow microphone". While listening, the same button stops. */
    data object MicTapped : VoiceAction
    data class MicPermissionAnswered(val permission: MicPermission) : VoiceAction
    data class SpeechArrived(val heard: Heard) : VoiceAction

    /** A pause long enough to mean done, nothing said at all, or the length cap. */
    data object WentQuiet : VoiceAction

    /** The recognizer has not settled long after the mic stopped. */
    data object TranscriptOverdue : VoiceAction
    data object TypeInsteadTapped : VoiceAction
    data object OpenSettingsTapped : VoiceAction

    /** The app went to the background, or something covered the sheet. */
    data object ScreenPaused : VoiceAction
}

/** Show the system microphone prompt; the answer comes back as [VoiceAction.MicPermissionAnswered]. */
data object RequestMicPermission : LoggerEvent

internal fun SpeechFailure.toNotice(): VoiceNotice = when (this) {
    SpeechFailure.NoRecognizer, SpeechFailure.NoModel, SpeechFailure.NoMicrophone -> VoiceNotice.Unavailable
    SpeechFailure.NotAllowed -> VoiceNotice.Denied(canAskAgain = false)
    SpeechFailure.NeedsConnection -> VoiceNotice.NeedsConnection
    SpeechFailure.NoSpeech -> VoiceNotice.NoSpeech
    SpeechFailure.Busy, SpeechFailure.Unknown -> VoiceNotice.Failed
}

/** How long a transcript was, coarsely. The only thing about it telemetry ever sees. */
enum class WordBucket(val wire: String) {
    UpTo3("1-3"),
    UpTo7("4-7"),
    UpTo15("8-15"),
    More("16+"),
    ;

    companion object {
        fun of(text: String): WordBucket = when (text.split(WHITESPACE).count { it.isNotEmpty() }) {
            in 0..3 -> UpTo3
            in 4..7 -> UpTo7
            in 8..15 -> UpTo15
            else -> More
        }

        private val WHITESPACE = Regex("\\s+")
    }
}
