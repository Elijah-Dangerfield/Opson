package com.opson.libraries.diary.impl

import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class LoggedFoodsImplTest : CoroutineTest() {
    private val db = FakeDiaryDb()
    private val clock = FakeClock()
    private val settings = FakeSettingsRepository()
    private val repo = DiaryRepositoryImpl(db.tables, DiaryWriter(db.tables, clock, FakeScheduler()), settings, FakeCalendar(), FakeFastEnder())
    private val logged = LoggedFoodsImpl(db.tables, settings)
    private val day = LocalDate(2026, 10, 25)

    @Test
    fun countsEachFoodAndKeepsItsNewestEntry() = runUnitTest {
        log(food("Oats", "oats").copy(kcal = 150.0))
        log(food("Eggs", "eggs"))
        log(food("Oats", "oats").copy(kcal = 300.0))

        val foods = logged.observe().first()

        assertEquals(listOf("Oats" to 2, "Eggs" to 1), foods.map { it.latest.name to it.times })
        assertEquals(300.0, foods.first().latest.kcal, "the last amount is the one a + logs again")
    }

    @Test
    fun leavesOutQuickNumbersAndNameOnlyLines() = runUnitTest {
        log(food("Oats", "oats"))
        log(LogDraft(name = "Something at the market", precision = Precision.Estimate, kcal = 100.0))
        log(LogDraft(name = "Quick number", precision = Precision.Estimate, kcal = 300.0), LogMethod.QuickNumber)

        assertEquals(listOf("Oats"), logged.observe().first().map { it.latest.name })
    }

    @Test
    fun customFoodsCountToo() = runUnitTest {
        log(LogDraft(name = "Protein brownie", precision = Precision.Weighed, kcal = 210.0, customFoodId = "brownie"))
        log(LogDraft(name = "Protein brownie", precision = Precision.Weighed, kcal = 210.0, customFoodId = "brownie"))

        assertEquals(listOf(2), logged.observe().first().map { it.times })
    }

    @Test
    fun stopsAtTheLimitNewestFirst() = runUnitTest {
        listOf("Oats", "Eggs", "Rice").forEach { log(food(it, it.lowercase())) }

        assertEquals(listOf("Rice", "Eggs"), logged.observe(limit = 2).first().map { it.latest.name })
    }

    @Test
    fun aFoodTakenOffRecentStaysOutUntilLoggedAgain() = runUnitTest {
        log(food("Oats", "oats"))
        log(food("Eggs", "eggs"))
        settings.update { it.hideFromRecent("f:oats", clock.now.toEpochMilliseconds()) }
        clock.now += 1.minutes

        assertEquals(listOf("Eggs"), logged.observe().first().map { it.latest.name })

        log(food("Oats", "oats"))
        assertEquals(listOf("Oats", "Eggs"), logged.observe().first().map { it.latest.name })
    }

    private suspend fun log(draft: LogDraft, method: LogMethod = LogMethod.Search) {
        repo.addItems(day, MealSlot.Lunch, listOf(draft), method)
        clock.now += 1.minutes
    }

    private fun food(name: String, id: String) = LogDraft(name = name, precision = Precision.Estimate, kcal = 100.0, foodId = id)
}
