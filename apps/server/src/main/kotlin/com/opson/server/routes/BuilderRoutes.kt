package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.data.CaptureLineJson
import com.opson.server.domain.BuilderRules
import com.opson.server.domain.BuilderService
import com.opson.server.domain.BuilderStanding
import com.opson.server.domain.BuilderStatus
import com.opson.server.domain.TrustLevel
import com.opson.server.domain.BuilderRepository
import com.opson.server.domain.CaptureLine
import com.opson.server.domain.CatalogSource
import com.opson.server.domain.ContributionGate
import com.opson.server.domain.OpenContributionGate
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.FoodLabelLimiter
import com.opson.server.domain.FoodLabelLimits
import com.opson.server.domain.FoodSubmissionRepository
import com.opson.server.domain.FoodVerification
import com.opson.server.domain.FoodVerificationRepository
import com.opson.server.domain.JoinBlock
import com.opson.server.domain.JoinOutcome
import com.opson.server.domain.Permission
import com.opson.server.domain.Permissions
import com.opson.server.domain.UserId
import com.opson.server.domain.VerificationCheck
import com.opson.server.domain.VerificationOutcome
import com.opson.server.domain.VerifyOutcome
import com.opson.server.plugins.FOOD_REPORT_LIMIT
import com.opson.server.plugins.SUPABASE_JWT_AUTH
import com.opson.server.plugins.isAnonymousUser
import com.opson.server.plugins.problem
import com.opson.server.plugins.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * One person's place in the Builder program, as the app's Builder page draws it. [status] is
 * `none`, `active`, `left` or `removed`; [trust] a [com.opson.server.domain.TrustLevel] wire;
 * [joinBlock] why Join is not offered (`guest`, `too_new`, `removed`, `full`), with
 * [availableAtEpochMs] for `too_new`. [accepted30d] of [needed] keeps Pro on; [proUntilEpochMs]
 * is when Builder Pro runs out.
 */
@Serializable
data class BuilderMeResponse(
    val status: String,
    val trust: String,
    val joinedAtEpochMs: Long? = null,
    val proUntilEpochMs: Long? = null,
    val accepted30d: Int = 0,
    val needed: Int = BuilderRules.PRO_THRESHOLD,
    val photoRetention: Boolean = false,
    val canJoin: Boolean = false,
    val joinBlock: String? = null,
    val availableAtEpochMs: Long? = null,
    val termsVersion: String = BuilderRules.TERMS_VERSION,
)

@Serializable
data class BuilderJoinRequest(val acceptTerms: Boolean = false, val termsVersion: String? = null, val photoRetention: Boolean = false)

@Serializable
data class BuilderSettingsRequest(val photoRetention: Boolean? = null)

@Serializable
data class VerificationRequest(val id: String? = null, val engine: String? = null, val lines: List<CaptureLineJson> = emptyList())

/** [missing] are the headline fields (`energy_kcal`, `fat_g`) the label's text did not show. */
@Serializable
data class VerificationResponse(val id: String, val outcome: String, val grounded: Int, val groundable: Int, val missing: List<String> = emptyList())

@Serializable
data class AdminBuilderResponse(
    val me: BuilderMeResponse,
    val role: String,
    val trustHold: Boolean,
    val trustedSinceEpochMs: Long? = null,
    val removedReason: String? = null,
    val removedBy: String? = null,
    val reviewedTotal: Int,
    val agreementLast50: Double? = null,
    val decided30d: Int,
    val rejected30d: Int,
    val fakeOrCopied: Int,
    val audits30d: Int,
    val auditsFailed30d: Int,
    val autoApprovedSinceTrusted: Int,
)

@Serializable
data class AdminBuilderActionRequest(val action: String? = null, val reason: String? = null)

/**
 * The Builder program (builder-program.md), behind `release.builders` in the app:
 *
 *  - `GET  /v1/builders/me`            where the caller stands
 *  - `POST /v1/builders/join {acceptTerms, termsVersion, photoRetention}` joins: the `builder`
 *    role, and Pro while active. 400 `terms_required`; 403 `guest`, `too_new`, `removed`;
 *    409 `full`.
 *  - `POST /v1/builders/leave`         leaves; the role goes, the record stays
 *  - `PUT  /v1/builders/me {photoRetention}` changes the optional photo consent
 *  - `POST /v1/foods/{foodId}/verifications {id, engine, lines}` a Builder checked a food
 *    against its pack: the label's text, read on the phone. Confirmed when every headline
 *    number of the food is in it. No photo. 403 `not_a_builder`, 404, 409 `id_conflict`, 429.
 *
 * Self-serve, as builder-program.md's open enrolment has it, bounded by `builders.capacity`
 * rather than a waitlist: the same gates keep out guests and brand-new accounts, and the owner
 * can set the cap low for a closed alpha. Nothing logged names a food or a number.
 */
@OptIn(ExperimentalTime::class)
@Suppress("LongParameterList", "LongMethod")
fun Route.builderRoutes(
    builders: BuilderService,
    permissions: Permissions,
    catalog: FoodCatalogRepository,
    submissions: FoodSubmissionRepository,
    verifications: FoodVerificationRepository,
    limiter: FoodLabelLimiter,
    clock: Clock,
    contributionGate: ContributionGate = OpenContributionGate,
) {
    val logger = LoggerFactory.getLogger("BuilderRoutes")
    authenticate(SUPABASE_JWT_AUTH) {
        route("/v1/builders") {
            get("/me") {
                val userId = call.userId() ?: return@get call.unauthorized()
                call.respond(builders.standing(userId, call.isAnonymousUser()).toMe())
            }

            put("/me") {
                val userId = call.userId() ?: return@put call.unauthorized()
                val keep = call.receive<BuilderSettingsRequest>().photoRetention
                    ?: return@put call.respond(HttpStatusCode.BadRequest, problem("invalid_settings", "photoRetention is required"))
                builders.setPhotoRetention(userId, keep)
                call.respond(builders.standing(userId, call.isAnonymousUser()).toMe())
            }

            post("/join") {
                val userId = call.userId() ?: return@post call.unauthorized()
                val request = call.receive<BuilderJoinRequest>()
                if (!request.acceptTerms || request.termsVersion != BuilderRules.TERMS_VERSION) {
                    return@post call.respond(HttpStatusCode.BadRequest, problem("terms_required", "Accept the Builder rules first."))
                }
                when (val outcome = builders.join(userId, call.isAnonymousUser(), request.photoRetention)) {
                    is JoinOutcome.Joined -> {
                        logger.info("builder joined retention={}", request.photoRetention)
                        call.respond(outcome.standing.toMe())
                    }
                    is JoinOutcome.Blocked -> {
                        logger.info("builder join blocked code={}", outcome.block.code)
                        val status = if (outcome.block == JoinBlock.Full) HttpStatusCode.Conflict else HttpStatusCode.Forbidden
                        call.respond(status, problem(outcome.block.code, outcome.block.message()))
                    }
                    JoinOutcome.NoSuchUser -> call.unauthorized()
                }
            }

            post("/leave") {
                val userId = call.userId() ?: return@post call.unauthorized()
                if (builders.leave(userId)) logger.info("builder left")
                call.respond(builders.standing(userId, call.isAnonymousUser()).toMe())
            }
        }

        rateLimit(RateLimitName(FOOD_REPORT_LIMIT)) {
            post("/v1/foods/{foodId}/verifications") {
                val userId = call.userId() ?: return@post call.unauthorized()
                val foodId = call.parameters["foodId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_id", "The food id must be a UUID"))
                val request = call.receive<VerificationRequest>()
                val id = request.id?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_id", "id must be a UUID"))
                if (request.lines.size > FoodLabelLimits.MAX_CAPTURE_LINES || request.lines.any { it.text.length > FoodLabelLimits.MAX_CAPTURE_LINE }) {
                    return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_capture", "Too much text"))
                }
                if (!permissions.can(userId, Permission.FoodContribute)) {
                    return@post call.respond(HttpStatusCode.Forbidden, problem("not_a_builder", "Checking foods is for Builders."))
                }
                if (call.refuseIfRestricted(contributionGate, userId, id.toString(), logger, "food verification")) return@post
                val food = catalog.findById(foodId)
                    ?.takeIf { it.source != CatalogSource.UserSubmitted || submissions.isVisible(foodId, userId) }
                    ?: return@post call.respond(HttpStatusCode.NotFound, problem("not_found", "No such food"))
                if (!limiter.tryVerify(userId)) {
                    return@post call.respond(HttpStatusCode.TooManyRequests, problem("rate_limited", "That's a lot of checks for now. Try again later."))
                }
                val lines = request.lines.map { CaptureLine(it.text, it.confidence) }
                val grounding = VerificationCheck.ground(food.per100g, food.portions.firstOrNull()?.grams, lines)
                val outcome = if (grounding.complete && grounding.groundable == VerificationCheck.HEADLINE) {
                    VerificationOutcome.Confirmed
                } else {
                    VerificationOutcome.Unconfirmed
                }
                val recorded = verifications.record(
                    FoodVerification(id, foodId, userId, clock.now(), outcome, grounding.grounded, grounding.groundable, request.engine),
                )
                when (recorded) {
                    is VerifyOutcome.Recorded -> {
                        logger.info("food verification outcome={} grounded={}/{}", recorded.verification.outcome.wire, grounding.grounded, grounding.groundable)
                        call.respond(HttpStatusCode.Created, recorded.verification.toResponse(grounding.missing))
                    }
                    is VerifyOutcome.Existing -> call.respond(recorded.verification.toResponse(emptyList()))
                    VerifyOutcome.Conflict -> call.respond(HttpStatusCode.Conflict, problem("id_conflict", "That id is taken"))
                }
            }
        }
    }
}

/**
 * The console's view of one Builder, under the Users › Access page:
 *
 *  - `GET  /v1/admin/users/{id}/builder`  standing, trust and the record behind it
 *  - `POST /v1/admin/users/{id}/builder {action, reason?}` `remove` (with a reason), `restore`,
 *    `hold` (keep at Builder whatever the record says) or `release`
 */
@OptIn(ExperimentalTime::class)
fun Route.builderAdminRoutes(config: AdminConfig, builders: BuilderService, repository: BuilderRepository) {
    route("/v1/admin/users/{id}/builder") {
        get {
            if (!call.requireAdmin(config)) return@get
            val userId = call.adminUserId() ?: return@get
            call.respond(builders.standing(userId).toAdmin())
        }

        post {
            if (!call.requireAdmin(config)) return@post
            val userId = call.adminUserId() ?: return@post
            val request = call.receive<AdminBuilderActionRequest>()
            val actor = call.request.header("X-Admin-Actor")?.trim()?.take(MAX_ACTOR)?.ifEmpty { null } ?: "admin"
            val reason = request.reason?.trim()?.ifEmpty { null }?.take(MAX_REASON)
            val done = when (request.action) {
                "remove" -> builders.remove(userId, actor, reason ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_reason", "Say why.")))
                "restore" -> builders.restore(userId, actor)
                "hold" -> repository.setTrustHold(userId, true)
                "release" -> repository.setTrustHold(userId, false)
                else -> return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_action", "remove, restore, hold or release"))
            }
            if (!done) return@post call.respond(HttpStatusCode.NotFound, problem("not_found", "Not a Builder."))
            call.respond(builders.standing(userId).toAdmin())
        }
    }
}

private suspend fun ApplicationCall.unauthorized() = respond(HttpStatusCode.Unauthorized, problem("unauthorized", "Sign in again"))

private suspend fun ApplicationCall.adminUserId(): UserId? {
    val id = parameters["id"]?.let(UserId::parse)
    if (id == null) respond(HttpStatusCode.BadRequest, problem("invalid_user_id", "Not a user id."))
    return id
}

@OptIn(ExperimentalTime::class)
private fun JoinBlock.message(): String = when (this) {
    JoinBlock.Guest -> "Sign in with an account to join."
    is JoinBlock.TooNew -> "Add a food from its label first, or try again in a few days."
    JoinBlock.Removed -> "This account can't join Builders."
    JoinBlock.Full -> "Builders is full right now."
}

@OptIn(ExperimentalTime::class)
internal fun BuilderStanding.toMe() = BuilderMeResponse(
    // A ban or restriction takes the builder role away (ModerationService) and leaves the row: show it as removed.
    status = if (membership?.status == BuilderStatus.Active && trust == TrustLevel.Anyone) BuilderStatus.Removed.wire else membership?.status?.wire ?: "none",
    trust = trust.wire,
    joinedAtEpochMs = membership?.joinedAt?.toEpochMilliseconds(),
    proUntilEpochMs = proUntil?.toEpochMilliseconds(),
    accepted30d = history.accepted30d,
    photoRetention = membership?.photoRetention == true,
    canJoin = !isActive && joinBlock == null,
    joinBlock = joinBlock?.code,
    availableAtEpochMs = (joinBlock as? JoinBlock.TooNew)?.availableAt?.toEpochMilliseconds(),
)

@OptIn(ExperimentalTime::class)
private fun BuilderStanding.toAdmin() = AdminBuilderResponse(
    me = toMe(),
    role = role.wire,
    trustHold = membership?.trustHold == true,
    trustedSinceEpochMs = membership?.trustedSince?.toEpochMilliseconds(),
    removedReason = membership?.removedReason,
    removedBy = membership?.removedBy,
    reviewedTotal = history.reviewedTotal,
    agreementLast50 = history.reviewerAgreed.takeIf { it.isNotEmpty() }?.let { list -> list.count { it }.toDouble() / list.size },
    decided30d = history.decided30d,
    rejected30d = history.rejected30d,
    fakeOrCopied = history.fakeOrCopied,
    audits30d = history.audits30d,
    auditsFailed30d = history.auditsFailed30d,
    autoApprovedSinceTrusted = history.autoApprovedSinceTrusted,
)

private fun FoodVerification.toResponse(missing: List<String>) =
    VerificationResponse(id.toString(), outcome.wire, grounded, groundable, missing)

private const val MAX_ACTOR = 100
private const val MAX_REASON = 500
