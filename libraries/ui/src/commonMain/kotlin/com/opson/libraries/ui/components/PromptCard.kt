package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.ToneIcon
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.color.ToneColors
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A card that offers one gentle thing: a [ToneIcon], a bold [title], a [body], then [actions]
 * side by side (each a [PromptAction] with `Modifier.weight(1f)`). Today's untracked day is this
 * card. [footer] goes under the actions: a quiet "I'd rather not see these" ([PromptCardFooterLink]).
 */
@Composable
fun PromptCard(
    icon: IconResource,
    tone: ToneColors,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    EdgeCard(
        modifier = modifier,
        edgeToEdge = false,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            ToneIcon(icon = icon, tone = tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
                Text(text = body, typography = AppTheme.type.body, color = AppTheme.colors.muted)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row), content = actions)
        footer?.invoke()
    }
}

/** A [PromptCard]'s quiet last line, a text button across the card: "I'd rather not see these". */
@Composable
fun PromptCardFooterLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FlatButton(text = label, onClick = onClick, tone = FlatButtonTone.Text, centered = true, modifier = modifier.fillMaxWidth())
}

/**
 * One of a [PromptCard]'s buttons: the accent tint with accent text when [emphasis], else the
 * soft grey with ink, so neither choice reads as the wrong one. 56 tall, as the design's are.
 */
@Composable
fun PromptAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .clip(AppTheme.shapes.inputLarge)
            .background(if (emphasis) AppTheme.colors.accentTint else AppTheme.colors.soft)
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.97f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            typography = AppTheme.type.bodyBold.ExtraBold,
            color = if (emphasis) AppTheme.colors.accent else AppTheme.colors.ink,
            textAlign = TextAlign.Center,
        )
    }
}

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun PromptCardPreview() {
    PreviewLightDark {
        PromptCard(
            icon = AppIcons.BeachAccess,
            tone = ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint),
            title = "Not tracking this day",
            body = "Trends skips this day.",
        ) {
            PromptAction(label = "Track it again", onClick = {}, modifier = Modifier.weight(1f))
        }
    }
}
