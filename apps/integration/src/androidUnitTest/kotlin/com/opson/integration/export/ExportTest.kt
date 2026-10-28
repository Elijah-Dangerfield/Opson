package com.opson.integration.export

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.awaitState
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.CheckInMood
import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.diary.DayHunger
import com.opson.libraries.diary.ExerciseDraft
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Export everything.** What a person logs on their phone, synced, comes back in the zip the
 * Support page shares: every kind of row in its own file, local times in the phone's zone, and
 * nobody else's rows. The columns themselves are `ExportArchiveTest` on the server.
 */
class ExportTest : IntegrationTest() {

    @Test
    fun aSeededAccount_getsEveryKindOfRowBack() = integration {
        val user = randomUserId()
        val phone = client(user)
        val day = phone.today()
        val zone = TimeZone.currentSystemDefault()
        // Settings only save once the account's own have been read, as on a phone.
        phone.syncNow().getOrThrow()
        phone.settings.isKnown.awaitState { it }

        phone.log("Porridge", kcal = 320.0, slot = MealSlot.Breakfast)
        phone.diary.setFeeling(day, MealSlot.Breakfast, MealFeeling(hunger = Hunger.Hungry, note = "Before the train"))
        phone.diary.addWater(day, 750)
        phone.diary.setDayNote(day, "A steadier day")
        phone.checkIns.setCheckIn(day, CheckIn(hunger = DayHunger.Steady, moods = setOf(CheckInMood.Calm)))
        phone.weights.addWeighIn(62.5)
        phone.exercise.add(ExerciseDraft(day, LocalDateTime(day, LocalTime(7, 30)).toInstant(zone), "yoga", minutes = 45, kcal = 120))
        phone.customFoods.create(CustomFoodDraft("Gran's flapjack", servingAmount = 1.0, servingUnit = "square", kcal = 210.0))
        phone.savedMeals.create("Packed lunch", MealSlot.Lunch, listOf(LogDraft(name = "Cheese sandwich", precision = Precision.Estimate, kcal = 380.0)))
        phone.settings.update { it.copy(numbers = NumbersSettings(preset = PresetId.NoNumbers)) }
        phone.syncNow().getOrThrow()

        val files = unzip(phone.export.everything().getOrThrow())

        assertTrue(files.getValue("README.txt").contains(zone.id), "the README names the phone's zone")
        val entry = files.getValue("entries.csv").rows().single()
        assertTrue(entry.startsWith("$day,") && ",${zone.id}," in entry && ",breakfast,Porridge," in entry, entry)
        assertEquals("$day,breakfast,hungry,Before the train,,", files.getValue("meal_notes.csv").rows().single())
        assertTrue(files.getValue("day_notes.csv").rows().single().startsWith("$day,A steadier day,"))
        assertEquals("$day,steady,,,calm", files.getValue("check_ins.csv").rows().single())
        assertEquals("$day,750", files.getValue("water.csv").rows().single())
        assertTrue(files.getValue("weigh_ins.csv").rows().single().endsWith(",62.5"))
        assertTrue(files.getValue("exercise.csv").rows().single().startsWith("$day,07:30,${zone.id},"))
        assertEquals("Gran's flapjack,,1,square,210,,,,", files.getValue("custom_foods.csv").rows().single())
        assertEquals("Packed lunch,lunch,Cheese sandwich,,,380,,,,", files.getValue("saved_meals.csv").rows().single())
        assertTrue(files.getValue("daily_summary.csv").rows().any { it.startsWith("$day,1,320,") })
        assertTrue(files.getValue("settings.json").contains("\"no_numbers\""), "the settings as synced")
        assertEquals(listOf("fasts.csv"), files.filterValues { it.rows().isEmpty() }.keys.filter { it.endsWith(".csv") })
    }

    @Test
    fun anExportHoldsOnlyTheCallersRows() = integration {
        val alice = client(randomUserId())
        alice.log("Alice's soup", kcal = 250.0)
        alice.diary.setDayNote(alice.today(), "Alice's note")
        alice.syncNow().getOrThrow()

        val bob = client(randomUserId())
        bob.syncNow().getOrThrow()
        val files = unzip(bob.export.everything().getOrThrow())

        assertTrue(files.filterKeys { it.endsWith(".csv") }.values.all { it.rows().isEmpty() }, "Bob's export is empty")
        assertTrue(files.values.none { "Alice" in it })
    }

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { put(it.name, zip.readBytes().decodeToString()) }
        }
    }

    /** The data rows, without the header. */
    private fun String.rows(): List<String> = split("\r\n").filter { it.isNotEmpty() }.drop(1)
}
