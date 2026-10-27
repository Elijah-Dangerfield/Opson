package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.ToneIcon
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.color.ToneColors
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * What a chart or list says before there is anything to put in it, so a screen never draws an
 * empty graph or a column of zeros: an [icon], one plain [message] saying what will show up
 * here and when, and, when there is one, the action that fills it ([actionLabel]). Centred, on
 * a card inside the page padding.
 */
@Composable
fun EmptyCard(
    message: String,
    modifier: Modifier = Modifier,
    icon: IconResource? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    EdgeCard(
        modifier = modifier,
        edgeToEdge = false,
        contentPadding = EdgeCardDefaults.InsetPadding,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            if (icon != null) ToneIcon(icon = icon, tone = ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint))
            Text(
                text = message,
                typography = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null) {
            PromptAction(label = actionLabel, onClick = onAction, emphasis = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun EmptyCardPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
            EmptyCard(
                message = "Once you start weighing in, your graph shows up here.",
                icon = AppIcons.MonitorWeight,
                actionLabel = "Add a weigh-in",
            )
            EmptyCard(message = "Your week shows up here once you've logged a day.", icon = AppIcons.Insights)
        }
    }
}
