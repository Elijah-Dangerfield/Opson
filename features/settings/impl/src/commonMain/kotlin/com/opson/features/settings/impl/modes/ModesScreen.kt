package com.opson.features.settings.impl.modes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import com.opson.libraries.nutrition.ModeTiming
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.dialog.TimePickerDialog
import com.opson.libraries.ui.components.radio.RadioMatrix
import com.opson.libraries.ui.components.radio.RadioMatrixRow
import com.opson.libraries.ui.components.radio.RadioRow
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.modes_back_modes
import opson.libraries.resources.generated.resources.modes_each_number_body
import opson.libraries.resources.generated.resources.modes_each_number_title
import opson.libraries.resources.generated.resources.modes_end_after_ends
import opson.libraries.resources.generated.resources.modes_end_after_length
import opson.libraries.resources.generated.resources.modes_end_after_off
import opson.libraries.resources.generated.resources.modes_end_after_on
import opson.libraries.resources.generated.resources.modes_end_after_then
import opson.libraries.resources.generated.resources.modes_end_after_title
import opson.libraries.resources.generated.resources.modes_reveal_weekly_body
import opson.libraries.resources.generated.resources.modes_reveal_weekly_title
import opson.libraries.resources.generated.resources.modes_weeks
import opson.libraries.resources.generated.resources.settings_back_more
import opson.libraries.resources.generated.resources.settings_modes_cell
import opson.libraries.resources.generated.resources.settings_modes_changed
import opson.libraries.resources.generated.resources.settings_modes_changed_from
import opson.libraries.resources.generated.resources.settings_modes_changes
import opson.libraries.resources.generated.resources.settings_modes_footnote
import opson.libraries.resources.generated.resources.settings_modes_pause_body
import opson.libraries.resources.generated.resources.settings_modes_pause_title
import opson.libraries.resources.generated.resources.settings_modes_photos
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

/**
 * Modes and numbers (design sync 4, 13.5a): the four starting points, then End after a set time,
 * Each number and the pause, and who last changed it. The reveal time shows only while something
 * is set to Later.
 */
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
        SettingsCard {
            EndAfter(state.endAfter, onAction)
            HorizontalDivider(color = AppTheme.colors.line)
            SettingsRow(
                title = stringResource(Res.string.modes_each_number_title),
                subtitle = stringResource(Res.string.modes_each_number_body),
                icon = null,
                chevron = true,
                showDivider = true,
                onClick = { onAction(ModesAction.EachNumberTapped) },
            )
            SettingsRow(
                title = stringResource(Res.string.settings_modes_pause_title),
                subtitle = stringResource(Res.string.settings_modes_pause_body),
                icon = null,
                onClick = { onAction(ModesAction.PauseToggled) },
                trailing = { AppSwitch(checked = state.pauseOn, onCheckedChange = { onAction(ModesAction.PauseToggled) }) },
            )
        }
        state.reveal?.let { reveal -> RevealCard(reveal, onAction) }
        ChangedNoteText(state.changedNote)
    }
    val reveal = state.reveal
    if (state.pickingRevealTime && reveal != null) {
        TimePickerDialog(
            title = stringResource(Res.string.settings_modes_reveal_title),
            initial = reveal.time,
            okLabel = stringResource(Res.string.settings_modes_reveal_ok),
            cancelLabel = stringResource(Res.string.settings_modes_reveal_cancel),
            onPicked = { onAction(ModesAction.RevealTimePicked(it)) },
            onDismiss = { onAction(ModesAction.RevealTimeDismissed) },
        )
    }
}

/** The per-metric matrix, its own page since design sync 4 ("Each number ›"). */
@Composable
fun EachNumberScreen(
    state: ModesState,
    onAction: (ModesAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.modes_each_number_title),
            backLabel = stringResource(Res.string.modes_back_modes),
            onBack = { onAction(ModesAction.BackTapped) },
        )
        LevelMatrix(state.matrix, state.photos, onAction)
        ChangedNoteText(state.changedNote)
    }
}

/** "End after a set time": the switch, and while [EndAfterRow.editing], how long and what comes next. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EndAfter(row: EndAfterRow, onAction: (ModesAction) -> Unit) {
    val timing = row.timing
    SettingsRow(
        title = stringResource(Res.string.modes_end_after_title),
        subtitle = if (timing != null) {
            stringResource(
                Res.string.modes_end_after_on,
                pluralStringResource(Res.plurals.modes_weeks, timing.weeks, timing.weeks),
                stringResource(timing.then.label),
            )
        } else {
            stringResource(Res.string.modes_end_after_off)
        },
        icon = null,
        onClick = { onAction(if (timing != null) ModesAction.EndAfterTapped else ModesAction.EndAfterToggled) },
        trailing = { AppSwitch(checked = timing != null, onCheckedChange = { onAction(ModesAction.EndAfterToggled) }) },
    )
    if (timing == null || !row.editing) return
    Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(Res.string.modes_end_after_length), typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            ModeTiming.LENGTHS.forEach { weeks ->
                PillChip(
                    label = pluralStringResource(Res.plurals.modes_weeks, weeks, weeks),
                    selected = weeks == timing.weeks,
                    onClick = { onAction(ModesAction.EndAfterWeeksPicked(weeks)) },
                )
            }
        }
        Text(
            text = stringResource(Res.string.modes_end_after_then),
            typography = AppTheme.type.secondarySmallStrong,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(top = 6.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            row.thenChoices.forEach { preset ->
                PillChip(
                    label = stringResource(preset.label),
                    selected = preset == timing.then,
                    onClick = { onAction(ModesAction.EndAfterThenPicked(preset)) },
                )
            }
        }
        row.endsOnText?.let {
            Text(
                text = stringResource(Res.string.modes_end_after_ends, it),
                typography = AppTheme.type.secondarySmall,
                color = AppTheme.colors.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** When Later's totals open: the time, and once a week instead of each day. */
@Composable
private fun RevealCard(reveal: RevealRow, onAction: (ModesAction) -> Unit) {
    SettingsCard {
        SettingsRow(
            title = stringResource(Res.string.settings_modes_reveal_title),
            subtitle = stringResource(Res.string.settings_modes_reveal_body),
            icon = null,
            showDivider = true,
            onClick = { onAction(ModesAction.RevealTimeTapped) },
            trailing = { Text(text = reveal.timeText, typography = AppTheme.type.numeric, color = AppTheme.colors.accent) },
        )
        SettingsRow(
            title = stringResource(Res.string.modes_reveal_weekly_title),
            subtitle = stringResource(Res.string.modes_reveal_weekly_body),
            icon = null,
            onClick = { onAction(ModesAction.RevealWeeklyToggled) },
            trailing = { AppSwitch(checked = reveal.weekly, onCheckedChange = { onAction(ModesAction.RevealWeeklyToggled) }) },
        )
    }
}

@Composable
private fun ChangedNoteText(note: ChangedNote) {
    Text(
        text = when (note) {
            ChangedNote.DuringSetup -> stringResource(Res.string.settings_modes_setup)
            is ChangedNote.LastChanged -> stringResource(Res.string.settings_modes_changed, note.timestamp)
        },
        typography = AppTheme.type.secondarySmall,
        color = AppTheme.colors.muted,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun LevelMatrix(matrix: List<MatrixRow>, photos: PhotosRow?, onAction: (ModesAction) -> Unit) {
    val levels = ModesStateBuilder.levels
    val levelLabels = levels.map { stringResource(it.label) }
    val metricRows = matrix.map { row ->
        val metric = stringResource(row.metric.label)
        RadioMatrixRow(
            label = metric,
            selectedColumn = levels.indexOf(row.level),
            cellDescriptions = levelLabels.map { stringResource(Res.string.settings_modes_cell, metric, it) },
            note = row.changedFrom?.let { stringResource(Res.string.settings_modes_changed_from, stringResource(it.label)) },
            highlighted = row.changedFrom != null,
        )
    }
    val photosRow = photos?.let { row ->
        val label = stringResource(Res.string.settings_modes_photos)
        val from = row.changedFrom?.let { if (it) Visibility.Show else Visibility.Hide }
        RadioMatrixRow(
            label = label,
            selectedColumn = levels.indexOf(row.level),
            cellDescriptions = levelLabels.map { stringResource(Res.string.settings_modes_cell, label, it) },
            note = from?.let { stringResource(Res.string.settings_modes_changed_from, stringResource(it.label)) },
            highlighted = from != null,
            disabledColumns = levels.indices.filterNot { levels[it] in ModesStateBuilder.photoLevels }.toSet(),
        )
    }
    val rows = metricRows + listOfNotNull(photosRow)
    EdgeCard(
        edgeToEdge = false,
        contentPadding = PaddingValues(start = SettingsDefaults.CardPadding, end = SettingsDefaults.CardPadding, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        RadioMatrix(
            header = "",
            columns = levelLabels,
            rows = rows,
            onSelect = { row, column ->
                val action = if (row < matrix.size) {
                    ModesAction.LevelTapped(matrix[row].metric, levels[column])
                } else {
                    ModesAction.PhotosTapped(shown = levels[column] == Visibility.Show)
                }
                onAction(action)
            },
            highlightBleed = SettingsDefaults.CardPadding,
        )
        Text(
            text = stringResource(Res.string.settings_modes_footnote),
            typography = AppTheme.type.secondarySmall,
            color = AppTheme.colors.muted,
        )
    }
}

/** The design's page (13.5a): All numbers for two weeks, then No numbers, with the pause on. */
@Preview
@Composable
private fun ModesV4Preview() {
    SettingsPreview(dark = true) {
        ModesScreen(ModesStateBuilder.build(SettingsFixtures.fullForTwoWeeks, SettingsFixtures.timeZone), onAction = {})
    }
}

/** End after a set time open: how long, and what comes next. */
@Preview
@Composable
private fun ModesEndAfterPreview() {
    SettingsPreview {
        ModesScreen(ModesStateBuilder.build(SettingsFixtures.fullForTwoWeeks, SettingsFixtures.timeZone, editingEndAfter = true), onAction = {})
    }
}

/** Macros only with one change, so the badge shows. */
@Preview
@Composable
private fun ModesPreview() {
    SettingsPreview {
        ModesScreen(ModesStateBuilder.build(SettingsFixtures.nutrientsFirstWithACarbsChange, SettingsFixtures.timeZone), onAction = {})
    }
}

/** Every number on Later, with the reveal time and the weekly switch it brings, and the picker open. */
@Preview
@Composable
private fun ModesRevealTimePreview() {
    SettingsPreview {
        ModesScreen(
            ModesStateBuilder.build(NumbersSettings.later(), SettingsFixtures.timeZone, pickingRevealTime = true),
            onAction = {},
        )
    }
}

/** Each number: the matrix with carbs changed and `release.progressPhotos` on, so it ends with the photos row. */
@Preview
@Composable
private fun EachNumberPreview() {
    SettingsPreview {
        EachNumberScreen(
            ModesStateBuilder.build(SettingsFixtures.nutrientsFirstWithACarbsChange, SettingsFixtures.timeZone, photosReleased = true),
            onAction = {},
        )
    }
}

/** Photos switched on in Macros only, which hides them by default: a change like any other. */
@Preview
@Composable
private fun EachNumberPhotosShownPreview() {
    val shown = SettingsFixtures.nutrientsFirstWithACarbsChange.copy(progressPhotos = true)
    SettingsPreview { EachNumberScreen(ModesStateBuilder.build(shown, SettingsFixtures.timeZone, photosReleased = true), onAction = {}) }
}
