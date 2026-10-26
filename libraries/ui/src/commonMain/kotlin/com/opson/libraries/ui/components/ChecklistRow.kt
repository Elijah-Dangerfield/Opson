package com.opson.libraries.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.thenIf
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One line of a checklist card (Today's Rhythm layout, design v3 2.7): a round mark, a title over
 * a muted line, and [actions] under the text when there are any. The mark is an accent ring
 * when open and solid accent with a check once [done]. A line under every row but the last.
 */
@Composable
fun ChecklistRow(
    title: String,
    subtitle: String,
    done: Boolean,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.section),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.page),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .thenIf(!done) { border(2.dp, AppTheme.colors.accent.color, CircleShape) }
                    .thenIf(done) { background(AppTheme.colors.accent) },
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(icon = AppIcons.Check, size = 22.dp, color = AppTheme.colors.onAccent)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                Column {
                    Text(text = title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
                    Text(text = subtitle, typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.muted)
                }
                if (actions != null) Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row), content = actions)
            }
        }
        if (showDivider) HorizontalDivider(color = AppTheme.colors.line)
    }
}

/** A large action under a [ChecklistRow]: "Already ate" on the soft fill, or "Log it" on the accent when [primary]. 56 tall. */
@Composable
fun ChecklistAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .clip(AppTheme.shapes.inputLarge)
            .background(if (primary) AppTheme.colors.accent else AppTheme.colors.soft)
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.97f, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            typography = AppTheme.type.bodyBold.ExtraBold,
            color = if (primary) AppTheme.colors.onAccent else AppTheme.colors.ink,
            maxLines = 1,
        )
    }
}

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun ChecklistRowPreview() {
    PreviewLightDark {
        EdgeCard(edgeToEdge = false, verticalArrangement = Arrangement.Top) {
            ChecklistRow(title = "Breakfast", subtitle = "8:10 am · planned around 8:00", done = true)
            ChecklistRow(title = "Dinner", subtitle = "Planned around 7:00", done = false, showDivider = false) {
                ChecklistAction(label = "Already ate", onClick = {}, modifier = Modifier.weight(1f))
                ChecklistAction(label = "Log it", onClick = {}, primary = true, modifier = Modifier.weight(1f))
            }
        }
    }
}
