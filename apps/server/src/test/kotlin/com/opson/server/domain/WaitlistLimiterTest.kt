package com.opson.server.domain

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
class WaitlistLimiterTest {

    private class MovableClock(var now: Instant = Instant.parse("2026-10-26T08:00:00Z")) : Clock {
        override fun now(): Instant = now

        fun advance(by: Duration) {
            now += by
        }
    }

    private val clock = MovableClock()
    private val limiter = WaitlistLimiter(clock)

    @Test
    fun fiveInTenMinutesPerIp() {
        repeat(WaitlistLimiter.BURST) { assertTrue(limiter.tryAcquire("1.1.1.1")) }
        assertFalse(limiter.tryAcquire("1.1.1.1"))
        assertTrue(limiter.tryAcquire("2.2.2.2"))
        clock.advance(WaitlistLimiter.BURST_WINDOW)
        assertTrue(limiter.tryAcquire("1.1.1.1"))
    }

    @Test
    fun fiftyInADay() {
        // Spaced out so the burst window never trips: one every three minutes.
        repeat(WaitlistLimiter.DAILY) {
            assertTrue(limiter.tryAcquire("1.1.1.1"), "attempt $it")
            clock.advance(3.minutes)
        }
        assertFalse(limiter.tryAcquire("1.1.1.1"))
        clock.advance(24.hours)
        assertTrue(limiter.tryAcquire("1.1.1.1"))
    }
}
