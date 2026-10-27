package com.opson.features.settings.impl.integrations

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/** A health store in memory: [connectOutcome] is what Connect answers, [energy] what a read returns. */
class FakeHealthRepository(
    status: HealthStatus = HealthStatus.NotConnected,
    var connectOutcome: ConnectOutcome = ConnectOutcome.Connected,
    energy: EnergyExpended? = null,
    override val source: HealthSource = HealthSource.HealthConnect,
) : HealthRepository {
    val statusFlow = MutableStateFlow(status)
    val energyFlow = MutableStateFlow(energy)
    var refreshes = 0
    var connects = 0
    var disconnects = 0
    var settingsOpened = 0
    val energyDays = mutableListOf<LocalDate>()

    override val status: StateFlow<HealthStatus> = statusFlow

    override suspend fun refresh() {
        refreshes++
    }

    override suspend fun connect(): ConnectOutcome {
        connects++
        statusFlow.value = when (connectOutcome) {
            ConnectOutcome.Connected -> HealthStatus.Connected
            ConnectOutcome.Denied -> HealthStatus.NotConnected
            ConnectOutcome.NeedsInstall -> HealthStatus.NeedsInstall
            ConnectOutcome.Unsupported -> HealthStatus.Unsupported
        }
        return connectOutcome
    }

    override suspend fun disconnect() {
        disconnects++
        statusFlow.value = HealthStatus.NotConnected
    }

    override fun energy(day: LocalDate, timeZone: TimeZone): Flow<EnergyExpended?> {
        energyDays += day
        return combine(statusFlow, energyFlow) { status, energy -> energy.takeIf { status == HealthStatus.Connected } }
    }

    override suspend fun energyBetween(from: LocalDate, to: LocalDate, timeZone: TimeZone): Map<LocalDate, EnergyExpended>? =
        error("Settings reads one day at a time")

    override fun openSourceSettings(): Boolean {
        settingsOpened++
        return true
    }
}

class FakeCalendar(private val today: LocalDate = IntegrationsFixtures.today) : LocalCalendar {
    override fun timeZone(): TimeZone = TimeZone.UTC
    override fun today(): LocalDate = today
}

fun healthFlag(on: Boolean): HealthReleaseFlag = HealthReleaseFlag(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("health" to on))
    },
)

/**
 * The diary as the days-left-out pieces see it: days by date, read back as ranges, and the
 * untracked mark. Anything else is not theirs to call.
 */
class FakeDiary(days: List<Day> = emptyList()) : DiaryRepository {
    val days = MutableStateFlow(days.associateBy { it.date })
    val ranges = mutableListOf<Pair<LocalDate, LocalDate>>()

    fun untracked(): Set<LocalDate> = days.value.values.filter { it.untracked }.map { it.date }.toSet()

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Day>> {
        ranges += from to to
        return days.map { byDate ->
            generateSequence(from) { it.plus(DatePeriod(days = 1)) }.takeWhile { it <= to }.map { byDate[it] ?: Day.empty(it) }.toList()
        }
    }

    override suspend fun setUntracked(day: LocalDate, untracked: Boolean) {
        days.value = days.value + (day to (days.value[day] ?: Day.empty(day)).copy(untracked = untracked))
    }

    override fun observeDay(date: LocalDate): Flow<Day> = days.map { it[date] ?: Day.empty(date) }
    override fun observeLoggedDates(year: Int, month: Int): Flow<Set<LocalDate>> = error("not read here")
    override suspend fun addItems(day: LocalDate, slot: MealSlot, items: List<LogDraft>, method: LogMethod): List<LogItem> = error("not written here")
    override suspend fun updateItem(item: LogItem) = error("not written here")
    override suspend fun removeItem(id: String) = error("not written here")
    override suspend fun moveItem(id: String, slot: MealSlot) = error("not written here")
    override suspend fun copyDay(from: LocalDate, to: LocalDate): List<LogItem> = error("not written here")
    override suspend fun copySlot(fromDay: LocalDate, toDay: LocalDate, slot: MealSlot): List<LogItem> = error("not written here")
    override suspend fun clearDay(day: LocalDate) = error("not written here")
    override suspend fun addWater(day: LocalDate, ml: Int) = error("not written here")
    override suspend fun removeWater(day: LocalDate, ml: Int) = error("not written here")
    override suspend fun setWater(day: LocalDate, ml: Int) = error("not written here")
    override suspend fun closeDay(day: LocalDate) = error("not written here")
    override suspend fun reopenDay(day: LocalDate) = error("not written here")
    override suspend fun setDayNote(day: LocalDate, note: String?) = error("not written here")
    override suspend fun setDayFeeling(day: LocalDate, feeling: DayFeeling?) = error("not written here")
    override suspend fun setAteNoDetails(day: LocalDate, slot: MealSlot, ate: Boolean) = error("not written here")
    override suspend fun setFeeling(day: LocalDate, slot: MealSlot, feeling: MealFeeling?) = error("not written here")
    override suspend fun setMealGroups(day: LocalDate, on: Boolean?) = error("not written here")
    override fun recentFoods(limit: Int): Flow<List<LogItem>> = error("not read here")
    override fun usualAt(slot: MealSlot, limit: Int, days: Int): Flow<List<LogItem>> = error("not read here")
    override fun frequentFoods(limit: Int, days: Int): Flow<List<LogItem>> = error("not read here")
}
