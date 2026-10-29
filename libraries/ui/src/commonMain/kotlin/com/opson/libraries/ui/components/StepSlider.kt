package com.opson.libraries.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.system.tick
import com.opson.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

/**
 * A slider that only lands on whole steps of [range]: a track with a mark per step and a knob on
 * the picked one. Drag along it or tap a step; each new step ticks, so it moves in detents.
 * [value] null is unanswered: the marks alone, no knob, and the first touch answers it.
 *
 * Screen readers get an adjustable range over [range], [stateDescription] read for the step
 * (say the step's word, not only its number).
 */
@Composable
fun StepSlider(
    value: Int?,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    stateDescription: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    val change by rememberUpdatedState(onValueChange)
    // The step last sent, so a drag that stays on one step neither ticks nor sends it again
    // before the new value comes back down.
    val sent = remember { SentStep(value) }
    SideEffect { sent.step = value }
    val steps = range.last - range.first
    val pick: (Int) -> Unit = { step ->
        val clamped = step.coerceIn(range)
        if (clamped != sent.step) {
            sent.step = clamped
            haptics.tick()
            change(clamped)
        }
    }

    val trackColor = AppTheme.colors.track.color
    val fillColor = AppTheme.colors.accent.color
    val markColor = AppTheme.colors.muted.color
    val knobColor = AppTheme.colors.surface.color

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SliderHeight)
            .semantics {
                contentDescription?.let { this.contentDescription = it }
                stateDescription?.let { this.stateDescription = it }
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = (value ?: range.first).toFloat(),
                    range = range.first.toFloat()..range.last.toFloat(),
                    steps = (steps - 1).coerceAtLeast(0),
                )
                setProgress { target ->
                    pick(target.roundToInt())
                    true
                }
            }
            .pointerInput(range) {
                detectTapGestures { pick(stepAt(it.x, size.width.toFloat(), KnobRadius.toPx(), range)) }
            }
            .pointerInput(range) {
                detectHorizontalDragGestures(
                    onDragStart = { pick(stepAt(it.x, size.width.toFloat(), KnobRadius.toPx(), range)) },
                ) { change, _ ->
                    change.consume()
                    pick(stepAt(change.position.x, size.width.toFloat(), KnobRadius.toPx(), range))
                }
            }
            .drawBehind {
                val inset = KnobRadius.toPx()
                val width = size.width - inset * 2
                val y = size.height / 2
                val trackHeight = TrackHeight.toPx()
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(inset, y - trackHeight / 2),
                    size = Size(width, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2),
                )
                fun xOf(step: Int): Float = inset + if (steps == 0) 0f else width * (step - range.first) / steps
                if (value != null) {
                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset(inset, y - trackHeight / 2),
                        size = Size(xOf(value) - inset, trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2),
                    )
                }
                range.forEach { step ->
                    drawCircle(color = markColor.copy(alpha = MarkAlpha), radius = MarkRadius.toPx(), center = Offset(xOf(step), y))
                }
                if (value != null) {
                    val center = Offset(xOf(value), y)
                    drawCircle(color = fillColor, radius = KnobRadius.toPx(), center = center)
                    drawCircle(color = knobColor, radius = KnobDot.toPx(), center = center)
                }
            },
    )
}

/** The step nearest [x] on a track inset by [inset] at each end. */
internal fun stepAt(x: Float, width: Float, inset: Float, range: IntRange): Int {
    val steps = range.last - range.first
    val track = (width - inset * 2).coerceAtLeast(1f)
    val fraction = ((x - inset) / track).coerceIn(0f, 1f)
    return range.first + (fraction * steps).roundToInt()
}

private class SentStep(var step: Int?)

private val SliderHeight = 48.dp
private val TrackHeight = 6.dp
private val KnobRadius = 13.dp
private val KnobDot = 5.dp
private val MarkRadius = 2.dp
private const val MarkAlpha = 0.6f

@Preview(widthDp = 390)
@Composable
private fun StepSliderPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepSlider(value = 4, onValueChange = {}, range = 1..10)
            StepSlider(value = null, onValueChange = {}, range = 1..10)
        }
    }
}
