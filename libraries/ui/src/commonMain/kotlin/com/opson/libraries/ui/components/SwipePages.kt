package com.opson.libraries.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import opson.libraries.resources.generated.resources.Res as SharedRes
import opson.libraries.resources.generated.resources.ds_page_of
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Pages side by side that the person swipes between, as Today's summary card (design sync 2,
 * 2.1 and 2.1b). Every page is composed and measured at once, and the row is as tall as the page
 * showing. Mid-swipe the height runs between the two pages' heights in step with the finger, so
 * the card grows or shrinks as the next page slides in rather than jumping once it lands. A dot
 * tapped slides there the same way. The height is worked out in the measure pass from the drag,
 * so a swipe relayouts the row each frame and never recomposes it. A shorter page sits at the top
 * of a taller row; a taller one is clipped at the bottom until the row has grown to fit it.
 *
 * The pages off screen stay in the tree for anything that reads it (the leak tests) but are
 * hidden from screen readers; [PageIndicator] is how those move between pages.
 *
 * [page] is the one showing; [onPageChange] reports a swipe that settled on another.
 */
@Composable
fun SwipePages(
    page: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (page: Int) -> Unit,
) {
    var current by remember { mutableIntStateOf(page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) }
    var drag by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(page, pageCount) {
        val wanted = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (wanted != current) {
            // Slid to, like a swipe, so the height eases there too. Unmeasured, there is nothing to slide.
            if (width > 0) animate(drag, -(wanted - current) * width.toFloat()) { value, _ -> drag = value }
            current = wanted
            drag = 0f
        } else if (drag != 0f) {
            // Back to the page it started from before a slide landed: that slide was cancelled part
            // way, so ease the pages back rather than leave them where it stopped.
            animate(drag, 0f) { value, _ -> drag = value }
            drag = 0f
        }
    }
    val dragState = rememberDraggableState { delta ->
        val min = -(pageCount - 1 - current) * width.toFloat()
        val max = current * width.toFloat()
        drag = (drag + delta).coerceIn(min, max)
    }
    Layout(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { width = it.width }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = pageCount > 1,
                onDragStopped = { velocity ->
                    val threshold = width * SETTLE_SHARE
                    val target = when {
                        drag < -threshold || velocity < -FLING_VELOCITY -> current + 1
                        drag > threshold || velocity > FLING_VELOCITY -> current - 1
                        else -> current
                    }.coerceIn(0, pageCount - 1)
                    scope.launch {
                        animate(drag, -(target - current) * width.toFloat()) { value, _ -> drag = value }
                        if (target != current) {
                            current = target
                            onPageChange(target)
                        }
                        drag = 0f
                    }
                },
            ),
        content = {
            repeat(pageCount) { index ->
                Box(Modifier.semantics { if (index != current) hideFromAccessibility() }) { content(index) }
            }
        },
    ) { measurables, constraints ->
        val pageWidth = constraints.maxWidth
        val pageConstraints = Constraints.fixedWidth(pageWidth).copy(maxHeight = constraints.maxHeight)
        val placeables = measurables.map { it.measure(pageConstraints) }
        val height = settledHeight(placeables.map { it.height }, current, drag, pageWidth).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(pageWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                placeable.place((index - current) * pageWidth + drag.roundToInt(), 0)
            }
        }
    }
}

/**
 * How tall the row is with [current] showing and the pages [drag]ged by that many pixels: the
 * two pages either side of where the drag has got to, mixed by how far it has got. At rest it is
 * [current]'s own height.
 */
internal fun settledHeight(heights: List<Int>, current: Int, drag: Float, pageWidth: Int): Int {
    if (heights.isEmpty()) return 0
    val last = heights.lastIndex
    if (pageWidth <= 0) return heights[current.coerceIn(0, last)]
    val position = (current - drag / pageWidth).coerceIn(0f, last.toFloat())
    val from = floor(position).toInt()
    val to = (from + 1).coerceAtMost(last)
    val fraction = position - from
    return (heights[from] + (heights[to] - heights[from]) * fraction).roundToInt()
}

/**
 * Which page of a [SwipePages] is showing: a plain row of small dots, the current one filled in
 * [selectedColor] and the rest in [unselectedColor], all the same size. Each dot is a tab that
 * opens its page, which is how a screen reader moves between pages. Each dot's tap area is 18 by
 * 28 dp, bigger than the dot but under the 48 dp target; swiping the pages is the main way across.
 * TODO(review): design v3 asks 48 dp for every target. Growing these spreads the dots or adds
 * height under Today's card, so it is the owner's call (docs/review/findings/ui-library.md).
 */
@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: ColorResource = AppTheme.colors.ink,
    unselectedColor: ColorResource = AppTheme.colors.muted.withAlpha(UNSELECTED_ALPHA),
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(pageCount) { page ->
            val description = stringResource(SharedRes.string.ds_page_of, page + 1, pageCount)
            Box(
                modifier = Modifier
                    .size(width = DotTarget, height = DotTargetHeight)
                    .selectable(selected = page == currentPage, role = Role.Tab, onClick = { onPageSelected(page) })
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(DotSize).background(if (page == currentPage) selectedColor else unselectedColor, CircleShape))
            }
        }
    }
}

private val DotSize = 7.dp
private val DotTarget = 18.dp
private val DotTargetHeight = 28.dp
private const val UNSELECTED_ALPHA = 0.35f

private const val SETTLE_SHARE = 0.25f
private const val FLING_VELOCITY = 600f

@Preview(widthDp = 390)
@Composable
private fun SwipePagesPreview() {
    PreviewLightDark {
        var page by remember { mutableIntStateOf(0) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SwipePages(page = page, pageCount = 2, onPageChange = { page = it }) { index ->
                Text(
                    text = if (index == 0) "Energy page" else "Macros page\nwith a second line",
                    typography = AppTheme.type.cardTitle,
                    color = AppTheme.colors.ink,
                    modifier = Modifier.padding(20.dp),
                )
            }
            PageIndicator(pageCount = 2, currentPage = page, onPageSelected = { page = it }, modifier = Modifier.align(Alignment.CenterHorizontally))
            PageIndicator(pageCount = 3, currentPage = 1, onPageSelected = {}, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
