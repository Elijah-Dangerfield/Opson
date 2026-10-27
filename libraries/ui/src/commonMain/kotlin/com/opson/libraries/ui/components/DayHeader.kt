package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.thenIf
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Today's header (design sync 4, 13.3a): ‹, the day ("Today", or "Sun, Oct 25") with a small ▾, ›,
 * then [actions] on the right (⋮). On the page colour. Tapping the day opens the calendar, which
 * draws this same header with [expanded] true, the month as the title and a ▴. Give the chevrons
 * descriptions with `described`.
 */
@Composable
fun DayHeader(
    title: String,
    previousIcon: IconResource,
    nextIcon: IconResource,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTitleClick: () -> Unit,
    titleDescription: String,
    modifier: Modifier = Modifier,
    previousEnabled: Boolean = true,
    nextEnabled: Boolean = true,
    expanded: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The day and its chevrons take what the actions leave, so ⋮ always sits on the right
        // edge. Sharing one weight with the actions split the row in half instead, and a short
        // title ("Today") left ⋮ stranded mid-row.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            HeaderIconButton(icon = previousIcon, onClick = onPrevious, enabled = previousEnabled)
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .defaultMinSize(minHeight = MinTouchTarget)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = titleDescription
                    }
                    .bounceClick(scaleDown = 0.97f, onClick = onTitleClick)
                    .padding(start = 4.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    typography = AppTheme.type.dayLabel,
                    color = AppTheme.colors.ink,
                    // A month and year ("October 2026") may wrap, as the design draws it; a day never does.
                    maxLines = if (expanded) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    icon = AppIcons.ArrowDropDown,
                    size = 28.dp,
                    color = AppTheme.colors.muted,
                    modifier = Modifier.thenIf(expanded) { rotate(HALF_TURN) },
                )
            }
            HeaderIconButton(icon = nextIcon, onClick = onNext, enabled = nextEnabled)
        }
        Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** A 48 round icon button in ink on the page colour: the header's chevrons and ⋮. */
@Composable
fun HeaderIconButton(
    icon: IconResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(MinTouchTarget)
            .clip(CircleShape)
            .thenIf(selected) { background(AppTheme.colors.line) }
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
            }
            .bounceClick(enabled = enabled, scaleDown = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon = icon, size = 28.dp, color = if (enabled) AppTheme.colors.ink else AppTheme.colors.muted.withAlpha(DISABLED_ALPHA))
    }
}

/** An accent-tint card with an icon, a bold accent title and a body: "Planned meals count as logged". */
@Composable
fun InfoBanner(
    icon: IconResource,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.card)
            .background(AppTheme.colors.accentTint)
            .padding(HeroCardPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon = icon, size = 28.dp, color = AppTheme.colors.accent)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.accent)
            Text(text = body, typography = AppTheme.type.body, color = AppTheme.colors.accent)
        }
    }
}

/** A small surface pill with a coloured icon and a bold count: "3 meals logged", "3 glasses". */
@Composable
fun CountChip(
    icon: IconResource,
    iconColor: ColorResource,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = MinTouchTarget)
            .clip(AppTheme.shapes.inputLarge)
            .background(AppTheme.colors.surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon = icon, size = 22.dp, color = iconColor)
        Text(text = text, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink, maxLines = 1)
    }
}

/**
 * A bar with a range drawn as a dashed box (design v3 "the dashed box marks your range"), on the
 * hero by default. [fill], [rangeStart] and [rangeEnd] are fractions of the bar; the fill stops at
 * the end, so going past the range shows nothing new. 14 tall track, 18 tall box. Nutrients detail
 * draws it on a white card with [RangeBarDefaults.onSurface].
 *
 * When [rangeStart] and [rangeEnd] meet, the goal is one number, and it is drawn as a solid mark
 * across the bar at that point instead of a box. Both at 0 is no goal at all, and draws nothing.
 *
 * [tick] marks one more point on the bar in [tickColor] (Burned, on the Range bar card).
 *
 * [segments] split the fill end to end by their weights, each in its own colour with a hairline
 * between (Today's eaten bar by macro, design sync 2). Empty draws the fill in one colour.
 */
@Composable
fun RangeBar(
    fill: Float,
    rangeStart: Float,
    rangeEnd: Float,
    modifier: Modifier = Modifier,
    colors: RangeBarColors = RangeBarDefaults.onHero(),
    height: Dp = RangeBarDefaults.Height,
    segments: List<RangeBarSegment> = emptyList(),
    tick: Float? = null,
    tickColor: ColorResource = colors.box,
) {
    val tickPaint = tickColor.color
    val track = colors.track.color
    val fillColor = colors.fill.color
    val parts = segments.filter { it.weight > 0f }.map { it.weight to it.color.color }
    val boxColor = colors.box.color
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val trackHeight = size.height * TRACK_SHARE
                val top = (size.height - trackHeight) / 2
                val radius = CornerRadius(trackHeight / 2)
                drawRoundRect(track, topLeft = Offset(0f, top), size = Size(size.width, trackHeight), cornerRadius = radius)
                val filled = size.width * fill.coerceIn(0f, 1f)
                if (filled > 0f && parts.isEmpty()) {
                    drawRoundRect(fillColor, topLeft = Offset(0f, top), size = Size(filled, trackHeight), cornerRadius = radius)
                } else if (filled > 0f) {
                    clipPath(Path().apply { addRoundRect(RoundRect(0f, top, filled, top + trackHeight, radius)) }) {
                        val total = parts.sumOf { it.first.toDouble() }.toFloat()
                        var x = 0f
                        parts.forEachIndexed { index, (weight, color) ->
                            val w = filled * weight / total
                            val gap = if (index < parts.lastIndex) SEGMENT_GAP else 0f
                            drawRect(color, topLeft = Offset(x, top), size = Size((w - gap).coerceAtLeast(0f), trackHeight))
                            x += w
                        }
                    }
                }
                val start = size.width * rangeStart.coerceIn(0f, 1f)
                val end = size.width * rangeEnd.coerceIn(0f, 1f)
                if (end > start) {
                    drawRoundRect(
                        color = boxColor,
                        topLeft = Offset(start, 1f),
                        size = Size(end - start, size.height - 2f),
                        cornerRadius = CornerRadius(BOX_RADIUS),
                        style = Stroke(width = STROKE, pathEffect = dash),
                    )
                } else if (start > 0f) {
                    // One number: a mark across the bar where it sits, never a box around nothing.
                    drawLine(boxColor, Offset(start, 1f), Offset(start, size.height - 1f), strokeWidth = MARK_WIDTH, cap = StrokeCap.Round)
                }
                if (tick != null) {
                    val x = size.width * tick.coerceIn(0f, 1f)
                    drawLine(tickPaint, Offset(x, 0f), Offset(x, size.height), strokeWidth = MARK_WIDTH, cap = StrokeCap.Round)
                }
            },
    )
}

/** One piece of a [RangeBar]'s fill: [weight] relative to the others, drawn in [color]. */
data class RangeBarSegment(val weight: Float, val color: ColorResource)

/** The three colours of a [RangeBar]. */
data class RangeBarColors(val track: ColorResource, val fill: ColorResource, val box: ColorResource)

object RangeBarDefaults {
    /** Today's summary: the bar the day is read from (design v3, 2.1). */
    val Height: Dp = 22.dp

    /** A row in a list, as Nutrients' energy row: about half as tall (design v3, 5.4). */
    val CompactHeight: Dp = 12.dp

    /** White on the hero colour, as Today's summary. */
    @Composable
    fun onHero(): RangeBarColors = RangeBarColors(
        track = AppTheme.colors.onHero.withAlpha(RANGE_TRACK_ALPHA),
        fill = AppTheme.colors.onHero,
        box = AppTheme.colors.onHero.withAlpha(RANGE_BOX_ALPHA),
    )

    /** Ink on a white card: the energy row on Nutrients detail. */
    @Composable
    fun onSurface(): RangeBarColors = RangeBarColors(
        track = AppTheme.colors.track,
        fill = AppTheme.colors.ink,
        box = AppTheme.colors.accent,
    )

    /**
     * Today's summary card on the surface colour (design sync 2): a quiet track, the box in muted
     * ink so it reads as a mark rather than an action, and the fill split by macro by the caller.
     */
    @Composable
    fun summary(): RangeBarColors = RangeBarColors(
        track = AppTheme.colors.track,
        fill = AppTheme.colors.inkSoft,
        box = AppTheme.colors.inkSoft,
    )
}

private const val TRACK_SHARE = 0.6f
private const val SEGMENT_GAP = 3f
private const val DASH = 8f
private const val STROKE = 3f
private const val BOX_RADIUS = 10f
private const val MARK_WIDTH = 5f
private const val RANGE_TRACK_ALPHA = 0.16f
private const val RANGE_BOX_ALPHA = 0.7f
private const val DISABLED_ALPHA = 0.5f
private const val HALF_TURN = 180f

/** A dashed outline in [color] round the element, with [shape]'s rounding as [radius]. */
fun Modifier.dashedBorder(color: Color, radius: Dp, strokeWidth: Dp = 1.5.dp): Modifier = drawBehind {
    val stroke = strokeWidth.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))),
    )
}

/** A pill action: "Plan it" on the accent tint, or "Copy" on the solid accent when [solid]. 48 tall. */
@Composable
fun PillAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = MinTouchTarget)
            .clip(AppTheme.shapes.pill)
            .background(if (solid) AppTheme.colors.accent else AppTheme.colors.accentTint)
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.95f, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            typography = AppTheme.type.bodyBold.ExtraBold,
            color = if (solid) AppTheme.colors.onAccent else AppTheme.colors.accent,
            maxLines = 1,
        )
    }
}

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun DayHeaderPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
            DayHeader(
                title = "Today",
                previousIcon = AppIcons.ChevronLeft.described("Previous day"),
                nextIcon = AppIcons.ChevronRight.described("Next day"),
                onPrevious = {},
                onNext = {},
                onTitleClick = {},
                titleDescription = "Pick a day",
            ) {
                HeaderIconButton(icon = AppIcons.MoreVert, onClick = {})
            }
            InfoBanner(icon = AppIcons.EventNote, title = "Planned meals count as logged", body = "Edit anything tomorrow if plans change.")
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                CountChip(icon = AppIcons.Restaurant, iconColor = AppTheme.colors.accent, text = "3 meals logged")
                CountChip(icon = AppIcons.WaterDrop, iconColor = AppTheme.colors.nutrients.water.content, text = "3 glasses")
            }
            HeroCard { RangeBar(fill = 0.45f, rangeStart = 0.69f, rangeEnd = 0.83f) }
            RangeBar(
                fill = 0.8f,
                rangeStart = 0.69f,
                rangeEnd = 0.83f,
                colors = RangeBarDefaults.onSurface(),
                height = RangeBarDefaults.CompactHeight,
            )
            val n = AppTheme.colors.nutrients
            RangeBar(
                fill = 0.45f,
                rangeStart = 0.69f,
                rangeEnd = 0.83f,
                colors = RangeBarDefaults.onSurface(),
                segments = listOf(
                    RangeBarSegment(0.16f, n.protein.content),
                    RangeBarSegment(0.52f, n.carbs.content),
                    RangeBarSegment(0.32f, n.fat.content),
                ),
            )
            // One number: a mark, not a box.
            RangeBar(fill = 0.6f, rangeStart = 0.76f, rangeEnd = 0.76f, colors = RangeBarDefaults.onSurface())
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                PillAction(label = "Plan it", onClick = {})
                PillAction(label = "Copy", onClick = {}, solid = true)
            }
            Box(Modifier.width(120.dp).height(60.dp).dashedBorder(AppTheme.colors.line.color, 22.dp))
        }
    }
}
