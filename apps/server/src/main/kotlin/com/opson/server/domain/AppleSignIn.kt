package com.opson.server.domain

/**
 * Apple's token and revoke endpoints, for the one thing Opson needs them for: revoking a
 * person's Sign in with Apple token when they delete their account, which App Review
 * asks of every app that offers Sign in with Apple.
 *
 * Revoking takes a refresh token, and native Sign in with Apple only hands the app a
 * short-lived authorization code (five minutes, one use). So the app sends the code
 * straight after signing in, [exchange] turns it into a refresh token, and
 * [AppleTokenRepository] keeps it until deletion.
 */
interface AppleSignInClient {
    suspend fun exchange(authorizationCode: String): AppleExchangeResult

    suspend fun revoke(refreshToken: String): AppleRevokeResult
}

sealed interface AppleExchangeResult {
    data class Success(val refreshToken: String) : AppleExchangeResult

    /** No Apple key on this server (`APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`). */
    data object NotConfigured : AppleExchangeResult

    /** Apple refused the code: expired, used already, or issued to another app. */
    data object Rejected : AppleExchangeResult

    data class Failure(val statusCode: Int?, val cause: Throwable?) : AppleExchangeResult
}

sealed interface AppleRevokeResult {
    data object Success : AppleRevokeResult

    data object NotConfigured : AppleRevokeResult

    data class Failure(val statusCode: Int?, val cause: Throwable?) : AppleRevokeResult
}

/**
 * The newest Apple refresh token per account. Rows go with the account: the table
 * references `auth.users` with `ON DELETE CASCADE`, so read the token before deleting.
 */
interface AppleTokenRepository {
    suspend fun save(userId: UserId, refreshToken: String)

    suspend fun find(userId: UserId): String?
}
