package com.opson.features.settings.impl.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SettingsPreview
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Tone
import com.opson.libraries.reminders.ReminderCopy
import com.opson.libraries.reminders.ReminderKey
import com.opson.libraries.reminders.ReminderPlanner
import com.opson.libraries.reminders.ReminderText
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconTile
import com.opson.libraries.ui.components.icon.IconTileSize
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import kotlinx.datetime.LocalDate
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.reminders_note_app
import opson.libraries.resources.generated.resources.reminders_note_now
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A reminder as the system shows it (v3 `reminder-notification.png`), drawn in the app so the
 * notification's words can be previewed and scanned by `BlindModeLeakTest`. The real thing is
 * posted by the platform from the same [ReminderCopy] resources.
 */
@Composable
fun ReminderNotificationCard(text: ReminderText, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.bg, AppTheme.shapes.card)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            IconTile(
                icon = AppIcons.Restaurant,
                contentColor = AppTheme.colors.onAccent,
                containerColor = AppTheme.colors.accent,
                size = IconTileSize.Small,
            )
            Text(text = stringResource(Res.string.reminders_note_app), typography = AppTheme.type.secondaryStrong.Bold, color = AppTheme.colors.muted)
            Text(text = stringResource(Res.string.reminders_note_now), typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
        }
        Text(
            text = stringResource(text.title),
            typography = AppTheme.type.cardTitle,
            color = AppTheme.colors.ink,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(text = stringResource(text.body), typography = AppTheme.type.body, color = AppTheme.colors.inkSoft)
        if (text.actions.isNotEmpty()) {
            Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                text.actions.forEach { (_, label) -> ActionChip(stringResource(label), Modifier.weight(1f)) }
            }
        }
    }
}

/** A notification action as design sync 2 draws it: a tinted pill with accent text. Not tappable here. */
@Composable
private fun ActionChip(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .background(AppTheme.colors.accentTint, AppTheme.shapes.pill)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, typography = AppTheme.type.bodyBold.ExtraBold, color = AppTheme.colors.accent, maxLines = 1)
    }
}

/** Every reminder in [tone], each as the platform would show it. What the leak test scans. */
@Composable
fun ReminderNotifications(tone: Tone, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.ink)
            .padding(AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        ReminderNotificationSamples.keys.forEach { key -> ReminderNotificationCard(ReminderCopy.text(key, tone)) }
    }
}

/** One of each: the four meals, and both water nudges. */
object ReminderNotificationSamples {
    private val date = LocalDate(2026, 10, 25)

    val keys: List<ReminderKey> =
        MealSlot.meals.map { ReminderKey.Meal(date, it) } + ReminderPlanner.WATER_TIMES.indices.map { ReminderKey.Water(date, it) }
}

/** Design sync 2: dinner, plain, on the lock screen under the clock. */
@Suppress("VerifyStrings")
@Preview
@Composable
private fun ReminderNotificationPreview() {
    SettingsPreview {
        Column(
            modifier = Modifier.fillMaxSize().background(AppTheme.colors.hero).padding(AppTheme.spacing.page),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The lock screen's own clock and date, drawn for the preview only.
            Text(text = "7:15", typography = AppTheme.type.figureDisplay, color = AppTheme.colors.onHero, modifier = Modifier.padding(top = 48.dp))
            Text(
                text = "Sunday, October 25",
                typography = AppTheme.type.lead,
                color = AppTheme.colors.onHeroMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 40.dp),
            )
            ReminderNotificationCard(ReminderCopy.text(ReminderKey.Meal(LocalDate(2026, 10, 25), MealSlot.Dinner), Tone.Plain))
        }
    }
}

@Preview
@Composable
private fun ReminderNotificationsWarmPreview() {
    SettingsPreview { ReminderNotifications(Tone.Warm) }
}
