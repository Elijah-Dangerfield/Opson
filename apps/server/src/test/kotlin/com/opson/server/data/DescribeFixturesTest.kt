package com.opson.server.data

import com.opson.server.db.DatabaseTest
import com.opson.server.domain.CommonFoods
import com.opson.server.domain.DescribedItem
import com.opson.server.domain.FoodTier
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Assume
import org.junit.BeforeClass
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * "Describe it" against the real catalog: the committed USDA seed plus the
 * Branded rows that compete in search (`search-relevance-branded.sql.gz`).
 * Each fixture is something a person says or speech recognition writes down,
 * with the lines it must become. Prints every result, so a run is also the
 * report of what each phrase produces.
 */
class DescribeFixturesTest : DatabaseTest() {

    @Test
    fun everyFixtureReadsAsExpected() = runTest {
        val describer = CatalogFoodDescriber(PostgresFoodCatalogRepository(database, Clock.System))
        val failures = mutableListOf<String>()
        DescribeFixtures.all.groupBy { it.group }.forEach { (group, fixtures) ->
            println("\n== $group")
            fixtures.forEach { fixture ->
                val items = describer.describe(fixture.phrase)
                println("\"${fixture.phrase}\"")
                items.forEach { println("    ${it.line()}") }
                fixture.failures(items).forEach { failures += "\"${fixture.phrase}\": $it" }
            }
        }
        println("\n${DescribeFixtures.all.size - failures.map { it.substringBefore(':') }.distinct().size} of ${DescribeFixtures.all.size} phrases hold")
        failures.forEach { println("FAIL $it") }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** Every everyday food names a generic seed row, and its servings are portions that row has. */
    @Test
    fun everyCommonFoodIsInTheSeed() = runTest {
        val catalog = PostgresFoodCatalogRepository(database, Clock.System)
        val problems = mutableListOf<String>()
        for (common in CommonFoods.all) {
            for (name in listOf(common.name) + common.alternatives) {
                val food = catalog.search(name, limit = 10, record = false)
                    .firstOrNull { it.food.name.equals(name, ignoreCase = true) && it.food.tier == FoodTier.Reference }?.food
                if (food == null) {
                    problems += "$name: not found"
                    continue
                }
                if (name != common.name) continue
                listOfNotNull(common.serving, common.asAddition).forEach { serving ->
                    if (food.portions.none { it.grams != null && it.label.startsWith(serving.label, ignoreCase = true) }) {
                        problems += "$name: no portion '${serving.label}' in ${food.portions.map { it.label }}"
                    }
                }
            }
        }
        problems.forEach { println("MISSING $it") }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private fun DescribedItem.line(): String {
        val kcal = nutrients?.kcal?.let { "${it.toInt()} kcal" } ?: "no match"
        val alts = alternatives.joinToString(" | ") { it.name }.ifEmpty { "-" }
        return "$name · $amountText (${grams ?: "?"} g, $kcal, $confidence)  alt: $alts"
    }

    companion object {
        private val SEED = File("fooddb/usda_foods.sql.gz")
        private val BRANDED = File("src/test/resources/search-relevance-branded.sql.gz")
        private var loaded = false

        @JvmStatic
        @BeforeClass
        fun loadCatalog() {
            Assume.assumeTrue("USDA seed not generated", SEED.exists())
        }
    }

    private fun loadOnce() {
        if (loaded) return
        listOf(SEED, BRANDED).forEach { file ->
            val sql = GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }
            database.blockingTransaction {
                val jdbc = TransactionManager.current().connection.connection as java.sql.Connection
                jdbc.createStatement().use { it.execute(sql) }
            }
        }
        loaded = true
    }

    @org.junit.Before
    fun seed() = loadOnce()
}

/**
 * One thing a person says, and what each line must be: the food's name
 * (a regex, case-insensitive) and the amount ("1 slice"), in order. An
 * expectation of [UNMATCHED] means the line has no food.
 */
internal data class DescribeFixture(val group: String, val phrase: String, val expected: List<Pair<String, String?>>) {
    fun failures(items: List<DescribedItem>): List<String> {
        val problems = mutableListOf<String>()
        if (items.size != expected.size) problems += "expected ${expected.size} lines, got ${items.size} (${items.joinToString { it.name }})"
        expected.zip(items).forEachIndexed { i, (want, got) ->
            val (name, amount) = want
            if (name == UNMATCHED) {
                if (got.foodId != null) problems += "line ${i + 1}: expected no match, got ${got.name}"
            } else if (!Regex(name, RegexOption.IGNORE_CASE).containsMatchIn(got.name)) {
                problems += "line ${i + 1}: expected /$name/, got ${got.name}"
            }
            if (amount != null && got.amountText != amount) problems += "line ${i + 1}: expected amount '$amount', got '${got.amountText}'"
        }
        return problems
    }

    companion object {
        const val UNMATCHED = "<none>"
    }
}

internal object DescribeFixtures {
    private fun f(group: String, phrase: String, vararg expected: Pair<String, String?>) = DescribeFixture(group, phrase, expected.toList())

    private const val OWNER = "The seven from the device report"
    private const val RESTAURANT = "Restaurants, brand and count"
    private const val HOME = "Home meals"
    private const val COUNTS = "Counts and units"
    private const val VAGUE = "Vague amounts"
    private const val SNACKS = "Snacks and drinks"
    private const val CORRECTIONS = "Corrections and leaving things out"
    private const val NOISE = "Speech recognition noise"

    val all: List<DescribeFixture> = listOf(
        // Each "name" is a regex on the catalog name; each amount is the line's amount exactly.
        f(OWNER, "some toast and eggs", "^Bread, white, toasted$" to "1 medium or regular slice", "^Egg, whole, cooked, NS as to cooking method$" to "1 egg"),
        f(OWNER, "a coffee with milk", "^Coffee, brewed$" to "1 cup", "^Milk, NFS$" to "1 fl oz"),
        f(OWNER, "2 slices of pizza", "^Pizza, cheese, from restaurant or fast food" to "2 pieces"),
        f(OWNER, "chicken salad", "^Chicken salad spread$" to "1/2 cup"),
        f(OWNER, "a banana and peanut butter", "^Banana, raw$" to "1 banana", "^Peanut butter$" to "2 tablespoons"),
        f(OWNER, "oatmeal with berries", "^Oatmeal, NFS$" to "1 cup", "^Berries, NFS$" to "1/2 cup"),
        f(
            OWNER, "eggs, bacon, toast and orange juice",
            "^Egg, whole, cooked" to "1 egg", "^Bacon, NS as to type of meat, cooked$" to "2 medium slices",
            "^Bread, white, toasted$" to "1 medium or regular slice", "^Orange juice" to "1 cup",
        ),

        f(RESTAURANT, "8 piece grilled nuggets from Chick-fil-A", "^Chicken nuggets" to "8 nuggets"),
        f(RESTAURANT, "a Big Mac and medium fries", "^Big Mac \\(McDonalds\\)$" to "1 McDonald's Big Mac", "^Potato, french fries, NFS$" to "2 cups"),
        f(RESTAURANT, "Chipotle chicken burrito bowl", "^Burrito bowl, chicken$" to "1 item"),
        f(RESTAURANT, "grande oat milk latte from Starbucks", "^Coffee, Latte, with non-dairy milk$" to "1 medium"),
        f(RESTAURANT, "a chicken sandwich from Chick-fil-A", "^CHICK-FIL-A, chicken sandwich$" to "1 sandwich"),
        f(RESTAURANT, "a Whopper with cheese", "^Whopper with cheese \\(Burger King\\)$" to "1 cheeseburger"),
        f(RESTAURANT, "two slices of pepperoni pizza from Domino's", "^DOMINO'S 14\" Pepperoni Pizza" to "2 slices"),
        f(RESTAURANT, "a quarter pounder with cheese from McDonald's", "^McDONALD'S, QUARTER POUNDER with Cheese$" to "1 item"),
        f(RESTAURANT, "large fries from McDonald's", "^McDONALD'S, french fries$" to "1 large serving"),

        f(HOME, "leftover spaghetti", "^Pasta with tomato-based sauce, home recipe$" to "1 1/2 cups"),
        f(HOME, "a turkey sandwich on wheat", "^Turkey sandwich on wheat$" to "1 regular"),
        f(HOME, "a bowl of cereal with milk", "^Cereal, ready-to-eat, NFS$" to "1 1/2 cups", "^Milk, NFS$" to "1 fl oz"),
        f(HOME, "grilled chicken breast and broccoli", "chicken.*breast.*grilled|grilled.*breast" to null, "^Broccoli, NS as to form, cooked$" to "1 cup"),
        f(HOME, "mac and cheese", "^Macaroni or noodles with cheese$" to "1 cup"),
        f(HOME, "a peanut butter and jelly sandwich", "^Peanut butter and jelly sandwich, NFS$" to "1 sandwich"),

        f(COUNTS, "two scrambled eggs", "scrambled" to "2 large"),
        f(COUNTS, "half a cup of rice", "^Rice, cooked, NFS$" to "1/2 cup"),
        f(COUNTS, "a handful of almonds", "^Almonds, NFS$" to "1 handful"),
        f(COUNTS, "3 oz salmon", "salmon" to "3 oz"),
        f(COUNTS, "a tall glass of milk", "^Milk, NFS$" to "1 1/2 cups"),
        f(COUNTS, "2 tbsp peanut butter", "^Peanut butter$" to "2 tablespoons"),
        f(COUNTS, "200g greek yogurt", "^Yogurt, Greek" to "200 g"),
        f(COUNTS, "a can of coke", "^Soft drink, cola$" to "1 can"),
        f(COUNTS, "3 strips of bacon", "^Bacon, NS as to type of meat, cooked$" to "3 medium slices"),

        f(VAGUE, "some rice", "^Rice, cooked, NFS$" to "1 cup"),
        f(VAGUE, "a bit of cheese", "^Cheese, NFS$" to "1 slice"),
        f(VAGUE, "a big bowl of oatmeal", "^Oatmeal, NFS$" to "2 1/4 cups"),
        f(VAGUE, "a little ice cream", "^Ice cream, NFS$" to "1 scoop"),
        f(VAGUE, "some chips", "^Potato chips, NFS$" to "1 small single serving bag"),

        f(SNACKS, "an apple", "^Apple, raw$" to "1 medium"),
        f(SNACKS, "a beer", "^Beer$" to "1 can or bottle"),
        f(SNACKS, "a glass of red wine", "^Wine, red$" to "1 glass"),
        f(SNACKS, "a diet coke", "^Soft drink, cola, diet$" to "1 can"),
        f(SNACKS, "a cappuccino", "^Coffee, Cappuccino$" to "1 small"),
        f(SNACKS, "a protein bar", "protein bar" to "1 bar"),

        f(CORRECTIONS, "a coffee, no wait, a latte", "^Coffee, Latte$" to "1 medium"),
        f(CORRECTIONS, "a coffee no wait a latte", "^Coffee, Latte$" to "1 medium"),
        f(CORRECTIONS, "toast no butter", "^Bread, white, toasted$" to "1 medium or regular slice"),
        f(CORRECTIONS, "two eggs actually three eggs", "^Egg, whole, cooked" to "3 eggs"),
        f(CORRECTIONS, "a bagel with cream cheese, without the butter", "^Bagel$" to "1 regular", "^Cream cheese" to null),

        f(NOISE, "8 piece grilled nuggest from chickfila", "^Chicken nuggets" to "8 nuggets"),
        f(NOISE, "chic filet chicken sandwich", "^CHICK-FIL-A, chicken sandwich$" to "1 sandwich"),
        f(NOISE, "a big mack and frys", "^Big Mac \\(McDonalds\\)$" to "1 McDonald's Big Mac", "^Potato, french fries, NFS$" to "2 cups"),
        f(
            NOISE, "i had um two eggs bacon toast and orange juice for breakfast",
            "^Egg, whole, cooked" to "2 eggs", "^Bacon" to "2 medium slices", "^Bread, white, toasted$" to null, "^Orange juice" to "1 cup",
        ),
        f(NOISE, "eggs bacon toast", "^Egg, whole, cooked" to "1 egg", "^Bacon" to null, "^Bread, white, toasted$" to null),
        f(NOISE, "mcdonalds big mac", "BIG MAC|Big Mac" to null),
        f(NOISE, "uh a latte from star bucks", "^Coffee, Latte$" to "1 medium"),
        f(NOISE, "So I had a coffee. And then a banana", "^Coffee, brewed$" to "1 cup", "^Banana, raw$" to "1 banana"),
    )
}
