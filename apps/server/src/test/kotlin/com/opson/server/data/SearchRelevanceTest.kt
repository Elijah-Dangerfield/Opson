package com.opson.server.data

import com.opson.server.config.DatabaseConfig
import com.opson.server.db.Database
import com.opson.server.db.DatabaseTest
import com.opson.server.domain.Food
import com.opson.server.domain.FoodCatalogRepository
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Assume
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The relevance suite: about 75 queries a person types (staples, dishes,
 * brands, misspellings, prefixes typed so far) with what their top 3 must
 * hold, in `search-relevance.tsv`. It runs against the committed USDA generic
 * seed plus `search-relevance-branded.sql.gz`, the Branded rows from dev that
 * compete for those queries (made by `scripts/search_relevance_fixture.py`),
 * so Branded products can crowd out a food here the way they do in the app.
 *
 * Prints every query's top 3 and the hit rate, and fails below [FLOOR].
 * Raise the floor when ranking improves; never lower it to land a change.
 *
 * `SEARCH_RELEVANCE_JDBC_URL` (with `_USER` and `_PASSWORD`) runs the same
 * suite against a database that already holds a full catalog instead of
 * the fixture. Flyway migrates it first, so point it at a local copy, never
 * at a shared database.
 */
class SearchRelevanceTest : DatabaseTest() {

    @Test
    fun topThreeHoldWhatPeopleMean() = runTest {
        val target = externalDatabase() ?: run {
            Assume.assumeTrue("USDA seed not generated", SEED.exists())
            applyGzippedSql(SEED)
            applyGzippedSql(BRANDED)
            database
        }
        val catalog = PostgresFoodCatalogRepository(target, Clock.System)
        val cases = RelevanceCase.load(File(System.getenv("SEARCH_RELEVANCE_SUITE")?.takeIf { it.isNotBlank() } ?: SUITE))
        cases.forEach { catalog.search(it.query, FoodCatalogRepository.DEFAULT_LIMIT, record = false) } // warm up
        val millis = mutableListOf<Double>()
        val outcomes = cases.map { case ->
            val started = System.nanoTime()
            val top = catalog.search(case.query, FoodCatalogRepository.DEFAULT_LIMIT, record = false).take(3)
            millis += ((System.nanoTime() - started) / 1e6).also { case.millis = it }
            case to case.failures(top.map { it.food })
        }

        val passed = outcomes.count { (_, failures) -> failures.isEmpty() }
        val rate = passed.toDouble() / cases.size
        println("Search relevance: $passed of ${cases.size} queries hold (${"%.1f".format(rate * 100)}%)")
        millis.sort()
        println(
            "Search time, limit ${FoodCatalogRepository.DEFAULT_LIMIT}: p50 ${"%.0f".format(millis[millis.size / 2])} ms, " +
                "p95 ${"%.0f".format(millis[(millis.size * 95) / 100])} ms, max ${"%.0f".format(millis.last())} ms",
        )
        for ((case, failures) in outcomes) {
            val mark = if (failures.isEmpty()) "ok  " else "FAIL"
            println("$mark ${case.query} (${"%.0f".format(case.millis)} ms)  ->  ${case.lastTop.joinToString(" | ") { it.label() }}")
            failures.forEach { println("       missed: $it") }
        }
        if (target !== database) target.close()
        assertTrue(rate >= FLOOR, "relevance fell to $passed of ${cases.size}, below the floor of ${(FLOOR * 100).toInt()}%")
    }

    private fun applyGzippedSql(file: File) {
        val sql = GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }
        database.blockingTransaction {
            val jdbc = TransactionManager.current().connection.connection as java.sql.Connection
            jdbc.createStatement().use { it.execute(sql) }
        }
    }

    private fun externalDatabase(): Database? {
        val url = System.getenv("SEARCH_RELEVANCE_JDBC_URL")?.takeIf { it.isNotBlank() } ?: return null
        return Database.connect(
            DatabaseConfig(
                jdbcUrl = url,
                username = System.getenv("SEARCH_RELEVANCE_USER").orEmpty(),
                password = System.getenv("SEARCH_RELEVANCE_PASSWORD").orEmpty(),
                poolMaxSize = 2,
                poolMinIdle = 1,
            ),
        )
    }

    private fun Food.label(): String = if (brand == null) name else "$name [$brand]"

    private companion object {
        const val SUITE = "src/test/resources/search-relevance.tsv"
        val SEED = File("fooddb/usda_foods.sql.gz")
        val BRANDED = File("src/test/resources/search-relevance-branded.sql.gz")
        /** 83 of 83 on 2026-10-30 (78 of 83 before the plural fix); 78 of 78 on 2026-10-26, 67 before 0.2. */
        const val FLOOR = 0.95
    }
}

/** One line of `search-relevance.tsv`. */
internal class RelevanceCase(val query: String, private val expectations: List<Expectation>) {
    var lastTop: List<Food> = emptyList()
        private set
    var millis: Double = 0.0

    /** What [top] misses, as the expectation text; empty when the query holds. */
    fun failures(top: List<Food>): List<String> {
        lastTop = top
        return expectations.filterNot { it.holds(top) }.map { it.text }
    }

    class Expectation(val text: String) {
        private val first = text.startsWith("1:")
        private val negated = text.startsWith("!")
        private val body = text.removePrefix("1:").removePrefix("!")
        private val field = body.substringBefore('=', "name").takeIf { it in FIELDS } ?: "name"
        private val regex = Regex(if (field == "name") body else body.substringAfter('='), RegexOption.IGNORE_CASE)

        private fun matches(food: Food): Boolean = when (field) {
            "brand" -> regex.containsMatchIn(food.brand.orEmpty())
            "any" -> regex.containsMatchIn(food.name) || regex.containsMatchIn(food.brand.orEmpty())
            else -> regex.containsMatchIn(food.name)
        }

        fun holds(top: List<Food>): Boolean = when {
            first -> top.firstOrNull()?.let(::matches) == true
            negated -> top.none(::matches)
            else -> top.any(::matches)
        }
    }

    companion object {
        private val FIELDS = setOf("brand", "any")

        fun load(file: File): List<RelevanceCase> = file.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val (query, expected) = line.split('\t', limit = 2)
                RelevanceCase(query, expected.split(" ; ").map { Expectation(it.trim()) })
            }
    }
}
