package com.opson.libraries.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.thenIf
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One day in a [MonthGrid]. [marked] is the "something logged" dot, never an
 * amount. A disabled day (past the furthest day the user may open) draws in
 * the `line` colour and does not respond to taps.
 */
data class MonthGridCell(
    val date: LocalDate,
    val contentDescription: String,
    val selected: Boolean = false,
    val isToday: Boolean = false,
    val enabled: Boolean = true,
    val marked: Boolean = false,
)

/**
 * The calendar sheet's month: a row of weekday initials, then the days in
 * weeks of seven on a surface card. A `null` cell is a blank before the first
 * day; the caller lays the month out (which weekday starts the week is its
 * call), the grid only draws it.
 *
 * Selected: an accent circle. Today, when not selected: an accent ring.
 */
@Composable
fun MonthGrid(
    weekdayLabels: List<String>,
    cells: List<MonthGridCell?>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(weekdayLabels.size == DAYS_IN_WEEK) { "A week has seven days, got ${weekdayLabels.size} labels" }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.groupedList)
            .background(AppTheme.colors.surface)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Text(
                    text = label,
                    typography = AppTheme.type.captionStrong,
                    color = AppTheme.colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(top = 4.dp, bottom = 6.dp),
                )
            }
        }
        cells.chunked(DAYS_IN_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until DAYS_IN_WEEK).forEach { index ->
                    val cell = week.getOrNull(index)
                    Box(Modifier.weight(1f).height(CELL_HEIGHT), contentAlignment = Alignment.Center) {
                        if (cell != null) DayCell(cell, onDayClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(cell: MonthGridCell, onDayClick: (LocalDate) -> Unit) {
    val colors = AppTheme.colors
    val textColor = when {
        cell.selected -> colors.onAccent
        cell.enabled -> colors.ink
        else -> colors.line
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(CELL_HEIGHT)
            .clickable(enabled = cell.enabled, role = Role.Button) { onDayClick(cell.date) }
            .semantics {
                contentDescription = cell.contentDescription
                selected = cell.selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .thenIf(cell.selected) { background(colors.accent) }
                .thenIf(cell.isToday && !cell.selected) { border(1.5.dp, colors.accent.color, CircleShape) },
            contentAlignment = Alignment.Center,
        ) {
            // The cell's description says "October 25"; the bare "25" would only repeat it.
            Text(
                text = cell.date.day.toString(),
                typography = AppTheme.type.numeric,
                color = textColor,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .thenIf(cell.marked) { background(colors.nutrients.fat.content) },
        )
    }
}

/** The key under a [MonthGrid]: the "something logged" dot and its [text]. */
@Composable
fun MonthGridLegend(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(AppTheme.colors.nutrients.fat.content))
        Text(text = text, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
    }
}

private const val DAYS_IN_WEEK = 7
private val CELL_HEIGHT = 46.dp

@Preview(widthDp = 390)
@Composable
private fun MonthGridPreview() {
    // October 2026, Sunday first: the 1st is a Thursday, so four blanks lead.
    val today = LocalDate(2026, 10, 25)
    val first = LocalDate(2026, 10, 1)
    val days = (0 until 31).map { offset ->
        val date = first.plus(DatePeriod(days = offset))
        MonthGridCell(
            date = date,
            contentDescription = DateCopy.longMonthDay(date),
            selected = date == LocalDate(2026, 10, 22),
            isToday = date == today,
            enabled = date <= today.plus(DatePeriod(days = 6)),
            marked = date < today && date.day % 6 != 0,
        )
    }
    PreviewLightDark {
        MonthGrid(
            weekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S"),
            cells = List(4) { null } + days,
            onDayClick = {},
        )
    }
}
