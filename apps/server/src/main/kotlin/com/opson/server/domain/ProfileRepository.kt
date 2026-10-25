package com.opson.server.domain

/**
 * Persistence for [Profile]. The interface lives in `domain/`, the Postgres impl
 * in `data/` (`PostgresProfileRepository`) — routes depend on this, never the
 * impl. Swapping the backing store is a one-line DI change.
 *
 * Implementations MUST run each method in a single transaction.
 */
interface ProfileRepository {

    /**
     * Returns the caller's profile, creating one with a default display name on
     * first contact. Idempotent: calling it twice returns the same row.
     */
    suspend fun findOrCreate(userId: UserId): Profile

    /**
     * Like [findOrCreate], but also reports whether THIS call created the row —
     * i.e. this is a brand-new account's first contact. It's the authoritative
     * "net-new account" signal: deterministic and decided by whoever wins the
     * insert. `GET /v1/me` surfaces it as `isNewAccount` for the client's
     * auth-outcome classifier (SignedUp vs SignedIn). Default delegates with
     * `created = false` for fakes that don't distinguish; the Postgres impl
     * reports the real flag.
     */
    suspend fun findOrCreateResult(userId: UserId): FindOrCreateProfileResult =
        FindOrCreateProfileResult(findOrCreate(userId), created = false)

    /**
     * Updates the display name. Returns an outcome the route maps to an HTTP
     * status (200 / 409 / 404) — the repository never speaks HTTP.
     */
    suspend fun updateDisplayName(userId: UserId, displayName: String): UpdateProfileOutcome

    /**
     * Remove the profile row for [userId]. Idempotent — succeeds whether a row
     * existed or not. The caller (`DELETE /v1/me`) pairs this with a Supabase
     * Admin API call that deletes the underlying `auth.users` row; this method
     * only owns OUR table.
     */
    suspend fun delete(userId: UserId)
}

/** Result of [ProfileRepository.findOrCreateResult]: the profile plus whether
 *  this call created it. */
data class FindOrCreateProfileResult(val profile: Profile, val created: Boolean)

/**
 * Result of a write, pattern-matched by the route into an HTTP status. Modeling
 * outcomes as a sealed type (instead of throwing) keeps the route's `when`
 * exhaustive and the failure modes explicit.
 */
sealed interface UpdateProfileOutcome {
    data class Success(val profile: Profile) : UpdateProfileOutcome
    data object DisplayNameTaken : UpdateProfileOutcome
    data object NotFound : UpdateProfileOutcome
}
