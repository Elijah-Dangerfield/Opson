package com.opson.libraries.widgets.impl

import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.AppConfigRepository
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.opson.AppEvent
import com.opson.libraries.opson.AppEventBus
import com.opson.libraries.widgets.WidgetPublisher
import com.opson.libraries.widgets.WidgetSnapshot
import com.opson.libraries.widgets.WidgetSnapshotSource
import com.opson.libraries.widgets.WidgetStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource

/* Hand-rolled fakes for the widget tests (docs/practices/testing.md). */

/** Records what reached the platform. */
class FakePublisher : WidgetPublisher {
    val published = mutableListOf<WidgetSnapshot>()
    var clears = 0
        private set

    override suspend fun publish(snapshot: WidgetSnapshot) {
        published += snapshot
    }

    override suspend fun clear() {
        clears++
    }
}

/** Snapshots on demand, and whether anyone is collecting. */
class FakeSnapshotSource : WidgetSnapshotSource {
    val snapshots = MutableSharedFlow<WidgetSnapshot>(replay = 1)
    var collections = 0
        private set

    override fun observe(): Flow<WidgetSnapshot> = snapshots.onStart { collections++ }
}

class FakeSettingsRepository(initial: Settings, initiallyKnown: Boolean = true) : SettingsRepository {
    val state = MutableStateFlow(initial)
    val known = MutableStateFlow(initiallyKnown)
    override val settings: StateFlow<Settings> = state
    override val isKnown: StateFlow<Boolean> = known

    override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings =
        transform(state.value).also { state.value = it }
}

class FakePolicySource(initial: VisibilityPolicy) : VisibilityPolicySource {
    val state = MutableStateFlow(initial)
    override val policy: StateFlow<VisibilityPolicy> = state
}

/** Days by date. Only what the widgets read is real; [reads] counts it. */
class FakeDiary : DiaryRepository {
    val days = MutableStateFlow<Map<LocalDate, Day>>(emptyMap())
    var reads = 0
        private set

    override fun observeDay(date: LocalDate): Flow<Day> = days.onStart { reads++ }.map { it[date] ?: Day.empty(date) }

    override suspend fun setAteNoDetails(day: LocalDate, slot: MealSlot, ate: Boolean) = unused()
    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Day>> = unused()
    override fun observeLoggedDates(year: Int, month: Int): Flow<Set<LocalDate>> = unused()
    override suspend fun addItems(day: LocalDate, slot: MealSlot, items: List<LogDraft>, method: LogMethod): List<LogItem> = unused()
    override suspend fun updateItem(item: LogItem) = unused()
    override suspend fun removeItem(id: String) = unused()
    override suspend fun moveItem(id: String, slot: MealSlot) = unused()
    override suspend fun copyDay(from: LocalDate, to: LocalDate): List<LogItem> = unused()
    override suspend fun copySlot(fromDay: LocalDate, toDay: LocalDate, slot: MealSlot): List<LogItem> = unused()
    override suspend fun clearDay(day: LocalDate) = unused()
    override suspend fun addWater(day: LocalDate, ml: Int) = unused()
    override suspend fun removeWater(day: LocalDate, ml: Int) = unused()
    override suspend fun setWater(day: LocalDate, ml: Int) = unused()
    override suspend fun closeDay(day: LocalDate) = unused()
    override suspend fun reopenDay(day: LocalDate) = unused()
    override suspend fun setDayNote(day: LocalDate, note: String?) = unused()
    override suspend fun setUntracked(day: LocalDate, untracked: Boolean) = unused()
    override suspend fun setDayFeeling(day: LocalDate, feeling: DayFeeling?) = unused()
    override suspend fun setFeeling(day: LocalDate, slot: MealSlot, feeling: MealFeeling?) = unused()
    override suspend fun setMealGroups(day: LocalDate, on: Boolean?) = unused()
    override fun recentFoods(limit: Int): Flow<List<LogItem>> = unused()
    override fun usualAt(slot: MealSlot, limit: Int, days: Int): Flow<List<LogItem>> = unused()
    override fun frequentFoods(limit: Int, days: Int): Flow<List<LogItem>> = unused()

    private fun unused(): Nothing = error("Widgets never call this")
}

class FixedCalendar(var date: LocalDate) : LocalCalendar {
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

/** The resource's key, so a test never depends on English copy. */
object KeyStrings : WidgetStrings {
    override suspend fun get(resource: StringResource, vararg args: Any): String =
        if (args.isEmpty()) resource.key else "${resource.key}(${args.joinToString()})"
}

/** `release.widgets` from a flow the test flips. */
fun configFlow(flag: MutableStateFlow<Boolean>): AppConfigFlow {
    fun map(on: Boolean) = object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("widgets" to on))
    }
    val repository = object : AppConfigRepository {
        override fun config(): AppConfigMap = map(flag.value)
        override fun configStream(): Flow<AppConfigMap> = flag.map(::map)
    }
    return AppConfigFlow(repository)
}
