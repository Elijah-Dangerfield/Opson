package com.opson.integration.account

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.diary.MealSlot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Export my data.** Support's "Export" downloads `GET /v1/export.csv` through the app's own
 * client. It is the account's log, not the phone's: what another phone synced is in it, and what
 * was deleted is not. Another account's rows never are.
 */
class ExportTest : IntegrationTest() {

    @Test
    fun theCsvHoldsWhatWasLoggedOnAnotherPhone() = integration {
        val user = randomUserId()
        val otherPhone = client(user)
        otherPhone.log("Lentil soup", kcal = 420.0, slot = MealSlot.Dinner)
        val removed = otherPhone.log("Biscuit", kcal = 90.0)
        otherPhone.weights.addWeighIn(71.5)
        otherPhone.syncNow().getOrThrow()
        otherPhone.diary.removeItem(removed.id)
        otherPhone.syncNow().getOrThrow()
        client(randomUserId()).apply { log("Someone else's toast", kcal = 180.0) }.syncNow().getOrThrow()

        val phone = client(user)
        val csv = phone.dataExport.csv().getOrThrow().decodeToString()

        val lines = csv.lines().filter { it.isNotBlank() }
        assertTrue(lines.size > 1, "a header and rows: $csv")
        assertTrue(lines.any { "Lentil soup" in it }, "the other phone's entry: $csv")
        assertTrue(lines.any { "71.5" in it }, "the other phone's weigh-in: $csv")
        assertFalse(lines.any { "Biscuit" in it }, "a deleted entry is not exported: $csv")
        assertFalse(lines.any { "Someone else" in it }, "another account's rows never are: $csv")
    }
}
