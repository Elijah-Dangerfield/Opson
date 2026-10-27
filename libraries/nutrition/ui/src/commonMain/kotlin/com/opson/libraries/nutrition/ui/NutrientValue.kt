package com.opson.libraries.nutrition.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.components.barFill
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.text.ProvideTextConfig
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.typography.TypographyResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The tag `BlindModeLeakTest` looks for. Every rendered number carries one. */
fun Metric.testTag(): String = "metric:$key"

/**
 * Resolves [value] through the current [com.opson.libraries.nutrition.VisibilityPolicy]
 * and renders it. Prefer [NutrientText] with a [Display] the view model already
 * resolved; this is for the places where a raw value is all there is.
 */
@Composable
fun NutrientValue(
    metric: Metric,
    value: Double?,
    surface: Surface,
    modifier: Modifier = Modifier,
    context: FormatContext = FormatContext(),
    typography: TypographyResource? = null,
    color: ColorResource? = null,
    textAlign: TextAlign? = null,
    maxLines: Int? = null,
) {
    NutrientText(
        metric = metric,
        display = currentVisibilityPolicy().resolve(metric, value, surface, context),
        modifier = modifier,
        typography = typography,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
    )
}

/** Renders a resolved [display]. Hidden, and softened water (bar only), render nothing at all. */
@Composable
fun NutrientText(
    metric: Metric,
    display: Display,
    modifier: Modifier = Modifier,
    typography: TypographyResource? = null,
    color: ColorResource? = null,
    textAlign: TextAlign? = null,
    maxLines: Int? = null,
) {
    val text = when (display) {
        Display.Hidden -> null
        is Display.Soft -> display.text
        is Display.Shown -> display.text
    } ?: return
    ProvideTextConfig(typography = typography, textAlign = textAlign, maxLines = maxLines) {
        Text(text = text, modifier = modifier.testTag(metric.testTag()), color = color)
    }
}

/**
 * Renders [content] only when [display] is not hidden. Labels go in here, so
 * hiding a number removes its label too, not just the digits.
 */
@Composable
fun NutrientVisible(display: Display, content: @Composable () -> Unit) {
    if (display !is Display.Hidden) content()
}

/**
 * A fill bar for a resolved [display]: nothing when hidden or when there is no
 * goal to fill towards. Size it with [modifier]. Overfull clamps to full, so a
 * bar never says by how much.
 */
@Composable
fun NutrientBar(
    metric: Metric,
    display: Display,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
) {
    val fraction = when (display) {
        Display.Hidden -> null
        is Display.Soft -> display.fraction
        is Display.Shown -> display.fraction
    } ?: return
    val fill = barFill(fraction)
    Spacer(
        modifier
            .testTag(metric.testTag())
            .clip(shape)
            .drawBehind {
                drawRect(trackColor)
                drawRect(color, size = size.copy(width = size.width * fill))
            },
    )
}

/** [NutrientBar] for a raw value, resolved through the current policy. */
@Composable
fun NutrientBar(
    metric: Metric,
    value: Double?,
    surface: Surface,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    context: FormatContext = FormatContext(),
    shape: Shape = CircleShape,
) {
    NutrientBar(
        metric = metric,
        display = currentVisibilityPolicy().resolve(metric, value, surface, context),
        color = color,
        trackColor = trackColor,
        modifier = modifier,
        shape = shape,
    )
}

@Preview
@Composable
private fun NutrientValuePreview() {
    PreviewContent {
        Column {
            NutrientValue(Metric.Calories, 1245.0, Surface.Today)
            NutrientValue(Metric.Protein, 47.0, Surface.Today, context = FormatContext(of = 120.0))
            NutrientBar(
                metric = Metric.Protein,
                value = 47.0,
                surface = Surface.Today,
                color = AppTheme.colors.nutrients.protein.content.color,
                trackColor = AppTheme.colors.track.color,
                context = FormatContext(of = 120.0),
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }
    }
}

@Preview
@Composable
private fun NutrientValueBlindPreview() {
    CompositionLocalProvider(
        LocalVisibilityPolicy provides previewVisibilityPolicy(PresetId.NoNumbers),
    ) {
        NutrientValuePreview()
    }
}
