package com.opson.integration.onboarding

import com.opson.features.onboarding.impl.NumbersFeel
import com.opson.features.onboarding.impl.SetupAnswers
import com.opson.features.onboarding.impl.StartingSetup
import com.opson.features.onboarding.impl.Why
import com.opson.features.onboarding.impl.startingWith
import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.awaitState
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.datetime.LocalTime
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
            it.copy(numbers = it.numbers.copy(preset = PresetId.MacrosWithGrams), waterGlasses = 6)
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
        assertEquals("macros_grams", stored["preset"], "the analytics key names the mode as 0.2 does")
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
        val pulled = reinstall.settings.settings.awaitState { it.numbers.preset == PresetId.MacrosWithGrams }
        assertEquals(6, pulled.waterGlasses)
        val day = reinstall.diary.observeDay(phone.today()).first()
        assertEquals(listOf(soup.id), day.allItems.map { it.id })
        assertEquals(420.0, day.allItems.single().kcal)
        assertEquals(0, reinstall.pendingChanges(), "pulling queues nothing to push back")
    }

    /**
     * Onboarding v2's setup steps (docs/plans/onboarding-v2.md): the goal, resting energy's
     * details and meal times are saved with the mode before the guest exists, and the first
     * weigh-in right after. All of it must reach the server as top-level keys and come back on
     * a fresh install, or a set surplus would start a new phone with no burn to aim at.
     */
    @Test
    fun setupSteps_reachTheServer_andAFreshInstallPullsThemBack() = integration {
        val phone = client(userId = null)
        phone.auth.observe().first { it is AuthState.Unauthenticated }

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
        assertEquals(SignInOutcome.Success, phone.auth.createGuestSession())
        val guest = assertNotNull(phone.userId)
        phone.weights.addWeighIn(64.0)

        phone.syncNow().getOrThrow()
        assertEquals(0, phone.pendingChanges())
        val stored = server.query(
            "SELECT jsonb_exists(settings, 'mealTimes') AS has_times, (settings->>'birthYear')::int AS year, " +
                "settings->>'targetStyle' AS style FROM user_settings WHERE user_id = ?::uuid",
            guest,
        ).single()
        assertEquals(true, stored["has_times"])
        assertEquals(1992, stored["year"])
        assertNotNull(stored["style"], "the goal style is its own top-level key")
        assertEquals(1L, server.countFor("weigh_ins", guest))

        val reinstall = client(guest)
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
}
