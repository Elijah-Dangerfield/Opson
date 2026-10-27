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
import com.opson.libraries.nutrition.RangeEnd
import com.opson.libraries.nutrition.TargetStyle
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
 * choice, the check-in switch, the photos choice and the goal style.
 */
class SettingsMergeAcrossVersionsTest : IntegrationTest() {

    /**
     * The 0.1 wire: settings rows go up and come down without the keys 0.2 added, at the top
     * level and inside `targets`, whose 0.1 serializer knew no goal style.
     */
    private class OldClientWire(private val delegate: DiarySyncApi) : DiarySyncApi {
        override suspend fun sync(request: SyncRequestDto): Catching<SyncResponseDto> {
            val sent = request.copy(
                mutations = request.mutations.map { m ->
                    if (m.entity != "settings") m else m.copy(row = m.row.withoutNewKeysInSettings())
                },
            )
            return delegate.sync(sent).map { response ->
                val pulled = response.changes.settings ?: return@map response
                response.copy(changes = response.changes.copy(settings = pulled.copy(settings = pulled.settings.as01())))
            }
        }

        private fun JsonObject.withoutNewKeysInSettings(): JsonObject =
            JsonObject(this + ("settings" to getValue("settings").jsonObject.as01()))

        private fun JsonObject.as01(): JsonObject {
            val targets = this["targets"]?.jsonObject ?: return JsonObject(this - V02_KEYS)
            return JsonObject(this - V02_KEYS + ("targets" to JsonObject(targets - "style")))
        }
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
        // The pull lands in the store, and the settings flow catches up on the app
        // scope a moment later, so wait on the flow the way a screen reads it.
        val settings = newPhone.settings.settings.awaitState { it.waterGlasses == 10 }
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
        // The pull lands in the store, and the settings flow catches up on the app scope a moment later.
        val settings = newPhone.settings.settings.awaitState { it.numbers.pauseBeforeMore }
        assertFalse(settings.checkIn)
        assertEquals(false, settings.numbers.progressPhotos)
        assertEquals(true, settings.numbers.pauseBeforeMore)
    }

    @Test
    fun aV01SaveOfTargets_keepsTheGoalStyle() = integration {
        val user = randomUserId()
        val newPhone = client(user)
        newPhone.syncNow().getOrThrow()
        newPhone.settings.isKnown.awaitState { it }
        val surplus = newPhone.settings.update { it.copy(targets = it.targets.useSurplus(burnKcal = null).stepSurplus(up = true)!!) }
        assertEquals(TargetStyle.Surplus(50), surplus.targets.style)
        newPhone.syncNow().getOrThrow()

        val oldPhone = client(user, wrapSync = ::OldClientWire)
        oldPhone.syncNow().getOrThrow()
        oldPhone.settings.isKnown.awaitState { it }
        assertEquals(TargetStyle.Range, oldPhone.settings.settings.value.targets.style, "0.1 never sees the goal style")
        // 0.1 rewrites targets whole: the style must not live inside it.
        oldPhone.settings.update { it.copy(targets = it.targets.step(RangeEnd.High, up = true)!!) }
        oldPhone.syncNow().getOrThrow()

        val stored = server.query(
            "SELECT settings->'targetStyle'->>'kind' AS kind, (settings->'targets'->>'maxKcal')::int AS max_kcal, " +
                "jsonb_exists(settings->'targets', 'style') AS nested FROM user_settings WHERE user_id = ?::uuid",
            user,
        ).single()
        assertEquals(2350, stored["max_kcal"], "the 0.1 save landed")
        assertEquals("surplus", stored["kind"], "the merge kept targetStyle")
        assertEquals(false, stored["nested"])

        newPhone.syncNow().getOrThrow()
        val settings = newPhone.settings.settings.awaitState { it.targets.maxKcal == 2350 }
        assertEquals(TargetStyle.Surplus(50), settings.targets.style)
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
        val V02_KEYS = setOf("mealTimes", "exerciseRange", "checkIn", "progressPhotos", "targetStyle")
    }
}
