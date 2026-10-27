package com.opson.server.plugins

import com.auth0.jwk.JwkException
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.JWTVerifier
import com.opson.server.domain.UserId
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTAuthenticationProvider
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.AttributeKey
import java.net.URI
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
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
fun Application.installAuthentication(verification: JwtVerification, banGate: BanGate? = null) =
    installAuthentication(BearerVerifier(verification), banGate)

/** As above, sharing [bearer] (and its cached signing keys) with [identifyCallerIfSignedIn]. */
fun Application.installAuthentication(bearer: BearerVerifier, banGate: BanGate? = null) {
    install(Authentication) {
        jwt(SUPABASE_JWT_AUTH) {
            verifier { header -> header.bearerToken()?.let(bearer::verifierFor) }
            validateAndChallengeForUserId(banGate)
        }
    }
}

/**
 * Checks a bearer token the way [SUPABASE_JWT_AUTH] does: signature against
 * the project's keys, issuer, audience `authenticated`, expiry. Shared by the
 * auth provider and by [identifyCallerIfSignedIn], so a route that serves
 * everyone resolves exactly the users a protected route would.
 */
class BearerVerifier(verification: JwtVerification) {
    private val forToken: (String) -> JWTVerifier? = when (verification) {
        is JwtVerification.Static -> { _ -> verification.verifier }
        is JwtVerification.Jwks -> {
            val jwkProvider = JwkProviderBuilder(URI(verification.jwksUrl).toURL())
                .cached(10, 24, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build()
            ({ token -> jwksVerifier(jwkProvider, verification.issuer, token) })
        }
    }

    /** The verifier for [token], or null when its signing key can't be found. */
    fun verifierFor(token: String): JWTVerifier? = forToken(token)

    /** [token] decoded, or null when it is malformed, expired, or not signed by this project. */
    fun verify(token: String): DecodedJWT? = try {
        verifierFor(token)?.verify(token)
    } catch (_: JWTVerificationException) {
        null
    }
}

private fun jwksVerifier(jwkProvider: JwkProvider, issuer: String, token: String): JWTVerifier? {
    val jwk = try {
        jwkProvider.get(JWT.decode(token).keyId)
    } catch (_: JWTDecodeException) {
        return null
    } catch (_: JwkException) {
        return null
    }
    val publicKey = jwk.publicKey
    val algorithm = when (jwk.algorithm) {
        "ES256" -> Algorithm.ECDSA256(publicKey as ECPublicKey, null)
        "ES384" -> Algorithm.ECDSA384(publicKey as ECPublicKey, null)
        "ES512" -> Algorithm.ECDSA512(publicKey as ECPublicKey, null)
        "RS384" -> Algorithm.RSA384(publicKey as RSAPublicKey, null)
        "RS512" -> Algorithm.RSA512(publicKey as RSAPublicKey, null)
        "RS256", null -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
        else -> return null
    }
    return JWT.require(algorithm).withIssuer(issuer).withAudience("authenticated").build()
}

private fun HttpAuthHeader.bearerToken(): String? =
    (this as? HttpAuthHeader.Single)?.takeIf { it.authScheme.equals("Bearer", ignoreCase = true) }?.blob

/**
 * For a route that answers everyone: when the request carries a token that
 * verifies, its user becomes the call's principal ([userId] works); when the
 * token is missing, expired, from another Supabase project or malformed, the
 * call goes on anonymously. `authenticate(optional = true)` can't do this:
 * Ktor lets only a request with no credentials through, and answers 401 to
 * one with bad credentials. Bans are not checked here; banned users are
 * stopped on the routes that touch their data.
 */
fun Route.identifyCallerIfSignedIn(bearer: BearerVerifier) {
    install(
        createRouteScopedPlugin("IdentifyCallerIfSignedIn") {
            onCall { call ->
                val token = call.request.parseAuthorizationHeader()?.bearerToken() ?: return@onCall
                val decoded = bearer.verify(token) ?: return@onCall
                if (decoded.subject?.let(UserId::parse) != null) call.authentication.principal(JWTPrincipal(decoded))
            }
        },
    )
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
 * `authenticate(SUPABASE_JWT_AUTH) { … }` block or under [identifyCallerIfSignedIn];
 * null elsewhere.
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
