package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A quiet bar along the bottom of a screen with one undo-style action: "Added 2 foods to
 * dinner · Undo" (v3 3.9). Drawn by the screen from its own state, so it lasts exactly as long
 * as the state says; the app's snackbar only knows short and long. Inverse colours (ink
 * behind, the page colour in front) so it reads over any card. No sound, no vibration: the
 * screen reader hears it politely and that is all.
 */
@Composable
fun UndoBar(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.card)
            .background(AppTheme.colors.ink)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            typography = AppTheme.type.body,
            color = AppTheme.colors.bg,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
                .clip(AppTheme.shapes.pill)
                .semantics { role = Role.Button }
                .bounceClick(scaleDown = 0.95f, onClick = onAction)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = actionLabel, typography = AppTheme.type.bodyBold.ExtraBold, color = AppTheme.colors.accentTint)
        }
    }
}

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun UndoBarPreview() {
    PreviewLightDark {
        UndoBar(message = "Added 2 foods to dinner", actionLabel = "Undo", onAction = {})
    }
}
