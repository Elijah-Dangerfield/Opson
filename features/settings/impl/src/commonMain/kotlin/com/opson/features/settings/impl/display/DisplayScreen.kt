package com.opson.features.settings.impl.display

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.look.AppearanceSection
import com.opson.features.settings.impl.look.LookAndFeelAction
import com.opson.features.settings.impl.look.LookAndFeelState
import com.opson.features.settings.impl.look.LookAndFeelStateBuilder
import com.opson.features.settings.impl.modes.ModesAction
import com.opson.features.settings.impl.modes.ModesState
import com.opson.features.settings.impl.modes.ModesStateBuilder
import com.opson.features.settings.impl.modes.NumbersSection
import com.opson.features.settings.impl.modes.RevealTimeDialog
import com.opson.libraries.diary.TextSize
import com.opson.libraries.diary.ThemeMode
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_back_more
import opson.libraries.resources.generated.resources.settings_display_appearance
import opson.libraries.resources.generated.resources.settings_display_numbers
import opson.libraries.resources.generated.resources.settings_display_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * More › Display: what the app shows and how it looks, on one page. Numbers first (the modes,
 * End after a set time, Each number and the pause), then Appearance (theme, text size, weight
 * units). The halves keep their own view models, so each writes what it always wrote; the back
 * button is the Numbers half's.
 */
@Composable
fun DisplayScreen(
    modes: ModesState,
    look: LookAndFeelState,
    onModesAction: (ModesAction) -> Unit,
    onLookAction: (LookAndFeelAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.settings_display_title),
            backLabel = stringResource(Res.string.settings_back_more),
            onBack = { onModesAction(ModesAction.BackTapped) },
        )
        SectionTitle(stringResource(Res.string.settings_display_numbers))
        NumbersSection(modes, onModesAction)
        SectionTitle(stringResource(Res.string.settings_display_appearance), Modifier.padding(top = 12.dp))
        AppearanceSection(look, onLookAction)
    }
    RevealTimeDialog(modes, onModesAction)
}

/** A heading over one half of the page, a size up from the labels inside it. */
@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        typography = AppTheme.type.sheetTitle,
        color = AppTheme.colors.ink,
        modifier = modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun DisplayPreviewPage(
    numbers: NumbersSettings,
    look: LookAndFeelState = LookAndFeelStateBuilder.build(SettingsFixtures.settings()),
    dark: Boolean = false,
    pickingRevealTime: Boolean = false,
    editingEndAfter: Boolean = false,
) {
    SettingsPreview(dark = dark) {
        DisplayScreen(
            modes = ModesStateBuilder.build(
                numbers,
                SettingsFixtures.timeZone,
                pickingRevealTime = pickingRevealTime,
                editingEndAfter = editingEndAfter,
            ),
            look = look,
            onModesAction = {},
            onLookAction = {},
        )
    }
}

/** The page as most people meet it: Macros only with one change, and the default look. */
@Preview
@Composable
private fun DisplayPreview() = DisplayPreviewPage(SettingsFixtures.nutrientsFirstWithACarbsChange)

/** Design sync 4's numbers (All numbers for two weeks, then No numbers, the pause on), in dark with large text and pounds. */
@Preview
@Composable
private fun DisplayChangedDarkPreview() = DisplayPreviewPage(
    SettingsFixtures.fullForTwoWeeks,
    look = LookAndFeelState(theme = ThemeMode.Dark, textSize = TextSize.Large, weightUnit = WeightUnit.Lb),
    dark = true,
)

/** End after a set time open: how long, and what comes next. */
@Preview
@Composable
private fun DisplayEndAfterPreview() = DisplayPreviewPage(SettingsFixtures.fullForTwoWeeks, editingEndAfter = true)

/** Every number on Later, with the reveal time and the weekly switch it brings, and the picker open. */
@Preview
@Composable
private fun DisplayRevealTimePreview() = DisplayPreviewPage(NumbersSettings.later(), pickingRevealTime = true)
