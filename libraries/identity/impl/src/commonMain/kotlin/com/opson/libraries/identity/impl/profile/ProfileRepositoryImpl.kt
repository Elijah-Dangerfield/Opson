package com.opson.libraries.identity.impl.profile

import com.opson.libraries.core.AutoInit
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.PendingIdentity
import com.opson.libraries.identity.impl.auth.PendingGuestAccountStore
import com.opson.libraries.identity.impl.MeDto
import com.opson.libraries.identity.impl.PatchMeRequest
import com.opson.libraries.identity.impl.ProfileApi
import com.opson.libraries.identity.profile.Profile
import com.opson.libraries.identity.profile.ProfileRepository
import com.opson.libraries.identity.profile.ProfileEditRejection
import com.opson.libraries.identity.profile.UpdateProfileOutcome
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Backs [ProfileRepository] on top of [AuthRepository] + `/v1/me`
 * (the per-user profile). Despite the historical "Supabase" prefix on
 * prior iterations, this class never talks to Supabase directly — all
 * data comes from our own backend. [AuthRepository] / [SupabaseAuthGateway]
 * own the supabase-kt session and only feed [AuthState] in here.
 *
 * **Profile resolve** (the user-specific bit):
 *
 *  - On init, [appScope] launches a collector on [AuthRepository.observe].
 *    Every auth state change triggers a resolve. The first resolve
 *    completes the initial resolve; subsequent ones cover sign-in,
 *    sign-out, account delete, etc.
 *  - Resolve:
 *      - [AuthState.Authenticated] → `/v1/me` get-or-create →
 *        [Profile.Authenticated], cached.
 *      - [AuthState.Unauthenticated] → cache fallback. If cached
 *        profile exists, emit it (the supabase-kt session may still
 *        be valid for some calls). Otherwise emit
 *        [Profile.Fallback] keyed on a stable client UUID from cache.
 *      - Network error during the `/v1/me` call → same cache fallback
 *        path. Cache as fallback, not first-frame.
 *  - Profile flow has no in-flight sentinel. [current] suspends until
 *    the first resolved emission; [observe] only emits resolved values.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ProfileRepository::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class ProfileRepositoryImpl(
    private val authRepository: AuthRepository,
    private val profileApi: ProfileApi,
    private val profileCache: ProfileCache,
    private val pendingGuestAccountStore: PendingGuestAccountStore,
    private val pendingProfileEditStore: PendingProfileEditStore,
    private val appScope: AppCoroutineScope,
) : ProfileRepository, AutoInit {

    private val logger = KLog.withTag("ProfileRepository")
    private val mutex = Mutex()
    private val _state = MutableSharedFlow<Profile>(replay = 1)
    private val sharedState: Flow<Profile> = _state.asSharedFlow()

    /**
     * Latch for the server's one-shot `isNewAccount` flag (see
     * [resolveIsNewAccount]). Set true when a `/v1/me` hydrate reports a
     * brand-new account, keyed to [newAccountLatchUserId] so it can't leak
     * across an account switch. Consumed (reset) by [resolveIsNewAccount].
     */
    private val _accountJustCreated = MutableStateFlow(false)
    private var newAccountLatchUserId: String? = null

    /**
     * One-shot rejections from flushing a queued offline edit the server then
     * refused. `extraBufferCapacity` so an emit from inside the resolve mutex
     * never suspends waiting on a slow collector.
     */
    private val _editRejections = MutableSharedFlow<ProfileEditRejection>(extraBufferCapacity = 4)

    init {
        logger.d { "init: subscribing to AuthRepository.observe()" }
        // Watch auth state. Every change re-resolves the profile —
        // sign-in flips Fallback → Authenticated; sign-out flips the
        // other way; refresh-after-claim swaps the Authenticated
        // payload to the non-anonymous one.
        appScope.launch {
            authRepository.observe().collect { auth ->
                logger.d { "Auth changed → resolve (${auth::class.simpleName})" }
                Catching { resolve(auth) }
                    .logOnFailure { "Profile resolve from auth change failed" }
            }
        }
    }

    override suspend fun current(): Profile = sharedState.first()

    override fun observe(): Flow<Profile> = sharedState

    override suspend fun resolveIsNewAccount(): Boolean {
        val auth = authRepository.current()
        if (auth !is AuthState.Authenticated) return false
        // Ensure a fresh /v1/me has run this session so the latch is populated —
        // idempotent get-or-create. The auth-change collector may have already
        // hydrated (and latched) before us; either way the latch holds the
        // answer. Errors fall back to "returning" so we never trap a real user.
        Catching { resolve(auth) }.logOnFailure { "resolveIsNewAccount: hydrate failed" }
        // Non-consuming: the Home welcome observes the same latch (see
        // observeAccountJustCreated). Reset happens on sign-out / account switch.
        return _accountJustCreated.value
    }

    override fun observeAccountJustCreated(): Flow<Boolean> = _accountJustCreated.asStateFlow()

    override fun observeEditRejections(): Flow<ProfileEditRejection> = _editRejections

    override suspend fun flushPendingEdits() {
        // Cheap pre-check off the lock: nothing to do unless a session is live
        // and an edit is actually queued. Otherwise re-resolve, which fetches
        // server truth and flushes the queued edit on top (see resolve).
        val auth = authRepository.current()
        if (auth !is AuthState.Authenticated) return
        val hasPending = Catching { pendingProfileEditStore.read() }.getOrNull() != null
        if (!hasPending) return
        logger.d { "flushPendingEdits: re-resolving to flush a queued edit" }
        Catching { resolve(auth) }.logOnFailure { "flushPendingEdits resolve failed" }
    }

    private suspend fun resolve(auth: AuthState): Profile = mutex.withLock {
        val resolved = when (auth) {
            is AuthState.Authenticated -> resolveAuthenticatedLocked(auth)
            is AuthState.Unauthenticated -> resolveFallbackLocked(auth)
        }
        _state.emit(resolved)
        // Info-level — profile emissions are load-bearing observability,
        // same reason as the auth-state emit logs.
        when (resolved) {
            is Profile.Authenticated -> logger.i {
                "Emitted Profile.Authenticated(id=${resolved.id}, isAnonymous=${resolved.isAnonymous}, hasEmail=${resolved.email != null})"
            }
            is Profile.Fallback -> logger.i {
                "Emitted Profile.Fallback(localId=${resolved.id}) — no auth + no cached profile"
            }
        }
        resolved
    }

    private suspend fun resolveAuthenticatedLocked(auth: AuthState.Authenticated): Profile {
        val base = fetchServerProfileLocked(auth)
        // A session is live again — flush any edit queued while offline, on top
        // of the freshly-fetched server truth, before emitting. Folding the
        // flush into the resolve means the user never sees the un-patched server
        // value flash in (no "new name → old name → new name" churn).
        return if (base is Profile.Authenticated) flushQueuedEditLocked(base, auth.email) else base
    }

    private suspend fun fetchServerProfileLocked(auth: AuthState.Authenticated): Profile =
        Catching {
            logger.d { "GET /v1/me for ${auth.userId}" }
            val me = profileApi.me()
            // Latch the brand-new-account signal for the auth-outcome classifier.
            // `isNewAccount` is one-shot server-side (only the /v1/me that created
            // the profile carries it), and this hydrate can race the classifier —
            // so capture it here, keyed to the userId so a stale latch can't leak
            // into a subsequent account switch.
            if (me.userId != newAccountLatchUserId) {
                newAccountLatchUserId = me.userId
                _accountJustCreated.value = me.isNewAccount
            } else if (me.isNewAccount) {
                _accountJustCreated.value = true
            }
            val profile = Profile.Authenticated(
                id = me.userId,
                displayName = me.displayName,
                email = auth.email,
                isAnonymous = me.isAnonymous,
                createdAt = Instant.fromEpochMilliseconds(me.createdAtEpochMs),
            )
            profileCache.writeAuthenticated(profile)
            // Real session resolved — local fallback no longer relevant.
            profileCache.writeLocalId(null)
            profile
        }.fold(
            onSuccess = { it },
            onFailure = { cause ->
                logger.w(cause) { "/v1/me failed; falling back to cache" }
                // Cache as fallback: an old real profile is better than
                // nothing; the supabase-kt session may still work for
                // individual calls.
                val cached = Catching { profileCache.readAuthenticated() }
                    .logOnFailure { "Profile cache read failed" }
                    .getOrNull()
                if (cached != null) {
                    logger.i { "Cache fallback: using cached profile ${cached.id}" }
                    cached
                } else {
                    logger.i { "Cache empty: emitting Profile.Fallback with localId" }
                    buildFallbackLocked()
                }
            },
        )

    /**
     * If an offline edit is queued, PATCH it on top of [base] (the freshly
     * resolved server profile). Returns the profile to emit:
     *  - **success** → server-confirmed profile; the queue is cleared.
     *  - **validation rejection** (name taken / invalid) → the queue is cleared
     *    (it can never succeed as-is), the optimistic value reverts to [base]
     *    (server truth), and a [ProfileEditRejection] is surfaced.
     *  - **transient failure** (network / 5xx) → the queue is kept, and the
     *    optimistic overlay (base + the edit) is emitted so the user keeps
     *    seeing their pending change; the next trigger retries.
     */
    private suspend fun flushQueuedEditLocked(
        base: Profile.Authenticated,
        email: String?,
    ): Profile.Authenticated {
        val pending = Catching { pendingProfileEditStore.read() }.getOrNull() ?: return base
        logger.i { "Flushing queued offline profile edit" }
        return Catching {
            profileApi.patchMe(
                PatchMeRequest(
                    displayName = pending.displayName,
                ),
            )
        }.fold(
            onSuccess = { updated ->
                Catching { pendingProfileEditStore.clear() }
                    .logOnFailure { "Clearing flushed profile edit failed" }
                val profile = updated.toAuthenticated(email)
                profileCache.writeAuthenticated(profile)
                logger.i { "Queued edit flushed: Success for ${profile.id}" }
                profile
            },
            onFailure = { e ->
                val rejection = e.toValidationRejectionOrNull()
                if (rejection != null) {
                    Catching { pendingProfileEditStore.clear() }
                        .logOnFailure { "Clearing rejected profile edit failed" }
                    profileCache.writeAuthenticated(base)
                    _editRejections.tryEmit(rejection)
                    logger.w(e) { "Queued edit rejected ($rejection) — reverted to server truth" }
                    base
                } else {
                    // Transient — keep the queue, keep showing the optimistic value.
                    val optimistic = base.applyingEdit(pending)
                    profileCache.writeAuthenticated(optimistic)
                    logger.w(e) { "Queued edit flush failed transiently — keeping it queued" }
                    optimistic
                }
            },
        )
    }

    private suspend fun resolveFallbackLocked(auth: AuthState.Unauthenticated): Profile {
        // Sign-out / delete tears down the session: clear the brand-new-account
        // latch so it can't leak into whatever account authenticates next.
        _accountJustCreated.value = false
        newAccountLatchUserId = null
        // A server-confirmed dead session (the auth server rejected our token):
        // the cached profile is a ghost. Surfacing it as Authenticated is exactly
        // what makes the app keep firing authed calls that all 401. Clear it and
        // drop to Fallback so the app knows it has no working account — routing to
        // re-auth happens off the SessionExpired auth state, not from here.
        if (auth.reason == AuthState.Unauthenticated.Reason.SessionExpired) {
            val cached = Catching { profileCache.readAuthenticated() }
                .logOnFailure { "Profile cache read failed" }
                .getOrNull()
            if (cached != null) {
                logger.i { "SessionExpired — clearing stale cached profile ${cached.id}" }
                Catching { profileCache.clear() }
                    .logOnFailure { "Failed to clear stale cached profile after session expiry" }
            }
            return buildFallbackLocked()
        }

        // Benign unauthenticated (no session yet / clean sign-out / offline): we
        // may have a profile cached from a previous session. If so, that's the
        // best we have to show until auth comes back. Otherwise the fallback UUID.
        val cached = Catching { profileCache.readAuthenticated() }
            .logOnFailure { "Profile cache read failed" }
            .getOrNull()
        if (cached != null) {
            logger.d { "Unauthenticated but cached profile ${cached.id} exists; surfacing it" }
            return cached
        }
        logger.d { "Unauthenticated + no cache; emitting Profile.Fallback" }
        return buildFallbackLocked()
    }

    /**
     * Build a [Profile.Fallback], enriching it with the user's locally-chosen
     * identity when one is owed but unsynced — the name picked during an
     * offline onboarding (held in [PendingGuestAccountStore]). Surfacing it lets
     * the app show the user's choice instead of a generic "You" while we're
     * session-less; it syncs to the server once a session is minted. When nothing
     * is owed (no offline onboarding pending), the fields stay null.
     */
    private suspend fun buildFallbackLocked(): Profile.Fallback {
        val pending = Catching { pendingGuestAccountStore.read() }
            .logOnFailure { "Reading pending identity for Fallback failed" }
            .getOrNull()
        return Profile.Fallback(
            id = ensureLocalIdLocked(),
            displayName = pending?.displayName,
        )
    }

    /**
     * Apply an offline profile edit for a session-less (Fallback) user by
     * merging it into the owed guest-account record. The guest-mint path applies
     * that identity when a session is established, so the single mint is the
     * sync. Emits an enriched [Profile.Fallback] immediately so the UI reflects
     * the change without waiting on the network. Assumes [mutex] is held.
     */
    private suspend fun queueGuestIdentityEditLocked(
        displayName: String?,
    ): UpdateProfileOutcome {
        val existing = Catching { pendingGuestAccountStore.read() }.getOrNull()
        val merged = PendingIdentity(
            displayName = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: existing?.displayName,
        )
        Catching { pendingGuestAccountStore.set(merged) }
            .logOnFailure { "Queuing offline profile edit failed" }
        _state.emit(
            Profile.Fallback(
                id = ensureLocalIdLocked(),
                displayName = merged.displayName,
            ),
        )
        logger.i { "update: Queued offline identity edit (will sync on session mint)" }
        return UpdateProfileOutcome.Queued
    }

    private suspend fun ensureLocalIdLocked(): String {
        val existing = Catching { profileCache.readLocalId() }.getOrNull()
        if (existing != null) return existing
        val fresh = Uuid.random().toString()
        Catching { profileCache.writeLocalId(fresh) }
            .logOnFailure { "Failed to persist new localId" }
        return fresh
    }

    // ---------- update ----------

    override suspend fun update(
        displayName: String?,
    ): UpdateProfileOutcome = mutex.withLock {
        // Don't log the new values themselves — display names are
        // mildly user-identifying. Just record which fields are
        // changing.
        logger.d {
            "update: fields=[" +
                listOfNotNull(
                    "displayName".takeIf { displayName != null },
                ).joinToString() +
                "]"
        }
        // PATCH /v1/me requires a session. When we don't have one, branch on
        // whether this is a real account that's merely offline vs. a guest who
        // hasn't reached the server yet:
        val auth = authRepository.current()
        if (auth !is AuthState.Authenticated) {
            val cachedAuthed = Catching { profileCache.readAuthenticated() }
                .logOnFailure { "Profile cache read during offline update failed" }
                .getOrNull()
            if (cachedAuthed != null) {
                // A real (claimed/anon) account, just offline. Apply the edit
                // optimistically and queue it to PATCH when a session returns —
                // offline-first: the user's change sticks and syncs on reconnect.
                val base = lastEmittedAuthenticatedOrNull() ?: cachedAuthed
                val optimistic = base.applyingEdit(displayName)
                profileCache.writeAuthenticated(optimistic)
                _state.emit(optimistic)
                Catching {
                    pendingProfileEditStore.enqueue(displayName)
                }.logOnFailure { "Queuing offline profile edit failed" }
                logger.i { "update: Queued offline edit for cached account (will sync when online)" }
                return@withLock UpdateProfileOutcome.Queued
            }
            // True Fallback — onboarded but session-less (e.g. onboarded
            // offline). Record the chosen identity into the owed guest-account
            // record so it (a) surfaces on the Fallback now and (b) is applied
            // server-side when the session is minted. Optimistic local emit.
            return@withLock queueGuestIdentityEditLocked(
                displayName = displayName,
            )
        }

        // Optimistic: write the prospective profile to cache + state
        // immediately so the UI updates without a round-trip. On
        // failure, roll back.
        val priorProfile = lastEmittedAuthenticatedOrNull()
        if (priorProfile != null) {
            val optimistic = priorProfile.copy(
                displayName = displayName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: priorProfile.displayName,
            )
            profileCache.writeAuthenticated(optimistic)
            _state.emit(optimistic)
            logger.d { "update: optimistic write applied; awaiting server confirm" }
        }

        Catching {
            profileApi.patchMe(
                PatchMeRequest(
                    displayName = displayName,
                ),
            )
        }.fold(
            onSuccess = { updated ->
                val profile = Profile.Authenticated(
                    id = updated.userId,
                    displayName = updated.displayName,
                    isAnonymous = updated.isAnonymous,
                    email = auth.email,
                    createdAt = Instant.fromEpochMilliseconds(updated.createdAtEpochMs),
                )
                profileCache.writeAuthenticated(profile)
                _state.emit(profile)
                logger.i { "update: Success for ${profile.id}" }
                UpdateProfileOutcome.Success(profile)
            },
            onFailure = { e ->
                // Validation failures are terminal — the user must fix the input,
                // so roll the optimistic write back and surface the typed error.
                val validation = when (e) {
                    is ClientRequestException -> when (e.response.status.value) {
                        409 -> UpdateProfileOutcome.DisplayNameTaken
                        400 -> UpdateProfileOutcome.InvalidDisplayName
                        else -> null
                    }
                    else -> null
                }
                if (validation != null) {
                    if (priorProfile != null) {
                        profileCache.writeAuthenticated(priorProfile)
                        _state.emit(priorProfile)
                        logger.d { "update: rolled back optimistic write (validation)" }
                    }
                    logger.w(e) { "update: ${validation::class.simpleName}" }
                    validation
                } else {
                    // Transient (network / 401 mid-edit / 5xx) — keep the
                    // optimistic value and queue the PATCH to flush on reconnect
                    // instead of losing the edit. Offline-first.
                    Catching {
                        pendingProfileEditStore.enqueue(displayName)
                    }.logOnFailure { "Queuing edit after transient failure failed" }
                    logger.w(e) { "update: Queued after transient failure" }
                    UpdateProfileOutcome.Queued
                }
            },
        )
    }

    private fun lastEmittedAuthenticatedOrNull(): Profile.Authenticated? =
        _state.replayCache.firstOrNull() as? Profile.Authenticated

    private fun MeDto.toAuthenticated(email: String?): Profile.Authenticated = Profile.Authenticated(
        id = userId,
        displayName = displayName,
        email = email,
        isAnonymous = isAnonymous,
        createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
    )

    /** Overlay a raw edit onto a profile — the optimistic-write shape. */
    private fun Profile.Authenticated.applyingEdit(
        displayName: String?,
    ): Profile.Authenticated = copy(
        displayName = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: this.displayName,
    )

    private fun Profile.Authenticated.applyingEdit(edit: PendingProfileEdit): Profile.Authenticated =
        applyingEdit(
            displayName = edit.displayName,
        )

    /**
     * Map a flush failure to a terminal [ProfileEditRejection] (the server
     * refused the edit on its merits), or null when it's transient (network /
     * 5xx) and worth keeping queued.
     */
    private fun Throwable.toValidationRejectionOrNull(): ProfileEditRejection? =
        when (this) {
            is ClientRequestException -> when (response.status.value) {
                409 -> ProfileEditRejection.DisplayNameTaken
                400 -> ProfileEditRejection.InvalidDisplayName
                else -> null
            }
            else -> null
        }
}
