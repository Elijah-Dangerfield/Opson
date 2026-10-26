package com.opson.integration.helpers

import com.opson.libraries.diary.impl.DiarySyncApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * Base class for the end-to-end integration tests. Handles the cross-cutting
 * concerns every test needs:
 *
 *  - a REAL Main dispatcher (the view models drive init work on
 *    `viewModelScope`; the harness talks real TCP on real threads, so a real —
 *    not virtual — dispatcher is required);
 *  - the Docker-availability skip (no Docker → skipped, not red); and
 *  - a fresh in-process server per test, via [integration].
 *
 * Inside [integration], use [Harness.client] to spin up real clients and the
 * `await*` helpers to wait on real network state without fixed sleeps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class IntegrationTest {

    @BeforeTest
    fun requireDocker() = InProcessServer.assumeDockerAvailable()

    @BeforeTest
    fun installRealMainDispatcher() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun restoreMainDispatcher() = Dispatchers.resetMain()

    /** Boots a real [InProcessServer] and runs [block] against it. */
    protected fun integration(
        photoEstimates: Boolean = true,
        block: suspend Harness.() -> Unit,
    ) = runBlocking {
        InProcessServer(photoEstimates).use { server ->
            val harness = Harness(server)
            try {
                harness.block()
            } finally {
                harness.close()
            }
        }
    }
}

/** The per-test surface: the running server plus factories for real clients. */
class Harness(val server: InProcessServer) {

    private val closeables = mutableListOf<AutoCloseable>()

    /** A plain HTTP client for callers that are not the app: the website, an admin script. */
    val http: HttpClient = HttpClient(OkHttp) { expectSuccess = false }.also { closeables += it }

    /**
     * A real client (a fresh install) signed in as [userId]; null for an install
     * nobody has signed in on. Seeds the stub `auth.users` row a real Supabase
     * sign-up would have created. [through] puts a [FaultProxy] between this
     * client and the server.
     */
    fun client(
        userId: String? = randomUserId(),
        through: FaultProxy? = null,
        clock: TestClock = TestClock(),
        requestTimeoutMillis: Long = 30_000,
        wrapSync: (DiarySyncApi) -> DiarySyncApi = { it },
    ): TestClient {
        userId?.let { server.seedAuthUser(it) }
        return TestClient(
            serverUrl = through?.baseUrl ?: server.baseUrl,
            signedInAs = userId,
            newAccount = { randomUserId().also { server.seedAuthUser(it) } },
            clock = clock,
            requestTimeoutMillis = requestTimeoutMillis,
            wrapSync = wrapSync,
        ).also { closeables += AutoCloseable(it::close) }
    }

    /** A [FaultProxy] in front of this test's server, closed with the test. */
    fun faultProxy(): FaultProxy = FaultProxy(server.baseUrl).also { closeables += it }

    internal fun close() {
        closeables.asReversed().forEach { runCatching { it.close() } }
    }
}

// ---- await helpers (real time + generous timeouts; never fixed sleeps) ----

const val DEFAULT_TIMEOUT_MS = 20_000L

/**
 * Poll [block] until it returns true or [timeoutMs] elapses, sleeping
 * [stepMs] between tries. For asserting an *eventual* state that has no push
 * signal to await on. Prefer the flow-based [awaitState] whenever a flow
 * carries the signal.
 */
suspend fun awaitUntil(
    timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    stepMs: Long = 25,
    block: suspend () -> Boolean,
) {
    withTimeout(timeoutMs) {
        while (!block()) delay(stepMs)
    }
}

/** Suspend until the state satisfies [predicate]; fail loudly on timeout. */
suspend fun <S> StateFlow<S>.awaitState(
    timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    predicate: (S) -> Boolean,
): S = withTimeout(timeoutMs) { first(predicate) }
