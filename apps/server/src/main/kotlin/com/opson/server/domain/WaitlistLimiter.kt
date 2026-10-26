package com.opson.server.domain

import com.opson.server.di.ServerScope
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * How often one IP may post the waitlist form: [BURST] in any [BURST_WINDOW]
 * and [DAILY] in any rolling day (docs/plans/website.md). Every attempt
 * counts, a duplicate or a honeypot hit included, so a script learns nothing
 * by retrying.
 *
 * Its own limiter rather than a named Ktor bucket because the route has two
 * windows and answers `{"error":"rate_limited"}`, the body the site expects.
 * In memory like [PhotoEstimateLimiter]: it resets on deploy and each
 * machine counts on its own, which is fine for a form.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@Inject
class WaitlistLimiter(private val clock: Clock) {

    private val calls = ConcurrentHashMap<String, ArrayDeque<Instant>>()
    private val sinceSweep = AtomicInteger()

    /** Counts an attempt from [ip] and returns true, or returns false (and counts nothing) when over a limit. */
    fun tryAcquire(ip: String): Boolean {
        val now = clock.now()
        if (sinceSweep.incrementAndGet() >= SWEEP_EVERY) sweep(now)
        val history = calls.computeIfAbsent(ip) { ArrayDeque() }
        synchronized(history) {
            while (history.isNotEmpty() && now - history.first() >= DAY) history.removeFirst()
            if (history.size >= DAILY) return false
            if (history.count { now - it < BURST_WINDOW } >= BURST) return false
            history.addLast(now)
            return true
        }
    }

    /** Forgets IPs with nothing in the last day, so the map does not grow with every visitor. */
    private fun sweep(now: Instant) {
        sinceSweep.set(0)
        calls.entries.removeIf { (_, history) ->
            synchronized(history) { history.isEmpty() || now - history.last() >= DAY }
        }
    }

    companion object {
        const val BURST = 5
        val BURST_WINDOW: Duration = 10.minutes
        const val DAILY = 50
        private val DAY: Duration = 1.days
        private const val SWEEP_EVERY = 1_000
    }
}
