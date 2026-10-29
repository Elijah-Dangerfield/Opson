package com.opson.libraries.prompts.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.health.HealthSource
import com.opson.libraries.prompts.PromptContent
import com.opson.libraries.prompts.impl.candidates.CheckInPrompt
import com.opson.libraries.prompts.impl.candidates.ConnectHealthPrompt
import com.opson.libraries.prompts.impl.candidates.RemindersPrompt
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.PromptAction
import com.opson.libraries.ui.components.PromptCard
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.ToneIcon
import com.opson.libraries.ui.components.icon.ToneIconSize
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.color.ToneColors
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/*
 * How a prompt looks, whichever way it is drawn: the icon in the accent, a bold title, a line or
 * two, then the one thing to do and the way out. The dialog and the sheet share a body; the card
 * is Today's PromptCard, as the untracked-day card is.
 */

/** Inside the dialog's surface. Previewed on its own, since a hosted dialog needs the app's host to draw. */
@Composable
internal fun PromptDialogPanel(content: PromptContent, onAccept: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    PromptBody(
        content = content,
        onAccept = onAccept,
        onDismiss = onDismiss,
        modifier = modifier.padding(24.dp),
    )
}

/** A sheet's content, under its grab handle. */
@Composable
internal fun PromptSheetContent(content: PromptContent, onAccept: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    PromptBody(
        content = content,
        onAccept = onAccept,
        onDismiss = onDismiss,
        modifier = modifier.padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
    )
}

/** In Today's list. Not now first, as on the late-day card, so neither reads as the wrong answer. */
@Composable
internal fun PromptTodayCard(content: PromptContent, onAccept: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    PromptCard(
        icon = content.icon,
        tone = accentTone(),
        title = stringResource(content.title),
        body = stringResource(content.body),
        modifier = modifier,
    ) {
        PromptAction(label = stringResource(content.dismiss), onClick = onDismiss, modifier = Modifier.weight(1f))
        PromptAction(label = stringResource(content.accept), onClick = onAccept, modifier = Modifier.weight(1f), emphasis = true)
    }
}

@Composable
private fun PromptBody(content: PromptContent, onAccept: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        ToneIcon(icon = content.icon, tone = accentTone(), size = ToneIconSize.Large)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(content.title), typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink)
            Text(text = stringResource(content.body), typography = AppTheme.type.lead, color = AppTheme.colors.muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlatButton(
                text = stringResource(content.accept),
                onClick = onAccept,
                tone = FlatButtonTone.Accent,
                centered = true,
                minHeight = FlatButtonDefaults.TallMinHeight,
                typography = AppTheme.type.button,
                modifier = Modifier.fillMaxWidth(),
            )
            FlatButton(
                text = stringResource(content.dismiss),
                onClick = onDismiss,
                tone = FlatButtonTone.Text,
                centered = true,
                minHeight = FlatButtonDefaults.TallMinHeight,
                typography = AppTheme.type.button,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun accentTone(): ToneColors = ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint)

// One preview per prompt and way of drawing it, from the copy the shipped prompts use.

@Preview(widthDp = 320)
@Composable
private fun PromptRemindersDialogPreview() {
    PreviewContent(backgroundColor = AppTheme.colors.surface) {
        PromptDialogPanel(PromptPreviews.reminders, onAccept = {}, onDismiss = {})
    }
}

@Preview(widthDp = 320)
@Composable
private fun PromptRemindersDialogDarkPreview() {
    PreviewContent(darkTheme = true, backgroundColor = AppTheme.colors.surface) {
        PromptDialogPanel(PromptPreviews.reminders, onAccept = {}, onDismiss = {})
    }
}

@Preview(widthDp = 390)
@Composable
private fun PromptHealthSheetPreview() {
    PreviewContent(backgroundColor = AppTheme.colors.bg) {
        PromptSheetContent(PromptPreviews.healthApple, onAccept = {}, onDismiss = {})
    }
}

@Preview(widthDp = 390)
@Composable
private fun PromptHealthConnectSheetDarkPreview() {
    PreviewContent(darkTheme = true, backgroundColor = AppTheme.colors.bg) {
        PromptSheetContent(PromptPreviews.healthConnect, onAccept = {}, onDismiss = {})
    }
}

@Preview(widthDp = 390)
@Composable
private fun PromptCheckInCardPreview() {
    PreviewLightDark {
        PromptTodayCard(PromptPreviews.checkIn, onAccept = {}, onDismiss = {})
    }
}

@Preview(widthDp = 390)
@Composable
private fun PromptCheckInCardLargeTextPreview() {
    PreviewLightDark(largeText = true) {
        PromptTodayCard(PromptPreviews.checkIn, onAccept = {}, onDismiss = {})
    }
}

/** The shipped prompts' own copy, so a preview shows what the person reads. */
internal object PromptPreviews {
    val reminders = RemindersPrompt.CONTENT
    val healthApple = ConnectHealthPrompt.contentFor(HealthSource.AppleHealth)
    val healthConnect = ConnectHealthPrompt.contentFor(HealthSource.HealthConnect)
    val checkIn = CheckInPrompt.CONTENT
}
