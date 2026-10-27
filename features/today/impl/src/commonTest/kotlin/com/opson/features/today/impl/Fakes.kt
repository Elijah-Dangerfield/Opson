package com.opson.features.today.impl

import com.opson.libraries.core.fixed
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.CheckInEnabled
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayCheckIns
import com.opson.libraries.diary.DayFeeling
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * sheets read and write does anything; the rest is inert.
 */
class FakeDiaryRepository(days: List<Day> = emptyList()) : DiaryRepository, DiaryItemEditor, DayCheckIns {
    val days = MutableStateFlow(days.associateBy { it.date })
    val loggedDates = MutableStateFlow<Map<Pair<Int, Int>, Set<LocalDate>>>(emptyMap())
    val frequent = MutableStateFlow<List<LogItem>>(emptyList())

    val water = mutableListOf<Pair<LocalDate, Int>>()
    val removedWater = mutableListOf<Pair<LocalDate, Int>>()
    val copiedDays = mutableListOf<Pair<LocalDate, LocalDate>>()
    val copiedSlots = mutableListOf<Triple<LocalDate, LocalDate, MealSlot>>()
    val cleared = mutableListOf<LocalDate>()
    val ateNoDetails = mutableListOf<Pair<LocalDate, MealSlot>>()
    val feelings = mutableListOf<Triple<LocalDate, MealSlot, MealFeeling?>>()
    val notes = mutableListOf<Pair<LocalDate, String?>>()
    val untracked = mutableListOf<Pair<LocalDate, Boolean>>()
    val dayFeelings = mutableListOf<Pair<LocalDate, DayFeeling?>>()
    val closed = mutableListOf<LocalDate>()
    val removed = mutableListOf<String>()
    val restored = mutableListOf<List<String>>()
    val moved = mutableListOf<Pair<List<String>, MealSlot>>()
    val copiedItems = mutableListOf<Triple<List<String>, LocalDate, MealSlot>>()
    val checkIns = mutableListOf<Pair<LocalDate, CheckIn?>>()
    val updated = mutableListOf<LogItem>()

    /** Every write, in order, for tests that assert nothing was written. */
    val writes: Int
        get() = water.size + removedWater.size + copiedDays.size + copiedSlots.size + cleared.size + ateNoDetails.size +
            feelings.size + notes.size + closed.size + removed.size + untracked.size + dayFeelings.size +
            restored.size + moved.size + copiedItems.size + checkIns.size + updated.size

    override fun observeDay(date: LocalDate): Flow<Day> = days.map { it[date] ?: Day.empty(date) }

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Day>> =
        days.map { all -> all.values.filter { it.date in from..to }.sortedBy { it.date } }

    override fun observeLoggedDates(year: Int, month: Int): Flow<Set<LocalDate>> = loggedDates.map { it[year to month].orEmpty() }

    override suspend fun addItems(day: LocalDate, slot: MealSlot, items: List<LogDraft>, method: LogMethod): List<LogItem> = emptyList()

    override suspend fun updateItem(item: LogItem) {
        updated += item
    }

    override suspend fun removeItem(id: String) {
        removed += id
    }

    override suspend fun moveItem(id: String, slot: MealSlot) = Unit

    override suspend fun copyDay(from: LocalDate, to: LocalDate): List<LogItem> {
        copiedDays += from to to
        return emptyList()
    }

    override suspend fun copySlot(fromDay: LocalDate, toDay: LocalDate, slot: MealSlot): List<LogItem> {
        copiedSlots += Triple(fromDay, toDay, slot)
        return emptyList()
    }

    override suspend fun clearDay(day: LocalDate) {
        cleared += day
    }

    override suspend fun addWater(day: LocalDate, ml: Int) {
        water += day to ml
    }

    override suspend fun removeWater(day: LocalDate, ml: Int) {
        removedWater += day to ml
    }

    override suspend fun setWater(day: LocalDate, ml: Int) = Unit

    override suspend fun closeDay(day: LocalDate) {
        closed += day
    }

    override suspend fun reopenDay(day: LocalDate) = Unit

    override suspend fun setDayNote(day: LocalDate, note: String?) {
        notes += day to note
    }

    override suspend fun setUntracked(day: LocalDate, untracked: Boolean) {
        this.untracked += day to untracked
    }

    override suspend fun setDayFeeling(day: LocalDate, feeling: DayFeeling?) {
        dayFeelings += day to feeling
    }

    override suspend fun setAteNoDetails(day: LocalDate, slot: MealSlot, ate: Boolean) {
        if (ate) ateNoDetails += day to slot
    }

    override suspend fun setFeeling(day: LocalDate, slot: MealSlot, feeling: MealFeeling?) {
        feelings += Triple(day, slot, feeling)
    }

    override suspend fun setMealGroups(day: LocalDate, on: Boolean?) = Unit

    override fun recentFoods(limit: Int): Flow<List<LogItem>> = flowOf(emptyList())

    override fun usualAt(slot: MealSlot, limit: Int, days: Int): Flow<List<LogItem>> = flowOf(emptyList())

    override fun frequentFoods(limit: Int, days: Int): Flow<List<LogItem>> = frequent

    override suspend fun removeItems(ids: List<String>) {
        removed += ids
    }

    override suspend fun restoreItems(ids: List<String>) {
        restored += ids
    }

    override suspend fun moveItems(ids: List<String>, slot: MealSlot) {
        moved += ids to slot
    }

    /** Copies nothing, but answers with one item per id so counts read true. */
    override suspend fun copyItems(ids: List<String>, toDay: LocalDate, slot: MealSlot): List<LogItem> {
        copiedItems += Triple(ids, toDay, slot)
        return days.value.values.flatMap { it.allItems }.filter { it.id in ids }
    }

    override suspend fun setCheckIn(day: LocalDate, checkIn: CheckIn?) {
        checkIns += day to checkIn
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
