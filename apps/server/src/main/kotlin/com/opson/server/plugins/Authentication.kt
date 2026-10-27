package com.opson.server.plugins

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.JWTVerifier
import com.opson.server.domain.UserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTAuthenticationProvider
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import java.net.URI
import java.util.concurrent.TimeUnit

/** Authentication-realm name; used by `authenticate(SUPABASE_JWT_AUTH)` in routes. */
const val SUPABASE_JWT_AUTH = "supabase-jwt"

/**
 * How inbound JWTs are verified — the seam that makes auth testable.
 *
 *  - [Jwks] is production: fetch Supabase's public ES256 keys from the JWKS
 *    endpoint and verify signatures against them.
 *  - [Static] is for tests: verify against a caller-supplied verifier (e.g.
 *    HS256 + a known secret), so a test can mint matching tokens with no
 *    network.
 *
 * Both run the identical `validate` + `challenge` behaviour (UUID `sub`, JSON
 * 401) — only the signature check differs.
 */
sealed interface JwtVerification {
    data class Jwks(val jwksUrl: String, val issuer: String) : JwtVerification
    data class Static(val verifier: JWTVerifier) : JwtVerification
}

/**
 * Installs JWT auth under [SUPABASE_JWT_AUTH] with the given [verification]
 * strategy. The single seam shared by production [com.opson.server.module]
 * and full-stack tests.
 *
 * [banGate], when present, folds the moderation check into the auth flow: a
 * banned user's token validates to "no principal" and the challenge renders a
 * `403` [AccessDeniedResponse] instead of the default `401`. Folding it here
 * (rather than a separate post-auth plugin) is deliberate: the JWT provider's
 * validate→challenge path is the one place in Ktor that reliably
 * short-circuits the routing pipeline, so a banned caller never reaches a
 * route handler.
 */
fun Application.installAuthentication(verification: JwtVerification, banGate: BanGate? = null) {
    install(Authentication) {
        jwt(SUPABASE_JWT_AUTH) {
            when (verification) {
                is JwtVerification.Jwks -> {
                    val jwkProvider = JwkProviderBuilder(URI(verification.jwksUrl).toURL())
                        .cached(10, 24, TimeUnit.HOURS)
                        .rateLimited(10, 1, TimeUnit.MINUTES)
                        .build()
                    verifier(jwkProvider, verification.issuer) {
                        withAudience("authenticated")
                    }
                }

                is JwtVerification.Static -> verifier(verification.verifier)
            }
            validateAndChallengeForUserId(banGate)
        }
    }
}

/**
 * Marks a call whose token was valid but belongs to a banned user. Set during
 * `validate` (which then returns no principal); read in `challenge` so a ban
 * renders the `403` [AccessDeniedResponse] while a genuinely missing/invalid
 * token still renders the `401`.
 */
private val BannedResponseKey = AttributeKey<AccessDeniedResponse>("opson.banned-response")

private fun JWTAuthenticationProvider.Config.validateAndChallengeForUserId(banGate: BanGate?) {
    validate { credential ->
        // `sub` must be a UUID. Anything else is a malformed token → 401.
        val sub = credential.payload.subject
        val userId = if (sub.isNullOrBlank()) null else UserId.parse(sub)
        if (userId == null) return@validate null

        if (banGate != null && rejectIfBanned(banGate, userId)) return@validate null

        JWTPrincipal(credential.payload)
    }
    challenge { _, _ ->
        val banned = call.attributes.getOrNull(BannedResponseKey)
        if (banned != null) {
            call.respond(HttpStatusCode.Forbidden, banned)
        } else {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("error" to mapOf("code" to "unauthorized", "message" to "Missing or invalid access token")),
            )
        }
    }
}

/**
 * True if [userId] is banned — and, as a side effect, stashes the locked `403`
 * envelope on the call so `challenge` can render it.
 */
private suspend fun ApplicationCall.rejectIfBanned(banGate: BanGate, userId: UserId): Boolean {
    val status = banGate.banStatusFor(userId) ?: return false
    attributes.put(
        BannedResponseKey,
        AccessDeniedResponse(
            reason = status.reason.wire,
            until = status.until?.toString(),
            appealUrl = banGate.appealUrl,
        ),
    )
    return true
}

/**
 * The caller's `auth.users.id` from the validated JWT. Only meaningful inside an
 * `authenticate(SUPABASE_JWT_AUTH) { … }` block — null outside it.
 */
fun ApplicationCall.userId(): UserId? {
    val principal = principal<JWTPrincipal>() ?: return null
    val sub = principal.payload.subject ?: return null
    return UserId.parse(sub)
}

/**
 * Whether the caller's JWT was issued for an anonymous user. Supabase marks
 * anonymous users with the `is_anonymous: true` claim.
 */
fun ApplicationCall.isAnonymousUser(): Boolean {
    val principal = principal<JWTPrincipal>() ?: return false
    return principal.payload.getClaim("is_anonymous").asBoolean() ?: false
}
