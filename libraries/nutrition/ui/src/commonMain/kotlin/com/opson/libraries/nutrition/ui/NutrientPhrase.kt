package com.opson.libraries.nutrition.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.typography.TypographyResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A resolved [display] inside a sentence: [phrase] wraps its text, as in "Aim for about
 * 2,100 kcal, before activity" or "2,100 kcal, ratio macros". The whole line carries the
 * metric's test tag, and a hidden display renders nothing, sentence included, so hiding the
 * number removes the words around it too. [numberWeight] sets the number apart from its words
 * ("**120 g** protein", design sync 2's macro split).
 */
@Composable
fun NutrientPhrase(
    metric: Metric,
    display: Display,
    phrase: @Composable (String) -> String,
    modifier: Modifier = Modifier,
    typography: TypographyResource = AppTheme.type.secondary,
    color: ColorResource = AppTheme.colors.muted,
    textAlign: TextAlign? = null,
    numberWeight: FontWeight? = null,
) {
    val text = when (display) {
        Display.Hidden -> null
        is Display.Soft -> display.text
        is Display.Shown -> display.text
    } ?: return
    val sentence = phrase(text)
    val start = sentence.indexOf(text)
    if (numberWeight == null || start < 0) {
        Text(
            text = sentence,
            modifier = modifier.testTag(metric.testTag()),
            typography = typography,
            color = color,
            textAlign = textAlign,
        )
        return
    }
    Text(
        text = buildAnnotatedString {
            append(sentence)
            addStyle(SpanStyle(fontWeight = numberWeight), start, start + text.length)
        },
        modifier = modifier.testTag(metric.testTag()),
        typography = typography,
        color = color,
        textAlign = textAlign,
    )
}

@Suppress("VerifyStrings")
@Preview
@Composable
private fun NutrientPhrasePreview() {
    PreviewContent {
        val aim = currentVisibilityPolicy().resolve(Metric.Calories, 2100.0, Surface.Settings)
        NutrientPhrase(Metric.Calories, aim, phrase = { "Aim for about $it, before activity" })
    }
}
