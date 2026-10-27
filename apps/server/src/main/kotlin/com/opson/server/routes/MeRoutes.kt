package com.opson.server.routes

import com.opson.server.domain.DeleteUserResult
import com.opson.server.domain.DisplayNameRules
import com.opson.server.domain.ProfileRepository
import com.opson.server.domain.SupabaseAdminClient
import com.opson.server.domain.UpdateDisplayNameResult
import com.opson.server.domain.UpdateProfileOutcome
import com.opson.server.domain.UserId
import com.opson.server.plugins.DELETE_ACCOUNT_LIMIT
import com.opson.server.plugins.PROFILE_WRITE_LIMIT
import com.opson.server.plugins.SUPABASE_JWT_AUTH
import com.opson.server.plugins.isAnonymousUser
import com.opson.server.plugins.problem
import com.opson.server.plugins.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * `/v1/me` — the reference authenticated resource.
 *
 *  - `GET`    returns the caller's profile, creating it on first contact
 *             (get-or-create). `isNewAccount` is true only on the creating
 *             response.
 *  - `PATCH`  updates the display name (rate-limited — a write with a
 *             uniqueness constraint is a name-squatting surface). Validated
 *             by [DisplayNameRules], the authoritative backstop for the
 *             client's copy of the same rules.
 *  - `DELETE` permanent account deletion: removes the Supabase auth.users row
 *             (via the Admin API) AND our profile row. Returns 204. Both app
 *             stores require an in-app account-deletion path.
 *
 * The caller's id is the JWT `sub` (`call.userId()`) — the client never sends
 * it in the body. Domain outcomes map to HTTP statuses via an exhaustive
 * `when`; the route never lets a domain failure escape as a 500.
 *
 * DELETE ordering: admin call first (revokes the user's sessions immediately,
 * so even if the local profile delete fails the user can't come back via the
 * same account), then local cleanup. An orphan profile is recoverable by a
 * future sweep; an orphan auth.users with a live JWT is a security problem.
 * Every user-owned table references `auth.users` with `ON DELETE CASCADE`
 * (V2, V8, V14), so the hard delete removes the diary with the account.
 */
fun Route.meRoutes(repository: ProfileRepository, adminClient: SupabaseAdminClient) {
    val app = application
    authenticate(SUPABASE_JWT_AUTH) {
        get("/v1/me") {
            val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val (profile, isNewAccount) = repository.findOrCreateResult(userId)
            // Mirror the freshly-generated name onto the auth row so the
            // Supabase Users table shows who this account is. Best-effort:
            // renames re-mirror.
            if (isNewAccount) {
                fireDisplayNameMirror(app, adminClient, userId, profile.displayName)
            }
            call.respond(
                profile.toMeResponse(isAnonymous = call.isAnonymousUser(), isNewAccount = isNewAccount),
            )
        }

        rateLimit(RateLimitName(PROFILE_WRITE_LIMIT)) {
            patch("/v1/me") {
                val userId = call.userId() ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                val displayName = call.receive<UpdateMeRequest>().displayName?.trim()
                if (displayName.isNullOrEmpty() || !DisplayNameRules.isValid(displayName)) {
                    return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        problem(
                            "invalid_display_name",
                            "Display name must be ${DisplayNameRules.MIN_LENGTH}-" +
                                "${DisplayNameRules.MAX_LENGTH} characters and contain no emoji.",
                        ),
                    )
                }
                when (val outcome = repository.updateDisplayName(userId, displayName)) {
                    is UpdateProfileOutcome.Success -> {
                        fireDisplayNameMirror(app, adminClient, userId, outcome.profile.displayName)
                        call.respond(outcome.profile.toMeResponse(isAnonymous = call.isAnonymousUser()))
                    }

                    UpdateProfileOutcome.DisplayNameTaken ->
                        call.respond(HttpStatusCode.Conflict, problem("display_name_taken", "That display name is taken"))

                    UpdateProfileOutcome.NotFound ->
                        call.respond(HttpStatusCode.NotFound, problem("not_found", "Profile not found"))
                }
            }
        }

        rateLimit(RateLimitName(DELETE_ACCOUNT_LIMIT)) {
            delete("/v1/me") {
                // Anonymous accounts are deletable too — a guest's identity +
                // data is a real account the user has the right to erase (and
                // App Store account-deletion rules apply regardless of type).
                val userId = call.userId() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                when (val admin = adminClient.deleteUser(userId)) {
                    DeleteUserResult.Success, DeleteUserResult.AlreadyGone -> {
                        repository.delete(userId)
                        call.respond(HttpStatusCode.NoContent)
                    }

                    DeleteUserResult.NotConfigured -> call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        problem(
                            "delete_not_configured",
                            "Account deletion is not enabled on this server. Set SUPABASE_SERVICE_ROLE_KEY and redeploy.",
                        ),
                    )

                    is DeleteUserResult.Failure -> {
                        LoggerFactory.getLogger("MeRoutes").error(
                            "Supabase admin delete failed for user {} (status={})",
                            userId,
                            admin.statusCode,
                            admin.cause,
                        )
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            problem("delete_failed", "Could not delete account right now. Please try again."),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Best-effort mirror of the profile display name into Supabase auth metadata
 * (see [SupabaseAdminClient.updateUserDisplayName]). Off the request path: a
 * slow or failed admin call must never delay or fail `/v1/me`.
 */
private fun fireDisplayNameMirror(
    app: Application,
    adminClient: SupabaseAdminClient,
    userId: UserId,
    displayName: String,
) {
    app.launch {
        val result = try {
            adminClient.updateUserDisplayName(userId, displayName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            UpdateDisplayNameResult.Failure(statusCode = null, cause = e)
        }
        if (result is UpdateDisplayNameResult.Failure) {
            LoggerFactory.getLogger("MeRoutes")
                .warn("auth display-name mirror failed for user={} status={}", userId, result.statusCode, result.cause)
        }
    }
}
