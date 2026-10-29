package com.opson.features.today.impl

import com.opson.libraries.core.fixed
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.diary.CheckInEnabled
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayCheckIns
import com.opson.libraries.diary.DayReflection
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.DiaryItemEditor
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.Fast
import com.opson.libraries.diary.FastingRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.NumbersChanger
import com.opson.libraries.diary.NumbersOutcome
import com.opson.libraries.diary.RecentLogEvents
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.TodaySummaryEnabled
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RevealOn
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.CacheJsonSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

fun policy(
    preset: PresetId,
    at: Instant = TodayFixtures.noon,
    overrides: Map<Metric, Visibility> = emptyMap(),
    pause: Boolean = false,
    revealOn: RevealOn = RevealOn.DayClose,
): VisibilityPolicy = policy(NumbersSettings(preset = preset), at, overrides, pause, revealOn)

/** [numbers] with [overrides] on top of its own: `policy(NumbersSettings.later())` is every number on Later. */
fun policy(
    numbers: NumbersSettings,
    at: Instant = TodayFixtures.noon,
    overrides: Map<Metric, Visibility> = emptyMap(),
    pause: Boolean = false,
    revealOn: RevealOn = numbers.revealOn,
): VisibilityPolicy = VisibilityPolicy(
    numbers.copy(overrides = numbers.overrides + overrides, pauseBeforeMore = pause, revealOn = revealOn),
    Clock.fixed(at),
    TimeZone.UTC,
)

/** A display's words, or null when it renders nothing. */
fun Display.text(): String? = when (this) {
    Display.Hidden -> null
    is Display.Soft -> text
    is Display.Shown -> text
}

/**
 * An in-memory diary that remembers every write. Only what Today and its
 * sheets read and write does anything; the rest is inert. A day in [days] with
 * [Day.closedAt] set refuses writes to it, as the real diary does, and records
 * nothing for them.
 */
class FakeDiaryRepository(days: List<Day> = emptyList()) : DiaryRepository, DiaryItemEditor, DayCheckIns {
    val days = MutableStateFlow(days.associateBy { it.date })
    val loggedDates = MutableStateFlow<Map<Pair<Int, Int>, Set<LocalDate>>>(emptyMap())
    val frequent = MutableStateFlow<List<LogItem>>(emptyList())

    val water = mutableListOf<Pair<LocalDate, Int>>()
    val removedWater = mutableListOf<Pair<LocalDate, Int>>()
    val setWater = mutableListOf<Pair<LocalDate, Int>>()
    val copiedDays = mutableListOf<Pair<LocalDate, LocalDate>>()
    val copiedSlots = mutableListOf<Triple<LocalDate, LocalDate, MealSlot>>()
    val cleared = mutableListOf<LocalDate>()
    val ateNoDetails = mutableListOf<Pair<LocalDate, MealSlot>>()
    val feelings = mutableListOf<Triple<LocalDate, MealSlot, MealFeeling?>>()
    val notes = mutableListOf<Pair<LocalDate, String?>>()
    val untracked = mutableListOf<Pair<LocalDate, Boolean>>()
    val closed = mutableListOf<Pair<LocalDate, DayReflection>>()
    val reopened = mutableListOf<LocalDate>()
    val removed = mutableListOf<String>()
    val restored = mutableListOf<List<String>>()
    val moved = mutableListOf<Pair<List<String>, MealSlot>>()
    val copiedItems = mutableListOf<Triple<List<String>, LocalDate, MealSlot>>()
    val updated = mutableListOf<LogItem>()
    val checkIns = mutableListOf<Pair<LocalDate, CheckIn?>>()

    /** Every write, in order, for tests that assert nothing was written. */
    val writes: Int
        get() = water.size + removedWater.size + copiedDays.size + copiedSlots.size + cleared.size + ateNoDetails.size +
            feelings.size + notes.size + closed.size + reopened.size + removed.size + untracked.size +
            restored.size + moved.size + copiedItems.size + updated.size + checkIns.size

    override fun observeDay(date: LocalDate): Flow<Day> = days.map { it[date] ?: Day.empty(date) }

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Day>> =
        days.map { all -> all.values.filter { it.date in from..to }.sortedBy { it.date } }

    override fun observeLoggedDates(year: Int, month: Int): Flow<Set<LocalDate>> = loggedDates.map { it[year to month].orEmpty() }

    override suspend fun addItems(day: LocalDate, slot: MealSlot, items: List<LogDraft>, method: LogMethod): DayWrite<List<LogItem>> =
        open(day) { emptyList() }

    override suspend fun updateItem(item: LogItem): DayWrite<Unit> = openFor(listOf(item.id)) { updated += item }

    override suspend fun removeItem(id: String): DayWrite<Unit> = openFor(listOf(id)) { removed += id }

    override suspend fun moveItem(id: String, slot: MealSlot): DayWrite<Unit> = openFor(listOf(id)) { }

    override suspend fun copyDay(from: LocalDate, to: LocalDate): DayWrite<List<LogItem>> = open(to) {
        copiedDays += from to to
        emptyList()
    }

    override suspend fun copySlot(fromDay: LocalDate, toDay: LocalDate, slot: MealSlot): DayWrite<List<LogItem>> = open(toDay) {
        copiedSlots += Triple(fromDay, toDay, slot)
        emptyList()
    }

    override suspend fun clearDay(day: LocalDate): DayWrite<Unit> = open(day) { cleared += day }

    override suspend fun addWater(day: LocalDate, ml: Int): DayWrite<Unit> = open(day) { water += day to ml }

    override suspend fun removeWater(day: LocalDate, ml: Int): DayWrite<Unit> = open(day) { removedWater += day to ml }

    override suspend fun setWater(day: LocalDate, ml: Int): DayWrite<Unit> = open(day) { setWater += day to ml }

    /** Records the close, keeps its note and feeling on the day and marks it closed, so what reads it sees the change. */
    override suspend fun closeDay(day: LocalDate, reflection: DayReflection) {
        closed += day to reflection
        days.value += day to (days.value[day] ?: Day.empty(day)).let {
            it.copy(closedAt = it.closedAt ?: CLOSED_AT, note = reflection.note.cleaned(), feeling = reflection.feeling)
        }
    }

    /** Records the check-in and keeps it on the day, closed or not, as the diary does. */
    override suspend fun setCheckIn(day: LocalDate, checkIn: CheckIn?) {
        checkIns += day to checkIn
        days.value += day to (days.value[day] ?: Day.empty(day)).copy(checkIn = checkIn?.takeUnless { it.isEmpty })
    }

    override suspend fun reopenDay(day: LocalDate) {
        reopened += day
        days.value[day]?.let { days.value += day to it.copy(closedAt = null) }
    }

    /** Records the note and keeps it on the day, closed or not, as the diary does. */
    override suspend fun setDayNote(day: LocalDate, note: String?): DayWrite<Unit> {
        notes += day to note
        days.value += day to (days.value[day] ?: Day.empty(day)).copy(note = note.cleaned())
        return DayWrite.Written(Unit)
    }

    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    override suspend fun setUntracked(day: LocalDate, untracked: Boolean) {
        this.untracked += day to untracked
    }

    override suspend fun setAteNoDetails(day: LocalDate, slot: MealSlot, ate: Boolean): DayWrite<Unit> =
        open(day) { if (ate) ateNoDetails += day to slot }

    override suspend fun setFeeling(day: LocalDate, slot: MealSlot, feeling: MealFeeling?): DayWrite<Unit> =
        open(day) { feelings += Triple(day, slot, feeling) }

    override suspend fun setMealGroups(day: LocalDate, on: Boolean?) = Unit

    override fun recentFoods(limit: Int): Flow<List<LogItem>> = flowOf(emptyList())

    override fun usualAt(slot: MealSlot, limit: Int, days: Int): Flow<List<LogItem>> = flowOf(emptyList())

    override fun frequentFoods(limit: Int, days: Int): Flow<List<LogItem>> = frequent

    override suspend fun removeItems(ids: List<String>): DayWrite<Unit> = openFor(ids) { removed += ids }

    override suspend fun restoreItems(ids: List<String>): DayWrite<Unit> = openFor(ids) { restored += ids }

    override suspend fun moveItems(ids: List<String>, slot: MealSlot): DayWrite<Unit> = openFor(ids) { moved += ids to slot }

    /** Copies nothing, but answers with one item per id so counts read true. */
    override suspend fun copyItems(ids: List<String>, toDay: LocalDate, slot: MealSlot): DayWrite<List<LogItem>> = open(toDay) {
        copiedItems += Triple(ids, toDay, slot)
        days.value.values.flatMap { it.allItems }.filter { it.id in ids }
    }

    private fun <T> open(day: LocalDate, write: () -> T): DayWrite<T> =
        if (days.value[day]?.closedByUser == true) DayWrite.DayClosed(day) else DayWrite.Written(write())

    /** Refused when any of [ids] sits on a closed day. */
    private fun openFor(ids: List<String>, write: () -> Unit): DayWrite<Unit> {
        val closedDay = days.value.values.firstOrNull { day -> day.closedByUser && day.allItems.any { it.id in ids } }
        return if (closedDay != null) DayWrite.DayClosed(closedDay.date) else DayWrite.Written(write())
    }

    private companion object {
        val CLOSED_AT: Instant = Instant.parse("2026-10-25T21:00:00Z")
    }
}

/** This diary with its days unread until [open] turns true, as a slow first read leaves a screen loading. */
fun FakeDiaryRepository.heldUntil(open: StateFlow<Boolean>): DiaryRepository = object : DiaryRepository by this@heldUntil {
    override fun observeDay(date: LocalDate): Flow<Day> = flow {
        open.first { it }
        emitAll(this@heldUntil.observeDay(date))
    }
}

class FakeWeightRepository(weighIns: List<WeighIn> = emptyList()) : WeightRepository {
    val weighIns = MutableStateFlow(weighIns)
    var reads = 0
        private set

    override fun observeWeighIns(since: Instant?): Flow<List<WeighIn>> {
        reads++
        return weighIns.map { all -> all.filter { since == null || it.measuredAt >= since } }
    }

    override suspend fun addWeighIn(kg: Double, measuredAt: Instant?): WeighIn = error("Today never adds a weigh-in")

    override suspend fun removeWeighIn(id: String) = Unit
}

private fun release(key: String, on: Boolean): AppConfigMap = object : AppConfigMap() {
    override val map: Map<String, *> = mapOf("release" to mapOf(key to on))
}

fun summaryFlag(on: Boolean): TodaySummaryEnabled = TodaySummaryEnabled(release("todaySummary", on))

fun selectFlag(on: Boolean): TodaySelectEnabled = TodaySelectEnabled(release("todaySelect", on))

fun checkInFlag(on: Boolean): CheckInEnabled = CheckInEnabled(release("checkIn", on))

fun foodActionsFlag(on: Boolean): TodayFoodActionsEnabled = TodayFoodActionsEnabled(release("foodActions", on))

fun celebrationsFlag(on: Boolean): com.opson.libraries.diary.CelebrationsReleaseFlag =
    com.opson.libraries.diary.CelebrationsReleaseFlag(release("celebrations", on))

class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: StateFlow<Settings> = state
    override val isKnown: StateFlow<Boolean> = MutableStateFlow(true)

    override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings =
        transform(state.value).also { state.value = it }
}

class FakePolicySource(policy: VisibilityPolicy = policy(PresetId.Full)) : VisibilityPolicySource {
    override val policy = MutableStateFlow(policy)
}

/** Applies nothing; answers [outcome] and remembers what it was asked. */
class FakeNumbersChanger(var outcome: NumbersOutcome = NumbersOutcome.Applied(fewer = false)) : NumbersChanger {
    val requested = mutableListOf<NumbersChange>()

    override suspend fun request(change: NumbersChange): NumbersOutcome {
        requested += change
        return outcome
    }

    override suspend fun confirm(change: NumbersChange) = Unit

    override suspend fun turnOnPause() = Unit

    override suspend fun setEndAfter(weeks: Int, then: PresetId) = Unit

    override suspend fun clearEndAfter() = Unit

    override suspend fun endTimedMode() = Unit

    override fun pauseShown(change: NumbersChange) = Unit

    override fun pauseKept(change: NumbersChange) = Unit
}

class FakeCalendar(private val today: LocalDate = TodayFixtures.today) : LocalCalendar {
    override fun timeZone(): TimeZone = TimeZone.UTC

    override fun today(): LocalDate = today
}

class FakeSelectedDay(day: LocalDate = TodayFixtures.today) : SelectedDay {
    override val date = MutableStateFlow(day)

    override fun select(day: LocalDate) {
        date.value = day
    }
}

/** Holds the latest add like the real one, and remembers what was consumed and undone. */
class FakeRecentLogEvents : RecentLogEvents {
    override val latest = MutableStateFlow<LoggedToMeal?>(null)
    val consumed = mutableListOf<LoggedToMeal>()
    val undone = mutableListOf<LoggedToMeal>()

    override fun logged(event: LoggedToMeal) {
        latest.value = event
    }

    override fun consumed(event: LoggedToMeal) {
        consumed += event
        latest.compareAndSet(event, null)
    }

    override suspend fun undo(event: LoggedToMeal) {
        undone += event
        consumed(event)
    }
}

/** A clock the test moves by hand, alongside the scheduler's virtual time. */
class FakeClock(var now: Instant = TodayFixtures.noon) : Clock {
    override fun now(): Instant = now
}

/** Fasts for Today's card: the latest is set by the test; starts and ends are counted. */
class FakeFastingRepository : FastingRepository {
    val latest = MutableStateFlow<Fast?>(null)
    var startDueCalls = 0
        private set
    var endCalls = 0
        private set

    override fun observeLatest(): Flow<Fast?> = latest

    override suspend fun startDue() {
        startDueCalls++
    }

    override suspend fun end() {
        endCalls++
    }

    override suspend fun endForTimerOff() = Unit
}

/** Caches held in memory, each starting from its serializer's default, as a fresh install's would. */
class FakeCacheFactory : CacheFactory {
    override fun <T : Any> inMemory(defaultValue: () -> T): Cache<T> = MemoryCache { defaultValue() }

    override fun <T : Any> persistent(name: String, serializer: CacheJsonSerializer<T>, loadEagerly: Boolean): Cache<T> =
        MemoryCache { serializer.read(null) }

    private class MemoryCache<T : Any>(private val default: suspend () -> T) : Cache<T> {
        private val value = MutableStateFlow<T?>(null)

        override val updates: Flow<T> = flow {
            get()
            emitAll(value.filterNotNull())
        }

        override suspend fun get(): T = value.value ?: default().also { value.value = it }

        override suspend fun set(value: T) {
            this.value.value = value
        }

        /** Writes the default back, as the persistent cache does, so a collector sees the reset. */
        override suspend fun clear() {
            value.value = default()
        }

        override suspend fun update(transform: (T) -> T): T = transform(get()).also { set(it) }
    }
}
