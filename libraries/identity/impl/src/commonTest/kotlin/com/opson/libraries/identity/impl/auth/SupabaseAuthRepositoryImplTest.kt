package com.opson.libraries.identity.impl.auth

import app.cash.turbine.test
import com.opson.libraries.opson.AppEvent
import com.opson.libraries.opson.AppEventBus
import com.opson.libraries.opson.UserScopedDataReset
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.identity.auth.AppleSignInCredential
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.DeleteAccountOutcome
import com.opson.libraries.identity.auth.LinkEmailIdentityOutcome
import com.opson.libraries.identity.auth.OAuthProvider
import com.opson.libraries.identity.auth.RefreshOutcome
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.identity.auth.SignUpOutcome
import com.opson.libraries.identity.impl.MeDto
import com.opson.libraries.identity.impl.PatchMeRequest
import com.opson.libraries.identity.impl.ProfileApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.delete
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [SupabaseAuthRepositoryImpl]'s resolve loop + token contract.
 *
 * The interesting edges all flow through the [SupabaseAuthGateway] seam:
 *  - cold-boot resolve: `Initializing → … → Authenticated`,
 *  - no-session resolve: `NotAuthenticated → Unauthenticated` (NO auto-anon),
 *  - exhausted resolve attempts (gateway stays Transient forever),
 *  - `retry()` is a no-op when already Authenticated,
 *  - `signOut()` flips state, dumps the prior user's local data, and
 *    dispatches `AppEvent.UserChanged(prev, null)`,
 *  - an account switch dumps the departing user's data *before* emitting the
 *    new session, and announces `UserChanged(prev, next)`,
 *  - `current()` suspends pre-resolve, then unsuspends after.
 *
 * Tests deliberately *don't* construct a real `SupabaseClient` — the
 * gateway interface is the unit-test seam. Tests for the sign-in /
 * sign-up / link error-mapping outcomes belong in a separate file (they
 * exercise the supabase-kt exception classes, which are not synthesizable
 * cheaply from commonTest yet).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseAuthRepositoryImplTest : CoroutineTest() {

    // ---------- resolve loop ----------

    @Test
    fun resolve_authenticatedOnFirstPoll_emitsAuthenticatedImmediately() = runUnitTest {
        // Warm boot path: supabase-kt already has a persisted session.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = claimedSession(),
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val state = assertIs<AuthState.Authenticated>(repo.current())
        assertEquals("user-1", state.userId)
        assertEquals(false, state.isAnonymous)
        assertEquals("a@b.com", state.email)
    }

    // ---------- session rejection (server rejected our token) ----------

    @Test
    fun sessionRejected_tearsDown_toSessionExpired_carryingAnonymity() = runUnitTest {
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = anonymousSession(),
        )
        val bus = FakeSessionRejectionBus()
        val repo = build(gateway = gateway, sessionRejectionBus = bus)
        advanceUntilIdle()
        assertIs<AuthState.Authenticated>(repo.current())

        // The token layer reports the auth server rejected our (guest) session.
        bus.signalRejected(wasAnonymous = true)
        advanceUntilIdle()

        val state = assertIs<AuthState.Unauthenticated>(repo.current())
        assertEquals(AuthState.Unauthenticated.Reason.SessionExpired, state.reason)
        assertTrue(state.wasAnonymous, "the lost session's anonymity must survive for routing")
        assertEquals(1, gateway.signOutCalls, "the dead supabase session is torn down")
    }

    @Test
    fun sessionRejected_isIdempotent_acrossABurst() = runUnitTest {
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = claimedSession(),
        )
        val bus = FakeSessionRejectionBus()
        val repo = build(gateway = gateway, sessionRejectionBus = bus)
        advanceUntilIdle()

        // A storm of rejected requests all signal; the teardown happens once.
        bus.signalRejected(wasAnonymous = false)
        bus.signalRejected(wasAnonymous = false)
        advanceUntilIdle()

        assertIs<AuthState.Unauthenticated>(repo.current())
        assertEquals(1, gateway.signOutCalls, "a burst collapses to a single teardown")
    }

    @Test
    fun aSessionExpiry_keepsTheAccountsLocalData_andTheSameAccountSigningBackInKeepsIt() = runUnitTest {
        // Meals logged offline sit in the outbox. The session is then rejected
        // and the screen says "sign in again to pick up where you left off".
        val gateway = appleGateway().apply { advanceToAuthenticated(claimedSession()) }
        val bus = FakeSessionRejectionBus()
        val reset = RecordingUserScopedDataReset()
        val expiredAccounts = ExpiredAccountStore(TestCacheFactory())
        val repo = build(
            gateway = gateway,
            sessionRejectionBus = bus,
            userScopedDataReset = reset,
            expiredAccounts = expiredAccounts,
        )
        advanceUntilIdle()

        bus.signalRejected(wasAnonymous = false)
        advanceUntilIdle()
        assertEquals(emptyList(), reset.clearedFor, "an expiry is not a change of user")

        assertEquals(SignInOutcome.Success, repo.signInWithApple(appleCredential(authorizationCode = null)))

        assertEquals(emptyList(), reset.clearedFor, "the same account came back: its outbox drains")
        assertNull(expiredAccounts.read())
    }

    @Test
    fun aSessionExpiry_thenADifferentAccount_wipesTheHeldDataBeforeItsFirstEmission() = runUnitTest {
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = claimedSession(userId = "user-1"),
            onSignInWithAppleIdToken = { _, _ -> advanceToAuthenticated(claimedSession(userId = "user-2")) },
        )
        val bus = FakeSessionRejectionBus()
        val reset = RecordingUserScopedDataReset()
        val repo = build(gateway = gateway, sessionRejectionBus = bus, userScopedDataReset = reset)
        advanceUntilIdle()
        bus.signalRejected(wasAnonymous = false)
        advanceUntilIdle()

        repo.signInWithApple(appleCredential(authorizationCode = null))

        assertEquals(listOf("user-1"), reset.clearedFor)
        assertEquals("user-2", assertIs<AuthState.Authenticated>(repo.current()).userId)
    }

    @Test
    fun aSessionExpiry_thenTheSignInAgainTap_thenTheSameAccount_keepsItsLocalData() = runUnitTest {
        // The SessionExpired screen's only button signs out before routing to
        // sign-in. That tap must not count as a log out of the held account.
        val gateway = appleGateway().apply { advanceToAuthenticated(claimedSession()) }
        val bus = FakeSessionRejectionBus()
        val reset = RecordingUserScopedDataReset()
        val expiredAccounts = ExpiredAccountStore(TestCacheFactory())
        val repo = build(
            gateway = gateway,
            sessionRejectionBus = bus,
            userScopedDataReset = reset,
            expiredAccounts = expiredAccounts,
        )
        advanceUntilIdle()
        bus.signalRejected(wasAnonymous = false)
        advanceUntilIdle()

        repo.signOut()
        assertEquals(emptyList(), reset.clearedFor, "the tap only clears the dead session")
        assertNotNull(expiredAccounts.read(), "still waiting for the person to sign in")

        assertEquals(SignInOutcome.Success, repo.signInWithApple(appleCredential(authorizationCode = null)))

        assertEquals(emptyList(), reset.clearedFor, "the same account came back: its outbox drains")
        assertNull(expiredAccounts.read())
    }

    @Test
    fun aSessionExpiry_thenTheSignInAgainTap_thenADifferentAccount_wipesTheHeldData() = runUnitTest {
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = claimedSession(userId = "user-1"),
            onSignInWithAppleIdToken = { _, _ -> advanceToAuthenticated(claimedSession(userId = "user-2")) },
        )
        val bus = FakeSessionRejectionBus()
        val reset = RecordingUserScopedDataReset()
        val repo = build(gateway = gateway, sessionRejectionBus = bus, userScopedDataReset = reset)
        advanceUntilIdle()
        bus.signalRejected(wasAnonymous = false)
        advanceUntilIdle()
        repo.signOut()

        repo.signInWithApple(appleCredential(authorizationCode = null))

        assertEquals(listOf("user-1"), reset.clearedFor)
    }

    @Test
    fun aSessionExpiry_thenASignInWhileTheHoldCantBeRead_wipesTheHeldData() = runUnitTest {
        val cacheFactory = TestCacheFactory()
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = claimedSession(userId = "user-1"),
            onSignInWithAppleIdToken = { _, _ -> advanceToAuthenticated(claimedSession(userId = "user-2")) },
        )
        val bus = FakeSessionRejectionBus()
        val reset = RecordingUserScopedDataReset()
        val repo = build(
            gateway = gateway,
            sessionRejectionBus = bus,
            userScopedDataReset = reset,
            expiredAccounts = ExpiredAccountStore(cacheFactory),
        )
        advanceUntilIdle()
        bus.signalRejected(wasAnonymous = false)
        advanceUntilIdle()
        cacheFactory.readsFail = true

        repo.signInWithApple(appleCredential(authorizationCode = null))

        assertEquals(1, reset.clearedFor.size, "user-1's outbox must not drain under user-2's token")
    }

    @Test
    fun aLaunchWithALiveSession_whileTheHoldCantBeRead_keepsItsData() = runUnitTest {
        val cacheFactory = TestCacheFactory().apply { readsFail = true }
        val reset = RecordingUserScopedDataReset()
        build(
            gateway = FakeSupabaseAuthGateway(AuthGatewayStatus.Authenticated, session = claimedSession()),
            userScopedDataReset = reset,
            expiredAccounts = ExpiredAccountStore(cacheFactory),
        )
        advanceUntilIdle()

        assertEquals(emptyList(), reset.clearedFor)
    }

    @Test
    fun theHeldAccount_survivesARelaunch() = runUnitTest {
        val cacheFactory = TestCacheFactory()
        val bus = FakeSessionRejectionBus()
        build(
            gateway = FakeSupabaseAuthGateway(AuthGatewayStatus.Authenticated, session = claimedSession()),
            sessionRejectionBus = bus,
            expiredAccounts = ExpiredAccountStore(cacheFactory),
        )
        advanceUntilIdle()
        bus.signalRejected(wasAnonymous = false)
        advanceUntilIdle()

        // Next launch: no session, and someone else signs in.
        val reset = RecordingUserScopedDataReset()
        val relaunched = build(
            gateway = FakeSupabaseAuthGateway(
                initialStatus = AuthGatewayStatus.NotAuthenticated,
                session = null,
                onSignInWithAppleIdToken = { _, _ -> advanceToAuthenticated(claimedSession(userId = "user-2")) },
            ),
            userScopedDataReset = reset,
            expiredAccounts = ExpiredAccountStore(cacheFactory),
        )
        advanceUntilIdle()
        assertEquals(emptyList(), reset.clearedFor, "still waiting for the person to sign in")

        relaunched.signInWithApple(appleCredential(authorizationCode = null))

        assertEquals(listOf("user-1"), reset.clearedFor)
    }

    @Test
    fun markSessionUnrecoverable_tearsDown_toSessionExpired() = runUnitTest {
        // AUTH-19: the healer found a cached account but no revivable session —
        // the client-declared twin of a server rejection, same teardown.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.NotAuthenticated,
            session = null,
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()
        assertIs<AuthState.Unauthenticated>(repo.current())

        repo.markSessionUnrecoverable(wasAnonymous = true)
        advanceUntilIdle()

        val state = assertIs<AuthState.Unauthenticated>(repo.current())
        assertEquals(AuthState.Unauthenticated.Reason.SessionExpired, state.reason)
        assertTrue(state.wasAnonymous, "the stranded account's anonymity drives the recovery copy")
        assertEquals(1, gateway.signOutCalls, "any half-dead supabase state is torn down")
    }

    @Test
    fun sessionExpired_staysSticky_acrossAFailedReResolve() = runUnitTest {
        // AUTH-19: retry (healer probe, SessionExpired-screen button,
        // connectivity flip) re-resolves; when the session still isn't there the
        // reason must NOT decay to None — that decay made identity self-heal
        // read a dead session as an ordinary stranding and mint over it.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = anonymousSession(),
            onSignOut = {
                setStatus(AuthGatewayStatus.NotAuthenticated)
                replaceSession(null)
            },
        )
        val bus = FakeSessionRejectionBus()
        val repo = build(gateway = gateway, sessionRejectionBus = bus)
        advanceUntilIdle()
        bus.signalRejected(wasAnonymous = true)
        advanceUntilIdle()

        val retried = assertIs<AuthState.Unauthenticated>(repo.retry())

        assertEquals(
            AuthState.Unauthenticated.Reason.SessionExpired,
            retried.reason,
            "a still-dead session keeps its declared reason",
        )
        assertTrue(retried.wasAnonymous, "anonymity survives the re-resolve for routing")
    }

    @Test
    fun resolve_notAuthenticated_emitsUnauthenticated_withoutCreatingAnAccount() = runUnitTest {
        // Cold-boot fresh-install path: no session. We no longer auto-create
        // one — the app stays Unauthenticated until onboarding mints an
        // account explicitly. This is what kills the orphan-anon problem.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.NotAuthenticated,
            session = null,
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val state = assertIs<AuthState.Unauthenticated>(repo.current())
        assertNull(state.cause, "a clean no-session resolve carries no error")
    }

    @Test
    fun resolve_initializing_loopsUntilSettled() = runUnitTest {
        // Mid-init path: status reports Initializing once, then Authenticated.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Initializing,
            session = claimedSession(),
            statusSequence = listOf(
                AuthGatewayStatus.Initializing,
                AuthGatewayStatus.Authenticated,
            ),
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        assertIs<AuthState.Authenticated>(repo.current())
        assertTrue(gateway.statusReads >= 2, "resolve must re-poll after a Transient status")
    }

    @Test
    fun resolve_alwaysTransient_exhaustsAttempts_andEmitsUnauthenticatedNoCause() = runUnitTest {
        // Pathological path: gateway never settles. After MaxResolveAttempts
        // (5) iterations we emit Unauthenticated(cause=null) — distinguishable
        // from a real error because cause is null.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Initializing,
            session = null,
            // Every poll reports Initializing forever.
            statusSequence = List(10) { AuthGatewayStatus.Initializing },
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val state = assertIs<AuthState.Unauthenticated>(repo.current())
        assertNull(state.cause, "exhausted resolve has no exception attached")
    }

    @Test
    fun observe_emitsEachStateTransition() = runUnitTest {
        // After the initial resolve, every subsequent emit lands on the
        // shared flow. We pin the no-cause Unauthenticated → signOut path.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = claimedSession(),
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        repo.observe().test {
            assertIs<AuthState.Authenticated>(awaitItem())
            repo.signOut()
            assertIs<AuthState.Unauthenticated>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------- current() suspension ----------

    @Test
    fun current_suspendsUntilResolveCompletes() = runUnitTest {
        // The whole reason for the resolve loop: callers (including the
        // network client's bearer plugin) suspend until auth lands.
        val initGate = CompletableDeferred<Unit>()
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = claimedSession(),
            onAwaitInitialization = { initGate.await() },
        )
        val repo = build(gateway = gateway)

        val pending = async { repo.current() }
        // Yield the dispatcher to let the resolve start.
        advanceUntilIdle()
        assertTrue(pending.isActive, "current() must still be suspended until session hydration completes")

        initGate.complete(Unit)
        advanceUntilIdle()
        assertIs<AuthState.Authenticated>(pending.await())
    }

    // Token methods (accessToken / refreshAccessToken) live on
    // [GatewayAuthTokenProvider] now — see [GatewayAuthTokenProviderTest].

    // ---------- retry ----------

    @Test
    fun retry_isNoOp_whenAlreadyAuthenticated() = runUnitTest {
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = claimedSession(),
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val readsBefore = gateway.statusReads
        val result = repo.retry()
        assertIs<AuthState.Authenticated>(result)
        assertEquals(readsBefore, gateway.statusReads, "retry must not re-poll when authed")
    }

    @Test
    fun retry_reRunsResolve_afterUnauthenticated() = runUnitTest {
        // First resolve finds no session → Unauthenticated. Later a session
        // appears (e.g. it hydrated after a transient failure, or a sign-in
        // landed out of band); retry() re-resolves and lands authed.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.NotAuthenticated,
            session = null,
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()
        assertIs<AuthState.Unauthenticated>(repo.current())

        gateway.replaceSession(claimedSession())
        gateway.setStatus(AuthGatewayStatus.Authenticated)
        val result = repo.retry()
        assertIs<AuthState.Authenticated>(result)
    }

    // ---------- signOut ----------

    @Test
    fun signOut_clearsState_dumpsPriorUser_andAnnouncesUserChange() = runUnitTest {
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = claimedSession(),
        )
        val events = RecordingEventBus()
        val reset = RecordingUserScopedDataReset()
        val repo = build(gateway = gateway, appEventBus = events, userScopedDataReset = reset)
        advanceUntilIdle()

        repo.signOut()
        assertIs<AuthState.Unauthenticated>(repo.current())
        assertEquals(1, gateway.signOutCalls)
        // The departing user's device-local data is dumped exactly once.
        assertEquals(listOf("user-1"), reset.clearedFor)
        // Boot announced (null → user-1); sign-out announced (user-1 → null).
        assertEquals(
            listOf<AppEvent>(
                AppEvent.UserChanged(previous = null, current = "user-1"),
                AppEvent.UserChanged(previous = "user-1", current = null),
            ),
            events.dispatched,
        )
    }

    @Test
    fun signOut_emitsSignedOutReason_soHealerDoesNotResurrect() = runUnitTest {
        // The reason distinguishes a deliberate sign-out from a never-signed-in
        // stranded guest. Without it, identity self-heal would mint a fresh
        // anonymous guest right after the user chose to sign out.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = claimedSession(),
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        repo.signOut()

        val state = assertIs<AuthState.Unauthenticated>(repo.current())
        assertEquals(AuthState.Unauthenticated.Reason.SignedOut, state.reason)
    }

    @Test
    fun accountSwitch_dumpsDepartingUser_beforeEmit_andAnnouncesUserChange() = runUnitTest {
        // Start signed in as a guest, then sign into a *different* existing
        // account via the browser flow. signInWithOAuth only opens the browser;
        // the switch lands when completeOAuthRedirect imports the new session.
        // The guest's local data must be dumped before the new session is
        // announced, and the switch announced as UserChanged.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = anonymousSession(userId = "guest-1"),
            onCompleteOAuthRedirect = {
                advanceToAuthenticated(claimedSession(userId = "real-2", email = "real@b.com"))
            },
        )
        val events = RecordingEventBus()
        val reset = RecordingUserScopedDataReset()
        val repo = build(gateway = gateway, appEventBus = events, userScopedDataReset = reset)
        advanceUntilIdle()

        // Start the sign-in (parks, awaiting the redirect), then drive the redirect.
        // runCurrent (not advanceUntilIdle) so the 3-min await timeout doesn't fire.
        val signIn = async { repo.signInWithOAuth(OAuthProvider.Google) }
        runCurrent()
        repo.completeOAuthRedirect("opson://login-callback#access_token=t")
        assertIs<com.opson.libraries.identity.auth.SignInOutcome.Success>(signIn.await())

        val state = assertIs<AuthState.Authenticated>(repo.current())
        assertEquals("real-2", state.userId)
        assertEquals(false, state.isAnonymous)
        // The guest's data — and only the guest's — was dumped.
        assertEquals(listOf("guest-1"), reset.clearedFor)
        // The claim case is excluded: a switch to a *different* id announces.
        assertEquals(
            AppEvent.UserChanged(previous = "guest-1", current = "real-2"),
            events.dispatched.last(),
        )
    }

    @Test
    fun accountClaim_sameUserId_doesNotDump_andAnnouncesNothing() = runUnitTest {
        // Linking/claiming keeps the same user id (anon guest-1 becomes a
        // claimed account but stays guest-1). The link redirect carries no
        // session, so completeOAuthRedirect refreshes the session (a stale-token
        // refresh; hydrate alone leaves is_anonymous=true — see the device
        // regression below). The guest's progress is theirs: no dump, no
        // UserChanged, no other announcement — the new AuthState emission's
        // isAnonymous flip is itself what refires the level-keyed sync loops.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = anonymousSession(userId = "guest-1"),
            onRefreshSession = {
                // Same id, now non-anonymous + email — models the fresh JWT + user
                // a session refresh mints after the identity is linked.
                advanceToAuthenticated(claimedSession(userId = "guest-1", email = "claimed@b.com"))
            },
        )
        val events = RecordingEventBus()
        val reset = RecordingUserScopedDataReset()
        val repo = build(gateway = gateway, appEventBus = events, userScopedDataReset = reset)
        advanceUntilIdle()
        val dispatchedAtBoot = events.dispatched.size

        val link = async { repo.linkOAuthIdentity(OAuthProvider.Google) }
        runCurrent()
        repo.completeOAuthRedirect("opson://login-callback")
        assertIs<com.opson.libraries.identity.auth.LinkIdentityOutcome.Success>(link.await())

        val state = assertIs<AuthState.Authenticated>(repo.current())
        assertEquals("guest-1", state.userId)
        assertEquals(false, state.isAnonymous, "the claim still flips anon → claimed")
        assertEquals(1, gateway.refreshSessionCalls, "link redirect refreshes the session, never parses a session from the URL")
        assertEquals(0, gateway.hydrateCurrentUserCalls, "link redirect must NOT rely on hydrate-only (leaves is_anonymous=true on device)")
        assertEquals(0, gateway.completeOAuthRedirectCalls, "a link redirect must NOT import a session from the URL")
        assertTrue(reset.clearedFor.isEmpty(), "same-user claim must not dump the guest's data")
        val afterClaim = events.dispatched.drop(dispatchedAtBoot)
        assertEquals(
            emptyList<AppEvent>(),
            afterClaim,
            "same-user claim announces nothing — the AuthState emission carries the flip",
        )
    }

    @Test
    fun resolve_authenticatedButSessionNotHydrated_fetchesUserAndResolves_neverThrows() = runUnitTest {
        // Regression: a tokens-only session (status Authenticated but
        // currentSession() == null because the user object isn't fetched yet —
        // an OAuth import or a storage load) must hydrate + recover, NOT crash.
        // The old code did `error("Authenticated status but no session")`, which
        // surfaced on device as repeated red stack traces during sign-in/boot.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = null,
            onHydrateCurrentUser = {
                // Fetching the user populates the session — what supabase's
                // retrieveUserForCurrentSession(updateSession = true) does.
                advanceToAuthenticated(claimedSession(userId = "u-1"))
            },
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val state = assertIs<AuthState.Authenticated>(repo.current())
        assertEquals("u-1", state.userId)
        assertTrue(gateway.hydrateCurrentUserCalls >= 1, "resolve must hydrate a tokens-only session")
    }

    // ---------- browser OAuth: suspend-until-redirect ----------

    @Test
    fun linkOAuthIdentity_suspendsUntilRedirect_doesNotReturnSuccessWhileAnonymous() = runUnitTest {
        // Regression for the broken flow: linkIdentity(OAuth) only OPENS the
        // browser, so the repo must NOT report Success (and must not stay
        // anonymous) until the opson://login-callback redirect actually lands.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = anonymousSession(userId = "guest-1"),
            onRefreshSession = {
                advanceToAuthenticated(claimedSession(userId = "guest-1", email = "claimed@b.com"))
            },
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val link = async { repo.linkOAuthIdentity(OAuthProvider.Google) }
        runCurrent()

        // Browser opened, but the redirect hasn't returned: still suspended, and
        // the user is still the anonymous guest. (The old code emitted here and
        // returned Success → "Save your progress" banner persisted.)
        assertTrue(link.isActive, "linkOAuthIdentity must await the redirect, not resolve on browser open")
        assertEquals(1, gateway.linkOAuthIdentityCalls, "the browser was launched exactly once")
        assertTrue(
            assertIs<AuthState.Authenticated>(repo.current()).isAnonymous,
            "still anonymous until the redirect resolves the link",
        )

        // The redirect lands → the link resolves Success, now non-anonymous.
        repo.completeOAuthRedirect("opson://login-callback")
        assertIs<com.opson.libraries.identity.auth.LinkIdentityOutcome.Success>(link.await())
        assertEquals(false, assertIs<AuthState.Authenticated>(repo.current()).isAnonymous)
    }

    @Test
    fun linkOAuthIdentity_redirect_refreshesSession_evenWhenHydrateAloneLeavesAnonymous() =
        runUnitTest {
            // Device regression: after the Google link redirect the app stayed
            // anonymous (Save-your-progress banner persisted, hasEmail=false,
            // same user id) even though the link reported Success. Root cause:
            // the Link redirect path hydrated the user (GET /user) instead of
            // refreshing the session — and on device GET /user with the still-anon
            // token does NOT fold in the just-linked identity, so is_anonymous
            // stayed true. This test models that exact split: hydrate does NOT
            // claim, only a session refresh does.
            val gateway = FakeSupabaseAuthGateway(
                initialStatus = AuthGatewayStatus.Authenticated,
                session = anonymousSession(userId = "guest-1"),
                onHydrateCurrentUser = {
                    // Hydrate-only: refetches the user but the session is still the
                    // anon one — exactly what the device showed.
                    advanceToAuthenticated(anonymousSession(userId = "guest-1"))
                },
                onRefreshSession = {
                    // The fresh JWT + user the refresh mints carries the linked
                    // identity → claimed, with the real email surfaced.
                    advanceToAuthenticated(claimedSession(userId = "guest-1", email = "claimed@b.com"))
                },
            )
            val repo = build(gateway = gateway)
            advanceUntilIdle()

            val link = async { repo.linkOAuthIdentity(OAuthProvider.Google) }
            runCurrent()
            repo.completeOAuthRedirect("opson://login-callback")
            assertIs<com.opson.libraries.identity.auth.LinkIdentityOutcome.Success>(
                link.await(),
            )

            val state = assertIs<AuthState.Authenticated>(repo.current())
            assertEquals("guest-1", state.userId, "same id — it's a claim, not a switch")
            assertEquals(false, state.isAnonymous, "the link must flip the user off anonymous")
            assertEquals("claimed@b.com", state.email, "the claimed account's email must surface")
            assertEquals(1, gateway.refreshSessionCalls, "the link redirect refreshes the session")
            assertEquals(0, gateway.hydrateCurrentUserCalls, "hydrate-only is the broken path — must not be used")
        }

    @Test
    fun signInWithOAuth_suspendsUntilRedirect_thenParsesImportsAndReturnsSuccess() = runUnitTest {
        // Happy sign-in: signInWithOAuth parks awaiting the redirect;
        // completeOAuthRedirect parses+imports the session and emits Authenticated.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.NotAuthenticated,
            session = null,
            onCompleteOAuthRedirect = {
                advanceToAuthenticated(claimedSession(userId = "oauth-1", email = "g@b.com"))
            },
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val signIn = async { repo.signInWithOAuth(OAuthProvider.Google) }
        runCurrent()
        assertTrue(signIn.isActive, "signInWithOAuth must await the redirect, not resolve on browser open")
        assertEquals(1, gateway.signInWithOAuthCalls)

        repo.completeOAuthRedirect("opson://login-callback#access_token=t&refresh_token=r")

        assertIs<com.opson.libraries.identity.auth.SignInOutcome.Success>(signIn.await())
        assertEquals(1, gateway.completeOAuthRedirectCalls, "sign-in imports the session from the URL")
        assertEquals(0, gateway.hydrateCurrentUserCalls, "sign-in does not use the link refresh path")
        val state = assertIs<AuthState.Authenticated>(repo.current())
        assertEquals("oauth-1", state.userId)
        assertEquals(false, state.isAnonymous)
    }

    @Test
    fun completeOAuthRedirect_failure_resolvesSuspendedCall_andDoesNotThrow() = runUnitTest {
        // A redirect that fails to import (e.g. no tokens — user backed out)
        // must resolve the suspended sign-in with a failure outcome, never throw,
        // and leave auth state untouched.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.NotAuthenticated,
            session = null,
            onCompleteOAuthRedirect = { throw IllegalArgumentException("No access token found") },
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val signIn = async { repo.signInWithOAuth(OAuthProvider.Google) }
        runCurrent()

        // Does not throw out of the redirect path.
        repo.completeOAuthRedirect("opson://login-callback#error=access_denied")

        assertIs<com.opson.libraries.identity.auth.SignInOutcome.Cancelled>(signIn.await())
        assertIs<AuthState.Unauthenticated>(repo.current())
    }

    @Test
    fun completeOAuthRedirect_noPendingHandle_coldLaunchConfirmLink_importsSession() = runUnitTest {
        // AUTH-26: the user killed the app mid-signup, so no starter is parked
        // when the confirmation link relaunches the app. The link's fragment
        // carries a live session — import it directly instead of discarding it,
        // so the confirmed account isn't stranded unauthenticated.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.NotAuthenticated,
            session = null,
            onCompleteOAuthRedirect = {
                advanceToAuthenticated(claimedSession(userId = "confirmed-1", email = "new@b.com"))
            },
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val outcome = repo.completeOAuthRedirect("opson://login-callback#access_token=t&refresh_token=r")

        assertIs<com.opson.libraries.identity.auth.SignInOutcome.Success>(outcome)
        assertEquals(1, gateway.completeOAuthRedirectCalls, "cold-launch confirm link must import the session")
        val state = assertIs<AuthState.Authenticated>(repo.current())
        assertEquals("confirmed-1", state.userId)
        assertEquals(false, state.isAnonymous)
    }

    @Test
    fun completeOAuthRedirect_noPendingHandle_noImportableSession_isNoOp() = runUnitTest {
        // A genuine stray / duplicate redirect carries no tokens — parseSessionFromUrl
        // throws, so the import fails and auth state is left untouched (no throw).
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.NotAuthenticated,
            session = null,
            onCompleteOAuthRedirect = { throw IllegalArgumentException("No access token found") },
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val outcome = repo.completeOAuthRedirect("opson://login-callback")

        assertIs<com.opson.libraries.identity.auth.SignInOutcome.Cancelled>(outcome)
        assertIs<AuthState.Unauthenticated>(repo.current())
    }

    @Test
    fun completeOAuthRedirect_noPendingHandle_alreadyAuthenticated_isNoOp() = runUnitTest {
        // A stray redirect must never hijack a live session: with a session already
        // resolved and no starter parked, the redirect is ignored without touching
        // the gateway.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = claimedSession(userId = "live-1"),
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val outcome = repo.completeOAuthRedirect("opson://login-callback#access_token=t")

        assertIs<com.opson.libraries.identity.auth.SignInOutcome.Cancelled>(outcome)
        assertEquals(0, gateway.completeOAuthRedirectCalls, "already authenticated → the gateway is never touched")
        val state = assertIs<AuthState.Authenticated>(repo.current())
        assertEquals("live-1", state.userId)
    }

    @Test
    fun isOAuthRedirect_matchesCallbackUrl_only() = runUnitTest {
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.NotAuthenticated,
            session = null,
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        assertTrue(repo.isOAuthRedirect("opson://login-callback#access_token=t"))
        assertEquals(false, repo.isOAuthRedirect("opson://profile"))
    }

    // ---------- refreshSession ----------

    @Test
    fun refreshSession_anonymousUser_returnsStillPending() = runUnitTest {
        // Regression: VerifyEmailScreen's AppResumed handler calls
        // refreshSession() the moment it mounts. If we report EmailConfirmed
        // for any anonymous user (the gateway used to do that via
        // `isAnon || emailConfirmedAt != null`), the user is yanked to Home
        // before they can read the "tap the link in your email" copy.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = anonymousSession(),
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val outcome = repo.refreshSession()
        assertIs<RefreshOutcome.StillPending>(outcome)
    }

    @Test
    fun refreshSession_emailConfirmedSession_returnsEmailConfirmed() = runUnitTest {
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = claimedSession(),
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val outcome = repo.refreshSession()
        assertIs<RefreshOutcome.EmailConfirmed>(outcome)
    }

    @Test
    fun refreshSession_noSession_returnsSessionExpired_withoutHittingGateway() = runUnitTest {
        // A fresh email signup leaves NO session (confirmation pending). The
        // verify screen still calls refreshSession() on resume — there's nothing
        // to refresh, and supabase-kt throws on a null session. That throw used
        // to map to Unknown → a silent no-op that stranded the user on the verify
        // screen. Resolve to SessionExpired (→ back to sign-in) instead, and don't
        // even attempt the doomed refresh.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.NotAuthenticated,
            session = null,
            onRefreshSession = { error("supabase-kt throws when there is no session to refresh") },
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val outcome = repo.refreshSession()
        assertIs<RefreshOutcome.SessionExpired>(outcome)
        assertEquals(0, gateway.refreshSessionCalls, "no session → skip the refresh entirely")
    }

    // ---------- Sign in with Apple ----------

    @Test
    fun signInWithApple_sendsTheAuthorizationCodeSoDeletionCanRevoke() = runUnitTest {
        val api = RecordingProfileApi()
        val gateway = appleGateway()
        val repo = build(gateway = gateway, profileApi = api)
        advanceUntilIdle()

        val outcome = repo.signInWithApple(appleCredential(authorizationCode = "code-1"))
        advanceUntilIdle()

        assertEquals(SignInOutcome.Success, outcome)
        assertEquals(listOf("code-1"), api.sentCodes)
    }

    @Test
    fun signInWithApple_stillSucceeds_whenTheServerWontKeepTheCode() = runUnitTest {
        val api = RecordingProfileApi(failSend = true)
        val repo = build(gateway = appleGateway(), profileApi = api)
        advanceUntilIdle()

        val outcome = repo.signInWithApple(appleCredential(authorizationCode = "code-1"))
        advanceUntilIdle()

        assertEquals(SignInOutcome.Success, outcome)
        assertIs<AuthState.Authenticated>(repo.current())
    }

    @Test
    fun signInWithApple_withoutACode_sendsNothing() = runUnitTest {
        val api = RecordingProfileApi()
        val repo = build(gateway = appleGateway(), profileApi = api)
        advanceUntilIdle()

        repo.signInWithApple(appleCredential(authorizationCode = null))
        advanceUntilIdle()

        assertEquals(emptyList(), api.sentCodes)
    }

    private fun appleGateway() = FakeSupabaseAuthGateway(
        initialStatus = AuthGatewayStatus.NotAuthenticated,
        session = null,
        onSignInWithAppleIdToken = { _, _ -> advanceToAuthenticated(claimedSession()) },
    )

    private fun appleCredential(authorizationCode: String?) = AppleSignInCredential(
        identityToken = "id-token",
        nonce = "nonce",
        authorizationCode = authorizationCode,
    )

    // ---------- deleteAccount ----------

    @Test
    fun deleteAccount_noSession_returnsNotSignedIn() = runUnitTest {
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.NotAuthenticated,
            session = null,
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val outcome = repo.deleteAccount()
        assertIs<DeleteAccountOutcome.NotSignedIn>(outcome)
    }

    @Test
    fun deleteAccount_success_signsOutAndClearsTheAccount() = runUnitTest {
        val reset = RecordingUserScopedDataReset()
        val repo = build(
            gateway = signedInGateway(),
            profileApi = DeletingProfileApi(failure = null),
            userScopedDataReset = reset,
        )
        advanceUntilIdle()

        assertEquals(DeleteAccountOutcome.Success, repo.deleteAccount())

        val state = assertIs<AuthState.Unauthenticated>(repo.current())
        assertEquals(AuthState.Unauthenticated.Reason.SignedOut, state.reason)
        assertEquals(listOf("user-1"), reset.clearedFor)
    }

    @Test
    fun deleteAccount_serverWithoutTheServiceRoleKey_isNotConfigured() = runUnitTest {
        val refusal = serverRefusal(HttpStatusCode.ServiceUnavailable, code = "delete_not_configured")
        val repo = build(gateway = signedInGateway(), profileApi = DeletingProfileApi(failure = refusal))
        advanceUntilIdle()

        assertEquals(DeleteAccountOutcome.NotConfigured, repo.deleteAccount())
        assertIs<AuthState.Authenticated>(repo.current(), "a refused delete keeps the account signed in")
    }

    @Test
    fun deleteAccount_supabaseAdminFailure_isATransientUnknown_notNotConfigured() = runUnitTest {
        // Same 503 status, different problem: the admin call failed and a
        // retry may work. Reading it as a deploy problem misled the logs.
        val refusal = serverRefusal(HttpStatusCode.ServiceUnavailable, code = "delete_failed")
        val repo = build(gateway = signedInGateway(), profileApi = DeletingProfileApi(failure = refusal))
        advanceUntilIdle()

        assertIs<DeleteAccountOutcome.Unknown>(repo.deleteAccount())
    }

    @Test
    fun deleteAccount_sessionTheServerNoLongerAccepts_isNotSignedIn() = runUnitTest {
        val refusal = serverRefusal(HttpStatusCode.Unauthorized, code = "unauthorized")
        val repo = build(gateway = signedInGateway(), profileApi = DeletingProfileApi(failure = refusal))
        advanceUntilIdle()

        assertEquals(DeleteAccountOutcome.NotSignedIn, repo.deleteAccount())
    }

    @Test
    fun deleteAccount_cancelledWhileSigningOut_stillSignsOut() = runUnitTest {
        // The server already deleted the account; leaving the screen while the
        // logout request hangs must not leave its data behind.
        val gateway = hangingSignOutGateway()
        val reset = RecordingUserScopedDataReset()
        val repo = build(gateway = gateway, profileApi = DeletingProfileApi(failure = null), userScopedDataReset = reset)
        advanceUntilIdle()

        val deleting = launch { repo.deleteAccount() }
        gateway.awaitSignOutStarted()
        deleting.cancelAndJoin()

        val state = assertIs<AuthState.Unauthenticated>(repo.current())
        assertEquals(AuthState.Unauthenticated.Reason.SignedOut, state.reason)
        assertEquals(listOf("user-1"), reset.clearedFor)
    }

    private fun signedInGateway() = FakeSupabaseAuthGateway(
        initialStatus = AuthGatewayStatus.Authenticated,
        session = claimedSession(),
    )

    /** A gateway whose logout request never answers, and which clears the session when cancelled. */
    private fun hangingSignOutGateway() = FakeSupabaseAuthGateway(
        initialStatus = AuthGatewayStatus.Authenticated,
        session = claimedSession(),
        onSignOut = {
            try {
                signOutStarted.complete(Unit)
                awaitCancellation()
            } finally {
                replaceSession(null)
                setStatus(AuthGatewayStatus.NotAuthenticated)
            }
        },
    )

    /** The exception the app's `expectSuccess` client throws for this answer. */
    private suspend fun serverRefusal(status: HttpStatusCode, code: String): Throwable {
        val client = HttpClient(
            MockEngine {
                respond(
                    content = """{"error":{"code":"$code","message":"Please try again."}}""",
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) { expectSuccess = true }
        return assertNotNull(runCatching { client.delete("https://api.test/v1/me") }.exceptionOrNull())
    }

    @Test
    fun signOut_proceedsEvenIfGatewayThrows() = runUnitTest {
        // Server-side sign-out can fail (offline, expired token, etc.).
        // The local state still flips to Unauthenticated — leaving the
        // user "stuck signed in" client-side would be worse.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = claimedSession(),
            onSignOut = { throw RuntimeException("network") },
        )
        val events = RecordingEventBus()
        val reset = RecordingUserScopedDataReset()
        val repo = build(gateway = gateway, appEventBus = events, userScopedDataReset = reset)
        advanceUntilIdle()

        repo.signOut()
        assertIs<AuthState.Unauthenticated>(repo.current())
        // Even on a gateway failure the local dump + announcement still run.
        assertEquals(listOf("user-1"), reset.clearedFor)
        assertEquals(
            AppEvent.UserChanged(previous = "user-1", current = null),
            events.dispatched.last(),
        )
    }

    @Test
    fun signOut_offline_staysSignedOutAcrossALaterRetry() = runUnitTest {
        // The gateway clears the session locally even when the logout request
        // can't reach the server (RealSupabaseAuthGatewayTest), then throws.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = claimedSession(),
            onSignOut = {
                replaceSession(null)
                setStatus(AuthGatewayStatus.NotAuthenticated)
                throw RuntimeException("Unable to resolve host")
            },
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        repo.signOut()
        // The next foreground / connectivity edge re-resolves.
        val afterRetry = assertIs<AuthState.Unauthenticated>(repo.retry())

        assertNull(gateway.currentSession())
        assertEquals(
            AuthState.Unauthenticated.Reason.SignedOut,
            afterRetry.reason,
            "a re-resolve must not decay a sign-out into an ordinary missing session",
        )
    }

    @Test
    fun signOut_cancelledWhileTheLogoutRequestHangs_stillSignsOut() = runUnitTest {
        // The gateway drops the stored session even when cancelled
        // (RealSupabaseAuthGatewayTest), so the state has to follow it.
        val gateway = hangingSignOutGateway()
        val reset = RecordingUserScopedDataReset()
        val repo = build(gateway = gateway, userScopedDataReset = reset)
        advanceUntilIdle()

        val signingOut = launch { repo.signOut() }
        gateway.awaitSignOutStarted()
        signingOut.cancelAndJoin()

        val state = assertIs<AuthState.Unauthenticated>(repo.current())
        assertEquals(AuthState.Unauthenticated.Reason.SignedOut, state.reason)
        assertEquals(listOf("user-1"), reset.clearedFor, "the account's data leaves with its session")
    }

    // ---------- signup already-registered obfuscation (AUTH-21) ----------

    @Test
    fun signUp_obfuscatedAlreadyRegistered_mapsToEmailAlreadyRegistered_notVerification() = runUnitTest {
        // Supabase anti-enumeration returns a fake-success (no exception, a user
        // with an empty identities array, no session) for an already-registered
        // email. The gateway projects that to EmailAlreadyRegistered so the client
        // shows "already registered, sign in" instead of flashing the verify-email
        // screen and dropping the user into the app (AUTH-21).
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.NotAuthenticated,
            session = null,
            onSignUpWithEmail = { _, _ -> GatewaySignUpResult.EmailAlreadyRegistered },
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val outcome = repo.signUpWithEmail("dup@example.com", "password")
        assertIs<SignUpOutcome.EmailAlreadyRegistered>(outcome)
    }

    @Test
    fun signUp_newEmail_mapsToVerificationRequired_withServerEmail() = runUnitTest {
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.NotAuthenticated,
            session = null,
            onSignUpWithEmail = { _, _ -> GatewaySignUpResult.VerificationSent },
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val outcome = repo.signUpWithEmail("new@example.com", "password")
        assertEquals(SignUpOutcome.VerificationRequired("new@example.com"), outcome)
    }

    // ---------- signup / link timeout mapping (AUTH-20) ----------

    @Test
    fun signUp_requestTimeout_mapsToTimeout_notUnknown() = runUnitTest {
        // supabase-kt rethrows Ktor's HttpRequestTimeoutException raw (it's an
        // IOException, so Catching keeps it), and it's neither a RestException
        // nor supabase's HttpRequestException — so before AUTH-20 it fell through
        // to Unknown ("signup failed"). It must now surface as a retryable Timeout.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.NotAuthenticated,
            session = null,
            onSignUpWithEmail = { _, _ ->
                throw HttpRequestTimeoutException(url = "https://x/auth/v1/signup", timeoutMillis = 30_000L)
            },
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val outcome = repo.signUpWithEmail("new@example.com", "password")
        assertIs<SignUpOutcome.Timeout>(outcome)
    }

    @Test
    fun linkEmail_requestTimeout_mapsToTimeout_notUnknown() = runUnitTest {
        // Same raw-timeout case on the anonymous-guest claim path.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = anonymousSession(),
            onLinkEmailIdentity = { _, _ ->
                throw HttpRequestTimeoutException(url = "https://x/auth/v1/user", timeoutMillis = 30_000L)
            },
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()
        assertIs<AuthState.Authenticated>(repo.current())

        val outcome = repo.linkEmailIdentity("new@example.com", "password")
        assertIs<LinkEmailIdentityOutcome.Timeout>(outcome)
    }

    @Test
    fun aStoredSessionAwaitingRefresh_isNeverSignedOutAsUnrecoverable() = runUnitTest {
        // Offline launch after the token expired: supabase-kt reports
        // RefreshFailure and keeps retrying with the stored session.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.RefreshFailure(cause = null),
            session = null,
        )
        val repo = build(gateway = gateway)
        advanceUntilIdle()

        val state = assertIs<AuthState.Unauthenticated>(repo.current())
        assertEquals(AuthState.Unauthenticated.Reason.None, state.reason)

        repo.markSessionUnrecoverable(wasAnonymous = true)
        advanceUntilIdle()

        assertEquals(0, gateway.signOutCalls, "the stored session must survive")
        assertEquals(AuthState.Unauthenticated.Reason.None, (repo.current() as AuthState.Unauthenticated).reason)
    }

    // ---------- scaffolding ----------

    private fun build(
        gateway: FakeSupabaseAuthGateway,
        appEventBus: AppEventBus = NoOpEventBus,
        userScopedDataReset: UserScopedDataReset = NoOpUserScopedDataReset,
        sessionRejectionBus: com.opson.libraries.networking.SessionRejectionBus =
            FakeSessionRejectionBus(),
        profileApi: ProfileApi = UnusedProfileApi,
        expiredAccounts: ExpiredAccountStore = ExpiredAccountStore(TestCacheFactory()),
    ): SupabaseAuthRepositoryImpl = SupabaseAuthRepositoryImpl(
        gateway = gateway,
        profileApi = profileApi,
        appEventBus = appEventBus,
        userScopedDataReset = userScopedDataReset,
        tokenInvalidator = NoOpTokenInvalidator,
        sessionRejectionBus = sessionRejectionBus,
        expiredAccounts = expiredAccounts,
        appScope = AppCoroutineScope(dispatchers),
    )

    private object NoOpUserScopedDataReset : UserScopedDataReset {
        override suspend fun clearFor(previousUserId: String) = Unit
    }

    private class RecordingUserScopedDataReset : UserScopedDataReset {
        val clearedFor: MutableList<String> = mutableListOf()
        override suspend fun clearFor(previousUserId: String) {
            clearedFor += previousUserId
        }
    }

    private object NoOpTokenInvalidator :
        com.opson.libraries.networking.AuthTokenInvalidator {
        override fun invalidate() = Unit
    }

    private fun anonymousSession(
        userId: String = "anon-user",
        accessToken: String = "tok-anon",
    ): GatewaySession = GatewaySession(
        userId = userId,
        email = null,
        accessToken = accessToken,
        isAnonymous = true,
        isEmailConfirmed = false,
    )

    private fun claimedSession(
        userId: String = "user-1",
        email: String = "a@b.com",
        accessToken: String = "tok-claimed",
    ): GatewaySession = GatewaySession(
        userId = userId,
        email = email,
        accessToken = accessToken,
        isAnonymous = false,
        isEmailConfirmed = true,
    )

    private object NoOpEventBus : AppEventBus {
        override fun dispatch(event: AppEvent) = Unit
        override fun eventStream(): Flow<AppEvent> = emptyFlow()
        override fun liveEventStream(): Flow<AppEvent> = emptyFlow()
    }

    private class RecordingEventBus : AppEventBus {
        val dispatched: MutableList<AppEvent> = mutableListOf()
        override fun dispatch(event: AppEvent) {
            dispatched += event
        }
        override fun eventStream(): Flow<AppEvent> = emptyFlow()
        override fun liveEventStream(): Flow<AppEvent> = emptyFlow()
    }

    private object UnusedProfileApi : ProfileApi {
        override suspend fun me(): MeDto = error("ProfileApi not used by these tests")
        override suspend fun patchMe(request: PatchMeRequest): MeDto =
            error("ProfileApi not used by these tests")
        override suspend fun deleteMe() =
            error("ProfileApi not used by these tests")
        override suspend fun sendAppleAuthorization(authorizationCode: String) =
            error("ProfileApi not used by these tests")
    }

    private class DeletingProfileApi(private val failure: Throwable?) : ProfileApi {
        override suspend fun me(): MeDto = error("unused")
        override suspend fun patchMe(request: PatchMeRequest): MeDto = error("unused")
        override suspend fun deleteMe() {
            failure?.let { throw it }
        }
        override suspend fun sendAppleAuthorization(authorizationCode: String) = error("unused")
    }

    private class RecordingProfileApi(private val failSend: Boolean = false) : ProfileApi {
        val sentCodes = mutableListOf<String>()
        override suspend fun me(): MeDto = error("unused")
        override suspend fun patchMe(request: PatchMeRequest): MeDto = error("unused")
        override suspend fun deleteMe() = error("unused")
        override suspend fun sendAppleAuthorization(authorizationCode: String) {
            sentCodes += authorizationCode
            if (failSend) throw RuntimeException("503 apple_not_configured")
        }
    }
}

/**
 * In-memory [SupabaseAuthGateway] for unit tests. Tracks call counts +
 * lets the test drive every method outcome via small callbacks. Default
 * mutations (`onSignOut`, `onRefreshSession`)
 * succeed silently and leave session state untouched — overrides set
 * `currentStatus` / `currentSession` as needed.
 *
 * [statusSequence] lets a test simulate a status that flips across
 * resolve passes (e.g. Initializing → Authenticated). If null, every
 * read returns [initialStatus] (which mutations may overwrite).
 */
internal class FakeSupabaseAuthGateway(
    initialStatus: AuthGatewayStatus,
    session: GatewaySession?,
    statusSequence: List<AuthGatewayStatus>? = null,
    var onAwaitInitialization: suspend FakeSupabaseAuthGateway.() -> Unit = {},
    var onSignOut: suspend FakeSupabaseAuthGateway.() -> Unit = {},
    var onRefreshSession: suspend FakeSupabaseAuthGateway.() -> Unit = {},
    var onSignInWithOAuth: suspend FakeSupabaseAuthGateway.(OAuthProvider) -> Unit = {},
    var onLinkOAuthIdentity: suspend FakeSupabaseAuthGateway.(OAuthProvider) -> Unit = {},
    var onCompleteOAuthRedirect: suspend FakeSupabaseAuthGateway.(String) -> Unit = {
        error("completeOAuthRedirect not stubbed for this test")
    },
    var onHydrateCurrentUser: suspend FakeSupabaseAuthGateway.() -> Unit = {
        error("hydrateCurrentUser not stubbed for this test")
    },
    var onSignUpWithEmail: suspend FakeSupabaseAuthGateway.(String, String) -> GatewaySignUpResult = { _, _ ->
        error("signUpWithEmail not stubbed for this test")
    },
    var onLinkEmailIdentity: suspend FakeSupabaseAuthGateway.(String, String) -> Unit = { _, _ ->
        error("linkEmailIdentity not stubbed for this test")
    },
    var onSignInWithAppleIdToken: suspend FakeSupabaseAuthGateway.(String, String) -> Unit = { _, _ ->
        error("signInWithAppleIdToken not stubbed for these tests")
    },
) : SupabaseAuthGateway {

    private var status: AuthGatewayStatus = initialStatus
    private var session: GatewaySession? = session
    private val statusIterator: Iterator<AuthGatewayStatus>? = statusSequence?.iterator()

    var statusReads: Int = 0
        private set
    var refreshSessionCalls: Int = 0
        private set
    var signOutCalls: Int = 0
        private set
    val signOutStarted = CompletableDeferred<Unit>()

    suspend fun awaitSignOutStarted() = signOutStarted.await()
    var signInWithOAuthCalls: Int = 0
        private set
    var linkOAuthIdentityCalls: Int = 0
        private set
    var completeOAuthRedirectCalls: Int = 0
        private set
    var hydrateCurrentUserCalls: Int = 0
        private set

    /**
     * Flip the gateway to Authenticated with the given session. Use from
     * inside a stubbed sign-in to model "supabase-kt's status moves to
     * Authenticated after the sign-in resolves".
     */
    fun advanceToAuthenticated(session: GatewaySession) {
        status = AuthGatewayStatus.Authenticated
        this.session = session
    }

    fun replaceSession(session: GatewaySession?) {
        this.session = session
    }

    /**
     * Force [currentStatus] to start returning [next] on subsequent reads.
     * Used to model a gateway whose status flipped out of band (e.g. signOut
     * cleared the session — next status read should be NotAuthenticated).
     */
    fun setStatus(next: AuthGatewayStatus) {
        status = next
    }

    override suspend fun awaitInitialization() {
        onAwaitInitialization()
    }

    override fun currentStatus(): AuthGatewayStatus {
        statusReads++
        // If the test supplied a per-poll sequence, drain that first.
        return statusIterator?.takeIf { it.hasNext() }?.next() ?: status
    }

    override fun currentSession(): GatewaySession? = session

    override suspend fun refreshSession() {
        refreshSessionCalls++
        onRefreshSession()
    }

    override suspend fun signInWithEmail(email: String, password: String): Unit =
        error("signInWithEmail not stubbed for these tests")

    override suspend fun signUpWithEmail(email: String, password: String): GatewaySignUpResult =
        onSignUpWithEmail(email, password)

    override suspend fun resendVerificationEmail(email: String): Unit =
        error("resendVerificationEmail not stubbed for these tests")

    override suspend fun resetPasswordForEmail(email: String): Unit =
        error("resetPasswordForEmail not stubbed for these tests")

    override suspend fun signOut() {
        signOutCalls++
        onSignOut()
    }

    override suspend fun linkOAuthIdentity(provider: OAuthProvider) {
        linkOAuthIdentityCalls++
        onLinkOAuthIdentity(provider)
    }

    override suspend fun signInWithOAuth(provider: OAuthProvider) {
        signInWithOAuthCalls++
        onSignInWithOAuth(provider)
    }

    override suspend fun completeOAuthRedirect(url: String) {
        completeOAuthRedirectCalls++
        onCompleteOAuthRedirect(url)
    }

    override suspend fun hydrateCurrentUser() {
        hydrateCurrentUserCalls++
        onHydrateCurrentUser()
    }

    override fun isOAuthRedirect(url: String): Boolean =
        url.startsWith("opson://login-callback")

    override suspend fun signInWithAppleIdToken(idToken: String, nonce: String) {
        onSignInWithAppleIdToken(idToken, nonce)
    }

    override suspend fun linkAppleIdToken(idToken: String, nonce: String): Unit =
        error("linkAppleIdToken not stubbed for these tests")

    override suspend fun signInWithGoogleIdToken(idToken: String, nonce: String?): Unit =
        error("signInWithGoogleIdToken not stubbed for these tests")

    override suspend fun linkEmailIdentity(email: String, password: String) {
        onLinkEmailIdentity(email, password)
    }
}
