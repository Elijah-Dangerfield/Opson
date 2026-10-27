package com.opson.integration.account

import com.opson.integration.helpers.InProcessServer
import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.TestClient
import com.opson.integration.helpers.awaitState
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.DeleteAccountOutcome
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.first
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
        settings.update { it.copy(numbers = it.numbers.copy(preset = PresetId.Blind), waterGlasses = 9) }
        log("Pasta", kcal = 550.0, slot = MealSlot.Dinner)
        diary.addWater(day, 250)
        diary.setFeeling(day, MealSlot.Dinner, MealFeeling(hunger = Hunger.Hungry))
        weights.addWeighIn(80.0)
        customFoods.create(CustomFoodDraft(name = "Protein shake", servingAmount = 1.0, servingUnit = "scoop", kcal = 120.0))
        savedMeals.create("Lunch box", MealSlot.Lunch, listOf(LogDraft("Sandwich", Precision.Estimate, kcal = 400.0)))
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
        val before = InProcessServer.USER_TABLES.associateWith { server.countFor(it, user) }
        assertTrue(before.filterKeys { it != "fasts" }.values.all { it > 0 }, "every table holds something first: $before")

        assertEquals(DeleteAccountOutcome.Success, phone.auth.deleteAccount())

        val after = InProcessServer.USER_TABLES.associateWith { server.countFor(it, user) }
        assertEquals(InProcessServer.USER_TABLES.associateWith { 0L }, after)
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
        // The settings flow trails the cleared store by a moment, as it does for a screen.
        phone.settings.settings.awaitState { it == Settings() } // Blind is gone with the account
    }
}
