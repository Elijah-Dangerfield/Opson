package com.opson.server.testing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import com.opson.server.domain.UserId
import com.opson.server.plugins.JwtVerification
import java.util.Date

/**
 * HS256 tokens and the matching [JwtVerification.Static] for route and
 * full-stack tests, so a test can authenticate as any user with no network.
 */
object TestJwt {
    private const val ISSUER = "https://test-project.supabase.co/auth/v1"
    private const val SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef"

    private val verifier: JWTVerifier = JWT.require(Algorithm.HMAC256(SECRET))
        .withIssuer(ISSUER)
        .withAudience("authenticated")
        .build()

    val verification: JwtVerification = JwtVerification.Static(verifier)

    /** [anonymous] is a guest session, as Supabase marks one with the `is_anonymous` claim. */
    fun token(userId: UserId, anonymous: Boolean = false): String = JWT.create()
        .withIssuer(ISSUER)
        .withAudience("authenticated")
        .withSubject(userId.value.toString())
        .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
        .apply { if (anonymous) withClaim("is_anonymous", true) }
        .sign(Algorithm.HMAC256(SECRET))

    fun bearer(userId: UserId, anonymous: Boolean = false): String = "Bearer ${token(userId, anonymous)}"
}
