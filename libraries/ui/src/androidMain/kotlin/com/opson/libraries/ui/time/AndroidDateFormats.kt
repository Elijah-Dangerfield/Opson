package com.opson.libraries.ui.time

import android.content.Context
import android.icu.text.DateTimePatternGenerator
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import android.text.format.DateFormat
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.GregorianCalendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Dates as the device's locale writes them, through ICU's best pattern for each skeleton.
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
        val pattern = patterns.getOrPut("${locale.toLanguageTag()}|$clock") {
            DateTimePatternGenerator.getInstance(ULocale.forLocale(locale)).getBestPattern(clock)
        }
        val calendar = GregorianCalendar(dateTime.year, dateTime.month.number - 1, dateTime.day, dateTime.hour, dateTime.minute)
        // ICU's formatter, which knows the stand-alone names (c, L) java.text's does not everywhere.
        // A new one each time: it is not safe to share across threads.
        return SimpleDateFormat(pattern, locale).format(calendar.time)
    }
}
