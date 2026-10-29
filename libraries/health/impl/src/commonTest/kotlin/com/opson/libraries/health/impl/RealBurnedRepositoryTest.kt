package com.opson.libraries.health.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayReflection
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.ExerciseDraft
import com.opson.libraries.diary.ExerciseEntry
import com.opson.libraries.diary.ExerciseRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.ExerciseRelease
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.BodyDetail
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.RestingEnergy
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.RestingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * [RealBurnedRepository]: the resting source picked, its fall back to the calculation, the
 * latest weigh-in, and activity only from a connected store with `release.health` on.
 */
class RealBurnedRepositoryTest {

    private val today = LocalDate(2026, 10, 27)

    /** 70 kg, 170 cm, 35, female: 1,430 resting. */
    private val profiled = Settings(birthYear = 1991, heightCm = 170.0, bodySex = BodySex.Female)

    private fun repository(
        settings: Settings = profiled,
        weighIns: List<WeighIn> = listOf(WeighIn("w1", Instant.parse("2026-10-01T08:00:00Z"), 80.0), WeighIn("w2", Instant.parse("2026-10-20T08:00:00Z"), 70.0)),
        health: FakeHealth = FakeHealth(),
        healthOn: Boolean = true,
        exercise: List<ExerciseEntry> = emptyList(),
        exerciseOn: Boolean = false,
        diary: FakeDiary = FakeDiary(),
    ) = RealBurnedRepository(
        FakeSettings(settings),
        FakeWeights(weighIns),
        health,
        healthFlag(healthOn),
        FixedCalendar(today),
        FakeExercise(exercise),
        ExerciseRelease { exerciseOn },
        diary,
    )

    private fun entry(kcal: Int, notOnWatch: Boolean = false) =
        ExerciseEntry("e$kcal", today, Instant.parse("2026-10-27T07:00:00Z"), "cycling", minutes = 30, kcal = kcal, notOnWatch = notOnWatch)

    @Test
    fun calculated_fromTheLatestWeighIn() = runTest {
        val burned = repository(health = FakeHealth(connected = false)).burned(today).first()
        assertEquals(Burned(RestingEnergy(1430, RestingFrom.Calculated), activityCounted = true, everydayKcal = 290), burned)
        assertEquals(1720, burned?.totalKcal, "sedentary until the person says otherwise: 1,430 plus 0.2 of it")
    }

    @Test
    fun withNoHealthApp_everydayActivityIsTheDaysMovement_withExerciseOff() = runTest {
        val light = repository(settings = profiled.copy(everydayActivity = EverydayActivity.SomeWalking), health = FakeHealth(connected = false)).burned(today).first()
        assertEquals(true, light?.movementEstimated)
        assertEquals(1430 + 540, light?.totalKcal)
        // Connected, the reading wins and everyday activity is left out.
        val connected = repository(settings = profiled.copy(everydayActivity = EverydayActivity.PhysicalWork)).burned(today).first()
        assertEquals(false, connected?.movementEstimated)
        assertEquals(1430 + 570, connected?.totalKcal)
    }

    @Test
    fun nothingToCalculateFrom_isNoBurned_andSaysWhatIsMissing() = runTest {
        val repository = repository(settings = Settings(), weighIns = emptyList(), health = FakeHealth(connected = false))
        assertNull(repository.burned(today).first())
        val choices = repository.restingChoices().first()
        assertNull(choices.calculatedKcal)
        assertEquals(setOf(BodyDetail.Weight, BodyDetail.Height, BodyDetail.Age, BodyDetail.Sex), choices.missing)
    }

    @Test
    fun activityFromAConnectedStore_countsWhenSwitchedOn() = runTest {
        val burned = repository().burned(today).first()
        assertEquals(390, burned?.movementKcal)
        assertEquals(180, burned?.workoutKcal)
        assertEquals(1430 + 570, burned?.totalKcal)

        val off = repository(settings = profiled.copy(activityInBurned = false)).burned(today).first()
        assertEquals(1430 + 290, off?.totalKcal, "switched off, the watch stops counting and the everyday baseline stays")
    }

    @Test
    fun theHealthApp_givesTheMostRecentWholeDaysResting_elseTheCalculation() = runTest {
        val health = repository(settings = profiled.copy(restingSource = RestingSource.Health)).burned(today).first()
        assertEquals(RestingEnergy(1560, RestingFrom.Health), health?.resting)

        val none = repository(settings = profiled.copy(restingSource = RestingSource.Health), health = FakeHealth(week = emptyMap()))
        assertEquals(RestingEnergy(1430, RestingFrom.Calculated), none.burned(today).first()?.resting)
    }

    @Test
    fun withReleaseHealthOff_theStoreIsNeverAsked() = runTest {
        val health = FakeHealth()
        val burned = repository(settings = profiled.copy(restingSource = RestingSource.Health), health = health, healthOn = false).burned(today).first()
        assertEquals(Burned(RestingEnergy(1430, RestingFrom.Calculated), everydayKcal = 290), burned)
        assertEquals(0, health.reads)
        assertNull(repository(healthOn = false).restingChoices().first().healthSource)
    }

    @Test
    fun withoutAHealthApp_burnedIsRestingPlusEverydayActivityPlusLoggedExercise() = runTest {
        val repository = repository(
            settings = profiled.copy(everydayActivity = EverydayActivity.SomeWalking),
            health = FakeHealth(connected = false),
            exercise = listOf(entry(200), entry(150)),
            exerciseOn = true,
        )

        val burned = repository.burned(today).first()

        // 1,430 resting, 1,430 x 0.375 = 536 everyday (540 to the nearest 10), 350 logged.
        assertEquals(540, burned?.everydayKcal)
        assertEquals(350, burned?.exerciseKcal)
        assertEquals(1430 + 540 + 350, burned?.totalKcal)
    }

    @Test
    fun withAHealthApp_itsActivityReplacesTheManualSide_exceptWhatTheWatchMissed() = runTest {
        val burned = repository(exercise = listOf(entry(200), entry(150, notOnWatch = true)), exerciseOn = true).burned(today).first()

        assertNull(burned?.everydayKcal)
        assertEquals(150, burned?.exerciseKcal)
        assertEquals(1430 + 390 + 180 + 150, burned?.totalKcal)
    }

    @Test
    fun aStoreThatReadNoActivity_fallsBackToTheManualSide() = runTest {
        val burned = repository(health = FakeHealth(todayEnergy = EnergyExpended(900.0, null, null)), exercise = listOf(entry(200)), exerciseOn = true)
            .burned(today).first()

        assertEquals(1430 + 290 + 200, burned?.totalKcal)
    }

    @Test
    fun withReleaseExerciseOff_loggedExerciseIsLeftOut_butEverydayActivityStillCounts() = runTest {
        val exercise = listOf(entry(200))
        val burned = repository(health = FakeHealth(connected = false), exercise = exercise, exerciseOn = false).burned(today).first()

        assertEquals(Burned(RestingEnergy(1430, RestingFrom.Calculated), everydayKcal = 290), burned)
    }

    @Test
    fun yourOwnNumber_wins() = runTest {
        val own = repository(settings = profiled.copy(restingSource = RestingSource.Own, restingOwnKcal = 1600)).burned(today).first()
        assertEquals(RestingEnergy(1600, RestingFrom.Own), own?.resting)
    }

    @Test
    fun countingDigestingFood_addsATenthOfWhatWasEaten() = runTest {
        val diary = FakeDiary(eatenKcal = 1245.0)
        val burned = repository(settings = profiled.copy(thermicEffectInBurned = true), health = FakeHealth(connected = false), diary = diary).burned(today).first()

        assertEquals(125, burned?.foodKcal)
        assertEquals(1430 + 290 + 125, burned?.totalKcal)
    }

    @Test
    fun countingDigestingFood_isAlsoAddedOnTopOfTheHealthApp() = runTest {
        val diary = FakeDiary(eatenKcal = 2000.0)
        val burned = repository(settings = profiled.copy(thermicEffectInBurned = true), diary = diary).burned(today).first()

        assertEquals(200, burned?.foodKcal)
        assertEquals(1430 + 570 + 200, burned?.totalKcal, "the store's active energy never includes digestion")
    }

    @Test
    fun digestingFood_off_neverReadsTheDiary() = runTest {
        val diary = FakeDiary(eatenKcal = 1245.0)
        val burned = repository(health = FakeHealth(connected = false), diary = diary).burned(today).first()

        assertNull(burned?.foodKcal)
        assertEquals(0, diary.reads)
    }

    @Test
    fun aSpan_isEachDayAsBurnedWouldGiveIt() = runTest {
        val repository = repository(
            settings = profiled.copy(thermicEffectInBurned = true, everydayActivity = EverydayActivity.SomeWalking),
            health = FakeHealth(connected = false),
            exercise = listOf(entry(200)),
            exerciseOn = true,
            diary = FakeDiary(eatenKcal = 1245.0),
        )
        val from = LocalDate(2026, 10, 25)

        val span = repository.burnedBetween(from, today).first()

        val dates = listOf(from, LocalDate(2026, 10, 26), today)
        assertEquals(dates.associateWith { requireNotNull(repository.burned(it).first()) }, span)
        assertEquals(1430 + 540 + 200 + 125, span[today]?.totalKcal, "today's logged exercise, and digesting food on every day")
        assertEquals(1430 + 540 + 125, span[from]?.totalKcal)
    }

    @Test
    fun aSpan_readsTheHealthAppsActivityPerDay_andFallsBackWhereItHasNone() = runTest {
        val span = repository().burnedBetween(LocalDate(2026, 10, 25), LocalDate(2026, 10, 26)).first()

        assertEquals(1430 + 500, span[LocalDate(2026, 10, 25)]?.totalKcal, "the store's active energy that day")
        assertEquals(1430 + 290, span[LocalDate(2026, 10, 26)]?.totalKcal, "nothing read that day, so everyday activity stands in")
    }

    @Test
    fun aSpan_withNothingToCalculateFrom_isEmpty() = runTest {
        val repository = repository(settings = Settings(), weighIns = emptyList(), health = FakeHealth(connected = false))
        assertEquals(emptyMap(), repository.burnedBetween(LocalDate(2026, 10, 25), today).first())
    }

    private fun healthFlag(on: Boolean) = HealthReleaseFlag(
        object : AppConfigMap() {
            override val map: Map<String, *> = mapOf("release" to mapOf("health" to on))
        },
    )

    private class FakeSettings(initial: Settings) : SettingsRepository {
        override val settings = MutableStateFlow(initial)
        override val isKnown: StateFlow<Boolean> = MutableStateFlow(true)

        override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings {
            settings.value = transform(settings.value)
            return settings.value
        }
    }

    private class FakeWeights(private val weighIns: List<WeighIn>) : WeightRepository {
        override fun observeWeighIns(since: Instant?): Flow<List<WeighIn>> = flowOf(weighIns)
        override suspend fun addWeighIn(kg: Double, measuredAt: Instant?): WeighIn = error("not used")
        override suspend fun removeWeighIn(id: String) = Unit
    }

    private class FakeExercise(private val entries: List<ExerciseEntry>) : ExerciseRepository {
        override fun observeDay(day: LocalDate): Flow<List<ExerciseEntry>> = flowOf(entries.filter { it.day == day })
        override suspend fun get(id: String): ExerciseEntry? = entries.firstOrNull { it.id == id }
        override suspend fun add(draft: ExerciseDraft): DayWrite<ExerciseEntry> = error("not used")
        override suspend fun update(id: String, draft: ExerciseDraft): DayWrite<Unit> = error("not used")
        override suspend fun delete(id: String): DayWrite<Unit> = error("not used")
    }

    /** One day with one food of [eatenKcal]; counts the reads, so a test can say it was never asked. */
    private inner class FakeDiary(private val eatenKcal: Double = 0.0) : DiaryRepository {
        var reads = 0

        override fun observeDay(date: LocalDate): Flow<Day> {
            reads++
            val item = LogItem(
                id = "lunch",
                day = date,
                slot = MealSlot.Lunch,
                loggedAt = Instant.parse("2026-10-27T12:00:00Z"),
                name = "lunch",
                precision = Precision.Estimate,
                method = LogMethod.Search,
                kcal = eatenKcal,
            )
            return flowOf(Day(date, MealSlot.entries.associateWith { emptyList<LogItem>() } + (MealSlot.Lunch to listOf(item))))
        }

        override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Day>> {
            val dates = generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= to }.toList()
            return kotlinx.coroutines.flow.combine(dates.map(::observeDay)) { it.toList() }
        }
        override fun observeLoggedDates(year: Int, month: Int): Flow<Set<LocalDate>> = error("not used")
        override suspend fun addItems(day: LocalDate, slot: MealSlot, items: List<LogDraft>, method: LogMethod): DayWrite<List<LogItem>> = error("not used")
        override suspend fun updateItem(item: LogItem): DayWrite<Unit> = error("not used")
        override suspend fun removeItem(id: String): DayWrite<Unit> = error("not used")
        override suspend fun moveItem(id: String, slot: MealSlot): DayWrite<Unit> = error("not used")
        override suspend fun copyDay(from: LocalDate, to: LocalDate): DayWrite<List<LogItem>> = error("not used")
        override suspend fun copySlot(fromDay: LocalDate, toDay: LocalDate, slot: MealSlot): DayWrite<List<LogItem>> = error("not used")
        override suspend fun clearDay(day: LocalDate): DayWrite<Unit> = error("not used")
        override suspend fun addWater(day: LocalDate, ml: Int): DayWrite<Unit> = error("not used")
        override suspend fun removeWater(day: LocalDate, ml: Int): DayWrite<Unit> = error("not used")
        override suspend fun setWater(day: LocalDate, ml: Int): DayWrite<Unit> = error("not used")
        override suspend fun closeDay(day: LocalDate, reflection: DayReflection) = error("not used")
        override suspend fun reopenDay(day: LocalDate) = error("not used")
        override suspend fun setDayNote(day: LocalDate, note: String?): DayWrite<Unit> = error("not used")
        override suspend fun setUntracked(day: LocalDate, untracked: Boolean) = error("not used")
        override suspend fun setAteNoDetails(day: LocalDate, slot: MealSlot, ate: Boolean): DayWrite<Unit> = error("not used")
        override suspend fun setFeeling(day: LocalDate, slot: MealSlot, feeling: MealFeeling?): DayWrite<Unit> = error("not used")
        override suspend fun setMealGroups(day: LocalDate, on: Boolean?) = error("not used")
        override fun recentFoods(limit: Int): Flow<List<LogItem>> = error("not used")
        override fun usualAt(slot: MealSlot, limit: Int, days: Int): Flow<List<LogItem>> = error("not used")
        override fun frequentFoods(limit: Int, days: Int): Flow<List<LogItem>> = error("not used")
    }

    private class FixedCalendar(private val today: LocalDate) : LocalCalendar {
        override fun timeZone(): TimeZone = TimeZone.UTC
        override fun today(): LocalDate = today
    }

    /** Connected, with 390 movement and 180 workout today, and 1,562 resting the day before yesterday. */
    private inner class FakeHealth(
        connected: Boolean = true,
        private val todayEnergy: EnergyExpended = EnergyExpended(restingKcal = 900.0, activeKcal = 570.0, workoutKcal = 180.0),
        private val week: Map<LocalDate, EnergyExpended> = mapOf(
            LocalDate(2026, 10, 25) to EnergyExpended(restingKcal = 1562.0, activeKcal = 500.0, workoutKcal = null),
            LocalDate(2026, 10, 20) to EnergyExpended(restingKcal = 1700.0, activeKcal = 500.0, workoutKcal = null),
        ),
    ) : HealthRepository {
        var reads = 0
        override val source = HealthSource.HealthConnect
        override val status: StateFlow<HealthStatus> = MutableStateFlow(if (connected) HealthStatus.Connected else HealthStatus.NotConnected)
        override suspend fun refresh() = Unit
        override suspend fun connect(): ConnectOutcome = ConnectOutcome.Connected
        override suspend fun disconnect() = Unit
        override fun energy(day: LocalDate, timeZone: TimeZone): Flow<EnergyExpended?> =
            flowOf(todayEnergy).map { reads++; it }

        override suspend fun energyBetween(from: LocalDate, to: LocalDate, timeZone: TimeZone): Map<LocalDate, EnergyExpended> {
            reads++
            return week.filterKeys { it in from..to }
        }

        override fun openSourceSettings(): Boolean = false
    }
}
