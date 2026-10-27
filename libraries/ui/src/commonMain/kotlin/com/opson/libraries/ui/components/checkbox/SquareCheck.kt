package com.opson.libraries.ui.components.checkbox

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The rounded square tick of design sync 2: the accent filled with a white check when [checked],
 * an outline when not. Drawing only; the row it sits in carries the toggle and its semantics, so
 * the whole row is the target (select foods, the summary card's stats).
 */
@Composable
fun SquareCheck(
    checked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) = Check(checked, RoundedCornerShape(8.dp), modifier, size)

/**
 * The round tick of design sync 4's select mode (13.3b): the accent filled with a check when
 * [checked], a ring when not. Drawing only, as [SquareCheck].
 */
@Composable
fun RoundCheck(
    checked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) = Check(checked, CircleShape, modifier, size)

@Composable
private fun Check(checked: Boolean, shape: Shape, modifier: Modifier, size: Dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(
                if (checked) {
                    Modifier.background(AppTheme.colors.accent)
                } else {
                    Modifier.background(AppTheme.colors.surface).border(2.dp, AppTheme.colors.line.color, shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(icon = AppIcons.Check, size = size * CHECK_SHARE, color = AppTheme.colors.onAccent)
    }
}

private const val CHECK_SHARE = 0.7f

@Preview
@Composable
private fun SquareCheckPreview() {
    PreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SquareCheck(checked = true)
            SquareCheck(checked = false)
            RoundCheck(checked = true)
            RoundCheck(checked = false)
        }
    }
}
