package com.opson.integration.account

import com.opson.integration.helpers.Harness
import com.opson.integration.helpers.InProcessServer
import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.awaitState
import com.opson.integration.helpers.awaitUntil
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.nutrition.PresetId
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Support's user lookup.** A phone that signs in, picks a mode and logs is found by the admin
 * console's lookup with the build it runs, the mode it chose and how much it logged, and with
 * nothing it wrote: the food's name never comes back.
 */
class AdminUserLookupTest : IntegrationTest() {

    @Test
    fun theLookupShowsTheBuildTheModeAndTheCounts_andNoFood() = integration {
        val user = randomUserId()
        val phone = client(user, device = { it.copy(platform = "ios", appVersion = "0.2.0", buildNumber = "52") })
        server.sql("UPDATE auth.users SET email = ? WHERE id = ?::uuid", "sam.lee@example.com", user)

        phone.lifecycle.foreground()
        phone.syncNow().getOrThrow()
        phone.settings.isKnown.awaitState { it }
        phone.settings.update { it.withNumbers(it.numbers.pickPreset(PresetId.NoNumbers, phone.clock.now())) }
        phone.log(name = "Grandma's secret soup", kcal = 640.0)
        phone.syncNow().getOrThrow()

        // The build is recorded after the response, off the request.
        var detail = JsonObject(emptyMap())
        try {
            awaitUntil {
                detail = adminGet("/v1/admin/users/$user")
                detail["installs"]?.jsonArray?.isNotEmpty() == true
            }
        } catch (timeout: TimeoutCancellationException) {
            throw AssertionError("no build recorded; last lookup: $detail; rows: ${server.query("SELECT * FROM user_installs")}", timeout)
        }

        val account = detail.getValue("user").jsonObject
        assertEquals("sam.lee@example.com", account.getValue("email").jsonPrimitive.content)
        assertEquals("no_numbers", account.getValue("preset").jsonPrimitive.content)
        assertEquals("1", account.getValue("entries").jsonPrimitive.content)
        val install = detail.getValue("installs").jsonArray.single().jsonObject
        assertEquals("ios", install.getValue("platform").jsonPrimitive.content)
        assertEquals("0.2.0", install.getValue("appVersion").jsonPrimitive.content)
        assertEquals("52", install.getValue("buildNumber").jsonPrimitive.content)
        // Compare values, not the raw text: a random install id or timestamp can contain "640".
        val leaked = detail.leaves().filter { it.contains("soup", ignoreCase = true) || it.toDoubleOrNull() == 640.0 }
        assertTrue(leaked.isEmpty(), "a food reached the lookup: $leaked in $detail")

        val byEmail = adminSearch("sam.lee@")
        assertEquals(listOf(user), byEmail.map { it.jsonObject.getValue("userId").jsonPrimitive.content })
        assertTrue(adminSearch(user).isNotEmpty(), "found by id too")
    }

    private suspend fun Harness.adminGet(path: String): JsonObject =
        Json.parseToJsonElement(
            http.get("${server.baseUrl}$path") { header("X-Admin-Token", InProcessServer.ADMIN_TOKEN) }.bodyAsText(),
        ).jsonObject

    private suspend fun Harness.adminSearch(query: String) =
        Json.parseToJsonElement(
            http.post("${server.baseUrl}/v1/admin/users/search") {
                header("X-Admin-Token", InProcessServer.ADMIN_TOKEN)
                contentType(ContentType.Application.Json)
                setBody("""{"query":"$query"}""")
            }.bodyAsText(),
        ).jsonObject.getValue("users").jsonArray
}

/** Every primitive value in [this], as text, however deep. */
private fun JsonElement.leaves(): List<String> = when (this) {
    is JsonObject -> values.flatMap { it.leaves() }
    is JsonArray -> flatMap { it.leaves() }
    is JsonPrimitive -> listOf(content)
}
