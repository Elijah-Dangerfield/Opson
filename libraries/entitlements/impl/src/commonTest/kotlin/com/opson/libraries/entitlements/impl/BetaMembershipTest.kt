package com.opson.libraries.entitlements.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Recording a beta member (`POST /v1/me/beta`): only while `release.betaNotice` is on, once per
 * account, and a server that lacks the route or has closed the beta is not a failure, so the sync
 * coordinator never retries it into the ground.
 */
class BetaMembershipTest : CoroutineTest() {

    private var status = HttpStatusCode.Created
    private var on = true
    private val paths = mutableListOf<String>()

    private fun membership() = BetaMembership(
        cacheFactory = MemoryCacheFactory(),
        networkClient = FakeNetworkClient({ status }, { """{"joinedAtEpochMs":1}""" }, paths, testDispatcher),
        beta = { on },
    )

    @Test
    fun offAsksNothing() = runUnitTest {
        on = false
        assertTrue(membership().sync().isSuccess)
        assertTrue(paths.isEmpty())
    }

    @Test
    fun aJoin_isAskedOnce_perAccount() = runUnitTest {
        val beta = membership()
        assertTrue(beta.sync().isSuccess)
        assertTrue(beta.sync().isSuccess)
        assertEquals(listOf(BetaMembership.PATH), paths)

        beta.clear(previousUserId = "someone")
        assertTrue(beta.sync().isSuccess)
        assertEquals(2, paths.size, "the next account is asked about too")
    }

    @Test
    fun anOldServer_aClosedBeta_orAGuest_isNotAFailure_andIsAskedAgainLater() = runUnitTest {
        val beta = membership()
        listOf(HttpStatusCode.NotFound, HttpStatusCode.Conflict, HttpStatusCode.Forbidden).forEach {
            status = it
            assertTrue(beta.sync().isSuccess, "$it")
        }
        status = HttpStatusCode.OK
        assertTrue(beta.sync().isSuccess)
        assertEquals(4, paths.size)
        assertTrue(beta.sync().isSuccess)
        assertEquals(4, paths.size, "recorded once the server said so")
    }

    @Test
    fun anythingElse_fails_soTheCoordinatorRetries() = runUnitTest {
        status = HttpStatusCode.InternalServerError
        assertTrue(membership().sync().isFailure)
    }
}
