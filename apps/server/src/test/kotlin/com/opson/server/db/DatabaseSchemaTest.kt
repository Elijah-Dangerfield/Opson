package com.opson.server.db

import com.opson.server.domain.CatalogSource
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
                FoodsTable, OffFoodsTable, FoodSearchEventsTable, BarcodeLookupsTable, BarcodeMissesTable,
                LogEntriesTable, DayRecordsTable, MealFeelingsTable, WeighInsTable,
                CustomFoodsTable, SavedMealsTable, UserSettingsTable,
            ).forEach { table -> table.selectAll().limit(1).toList() }
        }
    }

    @Test
    fun rowLevelSecurityIsOnForEveryNewTable() {
        val tables = listOf(
            "foods", "off_foods", "food_search_events", "barcode_lookups", "barcode_misses", "log_entries",
            "day_records", "meal_feelings", "weigh_ins", "custom_foods", "saved_meals", "user_settings",
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
    fun foodTiersFollowTheSourceForEverySourceTheCheckAllows() {
        assertTrue("foods_set_tier" in strings("SELECT tgname FROM pg_trigger WHERE NOT tgisinternal"))
        // The Kotlin enum and the SQL function must agree, or a cached food
        // would be served with one tier and searched with another.
        val sources = CatalogSource.entries.joinToString(",") { "'${it.wire}'" }
        val tiers = strings("SELECT s || '=' || public.food_source_tier(s) FROM unnest(ARRAY[$sources]) AS s")
        assertEquals(CatalogSource.entries.map { "${it.wire}=${it.tier.wire}" }, tiers)
    }

    /**
     * V12 was applied to opson-dev by hand before Flyway saw it, so running
     * it over an existing table must change nothing. The monthly reload's
     * swap renames indexes by their `off_foods_` prefix, so every index has
     * to carry it.
     */
    @Test
    fun offFoodsMigrationIsIdempotentAndItsIndexesCarryThePrefix() {
        val indexes = "SELECT indexname::text FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'off_foods' ORDER BY 1"
        val before = strings(indexes)
        assertEquals(
            listOf(
                "off_foods_barcode_key", "off_foods_countries_idx", "off_foods_name_trgm_idx",
                "off_foods_pkey", "off_foods_search_idx",
            ),
            before,
        )
        val migration = checkNotNull(javaClass.getResource("/db/migration/V12__off_foods.sql")).readText()
        database.blockingTransaction { exec(migration) }
        assertEquals(before, strings(indexes))
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
            "kg", "water_ml", "note", "name", "settings", "feeling",
        )
        val columns = strings("SELECT column_name::text FROM information_schema.columns WHERE table_schema = 'analytics'")
        assertTrue(columns.isNotEmpty())
        assertEquals(emptySet(), columns.toSet() intersect forbidden)
        assertEquals(ANALYTICS_VIEWS.toSet(), strings("SELECT table_name::text FROM information_schema.views WHERE table_schema = 'analytics'").toSet())
    }

    /** V11: a day marked untracked is a gap, so a coffee logged on a holiday is not a one-meal day. */
    @Test
    fun untrackedDaysLeaveMealsPerDay() {
        val tracked = seedAuthUser().value
        val holiday = seedAuthUser().value
        val day = "2026-10-25"
        database.blockingTransaction {
            listOf(tracked, holiday).forEach { user ->
                exec(
                    "INSERT INTO log_entries (id, user_id, day, slot, logged_at, name, precision, method, client_updated_at) " +
                        "VALUES (gen_random_uuid(), '$user', '$day', 'breakfast', now(), 'Coffee', 'estimate', 'search', now())",
                )
            }
            exec("INSERT INTO day_records (user_id, day, untracked, client_updated_at) VALUES ('$holiday', '$day', TRUE, now())")
        }
        try {
            val rows = strings("SELECT meals_per_day || ':' || person_days FROM analytics.meals_per_day_distribution WHERE day = '$day'")
            assertEquals(listOf("1:1"), rows)
        } finally {
            database.blockingTransaction {
                exec("DELETE FROM log_entries WHERE day = '$day'")
                exec("DELETE FROM day_records WHERE day = '$day'")
            }
        }
    }

    /** V13 is additive and idempotent: running it again over a loaded table changes nothing. */
    @Test
    fun barcodeMissesMigrationIsIdempotent() {
        val rows = "SELECT count(*)::text FROM barcode_misses"
        database.blockingTransaction {
            exec("INSERT INTO barcode_misses (id, barcode, platform) VALUES (gen_random_uuid(), '04963406', 'ios')")
        }
        try {
            val before = strings(rows)
            val migration = checkNotNull(javaClass.getResource("/db/migration/V13__barcode_misses.sql")).readText()
            database.blockingTransaction { exec(migration) }
            assertEquals(before, strings(rows))
        } finally {
            database.blockingTransaction { exec("DELETE FROM barcode_misses") }
        }
    }

    /** The coverage views: errors stay out of the hit rate, misses group on the GTIN, region defaults to unknown. */
    @Test
    fun barcodeCoverageViewsAggregate() {
        database.blockingTransaction {
            listOf("catalog", "off", "miss", "error").forEach { outcome ->
                exec("INSERT INTO barcode_lookups (at, barcode, outcome) VALUES ('2001-02-03T12:00:00Z', '049000028911', '$outcome')")
            }
            // A UPC-A and its EAN-13 are one product; the third has no region.
            exec(
                "INSERT INTO barcode_misses (id, barcode, gtin, country, platform) VALUES " +
                    "(gen_random_uuid(), '049000028911', '00049000028911', 'US', 'ios'), " +
                    "(gen_random_uuid(), '0049000028911', '00049000028911', 'CA', 'android'), " +
                    "(gen_random_uuid(), '4006381333931', '04006381333931', NULL, 'android')",
            )
        }
        try {
            assertEquals(
                listOf("4:2:1:1:0.6667"),
                strings(
                    "SELECT lookups || ':' || hits || ':' || misses || ':' || errors || ':' || hit_rate " +
                        "FROM analytics.barcode_hit_rate_daily WHERE day = '2001-02-03'",
                ),
            )
            assertEquals(
                listOf("00049000028911:2:2", "04006381333931:1:0"),
                strings("SELECT code || ':' || misses || ':' || countries FROM analytics.top_missed_barcodes"),
            )
            assertEquals(
                listOf("CA:1", "US:1", "unknown:1"),
                strings("SELECT country || ':' || misses FROM analytics.barcode_misses_by_country ORDER BY country"),
            )
        } finally {
            database.blockingTransaction {
                exec("DELETE FROM barcode_lookups WHERE at < '2002-01-01'")
                exec("DELETE FROM barcode_misses")
            }
        }
    }

    @Test
    fun grafanaRoleReadsAnalyticsAndNothingElse() {
        database.blockingTransaction {
            exec("SET LOCAL ROLE grafana_ro")
            ANALYTICS_VIEWS.forEach { exec("SELECT * FROM analytics.$it LIMIT 1") }
        }
        listOf(
            "log_entries", "user_settings", "weigh_ins", "foods", "off_foods", "food_search_events", "barcode_misses",
            "profiles",
        ).forEach { table ->
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
            "weekly_retention_cohorts", "retention_d1_d7_d30", "barcode_hit_rate_daily", "top_missed_barcodes",
            "barcode_misses_by_country",
        )
    }
}
