package com.opson.features.settings.impl.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.opson.libraries.ui.components.button.FlatButton
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
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            IconTile(
                icon = AppIcons.Restaurant,
                contentColor = AppTheme.colors.onAccent,
                containerColor = AppTheme.colors.accent,
                size = IconTileSize.Small,
            )
            Text(text = stringResource(Res.string.reminders_note_app), typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.muted)
            Text(text = stringResource(Res.string.reminders_note_now), typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
        }
        Text(text = stringResource(text.title), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
        Text(text = stringResource(text.body), typography = AppTheme.type.secondary, color = AppTheme.colors.ink)
        if (text.actions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                text.actions.forEach { (_, label) ->
                    FlatButton(
                        text = stringResource(label),
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        centered = true,
                        shape = AppTheme.shapes.pill,
                    )
                }
            }
        }
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

/** v3: dinner, plain, on the lock screen. */
@Preview
@Composable
private fun ReminderNotificationPreview() {
    SettingsPreview(dark = true) {
        Column(modifier = Modifier.background(AppTheme.colors.bg).padding(AppTheme.spacing.page)) {
            ReminderNotificationCard(ReminderCopy.text(ReminderKey.Meal(LocalDate(2026, 10, 25), MealSlot.Dinner), Tone.Plain))
        }
    }
}

@Preview
@Composable
private fun ReminderNotificationsWarmPreview() {
    SettingsPreview { ReminderNotifications(Tone.Warm) }
}
