package com.opson.libraries.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.libraries.ui.system.color.ColorResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.min

/**
 * Four corner brackets that show where to hold a barcode, drawn over a camera
 * preview. Size it with [modifier]; the brackets sit in its corners. [color]
 * defaults to [AppTheme.colors.onHero], white in both themes, because what is
 * under it is a camera image, not the theme.
 */
@Composable
fun ScanFrame(
    modifier: Modifier = Modifier,
    color: ColorResource = AppTheme.colors.onHero,
) {
    val ink = color.color
    Canvas(modifier = modifier) {
        val stroke = StrokeWidth.toPx()
        val arm = min(size.width, size.height) * ARM_FRACTION
        val inset = stroke / 2
        val corners = listOf(
            Offset(inset, inset) to Offset(1f, 1f),
            Offset(size.width - inset, inset) to Offset(-1f, 1f),
            Offset(inset, size.height - inset) to Offset(1f, -1f),
            Offset(size.width - inset, size.height - inset) to Offset(-1f, -1f),
        )
        corners.forEach { (corner, direction) -> bracket(corner, direction, arm, stroke, ink) }
    }
}

private fun DrawScope.bracket(
    corner: Offset,
    direction: Offset,
    arm: Float,
    stroke: Float,
    color: Color,
) {
    drawLine(color, corner, corner.copy(x = corner.x + direction.x * arm), stroke, StrokeCap.Round)
    drawLine(color, corner, corner.copy(y = corner.y + direction.y * arm), stroke, StrokeCap.Round)
}

private val StrokeWidth = 3.dp
private const val ARM_FRACTION = 0.22f

@Preview(widthDp = 390)
@Composable
private fun ScanFramePreview() {
    PreviewLightDark {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .clip(AppTheme.shapes.card)
                .background(AppTheme.colors.hero),
            contentAlignment = Alignment.Center,
        ) {
            ScanFrame(modifier = Modifier.fillMaxWidth(0.78f).aspectRatio(1.8f))
        }
    }
}
