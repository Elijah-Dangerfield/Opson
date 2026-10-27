package com.opson.libraries.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.StandardBorderWidth
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Something that looks like a filled field and opens an editor when tapped,
 * for a value that is picked rather than typed: a food on an estimate line, or
 * its amount (3.7). One line, ellipsized. [active] outlines it while its
 * editor is open.
 */
@Composable
fun TapField(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    muted: Boolean = false,
    /** What a tap does, for screen readers ("Change the amount of Olive oil"). */
    description: String? = null,
) {
    val shape = AppTheme.shapes.input
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = AppTheme.spacing.touchTarget)
            .clip(shape)
            .background(AppTheme.colors.soft)
            .border(StandardBorderWidth, if (active) AppTheme.colors.accent.color else AppTheme.colors.line.color, shape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .padding(horizontal = AppTheme.spacing.row),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            typography = AppTheme.type.body,
            color = if (muted) AppTheme.colors.muted else AppTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(widthDp = 390)
@Composable
private fun TapFieldPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TapField("Chicken breast, grilled", onClick = {}, modifier = Modifier.weight(1f))
                TapField("about 120 g", onClick = {}, modifier = Modifier.weight(1f), active = true)
            }
            TapField("Not in the food list yet", onClick = {}, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), muted = true)
        }
    }
}
