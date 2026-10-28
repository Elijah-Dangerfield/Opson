package com.opson.server.domain

import com.opson.server.data.LabelParseCache
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class FoodLabelLimiterTest {

    private class MovableClock(var now: Instant = Instant.parse("2026-10-27T08:00:00Z")) : Clock {
        override fun now(): Instant = now
    }

    private val clock = MovableClock()
    private val alice = UserId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val bob = UserId(UUID.fromString("22222222-2222-2222-2222-222222222222"))

    @Test
    fun readsAndSubmissionsHaveTheirOwnAllowances() {
        val limiter = FoodLabelLimiter(clock)
        repeat(FoodLabelLimiter.PARSE_BURST) { assertTrue(limiter.tryParse(alice)) }
        assertFalse(limiter.tryParse(alice))
        assertTrue(limiter.trySubmit(alice), "a submission is not a read")
        assertTrue(limiter.tryParse(bob))
        clock.now += FoodLabelLimiter.BURST_WINDOW
        assertTrue(limiter.tryParse(alice))
    }

    @Test
    fun theDailyCapsHold() {
        val limiter = FoodLabelLimiter(clock)
        repeat(FoodLabelLimiter.DAILY_SUBMISSIONS) {
            assertTrue(limiter.trySubmit(alice))
            clock.now += 30.minutes
        }
        assertFalse(limiter.trySubmit(alice))
    }

    @Test
    fun aKeptReadingIsOnlyItsReadersAndOnlyForAnHour() {
        val cache = LabelParseCache(clock)
        val reading = LabelReading(LabelFacts(name = "Oats"), emptyMap(), readable = true)
        val id = cache.put(alice, reading, "claude-fable-5-1")

        assertEquals("claude-fable-5-1", assertNotNull(cache.get(id, alice)).model)
        assertNull(cache.get(id, bob))
        clock.now += LabelParseCache.TTL + 1.minutes
        assertNull(cache.get(id, alice))
    }
}
