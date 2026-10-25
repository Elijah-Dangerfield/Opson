package com.opson.integration.helpers

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
        block: suspend Harness.() -> Unit,
    ) = runBlocking {
        InProcessServer().use { server ->
            val harness = Harness(server)
            try {
                harness.block()
            } finally {
                harness.close()
            }
        }
    }
}

/** The per-test surface: the running server plus a factory for real clients. */
class Harness(val server: InProcessServer) {

    private val clients = mutableListOf<TestClient>()

    /**
     * A real client for [userId]. Seeds the stub `auth.users` row by default —
     * the precondition a real Supabase sign-up establishes and the JWT implies;
     * pass `seedAuthUser = false` to exercise the unknown-user path.
     */
    fun client(
        userId: String = randomUserId(),
        seedAuthUser: Boolean = true,
    ): TestClient {
        if (seedAuthUser) server.seedAuthUser(userId)
        return TestClient(serverUrl = server.baseUrl, userId = userId).also { clients += it }
    }

    internal fun close() {
        clients.forEach { it.close() }
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
