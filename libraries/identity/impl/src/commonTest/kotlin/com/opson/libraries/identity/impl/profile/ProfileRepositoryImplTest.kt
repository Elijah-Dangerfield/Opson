package com.opson.libraries.identity.impl.profile

import app.cash.turbine.test
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.DeleteAccountOutcome
import com.opson.libraries.identity.auth.LinkEmailIdentityOutcome
import com.opson.libraries.identity.auth.LinkIdentityOutcome
import com.opson.libraries.identity.auth.OAuthProvider
import com.opson.libraries.identity.auth.RefreshOutcome
import com.opson.libraries.identity.auth.ResendOutcome
import com.opson.libraries.identity.auth.SendResetOutcome
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.identity.auth.SignUpOutcome
import com.opson.libraries.identity.impl.MeDto
import com.opson.libraries.identity.impl.PatchMeRequest
import com.opson.libraries.identity.impl.ProfileApi
import com.opson.libraries.identity.impl.auth.PendingGuestAccountStore
import com.opson.libraries.identity.profile.Profile
import com.opson.libraries.identity.profile.ProfileEditRejection
import com.opson.libraries.identity.profile.UpdateProfileOutcome
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.CacheJsonSerializer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the resolve / update contracts for
 * [ProfileRepositoryImpl]. The interesting edges:
 *
 *  - Authenticated → /v1/me success emits Authenticated AND writes cache;
 *    failure falls back to cached profile, then to Profile.Fallback with
 *    a generated localId.
 *  - Unauthenticated reads the cache; if no cached profile, generates +
 *    persists a localId so subsequent fallbacks are stable.
 *  - update() is optimistic: emits the prospective profile immediately,
 *    rolls back on failure, and maps HTTP status codes to outcomes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryImplTest : CoroutineTest() {

    // ---------- resolve from auth ----------

    @Test
    fun authenticated_andServerSucceeds_emitsAuthenticated_andCachesIt() = runUnitTest {
        val auth = FakeAuthRepository()
        val api = FakeProfileApi(meResult = Result.success(SAMPLE_ME))
        val cache = newProfileCache()
        val repo = build(auth, api, cache)

        auth.emit(AuthState.Authenticated(userId = "u1", isAnonymous = false, email = "a@b.com"))
        advanceUntilIdle()

        val profile = assertIs<Profile.Authenticated>(repo.observe().first())
        assertEquals("u1", profile.id)
        assertEquals("Alice", profile.displayName)
        assertEquals("a@b.com", profile.email, "email surfaced from AuthState, not from /v1/me")

        // Cache was written so a subsequent failure can fall back to it.
        val cached = cache.readAuthenticated()
        assertNotNull(cached)
        assertEquals("u1", cached.id)
    }

    @Test
    fun resolveIsNewAccount_serverReportsNew_latchesTrue_evenAfterServerGoesOneShotFalse() = runUnitTest {
        val auth = FakeAuthRepository()
        val api = FakeProfileApi(meResult = Result.success(SAMPLE_ME.copy(isNewAccount = true)))
        val repo = build(auth, api, newProfileCache())

        // Auth change auto-hydrates and latches the one-shot server signal.
        auth.emit(AuthState.Authenticated(userId = "u1", isAnonymous = false, email = "a@b.com"))
        advanceUntilIdle()
        // Model the server's one-shot: a subsequent /v1/me reports NOT new. The
        // latch must hold true regardless — a racing hydrate can't erase it.
        api.meResult = Result.success(SAMPLE_ME.copy(isNewAccount = false))

        assertTrue(repo.resolveIsNewAccount(), "latched new-account signal survives the racing hydrate")
        assertTrue(repo.resolveIsNewAccount(), "non-consuming — the Home welcome observes the same latch")
    }

    @Test
    fun accountJustCreated_reset_onSignOut() = runUnitTest {
        val auth = FakeAuthRepository()
        val api = FakeProfileApi(meResult = Result.success(SAMPLE_ME.copy(isNewAccount = true)))
        val repo = build(auth, api, newProfileCache())
        auth.emit(AuthState.Authenticated(userId = "u1", isAnonymous = false, email = "a@b.com"))
        advanceUntilIdle()
        assertTrue(repo.observeAccountJustCreated().first())

        // Sign out — the latch must clear so it can't leak into the next account.
        auth.emit(AuthState.Unauthenticated())
        advanceUntilIdle()

        assertFalse(repo.observeAccountJustCreated().first())
    }

    @Test
    fun resolveIsNewAccount_returningAccount_returnsFalse() = runUnitTest {
        val auth = FakeAuthRepository()
        val api = FakeProfileApi(meResult = Result.success(SAMPLE_ME.copy(isNewAccount = false)))
        val repo = build(auth, api, newProfileCache())

        auth.emit(AuthState.Authenticated(userId = "u1", isAnonymous = false, email = "a@b.com"))
        advanceUntilIdle()

        assertFalse(repo.resolveIsNewAccount())
    }

    @Test
    fun resolveIsNewAccount_unauthenticated_returnsFalse() = runUnitTest {
        val auth = FakeAuthRepository().also { it.emit(AuthState.Unauthenticated()) }
        val api = FakeProfileApi(meResult = Result.success(SAMPLE_ME.copy(isNewAccount = true)))
        val repo = build(auth, api, newProfileCache())
        advanceUntilIdle()

        assertFalse(repo.resolveIsNewAccount(), "no session → treat as returning, never trap in onboarding")
    }

    @Test
    fun authenticated_andServerFails_fallsBackToCachedProfile() = runUnitTest {
        // Pre-seed cache with a previously-real profile.
        val cache = newProfileCache()
        cache.writeAuthenticated(SAMPLE_CACHED_PROFILE)

        val auth = FakeAuthRepository()
        val api = FakeProfileApi(meResult = Result.failure(RuntimeException("server down")))
        val repo = build(auth, api, cache)

        auth.emit(AuthState.Authenticated(userId = "u-new", isAnonymous = true, email = null))
        advanceUntilIdle()

        val profile = assertIs<Profile.Authenticated>(repo.observe().first())
        assertEquals(SAMPLE_CACHED_PROFILE.id, profile.id, "fell back to cached, not the live auth userId")
    }

    @Test
    fun authenticated_andServerFails_withNoCache_emitsFallbackWithLocalId() = runUnitTest {
        val cache = newProfileCache()
        val auth = FakeAuthRepository()
        val api = FakeProfileApi(meResult = Result.failure(RuntimeException("server down")))
        val repo = build(auth, api, cache)

        auth.emit(AuthState.Authenticated(userId = "u1", isAnonymous = true, email = null))
        advanceUntilIdle()

        val profile = assertIs<Profile.Fallback>(repo.observe().first())
        // localId persisted so subsequent fallbacks reuse it.
        assertEquals(profile.id, cache.readLocalId())
    }

    @Test
    fun unauthenticated_withPendingOnboardingChoice_enrichesFallbackIdentity() = runUnitTest {
        // A user who onboarded offline has their chosen name held in the
        // pending guest-account store. The Fallback surfaces it so the app shows
        // their choice instead of a generic "You" while session-less.
        val pendingStore = PendingGuestAccountStore(InMemoryCacheFactory)
        pendingStore.set(
            com.opson.libraries.identity.auth.PendingIdentity(
                displayName = "Foxy",
            ),
        )
        val auth = FakeAuthRepository()
        val repo = build(auth, FakeProfileApi(), pendingGuestAccountStore = pendingStore)

        auth.emit(AuthState.Unauthenticated())
        advanceUntilIdle()

        val fallback = assertIs<Profile.Fallback>(repo.observe().first())
        assertEquals("Foxy", fallback.displayName)
    }

    @Test
    fun unauthenticated_withNoCache_emitsFallback_andPersistsLocalId() = runUnitTest {
        val cache = newProfileCache()
        val auth = FakeAuthRepository()
        val api = FakeProfileApi()
        val repo = build(auth, api, cache)

        auth.emit(AuthState.Unauthenticated())
        advanceUntilIdle()

        val first = assertIs<Profile.Fallback>(repo.observe().first())
        val persistedId = cache.readLocalId()
        assertEquals(first.id, persistedId)

        // Second Unauthenticated should reuse the same localId, not mint a new one.
        auth.emit(AuthState.Unauthenticated(cause = RuntimeException("offline")))
        advanceUntilIdle()
        val second = assertIs<Profile.Fallback>(repo.observe().first())
        assertEquals(first.id, second.id, "fallback id must be stable across auth re-emissions")
    }

    @Test
    fun userScopedProfileCacheCleaner_clearsCachedProfile() = runUnitTest {
        // Stale cache from the previous account must not survive a user change,
        // or the next account would briefly resolve to it before /v1/me lands.
        val cache = newProfileCache()
        cache.writeAuthenticated(SAMPLE_CACHED_PROFILE)
        assertNotNull(cache.readAuthenticated())
        val cleaner = UserScopedProfileCacheCleaner(
            profileCache = cache,
        )

        cleaner.clear(previousUserId = SAMPLE_CACHED_PROFILE.id)

        assertNull(cache.readAuthenticated(), "user change must drop the cached profile")
    }

    @Test
    fun unauthenticated_butCachedProfileExists_emitsCachedAuthenticated() = runUnitTest {
        // Offline returning user: auth resolution failed but we have a
        // previously-cached real profile. The user sees their cached state
        // until auth comes back.
        val cache = newProfileCache()
        cache.writeAuthenticated(SAMPLE_CACHED_PROFILE)

        val auth = FakeAuthRepository()
        val api = FakeProfileApi()
        val repo = build(auth, api, cache)

        auth.emit(AuthState.Unauthenticated(cause = RuntimeException("offline")))
        advanceUntilIdle()

        val profile = assertIs<Profile.Authenticated>(repo.observe().first())
        assertEquals(SAMPLE_CACHED_PROFILE.id, profile.id)
    }

    @Test
    fun sessionExpired_dropsTheGhost_emitsFallback_andClearsStaleCache() = runUnitTest {
        // A server-confirmed dead session (token rejected) — unlike a benign
        // offline blip, the cached profile here is a ghost. Surfacing it as
        // Authenticated is exactly what makes the app keep firing authed calls
        // that all 401. We must drop to Fallback and clear the stale cache.
        val cache = newProfileCache()
        cache.writeAuthenticated(SAMPLE_CACHED_PROFILE)

        val auth = FakeAuthRepository()
        val repo = build(auth, FakeProfileApi(), cache)

        auth.emit(
            AuthState.Unauthenticated(
                reason = AuthState.Unauthenticated.Reason.SessionExpired,
                wasAnonymous = true,
            ),
        )
        advanceUntilIdle()

        assertIs<Profile.Fallback>(repo.observe().first())
        assertNull(cache.readAuthenticated(), "the stale cached profile must be cleared")
    }

    @Test
    fun authToAnonToClaimed_reEmitsProfile_perAuthChange() = runUnitTest {
        // The impl reads `isAnonymous` from /v1/me (the server is the
        // authoritative source for the JWT's is_anonymous claim), not
        // from the local AuthState. So the test drives both layers
        // together to exercise a real anon→claimed transition.
        val auth = FakeAuthRepository()
        val api = FakeProfileApi(meResult = Result.success(SAMPLE_ME.copy(isAnonymous = true)))
        val repo = build(auth, api)

        auth.emit(AuthState.Authenticated(userId = "u1", isAnonymous = true, email = null))
        advanceUntilIdle()
        val first = assertIs<Profile.Authenticated>(repo.observe().first())
        assertEquals(true, first.isAnonymous)

        // Claim happened — server now returns the claimed profile shape;
        // auth re-emits Authenticated with isAnonymous=false.
        api.meResult = Result.success(SAMPLE_ME.copy(isAnonymous = false))
        auth.emit(AuthState.Authenticated(userId = "u1", isAnonymous = false, email = "a@b.com"))
        advanceUntilIdle()
        val second = assertIs<Profile.Authenticated>(repo.observe().first())
        assertEquals(false, second.isAnonymous)
        assertEquals("a@b.com", second.email)
    }

    // ---------- update ----------

    @Test
    fun update_sessionless_withCachedRealAccount_queuesOptimisticallyAndOutbox() = runUnitTest {
        // A real (claimed) account that's merely offline: the edit applies
        // optimistically and is queued in the profile-edit outbox to PATCH when
        // a session returns — NOT routed to the guest-identity path.
        val cache = newProfileCache()
        cache.writeAuthenticated(
            Profile.Authenticated(
                id = "u1",
                displayName = "Real",
                email = "a@b.com",
                isAnonymous = false,
                createdAt = kotlin.time.Instant.fromEpochMilliseconds(0),
            ),
        )
        val editStore = PendingProfileEditStore(InMemoryCacheFactory)
        val auth = FakeAuthRepository().also { it.emit(AuthState.Unauthenticated()) }
        val repo = build(auth, FakeProfileApi(), cache, pendingProfileEditStore = editStore)
        advanceUntilIdle()

        val outcome = repo.update(displayName = "NewName")
        assertIs<UpdateProfileOutcome.Queued>(outcome)
        // Optimistic emit shows the new name immediately.
        assertEquals("NewName", (repo.observe().first() as Profile.Authenticated).displayName)
        // Queued in the outbox for the flush.
        assertEquals("NewName", editStore.read()?.displayName)
    }

    @Test
    fun queuedEdit_flushesOnAuthReturn_thenClearsOutbox() = runUnitTest {
        // Edit offline → queued. When auth returns, resolve GETs server truth and
        // PATCHes the queued edit on top, emitting the confirmed profile.
        val cache = newProfileCache()
        cache.writeAuthenticated(SAMPLE_CACHED_PROFILE)
        val editStore = PendingProfileEditStore(InMemoryCacheFactory)
        val api = FakeProfileApi(
            meResult = Result.success(SAMPLE_ME),
            patchResult = Result.success(SAMPLE_ME.copy(displayName = "Renamed")),
        )
        val auth = FakeAuthRepository().also { it.emit(AuthState.Unauthenticated()) }
        val repo = build(auth, api, cache, pendingProfileEditStore = editStore)
        advanceUntilIdle()

        assertIs<UpdateProfileOutcome.Queued>(repo.update(displayName = "Renamed"))
        assertEquals("Renamed", editStore.read()?.displayName)

        // Session returns.
        auth.emit(AuthState.Authenticated(userId = "u1", isAnonymous = false, email = "a@b.com"))
        advanceUntilIdle()

        assertEquals("Renamed", api.lastPatchRequest?.displayName, "flush PATCHed the queued edit")
        assertEquals("Renamed", (repo.observe().first() as Profile.Authenticated).displayName)
        assertNull(editStore.read(), "outbox cleared after a confirmed flush")
    }

    @Test
    fun queuedEdit_flushRejection_revertsAndEmitsRejection() = runUnitTest {
        // The name got taken while offline. On flush the server 409s: the queue
        // clears (it can't succeed), the value reverts to server truth, and a
        // rejection is surfaced for the UI to show "couldn't save your name."
        val cache = newProfileCache()
        cache.writeAuthenticated(SAMPLE_CACHED_PROFILE)
        val editStore = PendingProfileEditStore(InMemoryCacheFactory)
        val api = FakeProfileApi(
            meResult = Result.success(SAMPLE_ME), // server truth: "Alice"
            patchResult = Result.failure(clientResponseException(HttpStatusCode.Conflict)),
        )
        val auth = FakeAuthRepository().also { it.emit(AuthState.Unauthenticated()) }
        val repo = build(auth, api, cache, pendingProfileEditStore = editStore)
        advanceUntilIdle()
        repo.update(displayName = "Taken")
        advanceUntilIdle()

        repo.observeEditRejections().test {
            auth.emit(AuthState.Authenticated(userId = "u1", isAnonymous = false, email = "a@b.com"))
            assertEquals(ProfileEditRejection.DisplayNameTaken, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(editStore.read(), "a rejected edit is dropped, not retried forever")
        assertEquals("Alice", (repo.observe().first() as Profile.Authenticated).displayName, "reverted to server truth")
    }

    @Test
    fun update_authed_transientNetworkFailure_queuesInsteadOfRollingBack() = runUnitTest {
        // Online but the PATCH itself failed (flaky network): keep the optimistic
        // value and queue it, rather than losing the edit.
        val cache = newProfileCache()
        val editStore = PendingProfileEditStore(InMemoryCacheFactory)
        val api = FakeProfileApi(
            meResult = Result.success(SAMPLE_ME),
            patchResult = Result.failure(RuntimeException("network down")),
        )
        val auth = FakeAuthRepository().also {
            it.emit(AuthState.Authenticated(userId = "u1", isAnonymous = false, email = "a@b.com"))
        }
        val repo = build(auth, api, cache, pendingProfileEditStore = editStore)
        advanceUntilIdle()

        val outcome = repo.update(displayName = "Renamed")
        assertIs<UpdateProfileOutcome.Queued>(outcome)
        assertEquals("Renamed", (repo.observe().first() as Profile.Authenticated).displayName, "optimistic value kept")
        assertEquals("Renamed", editStore.read()?.displayName, "queued for retry")
    }

    @Test
    fun update_sessionless_fallback_queuesEditIntoPendingGuestIdentity() = runUnitTest {
        // The offline-onboarded (Fallback, no cached account) case: an edit is
        // applied locally + queued into the owed guest-account record so it
        // shows now and syncs when a session is minted.
        val pendingStore = PendingGuestAccountStore(InMemoryCacheFactory)
        val auth = FakeAuthRepository().also { it.emit(AuthState.Unauthenticated()) }
        val repo = build(auth, FakeProfileApi(), pendingGuestAccountStore = pendingStore)
        advanceUntilIdle()

        val outcome = repo.update(displayName = "Foxy")
        assertIs<UpdateProfileOutcome.Queued>(outcome)
        // Persisted for the mint to apply.
        val pending = pendingStore.read()
        assertEquals("Foxy", pending?.displayName)
        // Surfaced optimistically on the Fallback.
        val emitted = assertIs<Profile.Fallback>(repo.observe().first())
        assertEquals("Foxy", emitted.displayName)
    }

    @Test
    fun update_success_emitsServerProfile() = runUnitTest {
        val auth = FakeAuthRepository().also {
            it.emit(AuthState.Authenticated(userId = "u1", isAnonymous = false, email = "a@b.com"))
        }
        val api = FakeProfileApi(
            meResult = Result.success(SAMPLE_ME),
            patchResult = Result.success(SAMPLE_ME.copy(displayName = "Renamed")),
        )
        val cache = newProfileCache()
        val repo = build(auth, api, cache)
        advanceUntilIdle()

        val outcome = repo.update(displayName = "Renamed")
        val success = assertIs<UpdateProfileOutcome.Success>(outcome)
        assertEquals("Renamed", success.profile.displayName)
        assertEquals("a@b.com", success.profile.email, "email kept from AuthState across the patch round-trip")

        // Cache updated to the server-confirmed profile.
        assertEquals("Renamed", cache.readAuthenticated()?.displayName)
    }

    @Test
    fun update_409_returnsDisplayNameTaken_andRollsBackCache() = runUnitTest {
        val conflict = clientResponseException(HttpStatusCode.Conflict)
        val auth = FakeAuthRepository().also {
            it.emit(AuthState.Authenticated(userId = "u1", isAnonymous = false, email = "a@b.com"))
        }
        val api = FakeProfileApi(
            meResult = Result.success(SAMPLE_ME),
            patchResult = Result.failure(conflict),
        )
        val cache = newProfileCache()
        val repo = build(auth, api, cache)
        advanceUntilIdle()

        val priorName = (repo.observe().first() as Profile.Authenticated).displayName
        val outcome = repo.update(displayName = "Taken")
        assertIs<UpdateProfileOutcome.DisplayNameTaken>(outcome)

        // Cache was rolled back to the prior profile.
        assertEquals(priorName, cache.readAuthenticated()?.displayName)
        assertEquals(priorName, (repo.observe().first() as Profile.Authenticated).displayName)
    }

    @Test
    fun update_400_withDisplayNameInPatch_returnsInvalidDisplayName() = runUnitTest {
        val badRequest = clientResponseException(HttpStatusCode.BadRequest)
        val auth = FakeAuthRepository().also {
            it.emit(AuthState.Authenticated(userId = "u1", isAnonymous = false, email = null))
        }
        val api = FakeProfileApi(
            meResult = Result.success(SAMPLE_ME),
            patchResult = Result.failure(badRequest),
        )
        val repo = build(auth, api)
        advanceUntilIdle()

        val outcome = repo.update(displayName = "!!!")
        assertIs<UpdateProfileOutcome.InvalidDisplayName>(outcome)
    }

    @Test
    fun update_genericNetworkFailure_queuesForRetry() = runUnitTest {
        // Offline-first: a transient network failure on the PATCH no longer
        // discards the edit — it queues for flush on the next reconnect.
        val editStore = PendingProfileEditStore(InMemoryCacheFactory)
        val auth = FakeAuthRepository().also {
            it.emit(AuthState.Authenticated(userId = "u1", isAnonymous = false, email = null))
        }
        val api = FakeProfileApi(
            meResult = Result.success(SAMPLE_ME),
            patchResult = Result.failure(RuntimeException("no network")),
        )
        val repo = build(auth, api, pendingProfileEditStore = editStore)
        advanceUntilIdle()

        val outcome = repo.update(displayName = "Whatever")
        assertIs<UpdateProfileOutcome.Queued>(outcome)
        assertEquals("Whatever", editStore.read()?.displayName)
    }

    // ---------- scaffolding ----------

    private fun build(
        auth: FakeAuthRepository,
        api: FakeProfileApi,
        cache: ProfileCache = newProfileCache(),
        pendingGuestAccountStore: PendingGuestAccountStore = PendingGuestAccountStore(InMemoryCacheFactory),
        pendingProfileEditStore: PendingProfileEditStore = PendingProfileEditStore(InMemoryCacheFactory),
    ): ProfileRepositoryImpl = ProfileRepositoryImpl(
        authRepository = auth,
        profileApi = api,
        profileCache = cache,
        pendingGuestAccountStore = pendingGuestAccountStore,
        pendingProfileEditStore = pendingProfileEditStore,
        appScope = AppCoroutineScope(dispatchers),
    )

    private fun newProfileCache(): ProfileCache = ProfileCache(InMemoryCacheFactory)

    /**
     * Fake [CacheFactory] that backs every persistent cache with an
     * in-memory MutableStateFlow. The serializer is ignored — tests
     * don't need wire-level encoding. Sufficient for [ProfileCache]'s
     * read/write contract.
     */
    private object InMemoryCacheFactory : CacheFactory {
        override fun <T : Any> inMemory(defaultValue: () -> T): Cache<T> =
            FakeCache(defaultValue)

        override fun <T : Any> persistent(
            name: String,
            serializer: CacheJsonSerializer<T>,
            loadEagerly: Boolean,
        ): Cache<T> {
            // serializer.read(null) returns the default value per the
            // VersionedCacheJsonSerializer contract.
            return FakeCache {
                kotlinx.coroutines.runBlocking { serializer.read(null) }
            }
        }
    }

    private class FakeCache<T : Any>(private val initial: () -> T) : Cache<T> {
        private val state = MutableStateFlow(initial())
        override val updates: Flow<T> = state
        override suspend fun get(): T = state.value
        override suspend fun set(value: T) { state.value = value }
        override suspend fun clear() { state.value = initial() }
    }

    /** Stubbed [AuthRepository] that emits [AuthState] through a replay=1 shared flow. */
    private class FakeAuthRepository : AuthRepository {
        private val state = MutableSharedFlow<AuthState>(replay = 1, extraBufferCapacity = 8)

        fun emit(value: AuthState) {
            state.tryEmit(value)
        }

        override suspend fun current(): AuthState = state.first()
        override fun observe(): Flow<AuthState> = state
        override suspend fun retry(): AuthState = state.first()
        override suspend fun signInWithEmail(email: String, password: String): SignInOutcome =
            error("unused")
        override suspend fun signUpWithEmail(email: String, password: String): SignUpOutcome =
            error("unused")
        override suspend fun refreshSession(): RefreshOutcome = error("unused")
        override suspend fun resendVerificationEmail(email: String): ResendOutcome = error("unused")
        override suspend fun sendPasswordResetEmail(email: String): SendResetOutcome = error("unused")
        override suspend fun signOut() = Unit
        override suspend fun deleteAccount(): DeleteAccountOutcome = error("unused")
        override suspend fun linkOAuthIdentity(provider: OAuthProvider): LinkIdentityOutcome =
            error("unused")
        override suspend fun linkEmailIdentity(email: String, password: String): LinkEmailIdentityOutcome =
            error("unused")
        override suspend fun signInWithOAuth(provider: OAuthProvider): SignInOutcome =
            error("unused")
    }

    private class FakeProfileApi(
        var meResult: Result<MeDto> = Result.failure(RuntimeException("not stubbed")),
        var patchResult: Result<MeDto> = Result.failure(RuntimeException("not stubbed")),
    ) : ProfileApi {
        var lastPatchRequest: PatchMeRequest? = null
            private set

        override suspend fun me(): MeDto = meResult.getOrThrow()

        override suspend fun patchMe(request: PatchMeRequest): MeDto {
            lastPatchRequest = request
            return patchResult.getOrThrow()
        }

        override suspend fun deleteMe(): HttpResponse = error("deleteMe not used by ProfileRepository")

        override suspend fun sendAppleAuthorization(authorizationCode: String) =
            error("sendAppleAuthorization not used by ProfileRepository")
    }

    /**
     * Build a real [ClientRequestException] for a given status. The error
     * mapper in the impl branches purely on `e.response.status.value`, so
     * we just need an exception of the right shape — we trigger it by
     * routing a request through a [MockEngine] that responds with the
     * desired status. `expectSuccess = true` makes Ktor throw on 4xx,
     * which is what production HttpClient configuration does too.
     */
    private suspend fun clientResponseException(status: HttpStatusCode): ClientRequestException {
        val mock = HttpClient(MockEngine { respond(content = ByteReadChannel("{}"), status = status) }) {
            expectSuccess = true
        }
        return try {
            mock.get("/")
            error("expected ClientRequestException for status $status")
        } catch (e: ClientRequestException) {
            e
        } finally {
            mock.close()
        }
    }

    private companion object {
        val SAMPLE_ME = MeDto(
            userId = "u1",
            displayName = "Alice",
            isAnonymous = false,
            createdAtEpochMs = 0L,
        )

        val SAMPLE_CACHED_PROFILE = Profile.Authenticated(
            id = "cached-u",
            displayName = "Cached",
            email = "old@b.com",
            isAnonymous = false,
            createdAt = kotlin.time.Instant.fromEpochMilliseconds(0),
        )
    }
}
