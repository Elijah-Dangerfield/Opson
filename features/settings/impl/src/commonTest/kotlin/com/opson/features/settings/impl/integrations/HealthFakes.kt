package com.opson.features.settings.impl.integrations

import com.opson.libraries.config.AppConfigMap
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
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthProfile
import com.opson.libraries.health.HealthRecords
import com.opson.libraries.health.HealthRefresh
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthWeighIn
import com.opson.libraries.health.HealthWeighInImport
import com.opson.libraries.health.HealthWorkout
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
import kotlin.time.Instant

/**
 * A health store in memory: [connectOutcome] is what Connect answers, [energy] what a read returns,
 * [sources] the apps it names and [profile] the body details it has.
 */
class FakeHealthRepository(
    status: HealthStatus = HealthStatus.NotConnected,
    var connectOutcome: ConnectOutcome = ConnectOutcome.Connected,
    energy: EnergyExpended? = null,
    override val source: HealthSource = HealthSource.HealthConnect,
    var sources: List<String> = emptyList(),
    var profile: HealthProfile = HealthProfile(null, null, null),
    canAskForMore: Boolean = false,
) : HealthRepository, HealthRecords {
    override val canAskForMore = MutableStateFlow(canAskForMore)
    var sourceReads = 0
    var profileReads = 0
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
            ConnectOutcome.Failed -> statusFlow.value
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

    override fun workouts(day: LocalDate, timeZone: TimeZone): Flow<List<HealthWorkout>?> = error("Settings shows no workouts")

    override suspend fun profile(): HealthProfile? {
        profileReads++
        return profile.takeIf { statusFlow.value == HealthStatus.Connected }
    }

    override suspend fun weighIns(since: Instant): List<HealthWeighIn>? = error("the import reads these, not settings")

    override suspend fun sources(): List<String>? {
        sourceReads++
        return sources.takeIf { statusFlow.value == HealthStatus.Connected }
    }

    override suspend fun refreshNow(day: LocalDate, timeZone: TimeZone): HealthRefresh = error("Settings never pulls to refresh")
}

/** The weigh-in switch: [added] is what turning it on brings in, null for a store that can't be read. */
class FakeWeighInImport(on: Boolean = false, var added: Int? = 2) : HealthWeighInImport {
    val onFlow = MutableStateFlow(on)
    override val on: Flow<Boolean> = onFlow

    override suspend fun turnOn(): Int? {
        onFlow.value = true
        return added
    }

    override suspend fun turnOff() {
        onFlow.value = false
    }

    override suspend fun importNew(): Int? = added
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
    override suspend fun addItems(day: LocalDate, slot: MealSlot, items: List<LogDraft>, method: LogMethod): DayWrite<List<LogItem>> = error("not written here")
    override suspend fun updateItem(item: LogItem): DayWrite<Unit> = error("not written here")
    override suspend fun removeItem(id: String): DayWrite<Unit> = error("not written here")
    override suspend fun moveItem(id: String, slot: MealSlot): DayWrite<Unit> = error("not written here")
    override suspend fun copyDay(from: LocalDate, to: LocalDate): DayWrite<List<LogItem>> = error("not written here")
    override suspend fun copySlot(fromDay: LocalDate, toDay: LocalDate, slot: MealSlot): DayWrite<List<LogItem>> = error("not written here")
    override suspend fun clearDay(day: LocalDate): DayWrite<Unit> = error("not written here")
    override suspend fun addWater(day: LocalDate, ml: Int): DayWrite<Unit> = error("not written here")
    override suspend fun removeWater(day: LocalDate, ml: Int): DayWrite<Unit> = error("not written here")
    override suspend fun setWater(day: LocalDate, ml: Int): DayWrite<Unit> = error("not written here")
    override suspend fun closeDay(day: LocalDate, reflection: DayReflection) = error("not written here")
    override suspend fun reopenDay(day: LocalDate) = error("not written here")
    override suspend fun setDayNote(day: LocalDate, note: String?): DayWrite<Unit> = error("not written here")
    override suspend fun setAteNoDetails(day: LocalDate, slot: MealSlot, ate: Boolean): DayWrite<Unit> = error("not written here")
    override suspend fun setFeeling(day: LocalDate, slot: MealSlot, feeling: MealFeeling?): DayWrite<Unit> = error("not written here")
    override suspend fun setMealGroups(day: LocalDate, on: Boolean?) = error("not written here")
    override fun recentFoods(limit: Int): Flow<List<LogItem>> = error("not read here")
    override fun usualAt(slot: MealSlot, limit: Int, days: Int): Flow<List<LogItem>> = error("not read here")
    override fun frequentFoods(limit: Int, days: Int): Flow<List<LogItem>> = error("not read here")
}
