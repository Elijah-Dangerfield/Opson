package com.opson.server.plugins

import com.opson.server.domain.BanReason
import com.opson.server.domain.BanStatus
import com.opson.server.domain.ModerationRepository
import com.opson.server.domain.UserId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class BanGateTest {

    private val user = UserId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val clock = object : Clock {
        var now = Instant.parse("2026-10-26T12:00:00Z")
        override fun now(): Instant = now
    }

    private class FakeModeration(var status: BanStatus? = null, var failure: Throwable? = null) : ModerationRepository {
        var lookups = 0
        override suspend fun banStatusFor(userId: UserId): BanStatus? {
            lookups++
            failure?.let { throw it }
            return status
        }
    }

    @Test
    fun standingIsReadOncePerTtl() = runTest {
        val moderation = FakeModeration()
        val gate = BanGate(moderation, appealUrl = null, clock = clock)

        repeat(5) { assertNull(gate.banStatusFor(user)) }
        assertEquals(1, moderation.lookups)

        moderation.status = BanStatus(BanReason.Banned, until = null)
        clock.now += BanGate.CACHE_TTL
        assertEquals(BanReason.Banned, gate.banStatusFor(user)?.reason)
        assertEquals(2, moderation.lookups)
    }

    @Test
    fun aCachedBanLapsesWhenItsUntilPasses() = runTest {
        val moderation = FakeModeration(BanStatus(BanReason.Banned, until = clock.now + 5.seconds))
        val gate = BanGate(moderation, appealUrl = null, clock = clock)
        gate.banStatusFor(user)

        clock.now += 6.seconds

        assertNull(gate.banStatusFor(user))
        assertEquals(1, moderation.lookups)
    }

    @Test
    fun aFailedLookupAllowsThroughAndIsNotCached() = runTest {
        val moderation = FakeModeration(failure = IllegalStateException("db hiccup"))
        val gate = BanGate(moderation, appealUrl = null, clock = clock)

        assertNull(gate.banStatusFor(user))
        moderation.failure = null
        moderation.status = BanStatus(BanReason.Banned, until = null)

        assertEquals(BanReason.Banned, gate.banStatusFor(user)?.reason)
    }

    @Test
    fun cancellationIsNotMistakenForAFailedLookup() = runTest {
        val gate = BanGate(FakeModeration(failure = CancellationException("caller went away")), appealUrl = null, clock = clock)

        assertFailsWith<CancellationException> { gate.banStatusFor(user) }
    }
}
