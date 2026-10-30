package com.opson.integration.onboarding

import com.opson.libraries.nutrition.GoalKind
import com.opson.features.onboarding.impl.NumbersFeel
import com.opson.features.onboarding.impl.PlanAnswers
import com.opson.features.onboarding.impl.PlanContext
import com.opson.features.onboarding.impl.PlanFlow
import com.opson.features.onboarding.impl.SetupAnswers
import com.opson.features.onboarding.impl.startingWith
import com.opson.integration.helpers.Harness
import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.awaitState
import com.opson.integration.helpers.awaitUntil
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.identity.auth.AppleSignInCredential
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Onboarding v4 (docs/plans/onboarding-v4.md) end to end: what the goal question leaves in
 * settings reaches the server as top-level keys and comes back on a fresh install, and a beta
 * member is recorded on the server once, only while the beta is open there too.
 */
class OnboardingGoalSyncTest : IntegrationTest() {

    /**
     * A meals goal with its streak kept: the goal list, the weight goal answered as "no weight
     * goal", celebrations and the goal's own streak on. A second phone gets the same answers, or
     * Today would offer a goal flow they already answered and lose their streak choice.
     */
    @Test
    fun aGoalAndItsStreak_reachTheServer_andAFreshInstallPullsThemBack() = integration {
        val phone = signedInPhone()
        val account = assertNotNull(phone.userId)
        val today = phone.today()
        val context = PlanContext(
            today = today,
            hard = false,
            policy = VisibilityPolicy(NumbersSettings(preset = PresetId.Full), Clock.System, TimeZone.UTC),
            fresh = true,
            asks = true,
            streakOffered = true,
            birthdayAsked = true,
        )
        val answers = PlanAnswers(wants = true, pick = GoalKind.Meals, meals = 4, streak = true)
        phone.settings.update(stampNumbersChange = false) {
            val started = it.startingWith(PresetId.Full, SetupAnswers(feel = NumbersFeel.LoveThem)).copy(celebrationsEnabled = false)
            PlanFlow.save(started.withBirthDate(LocalDate(1990, 7, 2)), answers, context)
        }

        phone.syncNow().getOrThrow()
        awaitUntil { phone.pendingChanges() == 0 }
        val stored = server.query(
            "SELECT (settings->>'celebrationsEnabled')::boolean AS celebrations, (settings->>'goalStreakEnabled')::boolean AS goal_streak, " +
                "jsonb_array_length(settings->'goals') AS goals FROM user_settings WHERE user_id = ?::uuid",
            account,
        ).single()
        assertEquals(true, stored["celebrations"])
        assertEquals(true, stored["goal_streak"])
        assertEquals(1, stored["goals"])

        val reinstall = client(account)
        reinstall.syncNow().getOrThrow()
        val pulled = reinstall.settings.settings.awaitState { it.goalStreakEnabled }
        assertEquals(GoalType.Meals(4), GoalBook.goals(pulled).single().type)
        assertEquals(GoalDirection.JustTrack, pulled.weightGoal.direction)
        assertEquals(true, pulled.celebrationsEnabled)
        assertEquals(LocalDate(1990, 7, 2), pulled.knownBirthDate)
    }

    @Test
    fun aBetaMember_isRecordedOnce_andOnlyWhileTheBetaIsOpen() = integration {
        val phone = signedInPhone()
        val account = assertNotNull(phone.userId)
        fun betaGrants(): Long = server.query(
            "SELECT count(*) AS n FROM entitlement_grants WHERE user_id = ?::uuid AND source = 'beta' AND key = 'beta' AND kind = 'bundle'",
            account,
        ).single()["n"] as Long

        try {
            phone.betaMembership.sync().getOrThrow()
            assertEquals(0L, betaGrants(), "the flag is off on the phone, so nothing is asked")

            phone.betaOn = true
            phone.betaMembership.sync().getOrThrow()
            assertEquals(0L, betaGrants(), "off on the server: closed, and not a failure")

            setBeta(open = true)
            phone.betaMembership.sync().getOrThrow()
            phone.betaMembership.sync().getOrThrow()
            assertEquals(1L, betaGrants())

            val second = client(account)
            second.betaOn = true
            second.betaMembership.sync().getOrThrow()
            assertEquals(1L, betaGrants(), "another phone finds the first join")
        } finally {
            server.sql("DELETE FROM app_config_values WHERE path = 'release.betaNotice'")
        }
    }

    private suspend fun Harness.signedInPhone() = client(userId = null).also { phone ->
        phone.auth.observe().first { it is AuthState.Unauthenticated }
        assertEquals(SignInOutcome.Success, phone.auth.signInWithApple(AppleSignInCredential(identityToken = "id-token", nonce = "nonce", authorizationCode = null)))
    }

    /** `release.betaNotice` on the server for everyone, past its config cache. */
    private fun Harness.setBeta(open: Boolean) {
        server.sql(
            "INSERT INTO app_config_values (path, value_jsonb) VALUES ('release.betaNotice', ?::jsonb) " +
                "ON CONFLICT (path) DO UPDATE SET value_jsonb = EXCLUDED.value_jsonb",
            open.toString(),
        )
        server.clock.advance(31.seconds)
    }
}
