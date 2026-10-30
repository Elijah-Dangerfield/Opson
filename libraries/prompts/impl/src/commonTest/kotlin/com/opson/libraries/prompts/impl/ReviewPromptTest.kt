@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.prompts.impl

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.DayMark
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.prompts.PromptEvent
import com.opson.libraries.prompts.impl.candidates.MomentWatcher
import com.opson.libraries.prompts.impl.candidates.ReviewPrompt
import com.opson.libraries.review.ReviewLaunchOutcome
import com.opson.libraries.review.ReviewMoment
import com.opson.libraries.review.ReviewPromptCoordinator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReviewPromptTest : CoroutineTest() {

    private val today = LocalDate(2026, 10, 30)

    @Test
    fun `the moments that can ask`() {
        assertEquals(
            setOf(PromptEvent.CelebrationCheered, PromptEvent.DayClosed, PromptEvent.ShowedUp, PromptEvent.FoodApproved),
            ReviewPrompt.MOMENTS.keys,
        )
        assertEquals(ReviewMoment.entries.toSet(), ReviewPrompt.MOMENTS.values.toSet(), "every moment has its event")
    }

    @Test
    fun `another install's moment reads nothing and logs nothing`() = runUnitTest {
        val coordinator = FakeCoordinator(variant = ReviewMoment.Celebration)
        var reads = 0

        val ask = ReviewPrompt.shouldAsk(ReviewMoment.DayFeltGood, coordinator) { reads++; true }

        assertFalse(ask)
        assertEquals(0, reads)
        assertEquals(0, coordinator.clearChecks)
    }

    @Test
    fun `a moment that did not really happen is not a skip`() = runUnitTest {
        val coordinator = FakeCoordinator(variant = ReviewMoment.DayFeltGood)

        assertFalse(ReviewPrompt.shouldAsk(ReviewMoment.DayFeltGood, coordinator) { false })
        assertEquals(0, coordinator.clearChecks)
    }

    @Test
    fun `this install's moment asks when the coordinator is clear`() = runUnitTest {
        val coordinator = FakeCoordinator(variant = ReviewMoment.Celebration)

        assertTrue(ReviewPrompt.shouldAsk(ReviewMoment.Celebration, coordinator) { true })
        coordinator.clear = false
        assertFalse(ReviewPrompt.shouldAsk(ReviewMoment.Celebration, coordinator) { true })
        assertEquals(2, coordinator.clearChecks)
    }

    @Test
    fun `a day felt good only when wrapped up as Good`() {
        val closed = Instant.fromEpochMilliseconds(1)
        assertTrue(ReviewPrompt.feltGood(Day(date = today, closedAt = closed, feeling = DayFeeling.Good)))
        assertFalse(ReviewPrompt.feltGood(Day(date = today, closedAt = closed, feeling = DayFeeling.Steady)))
        assertFalse(ReviewPrompt.feltGood(Day(date = today, closedAt = closed)))
        assertFalse(ReviewPrompt.feltGood(Day(date = today, feeling = DayFeeling.Good)), "not wrapped up")
    }

    @Test
    fun `showing up means ten of the last fourteen days`() {
        fun marks(days: Int, skipping: Set<Int> = emptySet()) =
            (0 until days).filter { it !in skipping }.map { DayMark(today.minus(it, DateTimeUnit.DAY), logged = true) }

        assertTrue(ReviewPrompt.showsUp(marks(10), today))
        assertFalse(ReviewPrompt.showsUp(marks(9), today))
        assertFalse(ReviewPrompt.showsUp(marks(20, skipping = (0 until 14).filter { it % 3 == 0 }.toSet()), today))
        val untracked = marks(10).map { if (it.date == today) it.copy(untracked = true) else it }
        assertFalse(ReviewPrompt.showsUp(untracked, today), "a rest day is not showing up")
    }

    @Test
    fun `showed up fires on the first thing of the day only`() = runUnitTest {
        val days = MutableSharedFlow<MomentWatcher.Companion.Today>()
        var fired = 0
        backgroundScope.launch { MomentWatcher.showedUp(days).collect { fired++ } }
        runCurrent()
        val yesterday = today.minus(1, DateTimeUnit.DAY)

        days.emit(MomentWatcher.Companion.Today(yesterday, showedUp = true))
        assertEquals(0, fired, "where things stand at launch is not a moment")
        days.emit(MomentWatcher.Companion.Today(today, showedUp = false))
        days.emit(MomentWatcher.Companion.Today(today, showedUp = true))
        assertEquals(1, fired)
        days.emit(MomentWatcher.Companion.Today(today, showedUp = true))
        assertEquals(1, fired, "a second log the same day is not")
    }

    @Test
    fun `a new day that starts with a log is a moment`() = runUnitTest {
        val days = MutableSharedFlow<MomentWatcher.Companion.Today>()
        var fired = 0
        backgroundScope.launch { MomentWatcher.showedUp(days).collect { fired++ } }
        runCurrent()

        days.emit(MomentWatcher.Companion.Today(today.minus(1, DateTimeUnit.DAY), showedUp = true))
        days.emit(MomentWatcher.Companion.Today(today, showedUp = true))
        assertEquals(1, fired)
    }

    @Test
    fun `an approval fires only for a food seen waiting`() = runUnitTest {
        val reviews = MutableSharedFlow<Map<String, ReviewStatus>>()
        var fired = 0
        backgroundScope.launch { MomentWatcher.approvals(reviews).collect { fired++ } }
        runCurrent()

        reviews.emit(emptyMap())
        reviews.emit(mapOf("old" to ReviewStatus.Approved, "new" to ReviewStatus.Pending))
        assertEquals(0, fired, "the stored copy loading is not a moment")
        reviews.emit(mapOf("old" to ReviewStatus.Approved, "new" to ReviewStatus.Approved))
        assertEquals(1, fired)
        reviews.emit(mapOf("old" to ReviewStatus.Approved, "new" to ReviewStatus.Approved))
        assertEquals(1, fired)
    }
}

@HiddenFromObjC
private class FakeCoordinator(
    var variant: ReviewMoment? = null,
    var clear: Boolean = true,
) : ReviewPromptCoordinator {
    var clearChecks = 0
        private set

    override suspend fun asksAt(moment: ReviewMoment): Boolean = variant == moment

    override suspend fun clearToAsk(moment: ReviewMoment): Boolean {
        clearChecks++
        return clear
    }

    override suspend fun couldAsk(): Boolean = clear

    override suspend fun ask(moment: ReviewMoment, fromQa: Boolean): ReviewLaunchOutcome = ReviewLaunchOutcome.Requested
}
