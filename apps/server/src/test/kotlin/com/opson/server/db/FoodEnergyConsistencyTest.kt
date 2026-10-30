package com.opson.server.db

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * V33: whether a food's energy adds up to its macros. The rule is SQL
 * (`food_energy_check`), so it is tested here against real Postgres, with the
 * triggers that keep it current and the batch function the backfill script loops.
 */
class FoodEnergyConsistencyTest : DatabaseTest() {

    @After
    fun clean() {
        database.blockingTransaction {
            exec("DELETE FROM food_corrections")
            exec("DELETE FROM foods")
            exec("DELETE FROM off_foods")
        }
    }

    // ── The rule ───────────────────────────────────────────────────────────

    @Test
    fun aGenericFoodWithinItsSpecificFactorsAddsUp() {
        // USDA chicken breast: 4/4/9 gives 156.4 against 165, inside 12%.
        assertEquals("true:8.6", check(165, 31, 0, 3.6))
    }

    @Test
    fun aZeroCalorieCanWhoseCarbsGiveMoreThanFiveAServingDoesNot() {
        // Monster Energy Zero Ultra in Branded: 0 kcal, 0.83 g carbs per 100 ml, an 8 fl oz serving.
        assertEquals("false:-3.3", check(0, 0, 0.83, 0, serving = 240))
        // The same per 100 ml with no serving is 3.3 kcal under a "0": within a label's rounding.
        assertEquals("true:-3.3", check(0, 0, 0.83, 0))
    }

    @Test
    fun aOneGramPacketOfSweetenerMayPrintZero() {
        assertEquals("true:-360", check(0, 0, 90, 0, serving = 1))
    }

    @Test
    fun aMissingEnergyOrMacroCannotBeJudged() {
        assertEquals("null:null", check(null, 1, 2, 3))
        assertEquals("null:null", check(100, null, 2, 3))
        assertEquals("null:null", check(100, 1, 2, null))
    }

    @Test
    fun energyWithNoMacrosIsUnknownUntilAlcoholExplainsIt() {
        // Vodka: 231 kcal and nothing else. Without an alcohol figure it may be a spirit or an error.
        assertEquals("null:231", check(231, 0, 0, 0, serving = 42))
        assertEquals("true:0", check(231, 0, 0, 0, alcohol = 33, serving = 42))
        // Water is fine either way.
        assertEquals("true:0", check(0, 0, 0, 0))
    }

    @Test
    fun fiberCountedAtTwoOnAUsLabelAddsUp() {
        // A bran cereal, 40 g serving: 70 g carbs per 100 including 30 g fiber, 300 kcal.
        assertEquals("true:13", check(300, 10, 70, 3, fiber = 30, serving = 40))
        // Without the fiber figure the same label is 47 kcal per 100 off, 18.8 a serving.
        assertEquals("false:-47", check(300, 10, 70, 3, serving = 40))
    }

    @Test
    fun sugarAlcoholsCountedAt2Point4AddUp() {
        assertEquals("true:4", check(240, 0, 95, 0, polyols = 90, serving = 40))
        assertEquals("false:-140", check(240, 0, 95, 0, serving = 40))
    }

    @Test
    fun kilojoulesReadAsKilocaloriesAreFarOff() {
        assertEquals("false:1130", check(1500, 10, 60, 10))
    }

    @Test
    fun theServingIsTheFirstPortionsGramsOrTheMillilitresItsLabelNames() {
        fun amount(portions: String) = strings("SELECT coalesce(public.food_serving_amount('$portions'::jsonb)::text, 'null')").single()
        assertEquals("240", amount("""[{"label": "1 cup", "grams": 240}, {"label": "1 tbsp", "grams": 15}]"""))
        assertEquals("473", amount("""[{"label": "1 can (473 ml)", "grams": null}]"""))
        assertEquals("240", amount("""[{"label": "8.0 fl oz (240 ML)"}]"""))
        assertEquals("null", amount("""[{"label": "1 bar"}]"""))
        assertEquals("null", amount("[]"))
    }

    // ── Kept current ───────────────────────────────────────────────────────

    @Test
    fun aNewFoodIsJudgedAndAChangeToItsNumbersJudgesItAgain() {
        val id = insertFood(kcal = 0.0, carbs = 0.83, portions = """[{"label": "8 fl oz (240 ml)", "grams": null}]""")
        assertEquals("false", energy("foods", id).consistent)
        val checked = energy("foods", id).checkedAt
        assertNotNull(checked)

        database.blockingTransaction { exec("UPDATE foods SET popularity = popularity + 1 WHERE id = '$id'") }
        assertEquals(checked, energy("foods", id).checkedAt, "a popularity bump does not re-judge")

        database.blockingTransaction { exec("UPDATE foods SET kcal_100g = 2 WHERE id = '$id'") }
        assertEquals("true", energy("foods", id).consistent)
    }

    @Test
    fun anOpenFoodFactsRowIsJudgedOnInsert() {
        val id = UUID.randomUUID()
        database.blockingTransaction {
            exec(
                "INSERT INTO off_foods (id, barcode, name, kcal_100g, protein_100g, carbs_100g, fat_100g, source_version) " +
                    "VALUES ('$id', '03017620422003', 'x', 2252, 6.3, 57.5, 30.9, '2026-10-01')",
            )
        }
        // kJ in the kcal column: the macros give 533 kcal, which is 2231 kJ.
        val judged = energy("off_foods", id)
        assertEquals("false" to "1718.7", judged.consistent to judged.gap)
        assertNotNull(judged.checkedAt)
    }

    @Test
    fun aReviewersCorrectionClearsTheFlagAndRevertingItBringsItBack() {
        val id = insertFood(kcal = 0.0, carbs = 0.83, portions = """[{"label": "8 fl oz (240 ml)", "grams": null}]""")
        assertEquals("false", energy("foods", id).consistent)

        // In the order PostgresFoodReportRepository writes: inserted not live, then opened.
        val correction = UUID.randomUUID()
        database.blockingTransaction {
            exec(
                "INSERT INTO food_corrections (id, food_id, created_by, fields, base, reverted_at) " +
                    "VALUES ('$correction', '$id', 'r', '{\"kcal\": 4.2}', '{}', now())",
            )
            exec("UPDATE food_corrections SET reverted_at = NULL WHERE id = '$correction'")
        }
        assertEquals("true", energy("foods", id).consistent)

        database.blockingTransaction { exec("UPDATE food_corrections SET reverted_at = now() WHERE id = '$correction'") }
        assertEquals("false", energy("foods", id).consistent)
    }

    @Test
    fun aCorrectedServingIsTheAmountJudged() {
        // 0 kcal and 2 g carbs per 100 is 8 kcal under a "0"; in a 10 g sachet it is 0.8.
        val id = insertFood(kcal = 0.0, carbs = 2.0, portions = "[]")
        assertEquals("false", energy("foods", id).consistent, "per 100: 8 kcal under a 0")
        database.blockingTransaction {
            exec(
                "INSERT INTO food_corrections (id, food_id, created_by, fields, base) " +
                    "VALUES (gen_random_uuid(), '$id', 'r', '{\"servingLabel\": \"1 sachet\", \"servingGrams\": 10}', '{}')",
            )
        }
        assertEquals("true", energy("foods", id).consistent, "a 10 g sachet holds 0.8 kcal")
    }

    // ── The backfill ───────────────────────────────────────────────────────

    @Test
    fun theBackfillJudgesEveryRowInBatchesAndASecondRunWritesNothing() {
        val ids = List(5) { insertFood(kcal = 100.0, carbs = 25.0) }
        // As if loaded before V33: the columns empty. Setting them fires no trigger.
        database.blockingTransaction {
            exec("UPDATE foods SET energy_consistent = NULL, energy_gap_kcal = NULL, energy_checked_at = NULL")
        }

        assertEquals(Run(batches = 3, updated = 5), backfill("public.foods", batchSize = 2))
        ids.forEach { assertEquals("true", energy("foods", it).consistent) }
        assertEquals(Run(batches = 3, updated = 0), backfill("public.foods", batchSize = 2))
        // A changed rule re-judges everything judged before the run started.
        assertEquals(Run(batches = 1, updated = 5), backfill("public.foods", batchSize = 10, checkedBefore = "now() + interval '1 minute'"))
    }

    @Test
    fun theBackfillResumesAfterTheLastIdItReported() {
        List(4) { insertFood(kcal = 100.0, carbs = 25.0) }
        database.blockingTransaction { exec("UPDATE foods SET energy_checked_at = NULL") }

        val first = strings("SELECT last_id::text FROM public.food_energy_backfill('public.foods', NULL, 3)").single()
        val rest = strings("SELECT scanned || ':' || updated FROM public.food_energy_backfill('public.foods', '$first', 3)").single()
        assertEquals("1:1", rest)
        assertEquals(listOf("0"), strings("SELECT count(*)::text FROM foods WHERE energy_checked_at IS NULL"))
    }

    @Test
    fun theBackfillOnlyTouchesTheFoodTables() {
        val error = runCatching { strings("SELECT * FROM public.food_energy_backfill('public.profiles', NULL, 10)") }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("judges public.foods or public.off_foods"), error?.message)
    }

    // ── Grafana ────────────────────────────────────────────────────────────

    @Test
    fun theViewsCountBySourceAndListTheFurthestOffWithoutPendingFoods() {
        insertFood(kcal = 100.0, carbs = 25.0)
        insertFood(kcal = 0.0, carbs = 50.0)
        insertFood(kcal = null, carbs = 50.0)
        val pending = insertFood(kcal = 900.0, carbs = 1.0, source = "user_submitted", reviewStatus = "pending")
        val unchecked = insertFood(kcal = 100.0, carbs = 25.0)
        database.blockingTransaction { exec("UPDATE foods SET energy_checked_at = NULL, energy_consistent = NULL WHERE id = '$unchecked'") }

        assertEquals(
            listOf("4:1:1:1:1"),
            strings(
                "SELECT foods || ':' || consistent || ':' || inconsistent || ':' || unknown || ':' || unchecked " +
                    "FROM analytics.food_energy_by_source WHERE source = 'usda_sr'",
            ),
        )
        assertEquals(listOf("-200"), strings("SELECT gap_kcal_per_100::text FROM analytics.food_energy_worst"))
        assertEquals(listOf("0"), strings("SELECT count(*)::text FROM analytics.food_energy_worst WHERE food_id = '$pending'"))
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private data class Energy(val consistent: String, val gap: String, val checkedAt: String?)

    private data class Run(val batches: Int, val updated: Int)

    private fun check(
        kcal: Number?,
        protein: Number?,
        carbs: Number?,
        fat: Number?,
        fiber: Number? = null,
        polyols: Number? = null,
        alcohol: Number? = null,
        serving: Number? = null,
    ): String {
        val args = listOf(kcal, protein, carbs, fat, fiber, polyols, alcohol, serving).joinToString { it?.toString() ?: "NULL" }
        return strings(
            "SELECT coalesce(consistent::text, 'null') || ':' || coalesce(gap_kcal::text, 'null') FROM public.food_energy_check($args)",
        ).single()
    }

    private fun insertFood(
        kcal: Double?,
        carbs: Double,
        portions: String = "[]",
        source: String = "usda_sr",
        reviewStatus: String = "approved",
    ): UUID {
        val id = UUID.randomUUID()
        database.blockingTransaction {
            exec(
                "INSERT INTO foods (id, source, source_id, name, kcal_100g, protein_100g, carbs_100g, fat_100g, portions, review_status) " +
                    "VALUES ('$id', '$source', '$id', 'x', ${kcal ?: "NULL"}, 0, $carbs, 0, '$portions'::jsonb, '$reviewStatus')",
            )
        }
        return id
    }

    private fun energy(table: String, id: UUID): Energy {
        val row = strings(
            "SELECT coalesce(energy_consistent::text, 'null') || '|' || coalesce(energy_gap_kcal::text, 'null') || '|' || " +
                "coalesce(energy_checked_at::text, '') FROM $table WHERE id = '$id'",
        ).single().split('|')
        return Energy(row[0], row[1], row[2].ifEmpty { null })
    }

    /** What scripts/check_food_energy.py does: call the batch until it looks at nothing. */
    private fun backfill(table: String, batchSize: Int, checkedBefore: String = "NULL"): Run {
        var after = "NULL"
        var batches = 0
        var updated = 0
        while (true) {
            val (last, scanned, wrote) = strings(
                "SELECT coalesce(last_id::text, '') || '|' || scanned || '|' || updated " +
                    "FROM public.food_energy_backfill('$table', $after, $batchSize, $checkedBefore)",
            ).single().split('|')
            if (scanned.toInt() == 0) return Run(batches, updated)
            batches++
            updated += wrote.toInt()
            after = "'$last'"
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
}
