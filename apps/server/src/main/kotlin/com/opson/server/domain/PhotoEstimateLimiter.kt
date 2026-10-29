package com.opson.server.domain

import com.opson.server.di.ServerScope
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * How many photo estimates one person may ask for: [BURST] in any
 * [BURST_WINDOW], and [DAILY] in any rolling day. Every estimate is a paid
 * model call, so this is keyed on the user id from the JWT, not the IP the
 * global limiter uses (a household on one Wi-Fi shares an IP; one person
 * scripting the route does not need several).
 *
 * In memory, so it resets on deploy and each server machine counts on its
 * own. Opson runs one machine; a second would double the effective limit,
 * which is still a bounded cost.
 *
 * Whether someone may use photo estimates at all is the `photo_estimate`
 * entitlement, checked by the route before this ([EntitlementService]). This
 * is the fair-use cap on top, Pro included; a small free allowance, when
 * there is one, would be a second limit here keyed on that entitlement.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@Inject
class PhotoEstimateLimiter(private val clock: Clock) {

    private val calls = ConcurrentHashMap<UserId, ArrayDeque<Instant>>()

    /** Counts a call for [userId] and returns true, or returns false (and counts nothing) when over a limit. */
    fun tryAcquire(userId: UserId): Boolean {
        val now = clock.now()
        val history = calls.computeIfAbsent(userId) { ArrayDeque() }
        synchronized(history) {
            while (history.isNotEmpty() && now - history.first() >= DAY) history.removeFirst()
            if (history.size >= DAILY) return false
            if (history.count { now - it < BURST_WINDOW } >= BURST) return false
            history.addLast(now)
            return true
        }
    }

    companion object {
        const val BURST = 10
        val BURST_WINDOW: Duration = 10.minutes
        const val DAILY = 40
        private val DAY: Duration = 1.days
    }
}
