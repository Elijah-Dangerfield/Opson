package com.opson.server.routes

import com.opson.server.db.toJavaInstant
import com.opson.server.domain.CustomFood
import com.opson.server.domain.DayRecord
import com.opson.server.domain.DiaryRow
import com.opson.server.domain.Exercise
import com.opson.server.domain.Fast
import com.opson.server.domain.LogEntry
import com.opson.server.domain.MealFeeling
import com.opson.server.domain.MealSlot
import com.opson.server.domain.Nutrients
import com.opson.server.domain.PublicRecipes
import com.opson.server.domain.Recipe
import com.opson.server.domain.SavedMeal
import com.opson.server.domain.UserSettings
import com.opson.server.domain.WeighIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.TreeMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToLong
import kotlin.time.Instant

/** One CSV in `opson-export.zip`. The README (`resources/export/README.txt`) describes each. */
internal enum class ExportFile(val fileName: String, val header: List<String>) {
    Entries(
        "entries.csv",
        listOf(
            "day", "time", "time_zone", "logged_at_utc", "meal", "name", "amount", "quantity", "unit", "grams",
            "kcal", "protein_g", "carbs_g", "fat_g", "fiber_g", "micros", "precision", "method",
        ),
    ),
    WeighIns("weigh_ins.csv", listOf("day", "time", "time_zone", "measured_at_utc", "kg")),
    DayNotes(
        "day_notes.csv",
        listOf("day", "note", "feeling", "closed_at_utc", "untracked", "meal_groups", "ate_no_details"),
    ),
    MealNotes("meal_notes.csv", listOf("day", "meal", "hunger", "note", "fullness", "moods")),
    Exercise(
        "exercise.csv",
        listOf(
            "day", "time", "time_zone", "started_at_utc", "activity", "name", "minutes", "kcal", "kcal_typed",
            "not_on_watch",
        ),
    ),
    Fasts(
        "fasts.csv",
        listOf("day", "started", "ended", "time_zone", "started_at_utc", "ended_at_utc", "goal_hours", "hours"),
    ),
    CustomFoods(
        "custom_foods.csv",
        listOf("name", "brand", "serving_amount", "serving_unit", "kcal", "protein_g", "carbs_g", "fat_g", "fiber_g"),
    ),
    SavedMeals(
        "saved_meals.csv",
        listOf("meal", "usual_meal", "food", "amount", "grams", "kcal", "protein_g", "carbs_g", "fat_g", "fiber_g"),
    ),
    Recipes(
        "recipes.csv",
        listOf(
            "recipe", "servings", "notes", "shared_code", "ingredient", "amount", "grams", "kcal", "protein_g", "carbs_g",
            "fat_g", "fiber_g",
        ),
    ),
    CheckIns("check_ins.csv", listOf("day", "hunger", "energy", "sleep", "moods")),
    Water("water.csv", listOf("day", "water_ml")),
    DailySummary(
        "daily_summary.csv",
        listOf(
            "day", "foods_logged", "kcal", "protein_g", "carbs_g", "fat_g", "fiber_g", "foods_missing_kcal",
            "water_ml", "exercise_minutes", "exercise_kcal", "weight_kg", "closed", "untracked",
        ),
    ),
}

internal const val EXPORT_README = "README.txt"
internal const val EXPORT_SETTINGS = "settings.json"

/**
 * Writes `opson-export.zip` to [output] as rows arrive from the export repository, one file per
 * kind of row. Rows of a kind arrive together, so each file is written start to finish before
 * the next opens. What a day record splits into water and check-ins, and the daily summary, are
 * one small row per day, held until [finish] writes them. Times are shown in [zone] beside their
 * UTC instant, because a log entry stores only the instant.
 */
internal class ExportArchiveWriter(
    output: OutputStream,
    private val zone: ZoneId,
    exportedAt: Instant,
) {
    private val zip = ZipOutputStream(output)

    // Never closed on its own: closing it would close the zip under it. Flushed at each file's end.
    private val writer = OutputStreamWriter(zip, Charsets.UTF_8)
    private var open: ExportFile? = null
    private val started = mutableSetOf<ExportFile>()
    private var settingsWritten = false
    private val summaries = TreeMap<LocalDate, DaySummary>()
    private val water = mutableListOf<List<String?>>()
    private val checkIns = mutableListOf<List<String?>>()

    init {
        val readme = readmeTemplate()
            .replace("{exported_at}", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(exportedAt.zoned()))
            .replace("{time_zone}", zone.id)
        zip.putNextEntry(ZipEntry(EXPORT_README))
        writer.write(readme)
        writer.flush()
        zip.closeEntry()
    }

    fun write(row: DiaryRow) {
        when (row) {
            is LogEntry -> entry(row)
            is WeighIn -> weighIn(row)
            is DayRecord -> day(row)
            is MealFeeling -> row(ExportFile.MealNotes, mealNoteCells(row))
            is Exercise -> exercise(row)
            is Fast -> row(ExportFile.Fasts, fastCells(row))
            is CustomFood -> row(ExportFile.CustomFoods, customFoodCells(row))
            is SavedMeal -> savedMealRows(row).forEach { row(ExportFile.SavedMeals, it) }
            is Recipe -> recipeRows(row).forEach { row(ExportFile.Recipes, it) }
            is UserSettings -> settings(row.settingsJson)
        }
    }

    /** Every file the export promises, header only when there was nothing for it, then the end of the zip. */
    fun finish() {
        water.forEach { row(ExportFile.Water, it) }
        checkIns.forEach { row(ExportFile.CheckIns, it) }
        summaries.forEach { (day, summary) -> row(ExportFile.DailySummary, summary.cells(day)) }
        ExportFile.entries.filter { it !in started }.forEach(::start)
        closeFile()
        if (!settingsWritten) settings("{}")
        zip.finish()
        zip.flush()
    }

    private fun entry(entry: LogEntry) {
        val at = entry.loggedAt.zoned()
        row(
            ExportFile.Entries,
            listOf(
                entry.day.toString(), at.time(), zone.id, entry.loggedAt.toString(), entry.slot.wire, entry.name,
                entry.amountText, entry.quantity.cell(), entry.unit, entry.grams.cell(),
            ) + entry.nutrients.cells() + listOf(
                entry.micros?.takeIf { it.isNotEmpty() }?.let { micros -> JsonObject(micros.mapValues { JsonPrimitive(it.value) }).toString() },
                entry.precision.wire,
                entry.method.wire,
            ),
        )
        summaries.getOrPut(entry.day) { DaySummary() }.add(entry.nutrients)
    }

    private fun weighIn(weighIn: WeighIn) {
        val at = weighIn.measuredAt.zoned()
        row(ExportFile.WeighIns, listOf(at.toLocalDate().toString(), at.time(), zone.id, weighIn.measuredAt.toString(), weighIn.kg.cell()))
        // Oldest first, so the last one that day is the one kept.
        summaries.getOrPut(at.toLocalDate()) { DaySummary() }.weightKg = weighIn.kg
    }

    private fun day(record: DayRecord) {
        val day = record.day.toString()
        val ateNoDetails = MealSlot.entries.filter { record.ateNoDetails[it] == true }
        if (!record.note.isNullOrBlank() || record.feeling != null || record.closedAt != null || record.untracked ||
            record.mealGroups != null || ateNoDetails.isNotEmpty()
        ) {
            row(
                ExportFile.DayNotes,
                listOf(
                    day, record.note, record.feeling, record.closedAt?.toString(), record.untracked.toString(),
                    record.mealGroups?.toString(), ateNoDetails.joinToString(";") { it.wire }.ifEmpty { null },
                ),
            )
        }
        record.waterMl?.let { water += listOf(day, it.toString()) }
        record.checkIn?.let { checkIns += checkInCells(day, it) }
        val summary = summaries.getOrPut(record.day) { DaySummary() }
        summary.waterMl = record.waterMl
        summary.closed = record.closedAt != null
        summary.untracked = record.untracked
    }

    private fun exercise(exercise: Exercise) {
        val at = exercise.startedAt.zoned()
        row(
            ExportFile.Exercise,
            listOf(
                exercise.day.toString(), at.time(), zone.id, exercise.startedAt.toString(), exercise.activity, exercise.name,
                exercise.minutes.toString(), exercise.kcal.toString(), exercise.kcalTyped.toString(), exercise.notOnWatch.toString(),
            ),
        )
        val summary = summaries.getOrPut(exercise.day) { DaySummary() }
        summary.exerciseMinutes += exercise.minutes
        summary.exerciseKcal += exercise.kcal
    }

    private fun fastCells(fast: Fast): List<String?> {
        val hours = fast.endedAt?.let { end -> ((end - fast.startedAt).inWholeMinutes / 6.0).roundToLong() / 10.0 }
        return listOf(
            fast.day.toString(),
            fast.startedAt.zoned().dateTime(),
            fast.endedAt?.zoned()?.dateTime(),
            zone.id,
            fast.startedAt.toString(),
            fast.endedAt?.toString(),
            fast.goalHours?.toString(),
            hours.cell(),
        )
    }

    private fun settings(json: String) {
        closeFile()
        zip.putNextEntry(ZipEntry(EXPORT_SETTINGS))
        // Pretty, so the file reads in a text editor. Stored settings are always an object; anything
        // else is written as it was rather than lost.
        writer.write(orNullIfMalformed { PRETTY.encodeToString(JsonElement.serializer(), PRETTY.parseToJsonElement(json)) } ?: json)
        writer.write("\n")
        writer.flush()
        zip.closeEntry()
        settingsWritten = true
    }

    private fun row(file: ExportFile, cells: List<String?>) {
        if (open != file) start(file)
        check(cells.size <= file.header.size) { "${file.fileName} has ${file.header.size} columns, not ${cells.size}" }
        // Padded, so a row that stops early still has every column a spreadsheet expects.
        writer.writeRow(cells + List(file.header.size - cells.size) { null })
    }

    private fun start(file: ExportFile) {
        check(file !in started) { "${file.fileName} was already written; rows of one kind must arrive together" }
        closeFile()
        zip.putNextEntry(ZipEntry(file.fileName))
        writer.writeRow(file.header)
        open = file
        started += file
    }

    private fun closeFile() {
        if (open == null) return
        writer.flush()
        zip.closeEntry()
        open = null
    }

    private fun Instant.zoned(): ZonedDateTime = toJavaInstant().atZone(zone)

    private fun ZonedDateTime.time(): String = DateTimeFormatter.ofPattern("HH:mm").format(this)

    private fun ZonedDateTime.dateTime(): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(this)

    /** A day's line in `daily_summary.csv`. A nutrient total is the sum of the foods that had it. */
    private class DaySummary {
        var foods = 0
        var missingKcal = 0
        val totals = DoubleArray(NUTRIENTS)
        val known = BooleanArray(NUTRIENTS)
        var waterMl: Int? = null
        var exerciseMinutes = 0
        var exerciseKcal = 0
        var weightKg: Double? = null
        var closed = false
        var untracked = false

        fun add(nutrients: Nutrients) {
            foods++
            if (nutrients.kcal == null) missingKcal++
            nutrients.all().forEachIndexed { i, value ->
                if (value != null) {
                    totals[i] += value
                    known[i] = true
                }
            }
        }

        fun cells(day: LocalDate): List<String?> = listOf(day.toString(), foods.toString()) +
            totals.indices.map { i -> if (known[i]) totals[i].rounded().cell() else null } +
            listOf(
                missingKcal.toString(),
                waterMl?.toString(),
                exerciseMinutes.takeIf { it > 0 }?.toString(),
                exerciseKcal.takeIf { exerciseMinutes > 0 }?.toString(),
                weightKg.cell(),
                closed.toString(),
                untracked.toString(),
            )

        private fun Double.rounded(): Double = (this * 10).roundToLong() / 10.0

        private companion object {
            const val NUTRIENTS = 5
        }
    }

    private companion object {
        val PRETTY = Json { prettyPrint = true }

        fun readmeTemplate(): String =
            requireNotNull(ExportArchiveWriter::class.java.getResource("/export/README.txt")) { "export/README.txt is missing" }
                .readText()
    }
}

private fun mealNoteCells(feeling: MealFeeling): List<String?> = listOf(
    feeling.day.toString(), feeling.slot.wire, feeling.hunger, feeling.note, feeling.fullness,
    feeling.moods.joinToString(";").ifEmpty { null },
)

private fun customFoodCells(food: CustomFood): List<String?> =
    listOf(food.name, food.brand, food.servingAmount.cell(), food.servingUnit) + food.nutrients.cells()

/**
 * One row per food in the meal. The items are the client's snapshot JSON (`LogDraft`), opaque to
 * the server, so a field that is missing is a blank cell and a snapshot that does not parse still
 * leaves the meal's own row.
 */
private fun savedMealRows(meal: SavedMeal): List<List<String?>> {
    val items = orNullIfMalformed { Json.parseToJsonElement(meal.itemsJson).jsonArray }.orEmpty()
    val rows = items.mapNotNull { item ->
        val fields = orNullIfMalformed { item.jsonObject } ?: return@mapNotNull null
        fun text(key: String) = (fields[key] as? JsonPrimitive)?.contentOrNull
        fun number(key: String) = (fields[key] as? JsonPrimitive)?.doubleOrNull
        listOf(meal.name, meal.usualSlot?.wire, text("name"), text("amountText"), number("grams").cell()) +
            listOf("kcal", "protein", "carbs", "fat", "fiber").map { number(it).cell() }
    }
    return rows.ifEmpty { listOf(listOf(meal.name, meal.usualSlot?.wire)) }
}

/** One row per ingredient, the recipe's own cells on each; a recipe with none still gets its row. */
private fun recipeRows(recipe: Recipe): List<List<String?>> {
    val head = listOf(recipe.name, recipe.servings.cell(), recipe.notes, recipe.sharing?.code)
    val rows = PublicRecipes.items(recipe.itemsJson).map { item ->
        head + listOf(item.name, item.amountText, item.grams.cell()) +
            listOf(item.kcal, item.protein, item.carbs, item.fat, item.fiber).map { it.cell() }
    }
    return rows.ifEmpty { listOf(head) }
}

/** The check-in is the client's JSON (`CheckInWire`); one that does not parse leaves the day with blank cells, not a guess. */
private fun checkInCells(day: String, json: String): List<String?> {
    val fields = orNullIfMalformed { Json.parseToJsonElement(json).jsonObject } ?: return listOf(day)
    fun text(key: String) = (fields[key] as? JsonPrimitive)?.contentOrNull
    val moods = (fields["moods"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    return listOf(day, text("hunger"), text("energy"), text("sleep"), moods.joinToString(";").ifEmpty { null })
}

/**
 * For reading the client's opaque JSON only: a malformed value is a blank, never a failed export.
 * kotlinx's `SerializationException` and the `jsonObject`/`jsonArray` casts both throw this.
 */
@Suppress("SwallowedException")
private inline fun <T> orNullIfMalformed(block: () -> T): T? = try {
    block()
} catch (e: IllegalArgumentException) {
    null
}
