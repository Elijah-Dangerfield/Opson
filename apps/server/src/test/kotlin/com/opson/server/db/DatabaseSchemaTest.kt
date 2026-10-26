package com.opson.server.db

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Schema smoke test: proves Flyway created the table *and* that the Exposed
 * [ProfilesTable] projection matches the real columns (the `selectAll()`
 * references every declared column, so a drifted column name fails here). Add a
 * line per new table.
 */
class DatabaseSchemaTest : DatabaseTest() {

    @Test
    fun migrationsCreateProfilesTable() = runTest {
        val rows = database.transaction {
            ProfilesTable.selectAll().toList()
        }
        assertTrue(rows.isEmpty())
    }

    @Test
    fun catalogAndDiaryTablesMatchTheirExposedProjections() = runTest {
        database.transaction {
            listOf(
                FoodsTable, FoodSearchEventsTable, BarcodeLookupsTable,
                LogEntriesTable, DayRecordsTable, MealFeelingsTable, WeighInsTable,
                CustomFoodsTable, SavedMealsTable, UserSettingsTable,
            ).forEach { table -> table.selectAll().limit(1).toList() }
        }
    }

    @Test
    fun rowLevelSecurityIsOnForEveryNewTable() {
        val tables = listOf(
            "foods", "food_search_events", "barcode_lookups", "log_entries", "day_records",
            "meal_feelings", "weigh_ins", "custom_foods", "saved_meals", "user_settings",
        )
        val withRls = strings(
            "SELECT relname FROM pg_class WHERE relnamespace = 'public'::regnamespace AND relrowsecurity",
        )
        assertTrue(withRls.containsAll(tables), "RLS missing on ${tables - withRls.toSet()}")
    }

    @Test
    fun everySyncedTableHasTheCursorTrigger() {
        val triggers = strings("SELECT tgname FROM pg_trigger WHERE NOT tgisinternal")
        listOf("log_entries", "day_records", "meal_feelings", "weigh_ins", "custom_foods", "saved_meals", "user_settings")
            .forEach { assertTrue("${it}_bump_server_seq" in triggers, "no cursor trigger on $it") }
    }

    @Test
    fun analyticsViewsAreQueryable() {
        ANALYTICS_VIEWS.forEach { view -> strings("SELECT 1::text FROM analytics.$view LIMIT 1") }
    }

    /** The structural half of "Grafana can never show one person's day" (system-design §9). */
    @Test
    fun noAnalyticsViewExposesAUserOrANutrientAmount() {
        val forbidden = setOf(
            "user_id", "kcal", "protein", "carbs", "fat", "fiber", "micros", "grams", "quantity",
            "kg", "water_ml", "note", "name", "settings",
        )
        val columns = strings("SELECT column_name::text FROM information_schema.columns WHERE table_schema = 'analytics'")
        assertTrue(columns.isNotEmpty())
        assertEquals(emptySet(), columns.toSet() intersect forbidden)
        assertEquals(ANALYTICS_VIEWS.toSet(), strings("SELECT table_name::text FROM information_schema.views WHERE table_schema = 'analytics'").toSet())
    }

    @Test
    fun grafanaRoleReadsAnalyticsAndNothingElse() {
        database.blockingTransaction {
            exec("SET LOCAL ROLE grafana_ro")
            ANALYTICS_VIEWS.forEach { exec("SELECT * FROM analytics.$it LIMIT 1") }
        }
        listOf("log_entries", "user_settings", "weigh_ins", "foods", "food_search_events", "profiles").forEach { table ->
            assertFailsWith<Exception>("grafana_ro must not read public.$table") {
                database.blockingTransaction {
                    exec("SET LOCAL ROLE grafana_ro")
                    exec("SELECT * FROM public.$table LIMIT 1")
                }
            }
        }
    }

    private fun exec(sql: String) {
        TransactionManager.current().exec(sql)
    }

    private fun strings(sql: String): List<String> = database.blockingTransaction {
        val out = mutableListOf<String>()
        TransactionManager.current().exec(sql) { rs -> while (rs.next()) out += rs.getString(1) }
        out
    }

    private companion object {
        val ANALYTICS_VIEWS = listOf(
            "daily_active_loggers", "entries_by_method_precision", "meals_per_day_distribution", "days_closed",
            "preset_distribution", "search_daily", "top_zero_result_queries", "barcode_outcomes_daily",
            "weekly_retention_cohorts", "retention_d1_d7_d30",
        )
    }
}
