@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.ui.time

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number
import me.tatarka.inject.annotations.Inject
import platform.Foundation.NSCalendar
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.autoupdatingCurrentLocale
import platform.Foundation.localeIdentifier
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.concurrent.AtomicReference
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Dates as the device's locale writes them, through `NSDateFormatter` templates, which also follow
 * the person's 12 or 24 hour setting. One formatter per locale and skeleton, as making one is slow;
 * formatting with a shared one is safe across threads.
 */
@HiddenFromObjC
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = DateFormats::class)
class IosDateFormats : SkeletonDateFormats() {

    private val formatters = AtomicReference<Map<String, NSDateFormatter>>(emptyMap())

    override fun format(dateTime: LocalDateTime, skeleton: String): String {
        val locale = NSLocale.autoupdatingCurrentLocale
        val key = "${locale.localeIdentifier}|$skeleton"
        val formatter = formatters.value[key] ?: NSDateFormatter().also { made ->
            made.locale = locale
            made.setLocalizedDateFormatFromTemplate(skeleton)
            // Two threads racing here each make one; the map keeps the last, and both are right.
            formatters.value = formatters.value + (key to made)
        }
        val components = NSDateComponents().apply {
            year = dateTime.year.toLong()
            month = dateTime.month.number.toLong()
            day = dateTime.day.toLong()
            hour = dateTime.hour.toLong()
            minute = dateTime.minute.toLong()
        }
        val date = NSCalendar.currentCalendar.dateFromComponents(components) ?: return ""
        return formatter.stringFromDate(date)
    }
}
