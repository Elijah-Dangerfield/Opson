package com.opson.libraries.identity.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Owns the device's Supabase user lifecycle + access token.
 *
 * Sessions are never minted implicitly. Construction only hydrates whatever
 * session supabase-kt persisted; new sessions come only from signing in with
 * Apple, Google or email. Anonymous (guest) sessions still exist for people who
 * started before accounts were required, and the sign-in step links an identity
 * onto them ([linkAppleIdentity], [linkOAuthIdentity]); nothing creates new
 * ones. [retry] re-resolves a dormant session but never mints. After init resolves, [current] / [observe] return either
 * [AuthState.Authenticated] or [AuthState.Unauthenticated] (whose
 * [AuthState.Unauthenticated.reason] says why: none/offline, session
 * expired, signed out).
 *
 * **No in-flight sentinel state.** [current] suspends until the answer
 * is real; [observe] emits only resolved values. UI that wants to render
 * a spinner should do so while waiting on its first emission.
 *
 * Errors from auth operations are returned as sealed outcome types
 * rather than thrown, because the UI wants to render specific messages
 * for "invalid credentials" vs "network down" vs "email already
 * registered." Try/catch at every call site was the worse alternative.
 *
 * **Access tokens live elsewhere.** The networking layer reads tokens via
 * `AuthTokenProvider` (in `:libraries:networking`), not through this
 * interface — keeping the network → auth dependency narrow at the type
 * level.
 */
interface AuthRepository {

    /**
     * Suspends until auth resolves to a definitive state. Idempotent —
     * concurrent callers share one in-flight resolve.
     */
    suspend fun current(): AuthState

    /**
     * Reactive stream of auth state changes. First emission lands after
     * the initial resolve completes. Subsequent emissions on sign-in,
     * sign-out, account delete, etc.
     */
    fun observe(): Flow<AuthState>

    /**
     * Re-resolve the persisted session after a previous failure. Used by the
     * connectivity observer (offline → online flip) and explicit retry
     * actions. No-op if already Authenticated. Does **not** create an account —
     * a missing session stays [AuthState.Unauthenticated].
     */
    suspend fun retry(): AuthState

    /**
     * Suspends until the session resolves to [AuthState.Authenticated], then
     * returns it. For callers that *must* have a real session before proceeding
     * (so they never fire a tokenless request that 401s). Never resolves while
     * the user is session-less — pair with a timeout if the caller can't block
     * indefinitely.
     */
    suspend fun awaitAuthenticated(): AuthState.Authenticated =
        observe().first { it is AuthState.Authenticated } as AuthState.Authenticated

    /**
     * Run [block] only if the session is currently [AuthState.Authenticated];
     * otherwise no-op and return null. The guard for fire-and-forget authed
     * syncs: a session-less resolve **skips** the call (an intentional, logged
     * no-op) instead of 401ing. Re-fires when auth later arrives via the
     * level-keyed sync triggers (`SyncTriggers.activeAccount`).
     */
    suspend fun <T> ifAuthenticated(block: suspend (AuthState.Authenticated) -> T): T? =
        (current() as? AuthState.Authenticated)?.let { block(it) }

    /** Email/password sign-in. Server-issued JWT replaces any current session. */
    suspend fun signInWithEmail(email: String, password: String): SignInOutcome

    /**
     * Email/password sign-up. Supabase sends a verification email; the
     * session is in a "pending email confirmation" state until the user
     * clicks the link AND we call [refreshSession]. Until then,
     * `/v1/me`-protected calls will succeed because the JWT itself is
     * valid — the verification gate is product-level.
     */
    suspend fun signUpWithEmail(email: String, password: String): SignUpOutcome

    /**
     * Pulls the current Supabase session from the server. Used by the
     * "I clicked the verification link" button on the email-verification
     * screen to learn whether the user's email is now confirmed.
     */
    suspend fun refreshSession(): RefreshOutcome

    /** Resend the verification email for the current pending sign-up. */
    suspend fun resendVerificationEmail(email: String): ResendOutcome

    /**
     * Trigger Supabase's password-reset email. The user clicks the link in
     * their inbox, which lands them on the recovery deep-link target — that
     * screen lives in a follow-up once the redirect URL is configured.
     *
     * Always returns [SendResetOutcome.Sent] for unknown emails too: Supabase
     * doesn't differentiate at the API layer so the UI shouldn't either
     * (account-enumeration mitigation).
     */
    suspend fun sendPasswordResetEmail(email: String): SendResetOutcome

    /**
     * Declare the device's persisted session unrecoverable: no token survived
     * (storage lost it) and [retry] couldn't revive one, but local state says a
     * real account exists. Tears down like a server rejection — emits
     * [AuthState.Unauthenticated] with [AuthState.Unauthenticated.Reason.SessionExpired]
     * so the app routes to the recovery screen and from there to sign-in
     * (AUTH-19). [wasAnonymous] picks the copy: a claimed account signs in
     * again; a guest signs in too, losing this phone's guest log.
     *
     * Default no-op so test fakes that don't drive recovery needn't implement
     * it; the production impl overrides.
     */
    suspend fun markSessionUnrecoverable(wasAnonymous: Boolean) {}

    /**
     * Tear down the current Supabase session. The person signs in again to come back.
     * Called after a session expiry it only clears the dead session: the expired
     * account's data stays until someone signs in.
     */
    suspend fun signOut()

    /**
     * Permanently delete the current account. Calls the server's
     * `DELETE /v1/me` (which in turn invokes Supabase's Admin API to
     * remove `auth.users` plus drops the local profile row) and then
     * tears down the local Supabase session.
     */
    suspend fun deleteAccount(): DeleteAccountOutcome

    /**
     * Attach an Apple/Google identity to the current (typically anonymous)
     * Supabase user. Preserves the diary and settings.
     */
    suspend fun linkOAuthIdentity(provider: OAuthProvider): LinkIdentityOutcome

    /**
     * Switch sessions to an existing OAuth account. The current local
     * session is replaced; any guest progress tied to the previous
     * session is orphaned by design.
     *
     * Note: for the browser OAuth flow this only *launches* the system browser.
     * The session is established later, when the provider redirects back to
     * `opson://login-callback` and the app forwards that URL to
     * [completeOAuthRedirect]. A `SignInOutcome.Success` here means "browser
     * opened", not "signed in".
     */
    suspend fun signInWithOAuth(provider: OAuthProvider): SignInOutcome

    /**
     * Whether [url] is the browser-OAuth redirect deep link (`opson://login-callback`).
     * The app's deep-link collector uses this to send auth callbacks to
     * [completeOAuthRedirect] and route everything else through the nav graph.
     *
     * Defaults to false so test fakes that don't drive OAuth needn't implement
     * it; the production impl overrides.
     */
    fun isOAuthRedirect(url: String): Boolean = false

    /**
     * Finish a browser OAuth sign-in from the redirect deep link. [url] is the
     * full `opson://login-callback#access_token=…` callback. Imports the session
     * supabase-kt parsed out of the URL and emits the resulting
     * [AuthState.Authenticated] (replacing any prior session). Returns the
     * outcome so the caller can surface a failure (cancelled / network).
     *
     * Default-throws so test fakes that don't drive OAuth needn't implement it;
     * the production impl overrides.
     */
    suspend fun completeOAuthRedirect(url: String): SignInOutcome =
        throw NotImplementedError("completeOAuthRedirect not implemented by ${this::class.simpleName}")

    /**
     * Native Sign in with Apple — exchange the [credential] (id token + raw
     * nonce captured by [AppleSignInCoordinator]) for a Supabase session.
     * Replaces the current session, like [signInWithOAuth].
     *
     * Default-throws so the test fakes that don't exercise native sign-in
     * needn't implement it; the production impl overrides.
     */
    suspend fun signInWithApple(credential: AppleSignInCredential): SignInOutcome =
        throw NotImplementedError("signInWithApple not implemented by ${this::class.simpleName}")

    /**
     * Attach a native Apple identity to the current (typically anonymous) user,
     * preserving the diary and settings. Default-throws (see [signInWithApple]).
     */
    suspend fun linkAppleIdentity(credential: AppleSignInCredential): LinkIdentityOutcome =
        throw NotImplementedError("linkAppleIdentity not implemented by ${this::class.simpleName}")

    /**
     * Native Google id-token sign-in — exchange a Google id token (+ optional
     * nonce) for a session. Wired ahead of a Google token source (Credential
     * Manager / GIDSignIn) landing. Default-throws (see [signInWithApple]).
     */
    suspend fun signInWithGoogleIdToken(idToken: String, nonce: String? = null): SignInOutcome =
        throw NotImplementedError("signInWithGoogleIdToken not implemented by ${this::class.simpleName}")

    /**
     * Attach an email/password to the current anonymous Supabase user.
     * Triggers a verification email; the user is anonymous until they
     * click the link (see [refreshSession]).
     */
    suspend fun linkEmailIdentity(email: String, password: String): LinkEmailIdentityOutcome
}

/**
 * Resolved auth state. No in-flight sentinel — consumers suspend on
 * [AuthRepository.current] / observe the first [AuthRepository.observe]
 * emission instead.
 *
 * `Authenticated` covers both anonymous and claimed (email/OAuth) sessions
 * — the `isAnonymous` flag distinguishes when it matters (e.g. the
 * "claim your account" prompt).
 *
 * `Unauthenticated` is the fallback case: no Supabase session, no working
 * token, the network layer can't attach a bearer. Profile features fall
 * back to client-only state (`Profile.Fallback`). When connectivity
 * returns, [AuthRepository.retry] re-attempts.
 */
sealed interface AuthState {
    data class Authenticated(
        /** Supabase `auth.users.id`. The single source of user identity. */
        val userId: String,
        val isAnonymous: Boolean,
        val email: String?,
    ) : AuthState

    data class Unauthenticated(
        /** Why the last resolve failed. Null when nothing's been attempted. */
        val cause: Throwable? = null,
        /** What kind of unauthenticated state this is — drives app-level routing. */
        val reason: Reason = Reason.None,
        /**
         * Whether the session we lost was anonymous (guest). Only meaningful for
         * [Reason.SessionExpired], where it picks the copy: a claimed account
         * signs in again; a guest signs in too, losing this phone's guest log.
         */
        val wasAnonymous: Boolean = false,
    ) : AuthState {

        /**
         * Why we're unauthenticated.
         *
         * - [None] — no session / not-yet-resolved / offline. No forced routing;
         *   the auth gate handles navigation as usual. For an onboarded person,
         *   identity self-heal tries a refresh, then sends them to sign in.
         * - [SessionExpired] — the session is genuinely dead: either the auth
         *   server **rejected** our token and a refresh failed, or the client
         *   declared it unrecoverable ([AuthRepository.markSessionUnrecoverable] —
         *   storage lost the token while a cached account exists). The app sends
         *   the person to sign in again; the account's data stays on the device
         *   until someone does. Sticky within the run: a re-resolve that still
         *   finds no session keeps this reason instead of decaying to [None], so
         *   identity self-heal never reads it as an ordinary stranding.
         * - [SignedOut] — a **deliberate** sign-out / account delete this run.
         *   Keeps identity self-heal from re-resolving or routing it, and is
         *   sticky like [SessionExpired]. (After a relaunch this is [None]; the
         *   `hasUserOnboarded` guard — cleared on sign-out — covers that case.)
         */
        enum class Reason { None, SessionExpired, SignedOut }
    }
}
