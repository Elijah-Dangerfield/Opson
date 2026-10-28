package com.opson.libraries.reminders.impl

import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.AppConfigRepository
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.DayReflection
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.opson.AppEvent
import com.opson.libraries.opson.AppEventBus
import com.opson.libraries.reminders.LocalNotifications
import com.opson.libraries.reminders.NotificationPermission
import com.opson.libraries.reminders.Reminder
import com.opson.libraries.reminders.ReminderKey
import com.opson.libraries.reminders.ReminderNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/* Hand-rolled fakes for the coordinator test (docs/practices/testing.md). */

/** Holds what the coordinator scheduled, as the platform would, and records every other call. */
class FakeLocalNotifications(var permission: NotificationPermission = NotificationPermission.Granted) : LocalNotifications {
    val scheduled = mutableListOf<ReminderNotification>()
    val dismissed = mutableListOf<ReminderKey>()
    var dismissedAll = 0
        private set
    var replaces = 0
        private set

    val ids: List<String> get() = scheduled.map { it.reminder.id }

    override suspend fun status(): NotificationPermission = permission
    override suspend fun request(): NotificationPermission = permission
    override fun openSettings() = Unit

    override suspend fun pending(): List<Reminder> = scheduled.map { it.reminder }

    override suspend fun replaceAll(notifications: List<ReminderNotification>) {
        replaces++
        scheduled.clear()
        scheduled += notifications
    }

    override suspend fun add(notification: ReminderNotification) {
        scheduled.removeAll { it.reminder.id == notification.reminder.id }
        scheduled += notification
    }

    override suspend fun dismiss(keys: List<ReminderKey>) {
        dismissed += keys
    }

    override suspend fun dismissAll() {
        dismissedAll++
    }
}

class FakeSettingsRepository(initial: Settings, initiallyKnown: Boolean = true) : SettingsRepository {
    val state = MutableStateFlow(initial)
    val known = MutableStateFlow(initiallyKnown)
    override val settings: StateFlow<Settings> = state
    override val isKnown: StateFlow<Boolean> = known

    override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings =
        transform(state.value).also { state.value = it }
}

/** Days by date. Only what the coordinator reads and writes is real. */
class FakeDiary : DiaryRepository {
    val days = MutableStateFlow<Map<LocalDate, Day>>(emptyMap())

    override fun observeDay(date: LocalDate): Flow<Day> = days.map { it[date] ?: Day.empty(date) }

    override suspend fun setAteNoDetails(day: LocalDate, slot: MealSlot, ate: Boolean): DayWrite<Unit> {
        val current = days.value[day] ?: Day.empty(day)
        if (current.closedByUser) return DayWrite.DayClosed(day)
        val marks = if (ate) current.ateNoDetails + slot else current.ateNoDetails - slot
        days.value = days.value + (day to current.copy(ateNoDetails = marks))
        return DayWrite.Written(Unit)
    }

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Day>> = unused()
    override fun observeLoggedDates(year: Int, month: Int): Flow<Set<LocalDate>> = unused()
    override suspend fun addItems(day: LocalDate, slot: MealSlot, items: List<LogDraft>, method: LogMethod): DayWrite<List<LogItem>> = unused()
    override suspend fun updateItem(item: LogItem): DayWrite<Unit> = unused()
    override suspend fun removeItem(id: String): DayWrite<Unit> = unused()
    override suspend fun moveItem(id: String, slot: MealSlot): DayWrite<Unit> = unused()
    override suspend fun copyDay(from: LocalDate, to: LocalDate): DayWrite<List<LogItem>> = unused()
    override suspend fun copySlot(fromDay: LocalDate, toDay: LocalDate, slot: MealSlot): DayWrite<List<LogItem>> = unused()
    override suspend fun clearDay(day: LocalDate): DayWrite<Unit> = unused()
    override suspend fun addWater(day: LocalDate, ml: Int): DayWrite<Unit> = unused()
    override suspend fun removeWater(day: LocalDate, ml: Int): DayWrite<Unit> = unused()
    override suspend fun setWater(day: LocalDate, ml: Int): DayWrite<Unit> = unused()
    override suspend fun closeDay(day: LocalDate, reflection: DayReflection) = unused()
    override suspend fun reopenDay(day: LocalDate) = unused()
    override suspend fun setDayNote(day: LocalDate, note: String?): DayWrite<Unit> = unused()
    override suspend fun setUntracked(day: LocalDate, untracked: Boolean) = unused()
    override suspend fun setFeeling(day: LocalDate, slot: MealSlot, feeling: MealFeeling?): DayWrite<Unit> = unused()
    override suspend fun setMealGroups(day: LocalDate, on: Boolean?) = unused()
    override fun recentFoods(limit: Int): Flow<List<LogItem>> = unused()
    override fun usualAt(slot: MealSlot, limit: Int, days: Int): Flow<List<LogItem>> = unused()
    override fun frequentFoods(limit: Int, days: Int): Flow<List<LogItem>> = unused()

    private fun unused(): Nothing = error("Reminders never calls this")
}

class FixedCalendar(private val date: LocalDate) : LocalCalendar {
    override fun timeZone(): TimeZone = TimeZone.UTC
    override fun today(): LocalDate = date
}

class FakeEventBus : AppEventBus {
    val events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
    override fun dispatch(event: AppEvent) {
        events.tryEmit(event)
    }
    override fun eventStream(): Flow<AppEvent> = events
    override fun liveEventStream(): Flow<AppEvent> = events
}

/** `release.reminders` from a flow the test flips. */
fun configFlow(flag: MutableStateFlow<Boolean>): AppConfigFlow {
    fun map(on: Boolean) = object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("reminders" to on))
    }
    val repository = object : AppConfigRepository {
        override fun config(): AppConfigMap = map(flag.value)
        override fun configStream(): Flow<AppConfigMap> = flag.map(::map)
    }
    return AppConfigFlow(repository)
}
