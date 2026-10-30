package com.opson.libraries.health.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayReflection
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.ExerciseDraft
import com.opson.libraries.diary.ExerciseEntry
import com.opson.libraries.diary.ExerciseRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.TestDispatcherProvider
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.ExerciseRelease
import com.opson.libraries.health.HealthProfile
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthWeighIn
import com.opson.libraries.health.HealthWorkout
import com.opson.libraries.nutrition.BodyDetail
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.Heights
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.WeightGoal
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.opson.AppData
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * "Randomly the burned thing stopped showing on my Home Screen" (TestFlight 705, Apple Health
 * connected): Burned through the real [RealBurnedRepository] over the real [RealHealthRepository],
 * for settings shaped like the owner's. The goal flow wrote them (`PlanFlow.save`, rebuilt here by
 * hand since this module can't see onboarding): male, a full birthday, 5'11" in feet and inches,
 * pounds, a goal to lose that set Targets as a range, then the range moved by hand and "Use my
 * goal" tapped again.
 *
 * What it shows: nothing the health store does (not connected yet, every read failing, a first
 * read slower than the 2-second grace, the app in the background) makes Burned null while the
 * profile is whole; the health app's side only ever falls back to everyday activity. Burned is null
 * exactly when a detail resting energy needs is missing or out of range, and the Resting energy
 * page's [com.opson.libraries.health.RestingChoices.unusable] names it, which is what Today now
 * shows in Burned's place. Which Today element hid and why is in `TodayViewModelTest` and
 * `TodayEnergyCardTest`.
 */
class OwnerBurnedTest {

    private val today = LocalDate(2026, 10, 30)
    private val noon = Instant.parse("2026-10-30T16:00:00Z")

    /** Lose to 160 lb at half a pound a week. */
    private val owner: Settings = run {
        val goal = WeightGoal(GoalDirection.Lose, targetKg = 72.6, kgPerWeek = 0.23, startKg = 77.1, startDate = today)
        Settings(
            bodySex = BodySex.Male,
            bodyStage = BodyStage.None,
            heightCm = Heights.cm(5, 11.0),
            heightUnit = HeightUnit.FtIn,
            weightUnit = WeightUnit.Lb,
            weightUnitChosen = true,
            everydayActivity = EverydayActivity.SomeWalking,
            // Burned never reads the target, so the goal's own band is left out here.
            weightGoal = goal,
        ).withBirthDate(LocalDate(1998, 5, 11))
    }

    /** 170 lb, typed the right way. */
    private val weighIn = WeighIn("w1", Instant.parse("2026-10-29T13:00:00Z"), 77.1)

    // Nothing the health store does takes Burned away

    @Test
    fun theOwnersSettings_alwaysHaveBurned_whateverTheStoreDoes() = ownerTest(reader = StoreReader(failReads = true)) { repository, _, _ ->
        // Not connected yet (the status starts there), then connected with every read failing.
        val burned = repository.burned(today).first()
        assertNotNull(burned)
        assertEquals(1760, burned.resting.kcal)
    }

    @Test
    fun aSlowFirstRead_inTheBackground_neverMakesBurnedNull() = ownerTest(reader = StoreReader(), inFront = false) { repository, foreground, scope ->
        val seen = mutableListOf<Burned?>()
        val job = scope.launch { repository.burned(today).collect { seen += it } }
        scope.advanceTimeBy(3.seconds)
        foreground.set(true)
        scope.advanceTimeBy(10.minutes)
        scope.runCurrent()
        job.cancel()
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it != null }, "Burned was null at some point: $seen")
    }

    @Test
    fun healthAsTheRestingSource_withEveryReadFailing_fallsBackToTheCalculation() =
        ownerTest(settings = owner.copy(restingSource = RestingSource.Health), reader = StoreReader(failReads = true)) { repository, _, _ ->
            assertEquals(1760, repository.burned(today).first()?.resting?.kcal)
        }

    @Test
    fun aWeighInStoredIn170Kg_isHighButNeverNull() = ownerTest(weighIns = listOf(weighIn.copy(kg = 170.0))) { repository, _, _ ->
        assertEquals(2690, repository.burned(today).first()?.resting?.kcal)
    }

    // The states that do hide it, and what names them

    @Test
    fun noWeighIn_isNoBurned_andNamesTheWeighIn() = ownerTest(weighIns = emptyList()) { repository, _, _ ->
        assertNull(repository.burned(today).first())
        assertEquals(setOf(BodyDetail.Weight), repository.restingChoices().first().unusable)
    }

    @Test
    fun aWeightOutsideTheEquationsRange_isNoBurned_andNamesTheWeight() = ownerTest(weighIns = listOf(weighIn.copy(kg = 450.0))) { repository, _, _ ->
        assertNull(repository.burned(today).first())
        val choices = repository.restingChoices().first()
        assertEquals(emptySet(), choices.missing, "the weight is there")
        assertEquals(setOf(BodyDetail.Weight), choices.unusable, "but the equation can't take it")
    }

    @Test
    fun noSex_noHeight_orNoBirthday_isNoBurned_andNamesWhatIsMissing() = run {
        ownerTest(settings = owner.copy(bodySex = null)) { repository, _, _ ->
            assertNull(repository.burned(today).first())
            assertEquals(setOf(BodyDetail.Sex), repository.restingChoices().first().unusable)
        }
        ownerTest(settings = owner.copy(heightCm = null)) { repository, _, _ ->
            assertEquals(setOf(BodyDetail.Height), repository.restingChoices().first().unusable)
        }
        ownerTest(settings = owner.copy(birthYear = null, birthDate = null)) { repository, _, _ ->
            assertEquals(setOf(BodyDetail.Age), repository.restingChoices().first().unusable)
        }
    }

    /** "Rather not say" and pregnancy both still calculate: neither hides Burned. */
    @Test
    fun ratherNotSay_andPregnant_stillHaveBurned() = run {
        ownerTest(settings = owner.copy(bodySex = BodySex.Unsaid)) { repository, _, _ -> assertNotNull(repository.burned(today).first()) }
        ownerTest(settings = owner.copy(bodySex = BodySex.Female, bodyStage = BodyStage.Pregnant)) { repository, _, _ ->
            assertNotNull(repository.burned(today).first())
        }
    }

    /** A birthday whose year never came with it (written before the two were kept together) still gives an age. */
    @Test
    fun aBirthDateWithoutItsYear_stillHasBurned() = ownerTest(settings = owner.copy(birthYear = null)) { repository, _, _ ->
        assertEquals(1760, repository.burned(today).first()?.resting?.kcal)
    }

    // ---- harness ----

    private fun ownerTest(
        settings: Settings = owner,
        weighIns: List<WeighIn> = listOf(weighIn),
        reader: StoreReader = StoreReader(),
        inFront: Boolean = true,
        block: suspend TestScope.(RealBurnedRepository, HealthAppForeground, TestScope) -> Unit,
    ) = runTest {
        val scope = AppCoroutineScope(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        val foreground = HealthAppForeground().apply { set(inFront) }
        val clock = object : Clock {
            override fun now(): Instant = noon + testScheduler.currentTime.milliseconds
        }
        val health = RealHealthRepository(reader, FakeAppCache(AppData(healthConnected = true)), clock, scope, foreground)
        val repository = RealBurnedRepository(
            settingsRepository = Settings1(settings),
            weights = Weights1(weighIns),
            health = health,
            healthFlag = HealthReleaseFlag(Config(mapOf("release" to mapOf("health" to true)))),
            calendar = Calendar1(today),
            exercise = NoExercise,
            exerciseRelease = ExerciseRelease { true },
            diary = NoDiary,
        )
        try {
            block(repository, foreground, this)
        } finally {
            scope.cancel()
        }
    }

    /** An Apple Health that has been allowed, answering the way HealthKit does. */
    class StoreReader(private val failReads: Boolean = false) : HealthReader {
        override val source = HealthSource.AppleHealth
        override suspend fun availability() = HealthAvailability.Available
        override suspend fun access() = HealthAccess.All
        override suspend fun requestAccess() = true
        override suspend fun read(from: Instant, to: Instant): EnergyExpended {
            if (failReads) error("Protected health data is inaccessible")
            return EnergyExpended(restingKcal = 1100.0, activeKcal = 420.0, workoutKcal = 0.0)
        }
        override suspend fun readDaily(from: Instant, to: Instant, timeZone: TimeZone): Map<LocalDate, EnergyExpended> {
            if (failReads) error("Protected health data is inaccessible")
            return emptyMap()
        }
        override suspend fun dailySteps(from: Instant, to: Instant, timeZone: TimeZone): Map<LocalDate, Int> = emptyMap()
        override suspend fun workouts(from: Instant, to: Instant): List<HealthWorkout> = emptyList()
        override suspend fun weighIns(from: Instant, to: Instant): List<HealthWeighIn> = emptyList()
        override suspend fun profile() = HealthProfile(null, null, null)
        override suspend fun sources(from: Instant, to: Instant): Set<String> = emptySet()
        override fun changes(): Flow<Unit> = MutableSharedFlow()
        override suspend fun revokeAccess() = Unit
        override fun openSettings() = true
    }

    private class Config(override val map: Map<String, *>) : AppConfigMap()

    private class Settings1(initial: Settings) : SettingsRepository {
        override val settings: StateFlow<Settings> = MutableStateFlow(initial)
        override val isKnown: StateFlow<Boolean> = MutableStateFlow(true)
        override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings = error("not used")
    }

    private class Weights1(private val weighIns: List<WeighIn>) : WeightRepository {
        override fun observeWeighIns(since: Instant?): Flow<List<WeighIn>> = flowOf(weighIns)
        override suspend fun addWeighIn(kg: Double, measuredAt: Instant?): WeighIn = error("not used")
        override suspend fun removeWeighIn(id: String) = Unit
    }

    private class Calendar1(private val today: LocalDate) : LocalCalendar {
        override fun timeZone(): TimeZone = TimeZone.UTC
        override fun today(): LocalDate = today
    }

    private object NoExercise : ExerciseRepository {
        override fun observeDay(day: LocalDate): Flow<List<ExerciseEntry>> = flowOf(emptyList())
        override suspend fun get(id: String): ExerciseEntry? = null
        override suspend fun add(draft: ExerciseDraft): DayWrite<ExerciseEntry> = error("not used")
        override suspend fun update(id: String, draft: ExerciseDraft): DayWrite<Unit> = error("not used")
        override suspend fun delete(id: String): DayWrite<Unit> = error("not used")
    }

    /** "Count digesting food" is off, so the diary is never read. */
    private object NoDiary : DiaryRepository {
        override fun observeDay(date: LocalDate): Flow<Day> = error("not read")
        override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Day>> = error("not read")
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
}
