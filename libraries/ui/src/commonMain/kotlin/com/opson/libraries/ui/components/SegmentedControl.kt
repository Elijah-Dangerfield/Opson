package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.tick
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The handoff's segmented control: a soft track with equal-width segments, the selected one
 * raised onto the surface color. Used for Day / Week average, Weighed / Estimated / Just the
 * name, Off / Subtle / Full and the like. Moving to another segment ticks.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 42.dp,
) {
    val colors = AppTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.input)
            .background(colors.soft)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            // The track's 3 dp inset is inside each segment's tap area, so a 42 dp thumb still
            // takes a 48 dp tap; the 1 dp on each inner side draws the 2 dp gap.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = {
                            if (!isSelected) haptics.tick()
                            onSelect(option)
                        },
                    )
                    .padding(
                        start = if (index == 0) 3.dp else 1.dp,
                        end = if (index == options.lastIndex) 3.dp else 1.dp,
                        top = 3.dp,
                        bottom = 3.dp,
                    )
                    .defaultMinSize(minHeight = minHeight)
                    .clip(AppTheme.shapes.segmentThumb)
                    .background(if (isSelected) colors.surface else colors.soft)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    typography = AppTheme.type.bodyStrong,
                    color = if (isSelected) colors.ink else colors.muted,
                    textAlign = TextAlign.Center,
                    // Two lines, so large text wraps "Just the name" rather than cutting it off.
                    maxLines = 2,
                )
            }
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun SegmentedControlPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentedControl(options = listOf("Day", "Week average"), selected = "Day", onSelect = {}, label = { it })
            SegmentedControl(
                options = listOf("Weighed", "Estimated", "Just the name"),
                selected = "Estimated",
                onSelect = {},
                label = { it },
                minHeight = 40.dp,
            )
        }
    }
}
