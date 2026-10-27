package com.opson.integration.helpers

import io.ktor.client.request.delete
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
import kotlinx.serialization.json.JsonObject
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
    setFlagBase(path, base)
    return putFlagRule(
        path = path,
        value = value,
        conditions = buildJsonObject { put("userAllow", buildJsonArray { add(JsonPrimitive(userId)) }) },
    )
}

/** The flag's base value, what every caller gets when no rule matches. `PUT /v1/admin/config/flags/{path}`. */
suspend fun Harness.setFlagBase(path: String, value: JsonElement) {
    adminPut("/v1/admin/config/flags/$path", buildJsonObject { put("value", value) }.toString())
}

/**
 * Creates the targeting rule [ruleId] on [path], or replaces it: the console's
 * rule editor. [conditions] is the wire `RuleConditions` object
 * (`platforms`, `minVersionCode`, `minAppVersion`, `userAllow`, `rolloutPercent`, and so on).
 */
suspend fun Harness.putFlagRule(
    path: String,
    value: JsonElement,
    conditions: JsonObject,
    ruleId: String = UUID.randomUUID().toString(),
    priority: Int = 0,
): String {
    adminPut(
        "/v1/admin/config/rules/$ruleId",
        buildJsonObject {
            put("flagPath", path)
            put("priority", priority)
            put("value", value)
            put("conditions", conditions)
        }.toString(),
    )
    return ruleId
}

/** Removes a rule [setFlagFor] added, so the flag falls back to its base value for that user. */
suspend fun Harness.removeFlagRule(ruleId: String) {
    val response = http.delete("${server.baseUrl}/v1/admin/config/rules/$ruleId") {
        header("X-Admin-Token", InProcessServer.ADMIN_TOKEN)
    }
    check(response.status.isSuccess()) { "admin DELETE rule answered ${response.status}" }
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
