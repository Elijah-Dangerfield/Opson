package com.opson.features.settings.impl.modes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.features.settings.impl.LightAndDark
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.label
import com.opson.features.settings.impl.line
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.ListRowChevron
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.radio.RadioMatrix
import com.opson.libraries.ui.components.radio.RadioMatrixRow
import com.opson.libraries.ui.components.radio.RadioRow
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_back_more
import opson.libraries.resources.generated.resources.settings_modes_cell
import opson.libraries.resources.generated.resources.settings_modes_changed
import opson.libraries.resources.generated.resources.settings_modes_footnote
import opson.libraries.resources.generated.resources.settings_modes_intro
import opson.libraries.resources.generated.resources.settings_modes_matrix_header
import opson.libraries.resources.generated.resources.settings_modes_pause_body
import opson.libraries.resources.generated.resources.settings_modes_pause_title
import opson.libraries.resources.generated.resources.settings_modes_setup
import opson.libraries.resources.generated.resources.settings_modes_targets
import opson.libraries.resources.generated.resources.settings_modes_targets_sub
import opson.libraries.resources.generated.resources.settings_modes_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ModesScreen(
    state: ModesState,
    onAction: (ModesAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        PageHeader(
            title = stringResource(Res.string.settings_modes_title),
            backLabel = stringResource(Res.string.settings_back_more),
            onBack = { onAction(ModesAction.BackTapped) },
        )
        Text(
            text = stringResource(Res.string.settings_modes_intro),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
        EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.ListPadding, verticalArrangement = Arrangement.Top) {
            state.presets.forEachIndexed { index, row ->
                RadioRow(
                    title = stringResource(row.id.label),
                    subtitle = stringResource(row.id.line),
                    selected = row.selected,
                    onClick = { onAction(ModesAction.PresetTapped(row.id)) },
                    showDivider = index < state.presets.lastIndex,
                )
            }
        }
        LevelMatrix(state.matrix, onAction)
        EdgeCard(edgeToEdge = false) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.settings_modes_pause_title),
                        typography = AppTheme.type.bodyStrong,
                        color = AppTheme.colors.ink,
                    )
                    Text(
                        text = stringResource(Res.string.settings_modes_pause_body),
                        typography = AppTheme.type.secondarySmall,
                        color = AppTheme.colors.muted,
                    )
                }
                AppSwitch(checked = state.pauseOn, onCheckedChange = { onAction(ModesAction.PauseToggled) })
            }
        }
        EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.ListPadding, verticalArrangement = Arrangement.Top) {
            ListRow(
                title = stringResource(Res.string.settings_modes_targets),
                subtitle = stringResource(Res.string.settings_modes_targets_sub),
                onClick = { onAction(ModesAction.TargetsTapped) },
                trailing = { ListRowChevron() },
            )
        }
        Text(
            text = when (val note = state.changedNote) {
                ChangedNote.DuringSetup -> stringResource(Res.string.settings_modes_setup)
                is ChangedNote.LastChanged -> stringResource(Res.string.settings_modes_changed, note.timestamp)
            },
            typography = AppTheme.type.secondarySmall,
            color = AppTheme.colors.muted,
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
        )
    }
    EdgeCard(edgeToEdge = false, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        RadioMatrix(
            header = stringResource(Res.string.settings_modes_matrix_header),
            columns = levelLabels,
            rows = rows,
            onSelect = { row, column -> onAction(ModesAction.LevelTapped(matrix[row].metric, levels[column])) },
        )
        Text(
            text = stringResource(Res.string.settings_modes_footnote),
            typography = AppTheme.type.caption,
            color = AppTheme.colors.muted,
        )
    }
}

@Preview(widthDp = 800, heightDp = 1500)
@Composable
private fun ModesFullPreview() {
    LightAndDark { ModesScreen(ModesStateBuilder.build(NumbersSettings(), SettingsFixtures.timeZone), onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 1500)
@Composable
private fun ModesAdjustedPreview() {
    LightAndDark { ModesScreen(ModesStateBuilder.build(SettingsFixtures.adjusted, SettingsFixtures.timeZone), onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 1500)
@Composable
private fun ModesBlindPreview() {
    val blind = NumbersSettings(preset = PresetId.Blind, pauseBeforeMore = true)
    LightAndDark { ModesScreen(ModesStateBuilder.build(blind, SettingsFixtures.timeZone), onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 900)
@Composable
private fun ModesEmptyPreview() {
    LightAndDark { ModesScreen(ModesState(), onAction = {}) }
}
