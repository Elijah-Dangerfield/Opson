package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.domain.UserDetail
import com.opson.server.domain.UserId
import com.opson.server.domain.UserInstall
import com.opson.server.domain.UserLookupRepository
import com.opson.server.domain.UserSummary
import com.opson.server.plugins.problem
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

@Serializable
data class UserSummaryResponse(
    val userId: String,
    val email: String? = null,
    val isGuest: Boolean,
    val displayName: String? = null,
    val joinedAtEpochMs: Long? = null,
    val lastSignInAtEpochMs: Long? = null,
    val lastSeenAtEpochMs: Long? = null,
    val platform: String? = null,
    val appVersion: String? = null,
    val buildNumber: Int? = null,
    val preset: String? = null,
    val entries: Long,
    val daysLogged: Long,
    val lastLogDay: String? = null,
)

@Serializable
data class UserSearchRequest(val query: String? = null)

@Serializable
data class UserSearchResponse(val users: List<UserSummaryResponse>)

@Serializable
data class UserInstallResponse(
    val installId: String,
    val platform: String,
    val appVersion: String,
    val buildNumber: Int,
    val firstSeenAtEpochMs: Long,
    val lastSeenAtEpochMs: Long,
)

@Serializable
data class UserSettingResponse(val key: String, val value: String? = null)

@Serializable
data class UserDetailResponse(
    val user: UserSummaryResponse,
    val installs: List<UserInstallResponse>,
    val settings: List<UserSettingResponse>,
    val methods: Map<String, Long>,
    val entriesLast7Days: Long,
    val entriesLast30Days: Long,
    val daysClosed: Long,
    val lastSyncedChangeAtEpochMs: Long? = null,
)

/**
 * The owner's user lookup, behind the same `X-Admin-Token` gate as the rest of `/v1/admin`:
 *
 *  - `POST /v1/admin/users/search {query}` accounts by exact id, or part of an email or display
 *                                          name; a blank query lists the most recently seen. At most 25.
 *  - `GET  /v1/admin/users/{id}`           one account: its builds, its Settings choices, how it logs
 *
 * Both read the `support` views (V22), which carry an account's email and counts but nothing
 * anyone wrote or measured. The search is a POST so an email typed into it stays in the body and
 * out of URLs, where request logs and traces would keep it, as the waitlist's removal does.
 */
@OptIn(ExperimentalTime::class)
fun Route.userAdminRoutes(config: AdminConfig, repository: UserLookupRepository) {
    route("/v1/admin/users") {
        post("/search") {
            if (!call.requireAdmin(config)) return@post
            val query = call.receive<UserSearchRequest>().query.orEmpty().take(MAX_QUERY)
            val users = repository.search(query, SEARCH_LIMIT)
            call.respond(UserSearchResponse(users.map { it.toResponse() }))
        }

        get("/{id}") {
            if (!call.requireAdmin(config)) return@get
            val userId = call.parameters["id"]?.let(UserId::parse)
                ?: return@get call.respond(HttpStatusCode.BadRequest, problem("invalid_user_id", "Not a user id."))
            val detail = repository.detail(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, problem("not_found", "No account with that id."))
            call.respond(detail.toResponse())
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun UserSummary.toResponse() = UserSummaryResponse(
    userId = userId.toString(),
    email = email,
    isGuest = isGuest,
    displayName = displayName,
    joinedAtEpochMs = joinedAt?.toEpochMilliseconds(),
    lastSignInAtEpochMs = lastSignInAt?.toEpochMilliseconds(),
    lastSeenAtEpochMs = lastSeenAt?.toEpochMilliseconds(),
    platform = platform,
    appVersion = appVersion,
    buildNumber = buildNumber,
    preset = preset,
    entries = entries,
    daysLogged = daysLogged,
    lastLogDay = lastLogDay?.toString(),
)

@OptIn(ExperimentalTime::class)
private fun UserInstall.toResponse() = UserInstallResponse(
    installId = installId,
    platform = platform,
    appVersion = appVersion,
    buildNumber = buildNumber,
    firstSeenAtEpochMs = firstSeenAt.toEpochMilliseconds(),
    lastSeenAtEpochMs = lastSeenAt.toEpochMilliseconds(),
)

@OptIn(ExperimentalTime::class)
private fun UserDetail.toResponse() = UserDetailResponse(
    user = summary.toResponse(),
    installs = installs.map { it.toResponse() },
    settings = settings.map { (key, value) -> UserSettingResponse(key, value) },
    methods = methods,
    entriesLast7Days = entriesLast7Days,
    entriesLast30Days = entriesLast30Days,
    daysClosed = daysClosed,
    lastSyncedChangeAtEpochMs = lastSyncedChangeAt?.toEpochMilliseconds(),
)

private const val SEARCH_LIMIT = 25
private const val MAX_QUERY = 200
