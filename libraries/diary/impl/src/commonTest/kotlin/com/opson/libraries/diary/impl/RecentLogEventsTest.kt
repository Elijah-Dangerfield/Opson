package com.opson.libraries.diary.impl

import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RecentLogEventsTest : CoroutineTest() {
    private val db = FakeDiaryDb()
    private val repo = DiaryRepositoryImpl(
        db.tables,
        DiaryWriter(db.tables, FakeClock(), FakeScheduler()),
        FakeSettingsRepository(),
        FakeCalendar(),
    )
    private val events = RecentLogEventsImpl(repo)
    private val today = LocalDate(2026, 10, 25)
    private val at = Instant.parse("2026-10-25T18:00:00Z")

    private fun draft(name: String) = LogDraft(name = name, precision = Precision.Named)

    @Test
    fun undoRemovesExactlyTheItemsThatWereLogged() = runUnitTest {
        val earlier = repo.addItems(today, MealSlot.Dinner, listOf(draft("Soup")), LogMethod.Search)
        val added = repo.addItems(today, MealSlot.Dinner, listOf(draft("Rice"), draft("Salad")), LogMethod.Search)
        val event = LoggedToMeal(today, MealSlot.Dinner, added.map { it.id }, at)
        events.logged(event)

        events.undo(event)

        assertEquals(earlier.map { it.id }, repo.observeDay(today).first().items(MealSlot.Dinner).map { it.id })
        assertNull(events.latest.value)
    }

    @Test
    fun consumingAnOlderEventLeavesANewerOneInPlace() {
        val first = LoggedToMeal(today, MealSlot.Lunch, listOf("a"), at)
        val second = first.copy(itemIds = listOf("b"), at = at + 1.seconds)
        events.logged(first)
        events.logged(second)

        events.consumed(first)

        assertEquals(second, events.latest.value)
    }

    @Test
    fun theUndoWindowIsSixSeconds() {
        val event = LoggedToMeal(today, MealSlot.Lunch, listOf("a"), at)
        assertTrue(event.undoOpenAt(at + 5.seconds))
        assertFalse(event.undoOpenAt(at + 6.seconds))
    }
}
