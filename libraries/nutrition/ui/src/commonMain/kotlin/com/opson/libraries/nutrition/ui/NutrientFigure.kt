package com.opson.libraries.nutrition.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.typography.TypographyResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A resolved amount set as a figure: the number in [typography], whatever follows it (" kcal",
 * " / 120 g", " of 120 g") in [restTypography] and [restColor], as design sync 2's summary card
 * draws "1,990 kcal" and "51 / 120 g". A soft word is set whole in [typography]. Hidden renders
 * nothing. Tagged like [NutrientText].
 */
@Composable
fun NutrientFigure(
    metric: Metric,
    display: Display,
    typography: TypographyResource,
    restTypography: TypographyResource,
    modifier: Modifier = Modifier,
    color: ColorResource = AppTheme.colors.ink,
    restColor: ColorResource = AppTheme.colors.muted,
    maxLines: Int = 1,
) {
    val text = when (display) {
        Display.Hidden -> null
        is Display.Soft -> display.text
        is Display.Shown -> display.text
    } ?: return
    val split = if (display is Display.Shown) text.indexOf(' ') else -1
    val rest = restTypography.style
    val annotated = buildAnnotatedString {
        if (split <= 0) {
            append(text)
        } else {
            append(text.substring(0, split))
            withStyle(SpanStyle(fontSize = rest.fontSize, fontWeight = rest.fontWeight, fontFamily = rest.fontFamily, color = restColor.color)) {
                append(text.substring(split))
            }
        }
    }
    Text(text = annotated, modifier = modifier.testTag(metric.testTag()), typography = typography, color = color, maxLines = maxLines)
}

@Preview
@Composable
private fun NutrientFigurePreview() {
    PreviewContent {
        val policy = currentVisibilityPolicy()
        Column {
            NutrientFigure(Metric.EnergyOut, policy.resolve(Metric.EnergyOut, 1990.0, Surface.Today), AppTheme.type.cardTitle.Tabular, AppTheme.type.secondary)
            NutrientFigure(
                Metric.Protein,
                policy.resolve(Metric.Protein, 51.0, Surface.Today, FormatContext(of = 120.0, goal = GoalStyle.Slash)),
                AppTheme.type.cardTitle.Tabular,
                AppTheme.type.secondary,
            )
        }
    }
}
