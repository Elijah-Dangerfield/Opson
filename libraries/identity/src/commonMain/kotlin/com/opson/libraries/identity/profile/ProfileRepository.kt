package com.opson.libraries.identity.profile

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Owns the user's profile — display name etc. Backed by `/v1/me` on our
 * server, mirrored in a local cache.
 *
 * Waits for [com.opson.libraries.identity.auth.AuthRepository]
 * to resolve before fetching: an authenticated session means
 * [Profile.Authenticated] from `/v1/me`; an unauthenticated state means
 * [Profile.Fallback] keyed on a stable client-generated UUID.
 *
 * **No in-flight sentinel.** [current] suspends until the answer is
 * real; [observe] emits only resolved profiles.
 *
 * **Cache as fallback, not first-frame.** The cache is consulted only
 * when `/v1/me` fails — the happy path is server-authoritative
 * end-to-end. Returning users on a healthy network see splash → home
 * with fresh data; returning users on an offline network fall back to
 * the cached profile via the `onFailure` branch.
 */
interface ProfileRepository {

    /**
     * Suspends until the profile resolves. Idempotent — concurrent
     * callers share one in-flight resolve.
     */
    suspend fun current(): Profile

    /**
     * Reactive. First emission after the initial resolve completes;
     * subsequent emissions on profile updates, auth changes, etc.
     * Never emits an "I have no value yet" sentinel.
     */
    fun observe(): Flow<Profile>

    /**
     * Patch the profile on the server. `null` = leave the field alone.
     */
    suspend fun update(
        displayName: String? = null,
    ): UpdateProfileOutcome

    /**
     * Whether the current session's account was **just created** on the server
     * (a brand-new account's first contact) — the authoritative discriminator
     * for SIGN-UP vs SIGN-IN, read once right after an identity auth. Sourced
     * from the server's `/v1/me` `isNewAccount` flag, latched inside the repo so
     * it survives the profile hydrate racing the caller (the flag is one-shot
     * server-side). Returns false when unauthenticated or on error — treat "not
     * sure" as returning, so a real returning user is never trapped in
     * onboarding. Not consuming: the latch resets on sign-out or an account
     * switch. Default false for fakes.
     */
    suspend fun resolveIsNewAccount(): Boolean = false

    /**
     * Reactive form of [resolveIsNewAccount] for observers that can't call a
     * suspend consume in a flow `combine`. Emits true once a `/v1/me` hydrate
     * reports a brand-new account and stays true for the session (reset on
     * sign-out / account switch) — it is NOT consumed by reads, so onboarding's
     * [resolveIsNewAccount] and a welcome surface can both observe the same
     * latch. Default: a constant-false flow for fakes.
     */
    fun observeAccountJustCreated(): Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)

    /**
     * Attempt to flush a queued offline edit (see [UpdateProfileOutcome.Queued])
     * now, if a session is available. Best-effort + idempotent — a no-op when
     * nothing's queued or still offline. Driven by the warm-foreground /
     * connectivity-regained triggers so a stuck edit isn't stranded when auth
     * never re-emits (e.g. the session stayed valid but a PATCH failed). Default
     * no-op so test fakes needn't implement it.
     */
    suspend fun flushPendingEdits() {}

    /**
     * Rejections surfaced when a **queued** offline edit is flushed and the
     * server refuses it (the display name was taken while you were offline, or
     * is invalid). The optimistic value is reverted; a global surface shows the
     * user a "couldn't save your name" message so the silent revert isn't
     * confusing. Default empty so fakes needn't implement it.
     */
    fun observeEditRejections(): Flow<ProfileEditRejection> = kotlinx.coroutines.flow.emptyFlow()
}

/** Why a flushed offline edit was refused by the server. Drives a user message. */
enum class ProfileEditRejection {
    DisplayNameTaken,
    InvalidDisplayName,
}

/**
 * Resolved profile. Sealed because the "real Supabase-backed profile"
 * vs "client-only fallback" distinction is meaningful to most callers —
 * server writes hard-gate on [Authenticated], read-only surfaces accept
 * either.
 */
sealed interface Profile {
    /** Stable id usable as a foreign key for client-side state. */
    val id: String

    /**
     * Profile backed by a real Supabase user + a `/v1/me` row on our
     * server. `id` is the Supabase `auth.users.id`.
     */
    data class Authenticated(
        override val id: String,
        val displayName: String,
        val email: String?,
        val isAnonymous: Boolean,
        /**
         * Server-issued wall-clock when the profile row was first
         * created. Stable across reloads + survives device-switch via
         * a claimed account. Useful for "member since" rendering — UI
         * does `Clock.System.now() - profile.createdAt` and gets a
         * [kotlin.time.Duration] back directly.
         */
        val createdAt: Instant,
    ) : Profile

    /**
     * Client-only fallback used when auth couldn't resolve AND there's
     * no cached server profile. `id` is a UUID generated client-side and
     * persisted across launches so any local-only state has a stable key.
     *
     * May carry a **locally-chosen** name, held on this device while there is
     * no session (an older build's offline onboarding left some). Surfaced so
     * the app shows the user's choice instead of a generic placeholder. It
     * never reaches the server: the account made at sign-in has a name of its
     * own. Null = nothing chosen.
     *
     * `Fallback` still means "no confirmed server session," so callers that
     * hard-gate on a real account (server writes) keep checking
     * `is Authenticated` — they must not treat a populated Fallback as real.
     */
    data class Fallback(
        override val id: String,
        val displayName: String? = null,
    ) : Profile
}

/**
 * Display name from either profile shape — the server-confirmed one when
 * [Profile.Authenticated], the locally-chosen one when [Profile.Fallback].
 * Null when no name is known yet (render "You" / a placeholder).
 *
 * Use this for *rendering* identity. Anything that gates on a real session
 * (server writes) must still match on `is Profile.Authenticated`.
 */
val Profile.displayNameOrNull: String?
    get() = when (this) {
        is Profile.Authenticated -> displayName
        is Profile.Fallback -> displayName
    }

sealed interface UpdateProfileOutcome {
    data class Success(val profile: Profile.Authenticated) : UpdateProfileOutcome

    /**
     * The edit was applied **locally** — the offline / session-less case. The
     * chosen name shows immediately. For a cached account it is sent once a
     * session is back; with no account (the Fallback) it stays on this device.
     * The caller treats this like a success: the user's change "stuck".
     */
    data object Queued : UpdateProfileOutcome
    data object DisplayNameTaken : UpdateProfileOutcome
    data object InvalidDisplayName : UpdateProfileOutcome
    data object NotSignedIn : UpdateProfileOutcome
    data class NetworkError(val cause: Throwable) : UpdateProfileOutcome
    data class Unknown(val cause: Throwable) : UpdateProfileOutcome
}
