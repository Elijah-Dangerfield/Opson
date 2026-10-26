package com.opson.features.settings.impl.more

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.SettingsRow
import com.opson.features.settings.impl.label
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_modes_changes
import opson.libraries.resources.generated.resources.settings_more_look
import opson.libraries.resources.generated.resources.settings_more_look_sub
import opson.libraries.resources.generated.resources.settings_more_modes
import opson.libraries.resources.generated.resources.settings_more_modes_changed
import opson.libraries.resources.generated.resources.settings_more_plan
import opson.libraries.resources.generated.resources.settings_more_plan_free
import opson.libraries.resources.generated.resources.settings_more_support
import opson.libraries.resources.generated.resources.settings_more_targets
import opson.libraries.resources.generated.resources.settings_more_targets_sub
import opson.libraries.resources.generated.resources.settings_more_title
import opson.libraries.resources.generated.resources.settings_more_water
import opson.libraries.resources.generated.resources.settings_more_water_glasses
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun MoreScreen(
    state: MoreState,
    onAction: (MoreAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        Text(
            text = stringResource(Res.string.settings_more_title),
            typography = AppTheme.type.h1,
            color = AppTheme.colors.ink,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
        )
        state.groups.forEach { group ->
            SettingsCard {
                group.forEachIndexed { index, row ->
                    MoreRowItem(
                        row = row,
                        showDivider = index < group.lastIndex,
                        onClick = { onAction(MoreAction.RowTapped(row)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreRowItem(row: MoreRow, showDivider: Boolean, onClick: () -> Unit) {
    when (row) {
        is MoreRow.Modes -> {
            val preset = stringResource(row.preset.label)
            SettingsRow(
                title = stringResource(Res.string.settings_more_modes),
                icon = AppIcons.Tune,
                subtitle = if (row.changes > 0) {
                    stringResource(
                        Res.string.settings_more_modes_changed,
                        preset,
                        pluralStringResource(Res.plurals.settings_modes_changes, row.changes, row.changes),
                    )
                } else {
                    preset
                },
                chevron = true,
                showDivider = showDivider,
                onClick = onClick,
            )
        }
        MoreRow.Targets -> SettingsRow(
            title = stringResource(Res.string.settings_more_targets),
            icon = AppIcons.Target,
            subtitle = stringResource(Res.string.settings_more_targets_sub),
            chevron = true,
            showDivider = showDivider,
            onClick = onClick,
        )
        MoreRow.LookAndFeel -> SettingsRow(
            title = stringResource(Res.string.settings_more_look),
            icon = AppIcons.Palette,
            subtitle = stringResource(Res.string.settings_more_look_sub),
            chevron = true,
            showDivider = showDivider,
            onClick = onClick,
        )
        is MoreRow.Water -> SettingsRow(
            title = stringResource(Res.string.settings_more_water),
            icon = AppIcons.WaterDropOutline,
            trailingText = pluralStringResource(Res.plurals.settings_more_water_glasses, row.glasses, row.glasses),
            showDivider = showDivider,
            onClick = onClick,
        )
        MoreRow.Support -> SettingsRow(
            title = stringResource(Res.string.settings_more_support),
            icon = AppIcons.Support,
            showDivider = showDivider,
            onClick = onClick,
        )
        MoreRow.Plan -> SettingsRow(
            title = stringResource(Res.string.settings_more_plan),
            icon = AppIcons.WorkspacePremium,
            trailingText = stringResource(Res.string.settings_more_plan_free),
            showDivider = showDivider,
        )
    }
}

@Preview
@Composable
private fun MorePreview() {
    SettingsPreview { MoreScreen(MoreStateBuilder.build(SettingsFixtures.settings()), onAction = {}) }
}

@Preview
@Composable
private fun MoreFuelWithAChangeDarkPreview() {
    val settings = SettingsFixtures.settings(SettingsFixtures.fuelWithACarbsChange)
    SettingsPreview(dark = true) { MoreScreen(MoreStateBuilder.build(settings), onAction = {}) }
}

@Preview
@Composable
private fun MoreBlindPreview() {
    val settings = SettingsFixtures.settings(NumbersSettings(preset = PresetId.Blind))
    SettingsPreview { MoreScreen(MoreStateBuilder.build(settings), onAction = {}) }
}
