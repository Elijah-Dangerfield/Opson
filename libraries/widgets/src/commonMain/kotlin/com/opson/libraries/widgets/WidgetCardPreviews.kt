package com.opson.libraries.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.PaletteChoice
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.PreviewContent
import com.opson.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Both sizes of the widget for [preset], built by the real [WidgetSnapshotBuilder] from
 * [WidgetFixtures]. What design sync compares with v3 `widgets.png`.
 */
@Composable
private fun WidgetPreviewPair(
    preset: PresetId,
    dark: Boolean = false,
    mealGroups: Boolean = true,
    palette: PaletteChoice = PaletteChoice.Pine,
) {
    PreviewContent(darkTheme = dark) {
        val inputs = WidgetFixtures.inputs(NumbersSettings(preset = preset), mealGroups = mealGroups)
        val snapshot = WidgetSnapshotBuilder.build(
            inputs.copy(settings = inputs.settings.copy(palette = palette)),
            WidgetFixtures.policy(preset),
            rememberWidgetCopy(),
        )
        // On the hero colour, as v3 draws them: a widget sits on a wallpaper.
        Column(
            modifier = Modifier.background(AppTheme.colors.hero.color).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WidgetCard(snapshot, WidgetFixtures.today, WidgetSize.Medium)
            WidgetCard(snapshot, WidgetFixtures.today, WidgetSize.Small)
        }
    }
}

@Preview
@Composable
private fun WidgetFullPreview() {
    WidgetPreviewPair(PresetId.Full)
}

@Preview
@Composable
private fun WidgetFullDarkPreview() {
    WidgetPreviewPair(PresetId.Full, dark = true)
}

@Preview
@Composable
private fun WidgetBlindPreview() {
    WidgetPreviewPair(PresetId.NoNumbers)
}

@Preview
@Composable
private fun WidgetBlindDarkPreview() {
    WidgetPreviewPair(PresetId.NoNumbers, dark = true)
}

/** Fuel softens calories: the circles, not a soft word. */
@Preview
@Composable
private fun WidgetFuelPreview() {
    WidgetPreviewPair(PresetId.NutrientsFirst)
}

/** Meal groups off in Blind: nothing to tick, so a line instead. */
@Preview
@Composable
private fun WidgetGroupsOffPreview() {
    WidgetPreviewPair(PresetId.NoNumbers, mealGroups = false)
}

@Preview
@Composable
private fun WidgetEspressoPreview() {
    WidgetPreviewPair(PresetId.Full, palette = PaletteChoice.Espresso)
}
