package com.opson.integration.helpers

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import com.opson.libraries.identity.auth.OAuthProvider
import com.opson.libraries.identity.impl.auth.AuthGatewayStatus
import com.opson.libraries.identity.impl.auth.GatewaySession
import com.opson.libraries.identity.impl.auth.GatewaySignUpResult
import com.opson.libraries.identity.impl.auth.SupabaseAuthGateway
import com.opson.libraries.opson.AppLifecycle
import com.opson.libraries.opson.AppLifecycleObserver
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.CacheJsonSerializer
import com.opson.libraries.storage.impl.db.AppDatabase
import com.opson.libraries.storage.impl.db.AppDatabaseBuilderFactory
import com.opson.libraries.storage.impl.db.AppDatabaseConstructor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/*
 * The seams a phone owns and the host JVM does not: the Supabase SDK, the
 * Android context Room is built with, on-disk caches, the app lifecycle and
 * the wall clock. Everything above them in TestClient is the app's own code.
 */

/**
 * The Supabase Auth SDK as a phone would see it, holding at most one session.
 * Its tokens are signed by [IntegrationAuth], which the in-process server
 * trusts, so every request a client makes carries a JWT the server's real auth
 * plugin verifies. A session the client starts with ([signedInAs]) is an
 * anonymous guest, as every account was before accounts were required.
 * [newAccount] stands in for Supabase creating a user on a first sign-in: it
 * returns the new id after seeding the server's `auth.users` row.
 */
class StubSupabaseGateway(
    signedInAs: String?,
    private val newAccount: () -> String,
) : SupabaseAuthGateway {

    /** Signs every token this hands out, on sign-in and refresh alike. */
    @Volatile
    private var signer: (userId: String) -> String = { IntegrationAuth.mintJwt(it) }

    @Volatile
    private var session: GatewaySession? = signedInAs?.let { sessionFor(it) }

    /**
     * Signs this session's tokens, now and on every refresh, with [signer]: a phone signed in to
     * another Supabase project gets that project's tokens however often it refreshes.
     */
    fun signWith(signer: (userId: String) -> String) {
        this.signer = signer
        session?.let { holdToken(signer(it.userId)) }
    }

    /**
     * Thrown by every refresh while set, as Supabase throws when it refuses the refresh token
     * (revoked, or the user deleted). Null refreshes normally: a new token for the same user.
     */
    @Volatile
    var refusesRefresh: Throwable? = null

    /** Refreshes asked for so far, refused ones included. */
    val refreshes = AtomicInteger()

    /**
     * Swaps the access token the session holds for [token], keeping the user: an expired token,
     * or one another Supabase project signed.
     */
    fun holdToken(token: String) {
        session = checkNotNull(session) { "No session to hold a token" }.copy(accessToken = token)
    }

    override suspend fun awaitInitialization() = Unit

    override fun currentStatus(): AuthGatewayStatus =
        if (session != null) AuthGatewayStatus.Authenticated else AuthGatewayStatus.NotAuthenticated

    override fun currentSession(): GatewaySession? = session

    override suspend fun refreshSession() {
        refreshes.incrementAndGet()
        refusesRefresh?.let { throw it }
        session = session?.let { sessionFor(it.userId, anonymous = it.isAnonymous) }
    }

    override suspend fun signOut() {
        session = null
    }

    override suspend fun signInWithEmail(email: String, password: String) = unused()
    override suspend fun signUpWithEmail(email: String, password: String): GatewaySignUpResult = unused()
    override suspend fun resendVerificationEmail(email: String) = unused()
    override suspend fun resetPasswordForEmail(email: String) = unused()
    override suspend fun linkOAuthIdentity(provider: OAuthProvider) = unused()
    override suspend fun signInWithOAuth(provider: OAuthProvider) = unused()
    override suspend fun completeOAuthRedirect(url: String) = unused()
    override suspend fun hydrateCurrentUser() = Unit
    override fun isOAuthRedirect(url: String): Boolean = false
    // With nobody signed in, Apple makes a new account, as Supabase does on a first
    // sign-in. With someone signed in it says yes to them: the journeys use that for
    // the authorization code the app sends afterwards, not for switching accounts.
    override suspend fun signInWithAppleIdToken(idToken: String, nonce: String) {
        session = session ?: sessionFor(newAccount(), anonymous = false)
    }

    // Linking keeps the user and claims it; the app refreshes afterwards for the new token.
    override suspend fun linkAppleIdToken(idToken: String, nonce: String) {
        session = checkNotNull(session) { "Linking needs a guest to link onto" }.copy(isAnonymous = false)
    }
    override suspend fun signInWithGoogleIdToken(idToken: String, nonce: String?) = unused()
    override suspend fun linkEmailIdentity(email: String, password: String) = unused()

    private fun unused(): Nothing = error("Not used by the integration suite")

    private fun sessionFor(userId: String, anonymous: Boolean = true) = GatewaySession(
        userId = userId,
        email = null,
        accessToken = signer(userId),
        isAnonymous = anonymous,
        isEmailConfirmed = true,
    )
}

/**
 * The app's [AppDatabase], in memory. `RealAppDatabaseProvider` adds the
 * bundled driver and the migration policy on top, as it does on a phone.
 *
 * The context only has to survive the builder: with a driver set, Room asks it
 * for nothing but itself. The journal mode is fixed because resolving
 * AUTOMATIC asks the context for the ActivityManager, and an in-memory
 * database has no journal file anyway.
 */
internal object InMemoryAppDatabase : AppDatabaseBuilderFactory {
    override fun create(): RoomDatabase.Builder<AppDatabase> =
        Room.inMemoryDatabaseBuilder<AppDatabase>(HostContext(), factory = { AppDatabaseConstructor.initialize() })
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)

    private class HostContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }
}

/**
 * Wall-clock time, shiftable. Starts at the real now and keeps running, so
 * every stamp a client writes still moves forward; [shiftTo] and [advance] jump
 * it, for journeys that depend on the time of day or on a throttle window.
 */
class TestClock : Clock {
    @Volatile
    private var offset: Duration = Duration.ZERO

    override fun now(): Instant = Clock.System.now() + offset

    fun advance(by: Duration) {
        offset += by
    }

    fun shiftTo(instant: Instant) {
        offset = instant - Clock.System.now()
    }
}

/** Foreground and background, on cue. */
class TestLifecycle : AppLifecycle {
    private val observers = CopyOnWriteArrayList<AppLifecycleObserver>()

    override fun addObserver(observer: AppLifecycleObserver) {
        observers += observer
    }

    override fun removeObserver(observer: AppLifecycleObserver) {
        observers -= observer
    }

    fun foreground() = observers.forEach { it.onEnterForeground() }

    fun background() = observers.forEach { it.onEnterBackground() }
}

/**
 * In-memory [CacheFactory]. The production stores (settings, profile, config)
 * are concrete classes over this seam, so their real logic runs with no file
 * system underneath. A fresh factory is a fresh install. Like the real
 * factory, one name is one cache, and collecting [Cache.updates] reads the
 * stored value (here, the default) without anyone calling `get` first.
 */
internal class InMemoryCacheFactory : CacheFactory {

    private val persistent = ConcurrentHashMap<String, Cache<*>>()

    override fun <T : Any> inMemory(defaultValue: () -> T): Cache<T> =
        HarnessCache { defaultValue() }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> persistent(
        name: String,
        serializer: CacheJsonSerializer<T>,
        loadEagerly: Boolean,
    ): Cache<T> = persistent.getOrPut(name) { HarnessCache { serializer.read(null) } } as Cache<T>

    /** Null state = "not yet loaded"; [get] seeds it from the default lazily. */
    private class HarnessCache<T : Any>(
        private val default: suspend () -> T,
    ) : Cache<T> {
        private val state = MutableStateFlow<T?>(null)

        override val updates: Flow<T> = flow {
            get()
            emitAll(state.filterNotNull())
        }

        override suspend fun get(): T {
            state.compareAndSet(null, default())
            return checkNotNull(state.value)
        }

        override suspend fun set(value: T) {
            state.value = value
        }

        override suspend fun clear() {
            state.value = default()
        }
    }
}
