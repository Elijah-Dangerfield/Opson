package com.opson.features.settings.impl.pause

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.inline
import com.opson.features.settings.impl.label
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.chip.PillChipStyle
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.confirm
import com.opson.libraries.ui.system.minTouchTarget
import com.opson.system.AppTheme
import kotlinx.datetime.LocalTime
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_pause_body_number
import opson.libraries.resources.generated.resources.settings_pause_body_pause
import opson.libraries.resources.generated.resources.settings_pause_body_preset
import opson.libraries.resources.generated.resources.settings_pause_body_reveal
import opson.libraries.resources.generated.resources.settings_pause_keep
import opson.libraries.resources.generated.resources.settings_pause_ready_number
import opson.libraries.resources.generated.resources.settings_pause_ready_pause
import opson.libraries.resources.generated.resources.settings_pause_ready_preset
import opson.libraries.resources.generated.resources.settings_pause_ready_reveal
import opson.libraries.resources.generated.resources.settings_pause_reason_anxious
import opson.libraries.resources.generated.resources.settings_pause_reason_curious
import opson.libraries.resources.generated.resources.settings_pause_reason_else
import opson.libraries.resources.generated.resources.settings_pause_reason_planning
import opson.libraries.resources.generated.resources.settings_pause_reason_prompt
import opson.libraries.resources.generated.resources.settings_pause_reason_provider
import opson.libraries.resources.generated.resources.settings_pause_talk
import opson.libraries.resources.generated.resources.settings_pause_title_number
import opson.libraries.resources.generated.resources.settings_modes_photos
import opson.libraries.resources.generated.resources.settings_modes_photos_inline
import opson.libraries.resources.generated.resources.settings_pause_title_pause
import opson.libraries.resources.generated.resources.settings_pause_title_preset
import opson.libraries.resources.generated.resources.settings_pause_title_reveal
import opson.libraries.resources.generated.resources.settings_pause_wait_number
import opson.libraries.resources.generated.resources.settings_pause_wait_pause
import opson.libraries.resources.generated.resources.settings_pause_wait_preset
import opson.libraries.resources.generated.resources.settings_pause_wait_reveal
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The pause sheet's body, apart from the `BottomSheet` so previews and the leak test can draw it.
 *
 * Design v3: "Keep things as they are" is the primary button, and the countdown is the quiet
 * one, filling in as the wait runs down.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PauseSheetContent(
    state: PauseState,
    onAction: (PauseAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = copyFor(state.subject, countdown = state.countdown.takeIf { state.secondsLeft > 0 })
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = 10.dp, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Text(text = copy.title, typography = AppTheme.type.dayLabel, color = AppTheme.colors.ink)
            Text(text = copy.body, typography = AppTheme.type.body, color = AppTheme.colors.muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.settings_pause_reason_prompt),
                typography = AppTheme.type.secondaryStrong,
                color = AppTheme.colors.muted,
            )
            // The chips' 48 dp tap areas sit edge to edge, which draws the gap between rows.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                PauseReason.entries.forEach { reason ->
                    PillChip(
                        label = stringResource(reason.label),
                        selected = state.reason == reason,
                        onClick = { onAction(PauseAction.ReasonPicked(reason)) },
                        style = PillChipStyle.Tint,
                    )
                }
            }
        }
        if (state.talkToSomeone) {
            TalkToSomeone(onClick = { onAction(PauseAction.TalkToSomeoneTapped) })
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            FlatButton(
                text = stringResource(Res.string.settings_pause_keep),
                onClick = { onAction(PauseAction.KeepAsTheyAreTapped) },
                tone = FlatButtonTone.Accent,
                centered = true,
                minHeight = FlatButtonDefaults.TallMinHeight,
                typography = AppTheme.type.button,
                modifier = Modifier.fillMaxWidth(),
            )
            FlatButton(
                text = copy.button,
                // Enabled only once the wait is over, so this plays only when the change applies.
                onClick = {
                    haptics.confirm()
                    onAction(PauseAction.ShowThemTapped)
                },
                tone = FlatButtonTone.Soft,
                centered = true,
                enabled = state.canShow,
                progress = if (state.secondsLeft > 0) state.waited else null,
                minHeight = FlatButtonDefaults.TallMinHeight,
                typography = AppTheme.type.button.Tabular,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Design sync 2: a quiet accent link to Find support, above the buttons. */
@Composable
private fun TalkToSomeone(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .minTouchTarget()
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.97f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon = AppIcons.SupportAgent, size = 22.dp, color = AppTheme.colors.accent)
        Text(text = stringResource(Res.string.settings_pause_talk), typography = AppTheme.type.bodyBold.ExtraBold, color = AppTheme.colors.accent)
    }
}

/** The sheet's words for one subject. [button] says the wait while [countdown] runs, then what applies. */
private class PauseCopy(val title: String, val body: String, val button: String)

@Composable
private fun numberCopy(inlineLabel: StringResource, label: StringResource, countdown: String?): PauseCopy {
    val inline = stringResource(inlineLabel)
    return PauseCopy(
        title = stringResource(Res.string.settings_pause_title_number, inline),
        body = stringResource(Res.string.settings_pause_body_number, stringResource(label)),
        button = if (countdown != null) {
            stringResource(Res.string.settings_pause_wait_number, inline, countdown)
        } else {
            stringResource(Res.string.settings_pause_ready_number, inline)
        },
    )
}

@Composable
private fun copyFor(subject: PauseSubject, countdown: String?): PauseCopy = when (subject) {
    is PauseSubject.Number -> numberCopy(subject.metric.inline, subject.metric.label, countdown)
    // Photos read like a number in the sheet: "Showing progress photos again".
    PauseSubject.ProgressPhotos -> numberCopy(Res.string.settings_modes_photos_inline, Res.string.settings_modes_photos, countdown)
    is PauseSubject.Preset -> {
        val label = stringResource(subject.id.label)
        PauseCopy(
            title = stringResource(Res.string.settings_pause_title_preset, label),
            body = stringResource(Res.string.settings_pause_body_preset, label),
            button = if (countdown != null) {
                stringResource(Res.string.settings_pause_wait_preset, countdown)
            } else {
                stringResource(Res.string.settings_pause_ready_preset, label)
            },
        )
    }
    PauseSubject.ThePause -> PauseCopy(
        title = stringResource(Res.string.settings_pause_title_pause),
        body = stringResource(Res.string.settings_pause_body_pause),
        button = if (countdown != null) {
            stringResource(Res.string.settings_pause_wait_pause, countdown)
        } else {
            stringResource(Res.string.settings_pause_ready_pause)
        },
    )
    is PauseSubject.RevealTime -> PauseCopy(
        title = stringResource(Res.string.settings_pause_title_reveal),
        body = stringResource(Res.string.settings_pause_body_reveal, subject.text),
        button = if (countdown != null) {
            stringResource(Res.string.settings_pause_wait_reveal, countdown)
        } else {
            stringResource(Res.string.settings_pause_ready_reveal, subject.text)
        },
    )
}

private val PauseReason.label: StringResource
    get() = when (this) {
        PauseReason.Curious -> Res.string.settings_pause_reason_curious
        PauseReason.PlanningAMeal -> Res.string.settings_pause_reason_planning
        PauseReason.ProviderAsked -> Res.string.settings_pause_reason_provider
        PauseReason.FeelingAnxious -> Res.string.settings_pause_reason_anxious
        PauseReason.SomethingElse -> Res.string.settings_pause_reason_else
    }

@Preview
@Composable
private fun ModesPausePreview() {
    SettingsPreview {
        PauseSheetContent(
            PauseState(
                subject = PauseSubject.Number(Metric.Calories),
                secondsLeft = 7,
                reason = PauseReason.PlanningAMeal,
                talkToSomeone = true,
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun ModesPauseReadyDarkPreview() {
    SettingsPreview(dark = true) {
        PauseSheetContent(PauseState(subject = PauseSubject.Preset(PresetId.Full), secondsLeft = 0), onAction = {})
    }
}

@Preview
@Composable
private fun ModesPauseTurningItOffPreview() {
    SettingsPreview {
        PauseSheetContent(PauseState(subject = PauseSubject.ThePause, secondsLeft = 3), onAction = {})
    }
}

/** Weight back to Numbers from Trend only: the Weight screen's old switch, now in Modes. */
@Preview
@Composable
private fun ModesPauseWeightPreview() {
    SettingsPreview {
        PauseSheetContent(PauseState(subject = PauseSubject.Number(Metric.Weight), secondsLeft = 4, talkToSomeone = true), onAction = {})
    }
}

@Preview
@Composable
private fun ModesPauseRevealEarlierPreview() {
    SettingsPreview {
        PauseSheetContent(PauseState(subject = PauseSubject.RevealTime(LocalTime(19, 30)), secondsLeft = 5), onAction = {})
    }
}
