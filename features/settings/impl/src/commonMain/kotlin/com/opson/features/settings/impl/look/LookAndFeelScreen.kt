package com.opson.features.settings.impl.look

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.libraries.diary.TextSize
import com.opson.libraries.diary.ThemeMode
import com.opson.libraries.diary.Tone
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_back_more
import opson.libraries.resources.generated.resources.settings_look_tags_body
import opson.libraries.resources.generated.resources.settings_look_tags_title
import opson.libraries.resources.generated.resources.settings_look_text
import opson.libraries.resources.generated.resources.settings_look_text_large
import opson.libraries.resources.generated.resources.settings_look_text_normal
import opson.libraries.resources.generated.resources.settings_look_theme
import opson.libraries.resources.generated.resources.settings_look_theme_dark
import opson.libraries.resources.generated.resources.settings_look_theme_light
import opson.libraries.resources.generated.resources.settings_look_theme_system
import opson.libraries.resources.generated.resources.settings_look_title
import opson.libraries.resources.generated.resources.settings_look_tone
import opson.libraries.resources.generated.resources.settings_look_tone_example
import opson.libraries.resources.generated.resources.settings_look_tone_plain
import opson.libraries.resources.generated.resources.settings_look_tone_plain_sample
import opson.libraries.resources.generated.resources.settings_look_tone_warm
import opson.libraries.resources.generated.resources.settings_look_tone_warm_sample
import opson.libraries.resources.generated.resources.settings_look_weight
import opson.libraries.resources.generated.resources.settings_look_weight_kg
import opson.libraries.resources.generated.resources.settings_look_weight_lb
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun LookAndFeelScreen(
    state: LookAndFeelState,
    onAction: (LookAndFeelAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.settings_look_title),
            backLabel = stringResource(Res.string.settings_back_more),
            onBack = { onAction(LookAndFeelAction.BackTapped) },
        )
        Choice(
            title = Res.string.settings_look_theme,
            options = ThemeMode.entries,
            selected = state.theme,
            label = ::themeLabel,
            onSelect = { onAction(LookAndFeelAction.ThemePicked(it)) },
        )
        Choice(
            title = Res.string.settings_look_text,
            options = TextSize.entries,
            selected = state.textSize,
            label = { if (it == TextSize.Large) Res.string.settings_look_text_large else Res.string.settings_look_text_normal },
            onSelect = { onAction(LookAndFeelAction.TextSizePicked(it)) },
        )
        Choice(
            title = Res.string.settings_look_weight,
            options = WeightUnit.entries,
            selected = state.weightUnit,
            label = { if (it == WeightUnit.Lb) Res.string.settings_look_weight_lb else Res.string.settings_look_weight_kg },
            onSelect = { onAction(LookAndFeelAction.WeightUnitPicked(it)) },
        )
        if (state.showsTalk) {
            Choice(
                title = Res.string.settings_look_tone,
                options = Tone.entries,
                selected = state.tone,
                label = { if (it == Tone.Warm) Res.string.settings_look_tone_warm else Res.string.settings_look_tone_plain },
                onSelect = { onAction(LookAndFeelAction.TonePicked(it)) },
            ) {
                val sample = if (state.tone == Tone.Warm) {
                    Res.string.settings_look_tone_warm_sample
                } else {
                    Res.string.settings_look_tone_plain_sample
                }
                Text(
                    text = stringResource(Res.string.settings_look_tone_example, stringResource(sample)),
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                )
            }
            EdgeCard(edgeToEdge = false) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.settings_look_tags_title),
                            typography = AppTheme.type.bodyStrong,
                            color = AppTheme.colors.ink,
                        )
                        Text(
                            text = stringResource(Res.string.settings_look_tags_body),
                            typography = AppTheme.type.secondarySmall,
                            color = AppTheme.colors.muted,
                        )
                    }
                    AppSwitch(
                        checked = state.askTags,
                        onCheckedChange = { onAction(LookAndFeelAction.HungerQuestionsToggled) },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> Choice(
    title: StringResource,
    options: List<T>,
    selected: T,
    label: (T) -> StringResource,
    onSelect: (T) -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    EdgeCard(edgeToEdge = false) {
        Text(text = stringResource(title), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
        SegmentedControl(options = options, selected = selected, onSelect = onSelect, label = { stringResource(label(it)) })
        footer()
    }
}

private fun themeLabel(theme: ThemeMode): StringResource = when (theme) {
    ThemeMode.System -> Res.string.settings_look_theme_system
    ThemeMode.Light -> Res.string.settings_look_theme_light
    ThemeMode.Dark -> Res.string.settings_look_theme_dark
}

@Preview
@Composable
private fun LookAndFeelDefaultsPreview() {
    SettingsPreview { LookAndFeelScreen(LookAndFeelStateBuilder.build(SettingsFixtures.settings()), onAction = {}) }
}

@Preview
@Composable
private fun LookAndFeelChangedDarkPreview() {
    val settings = SettingsFixtures.settings().copy(
        theme = ThemeMode.Dark,
        textSize = TextSize.Large,
        tone = Tone.Warm,
        askTags = false,
    )
    SettingsPreview(dark = true) { LookAndFeelScreen(LookAndFeelStateBuilder.build(settings), onAction = {}) }
}
