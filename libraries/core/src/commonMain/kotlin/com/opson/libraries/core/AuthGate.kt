package com.opson.libraries.core

/**
 * What an authed network call needs of the user's identity before it can
 * proceed. Each call declares it (`NetworkCall`'s `requirement`, [Account] by
 * default) and [AuthGate] enforces it centrally, so no screen or call carries
 * its own guard code. Routes carry no requirement: a person without an account
 * is sent to sign in as a whole (`AppViewModel.signInRequired`).
 */
enum class AuthRequirement {
    /** Anyone. */
    None,

    /** A server session. Blocks the unauthenticated state: signed out, an
     *  expired session, or a stranded one still being revived. Use for sync,
     *  search, photo estimates, anything that hits the user's server-side
     *  account. */
    Account,

    // TODO(review): no call passes this, since accounts are required. Delete it with
    // NeedClaimedAccount and AuthGateImpl's branch (docs/review/findings/core-libraries.md).
    /** A non-anonymous account. Blocks a legacy guest session too. */
    ClaimedAccount,
}

/**
 * Why auth-readiness is blocked. The one vocabulary shared by typed call
 * failures ([AuthUnready]) and anything else that has to explain "why can't
 * this user do this" honestly.
 */
enum class AuthReason {
    /** A stranded session is being revived or sent to sign in (`SessionHealer`).
     *  Self-resolves; ask the user to wait a moment. */
    FinishingSetup,

    /** No account at all: offer sign-in. */
    NeedAccount,

    /** A legacy guest session where a non-anonymous account is needed. */
    NeedClaimedAccount,

    /**
     * The session couldn't be confirmed because the device is offline, not
     * because the user has no account. Tell them they're offline instead of the
     * misleading "account needed", which an onboarded user reads as "my
     * progress is gone."
     */
    Offline,

    /** The auth server rejected the session; the user must sign in again. */
    SessionExpired,
}

/** The answer to "may this proceed?" for a given [AuthRequirement]. */
sealed interface AuthVerdict {
    data object Ready : AuthVerdict
    data class Blocked(val reason: AuthReason) : AuthVerdict
}

/**
 * The single authority on auth-readiness. One implementation (contributed from
 * identity) caches auth, onboarding and connectivity state, and the authed call
 * boundary consults it.
 *
 * [verdict] is a synchronous peek over cached state and fails closed while
 * auth is still resolving. [awaitVerdict] first suspends until auth has
 * resolved; use it anywhere that can run at launch (background syncs), where
 * fail-closed would wrongly block.
 */
interface AuthGate {
    fun verdict(requirement: AuthRequirement): AuthVerdict
    suspend fun awaitVerdict(requirement: AuthRequirement): AuthVerdict
}

/**
 * Typed failure for an authed call that was short-circuited before firing (or
 * discovered mid-flight to be session-dead). `getOrNull()` ignores it like any
 * other failure, and telemetry drops it as [ExpectedControlFlow].
 */
class AuthUnready(val reason: AuthReason, cause: Throwable? = null) :
    Exception("auth unready: $reason", cause), ExpectedControlFlow
