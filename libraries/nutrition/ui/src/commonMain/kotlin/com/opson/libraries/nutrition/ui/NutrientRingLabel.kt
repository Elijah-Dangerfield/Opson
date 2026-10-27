package com.opson.libraries.nutrition.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RingValue
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.ringValue
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The label in the middle of a Today ring: [value] over its unit, tagged
 * `metric:<key>`. Renders nothing at all for a `null` [value], which is what
 * `ringValue` returns for anything not effectively shown, so the ring keeps its
 * arc and loses its number.
 *
 * Defaults to the on-hero colours because every ring sits on the hero block.
 */
@Composable
fun NutrientRingLabel(
    metric: Metric,
    value: RingValue?,
    modifier: Modifier = Modifier,
    valueColor: ColorResource = AppTheme.colors.onHero,
    unitColor: ColorResource = AppTheme.colors.onHeroMuted,
) {
    if (value == null) return
    Column(
        modifier = modifier.testTag(metric.testTag()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value.value,
            typography = AppTheme.type.ringValue,
            color = valueColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = value.unit,
            typography = AppTheme.type.ringUnit,
            color = unitColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Preview
@Composable
private fun NutrientRingLabelPreview() {
    PreviewContent {
        val policy = currentVisibilityPolicy()
        val range = FormatContext(eaten = 2000.0, min = 1900.0, max = 2300.0)
        Row(
            modifier = Modifier.background(AppTheme.colors.hero),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(
                Metric.Calories to policy.ringValue(Metric.Calories, 1245.0, Surface.Today),
                Metric.Remaining to policy.ringValue(Metric.Remaining, 1060.0, Surface.Today),
                Metric.Remaining to policy.ringValue(Metric.Remaining, 100.0, Surface.Today, range),
            ).forEach { (metric, value) ->
                Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
                    NutrientRingLabel(metric, value)
                }
            }
        }
    }
}

@Preview
@Composable
private fun NutrientRingLabelBlindPreview() {
    CompositionLocalProvider(LocalVisibilityPolicy provides previewVisibilityPolicy(PresetId.NoNumbers)) {
        NutrientRingLabelPreview()
    }
}
