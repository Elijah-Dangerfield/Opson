package com.opson.server.routes

import com.opson.server.config.SupabaseConfig
import com.opson.server.plugins.installSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthRoutesTest {

    @Test
    fun healthReturnsOk() = testApplication {
        application {
            installSerialization()
            routing { healthRoutes() }
        }
        val response = client.get("/_health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"ok\""))
    }

    @Test
    fun healthNamesTheSupabaseProjectItVerifies() = testApplication {
        application {
            installSerialization()
            routing { healthRoutes(SupabaseConfig("https://abcdefghijklmnopqrst.supabase.co").projectRef) }
        }
        val body = Json.parseToJsonElement(client.get("/_health").bodyAsText()).jsonObject
        assertEquals("abcdefghijklmnopqrst", body.getValue("supabaseProject").jsonPrimitive.content)
    }
}
