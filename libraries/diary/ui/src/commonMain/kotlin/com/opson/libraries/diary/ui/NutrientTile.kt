package com.opson.libraries.diary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.ui.LocalVisibilityPolicy
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.nutrition.ui.currentVisibilityPolicy
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One number in a row of tiles (the amount sheet's Energy, Protein, Fiber,
 * Calcium): a small label over the value on a surface tile. v3 colours the macro labels with their
 * nutrient colour; pass [labelColor]. Hidden removes
 * the tile, label and all; give it a weight from the row.
 */
@Composable
fun NutrientTile(
    metric: Metric,
    label: String,
    display: Display,
    modifier: Modifier = Modifier,
    labelColor: ColorResource = AppTheme.colors.muted,
) {
    NutrientVisible(display) {
        Column(
            modifier = modifier
                .clip(AppTheme.shapes.inputLarge)
                .background(AppTheme.colors.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = label, typography = AppTheme.type.secondarySmallStrong, color = labelColor, maxLines = 1)
            NutrientText(metric = metric, display = display, typography = AppTheme.type.numeric, color = AppTheme.colors.ink)
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun NutrientTilePreview() {
    PreviewLightDark {
        val policy = currentVisibilityPolicy()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(Metric.Calories, "Energy", 105.0),
                Triple(Metric.Protein, "Protein", 1.3),
                Triple(Metric.Fiber, "Fiber", 3.1),
            ).forEach { (metric, label, value) ->
                NutrientTile(metric, label, policy.resolve(metric, value, Surface.Logger), Modifier.weight(1f))
            }
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun NutrientTileBlindPreview() {
    CompositionLocalProvider(LocalVisibilityPolicy provides previewVisibilityPolicy(PresetId.Blind)) {
        NutrientTilePreview()
    }
}
