package com.opson.admin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Wire shapes mirroring the server's `ModerationDto.kt`.

@Serializable
data class ModerationActionDto(
    val id: String,
    val userId: String? = null,
    val kind: String,
    val scope: String,
    val reason: String,
    val note: String? = null,
    val createdAtEpochMs: Long,
    val createdBy: String,
    val untilEpochMs: Long? = null,
    val markDevices: Boolean = false,
    val liftedAtEpochMs: Long? = null,
    val liftedBy: String? = null,
    val liftNote: String? = null,
    val appealState: String = "none",
    val appealNote: String? = null,
    val appealUpdatedAtEpochMs: Long? = null,
    val appealUpdatedBy: String? = null,
    val active: Boolean,
)

@Serializable
data class ModerationEventDto(
    val id: String,
    val atEpochMs: Long,
    val actor: String,
    val action: String,
    val actionId: String? = null,
    val before: JsonElement? = null,
    val after: JsonElement? = null,
)

@Serializable
data class DeviceMarkerDto(
    val id: String,
    val platform: String,
    val actionId: String? = null,
    val createdAtEpochMs: Long,
    val createdBy: String,
    val remoteState: String,
    val clearedAtEpochMs: Long? = null,
    val clearedBy: String? = null,
)

@Serializable
data class ContributionSummaryDto(val accepted: Int, val rolledBack: Int, val pending: Int, val devices: Int)

@Serializable
data class StandingDto(val kind: String, val scope: String, val untilEpochMs: Long? = null)

@Serializable
data class UserModerationDto(
    val userId: String,
    val standing: StandingDto? = null,
    val freeProAllowed: Boolean = true,
    val actions: List<ModerationActionDto> = emptyList(),
    val contributions: ContributionSummaryDto,
    val markers: List<DeviceMarkerDto> = emptyList(),
    val events: List<ModerationEventDto> = emptyList(),
    val deviceChecks: List<String> = emptyList(),
    val reasons: List<String> = emptyList(),
)

@Serializable
data class ModerateBody(
    val kind: String,
    val reason: String,
    val note: String? = null,
    val untilEpochMs: Long? = null,
    val markDevices: Boolean = false,
    val rollback: Boolean = false,
)

@Serializable
data class RevertedDto(val kind: String, val undone: Int, val closed: Int, val skipped: Int = 0)

@Serializable
data class ModeratedDto(val action: ModerationActionDto, val perksRemoved: List<String> = emptyList(), val rolledBack: List<RevertedDto>? = null)

@Serializable
data class RollbackDto(val rolledBack: List<RevertedDto>)

@Serializable
private data class NoteBody(val note: String? = null)

@Serializable
private data class AppealBody(val state: String, val note: String? = null)

@Serializable
private data class ReasonBody(val reason: String)

/**
 * Bans, suspensions, contribution restrictions, rollbacks and device markers
 * (`/v1/admin/users/{id}/moderation` and friends). Its own client beside [AdminApi], reached as
 * `ctx.api.moderation`.
 */
class ModerationApi(baseUrl: String, private val token: String, private val actor: String) {
    private val base = baseUrl.trimEnd('/')
    private val client = HttpClient(Js) { install(ContentNegotiation) { json(adminJson) } }

    suspend fun user(userId: String): UserModerationDto =
        client.get("$base/v1/admin/users/${segment(userId)}/moderation") { auth() }.require()

    suspend fun moderate(userId: String, body: ModerateBody): ModeratedDto =
        client.post("$base/v1/admin/users/${segment(userId)}/moderation") { auth(); json(body) }.require()

    suspend fun rollback(userId: String, reason: String): RollbackDto =
        client.post("$base/v1/admin/users/${segment(userId)}/contributions/rollback") { auth(); json(ReasonBody(reason)) }.require()

    suspend fun lift(actionId: String, note: String?) {
        client.post("$base/v1/admin/moderation/${segment(actionId)}/lift") { auth(); json(NoteBody(note)) }.requireOk()
    }

    suspend fun setAppeal(actionId: String, state: String, note: String?) {
        client.put("$base/v1/admin/moderation/${segment(actionId)}/appeal") { auth(); json(AppealBody(state, note)) }.requireOk()
    }

    suspend fun clearMarker(markerId: String, note: String?) {
        client.post("$base/v1/admin/devices/${segment(markerId)}/clear") { auth(); json(NoteBody(note)) }.requireOk()
    }

    private inline fun <reified T> HttpRequestBuilder.json(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun HttpRequestBuilder.auth() {
        header("X-Admin-Token", token)
        header("X-Admin-Actor", actor)
    }

    private suspend inline fun <reified T> HttpResponse.require(): T {
        requireOk()
        return body()
    }

    private suspend fun HttpResponse.requireOk() {
        if (status.value in 200..299) return
        val message = Catching {
            val error = (adminJson.parseToJsonElement(bodyAsText()) as? JsonObject)?.get("error") as? JsonObject
            (error?.get("message") as? JsonPrimitive)?.content
        }.getOrNull() ?: "HTTP ${status.value}"
        throw AdminApiException(status, message)
    }
}

private fun segment(value: String): String = js("encodeURIComponent(value)") as String
