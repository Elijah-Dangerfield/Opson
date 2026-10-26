package com.opson.libraries.widgets.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.widgets.NextMeal
import com.opson.libraries.widgets.WidgetEvents
import com.opson.libraries.widgets.WidgetLinks
import com.opson.libraries.widgets.WidgetLogOpen
import com.opson.libraries.widgets.WidgetOpens
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock

/**
 * Answers a widget's +: reads today and asks for the Logger on the next meal ([NextMeal]),
 * decided now rather than when the snapshot was written. App.kt routes [requests] through the
 * router, which holds them until the NavHost is bound, so a tap that cold-starts the app lands.
 *
 * Not gated on `release.widgets`: only a widget sends this link, and if one is on the home screen
 * its + should work.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = WidgetOpens::class)
@Inject
class WidgetOpener(
    private val diary: DiaryRepository,
    private val settingsRepository: SettingsRepository,
    private val calendar: LocalCalendar,
    private val clock: Clock,
    private val appScope: AppCoroutineScope,
) : WidgetOpens {

    private val opens = Channel<WidgetLogOpen>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val requests: Flow<WidgetLogOpen> = opens.receiveAsFlow()

    override fun opened(url: String): Boolean {
        if (!WidgetLinks.isLog(url)) return false
        appScope.launch {
            val today = calendar.today()
            val day = Catching { diary.observeDay(today).first() }
                .logOnFailure { "Reading today for a widget tap failed" }
                .getOrNull() ?: Day.empty(today)
            val now = clock.now().toLocalDateTime(calendar.timeZone()).time
            val slot = NextMeal.of(day, settingsRepository.settings.value, now)
            WidgetEvents.logTapped(nextMeal = slot != null)
            opens.trySend(WidgetLogOpen(slot = slot, day = today))
        }
        return true
    }
}
