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
const val DELETE_ACCOUNT_LIMIT = "delete-account"
const val BARCODE_LOOKUP_LIMIT = "barcode-lookup"
const val BARCODE_MISS_LIMIT = "barcode-miss"
const val PHOTO_ESTIMATE_LIMIT = "photo-estimate"
const val FOOD_SEARCH_LIMIT = "food-search"
const val DESCRIBE_LIMIT = "describe"
const val EXPORT_LIMIT = "export"
const val FOOD_LABEL_LIMIT = "food-label"
const val BARCODE_LINK_LIMIT = "barcode-link"
const val FOOD_REPORT_LIMIT = "food-report"
const val RECIPE_READ_LIMIT = "recipe-read"
const val RECIPE_WRITE_LIMIT = "recipe-write"

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

        register(RateLimitName(FOOD_SEARCH_LIMIT)) {
            // Each search is the catalog's heaviest statement. The app debounces
            // typing, so a person stays well under 2 a second for a minute; under
            // the global 600 alone, one client could keep the pool busy by itself.
            rateLimiter(limit = 120, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }

        register(RateLimitName(DESCRIBE_LIMIT)) {
            // One description is up to 20 catalog searches (DescribeParser.MAX_PHRASES).
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }

        register(RateLimitName(FOOD_LABEL_LIMIT)) {
            // Reading a label and submitting a food each carry two photos, and
            // a read is a paid model call. The real limits are per user
            // (FoodLabelLimiter, after auth); this stops a flood of 12 MB
            // bodies before any of them is read.
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }

        register(RateLimitName(BARCODE_LINK_LIMIT)) {
            // A link report follows a barcode miss and a search, so it can
            // never legitimately outrun the miss route's 60/min.
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }

        register(RateLimitName(RECIPE_READ_LIMIT)) {
            // A shared recipe can be read with no account (the website's recipe page), and each
            // read looks up its ingredients in the catalog. A person opening links never gets
            // near this; a scraper walking codes does.
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }

        register(RateLimitName(RECIPE_WRITE_LIMIT)) {
            // Sharing and reporting recipes. The real limits are per user (RecipeLimiter).
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }

        register(RateLimitName(FOOD_REPORT_LIMIT)) {
            // A report can carry a photo of the label. The real limits are per
            // user (FoodReportLimiter, after auth); this stops a flood of 6 MB
            // bodies before any of them is read.
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }

        register(RateLimitName(EXPORT_LIMIT)) {
            // An export reads someone's whole history, a page at a time.
            // People export now and then, not in a loop.
            rateLimiter(limit = 10, refillPeriod = 1.hours)
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
