package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.checkbox.SquareCheck
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Something only a Builder is offered, in the program's own color (`AppTheme.colors.builder`) with
 * its mark: Verify this food on a food's detail, Verify a food on the Foods tab. The whole card is
 * the button. Not hidden in a menu on purpose: it is the thing a Builder came to do.
 */
@Composable
fun BuilderCard(
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconResource = AppIcons.Builder,
) {
    val tone = AppTheme.colors.builder
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(AppTheme.shapes.card)
            .background(tone.container)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .bounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(tone.content),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon = icon, size = 22.dp, color = AppTheme.colors.onBuilder)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, typography = AppTheme.type.bodyBold, color = tone.content)
            Text(text = body, typography = AppTheme.type.secondary, color = AppTheme.colors.ink)
        }
        Icon(icon = AppIcons.ChevronRight, size = 24.dp, color = tone.content)
    }
}

/**
 * The Verified badge: the seal and [text] in the Builders color. [description], when given,
 * replaces both for a screen reader, which is where a row puts what Verified means.
 */
@Composable
fun VerifiedMark(
    text: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    iconSize: Dp = 14.dp,
) {
    val color = AppTheme.colors.builder.content
    Row(
        modifier = if (description != null) modifier.clearAndSetSemantics { contentDescription = description } else modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon = AppIcons.Verified, size = iconSize, color = color)
        Text(text = text, typography = AppTheme.type.captionStrong, color = color, maxLines = 1)
    }
}

/**
 * A row with a check box, for a question where several answers can be picked inside a card
 * (Report a problem's "What's wrong?"). The whole row is the toggle. [RadioRow]'s twin.
 */
@Composable
fun CheckRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
                if (subtitle != null) Text(text = subtitle, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
            }
            SquareCheck(checked = checked, size = 24.dp)
        }
        if (showDivider) HorizontalDivider()
    }
}

@Preview(widthDp = 390)
@Composable
private fun BuildersPartsPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BuilderCard(title = "Verify this food", body = "Nobody has checked it against the pack yet.", onClick = {})
            VerifiedMark(text = "Verified")
            EdgeCard(edgeToEdge = false) {
                CheckRow(title = "Wrong numbers", checked = true, onCheckedChange = {})
                CheckRow(title = "Missing information", subtitle = "Fiber, sugar or a serving size", checked = false, onCheckedChange = {}, showDivider = false)
            }
        }
    }
}
