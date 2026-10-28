package com.opson.server.routes

import com.opson.server.domain.CustomFood
import com.opson.server.domain.DayRecord
import com.opson.server.domain.DiaryRow
import com.opson.server.domain.Exercise
import com.opson.server.domain.Fast
import com.opson.server.domain.LogEntry
import com.opson.server.domain.LogMethod
import com.opson.server.domain.MealFeeling
import com.opson.server.domain.MealSlot
import com.opson.server.domain.Nutrients
import com.opson.server.domain.Precision
import com.opson.server.domain.SavedMeal
import com.opson.server.domain.UserSettings
import com.opson.server.domain.WeighIn
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Each file of `opson-export.zip` from rows of every kind, written the way the route writes them. */
class ExportArchiveTest {

    private val day = LocalDate.parse("2026-10-25")
    private val t0 = Instant.parse("2026-10-25T07:05:00Z")
    private val zone = ZoneId.of("America/Sao_Paulo")

    private fun entry(name: String, slot: MealSlot, at: Instant, nutrients: Nutrients, micros: Map<String, Double>? = null) = LogEntry(
        id = UUID.randomUUID(), day = day, slot = slot, loggedAt = at, foodId = null, customFoodId = null, name = name,
        amountText = "1 bowl", quantity = 1.0, unit = "bowl", grams = 240.0, precision = Precision.Estimate, nutrients = nutrients,
        micros = micros, method = LogMethod.Search, clientUpdatedAt = t0, deleted = false,
    )

    /** In the order [com.opson.server.domain.ExportRepository] promises. */
    private val everything: List<DiaryRow> = listOf(
        entry("Porridge", MealSlot.Breakfast, t0, Nutrients(kcal = 320.0, protein = 11.0, carbs = 54.0, fat = 6.5, fiber = 8.0), mapOf("iron_mg" to 2.1)),
        entry("Apple", MealSlot.Snack, Instant.parse("2026-10-25T14:40:00Z"), Nutrients(kcal = 95.0, fiber = 4.4)),
        entry("Soup from the cafe", MealSlot.Lunch, Instant.parse("2026-10-25T12:15:00Z"), Nutrients()),
        WeighIn(UUID.randomUUID(), Instant.parse("2026-10-25T06:50:00Z"), 64.2, t0, false),
        WeighIn(UUID.randomUUID(), Instant.parse("2026-10-25T21:00:00Z"), 64.6, t0, false),
        DayRecord(
            day, waterMl = 1750, closedAt = Instant.parse("2026-10-25T21:30:00Z"), note = "Busy, but I ate lunch",
            ateNoDetails = mapOf(MealSlot.Dinner to true), mealGroups = null, clientUpdatedAt = t0, deleted = false,
            feeling = "busy", checkIn = """{"hunger":"steady","energy":"ok","sleep":"good","moods":["calm","social"]}""",
        ),
        DayRecord(LocalDate.parse("2026-10-26"), 500, null, null, emptyMap(), null, t0, false, checkIn = "not json"),
        MealFeeling(day, MealSlot.Breakfast, "hungry", null, emptyList(), "Before the train", t0, false),
        Exercise(UUID.randomUUID(), day, Instant.parse("2026-10-25T17:00:00Z"), "running_10", null, 30, 291, false, true, t0, false),
        Fast(LocalDate.parse("2026-10-24"), Instant.parse("2026-10-24T19:00:00Z"), Instant.parse("2026-10-25T07:00:00Z"), 12, t0, false),
        Fast(day, Instant.parse("2026-10-25T20:00:00Z"), null, null, t0, false),
        CustomFood(UUID.randomUUID(), "Gran's flapjack", null, 1.0, "square", Nutrients(kcal = 210.0), t0, false),
        SavedMeal(
            UUID.randomUUID(), "Usual breakfast", MealSlot.Breakfast,
            """[{"name":"Porridge","precision":"estimate","amountText":"1 bowl","grams":240,"kcal":320},{"name":"Coffee","precision":"named"}]""",
            t0, false,
        ),
        SavedMeal(UUID.randomUUID(), "Broken", null, "{", t0, false),
        UserSettings("""{"numbers":{"preset":"macros_only"},"weightUnit":"kg"}""", null, t0, false),
    )

    private fun archive(rows: List<DiaryRow>, zone: ZoneId = this.zone): Map<String, String> {
        val out = ByteArrayOutputStream()
        val writer = ExportArchiveWriter(out, zone, Instant.parse("2026-10-27T13:00:00Z"))
        rows.forEach(writer::write)
        writer.finish()
        return unzip(out.toByteArray())
    }

    @Test
    fun everyFileIsThere_withItsHeader() {
        val files = archive(everything)
        assertEquals(setOf(EXPORT_README, EXPORT_SETTINGS) + ExportFile.entries.map { it.fileName }, files.keys)
        ExportFile.entries.forEach { file ->
            assertEquals(file.header.joinToString(","), files.getValue(file.fileName).csvLines().first(), file.fileName)
        }
    }

    @Test
    fun anEmptyAccount_stillGetsEveryFile() {
        val files = archive(emptyList())
        ExportFile.entries.forEach { assertEquals(1, files.getValue(it.fileName).csvLines().size, it.fileName) }
        assertEquals("{}", files.getValue(EXPORT_SETTINGS).trim())
    }

    @Test
    fun entriesCarryTheLocalMinuteAndZone_beside_UTC() {
        val rows = archive(everything).getValue("entries.csv").csvLines()
        assertEquals(
            "2026-10-25,04:05,America/Sao_Paulo,2026-10-25T07:05:00Z,breakfast,Porridge,1 bowl,1,bowl,240,320,11,54,6.5,8,\"{\"\"iron_mg\"\":2.1}\",estimate,search",
            rows[1],
        )
        assertEquals("2026-10-25,11:40,America/Sao_Paulo,2026-10-25T14:40:00Z,snack,Apple,1 bowl,1,bowl,240,95,,,,4.4,,estimate,search", rows[2])
    }

    @Test
    fun notesHungerAndCheckIns() {
        val files = archive(everything)
        assertEquals(
            listOf("2026-10-25,\"Busy, but I ate lunch\",busy,2026-10-25T21:30:00Z,false,,dinner"),
            files.getValue("day_notes.csv").csvLines().drop(1),
            "the second day has only water and a check-in, so it has no notes row",
        )
        assertEquals(listOf("2026-10-25,breakfast,hungry,Before the train,,"), files.getValue("meal_notes.csv").csvLines().drop(1))
        assertEquals(
            listOf("2026-10-25,steady,ok,good,calm;social", "2026-10-26,,,,"),
            files.getValue("check_ins.csv").csvLines().drop(1),
        )
        assertEquals(listOf("2026-10-25,1750", "2026-10-26,500"), files.getValue("water.csv").csvLines().drop(1))
    }

    @Test
    fun weighInsExerciseAndFasts() {
        val files = archive(everything)
        assertEquals(
            listOf("2026-10-25,03:50,America/Sao_Paulo,2026-10-25T06:50:00Z,64.2", "2026-10-25,18:00,America/Sao_Paulo,2026-10-25T21:00:00Z,64.6"),
            files.getValue("weigh_ins.csv").csvLines().drop(1),
        )
        assertEquals(
            listOf("2026-10-25,14:00,America/Sao_Paulo,2026-10-25T17:00:00Z,running_10,,30,291,false,true"),
            files.getValue("exercise.csv").csvLines().drop(1),
        )
        assertEquals(
            listOf(
                "2026-10-24,2026-10-24 16:00,2026-10-25 04:00,America/Sao_Paulo,2026-10-24T19:00:00Z,2026-10-25T07:00:00Z,12,12",
                "2026-10-25,2026-10-25 17:00,,America/Sao_Paulo,2026-10-25T20:00:00Z,,,",
            ),
            files.getValue("fasts.csv").csvLines().drop(1),
        )
    }

    @Test
    fun customFoodsAndSavedMeals_oneRowPerFood() {
        val files = archive(everything)
        assertEquals(listOf("Gran's flapjack,,1,square,210,,,,"), files.getValue("custom_foods.csv").csvLines().drop(1))
        assertEquals(
            listOf(
                "Usual breakfast,breakfast,Porridge,1 bowl,240,320,,,,",
                "Usual breakfast,breakfast,Coffee,,,,,,,",
                "Broken,,,,,,,,,",
            ),
            files.getValue("saved_meals.csv").csvLines().drop(1),
        )
    }

    @Test
    fun theDailySummary_addsWhatIsKnown_andSaysWhatIsNot() {
        val rows = archive(everything).getValue("daily_summary.csv").csvLines().drop(1)
        assertEquals(
            listOf(
                "2026-10-25,3,415,11,54,6.5,12.4,1,1750,30,291,64.6,true,false",
                "2026-10-26,0,,,,,,0,500,,,,false,false",
            ),
            rows,
        )
    }

    @Test
    fun settingsAreReadableJson_andTheReadmeNamesTheZone() {
        val files = archive(everything)
        val settings = files.getValue(EXPORT_SETTINGS)
        assertTrue(settings.contains("\n"), "pretty printed")
        assertTrue(settings.contains("\"macros_only\""))
        val readme = files.getValue(EXPORT_README)
        assertTrue(readme.contains("2026-10-27 10:00") && readme.contains("America/Sao_Paulo"), readme)
        assertFalse(readme.contains("{"), "every placeholder is filled")
        ExportFile.entries.forEach { assertTrue(readme.contains(it.fileName), "the README describes ${it.fileName}") }
        assertFalse(readme.contains('—'), "no em dashes in copy")
    }

    @Test
    fun rowsOfOneKindSplitApart_areAProgrammingError() {
        val out = ByteArrayOutputStream()
        val writer = ExportArchiveWriter(out, zone, t0)
        writer.write(everything[0])
        writer.write(everything[3])
        assertFailsWith<IllegalStateException> { writer.write(everything[1]) }
    }
}

internal fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
    ZipInputStream(bytes.inputStream()).use { zip ->
        generateSequence { zip.nextEntry }.forEach { entry -> put(entry.name, zip.readBytes().decodeToString()) }
    }
}

internal fun String.csvLines(): List<String> = split("\r\n").filter { it.isNotEmpty() }
