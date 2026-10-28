package com.opson.libraries.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.tick
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.thenIfNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One line of what an inspected chart point says: [label], what it is ("Trend"), before what it
 * reads, which is one of [value] or [word]. [value] is an amount the screen's builder already
 * resolved through the visibility policy, so a mode that hides it never puts it here; [word] is a
 * reading with no amount in it, like a hunger level. [tag] is the metric's test tag on [value], so
 * the leak tests see the amount wherever it renders.
 */
@Immutable
data class ChartReading(
    val value: String? = null,
    val word: StringResource? = null,
    val label: StringResource? = null,
    val tag: String? = null,
)

/**
 * What one point of a chart says when someone inspects it: [title] is the day, week or weekday,
 * [readings] whatever the mode lets it show, which can be nothing. A chart takes one per index,
 * `null` where it draws a gap, and a gap can't be picked.
 */
@Immutable
data class ChartReadout(val title: String, val readings: List<ChartReading> = emptyList())

/** Where a chart puts its points across its width, so a finger, the guide and the label agree with the drawing. */
@Immutable
sealed interface ChartAxis {
    /** Spread from [inset] in on the left to [inset] in on the right; a lone point is centred. [TrendLine]. */
    data class Points(val inset: Dp) : ChartAxis

    /** Equal slots with [gap] between them, each point in the middle of its slot. The bar charts. */
    data class Slots(val gap: Dp) : ChartAxis
}

/** The geometry of picking a point, pure so it is tested without a composition. */
object ChartPicking {

    const val NONE = -1

    fun pointX(index: Int, count: Int, width: Float, inset: Float): Float =
        if (count > 1) inset + (width - inset * 2) / (count - 1) * index else width / 2

    fun slotCenter(index: Int, count: Int, width: Float, gap: Float): Float {
        val slot = (width - gap * (count - 1)) / count
        return index * (slot + gap) + slot / 2
    }

    /**
     * The point nearest [x], skipping gaps, so dragging across a day with nothing on it lands on
     * the day beside it rather than on nothing. An earlier point wins a tie. [NONE] when every
     * point is a gap.
     */
    fun nearest(x: Float, readouts: List<ChartReadout?>, xOf: (Int) -> Float): Int {
        var best = NONE
        var bestDistance = Float.POSITIVE_INFINITY
        readouts.forEachIndexed { index, readout ->
            if (readout == null) return@forEachIndexed
            val distance = abs(xOf(index) - x)
            if (distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }
}

internal fun ChartAxis.x(index: Int, count: Int, width: Float, density: Density): Float = with(density) {
    when (this@x) {
        is ChartAxis.Points -> ChartPicking.pointX(index, count, width, inset.toPx())
        is ChartAxis.Slots -> ChartPicking.slotCenter(index, count, width, gap.toPx())
    }
}

/**
 * Which point of a chart is picked, [ChartPicking.NONE] for none. Charts read [index] only while
 * drawing ([emphasis] in a `graphicsLayer`, the highlight in a `Canvas`), so a drag redraws the
 * chart without recomposing the card around it.
 */
@Stable
class ChartSelection(initial: Int = ChartPicking.NONE) {

    private val picked = mutableIntStateOf(initial)

    /**
     * Written from the gesture's coroutine, outside any composition. Applying the write in its own
     * snapshot hands it to the recomposer there and then; a bare global write waits for the next
     * global apply, and a tap that only clears the pick could sit on screen until something else
     * moved.
     */
    var index: Int
        get() = picked.intValue
        internal set(value) {
            Snapshot.withMutableSnapshot { picked.intValue = value }
        }

    private var release: Job? = null

    /**
     * How strongly to draw [point]: fully when it is picked or nothing is, at [faded] otherwise. A
     * chart drawn in pale tints fades less, or its other bars vanish.
     */
    fun emphasis(point: Int, faded: Float = UNPICKED_ALPHA): Float = if (index == ChartPicking.NONE || index == point) 1f else faded

    internal fun pick(point: Int, haptics: HapticFeedback) {
        if (point == ChartPicking.NONE || point == index) return
        index = point
        haptics.tick()
    }

    internal fun hold() {
        release?.cancel()
    }

    internal fun clear() {
        hold()
        index = ChartPicking.NONE
    }

    /** Lifting the finger keeps the label up long enough to read it, then lets it go. */
    internal fun releaseLater(scope: CoroutineScope) {
        hold()
        release = scope.launch {
            delay(LINGER_MILLIS)
            index = ChartPicking.NONE
        }
    }

    private companion object {
        const val UNPICKED_ALPHA = 0.35f
        const val LINGER_MILLIS = 2_500L
    }
}

/**
 * For previews and screenshots: the point every chart below starts with picked, so a whole screen
 * renders with its labels up. A chart with a gap or nothing at that index starts with none.
 */
val LocalChartPreviewPick = staticCompositionLocalOf { ChartPicking.NONE }

/** A selection that starts over whenever the chart's points change, a new range for one. */
@Composable
fun rememberChartSelection(readouts: List<ChartReadout?>): ChartSelection {
    val preview = LocalChartPreviewPick.current
    return remember(readouts) { ChartSelection(preview.takeIf { readouts.getOrNull(it) != null } ?: ChartPicking.NONE) }
}

/**
 * Makes [content], a chart drawn to [axis], inspectable: press and drag across it, or tap it, and
 * the nearest point is picked, with a guide line behind it and a label above the chart saying its
 * [readouts]. Every move to a new point ticks. Letting go keeps the label up for a moment; tapping
 * the picked point again puts it away.
 *
 * It shares the page's vertical scroll: a drag that is clearly sideways, or one that starts with a
 * short hold, scrubs; a drag that starts up or down scrolls the page and picks nothing.
 *
 * Screen readers get the points as a list, one node per point in order, each saying the same as the
 * label, so what the mode hides is missing from both.
 *
 * Size it with [modifier]; [content] fills it. Empty [readouts] draw [content] alone.
 */
@Composable
fun InspectableChart(
    readouts: List<ChartReadout?>,
    axis: ChartAxis,
    selection: ChartSelection,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (readouts.isEmpty()) {
        Box(modifier, content = content)
        return
    }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val guide = AppTheme.colors.line.color
    Box(
        modifier
            .semantics { isTraversalGroup = true }
            .pointerInput(readouts, axis) {
                val pickAt = { x: Float -> ChartPicking.nearest(x, readouts) { axis.x(it, readouts.size, size.width.toFloat(), this) } }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    selection.hold()
                    var x = down.position.x
                    var travel = Offset.Zero
                    val slop = viewConfiguration.touchSlop
                    // Null when the hold ran out with the finger still down: that is a scrub too.
                    val intent = withTimeoutOrNull(HOLD_TO_SCRUB_MILLIS) {
                        var decided: Intent? = null
                        while (decided == null) {
                            val change: PointerInputChange? = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            decided = when {
                                change == null || change.isConsumed -> Intent.Pass
                                change.changedToUp() -> Intent.Tap.also { change.consume() }
                                else -> {
                                    travel += change.positionChange()
                                    x = change.position.x
                                    when {
                                        abs(travel.x) > slop && abs(travel.x) > abs(travel.y) -> Intent.Scrub
                                        abs(travel.y) > slop -> Intent.Pass
                                        else -> null
                                    }
                                }
                            }
                        }
                        decided
                    } ?: Intent.Scrub
                    when (intent) {
                        Intent.Pass -> if (selection.index != ChartPicking.NONE) selection.releaseLater(scope)
                        Intent.Tap -> {
                            val point = pickAt(x)
                            if (point == selection.index) {
                                selection.clear()
                            } else {
                                selection.pick(point, haptics)
                                selection.releaseLater(scope)
                            }
                        }
                        Intent.Scrub -> {
                            selection.pick(pickAt(x), haptics)
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (change.changedToUp() || !change.pressed) break
                                selection.pick(pickAt(change.position.x), haptics)
                            }
                            selection.releaseLater(scope)
                        }
                    }
                }
            }
            .drawBehind {
                val index = selection.index
                if (index !in readouts.indices) return@drawBehind
                val x = axis.x(index, readouts.size, size.width, this)
                drawLine(guide, Offset(x, 0f), Offset(x, size.height), strokeWidth = GUIDE_WIDTH.toPx())
            },
    ) {
        content()
        PointNodes(readouts, axis, Modifier.matchParentSize())
        PickedLabel(readouts, axis, selection)
    }
}

private enum class Intent { Tap, Scrub, Pass }

/** One invisible node per point, as wide as the space nearest it, so touch exploration finds points by position too. */
@Composable
private fun PointNodes(readouts: List<ChartReadout?>, axis: ChartAxis, modifier: Modifier) {
    Layout(
        modifier = modifier,
        content = { readouts.forEach { readout -> if (readout != null) PointNode(readout) } },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val xs = readouts.indices.filter { readouts[it] != null }.map { axis.x(it, readouts.size, width.toFloat(), this) }
        val placed = measurables.mapIndexed { k, measurable ->
            val left = if (k == 0) 0f else (xs[k - 1] + xs[k]) / 2
            val right = if (k == xs.lastIndex) width.toFloat() else (xs[k] + xs[k + 1]) / 2
            left.roundToInt() to measurable.measure(Constraints.fixed(max(0, (right - left).roundToInt()), constraints.maxHeight))
        }
        layout(width, constraints.maxHeight) { placed.forEach { (left, placeable) -> placeable.place(left, 0) } }
    }
}

@Composable
private fun PointNode(readout: ChartReadout) {
    val description = readout.spoken()
    Box(
        Modifier
            .thenIfNotNull(readout.readings.firstNotNullOfOrNull { it.tag }) { testTag(it) }
            .semantics { contentDescription = description },
    )
}

/** `Tue, Oct 20, 1,980 kcal`; `Oct 20, Trend 72.4 kg, Weigh-in 72.9 kg`; `Monday, Hungry`. */
@Composable
private fun ChartReadout.spoken(): String {
    val parts = readings.map { reading ->
        listOfNotNull(reading.label?.let { stringResource(it) }, reading.value ?: reading.word?.let { stringResource(it) }).joinToString(" ")
    }
    return (listOf(title) + parts).joinToString(", ")
}

/**
 * The picked point's label, above the chart over whatever sits there, centred on the point and kept
 * inside the chart's width. It reports no size, so showing it never moves the card. Only this reads
 * the index in composition, because only its words change with it.
 */
@Composable
private fun PickedLabel(readouts: List<ChartReadout?>, axis: ChartAxis, selection: ChartSelection) {
    val index = selection.index
    val readout = readouts.getOrNull(index) ?: return
    Box(
        Modifier.layout { measurable, constraints ->
            val width = constraints.maxWidth
            val placeable = measurable.measure(Constraints(maxWidth = width))
            val center = axis.x(index, readouts.size, width.toFloat(), this)
            val left = (center - placeable.width / 2f).roundToInt().coerceIn(0, max(0, width - placeable.width))
            val top = -placeable.height - LABEL_GAP.roundToPx()
            layout(0, 0) { placeable.place(left, top) }
        },
    ) {
        ChartLabel(readout)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartLabel(readout: ChartReadout, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(AppTheme.colors.ink, AppTheme.shapes.input)
            .padding(horizontal = LABEL_PADDING_H, vertical = LABEL_PADDING_V),
    ) {
        Text(text = readout.title, typography = AppTheme.type.secondary, color = AppTheme.colors.surface, maxLines = 1)
        if (readout.readings.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(LABEL_READING_GAP)) {
                readout.readings.forEach { reading -> LabelReading(reading) }
            }
        }
    }
}

@Composable
private fun LabelReading(reading: ChartReading) {
    Row(horizontalArrangement = Arrangement.spacedBy(LABEL_WORD_GAP)) {
        reading.label?.let {
            Text(text = stringResource(it), typography = AppTheme.type.secondary, color = AppTheme.colors.surface, maxLines = 1)
        }
        reading.word?.let {
            Text(text = stringResource(it), typography = AppTheme.type.secondaryStrong.Bold, color = AppTheme.colors.surface, maxLines = 1)
        }
        reading.value?.let { value ->
            Text(
                text = value,
                typography = AppTheme.type.secondaryStrong.Bold.Tabular,
                color = AppTheme.colors.surface,
                maxLines = 1,
                modifier = Modifier.thenIfNotNull(reading.tag) { testTag(it) },
            )
        }
    }
}

/** How long a still finger rests before a drag in any direction scrubs instead of scrolling. */
private const val HOLD_TO_SCRUB_MILLIS = 250L
private val GUIDE_WIDTH = 1.5.dp
private val LABEL_GAP = 6.dp
private val LABEL_PADDING_H = 10.dp
private val LABEL_PADDING_V = 6.dp
private val LABEL_READING_GAP = 10.dp
private val LABEL_WORD_GAP = 4.dp
