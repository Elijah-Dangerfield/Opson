package com.opson.libraries.ui.components.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * What a number field accepts while the user types. Pure, so a ViewModel that
 * validates the same input (a weigh-in, a custom amount) can call it too.
 */
object NumberInput {

    /**
     * Keeps digits and, when [allowDecimal], the first decimal point. A comma
     * counts as a decimal point, since that is what a decimal keypad types in
     * much of the world. Everything else is dropped, so a paste of "1,5 kg"
     * becomes "1.5".
     */
    fun sanitize(raw: String, allowDecimal: Boolean): String {
        val out = StringBuilder(raw.length)
        var seenPoint = false
        for (char in raw) {
            when {
                char.isAsciiDigit() -> out.append(char)
                allowDecimal && !seenPoint && (char == '.' || char == ',') -> {
                    out.append('.')
                    seenPoint = true
                }
            }
        }
        return out.toString()
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}

/**
 * A numeric input: an optional label above, the value, and an optional muted
 * [unit] after it ("g", "kcal", "ml"). Typed text goes through
 * [NumberInput.sanitize] before [onValueChange] sees it.
 *
 * The field shows the string it is given; it never formats or rounds a
 * number. Exposes its value as `EditableText` semantics (see [InputBox]), which
 * matters here more than anywhere: an Energy field is a calorie amount.
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    unit: String? = null,
    placeholder: String? = null,
    allowDecimal: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val field = @Composable { fieldModifier: Modifier ->
        InputBox(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier,
            placeholder = placeholder,
            suffix = unit,
            enabled = enabled,
            singleLine = true,
            minLines = 1,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = imeAction,
                autoCorrectEnabled = false,
            ),
            keyboardActions = keyboardActions,
            visualTransformation = VisualTransformation.None,
            textAlign = textAlign,
            typography = AppTheme.type.lead,
            fill = AppTheme.colors.bg,
            shape = AppTheme.shapes.input,
            contentPadding = PaddingValues(horizontal = 12.dp),
            minHeight = FieldMinHeight,
            filter = { NumberInput.sanitize(it, allowDecimal) },
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

/** The handoff's 46dp single-line input height. */
internal val FieldMinHeight = 46.dp

@Preview(widthDp = 390)
@Composable
private fun NumberFieldPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(value = "190", onValueChange = {}, label = "Energy", unit = "kcal", modifier = Modifier.weight(1f))
                NumberField(value = "", onValueChange = {}, label = "Protein", unit = "g", placeholder = "0", modifier = Modifier.weight(1f))
            }
            NumberField(
                value = "1",
                onValueChange = {},
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp),
            )
            NumberField(value = "", onValueChange = {}, placeholder = "ml", allowDecimal = false)
        }
    }
}
