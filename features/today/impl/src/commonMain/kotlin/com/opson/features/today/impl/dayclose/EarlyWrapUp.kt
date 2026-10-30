package com.opson.features.today.impl.dayclose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.libraries.diary.RecapPeriod
import com.opson.libraries.ui.components.ChevronLink
import com.opson.libraries.ui.components.PromptAction
import com.opson.libraries.ui.components.PromptCard
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.color.ToneColors
import kotlinx.datetime.LocalTime
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_dayclose_early_body
import opson.libraries.resources.generated.resources.today_dayclose_early_body_usual
import opson.libraries.resources.generated.resources.today_dayclose_early_learned
import opson.libraries.resources.generated.resources.today_dayclose_early_not_yet
import opson.libraries.resources.generated.resources.today_dayclose_early_title
import opson.libraries.resources.generated.resources.today_dayclose_week_recap
import org.jetbrains.compose.resources.stringResource

/**
 * Wrap up the day opened early (owner feedback, 2026-10-30: "Are you sure? 7 AM is early. You
 * usually end your day around 8 PM."): a gentle note at the top with "Not yet", never in the way.
 * [usual] is when this person usually wraps up ([com.opson.libraries.diary.LoggingHabits.wrapUp]),
 * null until it is learned.
 */
data class EarlyWrapUp(val now: LocalTime, val usual: LocalTime?) {
    companion object {
        /** More than this before the usual wrap-up is early. */
        const val HOURS_BEFORE_USUAL = 3

        val NOON = LocalTime(12, 0)

        private const val MINUTES_PER_HOUR = 60
        private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

        /** A usual wrap-up before this hour is the small hours of the next day, not the morning. */
        private const val SMALL_HOURS = 5

        /**
         * The note for wrapping up today at [now], or null when it isn't early: more than
         * [HOURS_BEFORE_USUAL] hours before the usual wrap-up, or before [NOON]. Someone whose
         * usual wrap-up is in the morning (a night shift) is held to their own time, not to noon.
         */
        fun of(now: LocalTime, usual: LocalTime?): EarlyWrapUp? {
            val minute = now.minutes()
            val usualMinute = usual?.let { if (it.hour < SMALL_HOURS) it.minutes() + MINUTES_PER_DAY else it.minutes() }
            val beforeUsual = usualMinute != null && minute < usualMinute - HOURS_BEFORE_USUAL * MINUTES_PER_HOUR
            val morning = minute < NOON.minutes() && (usualMinute == null || usualMinute >= NOON.minutes())
            return if (beforeUsual || morning) EarlyWrapUp(now, usual) else null
        }

        private fun LocalTime.minutes(): Int = hour * MINUTES_PER_HOUR + minute
    }
}

/** The note: what time it is, when they usually wrap up, and "Not yet", which goes back to Today. */
@Composable
internal fun EarlyWrapUpCard(early: EarlyWrapUp, onNotYet: () -> Unit, modifier: Modifier = Modifier) {
    PromptCard(
        icon = AppIcons.Schedule,
        tone = ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint),
        title = stringResource(Res.string.today_dayclose_early_title, DateCopy.time(early.now.hour, early.now.minute)),
        body = early.usual?.let { stringResource(Res.string.today_dayclose_early_body_usual, DateCopy.time(it.hour, it.minute)) }
            ?: stringResource(Res.string.today_dayclose_early_body),
        modifier = modifier,
        footer = early.usual?.let {
            {
                Text(
                    text = stringResource(Res.string.today_dayclose_early_learned),
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                )
            }
        },
    ) {
        PromptAction(label = stringResource(Res.string.today_dayclose_early_not_yet), onClick = onNotYet, modifier = Modifier.weight(1f), emphasis = true)
    }
}

/** Today wrapped up on the last day of the week, with recaps released: the way to the week's recap. */
@Composable
internal fun WeekRecapLink(period: RecapPeriod, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        ChevronLink(text = stringResource(Res.string.today_dayclose_week_recap, DateCopy.range(period.start, period.end)), onClick = onOpen)
    }
}
