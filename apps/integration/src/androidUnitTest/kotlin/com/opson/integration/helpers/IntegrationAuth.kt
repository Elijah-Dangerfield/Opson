package com.opson.integration.helpers

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.opson.server.plugins.JwtVerification
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Test-controlled HS256 auth: mints Supabase-shaped JWTs and the matching
 * [JwtVerification.Static] strategy the harness server installs via the server's
 * real `installAuthentication` seam (so the harness exercises the production
 * validate/challenge path rather than re-implementing it).
 */
object IntegrationAuth {
    const val ISSUER = "https://integration-test.supabase.co/auth/v1"
    private const val SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef"

    /** Another Supabase project: what a build wired to the wrong environment signs in to. */
    private const val FOREIGN_ISSUER = "https://another-project.supabase.co/auth/v1"
    private const val FOREIGN_SECRET = "fedcba9876543210fedcba9876543210fedcba9876543210"

    /**
     * A signed Supabase-shaped JWT for [userId] (a UUID string) that expires [expiresIn] from
     * now; a negative [expiresIn] is a token that has already expired.
     */
    fun mintJwt(userId: String, expiresIn: Duration = 1.hours): String = mint(userId, expiresIn, ISSUER, SECRET)

    /** A valid token from another Supabase project, which this server must not trust. */
    fun mintForeignJwt(userId: String): String = mint(userId, 1.hours, FOREIGN_ISSUER, FOREIGN_SECRET)

    private fun mint(userId: String, expiresIn: Duration, issuer: String, secret: String): String = JWT.create()
        .withIssuer(issuer)
        .withAudience("authenticated")
        .withSubject(userId)
        .withIssuedAt(Date(System.currentTimeMillis() - 2.hours.inWholeMilliseconds))
        .withExpiresAt(Date(System.currentTimeMillis() + expiresIn.inWholeMilliseconds))
        .sign(Algorithm.HMAC256(secret))

    /** The verification strategy that mirrors [mintJwt] — fed to the real install. */
    val verification: JwtVerification = JwtVerification.Static(
        JWT.require(Algorithm.HMAC256(SECRET))
            .withIssuer(ISSUER)
            .withAudience("authenticated")
            .build(),
    )
}
