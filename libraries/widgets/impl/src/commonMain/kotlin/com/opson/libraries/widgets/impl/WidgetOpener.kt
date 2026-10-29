package com.opson.libraries.widgets.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.isSomeones
import com.opson.libraries.opson.AppCache
import com.opson.libraries.widgets.NextMeal
import com.opson.libraries.widgets.WidgetEvents
import com.opson.libraries.widgets.WidgetLink
import com.opson.libraries.widgets.WidgetLinks
import com.opson.libraries.widgets.WidgetLogOpen
import com.opson.libraries.widgets.WidgetOpen
import com.opson.libraries.widgets.WidgetOpens
import com.opson.libraries.widgets.WidgetTodayOpen
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock

/**
 * Answers a widget's taps ([WidgetLinks]). The + reads today and asks for the Logger on the next
 * meal ([NextMeal]), decided now rather than when the snapshot was written; a meals widget row asks
 * for its own meal. The body of a widget asks for Today, moved to today first so it shows what the
 * widget showed. The water widget's add puts one glass (the person's glass size) on today, then asks
 * for Today, where the water card shows it and can take it off again. App.kt routes [requests]
 * through the router, which holds them until the NavHost is bound, so a tap that cold-starts the
 * app lands.
 *
 * Not gated on `release.widgets`: only a widget sends these links, and if one is on the home screen
 * it should work. Gated on the app being someone's ([isSomeones]), though: a widget is drawn on the
 * "Open Opson" card too, and a Logger opened (or a glass written) over sign-in would put what is
 * logged in an outbox nobody owns, which the next account to sign in then pushes as its own. Then
 * the tap only opens the app. [AuthRepository] is reached through a provider, only at a tap, as in
 * `WidgetRefresher`.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = WidgetOpens::class)
@Inject
class WidgetOpener(
    private val diary: DiaryRepository,
    private val settingsRepository: SettingsRepository,
    private val calendar: LocalCalendar,
    private val clock: Clock,
    private val appCache: AppCache,
    private val authRepositoryProvider: () -> AuthRepository,
    private val selectedDay: SelectedDay,
    private val appScope: AppCoroutineScope,
) : WidgetOpens {

    private val opens = Channel<WidgetOpen>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val requests: Flow<WidgetOpen> = opens.receiveAsFlow()

    override fun opened(url: String): Boolean {
        val link = WidgetLinks.parse(url) ?: return false
        appScope.launch {
            // The first answer, so a tap that cold-starts the app waits for auth rather than guessing.
            val onboarded = Catching { appCache.get().hasUserOnboarded }.getOrNull() == true
            if (!authRepositoryProvider().current().isSomeones(onboarded)) return@launch
            val today = calendar.today()
            when (link) {
                is WidgetLink.Log -> opens.trySend(log(today, link.slot))
                WidgetLink.Today -> {
                    WidgetEvents.opened(target = "today")
                    openToday(today)
                }
                WidgetLink.AddWater -> {
                    WidgetEvents.opened(target = "water")
                    addGlass(today)
                    openToday(today)
                }
            }
        }
        return true
    }

    private suspend fun log(today: LocalDate, asked: MealSlot?): WidgetLogOpen {
        val settings = settingsRepository.settings.value
        if (asked != null) {
            val day = readToday(today)
            // A row's meal, unless meal groups are off today, where the Logger has no meals to open on.
            val slot = asked.takeIf { day.mealGroupsOn(settings) }
            WidgetEvents.logTapped(nextMeal = slot != null)
            return WidgetLogOpen(slot = slot, day = today)
        }
        val now = clock.now().toLocalDateTime(calendar.timeZone()).time
        val slot = NextMeal.of(readToday(today), settings, now)
        WidgetEvents.logTapped(nextMeal = slot != null)
        return WidgetLogOpen(slot = slot, day = today)
    }

    private fun openToday(today: LocalDate) {
        selectedDay.select(today)
        opens.trySend(WidgetTodayOpen)
    }

    private suspend fun addGlass(today: LocalDate) {
        val glassMl = settingsRepository.settings.value.glassMl
        val written = Catching { diary.addWater(today, glassMl) }
            .logOnFailure { "Adding water from a widget failed" }
            .getOrNull() is DayWrite.Written<*>
        WidgetEvents.waterAdded(added = written)
    }

    private suspend fun readToday(today: LocalDate): Day = Catching { diary.observeDay(today).first() }
        .logOnFailure { "Reading today for a widget tap failed" }
        .getOrNull() ?: Day.empty(today)
}
