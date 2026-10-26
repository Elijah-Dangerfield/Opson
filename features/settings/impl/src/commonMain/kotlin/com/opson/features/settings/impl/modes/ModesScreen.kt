package com.opson.features.settings.impl.modes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SectionLabel
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsDefaults
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.SettingsRow
import com.opson.features.settings.impl.label
import com.opson.features.settings.impl.line
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.dialog.Dialog
import com.opson.libraries.ui.components.radio.RadioMatrix
import com.opson.libraries.ui.components.radio.RadioMatrixRow
import com.opson.libraries.ui.components.radio.RadioRow
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import kotlinx.datetime.LocalTime
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_back_more
import opson.libraries.resources.generated.resources.settings_modes_cell
import opson.libraries.resources.generated.resources.settings_modes_changed
import opson.libraries.resources.generated.resources.settings_modes_changed_from
import opson.libraries.resources.generated.resources.settings_modes_changes
import opson.libraries.resources.generated.resources.settings_modes_footnote
import opson.libraries.resources.generated.resources.settings_modes_intro
import opson.libraries.resources.generated.resources.settings_modes_matrix_header
import opson.libraries.resources.generated.resources.settings_modes_pause_body
import opson.libraries.resources.generated.resources.settings_modes_pause_title
import opson.libraries.resources.generated.resources.settings_modes_presets_header
import opson.libraries.resources.generated.resources.settings_modes_reveal_body
import opson.libraries.resources.generated.resources.settings_modes_reveal_cancel
import opson.libraries.resources.generated.resources.settings_modes_reveal_ok
import opson.libraries.resources.generated.resources.settings_modes_reveal_title
import opson.libraries.resources.generated.resources.settings_modes_setup
import opson.libraries.resources.generated.resources.settings_modes_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ModesScreen(
    state: ModesState,
    onAction: (ModesAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.settings_modes_title),
            backLabel = stringResource(Res.string.settings_back_more),
            onBack = { onAction(ModesAction.BackTapped) },
        )
        Text(
            text = stringResource(Res.string.settings_modes_intro),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.ink,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        SectionLabel(stringResource(Res.string.settings_modes_presets_header))
        SettingsCard {
            state.presets.forEachIndexed { index, row ->
                RadioRow(
                    title = stringResource(row.id.label),
                    subtitle = stringResource(row.id.line),
                    selected = row.selected,
                    onClick = { onAction(ModesAction.PresetTapped(row.id)) },
                    showDivider = index < state.presets.lastIndex,
                    badge = if (row.changes > 0) {
                        pluralStringResource(Res.plurals.settings_modes_changes, row.changes, row.changes)
                    } else {
                        null
                    },
                )
            }
        }
        SectionLabel(stringResource(Res.string.settings_modes_matrix_header))
        LevelMatrix(state.matrix, onAction)
        PauseCard(state.pauseOn, onAction)
        SettingsCard {
            SettingsRow(
                title = stringResource(Res.string.settings_modes_reveal_title),
                subtitle = stringResource(Res.string.settings_modes_reveal_body),
                icon = null,
                onClick = { onAction(ModesAction.RevealTimeTapped) },
                trailing = {
                    Text(text = state.revealAtText, typography = AppTheme.type.numeric, color = AppTheme.colors.accent)
                },
            )
        }
        Text(
            text = when (val note = state.changedNote) {
                ChangedNote.DuringSetup -> stringResource(Res.string.settings_modes_setup)
                is ChangedNote.LastChanged -> stringResource(Res.string.settings_modes_changed, note.timestamp)
            },
            typography = AppTheme.type.secondarySmall,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    if (state.pickingRevealTime) {
        RevealTimeDialog(
            initial = state.revealAt,
            onPicked = { onAction(ModesAction.RevealTimePicked(it)) },
            onDismiss = { onAction(ModesAction.RevealTimeDismissed) },
        )
    }
}

@Composable
private fun LevelMatrix(matrix: List<MatrixRow>, onAction: (ModesAction) -> Unit) {
    val levels = ModesStateBuilder.levels
    val levelLabels = levels.map { stringResource(it.label) }
    val rows = matrix.map { row ->
        val metric = stringResource(row.metric.label)
        RadioMatrixRow(
            label = metric,
            selectedColumn = levels.indexOf(row.level),
            cellDescriptions = levelLabels.map { stringResource(Res.string.settings_modes_cell, metric, it) },
            note = row.changedFrom?.let { stringResource(Res.string.settings_modes_changed_from, stringResource(it.label)) },
            highlighted = row.changedFrom != null,
        )
    }
    EdgeCard(
        edgeToEdge = false,
        contentPadding = PaddingValues(start = SettingsDefaults.CardPadding, end = SettingsDefaults.CardPadding, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        RadioMatrix(
            header = "",
            columns = levelLabels,
            rows = rows,
            onSelect = { row, column -> onAction(ModesAction.LevelTapped(matrix[row].metric, levels[column])) },
            highlightBleed = SettingsDefaults.CardPadding,
        )
        Text(
            text = stringResource(Res.string.settings_modes_footnote),
            typography = AppTheme.type.secondarySmall,
            color = AppTheme.colors.muted,
        )
    }
}

@Composable
private fun PauseCard(pauseOn: Boolean, onAction: (ModesAction) -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = SettingsDefaults.CardInset) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.settings_modes_pause_title),
                    typography = AppTheme.type.cardTitle,
                    color = AppTheme.colors.ink,
                )
                Text(
                    text = stringResource(Res.string.settings_modes_pause_body),
                    typography = AppTheme.type.secondarySmall,
                    color = AppTheme.colors.muted,
                )
            }
            AppSwitch(checked = pauseOn, onCheckedChange = { onAction(ModesAction.PauseToggled) })
        }
    }
}

/**
 * When Later numbers open. The picker's dial is the platform's Material time picker; only the
 * confirmed time leaves this dialog, as one action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RevealTimeDialog(initial: LocalTime, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.bg, SettingsDefaults.DialogShape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.settings_modes_reveal_title),
                typography = AppTheme.type.sheetTitle,
                color = AppTheme.colors.ink,
                modifier = Modifier.fillMaxWidth(),
            )
            RevealPicker(picker)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip, Alignment.End),
            ) {
                FlatButton(
                    text = stringResource(Res.string.settings_modes_reveal_cancel),
                    onClick = onDismiss,
                    tone = FlatButtonTone.Text,
                    centered = true,
                )
                FlatButton(
                    text = stringResource(Res.string.settings_modes_reveal_ok),
                    onClick = { onPicked(LocalTime(picker.hour, picker.minute)) },
                    tone = FlatButtonTone.AccentText,
                    centered = true,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RevealPicker(picker: TimePickerState) {
    val colors = AppTheme.colors
    // AppTheme paints raw Material red on purpose, so the one Material component here
    // gets a scheme built from the app's own colours.
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = colors.accent.color,
            onPrimary = colors.onAccent.color,
            primaryContainer = colors.accentTint.color,
            onPrimaryContainer = colors.accent.color,
            tertiaryContainer = colors.accentTint.color,
            onTertiaryContainer = colors.accent.color,
            surface = colors.bg.color,
            onSurface = colors.ink.color,
            onSurfaceVariant = colors.muted.color,
            surfaceContainerHighest = colors.soft.color,
            outline = colors.line.color,
        ),
        typography = Typography(),
    ) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle.Default) {
            TimePicker(
                state = picker,
                colors = TimePickerDefaults.colors(
                    clockDialColor = colors.soft.color,
                    clockDialSelectedContentColor = colors.onAccent.color,
                    clockDialUnselectedContentColor = colors.ink.color,
                    selectorColor = colors.accent.color,
                    containerColor = colors.bg.color,
                    periodSelectorBorderColor = colors.line.color,
                    periodSelectorSelectedContainerColor = colors.accentTint.color,
                    periodSelectorUnselectedContainerColor = colors.bg.color,
                    periodSelectorSelectedContentColor = colors.accent.color,
                    periodSelectorUnselectedContentColor = colors.muted.color,
                    timeSelectorSelectedContainerColor = colors.accentTint.color,
                    timeSelectorUnselectedContainerColor = colors.soft.color,
                    timeSelectorSelectedContentColor = colors.accent.color,
                    timeSelectorUnselectedContentColor = colors.ink.color,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun ModesPreview() {
    SettingsPreview {
        ModesScreen(ModesStateBuilder.build(SettingsFixtures.fuelWithACarbsChange, SettingsFixtures.timeZone), onAction = {})
    }
}

@Preview
@Composable
private fun ModesFullDarkPreview() {
    SettingsPreview(dark = true) {
        ModesScreen(ModesStateBuilder.build(NumbersSettings(), SettingsFixtures.timeZone), onAction = {})
    }
}

@Preview
@Composable
private fun ModesBlindPausedPreview() {
    val blind = NumbersSettings(preset = PresetId.Blind, pauseBeforeMore = true)
    SettingsPreview { ModesScreen(ModesStateBuilder.build(blind, SettingsFixtures.timeZone), onAction = {}) }
}

@Preview
@Composable
private fun ModesRevealTimePreview() {
    SettingsPreview {
        ModesScreen(
            ModesStateBuilder.build(NumbersSettings(preset = PresetId.Delayed), SettingsFixtures.timeZone, pickingRevealTime = true),
            onAction = {},
        )
    }
}
