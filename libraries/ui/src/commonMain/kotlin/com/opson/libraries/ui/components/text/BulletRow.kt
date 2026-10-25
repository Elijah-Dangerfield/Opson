package com.opson.libraries.ui.components.text

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.system.HorizontalSpacerD800

@Composable
fun BulletRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = { }
) {
    Row(modifier = modifier) {
        Text(text = "•")
        HorizontalSpacerD800()
        content()
    }
}