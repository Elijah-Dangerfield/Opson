package com.opson.features.trends.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.NumbersChanger
import com.opson.libraries.diary.NumbersOutcome
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Serves days from [days]; records what range each observer asked for. Writes are not used on these screens. */
class FakeDiaryRepository(days: List<Day> = TrendsFixtures.days()) : DiaryRepository {
    val days = MutableStateFlow(days)
    val dayRequests = mutableListOf<LocalDate>()
    val rangeRequests = mutableListOf<Pair<LocalDate, LocalDate>>()

    override fun observeDay(date: LocalDate): Flow<Day> {
        dayRequests += date
        return days.map { all -> all.firstOrNull { it.date == date } ?: Day.empty(date) }
    }

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Day>> {
        rangeRequests += from to to
        return days.map { all -> all.filter { it.date in from..to } }
    }

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
    override suspend fun setAteNoDetails(day: LocalDate, slot: MealSlot, ate: Boolean) = unused()
    override suspend fun setFeeling(day: LocalDate, slot: MealSlot, feeling: MealFeeling?) = unused()
    override suspend fun setMealGroups(day: LocalDate, on: Boolean?) = unused()
    override fun recentFoods(limit: Int): Flow<List<LogItem>> = unused()
    override fun usualAt(slot: MealSlot, limit: Int, days: Int): Flow<List<LogItem>> = unused()
    override fun frequentFoods(limit: Int, days: Int): Flow<List<LogItem>> = unused()
}

class FakeWeightRepository(weighIns: List<WeighIn> = TrendsFixtures.weighIns()) : WeightRepository {
    val weighIns = MutableStateFlow(weighIns)
    val added = mutableListOf<Double>()

    override fun observeWeighIns(since: Instant?): Flow<List<WeighIn>> =
        weighIns.map { all -> all.filter { since == null || it.measuredAt >= since } }

    override suspend fun addWeighIn(kg: Double, measuredAt: Instant?): WeighIn {
        added += kg
        val weighIn = WeighIn(id = "added-${added.size}", measuredAt = measuredAt ?: TrendsFixtures.clock.now(), kg = kg)
        weighIns.value = weighIns.value + weighIn
        return weighIn
    }

    override suspend fun removeWeighIn(id: String) = unused()
}

class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    override val settings = MutableStateFlow(initial)
    override val isKnown: StateFlow<Boolean> = MutableStateFlow(true)
    var updates = 0

    override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings {
        updates += 1
        val next = transform(settings.value)
        settings.value = next
        return next
    }
}

/** A fixed policy the test can swap, standing in for the settings-driven source. */
class FakePolicySource(initial: VisibilityPolicy = TrendsFixtures.policy()) : VisibilityPolicySource {
    override val policy = MutableStateFlow(initial)
}

/**
 * The real rule over the fake settings, so a test can see what was written:
 * a change that needs the pause writes nothing, anything else is applied and
 * stamped. The rule itself is `SettingsNumbersChangerTest` in `:libraries:diary:impl`.
 */
class FakeNumbersChanger(private val settings: FakeSettingsRepository) : NumbersChanger {
    val requested = mutableListOf<NumbersChange>()

    override suspend fun request(change: NumbersChange): NumbersOutcome {
        requested += change
        val current = settings.settings.value.numbers
        if (change.needsPause(current)) return NumbersOutcome.NeedsPause
        val next = change.applyTo(current, TrendsFixtures.clock.now())
        settings.update { it.withNumbers(next) }
        return NumbersOutcome.Applied(fewer = next.score() < current.score())
    }

    override suspend fun confirm(change: NumbersChange) = unused()
    override suspend fun turnOnPause() = unused()
    override fun pauseShown(change: NumbersChange) = unused()
    override fun pauseKept(change: NumbersChange) = unused()
}

private fun unused(): Nothing = error("Not used by the Trends screens")
