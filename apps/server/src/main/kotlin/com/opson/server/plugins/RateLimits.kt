package com.opson.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.request.header
import io.ktor.server.request.path
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Rate limiting. A global per-IP bucket guards every route; named buckets are
 * registered for routes that want a tighter cap — opt in with
 * `rateLimit(RateLimitName(PROFILE_WRITE_LIMIT)) { … }` (see MeRoutes' PATCH).
 *
 * Keying is per-IP: the limiter runs before auth's `validate`, so the JWT `sub`
 * isn't available yet. `/_health` is excluded so health probes don't drain the
 * bucket. Limits are deliberately loose — they catch hot loops and trivial
 * abuse, not concerted DoS (your edge/CDN owns that).
 */
const val PROFILE_WRITE_LIMIT = "profile-write"
const val PLAYER_REPORT_LIMIT = "player-report"
const val DELETE_ACCOUNT_LIMIT = "delete-account"
const val BARCODE_LOOKUP_LIMIT = "barcode-lookup"
const val BARCODE_MISS_LIMIT = "barcode-miss"
const val PHOTO_ESTIMATE_LIMIT = "photo-estimate"

fun Application.installRateLimits() {
    install(RateLimit) {
        global {
            rateLimiter(limit = 600, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
            requestWeight { call, _ -> if (call.request.path().startsWith("/_health")) 0 else 1 }
        }

        register(RateLimitName(PROFILE_WRITE_LIMIT)) {
            // Writes with a server-side uniqueness constraint (PATCH /v1/me) get
            // a tighter cap so name-squatting bots are expensive; a real user
            // still has plenty of retries.
            rateLimiter(limit = 30, refillPeriod = 1.hours)
            requestKey { call -> call.clientIp() }
        }

        register(RateLimitName(DELETE_ACCOUNT_LIMIT)) {
            // App Store review explicitly cites brute-force / harassment as
            // a deletion-endpoint risk. 5/hour per IP gives a legitimate
            // user multiple retries while making scripted abuse impractical.
            rateLimiter(limit = 5, refillPeriod = 1.hours)
            requestKey { call -> call.clientIp() }
        }

        register(RateLimitName(BARCODE_LOOKUP_LIMIT)) {
            // A catalog miss becomes a call to Open Food Facts, which limits
            // product reads per caller IP, and every Opson user shares the
            // server's IP. 60/min per client keeps one hot loop from spending
            // everyone's OFF budget; a person scanning a pantry never hits it.
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }

        register(RateLimitName(BARCODE_MISS_LIMIT)) {
            // A miss report follows a barcode lookup that missed, so it can
            // never legitimately outrun that route's 60/min. Its own bucket,
            // so a report never spends a lookup.
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }

        register(RateLimitName(PHOTO_ESTIMATE_LIMIT)) {
            // Every photo estimate is a paid model call. The real limit is per
            // user (PhotoEstimateLimiter, after auth); this per-IP bucket stops
            // a flood of large bodies before any of them is read or verified.
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }

        register(RateLimitName(PLAYER_REPORT_LIMIT)) {
            // POST /v1/reports targets another user, so it's a harassment /
            // spam surface (a bad actor mass-reporting someone). A real user
            // files a report rarely; 20/hour/IP leaves ample headroom for
            // legitimate use while making scripted report-floods impractical.
            // Per-IP keying mirrors the rest of the policy (see file header).
            rateLimiter(limit = 20, refillPeriod = 1.hours)
            requestKey { call -> call.clientIp() }
        }
    }
}

/**
 * Best-effort client IP. Trusts the standard reverse-proxy headers (Fly's
 * `Fly-Client-IP` first, then `X-Forwarded-For`, then the socket). Order
 * matters: the wrong choice makes everyone share the edge IP and the limiter
 * degenerates to one global bucket.
 */
internal fun io.ktor.server.application.ApplicationCall.clientIp(): String {
    request.header("Fly-Client-IP")?.takeIf { it.isNotBlank() }?.let { return it }
    request.header("X-Forwarded-For")?.split(',')?.firstOrNull()?.trim()
        ?.takeIf { it.isNotBlank() }?.let { return it }
    return request.local.remoteHost
}
