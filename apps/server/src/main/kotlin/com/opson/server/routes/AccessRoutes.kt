package com.opson.server.routes

import com.opson.server.domain.EntitlementService
import com.opson.server.http.clientContext
import com.opson.server.plugins.SUPABASE_JWT_AUTH
import com.opson.server.plugins.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `GET /v1/me/entitlements`: the caller's role, permissions and resolved entitlements
 * ([MyAccessResponse]). A route of its own rather than more fields on `GET /v1/me`, so the
 * profile's get-or-create stays as it is and a client that never asks pays nothing.
 *
 * Resolved for the calling client ([clientContext]), because the policy is remote config and a
 * targeting rule can widen it to one platform or a rollout percentage. This is what the app
 * caches; the server never trusts it back, and every gated route checks [EntitlementService]
 * itself.
 */
fun Route.accessRoutes(entitlements: EntitlementService) {
    authenticate(SUPABASE_JWT_AUTH) {
        get("/v1/me/entitlements") {
            val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(entitlements.access(userId, call.clientContext()).toMyAccessResponse())
        }
    }
}
