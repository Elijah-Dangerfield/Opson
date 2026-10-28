package com.opson.integration.account

import com.opson.integration.helpers.InProcessServer
import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.networking.AccessDeniedBus
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * **A banned user.** Supabase's `banned_until` on the account makes every authed call a 403 with
 * the access-denied envelope instead of a 401, and the phone hands that to `AccessDeniedBus`,
 * which the app shell routes to the blocking screen. The write the person made stays queued.
 */
class BanTest : IntegrationTest() {

    @Test
    fun aBannedUser_seesTheDenial() = integration {
        val user = randomUserId()
        server.seedAuthUser(user)
        server.sql("UPDATE auth.users SET banned_until = ?::timestamptz WHERE id = ?::uuid", BANNED_UNTIL, user)
        val phone = client(user)

        val denial = coroutineScope {
            val denied = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { phone.accessDenied.denials.first() } }
            phone.log("Soup", kcal = 200.0)
            phone.syncNow()
            denied.await()
        }

        assertEquals(AccessDeniedBus.Denial("banned", until = denial.until, appealUrl = InProcessServer.APPEAL_URL), denial)
        assertEquals(Instant.parse(BANNED_UNTIL), Instant.parse(checkNotNull(denial.until)))
        assertTrue(phone.syncNow().isFailure, "every call keeps failing the same way")
        assertEquals(1, phone.pendingChanges(), "the write stays on the phone")
        assertEquals(0L, server.countFor("log_entries", user))
    }

    private companion object {
        const val BANNED_UNTIL = "2099-01-01T00:00:00Z"
    }
}
