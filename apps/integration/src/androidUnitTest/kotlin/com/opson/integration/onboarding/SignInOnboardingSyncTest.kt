package com.opson.integration.onboarding

import com.opson.features.onboarding.impl.NumbersFeel
import com.opson.features.onboarding.impl.SetupAnswers
import com.opson.features.onboarding.impl.StartingSetup
import com.opson.features.onboarding.impl.Why
import com.opson.features.onboarding.impl.startingWith
import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.awaitState
import com.opson.integration.helpers.awaitUntil
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.identity.auth.AppleSignInCredential
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.LinkIdentityOutcome
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.datetime.LocalTime
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * **Sign-in, then setup, then the first sync.** Onboarding signs in first and
 * saves the mode after, so everything is written under a real account. It all
 * has to reach the server under that account, and a fresh install signed in as
 * the same account has to get it all back: the mode (not the defaults, which
 * would show a Delayed user their numbers) and the log.
 *
 * And the guests from before accounts were required: signing in links onto the
 * guest, so the account keeps what the guest logged.
 */
class SignInOnboardingSyncTest : IntegrationTest() {

    @Test
    fun aNewAccountsSetupAndFirstLog_reachTheServer_andAFreshInstallPullsThemBack() = integration {
        val phone = client(userId = null)
        phone.auth.observe().first { it is AuthState.Unauthenticated }

        // Onboarding, in its order: sign in, then save the mode.
        assertEquals(SignInOutcome.Success, phone.auth.signInWithApple(appleCredential()))
        val account = assertNotNull(phone.userId)
        assertFalse(assertIs<AuthState.Authenticated>(phone.auth.current()).isAnonymous)
        phone.settings.update(stampNumbersChange = false) {
            it.copy(numbers = it.numbers.copy(preset = PresetId.Later), waterGlasses = 6)
        }
        val soup = phone.log("Lentil soup", kcal = 420.0)

        phone.syncNow().getOrThrow()
        // Setup is saved after sign-in, before the new account's first pull has said the server
        // has no settings for it, so the settings wait for that pull and go out on the sync it
        // asks for next.
        awaitUntil { phone.pendingChanges() == 0 }

        val stored = server.query(
            "SELECT settings->>'preset' AS preset, (settings->>'waterGlasses')::int AS glasses FROM user_settings WHERE user_id = ?::uuid",
            account,
        ).single()
        assertEquals("later", stored["preset"], "the analytics key names the mode as 0.2 does")
        assertEquals(6, stored["glasses"])
        val entries = server.query("SELECT id::text AS id, name FROM log_entries WHERE user_id = ?::uuid", account)
        assertEquals(listOf(soup.id to "Lentil soup"), entries.map { it["id"] to it["name"] })

        // The same account on a fresh install: defaults until its first pull.
        val reinstall = client(account)
        // Once auth says who it is, the defaults on screen are known not to be theirs.
        reinstall.settings.isKnown.awaitState { !it }
        reinstall.syncNow().getOrThrow()

        reinstall.settings.isKnown.awaitState { it }
        // isKnown and settings are separate flows off the same store, so the
        // settings can trail it by a moment: wait on them as a screen would.
        val pulled = reinstall.settings.settings.awaitState { it.numbers.preset == PresetId.Later }
        assertEquals(6, pulled.waterGlasses)
        val day = reinstall.diary.observeDay(phone.today()).first()
        assertEquals(listOf(soup.id), day.allItems.map { it.id })
        assertEquals(420.0, day.allItems.single().kcal)
        assertEquals(0, reinstall.pendingChanges(), "pulling queues nothing to push back")
    }

    /**
     * Onboarding v2's setup steps (docs/plans/onboarding-v2.md): the goal, resting energy's
     * details and meal times are saved with the mode, and the first weigh-in right after. All
     * of it must reach the server as top-level keys and come back on a fresh install, or a set
     * surplus would start a new phone with no burn to aim at.
     */
    @Test
    fun setupSteps_reachTheServer_andAFreshInstallPullsThemBack() = integration {
        val phone = client(userId = null)
        phone.auth.observe().first { it is AuthState.Unauthenticated }
        assertEquals(SignInOutcome.Success, phone.auth.signInWithApple(appleCredential()))
        val account = assertNotNull(phone.userId)

        val breakfast = MealTimes(breakfast = LocalTime(9, 30))
        val setup = StartingSetup(
            targets = Targets.Default.raise().useSurplus(burnKcal = 1360),
            birthYear = 1992,
            heightCm = 168.0,
            sex = BodySex.Female,
            weightUnit = WeightUnit.Lb,
            mealTimes = breakfast,
        )
        val answers = SetupAnswers(why = setOf(Why.EatingRegularly), feel = NumbersFeel.LoveThem)
        phone.settings.update(stampNumbersChange = false) { it.startingWith(PresetId.Full, answers, setup) }
        phone.weights.addWeighIn(64.0)

        phone.syncNow().getOrThrow()
        awaitUntil { phone.pendingChanges() == 0 }
        val stored = server.query(
            "SELECT jsonb_exists(settings, 'mealTimes') AS has_times, (settings->>'birthYear')::int AS year, " +
                "settings->>'targetStyle' AS style FROM user_settings WHERE user_id = ?::uuid",
            account,
        ).single()
        assertEquals(true, stored["has_times"])
        assertEquals(1992, stored["year"])
        assertNotNull(stored["style"], "the goal style is its own top-level key")
        assertEquals(1L, server.countFor("weigh_ins", account))

        val reinstall = client(account)
        reinstall.syncNow().getOrThrow()
        val pulled = reinstall.settings.settings.awaitState { it.birthYear == 1992 }
        assertEquals(breakfast, pulled.mealTimes)
        assertEquals(168.0, pulled.heightCm)
        assertEquals(BodySex.Female, pulled.bodySex)
        assertEquals(WeightUnit.Lb, pulled.weightUnit)
        assertEquals(setup.targets, pulled.targets)
        assertEquals(listOf("eating_regularly"), pulled.onboarding.why)
        assertEquals(listOf(64.0), reinstall.weights.observeWeighIns().first().map { it.kg })
    }

    /**
     * A guest from before accounts were required signs in with Apple. Linking keeps the
     * same user, so what the guest logged stays theirs, and Apple's code still reaches the
     * server so deleting the account later can revoke the token.
     */
    @Test
    fun aGuestLinkingApple_keepsWhatItLogged_underTheSameAccount() = integration {
        val guest = randomUserId()
        val phone = client(guest)
        assertEquals(true, phone.auth.awaitAuthenticated().isAnonymous, "the stub starts every install as a guest")
        phone.log("Toast", kcal = 180.0)
        phone.syncNow().getOrThrow()

        val linked = phone.auth.linkAppleIdentity(appleCredential(code = "code-guest"))

        assertEquals(LinkIdentityOutcome.Success, linked)
        val now = assertIs<AuthState.Authenticated>(phone.auth.current())
        assertEquals(guest, now.userId, "a link keeps the user; a sign-in would have switched it")
        assertFalse(now.isAnonymous)
        awaitUntil { server.query("SELECT 1 FROM apple_refresh_tokens WHERE user_id = ?::uuid", guest).isNotEmpty() }

        phone.log("Soup", kcal = 300.0)
        phone.syncNow().getOrThrow()
        val names = server.query("SELECT name FROM log_entries WHERE user_id = ?::uuid ORDER BY name", guest)
            .map { it["name"] }
        assertEquals(listOf<Any?>("Soup", "Toast"), names, "the guest's entry and the new one, one account")
    }

    private fun appleCredential(code: String? = null) =
        AppleSignInCredential(identityToken = "id-token", nonce = "nonce", authorizationCode = code)
}
