package com.opson.libraries.config.impl.data

import com.opson.libraries.config.impl.serialization.ConfigJsonConverter
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.networking.AuthTokenProvider
import com.opson.libraries.networking.InternalNetworkingApi
import com.opson.libraries.networking.NetworkClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The config fetch attaches the session token when it has one. A server that refuses the token
 * (one deployed before it learned to serve a token it can't verify anonymously) must not keep
 * the version floor and the kill switch from the phone.
 */
@OptIn(InternalNetworkingApi::class)
class RemoteConfigRemoteDataSourceTest : CoroutineTest() {

    private class FakeNetworkClient(handler: MockRequestHandler) : NetworkClient {
        private val http = HttpClient(MockEngine(handler)) { expectSuccess = true }
        override val client: HttpClient get() = http
        override val authenticatedClient: HttpClient get() = http
        override suspend fun awaitAuthReady() = Unit
    }

    private class FixedToken(private val token: String?) : AuthTokenProvider {
        override suspend fun awaitReady() = Unit
        override suspend fun accessToken(): String? = token
        override suspend fun refreshAccessToken(rejected: String?): String? = null
    }

    private val sentAuth = mutableListOf<String?>()

    private fun source(token: String?, refuseTokensWith: HttpStatusCode?) = RemoteConfigRemoteDataSource(
        dispatcherProvider = dispatchers,
        networkClient = FakeNetworkClient { request ->
            val auth = request.headers[HttpHeaders.Authorization]
            sentAuth += auth
            if (auth != null && refuseTokensWith != null) {
                respond("""{"error":{"code":"unauthorized"}}""", refuseTokensWith)
            } else {
                respond("""{"upgrade":{"minSupportedVersionCode":42}}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        },
        authTokenProvider = FixedToken(token),
        converter = ConfigJsonConverter(Json),
    )

    @Test
    fun aTokenTheServerRefuses_isDroppedAndTheConfigStillArrives() = runUnitTest {
        val config = source(token = "foreign", refuseTokensWith = HttpStatusCode.Unauthorized).getConfig()

        assertTrue(config.isSuccess, "the floor reaches the phone")
        assertEquals(listOf("Bearer foreign", null), sentAuth)
    }

    @Test
    fun aForbiddenToken_isDroppedToo() = runUnitTest {
        assertTrue(source(token = "banned", refuseTokensWith = HttpStatusCode.Forbidden).getConfig().isSuccess)
        assertEquals(listOf("Bearer banned", null), sentAuth)
    }

    @Test
    fun anAcceptedToken_isSentOnce() = runUnitTest {
        assertTrue(source(token = "good", refuseTokensWith = null).getConfig().isSuccess)
        assertEquals(listOf<String?>("Bearer good"), sentAuth)
    }
}
