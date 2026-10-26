package com.opson.features.settings.impl.pause

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.features.settings.impl.LightAndDark
import com.opson.features.settings.impl.label
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_pause_body
import opson.libraries.resources.generated.resources.settings_pause_body_pause
import opson.libraries.resources.generated.resources.settings_pause_keep
import opson.libraries.resources.generated.resources.settings_pause_reason_asked
import opson.libraries.resources.generated.resources.settings_pause_reason_curious
import opson.libraries.resources.generated.resources.settings_pause_reason_goal
import opson.libraries.resources.generated.resources.settings_pause_reason_prompt
import opson.libraries.resources.generated.resources.settings_pause_reason_worried
import opson.libraries.resources.generated.resources.settings_pause_show
import opson.libraries.resources.generated.resources.settings_pause_title
import opson.libraries.resources.generated.resources.settings_pause_wait
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The pause sheet's body, apart from the `BottomSheet` so previews and the leak test can draw it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PauseSheetContent(
    state: PauseState,
    onAction: (PauseAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            text = stringResource(Res.string.settings_pause_title),
            typography = AppTheme.type.sheetTitle,
            color = AppTheme.colors.ink,
        )
        Text(text = body(state.subject), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            Text(
                text = stringResource(Res.string.settings_pause_reason_prompt),
                typography = AppTheme.type.secondarySmallStrong,
                color = AppTheme.colors.muted,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
            ) {
                PauseReason.entries.forEach { reason ->
                    PillChip(
                        label = stringResource(reason.label),
                        selected = state.reason == reason,
                        onClick = { onAction(PauseAction.ReasonPicked(reason)) },
                    )
                }
            }
        }
        FlatButton(
            text = if (state.secondsLeft > 0) {
                stringResource(Res.string.settings_pause_wait, state.secondsLeft)
            } else {
                stringResource(Res.string.settings_pause_show)
            },
            onClick = { onAction(PauseAction.ShowThemTapped) },
            tone = FlatButtonTone.Ink,
            centered = true,
            enabled = state.canShow,
            minHeight = FlatButtonDefaults.TallMinHeight,
            typography = AppTheme.type.button.Tabular,
            modifier = Modifier.fillMaxWidth(),
        )
        FlatButton(
            text = stringResource(Res.string.settings_pause_keep),
            onClick = { onAction(PauseAction.KeepAsTheyAreTapped) },
            centered = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun body(subject: PauseSubject): String = when (subject) {
    is PauseSubject.Preset -> stringResource(Res.string.settings_pause_body, stringResource(subject.id.label))
    is PauseSubject.Number -> stringResource(Res.string.settings_pause_body, stringResource(subject.metric.label))
    PauseSubject.ThePause -> stringResource(Res.string.settings_pause_body_pause)
    is PauseSubject.RevealTime -> stringResource(Res.string.settings_pause_body_pause)
}

private val PauseReason.label: StringResource
    get() = when (this) {
        PauseReason.Curious -> Res.string.settings_pause_reason_curious
        PauseReason.WorkingTowardAGoal -> Res.string.settings_pause_reason_goal
        PauseReason.WorriedOffTrack -> Res.string.settings_pause_reason_worried
        PauseReason.SomeoneAsked -> Res.string.settings_pause_reason_asked
    }

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun PauseFromBlindPreview() {
    LightAndDark {
        PauseSheetContent(PauseState(subject = PauseSubject.Preset(PresetId.Full), secondsLeft = 7), onAction = {})
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun PauseReadyPreview() {
    LightAndDark {
        PauseSheetContent(
            PauseState(subject = PauseSubject.Number(Metric.Water), secondsLeft = 0, reason = PauseReason.Curious),
            onAction = {},
        )
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun PauseTurningItOffPreview() {
    LightAndDark { PauseSheetContent(PauseState(subject = PauseSubject.ThePause, secondsLeft = 3), onAction = {}) }
}
