package com.opson.libraries.ui.components.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.system.thenIf
import com.opson.system.background
import com.opson.libraries.ui.components.bleedHorizontal
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One row of a [RadioMatrix]: a label, which column is picked, and a spoken description per
 * column ("Calories: Show"), since the dots alone say nothing to a screen reader.
 */
data class RadioMatrixRow(
    val label: String,
    val selectedColumn: Int,
    val cellDescriptions: List<String>,
    /** A short line under the label ("Changed from Hide"), in the accent. */
    val note: String? = null,
    /** Tints the row, for a row that differs from its starting point. */
    val highlighted: Boolean = false,
    /**
     * Columns this row doesn't offer, drawn as a dash with no touch target: Progress photos are
     * Show or Hide, never Soft or Later.
     */
    val disabledColumns: Set<Int> = emptySet(),
)

/**
 * The per-metric grid on Modes and numbers: a label column, then one radio column per choice.
 * Each cell is a 48 dp square touch target. With large text the label column wraps rather than
 * squeezing the cells. A [RadioMatrixRow.highlighted] row's tint reaches [highlightBleed] past
 * the matrix on each side, so inside a card it can run to the card's edges.
 */
@Composable
fun RadioMatrix(
    header: String,
    columns: List<String>,
    rows: List<RadioMatrixRow>,
    onSelect: (row: Int, column: Int) -> Unit,
    modifier: Modifier = Modifier,
    highlightBleed: Dp = 0.dp,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = header,
                typography = AppTheme.type.secondarySmallStrong,
                color = AppTheme.colors.muted,
                modifier = Modifier.weight(1f),
            )
            columns.forEach { column ->
                Text(
                    text = column,
                    typography = AppTheme.type.secondarySmallStrong,
                    color = AppTheme.colors.muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.width(CellWidth),
                )
            }
        }
        rows.forEachIndexed { rowIndex, row ->
            HorizontalDivider(color = AppTheme.colors.line)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .thenIf(row.highlighted) {
                        bleedHorizontal(highlightBleed)
                            .background(AppTheme.colors.accentTint)
                            .padding(horizontal = highlightBleed)
                    }
                    .padding(horizontal = 4.dp)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text(text = row.label, typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
                    if (row.note != null) {
                        Text(text = row.note, typography = AppTheme.type.captionStrong, color = AppTheme.colors.accent)
                    }
                }
                columns.indices.forEach { columnIndex ->
                    if (columnIndex in row.disabledColumns) {
                        Box(Modifier.width(CellWidth).height(AppTheme.spacing.touchTarget), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(width = DashWidth, height = DashHeight).background(AppTheme.colors.muted))
                        }
                        return@forEach
                    }
                    val selected = row.selectedColumn == columnIndex
                    Box(
                        modifier = Modifier
                            .width(CellWidth)
                            .height(AppTheme.spacing.touchTarget)
                            .semantics { row.cellDescriptions.getOrNull(columnIndex)?.let { contentDescription = it } }
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(rowIndex, columnIndex) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        RadioDot(selected = selected, size = MatrixDotSize)
                    }
                }
            }
        }
    }
}

private val CellWidth = 48.dp
private val MatrixDotSize = 20.dp
private val DashWidth = 10.dp
private val DashHeight = 2.dp

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun RadioMatrixPreview() {
    val columns = listOf("Show", "Soft", "Later", "Hide")
    PreviewLightDark {
        EdgeCard(edgeToEdge = false, verticalArrangement = Arrangement.Top) {
            RadioMatrix(
                header = "Each number",
                columns = columns,
                rows = listOf("Calories" to 0, "Left in range" to 3, "Vitamins, minerals" to 2).map { (label, picked) ->
                    RadioMatrixRow(label, picked, columns.map { "$label: $it" })
                } + RadioMatrixRow("Carbs", 0, columns.map { "Carbs: $it" }, note = "Changed from Hide", highlighted = true) +
                    RadioMatrixRow("Progress photos", 0, columns.map { "Progress photos: $it" }, disabledColumns = setOf(1, 2)),
                highlightBleed = 20.dp,
                onSelect = { _, _ -> },
            )
        }
    }
}
