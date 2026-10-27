package com.opson.integration.account

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.awaitUntil
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.identity.auth.AppleSignInCredential
import com.opson.libraries.identity.auth.DeleteAccountOutcome
import com.opson.libraries.identity.auth.SignInOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Deleting an account made with Sign in with Apple revokes its Apple token**, as
 * App Review asks. The app sends Apple's one-use code after signing in, the server
 * trades it with (fake) Apple for a refresh token, and `DELETE /v1/me` revokes that
 * token once the account is gone.
 */
class AppleRevocationTest : IntegrationTest() {

    @Test
    fun deletingAnAppleAccount_revokesItsAppleToken() = integration {
        val user = randomUserId()
        val phone = client(user)

        val signIn = phone.auth.signInWithApple(
            AppleSignInCredential(identityToken = "id-token", nonce = "nonce", authorizationCode = "code-1"),
        )
        assertEquals(SignInOutcome.Success, signIn)
        awaitUntil { server.query("SELECT 1 FROM apple_refresh_tokens WHERE user_id = ?::uuid", user).isNotEmpty() }

        assertEquals(DeleteAccountOutcome.Success, phone.auth.deleteAccount())

        assertEquals(listOf("apple-refresh-code-1"), server.upstreams.revokedAppleTokens)
        assertEquals(emptyList(), server.query("SELECT 1 FROM apple_refresh_tokens WHERE user_id = ?::uuid", user))
    }

    @Test
    fun deletingAnAccountWithoutApple_asksAppleNothing() = integration {
        val phone = client(randomUserId())

        assertEquals(DeleteAccountOutcome.Success, phone.auth.deleteAccount())

        assertEquals(emptyList(), server.upstreams.revokedAppleTokens)
    }
}
