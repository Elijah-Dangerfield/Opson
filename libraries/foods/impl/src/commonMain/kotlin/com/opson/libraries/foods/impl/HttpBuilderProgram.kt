package com.opson.libraries.foods.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.entitlements.EntitlementRepository
import com.opson.libraries.foods.ApplicationStatus
import com.opson.libraries.foods.BuilderApplication
import com.opson.libraries.foods.BuilderApplicationForm
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
import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.AppData
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
import com.opson.libraries.foods.ContributionAttestation
import com.opson.libraries.foods.FoodVerification
import com.opson.libraries.foods.VerifyField
import com.opson.libraries.foods.VerifyProblem
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.http.encodeURLPathPart
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
    /** Why they can't apply, in codes this app knows; [joinBlock] is the same for apps from before applications. */
    val applyBlock: String? = null,
    val application: BuilderApplicationDto? = null,
    val stats: BuilderStatsDto = BuilderStatsDto(),
) {
    fun toStanding() = BuilderStanding(
        status = BuilderStatus.fromWire(status),
        trust = BuilderTrust.fromWire(trust),
        joinedAt = joinedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        proUntil = proUntilEpochMs?.let(Instant::fromEpochMilliseconds),
        accepted30d = accepted30d,
        needed = needed,
        photoRetention = photoRetention,
        canApply = canJoin,
        joinBlock = JoinBlock.fromWire(applyBlock ?: joinBlock),
        availableAt = availableAtEpochMs?.let(Instant::fromEpochMilliseconds),
        termsVersion = termsVersion,
        application = application?.toApplication(),
        foodsAdded = stats.foodsAdded,
        checks = stats.checks,
        rejected = stats.rejected,
    )
}

/** The server's `BuilderApplicationResponse`. An unknown status reads as no application. */
@Serializable
data class BuilderApplicationDto(
    val id: String,
    val status: String,
    val submittedAtEpochMs: Long,
    val decidedAtEpochMs: Long? = null,
    val note: String? = null,
    val reapplyAtEpochMs: Long? = null,
) {
    fun toApplication(): BuilderApplication? = ApplicationStatus.fromWire(status)?.let { known ->
        BuilderApplication(
            id = id,
            status = known,
            submittedAt = Instant.fromEpochMilliseconds(submittedAtEpochMs),
            decidedAt = decidedAtEpochMs?.let(Instant::fromEpochMilliseconds),
            note = note?.trim()?.ifEmpty { null },
            reapplyAt = reapplyAtEpochMs?.let(Instant::fromEpochMilliseconds),
        )
    }
}

@Serializable
data class BuilderStatsDto(val foodsAdded: Int = 0, val checks: Int = 0, val rejected: Int = 0)

@Serializable
data class BuilderApplicationRequestDto(
    val acceptTerms: Boolean,
    val termsVersion: String,
    val photoRetention: Boolean,
    val motivation: String?,
    val shopping: String,
    val region: String,
    val via: String,
)

@Serializable
data class BuilderSettingsDto(val photoRetention: Boolean)

/**
 * [BuilderProgram] over `/v1/builders`. Kept in memory only: the server's answer is cheap and
 * comes back on every account edge (sign-in, foreground, reconnect), and a stale "you're a
 * Builder" on disk would show Builder mode to someone who left on another phone. The one thing
 * on disk is which decision this phone has shown ([AppData.builderDecisionSeen]). Every answer
 * that finds someone newly a Builder, or no longer one, refreshes the account's entitlements,
 * since the role brings or takes Pro; Opson's Accept happens elsewhere, so that is on any read.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = BuilderProgram::class)
@ContributesBinding(AppScope::class, boundType = UserScopedSyncer::class, multibinding = true)
@ContributesBinding(AppScope::class, boundType = UserScopedClearer::class, multibinding = true)
@Inject
class HttpBuilderProgram(
    private val networkClient: NetworkClient,
    private val entitlements: EntitlementRepository,
    private val appCache: AppCache,
) : BuilderProgram, UserScopedSyncer, UserScopedClearer {

    private val _standing = MutableStateFlow<BuilderStanding?>(null)
    override val standing: StateFlow<BuilderStanding?> = _standing.asStateFlow()

    override suspend fun refresh(): BuilderResult = call("builders.me") { client -> client.get(ME_PATH) { expectSuccess = false } }

    override suspend fun sendApplication(form: BuilderApplicationForm): BuilderResult = call("builders.apply") { client ->
        client.post(APPLY_PATH) {
            expectSuccess = false
            contentType(ContentType.Application.Json)
            setBody(
                BuilderApplicationRequestDto(
                    acceptTerms = true,
                    termsVersion = TERMS_VERSION,
                    photoRetention = form.photoRetention,
                    motivation = form.motivation?.trim()?.take(MAX_MOTIVATION)?.ifEmpty { null },
                    shopping = form.shopping.wire,
                    region = form.region.wire,
                    via = form.via,
                ),
            )
        }
    }

    override suspend fun withdraw(): BuilderResult = call("builders.withdraw") { client -> client.post(WITHDRAW_PATH) { expectSuccess = false } }

    override suspend fun decisionSeen(id: String): Boolean = Catching { appCache.get().builderDecisionSeen == id }.getOrDefault(false)

    override suspend fun markDecisionSeen(id: String) {
        Catching { appCache.update { it.copy(builderDecisionSeen = id) } }
    }

    override suspend fun leave(): BuilderResult = call("builders.leave") { client -> client.post(LEAVE_PATH) { expectSuccess = false } }

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
                HttpStatusCode.OK, HttpStatusCode.Created -> BuilderResult.Done(publish(response.body<BuilderMeDto>().toStanding()))
                // A server from before the program.
                HttpStatusCode.NotFound -> BuilderResult.Unavailable
                HttpStatusCode.Forbidden, HttpStatusCode.Conflict -> blocked(response.bodyAsText())
                else -> BuilderResult.Failed(offline = false)
            }
        }.getOrElse { BuilderResult.Failed(offline = it.isOfflineError()) }

    /**
     * Holds [fresh] as the standing, and refreshes entitlements when it changes whether this
     * account is a Builder, which only an answer can tell: Opson accepts in its console, not here.
     */
    private suspend fun publish(fresh: BuilderStanding): BuilderStanding {
        val previous = _standing.value
        _standing.value = fresh
        if (previous != null && previous.isActive != fresh.isActive) entitlements.refresh()
        return fresh
    }

    private fun blocked(body: String): BuilderResult =
        JoinBlock.entries.firstOrNull { "\"${it.wire}\"" in body }?.let(BuilderResult::Blocked) ?: BuilderResult.Failed(offline = false)

    internal companion object {
        const val ME_PATH = "/v1/builders/me"
        const val APPLY_PATH = "/v1/builders/applications"
        const val WITHDRAW_PATH = "/v1/builders/applications/withdraw"
        const val LEAVE_PATH = "/v1/builders/leave"
        const val MAX_MOTIVATION = 500

        /** The rules the app shows. The server refuses a join that accepted any other version. */
        const val TERMS_VERSION = "2026-10-29"
    }
}

@Serializable
data class VerificationRequestDto(
    val id: String,
    val engine: String,
    val lines: List<CaptureLineDto>,
    val problem: VerifyProblemDto? = null,
    val photos: VerifyPhotosDto? = null,
)

/** The server's `problem`: field codes, the right values for a basis, a note. */
@Serializable
data class VerifyProblemDto(
    val wrong: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
    val values: VerifyValuesDto? = null,
    val note: String? = null,
)

@Serializable
data class VerifyValuesDto(
    val name: String? = null,
    val brand: String? = null,
    val barcode: String? = null,
    val basisLabel: String? = null,
    val basisGrams: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val servingLabel: String? = null,
    val servingGrams: Double? = null,
)

/** Base64 JPEGs. */
@Serializable
data class VerifyPhotosDto(val front: String? = null, val label: String? = null)

@Serializable
data class VerificationResponseDto(
    val id: String,
    val outcome: String,
    val grounded: Int = 0,
    val groundable: Int = 0,
    val missing: List<String> = emptyList(),
    /** `open` when it waits for a reviewer; absent otherwise, and from servers before reviews. */
    val review: String? = null,
    /**
     * `agreement` when the label matched but the food shows as Verified only once a second
     * Builder's check or a reviewer agrees. Absent from servers before that rule, which mark it at once.
     */
    val awaiting: String? = null,
)

/**
 * `POST /v1/foods/{id}/verifications`. Not retried by itself; a check is one tap, and the same id
 * on "Try again" means a check that did arrive is not doubled. Nothing in it is logged.
 */
@ContributesBinding(AppScope::class)
@Inject
class HttpFoodChecks(
    private val networkClient: NetworkClient,
    private val attestation: ContributionAttestation = ContributionAttestation.None,
) : FoodChecks {
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun verify(verification: FoodVerification): FoodCheckResult {
        val request = VerificationRequestDto(
            id = verification.id,
            engine = verification.engine,
            lines = verification.lines.map { CaptureLineDto(it.text, it.confidence) },
            problem = verification.problem?.takeUnless { it.isEmpty }?.toDto(),
            photos = if (verification.front == null && verification.label == null) {
                null
            } else {
                VerifyPhotosDto(verification.front?.let { Base64.encode(it) }, verification.label?.let { Base64.encode(it) })
            },
        )
        val device = attestation.headers(verification.id)
        return networkClient.authedCall("foods.verification") { client ->
            val response = client.post("/v1/foods/${verification.foodId.encodeURLPathPart()}/verifications") {
                expectSuccess = false
                device.forEach { (name, value) -> header(name, value) }
                timeout {
                    requestTimeoutMillis = HttpFoodReports.TIMEOUT_MS
                    socketTimeoutMillis = HttpFoodReports.TIMEOUT_MS
                }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            when (response.status) {
                HttpStatusCode.Created, HttpStatusCode.OK -> response.body<VerificationResponseDto>().let { body ->
                    when {
                        body.awaiting == AWAITING_AGREEMENT -> FoodCheckResult.AwaitingAgreement
                        body.review == REVIEW_OPEN -> FoodCheckResult.InReview
                        body.outcome == CONFIRMED -> FoodCheckResult.Confirmed
                        else -> FoodCheckResult.Unconfirmed(body.missing)
                    }
                }
                HttpStatusCode.Forbidden -> FoodCheckResult.NotBuilder
                HttpStatusCode.NotFound -> FoodCheckResult.Gone
                HttpStatusCode.TooManyRequests -> FoodCheckResult.TooMany
                else -> FoodCheckResult.Failed(offline = false)
            }
        }.getOrElse { FoodCheckResult.Failed(offline = it.isOfflineError()) }
    }

    private fun VerifyProblem.toDto() = VerifyProblemDto(
        wrong = VerifyField.entries.filter { it in wrong }.map { it.wire },
        // A field is wrong or missing, never both: wrong wins.
        missing = VerifyField.entries.filter { it in missing && it !in wrong }.map { it.wire },
        values = values?.takeUnless { it.isEmpty }?.let {
            VerifyValuesDto(
                name = it.name?.trim()?.ifEmpty { null },
                brand = it.brand?.trim()?.ifEmpty { null },
                barcode = it.barcode?.trim()?.ifEmpty { null },
                basisLabel = it.basisLabel,
                basisGrams = it.basisGrams,
                kcal = it.kcal,
                protein = it.protein,
                carbs = it.carbs,
                fat = it.fat,
                servingLabel = it.servingLabel?.trim()?.ifEmpty { null },
                servingGrams = it.servingGrams,
            )
        },
        note = note?.trim()?.ifEmpty { null }?.take(MAX_NOTE),
    )

    private companion object {
        const val CONFIRMED = "confirmed"
        const val REVIEW_OPEN = "open"
        const val AWAITING_AGREEMENT = "agreement"
        const val MAX_NOTE = 1_000
    }
}
