package com.opson.integration.helpers

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/*
 * Calls made by something other than the app: the owner's admin tooling and
 * hand-built requests that play an older app on the wire.
 */

/**
 * Turns the remote flag [path] to [value] for [userId] alone, through the admin
 * API the config console uses: the flag's base value stays at the app's
 * default ([base]) and a targeting rule allows only this user, so no other test
 * in the JVM sees the change.
 */
suspend fun Harness.setFlagFor(
    userId: String,
    path: String,
    value: JsonElement = JsonPrimitive(true),
    base: JsonElement = JsonPrimitive(false),
): String {
    adminPut("/v1/admin/config/flags/$path", buildJsonObject { put("value", base) }.toString())
    val ruleId = UUID.randomUUID().toString()
    adminPut(
        "/v1/admin/config/rules/$ruleId",
        buildJsonObject {
            put("flagPath", path)
            put("priority", 0)
            put("value", value)
            put("conditions", buildJsonObject { put("userAllow", buildJsonArray { add(JsonPrimitive(userId)) }) })
        }.toString(),
    )
    return ruleId
}

private suspend fun Harness.adminPut(path: String, body: String) {
    val response = http.put("${server.baseUrl}$path") {
        header("X-Admin-Token", InProcessServer.ADMIN_TOKEN)
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    check(response.status.isSuccess()) { "admin PUT $path answered ${response.status}: ${response.bodyAsText()}" }
}

/** `POST /v1/sync` as [userId] with a hand-written [body], for wire shapes the current app never sends. */
suspend fun Harness.rawSync(userId: String, body: String): HttpResponse =
    http.post("${server.baseUrl}/v1/sync") {
        header(HttpHeaders.Authorization, "Bearer ${IntegrationAuth.mintJwt(userId)}")
        contentType(ContentType.Application.Json)
        setBody(body)
    }
