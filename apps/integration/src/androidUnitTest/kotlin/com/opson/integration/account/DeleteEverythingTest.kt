package com.opson.integration.account

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.TestClient
import com.opson.integration.helpers.awaitState
import com.opson.integration.helpers.awaitUntil
import com.opson.integration.helpers.randomUserId
import com.opson.integration.helpers.written
import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.diary.ExerciseDraft
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.FoodSubmission
import com.opson.libraries.foods.LabelParse
import com.opson.libraries.foods.SubmissionOrigin
import com.opson.libraries.foods.SubmitResult
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.DeleteAccountOutcome
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.first
import com.opson.integration.helpers.InProcessServer
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Delete everything.** "Delete account" goes to `DELETE /v1/me`, the server
 * has Supabase hard-delete the auth user, and every table that held the
 * person's data is empty for them afterwards. On the phone the user-change
 * reset runs: signed out, the database empty, the settings back to the
 * defaults.
 */
class DeleteEverythingTest : IntegrationTest() {

    private suspend fun TestClient.fillEveryTable() {
        val day = today()
        settings.update { it.copy(numbers = it.numbers.copy(preset = PresetId.NoNumbers), waterGlasses = 9) }
        log("Pasta", kcal = 550.0, slot = MealSlot.Dinner)
        diary.addWater(day, 250)
        diary.setFeeling(day, MealSlot.Dinner, MealFeeling(hunger = Hunger.Hungry))
        weights.addWeighIn(80.0)
        customFoods.create(CustomFoodDraft(name = "Protein shake", servingAmount = 1.0, servingUnit = "scoop", kcal = 120.0))
        savedMeals.create("Lunch box", MealSlot.Lunch, listOf(LogDraft("Sandwich", Precision.Estimate, kcal = 400.0)))
        exercise.add(ExerciseDraft(day, clock.now(), "yoga", minutes = 20, kcal = 60)).written()
        submitALabel()
    }

    /** A food pack sent for review, read by the server's stand-in label model. */
    private suspend fun TestClient.submitALabel() {
        // Enough of a JPEG for the server's type sniffing; the stand-in model never decodes it.
        val front = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + Random.nextBytes(64)
        val label = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte()) + Random.nextBytes(64)
        val read = assertIs<LabelParse.Read>(foodLabels.parse(front, label))
        val submission = FoodSubmission(
            id = UUID.randomUUID().toString(),
            parseId = read.parseId,
            origin = SubmissionOrigin.Search,
            barcode = null,
            front = front,
            label = label,
            facts = read.facts,
            acknowledged = emptyList(),
        )
        assertIs<SubmitResult.Submitted>(foodLabels.submit(submission))
    }

    /**
     * Rows a phone can't write: a role, a grant and their audit are set from the admin console,
     * and a food report needs a catalog food to point at. Written straight to the database so
     * the delete is still seen to empty those tables.
     */
    private fun InProcessServer.fillServerOnlyTables(user: String) {
        sql("INSERT INTO user_roles (user_id, role, granted_by) VALUES (?::uuid, 'builder', 'test')", user)
        sql(
            "INSERT INTO entitlement_grants (id, user_id, kind, key, granted_by) VALUES (?::uuid, ?::uuid, 'bundle', 'pro', 'test')",
            UUID.randomUUID().toString(), user,
        )
        sql(
            "INSERT INTO access_audit (id, actor, action, user_id) VALUES (?::uuid, 'test', 'set_role', ?::uuid)",
            UUID.randomUUID().toString(), user,
        )
        sql(
            "INSERT INTO food_reports (id, food_id, user_id, reason, snapshot) VALUES (?::uuid, ?::uuid, ?::uuid, 'wrong_calories', '{}'::jsonb)",
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), user,
        )
        // The Builder program (V31): membership and a check against the pack go with the account.
        sql("INSERT INTO builder_members (user_id, terms_version) VALUES (?::uuid, 'test')", user)
        sql(
            "INSERT INTO food_verifications (id, food_id, user_id, outcome, grounded, groundable) VALUES (?::uuid, ?::uuid, ?::uuid, 'confirmed', 4, 4)",
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), user,
        )
        // Moderation (V30). The sighting goes with the account; the rest keep their row with the
        // user id cleared and only the keyed hash left, which is what the privacy policy says.
        val action = UUID.randomUUID().toString()
        sql(
            "INSERT INTO moderation_actions (id, user_id, kind, scope, reason, created_by) " +
                "VALUES (?::uuid, ?::uuid, 'restriction', 'contributions', 'other', 'test')",
            action, user,
        )
        sql(
            "INSERT INTO moderation_events (id, actor, action, action_id, user_id) VALUES (?::uuid, 'test', 'restrict', ?::uuid, ?::uuid)",
            UUID.randomUUID().toString(), action, user,
        )
        sql(
            "INSERT INTO food_provenance (food_id, kind, ref_id, user_id) VALUES (?::uuid, 'submission', ?::uuid, ?::uuid)",
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), user,
        )
        sql("INSERT INTO device_sightings (user_id, device_hash, platform) VALUES (?::uuid, 'hash', 'ios')", user)
        sql(
            "INSERT INTO device_markers (id, device_hash, platform, action_id, user_id, created_by) " +
                "VALUES (?::uuid, 'hash', 'ios', ?::uuid, ?::uuid, 'test')",
            UUID.randomUUID().toString(), action, user,
        )
    }

    @Test
    fun deletingTheAccount_leavesNoRowsOnTheServer_andACleanPhone() = integration {
        val user = randomUserId()
        val phone = client(user)
        phone.homeVm().stateFlow.awaitState { it.userName != null } // the profile row exists
        phone.syncNow().getOrThrow()
        phone.settings.isKnown.awaitState { it }
        phone.fillEveryTable()
        phone.syncNow().getOrThrow()
        server.fillServerOnlyTables(user)
        val tables = server.userTables()
        // The build is recorded after the response, off the request.
        awaitUntil { server.countFor("user_installs", user) > 0 }
        val before = tables.associateWith { server.countFor(it, user) }
        assertEquals(
            emptySet(),
            before.filterValues { it == 0L }.keys - NOT_FILLED_HERE,
            "every table with a user_id holds something first, so the delete is seen to empty it: $before",
        )

        assertEquals(DeleteAccountOutcome.Success, phone.auth.deleteAccount())

        val after = tables.associateWith { server.countFor(it, user) }
        assertEquals(tables.associateWith { 0L }, after)
        assertEquals(emptyList(), server.query("SELECT id FROM auth.users WHERE id = ?::uuid", user))
        assertEquals(setOf(user), server.upstreams.deletedUsers)

        val state = assertIs<AuthState.Unauthenticated>(phone.auth.observe().first())
        assertEquals(AuthState.Unauthenticated.Reason.SignedOut, state.reason)
        assertNull(phone.userId)
        assertEquals(0, phone.pendingChanges())
        assertNull(phone.tables.syncState.get(), "the next account starts its pull from zero")
        assertEquals(emptyList(), phone.diary.observeDay(phone.today()).first().allItems)
        assertEquals(emptyList(), phone.weights.observeWeighIns().first())
        assertEquals(emptyList(), phone.customFoods.observeAll().first())
        assertEquals(emptyList(), phone.savedMeals.observeAll().first())
        assertTrue(phone.exercise.observeDay(phone.today()).first().isEmpty())
        // The settings flow trails the cleared store by a moment, as it does for a screen.
        phone.settings.settings.awaitState { it == Settings() } // No numbers is gone with the account
    }

    private companion object {
        /**
         * Tables this journey has no way to fill: a fast needs `release.fasting` and an evening,
         * an Apple token a Sign in with Apple ([AppleRevocationTest] deletes one).
         */
        val NOT_FILLED_HERE = setOf("fasts", "apple_refresh_tokens")
    }
}
