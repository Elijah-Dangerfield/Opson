package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The 10dp colour dot in front of a nutrient's name (the Targets macro rows, chart legends). */
@Composable
fun LegendDot(
    color: ColorResource,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(10.dp).background(color, CircleShape))
}

@Suppress("VerifyStrings")
@Preview
@Composable
private fun LegendDotPreview() {
    PreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(color = AppTheme.colors.nutrients.protein.content)
            Text("Protein", typography = AppTheme.type.bodyBold)
        }
    }
}
