package com.opson.features.trends.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.opson.libraries.medication.DoseFormat
import com.opson.libraries.medication.DoseMarks
import com.opson.libraries.medication.amount
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.medication_mark_started
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Dose ticks under the weight line (`release.medication`): a short mark where a dose changed,
 * labelled "5 mg", and "Started". No projection and no "lost since starting". A label that would
 * overlap the one before it is left off; its tick stays.
 *
 * The x positions follow `TrendLine`'s points, inset by its dot radius, so a tick lines up with
 * the weigh-ins either side of it.
 */
@Composable
internal fun WeightDoseTicks(ticks: List<DoseTick>, modifier: Modifier = Modifier) {
    if (ticks.isEmpty()) return
    val tickColor = AppTheme.colors.muted.color
    val labels = ticks.map { it.mark.label() }
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(TICK_HEIGHT)) {
            Canvas(Modifier.fillMaxWidth().height(TICK_HEIGHT)) {
                val inset = LINE_INSET.toPx()
                ticks.forEach { tick ->
                    val x = inset + (size.width - inset * 2) * tick.x
                    drawLine(tickColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
                }
            }
        }
        Layout(
            content = { labels.forEach { Text(text = it, typography = AppTheme.type.secondary, color = AppTheme.colors.muted, maxLines = 1) } },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val inset = LINE_INSET.toPx()
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                var freeFrom = Int.MIN_VALUE
                placeables.forEachIndexed { index, placeable ->
                    val center = inset + (width - inset * 2) * ticks[index].x
                    val left = (center - placeable.width / 2f).roundToInt().coerceIn(0, max(0, width - placeable.width))
                    if (left >= freeFrom) {
                        placeable.place(left, 0)
                        freeFrom = left + placeable.width + LABEL_GAP.roundToPx()
                    }
                }
            }
        }
    }
}

@Composable
private fun DoseMarks.Mark.label(): String = when (this) {
    is DoseMarks.Mark.Started -> stringResource(Res.string.medication_mark_started)
    is DoseMarks.Mark.Changed -> stringResource(unit.amount, DoseFormat.number(dose))
}

private val TICK_HEIGHT = 6.dp

/** `TrendLine`'s inset: the larger of half its line width and its dot radius. */
private val LINE_INSET = 2.5.dp
private val LABEL_GAP = 6.dp
