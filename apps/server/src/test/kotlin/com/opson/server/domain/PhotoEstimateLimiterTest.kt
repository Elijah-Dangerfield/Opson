package com.opson.server.domain

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class PhotoEstimateLimiterTest {

    private class MovableClock(var now: Instant = Instant.parse("2026-10-26T08:00:00Z")) : Clock {
        override fun now(): Instant = now

        fun advance(by: Duration) {
            now += by
        }
    }

    private val clock = MovableClock()
    private val limiter = PhotoEstimateLimiter(clock)
    private val alice = UserId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val bob = UserId(UUID.fromString("22222222-2222-2222-2222-222222222222"))

    @Test
    fun aBurstIsCappedAndEachPersonCountsAlone() {
        repeat(PhotoEstimateLimiter.BURST) { assertTrue(limiter.tryAcquire(alice)) }
        assertFalse(limiter.tryAcquire(alice))
        assertTrue(limiter.tryAcquire(bob))
    }

    @Test
    fun theBurstWindowSlides() {
        repeat(PhotoEstimateLimiter.BURST) { limiter.tryAcquire(alice) }
        clock.advance(PhotoEstimateLimiter.BURST_WINDOW - 1.minutes)
        assertFalse(limiter.tryAcquire(alice))
        clock.advance(1.minutes)
        assertTrue(limiter.tryAcquire(alice))
    }

    @Test
    fun aRefusedCallDoesNotCount() {
        repeat(PhotoEstimateLimiter.BURST) { limiter.tryAcquire(alice) }
        repeat(5) { assertFalse(limiter.tryAcquire(alice)) }
        clock.advance(PhotoEstimateLimiter.BURST_WINDOW)
        repeat(PhotoEstimateLimiter.BURST) { assertTrue(limiter.tryAcquire(alice)) }
    }

    @Test
    fun theDailyCapHoldsUntilADayHasPassed() {
        repeat(PhotoEstimateLimiter.DAILY) {
            assertTrue(limiter.tryAcquire(alice))
            clock.advance(15.minutes)
        }
        assertFalse(limiter.tryAcquire(alice))
        // The first call was ten hours ago; it ages out once a full day has passed.
        clock.advance(14.hours)
        assertTrue(limiter.tryAcquire(alice))
    }
}
