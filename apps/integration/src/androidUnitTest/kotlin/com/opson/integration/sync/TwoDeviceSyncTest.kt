package com.opson.integration.sync

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.TestClient
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * **Two devices, one account.** Whatever order the devices sync in, they end
 * up showing the same diary: the newest write to a row wins on both
 * (last-write-wins on `clientUpdatedAt`), and a delete travels as a tombstone
 * and removes the row on the device that did not delete it.
 */
class TwoDeviceSyncTest : IntegrationTest() {

    private suspend fun TestClient.itemsToday(): List<LogItem> = diary.observeDay(today()).first().allItems

    @Test
    fun logOnA_editOnB_deleteOnA_bothDevicesEndIdentical() = integration {
        val user = randomUserId()
        val a = client(user)
        val b = client(user)

        val oats = a.log("Oatmeal", kcal = 300.0, slot = MealSlot.Breakfast)
        a.syncNow().getOrThrow()
        b.syncNow().getOrThrow()
        assertEquals(listOf(oats), b.itemsToday(), "B pulls A's log exactly as A wrote it")

        b.diary.updateItem(b.itemsToday().single().copy(name = "Oatmeal with berries", kcal = 350.0))
        b.syncNow().getOrThrow()
        a.syncNow().getOrThrow()
        assertEquals("Oatmeal with berries", a.itemsToday().single().name)
        assertEquals(350.0, a.itemsToday().single().kcal)

        a.diary.removeItem(oats.id)
        a.syncNow().getOrThrow()
        b.syncNow().getOrThrow()
        assertEquals(emptyList(), a.itemsToday())
        assertEquals(emptyList(), b.itemsToday(), "the tombstone removed it on B")

        val row = server.query("SELECT deleted, name FROM log_entries WHERE id = ?::uuid", oats.id).single()
        assertEquals(true, row["deleted"], "the server keeps the tombstone, so a third device learns of the delete")
        assertEquals(0, a.pendingChanges() + b.pendingChanges())
    }

    @Test
    fun concurrentEdits_theLaterOneWins_whicheverDeviceSyncsLast() = integration {
        val user = randomUserId()
        val a = client(user)
        val b = client(user)
        val soup = a.log("Soup", kcal = 200.0)
        a.syncNow().getOrThrow()
        b.syncNow().getOrThrow()

        // Both edit before either syncs. B's edit is the later one.
        a.diary.updateItem(soup.copy(name = "Tomato soup"))
        b.clock.advance(1.minutes)
        b.diary.updateItem(b.itemsToday().single().copy(name = "Lentil soup"))

        // B syncs first; A's older edit arrives after it and loses.
        b.syncNow().getOrThrow()
        a.syncNow().getOrThrow()
        b.syncNow().getOrThrow()

        assertEquals("Lentil soup", a.itemsToday().single().name, "A takes B's newer edit even though A synced last")
        assertEquals("Lentil soup", b.itemsToday().single().name)
        assertEquals("Lentil soup", server.query("SELECT name FROM log_entries WHERE id = ?::uuid", soup.id).single()["name"])
    }

    @Test
    fun aDeleteNewerThanAnEdit_winsOnTheEditingDevice() = integration {
        val user = randomUserId()
        val a = client(user)
        val b = client(user)
        val toast = a.log("Toast", kcal = 90.0)
        a.syncNow().getOrThrow()
        b.syncNow().getOrThrow()

        b.diary.updateItem(b.itemsToday().single().copy(name = "Buttered toast"))
        a.clock.advance(1.minutes)
        a.diary.removeItem(toast.id)
        a.syncNow().getOrThrow()
        b.syncNow().getOrThrow()

        assertEquals(emptyList(), b.itemsToday(), "B's older edit is overruled by A's delete")
        assertEquals(0, b.pendingChanges())
    }

    @Test
    fun everyKindOfRecord_reachesTheOtherDevice() = integration {
        val user = randomUserId()
        val a = client(user)
        val b = client(user)
        val day = a.today()

        a.diary.addWater(day, 500)
        a.diary.setDayNote(day, "Long run in the morning")
        a.diary.setFeeling(day, MealSlot.Lunch, MealFeeling(hunger = Hunger.Starving))
        val weighIn = a.weights.addWeighIn(71.5)
        val bar = a.customFoods.create(CustomFoodDraft(name = "Gran's flapjack", servingAmount = 60.0, servingUnit = "g", kcal = 250.0))
        val meal = a.savedMeals.create(
            "Usual breakfast",
            MealSlot.Breakfast,
            listOf(LogDraft("Porridge", Precision.Estimate, amountText = "1 bowl", kcal = 300.0)),
        )
        a.syncNow().getOrThrow()
        b.syncNow().getOrThrow()

        val onB = b.diary.observeDay(day).first()
        assertEquals(500, onB.waterMl)
        assertEquals("Long run in the morning", onB.note)
        assertEquals(Hunger.Starving, onB.feelings[MealSlot.Lunch]?.hunger)
        assertEquals(listOf(weighIn), b.weights.observeWeighIns().first())
        assertEquals(listOf(bar), b.customFoods.observeAll().first())
        assertEquals(listOf(meal), b.savedMeals.observeAll().first())
        assertTrue(b.pendingChanges() == 0, "nothing pulled is queued to go back up")
    }

    /** The food detail's "From label" (design sync 4): the server takes it, and the other device reads it back as label. */
    @Test
    fun aFromLabelAmount_syncsAsLabel() = integration {
        val user = randomUserId()
        val a = client(user)
        val b = client(user)
        val day = a.today()
        a.diary.addItems(
            day = day,
            slot = MealSlot.Snack,
            items = listOf(LogDraft("Peanut butter", Precision.Label, amountText = "2 tbsp", quantity = 2.0, unit = "tbsp", grams = 32.0, kcal = 190.0)),
            method = LogMethod.Barcode,
        )
        a.syncNow().getOrThrow()
        b.syncNow().getOrThrow()

        assertEquals(Precision.Label, b.itemsToday().single().precision)
        val id = a.itemsToday().single().id
        assertEquals("label", server.query("SELECT precision FROM log_entries WHERE id = ?::uuid", id).single()["precision"])
    }
}
