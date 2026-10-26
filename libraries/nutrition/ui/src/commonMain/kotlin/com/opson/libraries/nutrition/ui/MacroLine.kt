package com.opson.libraries.nutrition.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.MacroPart
import com.opson.libraries.nutrition.NutrientAmounts
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.macroLine
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.typography.TypographyResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The separator `macroLine` parts are joined with in each style: ` · ` for long, a space for compact. */
val MacroLineStyle.separator: String
    get() = when (this) {
        MacroLineStyle.Long -> " · "
        MacroLineStyle.Compact -> " "
    }

/**
 * A line of macros from `macroLine`: each part its own `Text`, tagged
 * `metric:<key>`, joined by [separator]. Renders nothing for an empty list, so
 * a mode that hides every macro leaves no stray separator behind.
 *
 * Wraps rather than truncates: a cut-off "27 g ca…" is worse than a second line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MacroLine(
    parts: List<MacroPart>,
    modifier: Modifier = Modifier,
    separator: String = MacroLineStyle.Long.separator,
    typography: TypographyResource = AppTheme.type.secondarySmall,
    color: ColorResource = AppTheme.colors.muted,
) {
    if (parts.isEmpty()) return
    FlowRow(modifier = modifier) {
        parts.forEachIndexed { index, part ->
            if (index > 0) Text(text = separator, typography = typography, color = color)
            Text(
                text = part.text,
                typography = typography,
                color = color,
                modifier = Modifier.testTag(part.metric.testTag()),
            )
        }
    }
}

@Preview
@Composable
private fun MacroLinePreview() {
    PreviewContent {
        val policy = currentVisibilityPolicy()
        val lunch = NutrientAmounts(kcal = 515.0, protein = 30.0, carbs = 50.0, fat = 20.0, fiber = 1.0)
        Column {
            MacroLine(policy.macroLine(lunch, Surface.Today, style = MacroLineStyle.Long))
            MacroLine(
                policy.macroLine(lunch, Surface.Logger, style = MacroLineStyle.Compact),
                separator = MacroLineStyle.Compact.separator,
            )
        }
    }
}

@Preview
@Composable
private fun MacroLineFuelPreview() {
    CompositionLocalProvider(LocalVisibilityPolicy provides previewVisibilityPolicy(PresetId.Fuel)) {
        MacroLinePreview()
    }
}
