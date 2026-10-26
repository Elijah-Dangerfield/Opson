package com.opson.libraries.ui.components.checkbox

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.opson.libraries.ui.bounceClick
import com.opson.system.AppTheme
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.system.minTouchTarget

@Composable
fun CircleCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    Box(
        modifier = modifier
            .minTouchTarget()
            .height(IntrinsicSize.Min)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(AppTheme.colors.onBackground.color)
            .clipToBounds()
            .bounceClick(
                enabled = enabled,
                mutableInteractionSource = interactionSource
            ) { onCheckedChange?.invoke(!checked) },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Box(
            modifier =
            Modifier
                .clip(CircleShape)
                .background(AppTheme.colors.background.color)
                .fillMaxSize(0.8f),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            if (checked) {
                Box(
                    modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(AppTheme.colors.onBackground.color)
                        .fillMaxSize(0.8f)
                )
            }
        }
    }
}

@Composable
@Preview
private fun Unchecked() {
    PreviewContent {
        var isChecked by remember { mutableStateOf(false) }
        CircleCheckbox(
            checked = isChecked,
            onCheckedChange = {
                isChecked = it
            }
        )
    }
}

@Composable
@Preview
private fun Checked() {
    PreviewContent {
        var isChecked by remember { mutableStateOf(true) }
        CircleCheckbox(
            checked = isChecked,
            onCheckedChange = {
                isChecked = it
            }
        )
    }
}