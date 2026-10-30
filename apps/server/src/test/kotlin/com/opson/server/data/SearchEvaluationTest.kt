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
import kotlin.math.ln
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The ranking's offline evaluation: graded queries in `search-eval.tsv`, scored the way search
 * people score a ranking, so a change shows how much better or worse it is rather than only
 * whether a top 3 holds ([SearchRelevanceTest] does that).
 *
 *  - MRR: one over the rank of the first row graded 3 (what the person means), 0 when none is
 *    in the top 10, averaged over queries.
 *  - NDCG@10: gain 2^grade - 1, discounted by log2(rank + 1), over the best order of the top
 *    [POOL] rows. The pool is what search returned, so a food it never finds lowers MRR, not
 *    the ideal.
 *  - Good first: the first row is graded 2 or more.
 *  - Modifiers in the top 3: rows graded 0 among the first three, averaged ("Strawberry milk"
 *    for `strawberry`).
 *
 * Same data as the relevance suite: the committed USDA generic seed plus the Branded fixture,
 * or `SEARCH_RELEVANCE_JDBC_URL` for a full local copy. Prints every query and writes the
 * table to `build/reports/search-eval.txt`, so a ranking change can be compared before and
 * after:
 *
 *     ./gradlew :apps:server:test --tests '*SearchEvaluationTest*' -i
 *
 * Fails below the floors. Raise them when ranking improves; never lower them to land a change.
 */
class SearchEvaluationTest : DatabaseTest() {

    @Test
    fun rankingScoresAboveTheFloors() = runTest {
        val target = externalDatabase() ?: run {
            Assume.assumeTrue("USDA seed not generated", SEED.exists())
            applyGzippedSql(SEED)
            applyGzippedSql(BRANDED)
            database
        }
        val catalog = PostgresFoodCatalogRepository(target, Clock.System)
        val cases = EvalCase.load(File(SUITE))
        val scores = cases.map { case ->
            val pool = catalog.search(case.query, POOL, record = false).map { it.food }
            case.score(pool)
        }
        if (target !== database) target.close()

        val report = buildString {
            for (s in scores) {
                appendLine(
                    "%-22s RR %.2f  NDCG %.2f  %s".format(
                        s.query,
                        s.reciprocalRank,
                        s.ndcg,
                        s.top.take(SHOWN).joinToString(" | ") { (food, grade) -> "$grade ${food.label()}" },
                    ),
                )
            }
            val summary = Summary.of(scores)
            appendLine()
            appendLine(summary.toString())
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/search-eval.txt").writeText(report)

        val summary = Summary.of(scores)
        assertTrue(summary.mrr >= MRR_FLOOR, "MRR fell to ${summary.mrr}, below $MRR_FLOOR")
        assertTrue(summary.ndcg >= NDCG_FLOOR, "NDCG@10 fell to ${summary.ndcg}, below $NDCG_FLOOR")
        assertTrue(summary.goodFirst >= GOOD_FIRST_FLOOR, "good first rows fell to ${summary.goodFirst}, below $GOOD_FIRST_FLOOR")
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

    private companion object {
        const val SUITE = "src/test/resources/search-eval.tsv"
        val SEED = File("fooddb/usda_foods.sql.gz")
        val BRANDED = File("src/test/resources/search-relevance-branded.sql.gz")
        const val POOL = 50
        const val SHOWN = 5

        // 2026-10-30, 58 queries. Before the plural and plain-form change: MRR 0.914, NDCG@10 0.822,
        // good first 90%, modifiers in the top 3 0.31. After: 0.991, 0.890, 98%, 0.07.
        const val MRR_FLOOR = 0.95
        const val NDCG_FLOOR = 0.87
        const val GOOD_FIRST_FLOOR = 0.95
    }
}

private fun Food.label(): String = if (brand == null) name else "$name [$brand]"

/** One query's scores. [top] is the first ten rows with their grades. */
internal class EvalScore(val query: String, val reciprocalRank: Double, val ndcg: Double, val top: List<Pair<Food, Int>>) {
    val goodFirst: Boolean get() = (top.firstOrNull()?.second ?: 0) >= 2
    val modifiersInTop3: Int get() = top.take(3).count { it.second == 0 }
}

internal data class Summary(val queries: Int, val mrr: Double, val ndcg: Double, val goodFirst: Double, val modifiersInTop3: Double) {
    override fun toString(): String =
        "Search evaluation over $queries queries: MRR %.3f, NDCG@10 %.3f, good first %.0f%%, modifiers in top 3 %.2f"
            .format(mrr, ndcg, goodFirst * 100, modifiersInTop3)

    companion object {
        fun of(scores: List<EvalScore>) = Summary(
            queries = scores.size,
            mrr = scores.map { it.reciprocalRank }.average(),
            ndcg = scores.map { it.ndcg }.average(),
            goodFirst = scores.count { it.goodFirst }.toDouble() / scores.size,
            modifiersInTop3 = scores.map { it.modifiersInTop3 }.average(),
        )
    }
}

/** One line of `search-eval.tsv`. */
internal class EvalCase(val query: String, private val labels: List<Label>) {

    fun grade(food: Food): Int = labels.firstOrNull { it.matches(food) }?.grade ?: 0

    fun score(pool: List<Food>): EvalScore {
        val grades = pool.map(::grade)
        val top = grades.take(DEPTH)
        val best = labels.maxOf { it.grade }
        val firstBest = top.indexOfFirst { it == best }
        val ideal = dcg(grades.sortedDescending().take(DEPTH))
        return EvalScore(
            query = query,
            reciprocalRank = if (firstBest < 0) 0.0 else 1.0 / (firstBest + 1),
            ndcg = if (ideal == 0.0) 0.0 else dcg(top) / ideal,
            top = pool.take(DEPTH).zip(top),
        )
    }

    class Label(val grade: Int, text: String) {
        private val field = text.substringBefore('=', "name").takeIf { it in FIELDS } ?: "name"
        private val regex = Regex(if (field == "name") text else text.substringAfter('='), RegexOption.IGNORE_CASE)

        fun matches(food: Food): Boolean = when (field) {
            "brand" -> regex.containsMatchIn(food.brand.orEmpty())
            "any" -> regex.containsMatchIn(food.name) || regex.containsMatchIn(food.brand.orEmpty())
            else -> regex.containsMatchIn(food.name)
        }
    }

    companion object {
        const val DEPTH = 10
        private val FIELDS = setOf("brand", "any")

        fun dcg(grades: List<Int>): Double =
            grades.withIndex().sumOf { (i, g) -> (2.0.pow(g) - 1) / (ln(i + 2.0) / ln(2.0)) }

        fun load(file: File): List<EvalCase> = file.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val (query, labels) = line.split('\t', limit = 2)
                EvalCase(
                    query,
                    labels.split(" ; ").map { label ->
                        val (grade, regex) = label.trim().split(':', limit = 2)
                        Label(grade.toInt(), regex)
                    },
                )
            }
    }
}
