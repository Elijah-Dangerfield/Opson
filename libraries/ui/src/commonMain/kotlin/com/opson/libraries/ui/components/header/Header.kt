package com.opson.libraries.ui.components.header

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.thenIf
import com.opson.system.typography.TypographyResource
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.icon.IconButton
import com.opson.libraries.ui.components.icon.Icons
import com.opson.libraries.ui.components.text.Text
import org.jetbrains.compose.ui.tooling.preview.Preview
import opson.libraries.resources.generated.resources.Res as SharedRes
import opson.libraries.resources.generated.resources.ds_back
import org.jetbrains.compose.resources.stringResource

@Composable
fun TopBar(
    title: String? = null,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    typographyToken: TypographyResource = AppTheme.typography.Display.D900,
    backgroundColor: Color = AppTheme.colors.background.color,
    actions: @Composable () -> Unit = {},
    scrollState: ScrollState? = null,
    liftOnScroll: Boolean = scrollState != null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
            .thenIf(liftOnScroll) { elevateOnScroll(scrollState) }
            ,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (onNavigateBack != null) {
                IconButton(
                    size = IconButton.Size.Large,
                    icon = Icons.ChevronLeft(stringResource(SharedRes.string.ds_back)),
                    onClick = onNavigateBack
                )
            }
            title?.let {
                Text(text = title, typography = typographyToken)
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions()
        }
    }
}

/**
 * The lift a header gets once there is content scrolled up underneath it.
 *
 * Drawn, not cast. A real elevation — `Modifier.shadow`, or the
 * `shadowElevation` on a `graphicsLayer`, which is the same thing — throws a
 * shadow on all four sides of the node it is applied to. Two problems follow,
 * and the second is the one people report:
 *
 *  - A header sits at the top of the screen, usually under the status bar.
 *    Shadow above it has nothing to fall on, so the lift reads as a halo around
 *    the whole bar instead of the bar standing off the content.
 *  - The node being elevated here is inside `.background(...)`, so the layer
 *    holds the row's *contents* and not its fill. With nothing opaque inside it,
 *    the shadow renders against the title and the icons — the header text picks
 *    up the elevation, which is not a thing headers do.
 *
 * A header is lifted off the content *below* it and off nothing else, so that
 * is the only edge that gets anything. The gradient is painted after the
 * content and past the node's own bottom edge, which is what puts it on the
 * content rather than on the header.
 *
 * `Animatable` rather than `animateDpAsState`: the value is read inside the
 * draw lambda, in the draw phase, so a lift invalidates drawing rather than
 * recomposing the header every frame. `AnimatedStateReadInComposition` guards
 * that and it is right to.
 */
@Composable
private fun Modifier.elevateOnScroll(
    scrollState: ScrollState?,
): Modifier {

    checkNotNull(scrollState) {
        "ScrollState should not be null when liftOnScroll is true"
    }

    val shadowColor = AppTheme.colors.shadow.color
    val lift = remember { Animatable(0f) }
    val lifted = scrollState.canScrollBackward
    LaunchedEffect(lifted) {
        lift.animateTo(if (lifted) 1f else 0f, tween(durationMillis = LiftMillis))
    }

    return this.drawWithContent {
        drawContent()
        if (lift.value <= 0f) return@drawWithContent
        val depth = ShadowDepth.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    shadowColor.copy(alpha = shadowColor.alpha * ShadowPeak * lift.value),
                    shadowColor.copy(alpha = 0f),
                ),
                startY = size.height,
                endY = size.height + depth,
            ),
            topLeft = Offset(0f, size.height),
            size = Size(size.width, depth),
        )
    }
}

/** How far the lift reaches onto the content. Short: it is a hint, not a scrim. */
private val ShadowDepth = Dimension.D400

/**
 * The theme's shadow colour at full strength draws a crisp dark line rather
 * than a shadow — the giveaway that it is a gradient and not a lift is that you
 * can see where it starts.
 */
private const val ShadowPeak = 0.55f

private const val LiftMillis = 180

@Preview
@Composable
private fun PreviewHeader() {
    PreviewContent {
        com.opson.libraries.ui.components.header.TopBar(
            title = "Heading Title",
        )
    }
}

