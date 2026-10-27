package com.opson.integration.onboarding

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.awaitState
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **Guest onboarding and the first sync.** Onboarding saves the mode before
 * any account exists, then creates the guest; the first log follows. All of it
 * has to reach the server under the guest's id, and a fresh install signed in
 * as the same account has to get it all back: the mode (not the defaults,
 * which would show a Delayed user their numbers) and the log.
 */
class GuestOnboardingSyncTest : IntegrationTest() {

    @Test
    fun onboardingSettingsAndFirstLog_reachTheServer_andAFreshInstallPullsThemBack() = integration {
        val phone = client(userId = null)
        phone.auth.observe().first { it is AuthState.Unauthenticated }

        // Onboarding, in its order: save the mode, then create the guest.
        phone.settings.update(stampNumbersChange = false) {
            it.copy(numbers = it.numbers.copy(preset = PresetId.Delayed), waterGlasses = 6)
        }
        assertEquals(SignInOutcome.Success, phone.auth.createGuestSession())
        val guest = assertNotNull(phone.userId)
        val soup = phone.log("Lentil soup", kcal = 420.0)

        phone.syncNow().getOrThrow()
        assertEquals(0, phone.pendingChanges(), "everything queued was acknowledged")

        val stored = server.query(
            "SELECT settings->>'preset' AS preset, (settings->>'waterGlasses')::int AS glasses FROM user_settings WHERE user_id = ?::uuid",
            guest,
        ).single()
        assertEquals("delayed", stored["preset"])
        assertEquals(6, stored["glasses"])
        val entries = server.query("SELECT id::text AS id, name FROM log_entries WHERE user_id = ?::uuid", guest)
        assertEquals(listOf(soup.id to "Lentil soup"), entries.map { it["id"] to it["name"] })

        // The same account on a fresh install: defaults until its first pull.
        val reinstall = client(guest)
        // Once auth says who it is, the defaults on screen are known not to be theirs.
        reinstall.settings.isKnown.awaitState { !it }
        reinstall.syncNow().getOrThrow()

        reinstall.settings.isKnown.awaitState { it }
        // isKnown and settings are separate flows off the same store, so the
        // settings can trail it by a moment: wait on them as a screen would.
        val pulled = reinstall.settings.settings.awaitState { it.numbers.preset == PresetId.Delayed }
        assertEquals(6, pulled.waterGlasses)
        val day = reinstall.diary.observeDay(phone.today()).first()
        assertEquals(listOf(soup.id), day.allItems.map { it.id })
        assertEquals(420.0, day.allItems.single().kcal)
        assertEquals(0, reinstall.pendingChanges(), "pulling queues nothing to push back")
    }
}
