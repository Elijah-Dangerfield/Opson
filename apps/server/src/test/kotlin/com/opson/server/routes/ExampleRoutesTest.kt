package com.opson.server.routes

import com.opson.server.domain.ExampleData
import com.opson.server.domain.ExampleSource
import com.opson.server.plugins.installSerialization
import com.opson.server.plugins.installStatusPages
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reference route test. Boots a single route inside `testApplication` with
 * the same production plugins, injects a fake as a plain argument (no DI graph),
 * and asserts on the typed response. Copy this for new routes.
 */
class ExampleRoutesTest {

    @Test
    fun returnsExamplePayload() = testApplication {
        application {
            installSerialization()
            installStatusPages()
            routing { exampleRoutes(FakeExampleSource()) }
        }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/v1/example")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ExampleResponse>()
        assertEquals("hi", body.message)
        assertEquals(listOf("one", "two"), body.items)
    }

    private class FakeExampleSource : ExampleSource {
        override suspend fun get(): ExampleData = ExampleData("hi", listOf("one", "two"))
    }
}
