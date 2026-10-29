package com.opson.libraries.foods.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.entitlements.EntitlementRepository
import com.opson.libraries.foods.BuilderProgram
import com.opson.libraries.foods.BuilderResult
import com.opson.libraries.foods.BuilderStanding
import com.opson.libraries.foods.BuilderStatus
import com.opson.libraries.foods.BuilderTrust
import com.opson.libraries.foods.FoodCheckResult
import com.opson.libraries.foods.FoodChecks
import com.opson.libraries.foods.JoinBlock
import com.opson.libraries.foods.LabelTextLine
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.authedCall
import com.opson.libraries.networking.isOfflineError
import com.opson.libraries.opson.UserScopedClearer
import com.opson.libraries.opson.UserScopedSyncer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Instant

/** The server's `BuilderMeResponse`. */
@Serializable
data class BuilderMeDto(
    val status: String = "none",
    val trust: String = "anyone",
    val joinedAtEpochMs: Long? = null,
    val proUntilEpochMs: Long? = null,
    val accepted30d: Int = 0,
    val needed: Int = 10,
    val photoRetention: Boolean = false,
    val canJoin: Boolean = false,
    val joinBlock: String? = null,
    val availableAtEpochMs: Long? = null,
    val termsVersion: String = "",
) {
    fun toStanding() = BuilderStanding(
        status = BuilderStatus.fromWire(status),
        trust = BuilderTrust.fromWire(trust),
        joinedAt = joinedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        proUntil = proUntilEpochMs?.let(Instant::fromEpochMilliseconds),
        accepted30d = accepted30d,
        needed = needed,
        photoRetention = photoRetention,
        canJoin = canJoin,
        joinBlock = JoinBlock.fromWire(joinBlock),
        availableAt = availableAtEpochMs?.let(Instant::fromEpochMilliseconds),
        termsVersion = termsVersion,
    )
}

@Serializable
data class BuilderJoinDto(val acceptTerms: Boolean, val termsVersion: String, val photoRetention: Boolean)

@Serializable
data class BuilderSettingsDto(val photoRetention: Boolean)

/**
 * [BuilderProgram] over `/v1/builders`. Kept in memory only: the server's answer is cheap and
 * comes back on every account edge (sign-in, foreground, reconnect), and a stale "you're a
 * Builder" on disk would show Builder mode to someone who left on another phone. A join or a
 * leave also refreshes the account's entitlements, since the role brings or takes Pro.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = BuilderProgram::class)
@ContributesBinding(AppScope::class, boundType = UserScopedSyncer::class, multibinding = true)
@ContributesBinding(AppScope::class, boundType = UserScopedClearer::class, multibinding = true)
@Inject
class HttpBuilderProgram(
    private val networkClient: NetworkClient,
    private val entitlements: EntitlementRepository,
) : BuilderProgram, UserScopedSyncer, UserScopedClearer {

    private val _standing = MutableStateFlow<BuilderStanding?>(null)
    override val standing: StateFlow<BuilderStanding?> = _standing.asStateFlow()

    override suspend fun refresh(): BuilderResult = call("builders.me") { client -> client.get(ME_PATH) { expectSuccess = false } }

    override suspend fun join(photoRetention: Boolean): BuilderResult {
        val result = call("builders.join") { client ->
            client.post(JOIN_PATH) {
                expectSuccess = false
                contentType(ContentType.Application.Json)
                setBody(BuilderJoinDto(acceptTerms = true, termsVersion = TERMS_VERSION, photoRetention = photoRetention))
            }
        }
        if (result is BuilderResult.Done) entitlements.refresh()
        return result
    }

    override suspend fun leave(): BuilderResult {
        val result = call("builders.leave") { client -> client.post(LEAVE_PATH) { expectSuccess = false } }
        if (result is BuilderResult.Done) entitlements.refresh()
        return result
    }

    override suspend fun setPhotoRetention(keep: Boolean): BuilderResult = call("builders.settings") { client ->
        client.put(ME_PATH) {
            expectSuccess = false
            contentType(ContentType.Application.Json)
            setBody(BuilderSettingsDto(keep))
        }
    }

    override suspend fun sync(): Result<Unit> = Catching {
        when (val result = refresh()) {
            is BuilderResult.Failed -> if (!result.offline) error("builders answered with an error")
            else -> Unit
        }
    }

    override suspend fun clear(previousUserId: String) {
        _standing.value = null
    }

    private suspend fun call(name: String, request: suspend (HttpClient) -> HttpResponse): BuilderResult =
        networkClient.authedCall(name) { client ->
            val response = request(client)
            when (response.status) {
                HttpStatusCode.OK -> response.body<BuilderMeDto>().toStanding().also { _standing.value = it }.let(BuilderResult::Done)
                // A server from before the program.
                HttpStatusCode.NotFound -> BuilderResult.Unavailable
                HttpStatusCode.Forbidden, HttpStatusCode.Conflict -> blocked(response.bodyAsText())
                else -> BuilderResult.Failed(offline = false)
            }
        }.getOrElse { BuilderResult.Failed(offline = it.isOfflineError()) }

    private fun blocked(body: String): BuilderResult =
        JoinBlock.entries.firstOrNull { "\"${it.wire}\"" in body }?.let(BuilderResult::Blocked) ?: BuilderResult.Failed(offline = false)

    internal companion object {
        const val ME_PATH = "/v1/builders/me"
        const val JOIN_PATH = "/v1/builders/join"
        const val LEAVE_PATH = "/v1/builders/leave"

        /** The rules the app shows. The server refuses a join that accepted any other version. */
        const val TERMS_VERSION = "2026-10-29"
    }
}

@Serializable
data class VerificationRequestDto(val id: String, val engine: String, val lines: List<CaptureLineDto>)

@Serializable
data class VerificationResponseDto(
    val id: String,
    val outcome: String,
    val grounded: Int = 0,
    val groundable: Int = 0,
    val missing: List<String> = emptyList(),
)

/** `POST /v1/foods/{id}/verifications`. Not retried by itself; a check is one tap. */
@ContributesBinding(AppScope::class)
@Inject
class HttpFoodChecks(private val networkClient: NetworkClient) : FoodChecks {
    override suspend fun check(id: String, foodId: String, engine: String, lines: List<LabelTextLine>): FoodCheckResult =
        networkClient.authedCall("foods.verification") { client ->
            val response = client.post("/v1/foods/$foodId/verifications") {
                expectSuccess = false
                contentType(ContentType.Application.Json)
                setBody(VerificationRequestDto(id, engine, lines.map { CaptureLineDto(it.text, it.confidence) }))
            }
            when (response.status) {
                HttpStatusCode.Created, HttpStatusCode.OK -> response.body<VerificationResponseDto>().let { body ->
                    if (body.outcome == "confirmed") FoodCheckResult.Confirmed else FoodCheckResult.Unconfirmed(body.missing)
                }
                HttpStatusCode.Forbidden -> FoodCheckResult.NotBuilder
                HttpStatusCode.NotFound -> FoodCheckResult.Gone
                HttpStatusCode.TooManyRequests -> FoodCheckResult.TooMany
                else -> FoodCheckResult.Failed(offline = false)
            }
        }.getOrElse { FoodCheckResult.Failed(offline = it.isOfflineError()) }
}
