package com.opson.libraries.ui.components.chip

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.libraries.ui.PreviewContent
import com.opson.system.VerticalSpacerD1000
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Simple toggleable chip that reflects a selected state. Kept for existing callers; it is a
 * [PillChip] now, so new code should call that directly.
 */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    PillChip(label = label, selected = selected, onClick = onClick, modifier = modifier)
}


@Composable
@Preview
private fun PreviewSelectChip() {
    PreviewContent {
        Column {
            SelectChip(label = "Hello", selected = true, onClick = {})

            VerticalSpacerD1000()

            SelectChip(label = "Hello", selected = false, onClick = {})
        }
    }
}