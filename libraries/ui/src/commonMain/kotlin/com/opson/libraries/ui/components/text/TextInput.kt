package com.opson.libraries.ui.components.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A one-line text input with an optional muted [label] above it: a name, a brand. The same box
 * as [NumberField], for words. Capitalizes sentences, since what goes in here is a name.
 *
 * Exposes its value as `EditableText` semantics (see [InputBox]).
 */
@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val field = @Composable { fieldModifier: Modifier ->
        InputBox(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier.fillMaxWidth(),
            placeholder = placeholder,
            suffix = null,
            enabled = enabled,
            singleLine = true,
            minLines = 1,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
            keyboardActions = keyboardActions,
            visualTransformation = VisualTransformation.None,
            textAlign = TextAlign.Start,
            typography = AppTheme.type.lead,
            fill = AppTheme.colors.bg,
            shape = AppTheme.shapes.inputLarge,
            contentPadding = PaddingValues(horizontal = 14.dp),
            minHeight = TextInputMinHeight,
        )
    }
    if (label == null) {
        field(modifier)
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = label, typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.muted)
            field(Modifier)
        }
    }
}

/** Design v3's form field: 48 dp, so the box is also a full touch target. */
private val TextInputMinHeight = 48.dp

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun TextInputPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextInput(value = "Lola's pandesal", onValueChange = {}, label = "Name")
            TextInput(value = "", onValueChange = {}, label = "Brand or where it's from · optional", placeholder = "Corner bakery")
        }
    }
}
