package com.opson.libraries.ui.components.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A single-line input that can hide what is typed behind dots, for a weigh-in
 * the user has chosen not to see. Not a password field: nothing here is
 * secret from the user's phone, only from their eyes.
 *
 * While [masked], the value never reaches the screen or the accessibility
 * tree: the `EditableText` semantics carry the dots, and the field is marked
 * as a password so a screen reader does not read the number aloud either.
 * The caller decides [masked] from the policy (anything below Show for weight).
 */
@Composable
fun MaskedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    masked: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    unit: String? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
) {
    InputBox(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        suffix = unit,
        enabled = enabled,
        singleLine = true,
        minLines = 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = keyboardActions,
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        textAlign = TextAlign.Start,
        typography = AppTheme.type.body,
        fill = AppTheme.colors.bg,
        shape = AppTheme.shapes.input,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
        minHeight = FieldMinHeight,
    )
}

@Preview(widthDp = 390)
@Composable
private fun MaskedTextFieldPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MaskedTextField(value = "72.6", onValueChange = {}, masked = true, placeholder = "kg")
            MaskedTextField(value = "72.6", onValueChange = {}, masked = false, placeholder = "kg")
            MaskedTextField(value = "", onValueChange = {}, masked = true, placeholder = "kg")
        }
    }
}
