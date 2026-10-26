package com.opson.server.testing

import com.opson.server.db.BarcodeLookupsTable
import com.opson.server.db.CustomFoodsTable
import com.opson.server.db.Database
import com.opson.server.db.DayRecordsTable
import com.opson.server.db.FastsTable
import com.opson.server.db.FoodSearchEventsTable
import com.opson.server.db.FoodsTable
import com.opson.server.db.LogEntriesTable
import com.opson.server.db.MealFeelingsTable
import com.opson.server.db.SavedMealsTable
import com.opson.server.db.UserSettingsTable
import com.opson.server.db.WeighInsTable
import com.opson.server.domain.LogEntry
import com.opson.server.domain.LogMethod
import com.opson.server.domain.MealSlot
import com.opson.server.domain.Nutrients
import com.opson.server.domain.Precision
import com.opson.server.domain.SyncMutation
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
                SavedMealsTable, UserSettingsTable, FastsTable, FoodSearchEventsTable, BarcodeLookupsTable, FoodsTable,
            ).forEach { it.deleteAll() }
        }
    }
}
