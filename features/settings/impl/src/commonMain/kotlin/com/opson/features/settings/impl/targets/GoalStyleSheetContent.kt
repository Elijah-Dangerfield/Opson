package com.opson.features.settings.impl.targets

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsPreview
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.radio.RadioDot
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_goal_style_done
import opson.libraries.resources.generated.resources.settings_goal_style_title
import opson.libraries.resources.generated.resources.settings_targets_style_by_day_line
import opson.libraries.resources.generated.resources.settings_targets_style_deficit_line
import opson.libraries.resources.generated.resources.settings_targets_style_range_line
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The goal style sheet's body, apart from the `BottomSheet` so previews and the leak test can draw it. */
@Composable
fun GoalStyleSheetContent(
    state: GoalStyleState,
    onAction: (GoalStyleAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = 10.dp, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        SheetHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            text = stringResource(Res.string.settings_goal_style_title),
            typography = AppTheme.type.dayLabel,
            color = AppTheme.colors.ink,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
        )
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.options.forEach { kind ->
                StyleOption(
                    kind = kind,
                    selected = kind == state.selected,
                    onClick = { onAction(GoalStyleAction.StylePicked(kind)) },
                )
            }
        }
        FlatButton(
            text = stringResource(Res.string.settings_goal_style_done),
            onClick = { onAction(GoalStyleAction.DoneTapped) },
            tone = FlatButtonTone.Accent,
            centered = true,
            minHeight = FlatButtonDefaults.TallMinHeight,
            typography = AppTheme.type.button,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun StyleOption(kind: StyleKind, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.card
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.accentTint else colors.surface)
            .border(1.5.dp, (if (selected) colors.accent else colors.line).color, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon = kind.icon, size = 26.dp, color = if (selected) colors.accent else colors.muted)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(kind.label), typography = AppTheme.type.cardTitle, color = colors.ink)
            Text(
                text = stringResource(kind.line),
                typography = AppTheme.type.secondary,
                color = if (selected) colors.accent else colors.muted,
            )
        }
        RadioDot(selected = selected)
    }
}

private val StyleKind.icon: IconResource
    get() = when (this) {
        StyleKind.Range -> AppIcons.Straighten
        StyleKind.Deficit -> AppIcons.TrendingDown
        StyleKind.ByDay -> AppIcons.CalendarMonth
    }

private val StyleKind.line: StringResource
    get() = when (this) {
        StyleKind.Range -> Res.string.settings_targets_style_range_line
        StyleKind.Deficit -> Res.string.settings_targets_style_deficit_line
        StyleKind.ByDay -> Res.string.settings_targets_style_by_day_line
    }

@Preview
@Composable
private fun TargetsGoalStylePreview() {
    SettingsPreview {
        GoalStyleSheetContent(GoalStyleStateBuilder.build(SettingsFixtures.settings()), onAction = {})
    }
}

@Preview
@Composable
private fun TargetsGoalStyleFuelDarkPreview() {
    val fuel = SettingsFixtures.settings(NumbersSettings(preset = PresetId.Fuel))
    SettingsPreview(dark = true) {
        GoalStyleSheetContent(GoalStyleStateBuilder.build(fuel, picked = StyleKind.ByDay), onAction = {})
    }
}
