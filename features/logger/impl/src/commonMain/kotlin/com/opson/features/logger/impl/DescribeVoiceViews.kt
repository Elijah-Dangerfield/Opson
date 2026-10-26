package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_describe_prompt
import opson.libraries.resources.generated.resources.voice_allow
import opson.libraries.resources.generated.resources.voice_denied
import opson.libraries.resources.generated.resources.voice_denied_ask
import opson.libraries.resources.generated.resources.voice_failed
import opson.libraries.resources.generated.resources.voice_listening
import opson.libraries.resources.generated.resources.voice_mic_cd
import opson.libraries.resources.generated.resources.voice_needs_connection
import opson.libraries.resources.generated.resources.voice_no_speech
import opson.libraries.resources.generated.resources.voice_open_settings
import opson.libraries.resources.generated.resources.voice_stop_cd
import opson.libraries.resources.generated.resources.voice_stopped
import opson.libraries.resources.generated.resources.voice_try_again
import opson.libraries.resources.generated.resources.voice_type_instead
import opson.libraries.resources.generated.resources.voice_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * The line under the Describe field: the design's prompt, or what the mic is
 * doing. While it says anything about the mic it is a live region, so TalkBack
 * and VoiceOver read "Listening" when it starts and "Stopped listening" when
 * it ends; the prompt coming back is not read out.
 */
@Composable
internal fun DescribePromptLine(voice: VoiceUi?, modifier: Modifier = Modifier) {
    val announcement = voice?.announcement
    val text = when (announcement) {
        null -> stringResource(Res.string.logger_describe_prompt)
        VoiceAnnouncement.Listening -> stringResource(Res.string.voice_listening)
        VoiceAnnouncement.Stopped -> stringResource(Res.string.voice_stopped)
    }
    Text(
        text = text,
        typography = AppTheme.type.secondary,
        color = if (announcement == VoiceAnnouncement.Listening) AppTheme.colors.accent else AppTheme.colors.muted,
        modifier = if (announcement != null) modifier.semantics { liveRegion = LiveRegionMode.Polite } else modifier,
    )
}

/** The round mic from design v3, bottom right of the Describe box. A filled stop square while it listens. */
@Composable
internal fun MicButton(voice: VoiceUi, onAction: (LoggerAction) -> Unit) {
    val listening = voice.phase == VoicePhase.Listening
    RoundIconButton(
        icon = if (listening) {
            AppIcons.Stop.described(stringResource(Res.string.voice_stop_cd))
        } else {
            AppIcons.Mic.described(stringResource(Res.string.voice_mic_cd))
        },
        onClick = { onAction(VoiceAction.MicTapped) },
        selected = listening,
        size = AppTheme.spacing.touchTarget,
        // Settling on the last words: one more tap would do nothing, so it says so.
        enabled = voice.phase != VoicePhase.Transcribing,
    )
}

/** Why voice stepped aside, with the way back to typing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VoiceNoticeCard(notice: VoiceNotice, onAction: (LoggerAction) -> Unit) {
    val message = when (notice) {
        is VoiceNotice.Denied -> if (notice.canAskAgain) Res.string.voice_denied_ask else Res.string.voice_denied
        VoiceNotice.Unavailable -> Res.string.voice_unavailable
        VoiceNotice.NeedsConnection -> Res.string.voice_needs_connection
        VoiceNotice.NoSpeech -> Res.string.voice_no_speech
        VoiceNotice.Failed -> Res.string.voice_failed
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.inputLarge)
            .background(AppTheme.colors.soft)
            .padding(horizontal = AppTheme.spacing.section, vertical = AppTheme.spacing.row),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Text(
            text = stringResource(message),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.ink,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
        ) {
            when (notice) {
                is VoiceNotice.Denied -> if (notice.canAskAgain) {
                    NoticeButton(stringResource(Res.string.voice_allow)) { onAction(VoiceAction.MicTapped) }
                } else {
                    NoticeButton(stringResource(Res.string.voice_open_settings)) { onAction(VoiceAction.OpenSettingsTapped) }
                }
                VoiceNotice.NoSpeech, VoiceNotice.Failed ->
                    NoticeButton(stringResource(Res.string.voice_try_again)) { onAction(VoiceAction.MicTapped) }
                VoiceNotice.Unavailable, VoiceNotice.NeedsConnection -> Unit
            }
            ButtonGhost(onClick = { onAction(VoiceAction.TypeInsteadTapped) }, size = ButtonSize.Medium) {
                Text(stringResource(Res.string.voice_type_instead))
            }
        }
    }
}

@Composable
private fun NoticeButton(label: String, onClick: () -> Unit) {
    ButtonSecondary(onClick = onClick, size = ButtonSize.Medium) { Text(label) }
}
