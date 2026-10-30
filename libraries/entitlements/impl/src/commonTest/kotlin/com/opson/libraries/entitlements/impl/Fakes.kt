package com.opson.libraries.entitlements.impl

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

/** Answers on [dispatcher], the test's own, so what a sync writes is read back on the same thread. */
@OptIn(InternalNetworkingApi::class)
internal class FakeNetworkClient(
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

internal class MemoryCache<T : Any>(private val initial: suspend () -> T) : Cache<T> {
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

internal class MemoryCacheFactory : CacheFactory {
    override fun <T : Any> inMemory(defaultValue: () -> T): Cache<T> = MemoryCache { defaultValue() }
    override fun <T : Any> persistent(name: String, serializer: CacheJsonSerializer<T>, loadEagerly: Boolean): Cache<T> =
        MemoryCache { serializer.read(null) }
}
