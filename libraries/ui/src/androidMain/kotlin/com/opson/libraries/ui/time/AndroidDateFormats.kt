package com.opson.libraries.ui.time

import android.content.Context
import android.text.format.DateFormat
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.text.SimpleDateFormat
import java.util.GregorianCalendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Dates as the device's locale writes them, through the platform's best pattern for each skeleton.
 * The locale is read on every call, so a language change while the app runs is followed. The clock
 * follows the person's 12 or 24 hour setting, which on Android is separate from the locale.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = DateFormats::class)
class AndroidDateFormats(private val context: Context) : SkeletonDateFormats() {

    private val patterns = ConcurrentHashMap<String, String>()

    override fun format(dateTime: LocalDateTime, skeleton: String): String {
        val locale = Locale.getDefault()
        val clock = if ('j' in skeleton && DateFormat.is24HourFormat(context)) skeleton.replace('j', 'H') else skeleton
        val pattern = patterns.getOrPut("${locale.toLanguageTag()}|$clock") { DateFormat.getBestDateTimePattern(locale, clock) }
        val calendar = GregorianCalendar(dateTime.year, dateTime.month.number - 1, dateTime.day, dateTime.hour, dateTime.minute)
        // A new formatter each time: SimpleDateFormat is not safe to share across threads.
        return SimpleDateFormat(pattern, locale).apply { timeZone = calendar.timeZone }.format(calendar.time)
    }
}
