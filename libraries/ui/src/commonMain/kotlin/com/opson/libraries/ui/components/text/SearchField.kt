package com.opson.libraries.ui.components.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.core.Catching
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The logger's search bar: a search glyph and a single-line field on a surface
 * pill, no border. [trailing] is for a button inside the bar (the barcode
 * button, once scanning ships).
 *
 * [focusOnOpen] asks for the keyboard the first time the field composes, for
 * "Log something new" opening straight into search.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusOnOpen: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val inPreview = LocalInspectionMode.current
    LaunchedEffect(focusOnOpen) {
        // Without a window there is nothing to focus, and requesting it throws.
        if (focusOnOpen && !inPreview) Catching { focusRequester.requestFocus() }
    }
    val ink = AppTheme.colors.ink
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
        textStyle = AppTheme.type.body.style(ink.color),
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
        cursorBrush = SolidColor(AppTheme.colors.accent.color),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 50.dp)
                    .clip(AppTheme.shapes.inputLarge)
                    .background(AppTheme.colors.surface)
                    .padding(start = 12.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon = AppIcons.Search, size = 22.dp, color = AppTheme.colors.muted)
                Box(modifier = Modifier.weight(1f).padding(vertical = 12.dp), propagateMinConstraints = true) {
                    if (value.isEmpty()) {
                        Text(text = placeholder, typography = AppTheme.type.body, color = AppTheme.colors.muted, maxLines = 1)
                    }
                    innerTextField()
                }
                trailing?.invoke()
            }
        },
    )
}

/**
 * A one-line text input with the handoff's border, for short free text such as
 * "2 eggs and toast". Exposes its value as `EditableText` semantics (see
 * [InputBox]).
 */
@Composable
fun SingleLineField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    InputBox(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        suffix = null,
        enabled = true,
        singleLine = true,
        minLines = 1,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
        keyboardActions = keyboardActions,
        visualTransformation = VisualTransformation.None,
        textAlign = TextAlign.Start,
        typography = AppTheme.type.body,
        fill = AppTheme.colors.surface,
        shape = AppTheme.shapes.inputLarge,
        contentPadding = PaddingValues(horizontal = 14.dp),
        minHeight = 48.dp,
    )
}

@Preview(widthDp = 390)
@Composable
private fun SearchFieldPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            var query by remember { mutableStateOf("") }
            SearchField(value = query, onValueChange = { query = it }, placeholder = "Search for a food")
            SearchField(value = "banana", onValueChange = {}, placeholder = "Search for a food")
            SingleLineField(value = "", onValueChange = {}, placeholder = "2 eggs and toast")
        }
    }
}
