package com.opson.features.settings.impl.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.features.settings.impl.LightAndDark
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.label
import com.opson.libraries.diary.MacroMode
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.ListRowChevron
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_more_look
import opson.libraries.resources.generated.resources.settings_more_look_sub
import opson.libraries.resources.generated.resources.settings_more_modes
import opson.libraries.resources.generated.resources.settings_more_modes_adjusted
import opson.libraries.resources.generated.resources.settings_more_support
import opson.libraries.resources.generated.resources.settings_more_support_sub
import opson.libraries.resources.generated.resources.settings_more_targets
import opson.libraries.resources.generated.resources.settings_more_targets_grams
import opson.libraries.resources.generated.resources.settings_more_targets_hidden
import opson.libraries.resources.generated.resources.settings_more_targets_ratio
import opson.libraries.resources.generated.resources.settings_more_title
import opson.libraries.resources.generated.resources.settings_more_water
import opson.libraries.resources.generated.resources.settings_more_water_sub
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
        )
        EdgeCard(
            edgeToEdge = false,
            contentPadding = EdgeCardDefaults.ListPadding,
            verticalArrangement = Arrangement.Top,
        ) {
            state.rows.forEachIndexed { index, row ->
                MoreListRow(
                    row = row,
                    showDivider = index < state.rows.lastIndex,
                    onClick = { onAction(MoreAction.RowTapped(row)) },
                )
            }
        }
    }
}

@Composable
private fun MoreListRow(row: MoreRow, showDivider: Boolean, onClick: () -> Unit) {
    when (row) {
        is MoreRow.Modes -> {
            val preset = stringResource(row.preset.label)
            ListRow(
                title = stringResource(Res.string.settings_more_modes),
                subtitle = if (row.adjusted) stringResource(Res.string.settings_more_modes_adjusted, preset) else preset,
                onClick = onClick,
                showDivider = showDivider,
                trailing = { ListRowChevron() },
            )
        }
        is MoreRow.Targets -> ListRow(
            title = stringResource(Res.string.settings_more_targets),
            onClick = onClick,
            showDivider = showDivider,
            subtitleContent = { TargetsSubtitle(row) },
            trailing = { ListRowChevron() },
        )
        MoreRow.Water -> ListRow(
            title = stringResource(Res.string.settings_more_water),
            subtitle = stringResource(Res.string.settings_more_water_sub),
            onClick = onClick,
            showDivider = showDivider,
            trailing = { ListRowChevron() },
        )
        MoreRow.LookAndFeel -> ListRow(
            title = stringResource(Res.string.settings_more_look),
            subtitle = stringResource(Res.string.settings_more_look_sub),
            onClick = onClick,
            showDivider = showDivider,
            trailing = { ListRowChevron() },
        )
        MoreRow.Support -> ListRow(
            title = stringResource(Res.string.settings_more_support),
            subtitle = stringResource(Res.string.settings_more_support_sub),
            onClick = onClick,
            showDivider = showDivider,
            trailing = { ListRowChevron() },
        )
    }
}

@Composable
private fun TargetsSubtitle(row: MoreRow.Targets) {
    if (row.energy is Display.Hidden) {
        Text(
            text = stringResource(Res.string.settings_more_targets_hidden),
            typography = AppTheme.type.secondarySmall,
            color = AppTheme.colors.muted,
        )
        return
    }
    val template = when (row.macroMode) {
        MacroMode.Ratios -> Res.string.settings_more_targets_ratio
        MacroMode.Grams -> Res.string.settings_more_targets_grams
    }
    NutrientPhrase(
        metric = Metric.Calories,
        display = row.energy,
        phrase = { stringResource(template, it) },
        typography = AppTheme.type.secondarySmall.Tabular,
    )
}

@Preview(widthDp = 800, heightDp = 700)
@Composable
private fun MoreFullPreview() {
    val settings = SettingsFixtures.settings()
    LightAndDark { MoreScreen(MoreStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 700)
@Composable
private fun MoreBlindAdjustedPreview() {
    val settings = SettingsFixtures.settings(SettingsFixtures.adjusted.copy(preset = PresetId.Blind))
    LightAndDark { MoreScreen(MoreStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 700)
@Composable
private fun MoreEmptyPreview() {
    LightAndDark { MoreScreen(MoreState(), onAction = {}) }
}
