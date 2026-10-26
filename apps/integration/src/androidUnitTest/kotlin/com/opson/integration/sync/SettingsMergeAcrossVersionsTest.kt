package com.opson.integration.sync

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.awaitState
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.core.Catching
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.impl.DiarySyncApi
import com.opson.libraries.diary.impl.wire.SyncRequestDto
import com.opson.libraries.diary.impl.wire.SyncResponseDto
import com.opson.libraries.nutrition.ExerciseAdjustment
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * **Settings across app versions.** A 0.1 phone decodes settings with
 * `ignoreUnknownKeys` and re-encodes only what it knows, so every save it makes
 * leaves out what 0.2 added. The server merges a settings write into the stored
 * object instead of replacing it, so a 0.1 save on one phone must not wipe the
 * meal times and exercise choice a 0.2 phone set.
 */
class SettingsMergeAcrossVersionsTest : IntegrationTest() {

    /** The 0.1 wire: settings rows go up and come down without the keys 0.2 added. */
    private class OldClientWire(private val delegate: DiarySyncApi) : DiarySyncApi {
        override suspend fun sync(request: SyncRequestDto): Catching<SyncResponseDto> {
            val sent = request.copy(
                mutations = request.mutations.map { m ->
                    if (m.entity != "settings") m else m.copy(row = m.row.withoutNewKeysInSettings())
                },
            )
            return delegate.sync(sent).map { response ->
                val pulled = response.changes.settings ?: return@map response
                response.copy(changes = response.changes.copy(settings = pulled.copy(settings = JsonObject(pulled.settings - V02_KEYS))))
            }
        }

        private fun JsonObject.withoutNewKeysInSettings(): JsonObject =
            JsonObject(this + ("settings" to JsonObject(getValue("settings").jsonObject - V02_KEYS)))
    }

    @Test
    fun aV01SaveAfterAV02Save_keepsTheV02Keys() = integration {
        val user = randomUserId()
        val newPhone = client(user)
        newPhone.syncNow().getOrThrow()
        newPhone.settings.isKnown.awaitState { it }
        val earlyBreakfast = MealTimes(breakfast = LocalTime(6, 30))
        newPhone.settings.update { it.copy(mealTimes = earlyBreakfast, exerciseRange = ExerciseAdjustment.Some) }
        newPhone.syncNow().getOrThrow()

        val oldPhone = client(user, wrapSync = ::OldClientWire)
        oldPhone.syncNow().getOrThrow()
        oldPhone.settings.isKnown.awaitState { it }
        assertEquals(MealTimes(), oldPhone.settings.settings.value.mealTimes, "0.1 never sees the 0.2 keys")
        oldPhone.settings.update { it.copy(waterGlasses = 10) }
        oldPhone.syncNow().getOrThrow()

        val stored = server.query(
            "SELECT jsonb_exists(settings, 'mealTimes') AS has_times, jsonb_exists(settings, 'exerciseRange') AS has_range, " +
                "(settings->>'waterGlasses')::int AS glasses FROM user_settings WHERE user_id = ?::uuid",
            user,
        ).single()
        assertEquals(10, stored["glasses"], "the 0.1 save landed")
        assertEquals(true, stored["has_times"], "the merge kept mealTimes")
        assertEquals(true, stored["has_range"], "the merge kept exerciseRange")

        newPhone.syncNow().getOrThrow()
        val settings = newPhone.settings.settings.value
        assertEquals(10, settings.waterGlasses)
        assertEquals(earlyBreakfast, settings.mealTimes)
        assertEquals(ExerciseAdjustment.Some, settings.exerciseRange)
        assertFalse(newPhone.pendingChanges() > 0)
    }

    private companion object {
        val V02_KEYS = setOf("mealTimes", "exerciseRange")
    }
}
