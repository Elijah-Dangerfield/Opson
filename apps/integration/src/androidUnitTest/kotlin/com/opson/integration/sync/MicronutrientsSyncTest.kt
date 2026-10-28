package com.opson.integration.sync

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.awaitState
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.nutrition.Micronutrient
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **Vitamins and minerals between two devices.** A 0 a food listed reaches the other phone as 0
 * and a nutrient it never listed stays missing, so "Not listed" means the same thing on both.
 * Their own references and Today's four follow them, and going back to the defaults reaches the
 * other phone too: the server merges settings one level deep and keeps a key a write leaves out,
 * so the defaults are written as `{}` and `[]`, never left out.
 */
class MicronutrientsSyncTest : IntegrationTest() {

    @Test
    fun aListedZero_andANutrientNotListed_reachTheOtherDeviceAsTheyWere() = integration {
        val user = randomUserId()
        val a = client(user)
        val b = client(user)
        a.diary.addItems(
            day = a.today(),
            slot = MealSlot.Lunch,
            items = listOf(
                LogDraft("Sparkling water", Precision.Estimate, amountText = "1 can", kcal = 0.0, micros = mapOf("sodium_mg" to 0.0, "calcium_mg" to 12.0)),
                LogDraft("Something at a friend's", Precision.Estimate),
            ),
            method = LogMethod.Search,
        )
        a.syncNow().getOrThrow()
        b.syncNow().getOrThrow()

        val items = b.diary.observeDay(b.today()).first().allItems.associateBy { it.name }
        assertEquals(mapOf("sodium_mg" to 0.0, "calcium_mg" to 12.0), items.getValue("Sparkling water").micros)
        assertNull(items.getValue("Something at a friend's").micros, "nothing listed is null, not zeros")
        val row = server.query(
            "SELECT micros->>'sodium_mg' AS sodium, jsonb_exists(micros, 'vitamin_d_ug') AS has_d FROM log_entries WHERE name = ? AND user_id = ?::uuid",
            "Sparkling water",
            user,
        ).single()
        assertEquals("0.0", row["sodium"])
        assertEquals(false, row["has_d"])
    }

    @Test
    fun ownReferencesAndTodaysFour_followThem_andSoDoesGoingBack() = integration {
        val user = randomUserId()
        val a = client(user)
        val b = client(user)
        a.syncNow().getOrThrow()
        a.settings.isKnown.awaitState { it }
        a.settings.update { it.withMicroTarget(Micronutrient.Iron, 12.0).withTodayNutrient(Micronutrient.Sodium, false).withTodayNutrient(Micronutrient.VitaminD, true) }
        a.syncNow().getOrThrow()

        b.syncNow().getOrThrow()
        val pulled = b.settings.settings.awaitState { it.microTargets.isNotEmpty() }
        assertEquals(12.0, pulled.microReferences(b.today()).of(Micronutrient.Iron))
        assertEquals(listOf(Micronutrient.Potassium, Micronutrient.Calcium, Micronutrient.Iron, Micronutrient.VitaminD), pulled.todayMicronutrients)

        b.settings.update { it.copy(microTargets = emptyMap(), todayNutrients = emptyList()) }
        b.syncNow().getOrThrow()
        a.syncNow().getOrThrow()
        val reset = a.settings.settings.awaitState { it.microTargets.isEmpty() }
        assertEquals(emptyList(), reset.todayNutrients, "the default four reached A, rather than the merge keeping A's old picks")
    }
}
