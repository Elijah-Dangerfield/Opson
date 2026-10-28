package com.opson.libraries.foods.impl

import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.networking.InternalNetworkingApi
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.CacheJsonSerializer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalNetworkingApi::class)
class SubmittedFoodsStoreTest : CoroutineTest() {

    /** Answers on [dispatcher], the test's own, so a status the sync writes is read back on the same thread. */
    private class FakeNetworkClient(
        status: () -> HttpStatusCode,
        body: () -> String,
        paths: MutableList<String>,
        dispatcher: CoroutineDispatcher,
    ) : NetworkClient {
        private val http = HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    this.dispatcher = dispatcher
                    addHandler { request ->
                        paths += request.url.encodedPath
                        respond(body(), status(), headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                },
            ),
        ) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
            // Strict, as a debug build decodes: a field the DTOs leave out fails here.
            install(ContentNegotiation) { json(Json) }
        }
        override val client: HttpClient get() = http
        override val authenticatedClient: HttpClient get() = http
        override suspend fun awaitAuthReady() = Unit
    }

    private class MemoryCache<T : Any>(private val initial: suspend () -> T) : Cache<T> {
        private val state = MutableStateFlow<T?>(null)
        override val updates: Flow<T> = flow {
            get()
            emitAll(state.filterNotNull())
        }
        override suspend fun get(): T = state.value ?: initial().also { state.value = it }
        override suspend fun set(value: T) {
            state.value = value
        }
        override suspend fun clear() {
            state.value = initial()
        }
        override suspend fun update(transform: (T) -> T): T = transform(get()).also { state.value = it }
    }

    private class MemoryCacheFactory : CacheFactory {
        override fun <T : Any> inMemory(defaultValue: () -> T): Cache<T> = MemoryCache { defaultValue() }
        override fun <T : Any> persistent(name: String, serializer: CacheJsonSerializer<T>, loadEagerly: Boolean): Cache<T> =
            MemoryCache { serializer.read(null) }
    }

    private var status = HttpStatusCode.OK
    private var body = """{"submissions":[]}"""
    private val paths = mutableListOf<String>()

    private fun store() = SubmittedFoodsStore(
        cacheFactory = MemoryCacheFactory(),
        networkClient = FakeNetworkClient({ status }, { body }, paths, testDispatcher),
        appScope = AppCoroutineScope(dispatchers),
    )

    @Test
    fun aFoodSentForReviewIsUnverifiedAtOnce() = runUnitTest {
        val store = store()

        store.sent(customFoodId = "c1", submissionId = "s1", foodId = "f1")

        assertEquals(mapOf("c1" to ReviewStatus.Pending), store.reviews.value)
    }

    @Test
    fun theServersListMovesTheStatusAndKeepsTheDevicesLinkWhenTheServerHasNone() = runUnitTest {
        val store = store()
        store.sent(customFoodId = "c1", submissionId = "s1", foodId = "f1")
        // Newest first, as the server sends it; s1 predates the server storing the link.
        body = """
            {"submissions":[
              {"id":"s2","foodId":"f2","status":"pending","createdAt":"2026-10-28T10:00:00Z","customFoodId":"c2"},
              {"id":"s1","foodId":"f1","status":"approved","createdAt":"2026-10-27T10:00:00Z"}
            ]}
        """.trimIndent()

        assertTrue(store.sync().isSuccess)

        assertEquals(SubmittedFoodsStore.PATH, paths.single())
        assertEquals(mapOf("c1" to ReviewStatus.Approved, "c2" to ReviewStatus.Pending), store.reviews.value)
    }

    @Test
    fun aSubmissionTheServerNoLongerHasLosesItsMark() = runUnitTest {
        val store = store()
        store.sent(customFoodId = "c1", submissionId = "s1", foodId = "f1")

        assertTrue(store.sync().isSuccess)

        assertEquals(emptyMap(), store.reviews.value)
    }

    @Test
    fun aServerFromBeforeTheRouteChangesNothing() = runUnitTest {
        val store = store()
        store.sent(customFoodId = "c1", submissionId = "s1", foodId = "f1")
        status = HttpStatusCode.NotFound
        body = """{"error":{"code":"not_found","message":"x"}}"""

        assertTrue(store.sync().isSuccess)

        assertEquals(mapOf("c1" to ReviewStatus.Pending), store.reviews.value)
    }

    @Test
    fun aFailedSyncIsRetriedAndKeepsTheMarks() = runUnitTest {
        val store = store()
        store.sent(customFoodId = "c1", submissionId = "s1", foodId = "f1")
        status = HttpStatusCode.InternalServerError

        assertTrue(store.sync().isFailure, "the coordinator retries a failure")

        assertEquals(mapOf("c1" to ReviewStatus.Pending), store.reviews.value)
    }

    @Test
    fun aUserChangeForgetsTheDepartingAccountsFoods() = runUnitTest {
        val store = store()
        store.sent(customFoodId = "c1", submissionId = "s1", foodId = "f1")

        store.clear("previous-user")

        assertEquals(emptyMap(), store.reviews.value)
    }
}
