package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A bar with what is already there solid and what would be added striped on top: the amount
 * sheet's "Your day with this" (design sync 2, 3.3). [before] and [after] are fractions of the
 * goal; both stop at full ([barFill]), so a big day is a full bar and never an overflow colour.
 * The stripes are the same colour as the solid part, so nothing reads as a warning.
 */
@Composable
fun StripedBar(
    before: Float,
    after: Float,
    color: ColorResource,
    modifier: Modifier = Modifier,
    trackColor: ColorResource = AppTheme.colors.track,
    height: Dp = 8.dp,
) {
    val fill = color.color
    val solid = barFill(before)
    val total = barFill(after).coerceAtLeast(solid)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(AppTheme.shapes.bar)
            .background(trackColor)
            .drawBehind {
                val solidWidth = size.width * solid
                val endWidth = size.width * total
                if (solidWidth > 0f) {
                    drawRoundRect(color = fill, size = Size(solidWidth, size.height), cornerRadius = CornerRadius(size.height / 2))
                }
                if (endWidth > solidWidth) {
                    val stripe = STRIPE.toPx()
                    clipRect(left = solidWidth, right = endWidth) {
                        var x = solidWidth - size.height
                        while (x < endWidth) {
                            drawLine(
                                color = fill,
                                start = Offset(x, size.height),
                                end = Offset(x + size.height, 0f),
                                strokeWidth = stripe / 2,
                            )
                            x += stripe
                        }
                    }
                }
            },
    )
}

private val STRIPE = 6.dp

@Preview(widthDp = 390)
@Composable
private fun StripedBarPreview() {
    PreviewLightDark {
        val nutrients = AppTheme.colors.nutrients
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StripedBar(before = 0.55f, after = 0.77f, color = AppTheme.colors.ink)
            StripedBar(before = 0.42f, after = 0.69f, color = nutrients.protein.content)
            StripedBar(before = 0.65f, after = 0.89f, color = nutrients.carbs.content)
            // Past the goal: full, and the same colour.
            StripedBar(before = 0.8f, after = 1.4f, color = nutrients.fat.content)
        }
    }
}
