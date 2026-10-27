package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.color.MealTone
import org.jetbrains.compose.ui.tooling.preview.Preview

/** A small filled circle: the colour key beside a macro or meal label. */
@Composable
fun ColorDot(
    color: ColorResource,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
) {
    Box(modifier.size(size).background(color, CircleShape))
}

@Preview
@Composable
private fun ColorDotPreview() {
    PreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(AppTheme.colors.meal(MealTone.Breakfast).content)
            ColorDot(AppTheme.colors.meal(MealTone.Dinner).content, size = 10.dp)
            ColorDot(AppTheme.colors.track)
        }
    }
}
