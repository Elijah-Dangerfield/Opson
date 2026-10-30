package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.data.CaptureLineJson
import com.opson.server.data.VerificationPhotosJson
import com.opson.server.data.VerificationProblemJson
import com.opson.server.domain.ApplicantSummary
import com.opson.server.domain.ApplicationForm
import com.opson.server.domain.ApplicationStatus
import com.opson.server.domain.ApplyOutcome
import com.opson.server.domain.BuilderApplication
import com.opson.server.domain.BuilderApplicationRepository
import com.opson.server.domain.DecideOutcome
import com.opson.server.domain.LabelRegion
import com.opson.server.domain.ShoppingFrequency
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
import com.opson.server.domain.FoodVerificationLimits
import com.opson.server.domain.FoodVerificationRepository
import com.opson.server.domain.FoodSnapshot
import com.opson.server.domain.VerificationStatus
import com.opson.server.domain.JoinBlock
import com.opson.server.domain.Permission
import com.opson.server.domain.Permissions
import com.opson.server.domain.UserId
import com.opson.server.domain.VerificationCheck
import com.opson.server.domain.VerificationOutcome
import com.opson.server.domain.VerifyOutcome
import com.opson.server.plugins.FOOD_REPORT_LIMIT
import com.opson.server.plugins.SUPABASE_JWT_AUTH
import com.opson.server.plugins.bodyLimit
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
import kotlin.time.Instant

/**
 * One person's place in the Builder program, as the app's Builder page draws it. [status] is
 * `none`, `active`, `left` or `removed`; [trust] a [com.opson.server.domain.TrustLevel] wire.
 * [canJoin] says they may apply now (an app from before applications calls it Join, and its Join
 * now applies). [applyBlock] is why not (`guest`, `too_new`, `removed`, `pending`, `declined`), with
 * [availableAtEpochMs] for `too_new` and `declined`; [joinBlock] is the same in the four codes an
 * older app knows, `pending` and `declined` reading as `full`. [application] is their newest one.
 * [accepted30d] of [needed] keeps Pro on; [proUntilEpochMs] is when Builder Pro runs out.
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
    val applyBlock: String? = null,
    val application: BuilderApplicationResponse? = null,
    val stats: BuilderStatsResponse = BuilderStatsResponse(),
)

/**
 * An application as the applicant sees it: [status] `pending`, `accepted`, `declined` or
 * `withdrawn`; [note] is the owner's, if they wrote one; [reapplyAtEpochMs] is when a declined
 * applicant may try again.
 */
@Serializable
data class BuilderApplicationResponse(
    val id: String,
    val status: String,
    val submittedAtEpochMs: Long,
    val decidedAtEpochMs: Long? = null,
    val note: String? = null,
    val reapplyAtEpochMs: Long? = null,
)

/** What someone has done, for their Builder page: foods approved, checks that matched, submissions turned down. */
@Serializable
data class BuilderStatsResponse(val foodsAdded: Int = 0, val checks: Int = 0, val rejected: Int = 0)

/**
 * `POST /v1/builders/applications`. [shopping] is a [ShoppingFrequency] wire and [region] a
 * [LabelRegion] wire, both required; [motivation] is optional, at most 500 characters. [id] makes
 * a retry the same application.
 */
@Serializable
data class BuilderApplicationRequest(
    val id: String? = null,
    val acceptTerms: Boolean = false,
    val termsVersion: String? = null,
    val photoRetention: Boolean = false,
    val motivation: String? = null,
    val shopping: String? = null,
    val region: String? = null,
    val via: String? = null,
)

@Serializable
data class BuilderJoinRequest(val acceptTerms: Boolean = false, val termsVersion: String? = null, val photoRetention: Boolean = false)

@Serializable
data class BuilderSettingsRequest(val photoRetention: Boolean? = null)

@Serializable
data class VerificationRequest(
    val id: String? = null,
    val engine: String? = null,
    val lines: List<CaptureLineJson> = emptyList(),
    /** "Something was wrong" (V34): sends the check to a reviewer. */
    val problem: VerificationProblemJson? = null,
    /** The front and label photos (V34), kept only with a check that goes to a reviewer. */
    val photos: VerificationPhotosJson? = null,
)

/**
 * [missing] are the headline fields (`energy_kcal`, `fat_g`) the label's text did not show.
 * [review] is `open` when the check went to a reviewer (V34); absent otherwise, and always from a
 * server before V34, which is how the app knows to send a problem as a report instead.
 */
@Serializable
data class VerificationResponse(
    val id: String,
    val outcome: String,
    val grounded: Int,
    val groundable: Int,
    val missing: List<String> = emptyList(),
    val review: String? = null,
    /**
     * `agreement` on a match that counts once a second Builder's match or a reviewer agrees. It
     * also says `review: "open"`, which is what an app from before this reads.
     */
    val awaiting: String? = null,
)

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

/** The account behind an application, as the console's Builders tab shows it. Counts and dates only. */
@Serializable
data class ApplicantSummaryResponse(
    val email: String? = null,
    val displayName: String? = null,
    val isGuest: Boolean = false,
    val accountCreatedAtEpochMs: Long? = null,
    val entries: Long = 0,
    val daysLogged: Long = 0,
    val lastLogDay: String? = null,
    val submissionsApproved: Int = 0,
    val submissionsRejected: Int = 0,
    val submissionsPending: Int = 0,
    val reportsAccepted: Int = 0,
    val reportsRejected: Int = 0,
    val reportsOpen: Int = 0,
    val checks: Int = 0,
    val moderationActions: Int = 0,
    val moderationActive: Int = 0,
    val lastModerationReason: String? = null,
    val earlierApplications: Int = 0,
    val earlierDeclines: Int = 0,
    val membership: String? = null,
)

@Serializable
data class AdminApplicationResponse(
    val id: String,
    val userId: String,
    val status: String,
    val submittedAtEpochMs: Long,
    val termsVersion: String,
    val photoRetention: Boolean,
    val motivation: String? = null,
    val shopping: String? = null,
    val region: String? = null,
    val via: String? = null,
    val decidedAtEpochMs: Long? = null,
    val decidedBy: String? = null,
    val note: String? = null,
    val account: ApplicantSummaryResponse,
)

@Serializable
data class AdminApplicationListResponse(val pending: Long, val applications: List<AdminApplicationResponse>)

@Serializable
data class AdminApplicationDecisionRequest(val note: String? = null)

@Serializable
data class AdminApplicationDecisionResponse(val pending: Long, val application: AdminApplicationResponse)

/**
 * The Builder program (builder-program.md), behind `release.builders` in the app:
 *
 *  - `GET  /v1/builders/me`            where the caller stands, with their newest application
 *  - `POST /v1/builders/applications {id, acceptTerms, termsVersion, photoRetention, motivation?, shopping, region, via?}`
 *    applies; the owner decides in the console. 201 with the standing, 200 for a retry or an
 *    active Builder. 400 `terms_required`, `invalid_application`; 403 `guest`, `too_new`,
 *    `removed`, `declined` (with `availableAtEpochMs`).
 *  - `POST /v1/builders/applications/withdraw` takes back the pending one
 *  - `POST /v1/builders/join {acceptTerms, termsVersion, photoRetention}` is what an app from
 *    before applications sends from Join. It applies now, with no answers to the questions, and
 *    answers in the codes that app knows: a pending or declined application reads as `full`.
 *  - `POST /v1/builders/leave`         leaves; the role goes, the record stays
 *  - `PUT  /v1/builders/me {photoRetention}` changes the optional photo consent
 *  - `POST /v1/foods/{foodId}/verifications {id, engine, lines, problem?, photos?}` a Builder
 *    checked a food against its pack: the label's text, read on the phone. Confirmed when every
 *    headline number of the food is in it. With a `problem` ("Something was wrong"), or with
 *    `photos` and numbers the text did not show, it waits for a reviewer (`review: "open"`) with
 *    the photos and the food as served. 400 `invalid_problem`, `invalid_image`; 403
 *    `not_a_builder`, 404, 409 `id_conflict`, 413 `image_too_large`, 429.
 *
 * Nobody becomes a Builder through these: only the owner's Accept does (docs/decisions.md,
 * "Builders apply"). Nothing logged names a food, a number or what someone wrote.
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

            post("/applications") {
                val userId = call.userId() ?: return@post call.unauthorized()
                val request = call.receive<BuilderApplicationRequest>()
                if (!request.acceptTerms || request.termsVersion != BuilderRules.TERMS_VERSION) {
                    return@post call.respond(HttpStatusCode.BadRequest, problem("terms_required", "Accept the Builder rules first."))
                }
                val shopping = ShoppingFrequency.fromWire(request.shopping)
                val region = LabelRegion.fromWire(request.region)
                val motivation = request.motivation?.trim()?.ifEmpty { null }
                val id = request.id?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (shopping == null || region == null || (motivation?.length ?: 0) > MAX_MOTIVATION || (request.id != null && id == null)) {
                    return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_application", "Answer both questions, and keep the note short."))
                }
                val applicationId = id ?: UUID.randomUUID()
                val form = ApplicationForm(request.photoRetention, motivation, shopping, region, request.via?.take(MAX_VIA))
                when (val outcome = builders.apply(userId, call.isAnonymousUser(), form, applicationId)) {
                    is ApplyOutcome.Applied -> {
                        val created = outcome.created
                        if (created) logger.info("builder application sent region={} shopping={}", region.wire, shopping.wire)
                        call.respond(if (created) HttpStatusCode.Created else HttpStatusCode.OK, outcome.standing.toMe())
                    }
                    is ApplyOutcome.AlreadyBuilder -> call.respond(outcome.standing.toMe())
                    is ApplyOutcome.Blocked -> {
                        logger.info("builder application blocked code={}", outcome.block.code)
                        call.respond(HttpStatusCode.Forbidden, outcome.block.problem(legacy = false))
                    }
                    ApplyOutcome.NoSuchUser -> call.unauthorized()
                }
            }

            post("/applications/withdraw") {
                val userId = call.userId() ?: return@post call.unauthorized()
                if (builders.withdraw(userId)) logger.info("builder application withdrawn")
                call.respond(builders.standing(userId, call.isAnonymousUser()).toMe())
            }

            // An app from before applications: its Join applies, and hears back in the codes it knows.
            post("/join") {
                val userId = call.userId() ?: return@post call.unauthorized()
                val request = call.receive<BuilderJoinRequest>()
                if (!request.acceptTerms || request.termsVersion != BuilderRules.TERMS_VERSION) {
                    return@post call.respond(HttpStatusCode.BadRequest, problem("terms_required", "Accept the Builder rules first."))
                }
                val form = ApplicationForm(photoRetention = request.photoRetention, via = VIA_LEGACY_JOIN)
                when (val outcome = builders.apply(userId, call.isAnonymousUser(), form)) {
                    is ApplyOutcome.Applied -> {
                        if (outcome.created) logger.info("builder application sent from join")
                        call.respond(outcome.standing.toMe())
                    }
                    is ApplyOutcome.AlreadyBuilder -> call.respond(outcome.standing.toMe())
                    is ApplyOutcome.Blocked -> {
                        logger.info("builder join blocked code={}", outcome.block.code)
                        val status = if (outcome.block.legacyCode == JoinBlock.Full.code) HttpStatusCode.Conflict else HttpStatusCode.Forbidden
                        call.respond(status, outcome.block.problem(legacy = true))
                    }
                    ApplyOutcome.NoSuchUser -> call.unauthorized()
                }
            }

            post("/leave") {
                val userId = call.userId() ?: return@post call.unauthorized()
                if (builders.leave(userId)) logger.info("builder left")
                call.respond(builders.standing(userId, call.isAnonymousUser()).toMe())
            }
        }

        rateLimit(RateLimitName(FOOD_REPORT_LIMIT)) {
            bodyLimit(FoodVerificationLimits.MAX_BODY_BYTES) {
                post("/v1/foods/{foodId}/verifications") {
                    val userId = call.userId() ?: return@post call.unauthorized()
                    val foodId = call.parameters["foodId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_id", "The food id must be a UUID"))
                    // Before the body: anyone signed in can reach this route, and the body is two photos.
                    if (!permissions.can(userId, Permission.FoodContribute)) {
                        return@post call.respond(HttpStatusCode.Forbidden, problem("not_a_builder", "Checking foods is for Builders."))
                    }
                    val request = call.receive<VerificationRequest>()
                    val id = request.id?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_id", "id must be a UUID"))
                    if (request.lines.size > FoodLabelLimits.MAX_CAPTURE_LINES || request.lines.any { it.text.length > FoodLabelLimits.MAX_CAPTURE_LINE }) {
                        return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_capture", "Too much text"))
                    }
                    val reported = when (val checked = request.problem?.let(::validateProblem)) {
                        is Checked.Rejected -> return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_problem", checked.message))
                        is Checked.Ok -> checked.value
                        null -> null
                    }
                    val photos = when (val decoded = decodeVerificationPhotos(request.photos)) {
                        is PhotosDecoded.Rejected -> return@post call.respond(decoded.status, problem(decoded.code, decoded.message))
                        is PhotosDecoded.Ok -> decoded.photos
                    }
                    if (call.refuseIfRestricted(contributionGate, userId, id.toString(), logger, "food verification")) return@post
                    val food = catalog.findById(foodId)
                        ?.takeIf { it.source != CatalogSource.UserSubmitted || submissions.isVisible(foodId, userId) }
                        ?: return@post call.respond(HttpStatusCode.NotFound, problem("not_found", "No such food"))
                    // A retry of a check already recorded answers as before and spends none of the quota.
                    verifications.get(id)?.let { existing ->
                        return@post if (existing.userId == userId) {
                            call.respond(existing.toResponse(emptyList()))
                        } else {
                            call.respond(HttpStatusCode.Conflict, problem("id_conflict", "That id is taken"))
                        }
                    }
                    if (!limiter.tryVerify(userId)) {
                        return@post call.respond(HttpStatusCode.TooManyRequests, problem("rate_limited", "That's a lot of checks for now. Try again later."))
                    }
                    val lines = request.lines.map { CaptureLine(it.text, it.confidence) }
                    val grounding = VerificationCheck.ground(food.per100g, food.portions.firstOrNull()?.grams, lines)
                    val grounded = grounding.complete && grounding.groundable == VerificationCheck.HEADLINE
                    val matched = grounded && reported == null
                    // The text that matched is the app's: a Builder's match waits, with its photos, for a
                    // second Builder's match or a reviewer. An owner's or admin's stands on arrival.
                    val awaitsAgreement = matched && !permissions.can(userId, Permission.FoodEditDirect)
                    // A person looks at it when the Builder said something was wrong, or when the text
                    // did not show the numbers and they sent the photos a person needs to judge it.
                    val queued = reported != null || (!grounded && photos.isNotEmpty()) || awaitsAgreement
                    val outcome = if (matched) VerificationOutcome.Confirmed else VerificationOutcome.Unconfirmed
                    val recorded = verifications.record(
                        FoodVerification(
                            id = id,
                            foodId = foodId,
                            userId = userId,
                            createdAt = clock.now(),
                            outcome = outcome,
                            grounded = grounding.grounded,
                            groundable = grounding.groundable,
                            engine = request.engine,
                            status = VerificationStatus.Open.takeIf { queued },
                            problem = reported,
                            snapshot = FoodSnapshot.of(food).takeIf { queued },
                        ),
                        photos,
                    )
                    when (recorded) {
                        is VerifyOutcome.Recorded -> {
                            logger.info(
                                "food verification outcome={} grounded={}/{} review={} problem={} photos={}",
                                recorded.verification.outcome.wire, grounding.grounded, grounding.groundable, queued, reported != null, photos.size,
                            )
                            call.respond(HttpStatusCode.Created, recorded.verification.toResponse(grounding.missing))
                        }
                        is VerifyOutcome.Existing -> call.respond(recorded.verification.toResponse(emptyList()))
                        VerifyOutcome.Conflict -> call.respond(HttpStatusCode.Conflict, problem("id_conflict", "That id is taken"))
                    }
                }
            }
        }
    }
}

/**
 * The console's view of the Builder program:
 *
 *  - `GET  /v1/admin/users/{id}/builder`  one account's standing, trust and the record behind it
 *  - `POST /v1/admin/users/{id}/builder {action, reason?}` `remove` (with a reason), `restore`,
 *    `hold` (keep at Builder whatever the record says) or `release`
 *  - `GET  /v1/admin/builders/applications?status=pending|decided` the queue, oldest first, or
 *    the latest decisions, each with the account behind it; `pending` counts the whole queue
 *  - `POST /v1/admin/builders/applications/{id}/accept {note?}` makes them a Builder: the
 *    membership and the `builder` role, audited under `X-Admin-Actor`. 409 `not_pending`,
 *    `restricted`, `full`.
 *  - `POST /v1/admin/builders/applications/{id}/decline {note?}` the note is shown to them
 */
@OptIn(ExperimentalTime::class)
@Suppress("LongMethod")
fun Route.builderAdminRoutes(
    config: AdminConfig,
    builders: BuilderService,
    repository: BuilderRepository,
    applications: BuilderApplicationRepository,
) {
    val logger = LoggerFactory.getLogger("BuilderAdminRoutes")
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
            val actor = call.adminActor()
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

    route("/v1/admin/builders/applications") {
        get {
            if (!call.requireAdmin(config)) return@get
            val list = when (call.request.queryParameters["status"] ?: "pending") {
                "pending" -> applications.pending(MAX_LISTED)
                "decided" -> applications.decided(MAX_LISTED)
                else -> return@get call.respond(HttpStatusCode.BadRequest, problem("invalid_status", "pending or decided"))
            }
            val rows = list.map { it.toAdmin(applications.summary(it.userId, excluding = it.id)) }
            call.respond(AdminApplicationListResponse(applications.pendingCount(), rows))
        }

        post("/{applicationId}/{decision}") {
            if (!call.requireAdmin(config)) return@post
            val id = call.parameters["applicationId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_id", "Not an application id."))
            val note = call.receive<AdminApplicationDecisionRequest>().note?.trim()?.ifEmpty { null }?.take(MAX_REASON)
            val actor = call.adminActor()
            val outcome = when (call.parameters["decision"]) {
                "accept" -> builders.accept(id, actor, note)
                "decline" -> builders.decline(id, actor, note)
                else -> return@post call.respond(HttpStatusCode.NotFound, problem("not_found", "accept or decline"))
            }
            when (outcome) {
                is DecideOutcome.Decided -> {
                    logger.info("builder application {}", outcome.application.status.wire)
                    val row = outcome.application.toAdmin(applications.summary(outcome.application.userId, excluding = id))
                    call.respond(AdminApplicationDecisionResponse(applications.pendingCount(), row))
                }
                DecideOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, problem("not_found", "No such application."))
                DecideOutcome.NotPending -> call.respond(HttpStatusCode.Conflict, problem("not_pending", "Already decided or withdrawn."))
                DecideOutcome.Restricted -> call.respond(
                    HttpStatusCode.Conflict,
                    problem("restricted", "This account is banned, restricted or on a marked device, so it can't get Builder Pro."),
                )
                is DecideOutcome.Full -> call.respond(
                    HttpStatusCode.Conflict,
                    problem("full", "Builders is at builders.capacity (${outcome.capacity}). Raise it to accept more."),
                )
            }
        }
    }
}

private fun ApplicationCall.adminActor(): String =
    request.header("X-Admin-Actor")?.trim()?.take(MAX_ACTOR)?.ifEmpty { null } ?: "admin"

private suspend fun ApplicationCall.unauthorized() = respond(HttpStatusCode.Unauthorized, problem("unauthorized", "Sign in again"))

private suspend fun ApplicationCall.adminUserId(): UserId? {
    val id = parameters["id"]?.let(UserId::parse)
    if (id == null) respond(HttpStatusCode.BadRequest, problem("invalid_user_id", "Not a user id."))
    return id
}

@OptIn(ExperimentalTime::class)
private fun JoinBlock.message(): String = when (this) {
    JoinBlock.Guest -> "Sign in with an account to apply."
    is JoinBlock.TooNew -> "Add a food from its label first, or try again in a few days."
    JoinBlock.Removed -> "This account can't apply to Builders."
    JoinBlock.Full -> "Builders is full right now."
    JoinBlock.Pending -> "Your application is with us. We'll let you know."
    is JoinBlock.Declined -> "You can apply again later."
}

@OptIn(ExperimentalTime::class)
private fun JoinBlock.availableAt(): Instant? = when (this) {
    is JoinBlock.TooNew -> availableAt
    is JoinBlock.Declined -> availableAt
    else -> null
}

/** The problem envelope for a block, in an older app's codes when [legacy]. */
private fun JoinBlock.problem(legacy: Boolean) = problem(if (legacy) legacyCode else code, message())

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
    joinBlock = joinBlock?.legacyCode,
    availableAtEpochMs = joinBlock?.availableAt()?.toEpochMilliseconds(),
    applyBlock = joinBlock?.code,
    application = application?.toMe(),
    stats = BuilderStatsResponse(
        foodsAdded = history.approvedSubmissions,
        checks = history.confirmedChecks,
        rejected = history.rejectedSubmissions,
    ),
)

@OptIn(ExperimentalTime::class)
private fun BuilderApplication.toMe() = BuilderApplicationResponse(
    id = id.toString(),
    status = status.wire,
    submittedAtEpochMs = createdAt.toEpochMilliseconds(),
    decidedAtEpochMs = decidedAt?.toEpochMilliseconds(),
    note = decisionNote,
    reapplyAtEpochMs = decidedAt?.takeIf { status == ApplicationStatus.Declined }?.let { (it + BuilderRules.REAPPLY_AFTER).toEpochMilliseconds() },
)

@OptIn(ExperimentalTime::class)
private fun BuilderApplication.toAdmin(summary: ApplicantSummary) = AdminApplicationResponse(
    id = id.toString(),
    userId = userId.value.toString(),
    status = status.wire,
    submittedAtEpochMs = createdAt.toEpochMilliseconds(),
    termsVersion = termsVersion,
    photoRetention = photoRetention,
    motivation = motivation,
    shopping = shopping?.wire,
    region = region?.wire,
    via = via,
    decidedAtEpochMs = decidedAt?.toEpochMilliseconds(),
    decidedBy = decidedBy,
    note = decisionNote,
    account = ApplicantSummaryResponse(
        email = summary.email,
        displayName = summary.displayName,
        isGuest = summary.isGuest,
        accountCreatedAtEpochMs = summary.accountCreatedAt?.toEpochMilliseconds(),
        entries = summary.entries,
        daysLogged = summary.daysLogged,
        lastLogDay = summary.lastLogDay,
        submissionsApproved = summary.submissionsApproved,
        submissionsRejected = summary.submissionsRejected,
        submissionsPending = summary.submissionsPending,
        reportsAccepted = summary.reportsAccepted,
        reportsRejected = summary.reportsRejected,
        reportsOpen = summary.reportsOpen,
        checks = summary.checks,
        moderationActions = summary.moderationActions,
        moderationActive = summary.moderationActive,
        lastModerationReason = summary.lastModerationReason,
        earlierApplications = summary.earlierApplications,
        earlierDeclines = summary.earlierDeclines,
        membership = summary.membership,
    ),
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
    VerificationResponse(
        id = id.toString(),
        outcome = outcome.wire,
        grounded = grounded,
        groundable = groundable,
        missing = missing,
        review = status?.takeIf { it == VerificationStatus.Open }?.wire,
        awaiting = AWAITING_AGREEMENT.takeIf { awaitsAgreement },
    )

/** `awaiting` on a match that waits for a second Builder or a reviewer. */
private const val AWAITING_AGREEMENT = "agreement"

private const val MAX_ACTOR = 100
private const val MAX_REASON = 500
private const val MAX_MOTIVATION = 500
private const val MAX_VIA = 16
private const val MAX_LISTED = 100

/** `via` for an application sent from an older app's Join. */
private const val VIA_LEGACY_JOIN = "join"
