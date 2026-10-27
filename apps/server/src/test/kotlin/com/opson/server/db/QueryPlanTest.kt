package com.opson.server.db

import com.opson.server.data.OpenFoodFactsSearch
import com.opson.server.data.PostgresFoodCatalogRepository
import com.opson.server.data.SearchTerms
import com.opson.server.domain.Barcodes
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.UUIDColumnType
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The hot paths have an index to use. The test tables are nearly empty, where
 * Postgres would rightly scan them, so each plan is taken with sequential
 * scans priced out (`enable_seqscan = off`): a `Seq Scan` that remains means
 * no index can serve the statement, which on a real table means reading it all.
 */
class QueryPlanTest : DatabaseTest() {

    private fun plan(sql: String, args: List<Pair<IColumnType<*>, Any?>> = emptyList()): String =
        database.blockingTransaction {
            val tx = TransactionManager.current()
            tx.exec("SET LOCAL enable_seqscan = off")
            val lines = mutableListOf<String>()
            tx.exec("EXPLAIN $sql", args, explicitStatementType = StatementType.SELECT) { rs ->
                while (rs.next()) lines += rs.getString(1)
            }
            lines.joinToString("\n")
        }

    private fun assertUses(index: String, plan: String) {
        assertTrue(index in plan, "expected $index in:\n$plan")
        assertFalse("Seq Scan" in plan, "a sequential scan in:\n$plan")
    }

    private val user = UUIDColumnType() to UUID.randomUUID()

    @Test
    fun syncPullReadsEachTableByUserAndCursor() {
        for (table in listOf("log_entries", "day_records", "meal_feelings", "weigh_ins", "custom_foods", "saved_meals", "user_settings", "fasts", "exercises")) {
            val plan = plan(
                "SELECT * FROM $table WHERE user_id = ? AND server_seq > ? ORDER BY server_seq LIMIT 501",
                listOf(user, LongColumnType() to 0L),
            )
            assertUses("${table}_user_seq_idx", plan)
        }
    }

    @Test
    fun exportReadsEntriesByUserAndDay() {
        val plan = plan(
            "SELECT * FROM log_entries WHERE user_id = ? AND deleted = false ORDER BY day, logged_at",
            listOf(user),
        )
        assertUses("log_entries_user_day_idx", plan)
    }

    @Test
    fun aScanFindsItsBarcodeInEitherTable() {
        val forms = Barcodes.equivalents("3017620422003")
        val foods = plan(
            "SELECT * FROM foods WHERE barcode = ANY (string_to_array(?, '|'))",
            listOf(TextColumnType() to forms.joinToString("|")),
        )
        assertUses("foods_barcode_key", foods)
        val offFoods = plan(OpenFoodFactsSearch.BY_BARCODE_SQL, listOf(TextColumnType() to forms.joinToString("|")))
        assertUses("off_foods_barcode_key", offFoods)
    }

    @Test
    fun searchReadsFoodsOnlyThroughItsIndexes() {
        val terms = SearchTerms.of("banana")!!
        val plan = plan(PostgresFoodCatalogRepository.SEARCH_SQL, PostgresFoodCatalogRepository.searchArgs(terms, 25))
        assertTrue("foods_generic_" in plan, plan)
        assertFalse("Seq Scan on foods" in plan, "a sequential scan of foods in:\n$plan")
    }
}
