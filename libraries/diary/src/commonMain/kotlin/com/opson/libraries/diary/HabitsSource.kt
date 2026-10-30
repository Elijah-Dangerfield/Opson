package com.opson.libraries.diary

import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * How this person usually logs, read once by a page that wants it ([LoggingHabitsEstimator] over
 * the last four weeks). Worked out on the phone from the log each time; nothing is stored or sent.
 */
fun interface HabitsSource {
    suspend fun current(): LoggingHabits

    companion object {
        /** Nothing learned: for tests, previews, and a default where a constructor must not grow a required argument. */
        val None: HabitsSource = HabitsSource { LoggingHabits() }
    }
}

@ContributesBinding(AppScope::class)
@Inject
class LoggedHabitsSource(
    private val diary: DiaryRepository,
    private val settings: SettingsRepository,
    private val calendar: LocalCalendar,
) : HabitsSource {
    override suspend fun current(): LoggingHabits {
        val today = calendar.today()
        val days = diary.observeDays(today.minus(LoggingHabitsEstimator.WINDOW_DAYS, DateTimeUnit.DAY), today).first()
        return LoggingHabitsEstimator.of(days, today, calendar.timeZone(), settings.settings.value.mealTimes)
    }
}
