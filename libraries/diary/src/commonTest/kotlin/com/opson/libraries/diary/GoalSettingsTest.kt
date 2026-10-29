package com.opson.libraries.diary

import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.WeightGoal
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The goal's settings: the weight goal, the date of birth (with the year older builds read), the
 * pregnant or breastfeeding stage and the commitment, each a top-level key that round-trips, and
 * an unreadable value falling back to nothing planned.
 */
class GoalSettingsTest {

    private val goal = WeightGoal(
        direction = GoalDirection.Lose,
        targetKg = 75.0,
        kgPerWeek = 0.4,
        startKg = 80.0,
        startDate = LocalDate(2026, 10, 29),
        targetsKcal = 1950,
    )

    @Test
    fun defaults_areNoGoal_noStage_andNoCommitment() {
        val settings = SettingsJson.decodeFromString(Settings.serializer(), "{}")
        assertEquals(WeightGoal.None, settings.weightGoal)
        assertEquals(BodyStage.None, settings.bodyStage)
        assertNull(settings.birthDate)
        assertNull(settings.planCommitment)
    }

    @Test
    fun everything_roundTrips_asTopLevelKeys() {
        val settings = Settings(
            weightGoal = goal,
            bodyStage = BodyStage.Breastfeeding,
            planCommitment = PlanCommitment(LocalDate(2026, 10, 29), committed = true),
        ).withBirthDate(LocalDate(1998, 5, 11))
        val json = SettingsJson.encodeToJsonElement(Settings.serializer(), settings).jsonObject

        assertEquals("lose", json.getValue("weightGoal").jsonObject.getValue("direction").jsonPrimitive.content)
        assertEquals("1998-05-11", json.getValue("birthDate").jsonPrimitive.content)
        assertEquals(1998, json.getValue("birthYear").jsonPrimitive.content.toInt(), "older builds still read the year")
        assertEquals("breastfeeding", json.getValue("bodyStage").jsonPrimitive.content)
        assertEquals(settings, SettingsJson.decodeFromJsonElement(Settings.serializer(), json))
    }

    @Test
    fun noGoal_andNoStage_areWritten_soClearingReachesTheServer() {
        val json = SettingsJson.encodeToJsonElement(Settings.serializer(), Settings()).jsonObject
        assertEquals("none", json.getValue("weightGoal").jsonObject.getValue("direction").jsonPrimitive.content)
        assertEquals("none", json.getValue("bodyStage").jsonPrimitive.content)
    }

    @Test
    fun anUnknownDirectionOrStage_readsAsNothingPlanned() {
        val json = """{"weightGoal":{"direction":"bulk_fast","kgPerWeek":2.0},"bodyStage":"twins"}"""
        val settings = SettingsJson.decodeFromString(Settings.serializer(), json)
        assertEquals(WeightGoal.None, settings.weightGoal)
        assertEquals(BodyStage.None, settings.bodyStage)
        val badPace = SettingsJson.decodeFromString(Settings.serializer(), """{"weightGoal":{"direction":"gain","kgPerWeek":-1.0}}""")
        assertEquals(0.0, badPace.weightGoal.kgPerWeek)
    }

    @Test
    fun theDateOfBirth_isOnlyKnown_whileTheYearAgrees() {
        val born = Settings().withBirthDate(LocalDate(1990, 3, 4))
        assertEquals(LocalDate(1990, 3, 4), born.knownBirthDate)
        // An older build changed the year on Profile and knew nothing of the date.
        assertNull(born.copy(birthYear = 1991).knownBirthDate)
        assertFalse(born.copy(birthYear = 1991).isBirthday(LocalDate(2026, 3, 4)))
    }

    @Test
    fun theirBirthday_isTheDay_andTheTwentyEighthForALeapDayInAnOrdinaryYear() {
        val born = Settings().withBirthDate(LocalDate(1990, 3, 4))
        assertTrue(born.isBirthday(LocalDate(2026, 3, 4)))
        assertFalse(born.isBirthday(LocalDate(2026, 3, 5)))
        assertFalse(Settings().isBirthday(LocalDate(2026, 3, 4)))

        val leap = Settings().withBirthDate(LocalDate(2000, 2, 29))
        assertTrue(leap.isBirthday(LocalDate(2027, 2, 28)))
        assertFalse(leap.isBirthday(LocalDate(2028, 2, 28)), "2028 has its own 29th")
        assertTrue(leap.isBirthday(LocalDate(2028, 2, 29)))
    }

    @Test
    fun aMergeOfOnlyTheOldKeys_keepsTheNewOnes() {
        // The server merges one level deep: an older build's save without these keys leaves them.
        val stored = SettingsJson.encodeToJsonElement(Settings.serializer(), Settings(weightGoal = goal)).jsonObject
        val older = JsonObject(mapOf("birthYear" to JsonPrimitive(1985)))
        val merged = SettingsJson.decodeFromJsonElement(Settings.serializer(), JsonObject(stored + older))
        assertEquals(goal, merged.weightGoal)
        assertEquals(1985, merged.birthYear)
    }
}
