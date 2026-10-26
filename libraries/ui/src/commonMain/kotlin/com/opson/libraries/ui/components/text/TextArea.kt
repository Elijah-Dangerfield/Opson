package com.opson.libraries.ui.components.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.typography.TypographyResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A multi-line note: the day note, a meal note, a description to parse. The
 * handoff's `<textarea>`, three lines tall by default and growing with the text.
 *
 * Exposes its value as `EditableText` semantics (see [InputBox]).
 */
@Composable
fun TextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    minLines: Int = 3,
    enabled: Boolean = true,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    typography: TypographyResource = AppTheme.type.lead,
    borderColor: ColorResource = AppTheme.colors.line,
) {
    InputBox(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = placeholder,
        suffix = null,
        enabled = enabled,
        singleLine = false,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
        keyboardActions = keyboardActions,
        visualTransformation = VisualTransformation.None,
        textAlign = TextAlign.Start,
        typography = typography,
        fill = AppTheme.colors.surface,
        shape = AppTheme.shapes.inputLarge,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        minHeight = 0.dp,
        borderColor = borderColor,
    )
}

@Preview(widthDp = 390)
@Composable
private fun TextAreaPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextArea(value = "", onValueChange = {}, placeholder = "Dinner at Lola's for her birthday")
            var note by remember { mutableStateOf("Ate outside with Sam. Felt relaxed.") }
            TextArea(value = note, onValueChange = { note = it })
        }
    }
}
