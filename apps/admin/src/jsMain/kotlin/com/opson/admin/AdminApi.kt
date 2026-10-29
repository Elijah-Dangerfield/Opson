package com.opson.admin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.engine.js.Js
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Wire shapes mirroring the server's `ConfigAdminRoutes` DTOs. Kept as a small
 * standalone copy (this module shares no code with the server) so the admin
 * tool stays a single, dependency-light JS target.
 */
@Serializable
data class RuleConditions(
    val platforms: Set<String>? = null,
    val minVersionCode: Int? = null,
    val maxVersionCode: Int? = null,
    val minAppVersion: String? = null,
    val minAppVersionInclusive: Boolean = true,
    val maxAppVersion: String? = null,
    val maxAppVersionInclusive: Boolean = true,
    val countries: Set<String>? = null,
    val locales: Set<String>? = null,
    val userAllow: Set<String>? = null,
    val userDeny: Set<String>? = null,
    val rolloutPercent: Int? = null,
)

@Serializable
data class ConfigRuleDto(
    val id: String,
    val flagPath: String,
    val priority: Int,
    val value: JsonElement,
    val conditions: RuleConditions,
    val enabled: Boolean,
    val description: String? = null,
)

@Serializable
data class ConfigFlagDto(
    val path: String,
    val value: JsonElement,
    val updatedAtEpochMs: Long,
    val rules: List<ConfigRuleDto>,
)

@Serializable
private data class ConfigListResponse(val flags: List<ConfigFlagDto>)

@Serializable
private data class UpsertFlagRequest(val value: JsonElement)

@Serializable
data class UpsertRuleRequest(
    val flagPath: String,
    val priority: Int,
    val value: JsonElement,
    val conditions: RuleConditions = RuleConditions(),
    val enabled: Boolean = true,
    val description: String? = null,
)

@Serializable
data class ConfigAuditDto(
    val id: String,
    val atEpochMs: Long,
    val actor: String,
    val action: String,
    val flagPath: String? = null,
    val before: JsonElement? = null,
    val after: JsonElement? = null,
)

@Serializable
private data class AuditListResponse(val entries: List<ConfigAuditDto>)

// ---------- Manifest (per-version in-code defaults) ----------

@Serializable
data class ManifestEntryDto(
    val path: String,
    val type: String,
    val default: JsonElement,
    val description: String? = null,
    val allowedValues: JsonElement? = null,
)

@Serializable
data class ManifestVersionDto(
    val versionCode: Int,
    val appVersion: String? = null,
    val capturedAtEpochMs: Long,
    val flagCount: Int,
)

@Serializable
private data class ManifestVersionsResponse(val versions: List<ManifestVersionDto>)

@Serializable
data class ManifestResponse(
    val versionCode: Int? = null,
    val appVersion: String? = null,
    val entries: List<ManifestEntryDto>,
)

// ---------- Resolve (per-target preview) ----------

@Serializable
data class ResolveRequest(
    val platform: String? = null,
    val appVersion: String? = null,
    val buildNumber: Int? = null,
    val countryCode: String? = null,
    val locale: String? = null,
    val userId: String? = null,
    val installId: String? = null,
)

@Serializable
data class MatchedRuleDto(val id: String, val priority: Int, val description: String? = null)

@Serializable
data class ResolvedFlagDto(
    val path: String,
    val type: String? = null,
    val default: JsonElement? = null,
    val base: JsonElement? = null,
    val matchedRule: MatchedRuleDto? = null,
    val resolved: JsonElement? = null,
)

@Serializable
private data class ResolveResponse(val flags: List<ResolvedFlagDto>)

// ---------- Website waitlist ----------

@Serializable
data class WaitlistEntryDto(
    val id: String,
    val email: String,
    val source: String? = null,
    val createdAtEpochMs: Long,
)

@Serializable
data class WaitlistPageDto(val total: Long, val entries: List<WaitlistEntryDto>)

@Serializable
private data class WaitlistRemoveRequest(val email: String)

// ---------- User lookup ----------

@Serializable
data class UserSummaryDto(
    val userId: String,
    val email: String? = null,
    val isGuest: Boolean = false,
    val displayName: String? = null,
    val joinedAtEpochMs: Long? = null,
    val lastSignInAtEpochMs: Long? = null,
    val lastSeenAtEpochMs: Long? = null,
    val platform: String? = null,
    val appVersion: String? = null,
    val buildNumber: Int? = null,
    val preset: String? = null,
    val entries: Long = 0,
    val daysLogged: Long = 0,
    val lastLogDay: String? = null,
)

@Serializable
data class UserInstallDto(
    val installId: String,
    val platform: String,
    val appVersion: String,
    val buildNumber: Int,
    val firstSeenAtEpochMs: Long,
    val lastSeenAtEpochMs: Long,
)

@Serializable
data class UserSettingDto(val key: String, val value: String? = null)

@Serializable
data class UserDetailDto(
    val user: UserSummaryDto,
    val installs: List<UserInstallDto> = emptyList(),
    val settings: List<UserSettingDto> = emptyList(),
    val methods: Map<String, Long> = emptyMap(),
    val entriesLast7Days: Long = 0,
    val entriesLast30Days: Long = 0,
    val daysClosed: Long = 0,
    val lastSyncedChangeAtEpochMs: Long? = null,
)

@Serializable
private data class UserSearchRequest(val query: String)

@Serializable
private data class UserSearchResponse(val users: List<UserSummaryDto>)

// ---------- Food review ----------

/** A label's facts as the server keeps them: nutrients by their `micros` key (`energy_kcal`, `sodium_mg`). */
@Serializable
data class LabelFactsDto(
    val name: String? = null,
    val brand: String? = null,
    val barcode: String? = null,
    val servingSize: Double? = null,
    val servingUnit: String? = null,
    val servingLabel: String? = null,
    val servingsPerContainer: Double? = null,
    val basis: String = "unknown",
    val perServing: Map<String, Double> = emptyMap(),
    val per100: Map<String, Double> = emptyMap(),
    val acknowledged: List<String> = emptyList(),
)

@Serializable
data class LabelCheckDto(val code: String, val fields: List<String> = emptyList(), val expected: Double? = null, val actual: Double? = null)

@Serializable
data class LabelReadingDto(
    val facts: LabelFactsDto,
    val confidence: Map<String, Double> = emptyMap(),
    val readable: Boolean = true,
    val unitChecks: List<LabelCheckDto> = emptyList(),
)

@Serializable
data class SubmissionSummaryDto(
    val id: String,
    val createdAtEpochMs: Long,
    val status: String,
    val origin: String,
    val name: String? = null,
    val brand: String? = null,
    val barcode: String? = null,
    val failedChecks: List<String> = emptyList(),
    val hasPhotos: Boolean = false,
)

@Serializable
data class SubmissionListDto(val total: Long, val submissions: List<SubmissionSummaryDto>)

@Serializable
data class SubmissionDetailDto(
    val summary: SubmissionSummaryDto,
    val foodId: String,
    val model: String? = null,
    val parsed: LabelReadingDto? = null,
    val parsedChecks: List<LabelCheckDto> = emptyList(),
    val submitted: LabelFactsDto,
    val reviewed: LabelFactsDto? = null,
    val checks: List<LabelCheckDto> = emptyList(),
    val reviewedAtEpochMs: Long? = null,
    val reviewedBy: String? = null,
    val rejectReason: String? = null,
)

@Serializable
private data class ReviewEditRequest(val food: LabelFactsDto? = null)

@Serializable
private data class ReviewRejectRequest(val reason: String)

@Serializable
data class BarcodeLinkDto(
    val gtin: String,
    val barcode: String,
    val foodId: String,
    val foodName: String,
    val foodBrand: String? = null,
    val foodBarcode: String? = null,
    val pairings: Int,
    val firstAtEpochMs: Long,
    val lastAtEpochMs: Long,
    val status: String,
)

@Serializable
private data class BarcodeLinkListDto(val links: List<BarcodeLinkDto>)

@Serializable
private data class BarcodeLinkDecision(val gtin: String, val foodId: String)

val adminJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

/**
 * Thin client over the server's `/v1/admin` routes. One instance per connection (a given
 * base URL + token + actor). Throws [AdminApiException] with the server's
 * problem message on a non-2xx so the UI can show why a write was refused.
 */
class AdminApi(
    baseUrl: String,
    private val token: String,
    private val actor: String,
) {
    private val base = baseUrl.trimEnd('/')
    private val client = HttpClient(Js) {
        install(ContentNegotiation) { json(adminJson) }
    }

    /** Roles and entitlements for one account, and the policy overview. */
    val access: AccessApi = AccessApi(baseUrl, token, actor)

    /** Bans, restrictions, rollbacks and device markers for one account. */
    val moderation: ModerationApi = ModerationApi(baseUrl, token, actor)

    suspend fun listFlags(): List<ConfigFlagDto> =
        client.get("$base/v1/admin/config") { auth() }
            .require<ConfigListResponse>().flags

    suspend fun upsertFlag(path: String, value: JsonElement) {
        client.put("$base/v1/admin/config/flags/${encode(path)}") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(UpsertFlagRequest(value))
        }.requireOk()
    }

    suspend fun deleteFlag(path: String) {
        client.delete("$base/v1/admin/config/flags/${encode(path)}") { auth() }.requireOk()
    }

    suspend fun upsertRule(id: String, request: UpsertRuleRequest) {
        client.put("$base/v1/admin/config/rules/$id") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireOk()
    }

    suspend fun deleteRule(id: String) {
        client.delete("$base/v1/admin/config/rules/$id") { auth() }.requireOk()
    }

    suspend fun listAudit(flag: String?): List<ConfigAuditDto> {
        val suffix = if (flag.isNullOrBlank()) "" else "?flag=${encode(flag)}"
        return client.get("$base/v1/admin/config/audit$suffix") { auth() }
            .require<AuditListResponse>().entries
    }

    suspend fun listManifestVersions(): List<ManifestVersionDto> =
        client.get("$base/v1/admin/config/manifest/versions") { auth() }
            .require<ManifestVersionsResponse>().versions

    suspend fun getManifest(versionCode: Int?): ManifestResponse {
        val suffix = if (versionCode == null) "" else "?version=$versionCode"
        return client.get("$base/v1/admin/config/manifest$suffix") { auth() }.require()
    }

    suspend fun resolve(request: ResolveRequest): List<ResolvedFlagDto> =
        client.post("$base/v1/admin/config/resolve") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.require<ResolveResponse>().flags

    suspend fun listWaitlist(limit: Int, offset: Long): WaitlistPageDto =
        client.get("$base/v1/admin/waitlist?limit=$limit&offset=$offset") { auth() }.require()

    /** The whole list as CSV (`email,source,created_at`), for the operator to download. */
    suspend fun exportWaitlistCsv(): String =
        client.get("$base/v1/admin/waitlist/export.csv") { auth() }
            .also { it.requireOk() }
            .bodyAsText()

    /** Deletes [email] from the waitlist. The address goes in the body, never the URL. */
    suspend fun removeFromWaitlist(email: String) {
        client.post("$base/v1/admin/waitlist/remove") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(WaitlistRemoveRequest(email))
        }.requireOk()
    }

    /** Accounts by id, or part of an email or display name. The query goes in the body, never the URL. */
    suspend fun searchUsers(query: String): List<UserSummaryDto> =
        client.post("$base/v1/admin/users/search") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(UserSearchRequest(query))
        }.require<UserSearchResponse>().users

    suspend fun userDetail(userId: String): UserDetailDto =
        client.get("$base/v1/admin/users/${encode(userId)}") { auth() }.require()

    suspend fun listSubmissions(status: String, limit: Int = 50): SubmissionListDto =
        client.get("$base/v1/admin/food-submissions?status=${encode(status)}&limit=$limit") { auth() }.require()

    suspend fun submission(id: String): SubmissionDetailDto =
        client.get("$base/v1/admin/food-submissions/${encode(id)}") { auth() }.require()

    /**
     * A submission's photo, `front` or `label`, as bytes. Fetched with the token like everything
     * else: an `<img src>` cannot carry the header, so the view turns these into a blob URL that
     * lives only in this tab.
     */
    suspend fun submissionPhoto(id: String, kind: String): ByteArray =
        client.get("$base/v1/admin/food-submissions/${encode(id)}/photos/${encode(kind)}") { auth() }
            .also { it.requireOk() }
            .readRawBytes()

    suspend fun editSubmission(id: String, food: LabelFactsDto): SubmissionDetailDto =
        client.put("$base/v1/admin/food-submissions/${encode(id)}") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(ReviewEditRequest(food))
        }.require()

    suspend fun approveSubmission(id: String, food: LabelFactsDto?): SubmissionDetailDto =
        client.post("$base/v1/admin/food-submissions/${encode(id)}/approve") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(ReviewEditRequest(food))
        }.require()

    suspend fun rejectSubmission(id: String, reason: String): SubmissionDetailDto =
        client.post("$base/v1/admin/food-submissions/${encode(id)}/reject") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(ReviewRejectRequest(reason))
        }.require()

    suspend fun listBarcodeLinks(status: String = "pending"): List<BarcodeLinkDto> =
        client.get("$base/v1/admin/barcode-links?status=${encode(status)}") { auth() }.require<BarcodeLinkListDto>().links

    suspend fun attachBarcodeLink(gtin: String, foodId: String) {
        client.post("$base/v1/admin/barcode-links/attach") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(BarcodeLinkDecision(gtin, foodId))
        }.requireOk()
    }

    suspend fun dismissBarcodeLink(gtin: String, foodId: String) {
        client.post("$base/v1/admin/barcode-links/dismiss") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(BarcodeLinkDecision(gtin, foodId))
        }.requireOk()
    }

    suspend fun listFoodReports(limit: Int = 50): FoodReportQueueDto =
        client.get("$base/v1/admin/food-reports?limit=$limit") { auth() }.require()

    suspend fun foodReports(foodId: String): FoodReportFoodDto =
        client.get("$base/v1/admin/food-reports/foods/${encode(foodId)}") { auth() }.require()

    /** A report's label photo, as bytes for a blob URL, like [submissionPhoto]. */
    suspend fun foodReportPhoto(id: String): ByteArray =
        client.get("$base/v1/admin/food-reports/${encode(id)}/photo") { auth() }
            .also { it.requireOk() }
            .readRawBytes()

    suspend fun acceptFoodReports(foodId: String, reportIds: List<String>, values: CorrectionValuesDto): FoodCorrectionDto =
        client.post("$base/v1/admin/food-reports/foods/${encode(foodId)}/accept") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(AcceptReportsDto(reportIds, values))
        }.require()

    suspend fun rejectFoodReports(reportIds: List<String>, reason: String?) {
        client.post("$base/v1/admin/food-reports/reject") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CloseReportsDto(reportIds, reason = reason))
        }.requireOk()
    }

    suspend fun markFoodReportsDuplicate(reportIds: List<String>, duplicateOf: String?) {
        client.post("$base/v1/admin/food-reports/duplicate") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CloseReportsDto(reportIds, duplicateOf = duplicateOf))
        }.requireOk()
    }

    suspend fun revertFoodCorrection(id: String): FoodCorrectionDto =
        client.post("$base/v1/admin/food-corrections/${encode(id)}/revert") { auth() }.require()

    private fun io.ktor.client.request.HttpRequestBuilder.auth() {
        header("X-Admin-Token", token)
        header("X-Admin-Actor", actor)
    }

    private suspend inline fun <reified T> io.ktor.client.statement.HttpResponse.require(): T {
        requireOk()
        return body()
    }

    private suspend fun io.ktor.client.statement.HttpResponse.requireOk() {
        if (status.isSuccess()) return
        val message = Catching {
            adminJson.parseToJsonElement(bodyAsText())
        }.getOrNull()?.let { describeError(it) } ?: "HTTP ${status.value}"
        throw AdminApiException(status, message)
    }

    private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299

    private fun encode(segment: String): String = segment.encodeURIComponentSafe()
}

class AdminApiException(val status: HttpStatusCode, message: String) :
    RuntimeException("${status.value}: $message")

private fun describeError(body: JsonElement): String? = Catching {
    val error = (body as? kotlinx.serialization.json.JsonObject)?.get("error")
        as? kotlinx.serialization.json.JsonObject
    error?.get("message")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
}.getOrNull()

/** Browser-native URI component encoding (path segments can hold dots/etc.). */
private fun String.encodeURIComponentSafe(): String = encodeURIComponent(this)

private fun encodeURIComponent(value: String): String =
    js("encodeURIComponent(value)") as String
