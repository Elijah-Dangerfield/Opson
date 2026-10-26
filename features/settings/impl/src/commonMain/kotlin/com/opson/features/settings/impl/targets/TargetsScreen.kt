package com.opson.features.settings.impl.targets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.features.settings.impl.LightAndDark
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.label
import com.opson.libraries.diary.MacroMode
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.LegendDot
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.IconTile
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.color.ToneColors
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_back_more
import opson.libraries.resources.generated.resources.settings_targets_aim
import opson.libraries.resources.generated.resources.settings_targets_floor_note
import opson.libraries.resources.generated.resources.settings_targets_footer
import opson.libraries.resources.generated.resources.settings_targets_grams
import opson.libraries.resources.generated.resources.settings_targets_grams_note
import opson.libraries.resources.generated.resources.settings_targets_hidden_body
import opson.libraries.resources.generated.resources.settings_targets_hidden_link
import opson.libraries.resources.generated.resources.settings_targets_hidden_title
import opson.libraries.resources.generated.resources.settings_targets_lower
import opson.libraries.resources.generated.resources.settings_targets_macros_title
import opson.libraries.resources.generated.resources.settings_targets_raise
import opson.libraries.resources.generated.resources.settings_targets_range_label
import opson.libraries.resources.generated.resources.settings_targets_ratios
import opson.libraries.resources.generated.resources.settings_targets_ratios_note
import opson.libraries.resources.generated.resources.settings_targets_step_note
import opson.libraries.resources.generated.resources.settings_targets_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun TargetsScreen(
    state: TargetsState,
    onAction: (TargetsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        PageHeader(
            title = stringResource(Res.string.settings_targets_title),
            backLabel = stringResource(Res.string.settings_back_more),
            onBack = { onAction(TargetsAction.BackTapped) },
        )
        if (state.visible) {
            EnergyCard(state, onAction)
            if (state.macros.isNotEmpty()) MacroCard(state, onAction)
        } else {
            BackgroundCard(onAction)
        }
        Text(
            text = stringResource(Res.string.settings_targets_footer),
            typography = AppTheme.type.secondarySmall,
            color = AppTheme.colors.muted,
        )
    }
}

@Composable
private fun EnergyCard(state: TargetsState, onAction: (TargetsAction) -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = EdgeCardDefaults.InsetPadding,
        verticalArrangement = Arrangement.spacedBy(EdgeCardDefaults.SectionGap),
    ) {
        Column {
            Text(
                text = stringResource(Res.string.settings_targets_range_label),
                typography = AppTheme.type.secondarySmallStrong,
                color = AppTheme.colors.muted,
            )
            NutrientText(
                metric = Metric.Calories,
                display = state.range,
                typography = AppTheme.type.dayLabel.Tabular,
                color = AppTheme.colors.ink,
            )
            NutrientPhrase(
                metric = Metric.Calories,
                display = state.aim,
                phrase = { stringResource(Res.string.settings_targets_aim, it) },
                typography = AppTheme.type.secondary.Tabular,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            StepButton(
                text = stringResource(Res.string.settings_targets_lower),
                icon = AppIcons.Remove,
                dimmed = !state.canLower,
                onClick = { onAction(TargetsAction.LowerTapped) },
                modifier = Modifier.weight(1f),
            )
            StepButton(
                text = stringResource(Res.string.settings_targets_raise),
                icon = AppIcons.Add,
                dimmed = false,
                onClick = { onAction(TargetsAction.RaiseTapped) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(
                if (state.canLower) Res.string.settings_targets_step_note else Res.string.settings_targets_floor_note,
            ),
            typography = AppTheme.type.secondarySmall,
            color = AppTheme.colors.muted,
        )
    }
}

@Composable
private fun StepButton(
    text: String,
    icon: IconResource,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    FlatButton(
        text = text,
        onClick = onClick,
        icon = icon,
        tone = FlatButtonTone.SoftOutlined,
        centered = true,
        dimmed = dimmed,
        shape = AppTheme.shapes.input,
        minHeight = FlatButtonDefaults.StepperMinHeight,
        typography = AppTheme.type.buttonSmall,
        modifier = modifier,
    )
}

@Composable
private fun MacroCard(state: TargetsState, onAction: (TargetsAction) -> Unit) {
    EdgeCard(edgeToEdge = false) {
        Text(
            text = stringResource(Res.string.settings_targets_macros_title),
            typography = AppTheme.type.bodyBold,
            color = AppTheme.colors.ink,
        )
        SegmentedControl(
            options = MacroMode.entries,
            selected = state.macroMode,
            onSelect = { onAction(TargetsAction.MacroModePicked(it)) },
            label = {
                stringResource(
                    when (it) {
                        MacroMode.Ratios -> Res.string.settings_targets_ratios
                        MacroMode.Grams -> Res.string.settings_targets_grams
                    },
                )
            },
        )
        Column {
            state.macros.forEach { row -> MacroRow(row) }
        }
        Text(
            text = stringResource(
                when (state.macroMode) {
                    MacroMode.Ratios -> Res.string.settings_targets_ratios_note
                    MacroMode.Grams -> Res.string.settings_targets_grams_note
                },
            ),
            typography = AppTheme.type.secondarySmall,
            color = AppTheme.colors.muted,
        )
    }
}

@Composable
private fun MacroRow(row: TargetMacroRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.row),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
        ) {
            LegendDot(color = macroColor(row.metric))
            Text(text = stringResource(row.metric.label), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
            NutrientText(
                metric = row.metric,
                display = row.share,
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.muted,
            )
        }
        NutrientText(
            metric = row.metric,
            display = row.grams,
            typography = AppTheme.type.numeric,
            color = AppTheme.colors.ink,
        )
    }
    HorizontalDivider(color = AppTheme.colors.line)
}

@Composable
private fun macroColor(metric: Metric): ColorResource {
    val nutrients = AppTheme.colors.nutrients
    return when (metric) {
        Metric.Protein -> nutrients.protein
        Metric.Carbs -> nutrients.carbs
        Metric.Fat -> nutrients.fat
        else -> nutrients.fiber
    }.content
}

@Composable
private fun BackgroundCard(onAction: (TargetsAction) -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            IconTile(
                icon = AppIcons.VisibilityOff,
                tone = ToneColors(content = AppTheme.colors.accent, container = AppTheme.colors.accentTint),
            )
            Text(
                text = stringResource(Res.string.settings_targets_hidden_title),
                typography = AppTheme.type.cardTitle,
                color = AppTheme.colors.ink,
            )
        }
        Text(
            text = stringResource(Res.string.settings_targets_hidden_body),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
        FlatButton(
            text = stringResource(Res.string.settings_targets_hidden_link),
            onClick = { onAction(TargetsAction.ModesTapped) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(widthDp = 800, heightDp = 800)
@Composable
private fun TargetsFullPreview() {
    val settings = SettingsFixtures.settings()
    LightAndDark { TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 800)
@Composable
private fun TargetsAtTheFloorInGramsPreview() {
    val settings = SettingsFixtures.settings().let {
        it.copy(targets = it.targets.lower(scaleMacros = true)!!, macroMode = MacroMode.Grams)
    }
    LightAndDark { TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun TargetsBlindPreview() {
    val settings = SettingsFixtures.settings(NumbersSettings(preset = PresetId.Blind))
    LightAndDark { TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun TargetsEmptyPreview() {
    LightAndDark { TargetsScreen(TargetsState(), onAction = {}) }
}
