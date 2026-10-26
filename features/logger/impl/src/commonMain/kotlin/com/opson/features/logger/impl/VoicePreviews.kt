package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.dialog.bottomsheet.SheetFrame
import org.jetbrains.compose.ui.tooling.preview.Preview

// Describe it with release.voice on, from VoiceFixtures through the real builder. With the flag
// off the pane is LoggerDescribePreview and LoggerDescribeEmptyPreview, unchanged from 0.1.

private val full = previewVisibilityPolicy(PresetId.Full)
private val blind = previewVisibilityPolicy(PresetId.Blind)

@Composable
private fun Describe(state: LoggerState, dark: Boolean = false) = PreviewContent(darkTheme = dark) {
    SheetFrame { LoggerSheetContent(state, onAction = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerVoiceIdlePreview() = Describe(VoiceFixtures.describeState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerVoiceListeningPreview() = Describe(VoiceFixtures.listening(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerVoiceListeningDarkPreview() = Describe(VoiceFixtures.listening(full), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerVoiceMoreLinesPreview() = Describe(VoiceFixtures.listeningOverLines(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerVoiceBlindPreview() = Describe(VoiceFixtures.listeningOverLines(blind))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerVoiceTranscribingPreview() = Describe(VoiceFixtures.transcribing(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerVoiceDeniedPreview() = Describe(VoiceFixtures.notice(full, VoiceNotice.Denied(canAskAgain = false)))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerVoiceDeniedDarkPreview() =
    Describe(VoiceFixtures.notice(full, VoiceNotice.Denied(canAskAgain = false)), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerVoiceUnavailablePreview() = Describe(VoiceFixtures.notice(full, VoiceNotice.Unavailable))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerVoiceNoSpeechPreview() = Describe(VoiceFixtures.notice(full, VoiceNotice.NoSpeech))
