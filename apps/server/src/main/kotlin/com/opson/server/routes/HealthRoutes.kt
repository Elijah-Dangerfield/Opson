package com.opson.server.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Liveness/readiness probe. Stays `/_health` (not `/v1/_health`) so it's not
 * tied to a versioned API and load balancers can hit it unconditionally.
 *
 * [supabaseProject] is the Supabase project ref this server verifies tokens
 * against, null when Supabase isn't configured. The release workflows compare it
 * with the project a build signs in to: 0.1.0 shipped signing in to dev while
 * calling prod, and every request came back 401.
 */
fun Route.healthRoutes(supabaseProject: String? = null) {
    get("/_health") {
        call.respond(
            buildJsonObject {
                put("ok", true)
                supabaseProject?.let { put("supabaseProject", it) }
            },
        )
    }
}
