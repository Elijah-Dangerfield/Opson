package com.opson.server.routes

import com.opson.server.domain.AppleExchangeResult
import com.opson.server.domain.AppleRevokeResult
import com.opson.server.domain.AppleSignInClient
import com.opson.server.domain.AppleTokenRepository
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
import io.ktor.server.routing.post
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
 *             (via the Admin API) AND our profile row, then revokes the
 *             person's Sign in with Apple token if we hold one. Returns 204.
 *             Both app stores require an in-app account-deletion path.
 *  - `POST /v1/me/apple-authorization` takes the authorization code from a
 *             native Sign in with Apple and keeps the refresh token Apple
 *             trades it for, which is what deletion revokes. 204.
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
 * That includes the Apple token (V21), so it is read before the delete and
 * revoked after it. Revocation never blocks deletion: Apple being down or the
 * key being unset is logged, and the account is gone either way.
 */
fun Route.meRoutes(
    repository: ProfileRepository,
    adminClient: SupabaseAdminClient,
    appleTokens: AppleTokenRepository,
    apple: AppleSignInClient,
) {
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

            post("/v1/me/apple-authorization") {
                val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val code = call.receive<AppleAuthorizationRequest>().authorizationCode?.trim()
                if (code.isNullOrEmpty() || code.length > MAX_AUTHORIZATION_CODE_LENGTH) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        problem("invalid_authorization_code", "authorizationCode is required"),
                    )
                }
                when (val exchange = apple.exchange(code)) {
                    is AppleExchangeResult.Success -> {
                        appleTokens.save(userId, exchange.refreshToken)
                        call.respond(HttpStatusCode.NoContent)
                    }

                    AppleExchangeResult.NotConfigured -> {
                        logger.warn("Apple authorization for user {} not kept: no Apple key on this server", userId)
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            problem("apple_not_configured", "Sign in with Apple tokens are not kept by this server."),
                        )
                    }

                    AppleExchangeResult.Rejected -> call.respond(
                        HttpStatusCode.BadRequest,
                        problem("invalid_authorization_code", "Apple refused the authorization code"),
                    )

                    is AppleExchangeResult.Failure -> {
                        logger.warn("Apple token exchange failed for user {} (status={})", userId, exchange.statusCode, exchange.cause)
                        call.respond(
                            HttpStatusCode.BadGateway,
                            problem("apple_unavailable", "Could not reach Apple. Please try again."),
                        )
                    }
                }
            }
        }

        rateLimit(RateLimitName(DELETE_ACCOUNT_LIMIT)) {
            delete("/v1/me") {
                // Anonymous accounts are deletable too — a guest's identity +
                // data is a real account the user has the right to erase (and
                // App Store account-deletion rules apply regardless of type).
                val userId = call.userId() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                val appleToken = appleTokens.find(userId)
                when (val admin = adminClient.deleteUser(userId)) {
                    DeleteUserResult.Success, DeleteUserResult.AlreadyGone -> {
                        repository.delete(userId)
                        if (appleToken != null) revokeApple(apple, userId, appleToken)
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

private val logger = LoggerFactory.getLogger("MeRoutes")

/** Apple's codes are short; this only stops a megabyte reaching Apple. */
private const val MAX_AUTHORIZATION_CODE_LENGTH = 1024

private suspend fun revokeApple(apple: AppleSignInClient, userId: UserId, refreshToken: String) {
    when (val result = apple.revoke(refreshToken)) {
        AppleRevokeResult.Success -> logger.info("Revoked the Sign in with Apple token of deleted user {}", userId)
        AppleRevokeResult.NotConfigured -> logger.warn(
            "Did not revoke the Sign in with Apple token of deleted user {}: APPLE_TEAM_ID, APPLE_KEY_ID " +
                "or APPLE_PRIVATE_KEY is not set",
            userId,
        )
        is AppleRevokeResult.Failure -> logger.error(
            "Could not revoke the Sign in with Apple token of deleted user {} (status={}); the account is deleted anyway",
            userId,
            result.statusCode,
            result.cause,
        )
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
