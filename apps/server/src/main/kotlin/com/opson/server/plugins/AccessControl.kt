package com.opson.server.plugins

import com.opson.server.domain.BanStatus
import com.opson.server.domain.ModerationRepository
import com.opson.server.domain.UserId
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Wire body returned with a `403` when a banned user hits any authenticated
 * route. The contract is **locked machine-readable data only** — no
 * user-facing copy. The client keys its localized blocking screen off
 * [reason] (see AGENTS.md). [until] is an ISO-8601 timestamp
 * when the block lifts (null = indefinite); [appealUrl] points at a support
 * page (null when the server has none configured).
 *
 * camelCase fields match the rest of the server's JSON contract (e.g.
 * `MeResponse.isAnonymous`); the client deserializes the whole envelope.
 */
@Serializable
data class AccessDeniedResponse(
    val reason: String,
    val until: String? = null,
    val appealUrl: String? = null,
)

/**
 * Bundles the moderation read + the appeal URL the auth layer needs to gate
 * banned callers. Passed into [installAuthentication]; when present, a banned
 * user's token validates to "no principal" and the auth challenge renders a
 * `403` [AccessDeniedResponse] instead of the default `401`.
 *
 * Folding the gate into the auth flow (rather than a separate post-auth
 * plugin) is deliberate: the JWT provider's validate→challenge path is the
 * one place in Ktor that reliably short-circuits the routing pipeline, so a
 * banned caller never reaches a route handler.
 *
 * Each user's standing is read at most once per [CACHE_TTL]. Without that,
 * every authenticated request cost a database transaction before its route
 * started, twice the pool use when Postgres is slow. A ban therefore takes
 * up to [CACHE_TTL] to bite.
 */
class BanGate(
    val moderation: ModerationRepository,
    val appealUrl: String?,
    private val clock: Clock = Clock.System,
) {
    private class Checked(val status: BanStatus?, val at: Instant)

    private val recent = ConcurrentHashMap<UserId, Checked>()

    /**
     * [userId]'s ban, or null in good standing. A failed lookup is logged and
     * counts as good standing (fail open: a transient read must not lock
     * everyone out, and the token expires on its own); it is not cached.
     */
    suspend fun banStatusFor(userId: UserId): BanStatus? {
        val now = clock.now()
        recent[userId]?.takeIf { now - it.at < CACHE_TTL }?.let { return it.status.stillActive(now) }
        val status = try {
            moderation.banStatusFor(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoggerFactory.getLogger("BanGate").warn("Ban lookup failed for {}, allowing through", userId, e)
            return null
        }
        if (recent.size >= MAX_CACHED) {
            recent.entries.removeIf { now - it.value.at >= CACHE_TTL }
            if (recent.size >= MAX_CACHED) recent.clear()
        }
        recent[userId] = Checked(status, now)
        return status
    }

    private fun BanStatus?.stillActive(now: Instant): BanStatus? = this?.takeIf { it.until == null || it.until > now }

    companion object {
        val CACHE_TTL: Duration = 30.seconds
        private const val MAX_CACHED = 10_000
    }
}
