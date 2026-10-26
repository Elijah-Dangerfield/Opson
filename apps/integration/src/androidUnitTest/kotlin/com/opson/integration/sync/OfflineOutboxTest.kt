package com.opson.integration.sync

import com.opson.integration.helpers.FaultProxy.Fault
import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.diary.MealSlot
import kotlinx.coroutines.flow.first
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Offline, then online.** Writes made while the server cannot be reached
 * land on the device at once and wait in the outbox; they reach the server once
 * it can be reached again, exactly once each, including when a request reached
 * the server but its answer never made it back and the client sent it again.
 */
class OfflineOutboxTest : IntegrationTest() {

    @Test
    fun writesWhileUnreachable_waitInTheOutbox_andSyncOnceTheServerIsBack() = integration {
        val user = randomUserId()
        val proxy = faultProxy()
        val phone = client(user, through = proxy)
        phone.syncNow().getOrThrow()

        proxy.goDown()
        phone.log("Coffee", kcal = 5.0, slot = MealSlot.Breakfast)
        phone.log("Croissant", kcal = 270.0, slot = MealSlot.Breakfast)
        phone.weights.addWeighIn(70.2)

        assertEquals(2, phone.diary.observeDay(phone.today()).first().allItems.size, "the diary shows them at once")
        assertTrue(phone.syncNow().isFailure, "no network, no sync")
        assertEquals(3, phone.pendingChanges(), "all three wait in the outbox")
        assertEquals(0L, server.countFor("log_entries", user))

        proxy.comeBack()
        phone.syncNow().getOrThrow()

        assertEquals(0, phone.pendingChanges())
        assertEquals(2L, server.countFor("log_entries", user))
        assertEquals(1L, server.countFor("weigh_ins", user))
    }

    @Test
    fun aResponseLostAfterTheServerAppliedIt_isRetried_withoutDuplicates() = integration {
        val user = randomUserId()
        val proxy = faultProxy()
        val phone = client(user, through = proxy)
        val oatsId = server.seedFood("Oats, rolled ${UUID.randomUUID()}", kcalPer100g = 380.0)

        proxy.failNext("/v1/sync", Fault.LoseResponse)
        val oats = phone.log("Oats", kcal = 150.0, foodId = oatsId)
        phone.syncNow().getOrThrow()

        assertTrue(proxy.requests.count { it == "/v1/sync" } >= 2, "the lost answer was retried")
        assertEquals(0, phone.pendingChanges())
        assertEquals(
            listOf(oats.id),
            server.query("SELECT id::text AS id FROM log_entries WHERE user_id = ?::uuid", user).map { it["id"] },
            "one row, though the server saw the mutation twice",
        )
        assertEquals(
            1,
            server.query("SELECT popularity FROM foods WHERE id = ?::uuid", oatsId).single()["popularity"],
            "the replay did not count the pick twice",
        )

        val other = client(user)
        other.syncNow().getOrThrow()
        assertEquals(listOf(oats.id), other.diary.observeDay(other.today()).first().allItems.map { it.id })
    }
}
