package com.opson.features.settings.impl.look

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.opson.libraries.diary.TextSize
import com.opson.libraries.diary.ThemeMode
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_look_text
import opson.libraries.resources.generated.resources.settings_look_text_large
import opson.libraries.resources.generated.resources.settings_look_text_normal
import opson.libraries.resources.generated.resources.settings_look_theme
import opson.libraries.resources.generated.resources.settings_look_theme_dark
import opson.libraries.resources.generated.resources.settings_look_theme_light
import opson.libraries.resources.generated.resources.settings_look_theme_system
import opson.libraries.resources.generated.resources.settings_look_weight
import opson.libraries.resources.generated.resources.settings_look_weight_kg
import opson.libraries.resources.generated.resources.settings_look_weight_lb
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The Appearance half of More › Display: theme, text size and weight units. */
@Composable
internal fun ColumnScope.AppearanceSection(state: LookAndFeelState, onAction: (LookAndFeelAction) -> Unit) {
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
}

@Composable
private fun <T> Choice(
    title: StringResource,
    options: List<T>,
    selected: T,
    label: (T) -> StringResource,
    onSelect: (T) -> Unit,
) {
    EdgeCard(edgeToEdge = false) {
        Text(text = stringResource(title), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
        SegmentedControl(options = options, selected = selected, onSelect = onSelect, label = { stringResource(label(it)) })
    }
}

private fun themeLabel(theme: ThemeMode): StringResource = when (theme) {
    ThemeMode.System -> Res.string.settings_look_theme_system
    ThemeMode.Light -> Res.string.settings_look_theme_light
    ThemeMode.Dark -> Res.string.settings_look_theme_dark
}
