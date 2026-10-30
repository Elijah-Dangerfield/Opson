package com.opson.libraries.goals.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayReflection
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.GoalBook
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
import com.opson.libraries.goals.GoalFixtures
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.health.StepsRepository
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalVerdict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** A steps goal reads the health app's steps through [RealGoalRepository], and nothing else does. */
class RealGoalRepositoryStepsTest {

    private val today = GoalFixtures.today
    private val monday = GoalFixtures.monday

    @Test
    fun aStepsGoal_today_isMetOnceTheCountReachesIt() = runTest {
        val goal = repository(GoalFixtures.steps, FakeSteps(mapOf(today to 8400))).today().first().single()
        assertEquals(8400, goal.facts.steps)
        assertEquals(GoalVerdict.Met, goal.day.verdict)
    }

    @Test
    fun aStepsGoal_today_isStillGoingUnderIt() = runTest {
        val goal = repository(GoalFixtures.steps, FakeSteps(mapOf(today to 3000))).today().first().single()
        assertEquals(GoalVerdict.InProgress, goal.day.verdict)
    }

    @Test
    fun aStepsGoal_eachDay_isJudgedByItsCount() = runTest {
        val counts = mapOf(monday to 9100, monday.plus(DatePeriod(days = 1)) to 7500, monday.plus(DatePeriod(days = 2)) to 2000)
        val days = repository(GoalFixtures.steps, FakeSteps(counts)).days(monday, today).first().getValue("steps")
        assertEquals(listOf(GoalVerdict.Met, GoalVerdict.Near, GoalVerdict.Off, GoalVerdict.InProgress), days.map { it.verdict })
    }

    @Test
    fun theWeek_runsFromTheWeekStart_withDaysToComeAhead() = runTest {
        val week = repository(GoalFixtures.steps, FakeSteps(mapOf(monday to 9100))).weekDays().first().getValue("steps")
        assertEquals(monday, week.first().date)
        assertEquals(7, week.size)
        assertEquals(GoalVerdict.Met, week.first().verdict)
        assertEquals(GoalVerdict.Future, week.last().verdict)
    }

    @Test
    fun withoutAStepsGoal_theHealthAppIsNeverAskedForSteps() = runTest {
        val steps = FakeSteps(mapOf(today to 8400))
        repository(GoalFixtures.trackEveryDay, steps).today().first()
        assertTrue(steps.asked.isEmpty())
    }

    @Test
    fun withTheHealthReleaseOff_stepsAreNeverRead() = runTest {
        val steps = FakeSteps(mapOf(today to 8400))
        val goal = repository(GoalFixtures.steps, steps, healthOn = false).today().first().single()
        assertTrue(steps.asked.isEmpty())
        assertNull(goal.facts.steps)
    }

    private fun repository(goal: Goal, steps: StepsRepository, healthOn: Boolean = true) = RealGoalRepository(
        settingsRepository = FakeSettings(GoalBook.withGoals(Settings(), listOf(goal))),
        diary = EmptyDiary,
        burned = NoBurned,
        weights = NoWeighIns,
        calendar = object : LocalCalendar {
            override fun timeZone(): TimeZone = TimeZone.UTC
            override fun today(): LocalDate = today
        },
        steps = steps,
        healthFlag = HealthReleaseFlag(
            object : AppConfigMap() {
                override val map: Map<String, *> = mapOf("release" to mapOf("health" to healthOn))
            },
        ),
    )

    private class FakeSteps(private val counts: Map<LocalDate, Int>) : StepsRepository {
        val asked = mutableListOf<Pair<LocalDate, LocalDate>>()
        override fun stepsBetween(from: LocalDate, to: LocalDate, timeZone: TimeZone): Flow<Map<LocalDate, Int>?> {
            asked += from to to
            return flowOf(counts.filterKeys { it in from..to })
        }
    }

    private class FakeSettings(initial: Settings) : SettingsRepository {
        override val settings: StateFlow<Settings> = MutableStateFlow(initial)
        override val isKnown: StateFlow<Boolean> = MutableStateFlow(true)
        override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings = error("not written here")
    }

    private object NoBurned : BurnedRepository {
        override fun burned(day: LocalDate): Flow<Burned?> = flowOf(null)
        override fun restingChoices(): Flow<RestingChoices> = error("not read here")
    }

    private object NoWeighIns : WeightRepository {
        override fun observeWeighIns(since: Instant?): Flow<List<WeighIn>> = flowOf(emptyList())
        override suspend fun addWeighIn(kg: Double, measuredAt: Instant?): WeighIn = error("not written here")
        override suspend fun removeWeighIn(id: String) = Unit
    }

    /** An empty log: every day is [Day.empty]. */
    private object EmptyDiary : DiaryRepository {
        override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Day>> =
            flowOf(generateSequence(from) { it.plus(DatePeriod(days = 1)) }.takeWhile { it <= to }.map { Day.empty(it) }.toList())
        override fun observeDay(date: LocalDate): Flow<Day> = flowOf(Day.empty(date))
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
        override suspend fun setUntracked(day: LocalDate, untracked: Boolean) = error("not written here")
        override fun recentFoods(limit: Int): Flow<List<LogItem>> = error("not read here")
        override fun usualAt(slot: MealSlot, limit: Int, days: Int): Flow<List<LogItem>> = error("not read here")
        override fun frequentFoods(limit: Int, days: Int): Flow<List<LogItem>> = error("not read here")
    }
}
