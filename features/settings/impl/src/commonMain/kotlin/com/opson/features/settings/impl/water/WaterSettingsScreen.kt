package com.opson.features.settings.impl.water

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.features.settings.impl.LightAndDark
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsPage
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.WaterUnit
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_back_more
import opson.libraries.resources.generated.resources.settings_back_today
import opson.libraries.resources.generated.resources.settings_water_goal
import opson.libraries.resources.generated.resources.settings_water_goal_title
import opson.libraries.resources.generated.resources.settings_water_show
import opson.libraries.resources.generated.resources.settings_water_size
import opson.libraries.resources.generated.resources.settings_water_title
import opson.libraries.resources.generated.resources.settings_water_unit_floz
import opson.libraries.resources.generated.resources.settings_water_unit_ml
import opson.libraries.resources.generated.resources.settings_water_units
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun WaterSettingsScreen(
    state: WaterSettingsState,
    onAction: (WaterSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        PageHeader(
            title = stringResource(Res.string.settings_water_title),
            backLabel = stringResource(if (state.backToMore) Res.string.settings_back_more else Res.string.settings_back_today),
            onBack = { onAction(WaterSettingsAction.BackTapped) },
        )
        EdgeCard(contentPadding = EdgeCardDefaults.ListPadding, verticalArrangement = Arrangement.Top) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.section),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
            ) {
                Text(
                    text = stringResource(Res.string.settings_water_show),
                    typography = AppTheme.type.bodyStrong,
                    color = AppTheme.colors.ink,
                    modifier = Modifier.weight(1f),
                )
                AppSwitch(
                    checked = state.showOnToday,
                    onCheckedChange = { onAction(WaterSettingsAction.ShowOnTodayToggled) },
                )
            }
            HorizontalDivider(color = AppTheme.colors.line)
            Section(title = stringResource(Res.string.settings_water_goal_title)) {
                Text(
                    text = stringResource(Res.string.settings_water_goal, state.glasses, state.goalAmount),
                    typography = AppTheme.type.secondarySmall.Tabular,
                    color = AppTheme.colors.muted,
                )
                SegmentedControl(
                    options = state.goals,
                    selected = state.glasses,
                    onSelect = { onAction(WaterSettingsAction.GoalPicked(it)) },
                    label = { it.toString() },
                )
            }
            HorizontalDivider(color = AppTheme.colors.line)
            Section(title = stringResource(Res.string.settings_water_size)) {
                SegmentedControl(
                    options = state.sizes,
                    selected = state.sizes.firstOrNull { it.ml == state.glassMl },
                    onSelect = { size -> size?.let { onAction(WaterSettingsAction.GlassSizePicked(it.ml)) } },
                    label = { it?.label.orEmpty() },
                )
            }
            HorizontalDivider(color = AppTheme.colors.line)
            Section(title = stringResource(Res.string.settings_water_units)) {
                SegmentedControl(
                    options = WaterUnit.entries,
                    selected = state.unit,
                    onSelect = { onAction(WaterSettingsAction.UnitPicked(it)) },
                    label = {
                        stringResource(
                            when (it) {
                                WaterUnit.Ml -> Res.string.settings_water_unit_ml
                                WaterUnit.FlOz -> Res.string.settings_water_unit_floz
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
    ) {
        Text(text = title, typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
        content()
    }
}

@Preview(widthDp = 800, heightDp = 700)
@Composable
private fun WaterSettingsFromTodayPreview() {
    LightAndDark { WaterSettingsScreen(WaterSettingsStateBuilder.build(SettingsFixtures.settings(), backToMore = false), onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 700)
@Composable
private fun WaterSettingsBlindFlOzPreview() {
    val settings = SettingsFixtures.settings(NumbersSettings(preset = PresetId.Blind)).copy(waterUnit = WaterUnit.FlOz)
    LightAndDark { WaterSettingsScreen(WaterSettingsStateBuilder.build(settings, backToMore = true), onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 700)
@Composable
private fun WaterSettingsEmptyPreview() {
    LightAndDark { WaterSettingsScreen(WaterSettingsState(), onAction = {}) }
}
