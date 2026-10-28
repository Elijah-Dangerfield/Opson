package com.opson.integration.security

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.randomUserId
import com.opson.integration.helpers.written
import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.diary.ExerciseDraft
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.impl.wire.SyncMutationDto
import com.opson.libraries.diary.impl.wire.SyncRequestDto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **AuthZ on every sync path.** The user is the JWT `sub` and nothing else. A
 * second account can neither pull the first one's rows nor overwrite them by
 * sending mutations that reuse their ids, newer timestamps and all.
 */
class SyncAuthorizationTest : IntegrationTest() {

    @Test
    fun anotherAccount_cannotReadOrOverwriteRows_throughSync() = integration {
        val alice = randomUserId()
        val bob = randomUserId()
        val alicePhone = client(alice)
        val entry = alicePhone.log("Alice's breakfast", kcal = 400.0, slot = MealSlot.Breakfast)
        val weighIn = alicePhone.weights.addWeighIn(60.0)
        val food = alicePhone.customFoods.create(CustomFoodDraft("Alice's bar", 40.0, "g", kcal = 180.0))
        val meal = alicePhone.savedMeals.create("Alice's lunch", MealSlot.Lunch, listOf(LogDraft("Salad", Precision.Estimate)))
        alicePhone.diary.setDayNote(alicePhone.today(), "Alice's note")
        val run = alicePhone.exercise.add(ExerciseDraft(alicePhone.today(), alicePhone.clock.now(), "yoga", minutes = 30, kcal = 90)).written()
        alicePhone.syncNow().getOrThrow()

        // Bob pulls from the beginning: none of Alice's rows come.
        val bobPhone = client(bob)
        val pulled = bobPhone.syncApi.sync(SyncRequestDto(cursor = 0, accepts = EVERY_ENTITY)).getOrThrow()
        assertEquals(0, pulled.changes.logEntries.size + pulled.changes.weighIns.size + pulled.changes.customFoods.size + pulled.changes.savedMeals.size + pulled.changes.dayRecords.size)
        assertEquals(emptyList(), pulled.changes.exercises.orEmpty())

        // Bob replays Alice's ids with his own content and a later timestamp.
        val alicesRows = alicePhone.syncApi.sync(SyncRequestDto(cursor = 0, accepts = EVERY_ENTITY)).getOrThrow().changes
        val later = System.currentTimeMillis()
        fun JsonObject.hijacked(field: String, value: String) =
            JsonObject(this + (field to JsonPrimitive(value)) + ("clientUpdatedAtEpochMs" to JsonPrimitive(later)))
        val mutations = listOf(
            "log_entry" to wire(alicesRows.logEntries.single()).hijacked("name", "Bob was here"),
            "weigh_in" to JsonObject(wire(alicesRows.weighIns.single()) + ("kg" to JsonPrimitive(120.0)) + ("clientUpdatedAtEpochMs" to JsonPrimitive(later))),
            "custom_food" to wire(alicesRows.customFoods.single()).hijacked("name", "Bob was here"),
            "saved_meal" to wire(alicesRows.savedMeals.single()).hijacked("name", "Bob was here"),
            "day_record" to wire(alicesRows.dayRecords.single()).hijacked("note", "Bob was here"),
            "exercise" to wire(alicesRows.exercises.orEmpty().single()).hijacked("activity", "running_10"),
        ).map { (entity, row) -> SyncMutationDto(id = UUID.randomUUID().toString(), entity = entity, row = row) }
        val replay = bobPhone.syncApi.sync(SyncRequestDto(cursor = 0, mutations = mutations)).getOrThrow()
        assertTrue(replay.changes.logEntries.none { it.id == entry.id }, "the replay pulls nothing of Alice's either")

        assertEquals("Alice's breakfast", server.query("SELECT name FROM log_entries WHERE id = ?::uuid", entry.id).single()["name"])
        assertEquals(60.0f, server.query("SELECT kg FROM weigh_ins WHERE id = ?::uuid", weighIn.id).single()["kg"])
        assertEquals("Alice's bar", server.query("SELECT name FROM custom_foods WHERE id = ?::uuid", food.id).single()["name"])
        assertEquals("Alice's lunch", server.query("SELECT name FROM saved_meals WHERE id = ?::uuid", meal.id).single()["name"])
        assertEquals("yoga", server.query("SELECT activity FROM exercises WHERE id = ?::uuid", run.id).single()["activity"])
        assertEquals(
            listOf(alice),
            server.query("SELECT user_id::text AS u FROM log_entries WHERE id = ?::uuid", entry.id).map { it["u"] },
            "the row still belongs to Alice",
        )

        // And Alice's phone, after another sync, still shows only her own data.
        alicePhone.syncNow().getOrThrow()
        val day = alicePhone.diary.observeDay(alicePhone.today()).first()
        assertEquals(listOf("Alice's breakfast"), day.allItems.map { it.name })
        assertEquals("Alice's note", day.note)
        assertEquals(listOf(60.0), alicePhone.weights.observeWeighIns().first().map { it.kg })
    }

    /** A pulled row back in the shape a mutation carries, as the app's wire encodes it. */
    private inline fun <reified T> wire(row: T): JsonObject = wireJson.encodeToJsonElement(serializer<T>(), row).jsonObject

    private companion object {
        /** Every entity past 0.1's, so a pull would carry Alice's of those too. */
        val EVERY_ENTITY = listOf("fast", "exercise")
    }

    private val wireJson = Json {
        explicitNulls = false
        encodeDefaults = true
    }
}
