package com.opson.server.domain

import java.time.LocalDate
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * One signed-in request's claim about the app that made it: the install, and the build it runs.
 * [installId] is null for an install that had not hydrated its id yet.
 */
data class InstallSighting(
    val userId: UserId,
    val installId: String?,
    val platform: String,
    val appVersion: String?,
    val buildNumber: Int?,
)

/** The `user_installs` table (V21): which builds each account has run, and when. */
interface UserInstallRepository {
    /** First sighting of a build inserts its row; every later one moves `last_seen_at`. */
    @OptIn(ExperimentalTime::class)
    suspend fun record(sighting: InstallSighting, at: Instant)
}

/**
 * The owner's lookup of one account, for support: who it is, what it runs, what it chose in
 * Settings and how much it logs. Reads the `support` views (V21), so it can show nothing those
 * views leave out: no food, amount, weight, note or answer.
 */
interface UserLookupRepository {
    /**
     * Accounts matching [query]: an exact user id, or part of an email or display name.
     * Blank lists the most recently seen. At most [limit].
     */
    suspend fun search(query: String, limit: Int): List<UserSummary>

    suspend fun detail(userId: UserId): UserDetail?
}

@OptIn(ExperimentalTime::class)
data class UserSummary(
    val userId: UserId,
    val email: String?,
    val isGuest: Boolean,
    val displayName: String?,
    val joinedAt: Instant?,
    val lastSignInAt: Instant?,
    val lastSeenAt: Instant?,
    val platform: String?,
    val appVersion: String?,
    val buildNumber: Int?,
    val preset: String?,
    val entries: Long,
    val daysLogged: Long,
    val lastLogDay: LocalDate?,
)

@OptIn(ExperimentalTime::class)
data class UserInstall(
    val installId: String,
    val platform: String,
    val appVersion: String,
    val buildNumber: Int,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
)

@OptIn(ExperimentalTime::class)
data class UserDetail(
    val summary: UserSummary,
    val installs: List<UserInstall>,
    /** `support.user_settings`, column by column, in the view's order. Empty before the first sync. */
    val settings: List<Pair<String, String?>>,
    /** Entries per logging method, all time. */
    val methods: Map<String, Long>,
    val entriesLast7Days: Long,
    val entriesLast30Days: Long,
    val daysClosed: Long,
    val lastSyncedChangeAt: Instant?,
)
