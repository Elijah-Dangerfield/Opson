package com.opson.libraries.prompts.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.prompts.PromptAccept
import com.opson.libraries.prompts.PromptBlock
import com.opson.libraries.prompts.PromptCandidate
import com.opson.libraries.prompts.PromptEvent
import com.opson.libraries.prompts.PromptFrequency
import com.opson.libraries.prompts.PromptPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The processor's rules end to end, over an in-memory ledger: priority, the caps, the gaps, the
 * quiet start, who is asked, and what a moment may skip. Every test starts past the quiet start
 * unless it is about the quiet start.
 */
class PromptProcessorTest : CoroutineTest() {

    private val clock = MutableClock(T0)
    private val ledger = InMemoryLedger()
    private val audience = FakeAudience()
    private val sessions = FakeSessions()
    private val telemetry = RecordingTelemetry()

    private fun processor(vararg candidates: PromptCandidate, settled: Boolean = true): PromptProcessor {
        if (settled) ledger.accounts[USER] = AccountLedger(firstSeenAtMs = (T0 - 30.days).toEpochMilliseconds())
        return PromptProcessor(candidates.toSet(), ledger, audience, sessions, telemetry, clock)
    }

    /** A new session a day and a bit later: the usual way the next prompt gets its chance. */
    private fun nextDay() {
        clock.advance(25.hours)
        sessions.roll()
    }

    @Test
    fun `the highest priority eligible prompt wins`() = runUnitTest {
        val low = FakeCandidate("low", priority = 100)
        val high = FakeCandidate("high", priority = 300, eligible = false)
        val mid = FakeCandidate("mid", priority = 200)
        val processor = processor(low, high, mid)

        assertEquals("mid", processor.next()?.id)
        assertEquals(listOf("shown:mid:dialog:today"), telemetry.events)
    }

    @Test
    fun `a tie goes by id whatever order the set gives`() = runUnitTest {
        val processor = processor(FakeCandidate("b"), FakeCandidate("a"))

        assertEquals("a", processor.next()?.id)
    }

    @Test
    fun `eligibility is only asked once the rules pass`() = runUnitTest {
        val capped = FakeCandidate("capped", priority = 300)
        val processor = processor(capped, FakeCandidate("other"))
        ledger.update(USER) { it.with("capped") { record -> record.copy(shows = 1) } }

        assertEquals("other", processor.next()?.id)
        assertEquals(0, capped.eligibilityChecks)
    }

    @Test
    fun `a check that throws counts as not eligible`() = runUnitTest {
        val processor = processor(FakeCandidate("broken", priority = 300, throws = true), FakeCandidate("fine"))

        assertEquals("fine", processor.next()?.id)
    }

    @Test
    fun `one prompt a session`() = runUnitTest {
        val processor = processor(FakeCandidate("a", priority = 300), FakeCandidate("b"))

        assertEquals("a", processor.next()?.id)
        clock.advance(2.days)
        assertNull(processor.next(), "same session, even days later")

        sessions.roll()
        assertEquals("b", processor.next()?.id)
    }

    @Test
    fun `a day between any two prompts`() = runUnitTest {
        val processor = processor(FakeCandidate("a", priority = 300), FakeCandidate("b"))
        processor.next()

        clock.advance(23.hours)
        sessions.roll()
        assertNull(processor.next())

        clock.advance(1.hours)
        sessions.roll()
        assertEquals("b", processor.next()?.id)
    }

    @Test
    fun `shown once by default whatever happened to it`() = runUnitTest {
        val once = FakeCandidate("once")
        val processor = processor(once)
        processor.next()

        repeat(5) {
            nextDay()
            assertNull(processor.next())
        }
        assertEquals(1, ledger.account(USER).record("once").shows)
    }

    @Test
    fun `a prompt allowed back returns no sooner than its own gap`() = runUnitTest {
        val processor = processor(FakeCandidate("card", frequency = PromptFrequency(maxShows = 3, minGap = 3.days)))

        assertEquals("card", processor.next()?.id)
        nextDay()
        assertNull(processor.next(), "a day later")
        clock.advance(2.days)
        sessions.roll()
        assertEquals("card", processor.next()?.id)
        clock.advance(3.days)
        sessions.roll()
        assertEquals("card", processor.next()?.id)
        clock.advance(3.days)
        sessions.roll()
        assertNull(processor.next(), "three showings is its cap")
    }

    @Test
    fun `Not now ends a prompt that may come back`() = runUnitTest {
        val processor = processor(FakeCandidate("card", frequency = PromptFrequency(maxShows = 3, minGap = 1.days)))
        processor.next()
        processor.dismissed("card")

        clock.advance(10.days)
        sessions.roll()
        assertNull(processor.next())
        assertEquals(listOf("shown:card:dialog:today", "dismissed:card"), telemetry.events)
    }

    @Test
    fun `accepting ends it and says where to go`() = runUnitTest {
        val processor = processor(FakeCandidate("go", frequency = PromptFrequency(maxShows = 5, minGap = 1.days)))
        processor.next()

        assertIs<PromptAccept.Open>(processor.accepted("go"))
        clock.advance(10.days)
        sessions.roll()
        assertNull(processor.next())
        assertEquals(true, ledger.account(USER).record("go").accepted)
    }

    @Test
    fun `a showing takes one answer`() = runUnitTest {
        val processor = processor(FakeCandidate("go"))
        processor.next()

        processor.accepted("go")
        // The dialog's exit after the button calls back as a dismissal too.
        processor.dismissed("go")
        assertNull(processor.accepted("go"))

        val record = ledger.account(USER).record("go")
        assertEquals(0, record.dismissals)
        assertEquals(listOf("shown:go:dialog:today", "accepted:go"), telemetry.events)
    }

    @Test
    fun `a sensitive prompt is counted but never sent as an event`() = runUnitTest {
        val processor = processor(FakeCandidate("quiet", sensitive = true, frequency = PromptFrequency(maxShows = 5, maxDismissals = 5, minGap = 1.days)))

        assertEquals("quiet", processor.next()?.id)
        processor.dismissed("quiet")
        nextDay()
        assertEquals("quiet", processor.next()?.id)
        processor.accepted("quiet")

        assertEquals(emptyList(), telemetry.events)
        assertEquals(2, ledger.account(USER).record("quiet").shows)
    }

    @Test
    fun `nothing before onboarding is done or signed out`() = runUnitTest {
        val processor = processor(FakeCandidate("a"))

        audience.audience = audience.audience.copy(onboarded = false)
        assertNull(processor.next())
        audience.audience = audience.audience.copy(onboarded = true, userId = null)
        assertNull(processor.next())

        audience.audience = audience.audience.copy(userId = USER)
        assertEquals("a", processor.next()?.id)
    }

    @Test
    fun `an account's first two days on the phone are quiet`() = runUnitTest {
        val processor = processor(FakeCandidate("a"), settled = false)

        assertNull(processor.next(), "first look stamps the start")
        assertEquals(T0.toEpochMilliseconds(), ledger.account(USER).firstSeenAtMs)

        clock.advance(47.hours)
        sessions.roll()
        assertNull(processor.next())

        clock.advance(1.hours)
        sessions.roll()
        assertEquals("a", processor.next()?.id)
    }

    @Test
    fun `each account keeps its own counts`() = runUnitTest {
        val processor = processor(FakeCandidate("a"))
        processor.next()

        audience.audience = audience.audience.copy(userId = "user-2")
        ledger.accounts["user-2"] = AccountLedger(firstSeenAtMs = (T0 - 30.days).toEpochMilliseconds())
        sessions.roll()
        assertEquals("a", processor.next()?.id)
    }

    @Test
    fun `numbers have been hard means opted-in prompts only three days apart`() = runUnitTest {
        audience.audience = audience.audience.copy(numbersHard = true)
        val gentle1 = FakeCandidate("gentle1", priority = 200, offerWhenNumbersHard = true)
        val gentle2 = FakeCandidate("gentle2", priority = 100, offerWhenNumbersHard = true)
        val processor = processor(FakeCandidate("numbersy", priority = 300), gentle1, gentle2)

        assertEquals("gentle1", processor.next()?.id)
        nextDay()
        assertNull(processor.next(), "a day is not enough after numbers have been hard")
        clock.advance(2.days)
        sessions.roll()
        assertEquals("gentle2", processor.next()?.id)
    }

    @Test
    fun `a moment skips the day between prompts and the quiet start`() = runUnitTest {
        val after = FakeCandidate("after_report", priority = 400, onTodayOpen = false, events = setOf(PromptEvent.ReportSent))
        val processor = processor(FakeCandidate("ordinary"), after, settled = false)
        assertNull(processor.next(), "quiet start")

        processor.emit(PromptEvent.ReportSent)
        assertEquals("after_report", processor.next()?.id)
        assertEquals("shown:after_report:dialog:report_sent", telemetry.events.single())
    }

    @Test
    fun `a moment skips the gap but not the session or the prompt's own cap`() = runUnitTest {
        val after = FakeCandidate("after_report", priority = 400, onTodayOpen = false, events = setOf(PromptEvent.ReportSent))
        val processor = processor(FakeCandidate("ordinary"), after)
        assertEquals("ordinary", processor.next()?.id)

        processor.emit(PromptEvent.ReportSent)
        assertNull(processor.next(), "a prompt already showed this session")

        sessions.roll()
        clock.advance(1.hours)
        processor.emit(PromptEvent.ReportSent)
        assertEquals("after_report", processor.next()?.id, "an hour after the last prompt")

        sessions.roll()
        processor.emit(PromptEvent.ReportSent)
        assertNull(processor.next(), "shown once is its cap, moments included")
    }

    @Test
    fun `a moment is used up and goes stale`() = runUnitTest {
        val after = FakeCandidate("after_report", onTodayOpen = false, events = setOf(PromptEvent.ReportSent))
        val processor = processor(after)

        processor.emit(PromptEvent.ReportSent)
        clock.advance(11.minutes)
        assertNull(processor.next(), "Today settled too long after the report")

        processor.emit(PromptEvent.DayClosed)
        assertNull(processor.next(), "a moment it did not ask for")
        assertNull(processor.next(), "and nothing left over for the next look")
    }

    @Test
    fun `Show next skips every rule but onboarding`() = runUnitTest {
        val processor = processor(FakeCandidate("ordinary"), FakeCandidate("capped", eligible = false))
        processor.next()

        processor.showNext("capped")
        assertEquals("capped", processor.next()?.id)
        assertEquals("shown:capped:dialog:qa", telemetry.events.last())
    }

    @Test
    fun `a screen prompt names its own destination and is the session's one prompt`() = runUnitTest {
        val route = FakeRoute()
        val processor = processor(
            FakeCandidate("celebration", priority = 500, presentation = PromptPresentation.Screen, screenRoute = route),
            FakeCandidate("ordinary"),
        )

        val showing = processor.next()
        assertEquals("celebration", showing?.id)
        assertEquals(PromptPresentation.Screen, showing?.presentation)
        assertEquals(route, processor.screen("celebration"))
        assertNull(processor.card.value, "a screen is not drawn in the list")
        assertNull(processor.next(), "nothing stacks on it in the same session")
        assertEquals("shown:celebration:screen:today", telemetry.events.first())
    }

    @Test
    fun `a card is up until it is answered or the session ends`() = runUnitTest {
        val processor = processor(FakeCandidate("card", presentation = PromptPresentation.Card, frequency = PromptFrequency(maxShows = 2, minGap = 1.days)))

        assertEquals("card", processor.next()?.id)
        assertEquals("card", processor.card.value?.id)

        sessions.roll()
        processor.next()
        assertNull(processor.card.value, "the next session decides again")

        nextDay()
        assertEquals("card", processor.next()?.id)
        processor.dismissed("card")
        assertNull(processor.card.value)
    }

    @Test
    fun `one prompt may follow an answer to the prompt just shown`() = runUnitTest {
        val cheered = setOf(PromptEvent.CelebrationCheered)
        val review = FakeCandidate("review", priority = 200, onTodayOpen = false, events = cheered)
        val second = FakeCandidate("second", priority = 100, onTodayOpen = false, events = cheered)
        val other = FakeCandidate("other", priority = 400, onTodayOpen = false, events = setOf(PromptEvent.ReportSent))
        val celebration = FakeCandidate("celebration", priority = 500, presentation = PromptPresentation.Screen)
        val processor = processor(celebration, review, second, other)
        assertEquals("celebration", processor.next()?.id)

        processor.emit(PromptEvent.ReportSent)
        assertNull(processor.next(), "an ordinary moment still waits for the next session")

        processor.emit(PromptEvent.CelebrationCheered)
        assertEquals("review", processor.next()?.id)
        assertEquals("shown:review:dialog:celebration_cheered", telemetry.events.last())

        processor.emit(PromptEvent.CelebrationCheered)
        assertNull(processor.next(), "one follow-up a session")
        assertEquals(0, second.eligibilityChecks)
    }

    @Test
    fun `an answer is no way past the other rules`() = runUnitTest {
        val review = FakeCandidate("review", onTodayOpen = false, events = setOf(PromptEvent.CelebrationCheered))
        val processor = processor(review)
        audience.audience = audience.audience.copy(numbersHard = true)

        processor.emit(PromptEvent.CelebrationCheered)
        assertNull(processor.next())
    }

    @Test
    fun `a system prompt is launched and never answered`() = runUnitTest {
        val review = FakeCandidate("review", presentation = PromptPresentation.System, acceptsWith = PromptAccept.Done)
        val processor = processor(review)

        val showing = processor.next()
        assertEquals(PromptPresentation.System, showing?.presentation)
        processor.launch("review")

        assertEquals(1, review.accepts)
        assertEquals(listOf("shown:review:system:today"), telemetry.events, "no accepted and no dismissed")
        val record = ledger.account(USER).record("review")
        assertEquals(1, record.shows)
        assertEquals(false, record.accepted)
    }

    @Test
    fun `inspect names the rule holding each prompt back and reset clears it`() = runUnitTest {
        val processor = processor(FakeCandidate("a", priority = 300), FakeCandidate("b", eligible = false))
        processor.next()

        val before = processor.inspect().associateBy { it.id }
        assertEquals(PromptBlock.ShownEnough, before.getValue("a").blockedBy)
        assertEquals(PromptBlock.ShownThisSession, before.getValue("b").blockedBy)
        assertEquals(false, before.getValue("b").eligible)

        processor.reset()
        val after = processor.inspect().associateBy { it.id }
        assertNull(after.getValue("a").blockedBy)
        assertEquals(0, after.getValue("a").shows)
        assertEquals("a", processor.next()?.id, "a reset also ends the waits")
    }

    @Test
    fun `a reset keeps the quiet start over so testing can begin at once`() = runUnitTest {
        val processor = processor(FakeCandidate("a"), settled = false)
        assertNull(processor.next())

        processor.reset("a")
        assertEquals("a", processor.next()?.id)
    }

    private companion object {
        const val USER = "user-1"
        val T0: Instant = Instant.fromEpochMilliseconds(1_790_000_000_000L)
    }
}
