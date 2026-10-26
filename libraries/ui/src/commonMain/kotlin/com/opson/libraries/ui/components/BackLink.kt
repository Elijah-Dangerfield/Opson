package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.bounceClick
import com.opson.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The accent "‹ Trends" link above a pushed page's title. [label] is the page it
 * goes back to; the chevron is drawn here so every page spells it the same way.
 * Keeps a full touch target even though the text is small.
 */
@Composable
fun BackLink(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = AppTheme.spacing.touchTarget)
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.96f, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "$BACK_CHEVRON $label",
            typography = AppTheme.type.menuItem,
            color = AppTheme.colors.accent,
            maxLines = 1,
        )
    }
}

private const val BACK_CHEVRON = "‹"

@Preview(widthDp = 390)
@Composable
private fun BackLinkPreview() {
    PreviewLightDark {
        BackLink(label = "Trends", onClick = {})
    }
}
