package com.opson.server.plugins

import io.ktor.http.HttpMethod
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.Route

/**
 * The API-wide CORS policy: any origin, the headers and methods our own
 * callers use. Installed on the routing root rather than the application so a
 * route can replace it with a policy of its own (the waitlist form allows
 * only `WAITLIST_ALLOWED_ORIGINS`, see `waitlistRoutes`). Ktor applies the
 * nearest install to a call, and a route-level install answers preflights
 * through its own `OPTIONS` handler, so every path still gets one.
 */
fun Route.installCors() {
    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
        allowHeader("Authorization")
        allowHeader("X-Request-Id")
        // The config admin web GUI is a browser app on localhost that calls the
        // deployed server cross-origin with these custom headers. Without them in
        // the allow-list the CORS preflight is rejected with 403 before the
        // request is even authenticated. CORS isn't the security boundary here —
        // the admin token is — so allowing the header names is safe.
        allowHeader("X-Admin-Token")
        allowHeader("X-Admin-Actor")
        // Same story for methods: Ktor's CORS plugin only permits GET/POST/HEAD
        // by default, so the admin GUI's flag upserts (PUT) and deletes were
        // rejected at preflight with 403 while GETs sailed through.
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }
}
