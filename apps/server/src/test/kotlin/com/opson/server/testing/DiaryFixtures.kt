package com.opson.server.testing

import com.opson.server.db.BarcodeLookupsTable
import com.opson.server.db.CustomFoodsTable
import com.opson.server.db.Database
import com.opson.server.db.DayRecordsTable
import com.opson.server.db.ExercisesTable
import com.opson.server.db.FastsTable
import com.opson.server.db.FoodSearchEventsTable
import com.opson.server.db.FoodsTable
import com.opson.server.db.LogEntriesTable
import com.opson.server.db.MealFeelingsTable
import com.opson.server.db.RecipeReportsTable
import com.opson.server.db.RecipesTable
import com.opson.server.db.SavedMealsTable
import com.opson.server.db.UserSettingsTable
import com.opson.server.db.WeighInsTable
import com.opson.server.domain.LogEntry
import com.opson.server.domain.LogMethod
import com.opson.server.domain.MealSlot
import com.opson.server.domain.Nutrients
import com.opson.server.domain.Precision
import com.opson.server.domain.Recipe
import com.opson.server.domain.SyncMutation
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Instant

/** Row builders and table cleanup shared by the Postgres-backed tests. */
object DiaryFixtures {
    val T0: Instant = Instant.parse("2026-10-25T08:00:00Z")
    val DAY: LocalDate = LocalDate.parse("2026-10-25")

    fun entry(
        id: UUID = UUID.randomUUID(),
        name: String = "Banana",
        kcal: Double? = 105.0,
        updatedAt: Instant = T0,
        deleted: Boolean = false,
        foodId: UUID? = null,
        day: LocalDate = DAY,
    ) = LogEntry(
        id = id, day = day, slot = MealSlot.Breakfast, loggedAt = T0, foodId = foodId, customFoodId = null,
        name = name, amountText = "1 medium", quantity = 1.0, unit = null, grams = 118.0,
        precision = Precision.Estimate, nutrients = Nutrients(kcal = kcal, protein = 1.3, carbs = 27.0, fat = 0.4, fiber = 3.1),
        micros = mapOf("potassium_mg" to 422.0), method = LogMethod.Search, clientUpdatedAt = updatedAt, deleted = deleted,
    )

    fun mutation(row: com.opson.server.domain.DiaryRow, outboxId: String = UUID.randomUUID().toString()) =
        SyncMutation(outboxId, row)

    /**
     * A recipe for six: two ingredients, the first naming catalog food [foodId] by weight when
     * given. The numbers are the creator's, as the client snapshots them.
     */
    fun recipe(
        id: UUID = UUID.randomUUID(),
        name: String = "Weeknight chili",
        servings: Double = 6.0,
        foodId: UUID? = null,
        updatedAt: Instant = T0,
        deleted: Boolean = false,
        notes: String? = "Simmer for an hour.",
    ): Recipe {
        val items = buildJsonArray {
            add(
                buildJsonObject {
                    put("name", "Black beans")
                    put("amountText", "2 cans")
                    put("grams", 800.0)
                    put("kcal", 1000.0)
                    put("protein", 60.0)
                    put("carbs", 180.0)
                    put("fat", 4.0)
                    if (foodId != null) put("foodId", foodId.toString())
                    put("customFoodId", "8f2c3d40-0000-4000-8000-000000000001")
                },
            )
            add(
                buildJsonObject {
                    put("name", "Ground beef")
                    put("amountText", "1 lb")
                    put("grams", 454.0)
                    put("kcal", 1150.0)
                    put("protein", 78.0)
                    put("fat", 90.0)
                },
            )
        }
        return Recipe(
            id = id,
            name = name,
            servings = servings,
            itemsJson = items.toString(),
            itemCount = items.size,
            notes = notes,
            clientUpdatedAt = updatedAt,
            deleted = deleted,
        )
    }

    fun insertFood(database: Database, id: UUID = UUID.randomUUID(), name: String = "Bananas, raw"): UUID {
        database.blockingTransaction {
            FoodsTable.insert {
                it[FoodsTable.id] = id
                it[sourceKind] = "usda_sr"
                it[sourceId] = id.toString()
                it[FoodsTable.name] = name
                it[micros] = "{}"
                it[portions] = "[]"
            }
        }
        return id
    }

    fun cleanAll(database: Database) {
        database.blockingTransaction {
            listOf(
                LogEntriesTable, DayRecordsTable, MealFeelingsTable, WeighInsTable, CustomFoodsTable,
                SavedMealsTable, UserSettingsTable, FastsTable, ExercisesTable, RecipeReportsTable, RecipesTable,
                FoodSearchEventsTable, BarcodeLookupsTable, FoodsTable,
            ).forEach { it.deleteAll() }
        }
    }
}
