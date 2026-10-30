package com.opson.server.routes

import com.opson.server.domain.AppConfigSource
import com.opson.server.domain.BetaJoinOutcome
import com.opson.server.domain.EntitlementGrantRepository
import com.opson.server.domain.EntitlementService
import com.opson.server.domain.joinBeta
import com.opson.server.http.clientContext
import com.opson.server.plugins.SUPABASE_JWT_AUTH
import com.opson.server.plugins.isAnonymousUser
import com.opson.server.plugins.problem
import com.opson.server.plugins.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

/**
 * `GET /v1/me/entitlements`: the caller's role, permissions and resolved entitlements
 * ([MyAccessResponse]). A route of its own rather than more fields on `GET /v1/me`, so the
 * profile's get-or-create stays as it is and a client that never asks pays nothing.
 *
 * Resolved for the calling client ([clientContext]), because the policy is remote config and a
 * targeting rule can widen it to one platform or a rollout percentage. This is what the app
 * caches; the server never trusts it back, and every gated route checks [EntitlementService]
 * itself.
 *
 * `POST /v1/me/beta`: the caller joins the beta ([joinBeta]), recorded as a `beta` grant. 201 the
 * first time, 200 with the first join after that, 409 `beta_closed` while `release.betaNotice` is
 * off for them, 403 `anonymous` for a guest session. Body `{joinedAtEpochMs}`.
 */
@OptIn(ExperimentalTime::class)
fun Route.accessRoutes(entitlements: EntitlementService, config: AppConfigSource, grants: EntitlementGrantRepository) {
    authenticate(SUPABASE_JWT_AUTH) {
        get("/v1/me/entitlements") {
            val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(entitlements.access(userId, call.clientContext()).toMyAccessResponse())
        }
        post("/v1/me/beta") {
            val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            if (call.isAnonymousUser()) {
                return@post call.respond(HttpStatusCode.Forbidden, problem("anonymous", "Sign in to join the beta."))
            }
            when (val outcome = joinBeta(userId, call.clientContext(), config, grants)) {
                is BetaJoinOutcome.Joined -> call.respond(
                    if (outcome.new) HttpStatusCode.Created else HttpStatusCode.OK,
                    BetaMembershipResponse(outcome.since.toEpochMilliseconds()),
                )
                BetaJoinOutcome.Closed -> call.respond(HttpStatusCode.Conflict, problem("beta_closed", "The beta isn't taking new members."))
                BetaJoinOutcome.NoSuchUser -> call.respond(HttpStatusCode.NotFound, problem("not_found", "No such account."))
            }
        }
    }
}

/** `POST /v1/me/beta`: when the caller joined the beta. */
@Serializable
data class BetaMembershipResponse(val joinedAtEpochMs: Long)
