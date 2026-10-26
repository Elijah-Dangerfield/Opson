package com.opson.libraries.ui.components.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
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
)

/**
 * The per-metric grid on Modes and numbers: a label column, then one radio column per choice.
 * Each cell is a 44dp-tall touch target 46dp wide. With large text the label column wraps
 * rather than squeezing the cells.
 */
@Composable
fun RadioMatrix(
    header: String,
    columns: List<String>,
    rows: List<RadioMatrixRow>,
    onSelect: (row: Int, column: Int) -> Unit,
    modifier: Modifier = Modifier,
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
                    typography = AppTheme.type.caption,
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp).selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.label,
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.ink,
                    modifier = Modifier.weight(1f),
                )
                columns.indices.forEach { columnIndex ->
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

private val CellWidth = 46.dp
private val MatrixDotSize = 18.dp

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
                },
                onSelect = { _, _ -> },
            )
        }
    }
}
