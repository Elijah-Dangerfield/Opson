package com.opson.libraries.ui.components.radio

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A full-width row with a title, optional subtitle and a radio dot on the right, as in the
 * Modes preset list. The whole row is the touch target. [showDivider] draws the line under it.
 * [badge] is a small tinted pill after the title ("1 change").
 */
@Composable
fun RadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showDivider: Boolean = true,
    enabled: Boolean = true,
    badge: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
                ) {
                    Text(text = title, typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
                    if (badge != null) {
                        Text(
                            text = badge,
                            typography = AppTheme.type.captionStrong,
                            color = AppTheme.colors.accent,
                            maxLines = 1,
                            modifier = Modifier
                                .background(AppTheme.colors.accentTint, AppTheme.shapes.pill)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                if (subtitle != null) {
                    Text(text = subtitle, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
                }
            }
            RadioDot(selected = selected)
        }
        if (showDivider) HorizontalDivider()
    }
}

/**
 * The handoff's radio mark: a ring, filled with the accent when selected, with a surface-colored
 * inset so the fill reads as a dot. 22dp in rows, 20dp in the per-metric matrix.
 */
@Composable
fun RadioDot(
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val colors = AppTheme.colors
    val ring = if (selected) colors.accent else colors.line
    val inset = if (size >= 22.dp) 4.dp else 3.dp
    Box(
        modifier = modifier
            .size(size)
            .border(2.dp, ring.color, CircleShape)
            .padding(2.dp + inset),
    ) {
        if (selected) {
            Box(Modifier.matchParentSize().background(colors.accent, CircleShape))
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun RadioRowPreview() {
    PreviewLightDark {
        EdgeCard(edgeToEdge = false) {
            RadioRow(title = "Full numbers", subtitle = "Everything, as numbers", selected = true, onClick = {}, badge = "1 change")
            RadioRow(title = "Blind", subtitle = "Log food, see no numbers", selected = false, onClick = {}, showDivider = false)
        }
    }
}
