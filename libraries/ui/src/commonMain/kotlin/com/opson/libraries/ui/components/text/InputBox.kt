package com.opson.libraries.ui.components.text

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.typography.TypographyResource

/**
 * The handoff's text input: a 1dp `line` border on a flat fill, the value, and
 * an optional muted suffix.
 *
 * Built on foundation's `BasicTextField` on purpose. That is what exposes the
 * value as `EditableText` semantics, which `CaloriesHiddenLeakTest` reads alongside
 * `Text`: an Energy field holding 190 next to a `kcal` suffix scans as
 * "190 kcal" and fails the test in No numbers, exactly like a printed amount would.
 * A field drawn any other way would be invisible to it. With a password
 * transformation the semantics carry the bullets, not the value.
 *
 * [filter] runs on every edit before anything else sees it, so a keystroke it
 * drops never shows, even for a frame.
 *
 * Every return key leads out of the keyboard, and where there is none a Done bar stands in for
 * it (see [rememberFieldIme]).
 */
@Composable
internal fun InputBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    placeholder: String?,
    suffix: String?,
    enabled: Boolean,
    singleLine: Boolean,
    minLines: Int,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    visualTransformation: VisualTransformation,
    textAlign: TextAlign,
    typography: TypographyResource,
    fill: ColorResource,
    shape: Shape,
    contentPadding: PaddingValues,
    minHeight: Dp,
    borderColor: ColorResource = AppTheme.colors.line,
    filter: (String) -> String = { it },
) {
    val typed = rememberTypedText(value, onValueChange)
    val ink = AppTheme.colors.ink
    val ime = rememberFieldIme(keyboardActions, keyboardOptions.keyboardType, singleLine)
    BasicTextField(
        value = typed.text,
        onValueChange = { typed.onType(filter(it)) },
        modifier = modifier.then(ime.modifier),
        enabled = enabled,
        textStyle = typography.style(ink.color).copy(textAlign = textAlign),
        keyboardOptions = keyboardOptions,
        keyboardActions = ime.actions,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        minLines = minLines,
        visualTransformation = visualTransformation,
        cursorBrush = SolidColor(AppTheme.colors.accent.color),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = minHeight)
                    .clip(shape)
                    .background(fill)
                    .border(1.dp, borderColor.color, shape)
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                // Min constraints passed down so the inner field spans the box and a centred
                // value is centred in the box, not in its own width.
                Box(modifier = Modifier.weight(1f), propagateMinConstraints = true) {
                    if (typed.text.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            typography = typography,
                            color = AppTheme.colors.muted,
                            textAlign = textAlign,
                            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    innerTextField()
                }
                if (suffix != null) {
                    Text(text = suffix, typography = AppTheme.type.body, color = AppTheme.colors.muted, maxLines = 1)
                }
            }
        },
    )
}
