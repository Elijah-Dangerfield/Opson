package com.opson.server.domain

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Read-side view of a user's moderation standing, for the ban gate.
 *
 * Two sources, the stricter wins: Supabase's own `auth.users.banned_until` (a ban set from the
 * dashboard or the Admin API, which has no reason, so it reads as [BanReason.Banned]), and the
 * app's `moderation_actions` (V29), where a `full`-scope ban or suspension carries its kind and
 * end. A `contributions` restriction is not a ban: the app keeps working and only the
 * contribution routes refuse ([ContributionGate]).
 */
interface ModerationRepository {
    /**
     * The caller's current ban standing, or null if they are in good standing
     * (no `banned_until`, or a `banned_until` already in the past).
     */
    suspend fun banStatusFor(userId: UserId): BanStatus?
}

/**
 * Why a user is blocked + when (if ever) the block lifts. Mirrors the locked
 * wire contract the client reads off a `403` (see
 * [com.opson.server.plugins.AccessDeniedResponse]).
 */
@OptIn(ExperimentalTime::class)
data class BanStatus(
    val reason: BanReason,
    /** When the block lifts, or null for an indefinite block. */
    val until: Instant?,
)

enum class BanReason(val wire: String) {
    Banned("banned"),
    Suspended("suspended"),
}
