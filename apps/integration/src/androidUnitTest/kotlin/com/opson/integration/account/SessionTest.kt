package com.opson.integration.account

import com.opson.integration.helpers.IntegrationAuth
import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.identity.auth.AuthState
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.ktor.client.request.get
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * **A token that runs out mid-session.** The server answers 401 to an expired token; the phone
 * refreshes and sends the call again, and the person never notices. When Supabase refuses the
 * refresh too, the session is dead: the phone signs out with `SessionExpired`, which routes to
 * sign-in, rather than sending the dead token forever.
 */
class SessionTest : IntegrationTest() {

    @Test
    fun anExpiredToken_isRefreshedAndTheCallSucceeds() = integration {
        val user = randomUserId()
        val phone = client(user, setUpSession = { it.holdToken(IntegrationAuth.mintJwt(user, expiresIn = (-1).minutes)) })
        phone.log("Soup", kcal = 200.0)

        phone.syncNow().getOrThrow()

        assertTrue(phone.gateway.refreshes.get() >= 1, "the 401 asked Supabase for a new token")
        assertEquals(1L, server.countFor("log_entries", user))
        assertEquals(0, phone.pendingChanges())
        assertIs<AuthState.Authenticated>(phone.auth.current())
    }

    @Test
    fun aRejectedRefresh_signsOut() = integration {
        val user = randomUserId()
        val unauthorized = http.get("${server.baseUrl}/v1/me") // a real 401 for the SDK's exception to carry
        val phone = client(user) { gateway ->
            gateway.holdToken(IntegrationAuth.mintJwt(user, expiresIn = (-1).minutes))
            gateway.refusesRefresh = UnauthorizedRestException("invalid_grant", unauthorized, "Refresh token revoked")
        }

        phone.syncNow()

        val state = withTimeout(20_000) {
            phone.auth.observe().first { it is AuthState.Unauthenticated } as AuthState.Unauthenticated
        }
        assertEquals(AuthState.Unauthenticated.Reason.SessionExpired, state.reason)
        assertNull(phone.userId, "the dead session is gone, so nothing re-sends its token")
    }
}
