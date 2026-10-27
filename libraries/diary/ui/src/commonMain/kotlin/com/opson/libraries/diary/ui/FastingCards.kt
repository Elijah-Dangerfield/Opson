package com.opson.libraries.diary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.FastingStatus
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.HeroBody
import com.opson.libraries.ui.components.HeroCard
import com.opson.libraries.ui.components.HeroWashButton
import com.opson.libraries.ui.components.PillAction
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.RowIcon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import kotlinx.datetime.LocalTime
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.fasting_end
import opson.libraries.resources.generated.resources.fasting_hours_minutes
import opson.libraries.resources.generated.resources.fasting_minutes
import opson.libraries.resources.generated.resources.fasting_status_ended
import opson.libraries.resources.generated.resources.fasting_status_ended_big
import opson.libraries.resources.generated.resources.fasting_status_food_ends
import opson.libraries.resources.generated.resources.fasting_status_next
import opson.libraries.resources.generated.resources.fasting_status_open
import opson.libraries.resources.generated.resources.fasting_status_open_big
import opson.libraries.resources.generated.resources.fasting_status_resting
import opson.libraries.resources.generated.resources.fasting_status_running
import opson.libraries.resources.generated.resources.fasting_status_running_since
import opson.libraries.resources.generated.resources.fasting_today_running
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/*
 * Fasting's status, drawn twice: the hero card at the top of the Fasting page (design v3,
 * fasting.png) and the row card on Today (the prototype's `fs.todayCard`). Clock times and a
 * running duration only. Nothing here counts or compares fasts: no streak, no record, no goal
 * shown as a target to beat.
 */

/** The three lines of a status: the small label, the big line, and the line under it. */
private data class StatusLines(val label: String, val big: String, val body: String)

@Composable
private fun FastingStatus.lines(): StatusLines = when (this) {
    is FastingStatus.Running -> StatusLines(
        label = stringResource(Res.string.fasting_status_running),
        big = elapsed.fastingClock(),
        body = stringResource(Res.string.fasting_status_running_since, since.clock()),
    )
    is FastingStatus.WindowOpen -> StatusLines(
        label = stringResource(Res.string.fasting_status_open),
        big = stringResource(Res.string.fasting_status_open_big, until.clock()),
        body = stringResource(Res.string.fasting_status_food_ends),
    )
    is FastingStatus.Ended -> StatusLines(
        label = stringResource(Res.string.fasting_status_ended),
        big = stringResource(Res.string.fasting_status_ended_big, at.clock()),
        body = stringResource(Res.string.fasting_status_next, nextStart.clock()),
    )
    is FastingStatus.Resting -> StatusLines(
        label = stringResource(Res.string.fasting_status_resting),
        big = stringResource(Res.string.fasting_status_open_big, nextStart.clock()),
        body = stringResource(Res.string.fasting_status_food_ends),
    )
}

/** `13h 40m`, or `40m` under an hour. Whole minutes: the timer ticks once a minute. */
@Composable
fun Duration.fastingClock(): String {
    val hours = inWholeHours.toInt()
    val minutes = (inWholeMinutes % 60).toInt()
    return if (hours > 0) {
        stringResource(Res.string.fasting_hours_minutes, hours, minutes)
    } else {
        stringResource(Res.string.fasting_minutes, minutes)
    }
}

private fun LocalTime.clock(): String = DateCopy.time(hour, minute)

/**
 * The Fasting page's status card on the hero colour: "EATING WINDOW OPEN / Until 7:00 PM /
 * Logging food ends a fast." While a fast runs it shows the live time and End fast, which is
 * always allowed. There is no Start button: a fast starts when the window closes.
 */
@Composable
fun FastingStatusCard(
    status: FastingStatus,
    onEndFast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = status.lines()
    HeroCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = lines.label.uppercase(),
            typography = AppTheme.type.overline,
            color = AppTheme.colors.onHeroMuted,
        )
        Text(text = lines.big, typography = AppTheme.type.display.Tabular, color = AppTheme.colors.onHero)
        HeroBody(lines.body)
        if (status is FastingStatus.Running) {
            HeroWashButton(
                text = stringResource(Res.string.fasting_end),
                onClick = onEndFast,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Today's fasting card: its icon, what is happening, and End fast while a fast runs. Tapping the
 * card opens the Fasting page.
 */
@Composable
fun FastingTodayCard(
    status: FastingStatus,
    onOpen: () -> Unit,
    onEndFast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = status.lines()
    val sub = when (status) {
        is FastingStatus.Running -> stringResource(Res.string.fasting_today_running, lines.big, status.since.clock())
        else -> lines.big
    }
    TodayCard(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        onClick = onOpen,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            RowIcon(
                icon = if (status is FastingStatus.Running) AppIcons.HourglassTop else AppIcons.Restaurant,
                color = AppTheme.colors.nutrients.fiber.content,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = lines.label, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
                Text(text = sub, typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.muted)
            }
            if (status is FastingStatus.Running) {
                PillAction(label = stringResource(Res.string.fasting_end), onClick = onEndFast)
            }
        }
    }
}

/** Fixed statuses for previews here and on the screens that draw these cards. */
object FastingStatusFixtures {
    val running = FastingStatus.Running(since = LocalTime(19, 0), elapsed = 13.hours + 40.minutes)
    val windowOpen = FastingStatus.WindowOpen(until = LocalTime(19, 0))
    val ended = FastingStatus.Ended(at = LocalTime(20, 10), nextStart = LocalTime(19, 0))
    val resting = FastingStatus.Resting(nextStart = LocalTime(19, 0))
}

@Preview
@Composable
private fun FastingStatusCardPreview() {
    PreviewContent {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FastingStatusCard(FastingStatusFixtures.windowOpen, onEndFast = {})
            FastingStatusCard(FastingStatusFixtures.running, onEndFast = {})
            FastingStatusCard(FastingStatusFixtures.ended, onEndFast = {})
        }
    }
}

@Preview
@Composable
private fun FastingTodayCardPreview() {
    PreviewContent {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FastingTodayCard(FastingStatusFixtures.running, onOpen = {}, onEndFast = {})
            FastingTodayCard(FastingStatusFixtures.windowOpen, onOpen = {}, onEndFast = {})
            FastingTodayCard(FastingStatusFixtures.ended, onOpen = {}, onEndFast = {})
        }
    }
}

@Preview
@Composable
private fun FastingTodayCardDarkPreview() {
    PreviewContent(darkTheme = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FastingTodayCard(FastingStatusFixtures.running, onOpen = {}, onEndFast = {})
            FastingStatusCard(FastingStatusFixtures.running, onEndFast = {})
        }
    }
}
