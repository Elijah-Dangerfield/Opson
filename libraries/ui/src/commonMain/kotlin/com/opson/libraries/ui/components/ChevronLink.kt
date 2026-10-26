package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A bold accent link with a chevron at the end of a card title ("Patterns ›" on Trends' Recap).
 * At least 48 tall, so the touch target holds even though it reads as a word. [HeroLink] is the
 * same thing on the hero colour.
 */
@Composable
fun ChevronLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: ColorResource = AppTheme.colors.accent,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = MinTouchTarget)
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.96f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = text, typography = AppTheme.type.bodyBold.ExtraBold, color = color)
        Icon(icon = AppIcons.ChevronRight, size = 22.dp, color = color)
    }
}

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun ChevronLinkPreview() {
    PreviewLightDark { ChevronLink(text = "Patterns", onClick = {}) }
}
