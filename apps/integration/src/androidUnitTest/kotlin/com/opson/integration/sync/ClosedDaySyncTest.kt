package com.opson.integration.sync

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.randomUserId
import com.opson.integration.helpers.written
import com.opson.libraries.diary.DayReflection
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.ExerciseDraft
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * **A closed day holds on every phone.** Close the day on one device and, once the other has
 * synced, that one takes nothing for the day either: its diary refuses with
 * [DayWrite.DayClosed] and queues nothing. Reopening on either device opens it on both.
 */
class ClosedDaySyncTest : IntegrationTest() {

    @Test
    fun closedOnA_bCannotAddAfterSync_andReopeningOnB_opensItForBoth() = integration {
        val user = randomUserId()
        val a = client(user)
        val b = client(user)
        val day = a.today()
        val oats = a.log("Oatmeal", kcal = 300.0, slot = MealSlot.Breakfast)
        a.diary.closeDay(day, DayReflection(note = "Done for today"))
        a.syncNow().getOrThrow()
        b.syncNow().getOrThrow()

        val onB = b.diary.observeDay(day).first()
        assertTrue(onB.closedByUser, "the close travelled with the day record")
        assertEquals("Done for today", onB.note)

        val closed = DayWrite.DayClosed(day)
        val rice = LogDraft("Rice", Precision.Estimate, amountText = "1 cup", kcal = 200.0)
        assertEquals(closed, b.diary.addItems(day, MealSlot.Lunch, listOf(rice), LogMethod.Search))
        assertEquals(closed, b.diary.addWater(day, 250))
        assertEquals(closed, b.diary.removeItem(oats.id))
        assertEquals(closed, b.exercise.add(ExerciseDraft(day, b.clock.now(), "walking", minutes = 20, kcal = 60)))
        assertEquals(0, b.pendingChanges(), "nothing queued for the server")
        b.syncNow().getOrThrow()
        assertEquals(1L, server.query("SELECT count(*) AS n FROM log_entries WHERE user_id = ?::uuid AND deleted = false", user).single()["n"])

        b.clock.advance(1.minutes)
        b.diary.reopenDay(day)
        b.syncNow().getOrThrow()
        a.syncNow().getOrThrow()

        assertEquals(false, a.diary.observeDay(day).first().closedByUser, "reopened on B, open on A too")
        a.diary.addItems(day, MealSlot.Lunch, listOf(rice), LogMethod.Search).written()
        b.diary.addWater(day, 250).written()
        a.syncNow().getOrThrow()
        b.syncNow().getOrThrow()
        a.syncNow().getOrThrow()
        assertEquals(listOf("Oatmeal", "Rice"), b.diary.observeDay(day).first().allItems.map { it.name })
        assertEquals(250, a.diary.observeDay(day).first().waterMl)
    }
}
