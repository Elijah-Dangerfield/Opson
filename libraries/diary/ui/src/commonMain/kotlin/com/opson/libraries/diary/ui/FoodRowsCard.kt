package com.opson.libraries.diary.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background

/**
 * The grouped card a list of [FoodPickRow]s or [DiaryItemRow]s sits in inside a
 * sheet: surface, radius 20, rows inset 14 from its sides.
 */
@Composable
fun FoodRowsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.groupedList)
            .background(AppTheme.colors.surface)
            .padding(horizontal = 14.dp, vertical = 2.dp),
        content = content,
    )
}

/** A muted line inside a [FoodRowsCard], for "nothing here" and the like. */
@Composable
fun FoodRowsNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        typography = AppTheme.type.secondary,
        color = AppTheme.colors.muted,
        modifier = modifier.padding(vertical = 14.dp),
    )
}

/** The small bold muted heading over a section: Favorites, Recent. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text, typography = AppTheme.type.sectionLabel, color = AppTheme.colors.muted, modifier = modifier)
}
