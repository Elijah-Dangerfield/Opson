package com.opson.libraries.ui.components.text

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.system.AppTheme
import com.opson.system.typography.TypographyResource
import com.opson.libraries.ui.PreviewContent
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The design system's bare text field: the theme's text style, cursor and selection colours, and
 * the shared keyboard handling (every return key closes the keyboard or moves on, and iOS number
 * pads and multi-line fields get a Done bar; see [rememberFieldIme]). Draw the box with
 * [decorationBox]. For the handoff's own inputs, reach for [NumberField], [TextInput] or [TextArea].
 */
@Composable
fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    typographyToken: TypographyResource = LocalTextConfig.current.typography
        ?: AppTheme.typography.Default,
    color: Color = AppTheme.colors.text.color,
    disabledColor: Color = AppTheme.colors.textDisabled.color,
    textAlign: TextAlign = TextAlign.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    cursorBrush: Brush = SolidColor(AppTheme.colors.accentPrimary.color),
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit =
        @Composable { innerTextField -> innerTextField() },
) {
    val textSelectionColors = TextSelectionColors(
        handleColor = AppTheme.colors.accentPrimary.color,
        backgroundColor = AppTheme.colors.accentPrimary.color.copy(alpha = 0.4F)
    )

    val typed = rememberTypedText(value, onValueChange)
    val ime = rememberFieldIme(keyboardActions, keyboardOptions.keyboardType, singleLine)
    CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
        SelectionContainer {
            androidx.compose.foundation.text.BasicTextField(
                value = typed.text,
                onValueChange = typed::onType,
                modifier = modifier.then(ime.modifier),
                enabled = enabled,
                readOnly = readOnly,
                textStyle = typographyToken.style(if (enabled) color else disabledColor).merge(TextStyle(textAlign = textAlign)),
                keyboardOptions = keyboardOptions,
                keyboardActions = ime.actions,
                singleLine = singleLine,
                maxLines = maxLines,
                minLines = minLines,
                visualTransformation = visualTransformation,
                onTextLayout = onTextLayout,
                interactionSource = interactionSource,
                cursorBrush = cursorBrush,
                decorationBox = decorationBox
            )
        }
    }
}

@Composable
@Preview
private fun PreviewBasicTextField() {
    PreviewContent(modifier = Modifier.padding(24.dp)) {
        com.opson.libraries.ui.components.text.BasicTextField(
            value = "Hello World",
            onValueChange = { })
    }
}
