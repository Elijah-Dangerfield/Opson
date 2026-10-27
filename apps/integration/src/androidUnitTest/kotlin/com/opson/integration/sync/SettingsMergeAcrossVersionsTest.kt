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
import com.opson.libraries.nutrition.PresetId
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * **Settings across app versions.** A 0.1 phone decodes settings with
 * `ignoreUnknownKeys` and re-encodes only what it knows, so every save it makes
 * leaves out what 0.2 added. The server merges a settings write into the stored
 * object instead of replacing it, one level deep, so a 0.1 save on one phone
 * must not wipe what a 0.2 phone set at the top level: meal times, the exercise
 * choice, the check-in switch and the photos choice.
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

    @Test
    fun aV01Save_keepsTheCheckInSwitchAndThePhotosChoice() = integration {
        val user = randomUserId()
        val newPhone = client(user)
        newPhone.syncNow().getOrThrow()
        newPhone.settings.isKnown.awaitState { it }
        newPhone.settings.update { it.copy(checkIn = false, numbers = it.numbers.copy(progressPhotos = false)) }
        newPhone.syncNow().getOrThrow()

        val oldPhone = client(user, wrapSync = ::OldClientWire)
        oldPhone.syncNow().getOrThrow()
        oldPhone.settings.isKnown.awaitState { it }
        assertNull(oldPhone.settings.settings.value.numbers.progressPhotos, "0.1 never sees the photos choice")
        // 0.1 rewrites numbers whole: the photos choice must not live inside it.
        oldPhone.settings.update { it.copy(waterGlasses = 9, numbers = it.numbers.copy(pauseBeforeMore = true)) }
        oldPhone.syncNow().getOrThrow()

        val stored = server.query(
            "SELECT settings->>'checkIn' AS check_in, settings->>'progressPhotos' AS photos, " +
                "(settings->>'waterGlasses')::int AS glasses FROM user_settings WHERE user_id = ?::uuid",
            user,
        ).single()
        assertEquals(9, stored["glasses"], "the 0.1 save landed")
        assertEquals("false", stored["check_in"], "the merge kept checkIn")
        assertEquals("false", stored["photos"], "the merge kept progressPhotos")

        newPhone.syncNow().getOrThrow()
        val settings = newPhone.settings.settings.value
        assertFalse(settings.checkIn)
        assertEquals(false, settings.numbers.progressPhotos)
        assertEquals(true, settings.numbers.pauseBeforeMore)
    }

    @Test
    fun pickingAPresetClearsThePhotosChoice_onTheServerToo() = integration {
        val user = randomUserId()
        val phone = client(user)
        phone.syncNow().getOrThrow()
        phone.settings.isKnown.awaitState { it }
        phone.settings.update { it.copy(numbers = it.numbers.copy(preset = PresetId.Fuel, progressPhotos = true)) }
        phone.syncNow().getOrThrow()
        phone.settings.update { it.withNumbers(it.numbers.pickPreset(PresetId.Fuel, Instant.parse("2026-10-25T20:00:00Z"))) }
        phone.syncNow().getOrThrow()

        val secondPhone = client(user)
        secondPhone.syncNow().getOrThrow()
        secondPhone.settings.isKnown.awaitState { it }
        assertNull(secondPhone.settings.settings.value.numbers.progressPhotos, "a null is written, so the merge can't keep the old choice")
        assertFalse(secondPhone.settings.settings.value.numbers.showsProgressPhotos)
    }

    private companion object {
        /** Keys 0.1 doesn't know, so drops on decode and never sends. */
        val V02_KEYS = setOf("mealTimes", "exerciseRange", "checkIn", "progressPhotos")
    }
}
