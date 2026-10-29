package com.opson.integration.sync

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.awaitState
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.core.Catching
import com.opson.libraries.diary.MainNumberChoice
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.diary.impl.DiarySyncApi
import com.opson.libraries.diary.impl.wire.SyncRequestDto
import com.opson.libraries.diary.impl.wire.SyncResponseDto
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.GroupShape
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.ModeTiming
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.RangeEnd
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.RevealOn
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Visibility
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
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
 * choice, the check-in switch, the photos choice, the goal style, where Burned comes from and,
 * since design sync 4, the mode.
 *
 * The mode is the hard one, because 0.1 rewrites `numbers` whole and knows ten presets where 0.2
 * has four. A 0.2 phone writes its preset in a top-level `mode` and, in `numbers`, an old preset
 * with the same levels; it reads `mode` unless an old phone changed `numbers` after it.
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

    /** Vitamins and minerals: their own references and Today's four are top-level keys a 0.1 save leaves alone. */
    @Test
    fun aV01Save_keepsOwnMicroReferencesAndTodaysNutrients() = integration {
        val user = randomUserId()
        val newPhone = client(user)
        newPhone.syncNow().getOrThrow()
        newPhone.settings.isKnown.awaitState { it }
        newPhone.settings.update { it.withMicroTarget(Micronutrient.VitaminD, 25.0).withTodayNutrient(Micronutrient.Sodium, false) }
        newPhone.syncNow().getOrThrow()

        val oldPhone = client(user, wrapSync = ::OldClientWire)
        oldPhone.syncNow().getOrThrow()
        oldPhone.settings.isKnown.awaitState { it }
        oldPhone.settings.update { it.copy(waterGlasses = 7) }
        oldPhone.syncNow().getOrThrow()

        val stored = server.query(
            "SELECT (settings->'microTargets'->>'vitamin_d')::float8 AS vitamin_d, settings->>'todayNutrients' AS today, " +
                "(settings->>'waterGlasses')::int AS glasses FROM user_settings WHERE user_id = ?::uuid",
            user,
        ).single()
        assertEquals(7, stored["glasses"], "the 0.1 save landed")
        assertEquals(25.0, stored["vitamin_d"], "the merge kept microTargets")
        assertEquals("""["potassium", "calcium", "iron"]""", stored["today"], "the merge kept todayNutrients")

        newPhone.syncNow().getOrThrow()
        val settings = newPhone.settings.settings.awaitState { it.waterGlasses == 7 }
        assertEquals(mapOf("vitamin_d" to 25.0), settings.microTargets)
        assertEquals(listOf(Micronutrient.Potassium, Micronutrient.Calcium, Micronutrient.Iron), settings.todayMicronutrients)
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
        val surplus = newPhone.settings.update { it.copy(targets = it.targets.useSurplus(burnKcal = 1800).stepSurplus(up = false)!!) }
        assertEquals(TargetStyle.Surplus(250, 300), surplus.targets.style)
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
        assertEquals(TargetStyle.Surplus(250, 300), settings.targets.style)
    }

    /**
     * A week with a deficit day goes up inside `targetStyle`, so a 0.1 save of `targets` leaves it
     * alone, and the deficit day comes back to the 0.2 phone as it was.
     */
    @Test
    fun aV01SaveOfTargets_keepsADeficitDay() = integration {
        val user = randomUserId()
        val newPhone = client(user)
        newPhone.syncNow().getOrThrow()
        newPhone.settings.isKnown.awaitState { it }
        val week = newPhone.settings.update {
            val byDay = it.targets.useByDay().addDayGroup()!!.moveDay(DayOfWeek.TUESDAY, 1)
            it.copy(targets = byDay.shapeGroup(1, GroupShape.Deficit(0, 0), burnKcal = 2600)!!)
        }
        val tuesday = (week.targets.style as TargetStyle.ByDay).groups[1]
        assertEquals(GroupShape.Deficit(500, 500), tuesday.shape)
        newPhone.syncNow().getOrThrow()

        val oldPhone = client(user, wrapSync = ::OldClientWire)
        oldPhone.syncNow().getOrThrow()
        oldPhone.settings.isKnown.awaitState { it }
        oldPhone.settings.update { it.copy(targets = it.targets.step(RangeEnd.High, up = true)!!) }
        oldPhone.syncNow().getOrThrow()

        val stored = server.query(
            "SELECT settings->'targetStyle'->'groups'->1->>'shape' AS shape FROM user_settings WHERE user_id = ?::uuid",
            user,
        ).single()
        assertEquals("deficit", stored["shape"], "the merge kept targetStyle")

        newPhone.syncNow().getOrThrow()
        val settings = newPhone.settings.settings.awaitState { it.targets.maxKcal == week.targets.maxKcal + 50 }
        assertEquals(tuesday, (settings.targets.style as TargetStyle.ByDay).groups[1])
    }

    @Test
    fun aV01Save_keepsWhereBurnedComesFrom() = integration {
        val user = randomUserId()
        val newPhone = client(user)
        newPhone.syncNow().getOrThrow()
        newPhone.settings.isKnown.awaitState { it }
        newPhone.settings.update {
            it.copy(
                restingSource = RestingSource.Own,
                restingOwnKcal = 1600,
                activityInBurned = false,
                everydayActivity = EverydayActivity.OnMyFeet,
                birthYear = 1990,
                heightCm = 172.0,
                bodySex = BodySex.Unsaid,
            )
        }
        newPhone.syncNow().getOrThrow()

        val oldPhone = client(user, wrapSync = ::OldClientWire)
        oldPhone.syncNow().getOrThrow()
        oldPhone.settings.isKnown.awaitState { it }
        oldPhone.settings.update { it.copy(waterGlasses = 7) }
        oldPhone.syncNow().getOrThrow()

        val stored = server.query(
            "SELECT settings->>'restingSource' AS source, (settings->>'restingOwnKcal')::int AS own, " +
                "(settings->>'waterGlasses')::int AS glasses FROM user_settings WHERE user_id = ?::uuid",
            user,
        ).single()
        assertEquals(7, stored["glasses"], "the 0.1 save landed")
        assertEquals("own", stored["source"], "the merge kept restingSource")
        assertEquals(1600, stored["own"], "the merge kept restingOwnKcal")

        newPhone.syncNow().getOrThrow()
        val settings = newPhone.settings.settings.awaitState { it.waterGlasses == 7 }
        assertEquals(RestingSource.Own, settings.restingSource)
        assertEquals(1600, settings.restingOwnKcal)
        assertFalse(settings.activityInBurned)
        assertEquals(EverydayActivity.OnMyFeet, settings.everydayActivity)
        assertEquals(1990, settings.birthYear)
        assertEquals(172.0, settings.heightCm)
        assertEquals(BodySex.Unsaid, settings.bodySex)
    }

    @Test
    fun aV01Save_keepsTheTodayScreenSettings() = integration {
        val user = randomUserId()
        val newPhone = client(user)
        newPhone.syncNow().getOrThrow()
        newPhone.settings.isKnown.awaitState { it }
        newPhone.settings.update {
            it.copy(
                todayCard = SummaryCardPreset.Equation,
                todayMainNumber = MainNumberChoice.Eaten,
                todayMainNumberByStyle = mapOf("range" to "balance"),
                todayReadings = mapOf("macros" to "percent"),
                todayMealsCount = true,
                todayWater = false,
                todayCloseDay = false,
            )
        }
        newPhone.syncNow().getOrThrow()

        val oldPhone = client(user, wrapSync = ::OldClientWire)
        oldPhone.syncNow().getOrThrow()
        oldPhone.settings.isKnown.awaitState { it }
        oldPhone.settings.update { it.copy(waterGlasses = 6) }
        oldPhone.syncNow().getOrThrow()

        val stored = server.query(
            "SELECT settings->>'todayCard' AS card, settings->'todayMainNumberByStyle'->>'range' AS remembered, " +
                "(settings->>'waterGlasses')::int AS glasses FROM user_settings WHERE user_id = ?::uuid",
            user,
        ).single()
        assertEquals(6, stored["glasses"], "the 0.1 save landed")
        assertEquals("equation", stored["card"], "the merge kept todayCard")
        assertEquals("balance", stored["remembered"], "the merge kept todayMainNumberByStyle")

        newPhone.syncNow().getOrThrow()
        val settings = newPhone.settings.settings.awaitState { it.waterGlasses == 6 }
        assertEquals(SummaryCardPreset.Equation, settings.todayCard)
        assertEquals(MainNumberChoice.Eaten, settings.todayMainNumber)
        assertEquals(mapOf("range" to "balance"), settings.todayMainNumberByStyle)
        assertEquals(mapOf("macros" to "percent"), settings.todayReadings, "the merge kept todayReadings")
        assertEquals(true, settings.todayMealsCount)
        assertFalse(settings.todayWater)
        assertFalse(settings.todayCloseDay)
    }

    @Test
    fun pickingAPresetClearsThePhotosChoice_onTheServerToo() = integration {
        val user = randomUserId()
        val phone = client(user)
        phone.syncNow().getOrThrow()
        phone.settings.isKnown.awaitState { it }
        phone.settings.update { it.copy(numbers = it.numbers.copy(preset = PresetId.MacrosOnly, progressPhotos = true)) }
        phone.syncNow().getOrThrow()
        phone.settings.update { it.withNumbers(it.numbers.pickPreset(PresetId.MacrosOnly, Instant.parse("2026-10-25T20:00:00Z"))) }
        phone.syncNow().getOrThrow()

        val secondPhone = client(user)
        secondPhone.syncNow().getOrThrow()
        secondPhone.settings.isKnown.awaitState { it }
        assertNull(secondPhone.settings.settings.value.numbers.progressPhotos, "a null is written, so the merge can't keep the old choice")
        assertFalse(secondPhone.settings.settings.value.numbers.showsProgressPhotos)
    }

    @Test
    fun aV01Save_keepsTheModeA02PhoneChose_andItsTimer() = integration {
        val user = randomUserId()
        val newPhone = client(user)
        newPhone.syncNow().getOrThrow()
        newPhone.settings.isKnown.awaitState { it }
        val timer = ModeTiming(startedOn = LocalDate(2026, 10, 20), weeks = 2, then = PresetId.NoNumbers)
        val chosen = NumbersSettings(preset = PresetId.MacrosOnly, endAfter = timer, changedAt = Instant.parse("2026-10-25T20:14:00Z"))
        newPhone.settings.update(stampNumbersChange = false) { it.withNumbers(chosen) }
        newPhone.syncNow().getOrThrow()

        val oldPhone = client(user, wrapSync = ::OldClientWire)
        oldPhone.syncNow().getOrThrow()
        oldPhone.settings.isKnown.awaitState { it }
        oldPhone.settings.update { it.copy(waterGlasses = 10) }
        oldPhone.syncNow().getOrThrow()

        val stored = server.query(
            "SELECT settings->'mode'->>'preset' AS mode, settings->'numbers'->>'preset' AS old_preset, " +
                "(settings->>'waterGlasses')::int AS glasses FROM user_settings WHERE user_id = ?::uuid",
            user,
        ).single()
        assertEquals(10, stored["glasses"], "the 0.1 save landed")
        assertEquals("macros_only", stored["mode"], "the merge kept the mode")
        assertEquals("nutrient", stored["old_preset"], "0.1 reads a preset it knows, with the same levels")

        newPhone.syncNow().getOrThrow()
        val settings = newPhone.settings.settings.awaitState { it.waterGlasses == 10 }
        assertEquals(PresetId.MacrosOnly, settings.numbers.preset)
        assertEquals(emptyMap(), settings.numbers.overrides)
        assertEquals(timer, settings.numbers.endAfter)
    }

    @Test
    fun aModePickedOnA01Phone_afterA02Save_wins_migratedToTheSameLevels() = integration {
        val user = randomUserId()
        val newPhone = client(user)
        newPhone.syncNow().getOrThrow()
        newPhone.settings.isKnown.awaitState { it }
        newPhone.settings.update { it.withNumbers(it.numbers.pickPreset(PresetId.Full, Instant.parse("2026-10-25T20:00:00Z"))) }
        newPhone.syncNow().getOrThrow()

        // The old phone picks what it calls Coast: numbers rewritten, stamped later, no mode key.
        val oldPhone = client(user, wrapSync = ::OldClientWire)
        oldPhone.syncNow().getOrThrow()
        oldPhone.settings.isKnown.awaitState { it }
        val later = NumbersSettings.later(RevealOn.Week)
        val coast = later.copy(overrides = later.overrides + COAST_HIDES)
        oldPhone.settings.update { it.withNumbers(coast.copy(changedAt = Instant.parse("2026-10-26T08:00:00Z"))) }
        oldPhone.syncNow().getOrThrow()
        val sent = server.query("SELECT settings->'numbers'->>'preset' AS old_preset FROM user_settings WHERE user_id = ?::uuid", user).single()
        assertEquals("coast", sent["old_preset"])

        newPhone.syncNow().getOrThrow()
        // Coast migrates to All numbers with calories on Later and the rest of what it hid kept hidden.
        val numbers = newPhone.settings.settings.awaitState { it.numbers.revealOn == RevealOn.Week }.numbers
        assertEquals(PresetId.Full, numbers.preset)
        assertEquals(RevealOn.Week, numbers.revealOn)
        Metric.entries.forEach { assertEquals(coast.level(it), numbers.level(it), it.name) }
    }

    @Test
    fun aV01PhoneReadingA02Mode_neverSeesMoreNumbers() = integration {
        val user = randomUserId()
        val newPhone = client(user)
        newPhone.syncNow().getOrThrow()
        newPhone.settings.isKnown.awaitState { it }
        newPhone.settings.update { it.withNumbers(it.numbers.pickPreset(PresetId.NoNumbers, Instant.parse("2026-10-25T20:00:00Z"))) }
        newPhone.syncNow().getOrThrow()

        val oldPhone = client(user, wrapSync = ::OldClientWire)
        oldPhone.syncNow().getOrThrow()
        oldPhone.settings.isKnown.awaitState { it }
        val stored = server.query(
            "SELECT settings->'numbers'->>'preset' AS old_preset, settings->'numbers'->'overrides' AS overrides " +
                "FROM user_settings WHERE user_id = ?::uuid",
            user,
        ).single()
        assertEquals("blind", stored["old_preset"], "a 0.1 phone reads Blind: every number hidden")
        assertEquals("{}", stored["overrides"].toString(), "and nothing turned back on")
        val onOldPhone = oldPhone.settings.settings.value.numbers
        Metric.entries.forEach { assertEquals(Presets.NoNumbers.level(it), onOldPhone.level(it), it.name) }
    }

    private companion object {
        /** Keys 0.1 doesn't know, so drops on decode and never sends. */
        val V02_KEYS = setOf(
            "mealTimes", "exerciseRange", "checkIn", "progressPhotos", "targetStyle",
            "restingSource", "restingOwnKcal", "activityInBurned", "everydayActivity", "birthYear", "heightCm", "bodySex",
            "todayCard", "todayMainNumber", "todayMainNumberByStyle", "todayBurnedBreakdown", "todayMealsCount",
            "todayNutrientsLink", "todayWater", "todayWeight", "todayCloseDay",
            "mode", "modeTimerEnded", "microTargets", "todayNutrients", "todayReadings",
        )

        /** What Coast hid that Later on its own would only delay. */
        val COAST_HIDES = listOf(Metric.Remaining, Metric.EnergyOut, Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber)
            .associateWith { Visibility.Hide }
    }
}
