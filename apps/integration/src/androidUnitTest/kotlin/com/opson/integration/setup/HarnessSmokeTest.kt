package com.opson.integration.setup

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.awaitState
import com.opson.integration.helpers.randomUserId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The harness's own smoke test: a real client authenticates and resolves its
 * profile against the real in-process server over real Postgres. Proves the
 * helpers + JWT wiring work end to end — real HomeViewModel → real
 * ProfileRepositoryImpl → real HTTP client → real TCP → real auth plugin →
 * real repository → real DB — before any journey tests lean on them.
 */
class HarnessSmokeTest : IntegrationTest() {

    @Test
    fun realClient_resolvesProfile_againstRealServer() = integration {
        val userId = randomUserId()
        val client = client(userId)

        val vm = client.homeVm()

        val state = vm.stateFlow.awaitState { it.userName != null }
        // The server's get-or-create default display name, proving the value on
        // screen came from the real `/v1/me` round-trip, not a client fallback.
        assertEquals("user-${userId.take(8)}", state.userName)
    }
}
