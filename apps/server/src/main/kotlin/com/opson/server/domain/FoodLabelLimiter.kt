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
 * How often one person may have a label read, and submit a food, keyed on the
 * user id from the JWT like [PhotoEstimateLimiter]. A read is a paid model
 * call with two images; a submission stores two photos and adds to someone's
 * review queue. Someone adding a cupboard's worth of packs in one sitting
 * stays inside both; a script does not.
 *
 * In memory, so it resets on deploy and each machine counts on its own, as
 * [PhotoEstimateLimiter] does.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@Inject
class FoodLabelLimiter(private val clock: Clock) {

    private val parses = Window(PARSE_BURST, DAILY_PARSES)
    private val submissions = Window(SUBMIT_BURST, DAILY_SUBMISSIONS)
    private val verifications = Window(VERIFY_BURST, DAILY_VERIFICATIONS)

    fun tryParse(userId: UserId): Boolean = parses.tryAcquire(userId, clock.now())

    /** [daily] is higher for a Builder ([BuilderRules.BUILDER_DAILY_SUBMISSIONS]); the burst limit is everyone's. */
    fun trySubmit(userId: UserId, daily: Int = DAILY_SUBMISSIONS): Boolean = submissions.tryAcquire(userId, clock.now(), daily)

    /** A Builder checking foods against their packs: a cupboard in one sitting fits, a script does not. */
    fun tryVerify(userId: UserId): Boolean = verifications.tryAcquire(userId, clock.now())

    private class Window(private val burst: Int, private val daily: Int) {
        private val calls = ConcurrentHashMap<UserId, ArrayDeque<Instant>>()

        fun tryAcquire(userId: UserId, now: Instant, dailyLimit: Int = daily): Boolean {
            val history = calls.computeIfAbsent(userId) { ArrayDeque() }
            synchronized(history) {
                while (history.isNotEmpty() && now - history.first() >= DAY) history.removeFirst()
                if (history.size >= dailyLimit) return false
                if (history.count { now - it < BURST_WINDOW } >= burst) return false
                history.addLast(now)
                return true
            }
        }
    }

    companion object {
        const val PARSE_BURST = 10
        const val DAILY_PARSES = 40
        const val SUBMIT_BURST = 5
        const val DAILY_SUBMISSIONS = 20
        const val VERIFY_BURST = 20
        const val DAILY_VERIFICATIONS = 100
        val BURST_WINDOW: Duration = 10.minutes
        private val DAY: Duration = 1.days
    }
}
