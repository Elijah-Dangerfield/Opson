package com.opson.server.routes

import com.opson.server.domain.AppConfigSource
import com.opson.server.http.clientContext
import com.opson.server.plugins.BearerVerifier
import com.opson.server.plugins.identifyCallerIfSignedIn
import com.opson.server.plugins.userId
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * `GET /v1/app-config` — returns the resolved override tree keyed by ConfiguredValue.path.
 *
 * The source resolves the tree against the calling client: [clientContext] (platform,
 * app version, country, locale, install id) always, plus the resolved [userId] when the
 * request carries a Supabase JWT that verifies. That's what powers per-flag targeting + staged
 * rollouts server-side — the client just merges whatever tree it gets over its defaults.
 *
 * Never a 401: the tree carries the kill switch, maintenance mode and the version floor, which
 * must reach every build, including one signed in to another Supabase project or holding a
 * token that expired while the phone slept. A token that doesn't verify is served the
 * anonymous tree ([identifyCallerIfSignedIn]). [bearer] is null when the server has no
 * Supabase configured; everyone is then anonymous.
 *
 * Empty object is a legitimate response — it means "use client defaults". The client
 * always has safe defaults declared in its ConfiguredValue classes, so an empty server config
 * still produces a fully functional app.
 */
fun Route.appConfigRoutes(source: AppConfigSource, bearer: BearerVerifier?) {
    route("/v1/app-config") {
        if (bearer != null) identifyCallerIfSignedIn(bearer)
        get {
            call.respond(source.read(call.clientContext(), call.userId()))
        }
    }
}
