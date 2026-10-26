package com.opson.server.data

import com.opson.server.config.FoodSourcesConfig
import com.opson.server.db.BarcodeLookupsTable
import com.opson.server.db.Database
import com.opson.server.db.FoodSearchEventsTable
import com.opson.server.db.FoodsTable
import com.opson.server.db.toJavaInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.BarcodeOutcome
import com.opson.server.domain.Barcodes
import com.opson.server.domain.CatalogSource
import com.opson.server.domain.ExternalProduct
import com.opson.server.domain.Food
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.FoodMatch
import com.opson.server.domain.FoodTier
import com.opson.server.domain.MatchTier
import com.opson.server.domain.Nutrients
import com.opson.server.domain.Portion
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.sql.BooleanColumnType
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.UUIDColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.upsert
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.sql.ResultSet
import java.util.UUID
import kotlin.time.Clock

/**
 * Postgres-backed [FoodCatalogRepository].
 *
 * Search is one raw SQL statement because the ranking needs things Exposed's
 * DSL does not model: a `tsquery`, pg_trgm's `similarity()` and `%` operator
 * (schema-qualified to `extensions`, see V7) and a computed tier. The three
 * match conditions are each served by an index: `foods_name_trgm_idx` for the
 * prefix `LIKE` and the trigram operator, `foods_search_idx` for full text.
 *
 * Tiers, best first ([MatchTier]):
 *  - exact: the whole name is the query (or its singular / plural);
 *  - head: the name's first comma segment is, also after a category word
 *    ("Fish, salmon"), or its first two segments are exactly the query's
 *    words in any order ("Rice, brown" for "brown rice"). USDA names read
 *    "Egg, whole, raw", so this is what makes "eggs" find eggs;
 *  - prefix: the name starts with the query as whole words ("banana" finds
 *    "Banana chips" but "toast" does not find "Toaster pastries");
 *  - full text: every query word starts a word of the name or brand;
 *  - similar: trigram similarity only, for typos.
 * Above the match tier sit rules about what the query is asking for: a match
 * on the name beats a match only through the brand, a query that names a
 * brand ranks its products first, and otherwise generic foods come before
 * branded ones. SEARCH_SQL's comment has the full order. Within a match tier:
 * verified first, lower source tier, whole words, the plain form of a food
 * ("raw", "NFS", "plain", "whole", "brewed", "cooked") over prepared dishes,
 * similarity rounded to one decimal, popularity, shorter names.
 *
 * Any change to the ranking answers to the relevance suite
 * (`SearchRelevanceTest`, `search-relevance.tsv`).
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresFoodCatalogRepository(
    private val database: Database,
    private val clock: Clock,
    private val sources: FoodSourcesConfig = FoodSourcesConfig(),
) : FoodCatalogRepository {

    override suspend fun search(
        query: String,
        limit: Int,
        record: Boolean,
        country: String?,
        openFoodFacts: Boolean,
    ): List<FoodMatch> =
        database.transaction {
            val normalized = FoodCatalogRepository.normalizeQuery(query)
            val terms = SearchTerms.of(normalized)
            val capped = limit.coerceIn(1, FoodCatalogRepository.MAX_LIMIT)
            val matches = when {
                terms == null -> emptyList()
                openFoodFacts && sources.searchOpenFoodFacts && OpenFoodFactsSearch.searches(terms) ->
                    OpenFoodFactsSearch.merge(
                        catalog = runSearch(terms, capped),
                        openFoodFacts = runOpenFoodFactsSearch(terms, capped, OpenFoodFactsSearch.countryTag(country)),
                        limit = capped,
                    )
                else -> runSearch(terms, capped)
            }
            if (record && normalized.isNotEmpty()) {
                FoodSearchEventsTable.insert {
                    it[at] = clock.now().toJavaInstant()
                    it[queryNorm] = normalized
                    it[resultCount] = matches.size
                    it[picked] = false
                }
            }
            matches
        }

    private fun runSearch(terms: SearchTerms, limit: Int): List<FoodMatch> {
        val text = TextColumnType()
        val args = listOf<Pair<IColumnType<*>, Any?>>(
            text to terms.likePrefix, // generic: prefix
            text to terms.prefixTsQuery, // generic: full text
            BooleanColumnType() to terms.canNameBrand, // Branded: always when a brand may be named,
            IntegerColumnType() to limit, // otherwise only below this many generic matches
            text to terms.likePrefix, // Branded: prefix
            text to terms.prefixTsQuery, // Branded: full text
            IntegerColumnType() to limit, // typo fallback below this many direct matches,
            text to terms.likeWords, // or with no generic match and no brand carrying a query word
            text to terms.normalized, // fallback: trigram similarity
            text to terms.likePrefix, // fallback: not already direct
            text to terms.prefixTsQuery, // fallback: not already direct
            text to terms.phraseVariants.joinToString("|"), // p.variants
            text to terms.wordPrefixRegex, // p.prefix_regex
            text to terms.prefixTsQuery, // p.prefix_ts
            text to terms.wholeWordTsQuery, // p.whole_ts
            text to terms.queryWords, // p.query_words
            text to terms.normalized, // p.normalized
            BooleanColumnType() to terms.canNameBrand, // p.brandable
            text to terms.likeWords, // p.like_words
            text to terms.phrase, // p.phrase
            IntegerColumnType() to limit,
        )
        val results = mutableListOf<FoodMatch>()
        TransactionManager.current().exec(
            stmt = SEARCH_SQL,
            args = args,
            explicitStatementType = StatementType.SELECT,
        ) { rs ->
            while (rs.next()) {
                val tier = MatchTier.entries[rs.getInt("match_tier")]
                results += FoodMatch(rs.toFood(), tier, rs.getDouble("sim"))
            }
        }
        return results
    }

    private fun runOpenFoodFactsSearch(terms: SearchTerms, limit: Int, countryTag: String?): List<FoodMatch> {
        val results = mutableListOf<FoodMatch>()
        TransactionManager.current().exec(
            stmt = OpenFoodFactsSearch.SQL,
            args = OpenFoodFactsSearch.args(terms, limit, countryTag),
            explicitStatementType = StatementType.SELECT,
        ) { rs ->
            while (rs.next()) {
                results += FoodMatch(rs.toFood(), MatchTier.entries[rs.getInt("match_tier")], rs.getDouble("sim"))
            }
        }
        return results
    }

    override suspend fun findById(id: UUID): Food? = database.transaction {
        FoodsTable.selectAll().where { FoodsTable.id eq id }.singleOrNull()?.toFood()
            ?: if (sources.searchOpenFoodFacts) openFoodFactsById(id) else null
    }

    /** An `off_foods` row, which search hands out when Open Food Facts is on. */
    private fun openFoodFactsById(id: UUID): Food? {
        var food: Food? = null
        TransactionManager.current().exec(
            stmt = OpenFoodFactsSearch.BY_ID_SQL,
            args = listOf(UUIDColumnType() to id),
            explicitStatementType = StatementType.SELECT,
        ) { rs -> if (rs.next()) food = rs.toFood() }
        return food
    }

    override suspend fun findByBarcode(barcode: String): Food? = database.transaction {
        FoodsTable.selectAll()
            .where { FoodsTable.barcode inList Barcodes.equivalents(barcode) }
            .orderBy(FoodsTable.tier to SortOrder.ASC, FoodsTable.verified to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toFood()
    }

    override suspend fun upsertExternalProduct(source: CatalogSource, product: ExternalProduct): Food = database.transaction {
        FoodsTable.upsert(
            FoodsTable.sourceKind,
            FoodsTable.sourceId,
            // Keep the id a device may already hold, and the popularity users earned it.
            onUpdateExclude = listOf(FoodsTable.id),
        ) {
            it[id] = UUID.randomUUID()
            it[sourceKind] = source.wire
            it[sourceId] = product.barcode
            it[name] = product.name
            it[brand] = product.brand
            it[barcode] = product.barcode
            it[kcal100g] = product.per100g.kcal?.toFloat()
            it[protein100g] = product.per100g.protein?.toFloat()
            it[carbs100g] = product.per100g.carbs?.toFloat()
            it[fat100g] = product.per100g.fat?.toFloat()
            it[fiber100g] = product.per100g.fiber?.toFloat()
            it[micros] = JSON.encodeToString(MICROS_SERIALIZER, product.micros)
            it[portions] = JSON.encodeToString(PORTIONS_SERIALIZER, product.portions.map { p -> PortionJson(p.label, p.grams) })
        }
        FoodsTable.selectAll()
            .where { (FoodsTable.sourceKind eq source.wire) and (FoodsTable.sourceId eq product.barcode) }
            .single()
            .toFood()
    }

    override suspend fun recordBarcodeLookup(barcode: String, outcome: BarcodeOutcome) {
        database.transaction {
            BarcodeLookupsTable.insert {
                it[at] = clock.now().toJavaInstant()
                it[this.barcode] = barcode
                it[this.outcome] = outcome.wire
            }
        }
    }

    private fun ResultRow.toFood(): Food = Food(
        id = this[FoodsTable.id],
        source = CatalogSource.fromWire(this[FoodsTable.sourceKind]) ?: CatalogSource.UsdaSr,
        sourceId = this[FoodsTable.sourceId],
        name = this[FoodsTable.name],
        brand = this[FoodsTable.brand],
        barcode = this[FoodsTable.barcode],
        per100g = Nutrients(
            kcal = this[FoodsTable.kcal100g].clean(),
            protein = this[FoodsTable.protein100g].clean(),
            carbs = this[FoodsTable.carbs100g].clean(),
            fat = this[FoodsTable.fat100g].clean(),
            fiber = this[FoodsTable.fiber100g].clean(),
        ),
        micros = decodeMicros(this[FoodsTable.micros]),
        portions = decodePortions(this[FoodsTable.portions]),
        tier = FoodTier.fromWire(this[FoodsTable.tier].toInt()),
        verified = this[FoodsTable.verified],
    )

    private fun ResultSet.toFood(): Food = Food(
        id = getObject("id", UUID::class.java),
        source = CatalogSource.fromWire(getString("source")) ?: CatalogSource.UsdaSr,
        sourceId = getString("source_id"),
        name = getString("name"),
        brand = getString("brand"),
        barcode = getString("barcode"),
        per100g = Nutrients(
            kcal = nullableFloat("kcal_100g"),
            protein = nullableFloat("protein_100g"),
            carbs = nullableFloat("carbs_100g"),
            fat = nullableFloat("fat_100g"),
            fiber = nullableFloat("fiber_100g"),
        ),
        micros = decodeMicros(getString("micros")),
        portions = decodePortions(getString("portions")),
        tier = FoodTier.fromWire(getInt("tier")),
        verified = getBoolean("verified"),
    )

    private fun ResultSet.nullableFloat(column: String): Double? {
        val value = getFloat(column)
        return if (wasNull()) null else value.clean()
    }

    @Serializable
    private data class PortionJson(val label: String, val grams: Double? = null)

    internal companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        private val MICROS_SERIALIZER = MapSerializer(String.serializer(), Double.serializer())
        private val PORTIONS_SERIALIZER = ListSerializer(PortionJson.serializer())

        fun decodeMicros(json: String): Map<String, Double> = JSON.decodeFromString(MICROS_SERIALIZER, json)

        fun decodePortions(json: String): List<Portion> =
            JSON.decodeFromString(PORTIONS_SERIALIZER, json).map { Portion(it.label, it.grams) }

        /**
         * The column order here is what [toFood] on a ResultSet reads.
         *
         * Trigram similarity only runs when the direct matches (prefix and
         * full text) number fewer than the limit. Similar-only rows always
         * rank after direct ones, so skipping them changes nothing visible,
         * and with Branded Foods loaded the `%` operator alone rechecks tens
         * of thousands of rows for a word like "banana". The `count(*)`
         * subquery is uncorrelated, so Postgres evaluates it once and skips
         * the trigram scan entirely when it is false.
         *
         * A query counts as naming a brand only when no generic food is an
         * exact or head match for it: "peanut butter" is a food first, even
         * though "Peanut Butter & Co." is a brand.
         */
        /**
         * A brand, a name's first segment or the query as words: lowercase,
         * filler dropped ("Inc.", "Foods", "and", the s of "Campbell's"), naive
         * singulars, single spaces. Both sides of a "does the query name this
         * brand" check go through it, so they always agree.
         */
        private fun wordsOf(expr: String): String {
            val filler = SearchTerms.BRAND_FILLER.joinToString("|")
            return "btrim(regexp_replace(regexp_replace(regexp_replace(" +
                "regexp_replace(lower($expr), '[^[:alnum:]]+', ' ', 'g'), " +
                "'\\m($filler)\\M', ' ', 'g'), " +
                "'\\m([[:alnum:]]{2,}[^s ])s\\M', '\\1', 'g'), ' +', ' ', 'g'))"
        }

        /**
         * Category words USDA puts before the food ("Fish, salmon, raw",
         * "Nuts, almonds"). A head match also looks past them, so `salmon`
         * finds "Fish, salmon, raw" as it finds "Salmon, raw". Not meats:
         * "Beef, bacon" is not what `bacon` means.
         */
        private const val CATEGORIES =
            "fish,shellfish,crustaceans,mollusks,nuts,seeds,spices,beverages,candies,cereals,snacks,oil,cheese"

        /**
         * The column order here is what [toFood] on a ResultSet reads.
         *
         * Candidates: names starting with the query, and full text over name
         * and brand. The typo fallback (trigram similarity of the whole name,
         * or of its best-matching words) only runs when those are fewer than
         * the limit, or include no generic food: with Branded Foods loaded a
         * misspelling like "avacado" still finds the products that spell it
         * that way, and a person typing it means the fruit. The `count(*)`
         * and `EXISTS` subqueries are uncorrelated, so Postgres evaluates them
         * once and skips the trigram scan entirely when they say so.
         *
         * Ranking, in order:
         *  1. direct matches before typo matches, except a typo rescue: a
         *     generic food whose words closely match the query (trigram word
         *     similarity 0.45 or more: "avacado" and "Avocado, raw"), when
         *     nothing generic matched directly and no brand is named;
         *  2. a match on the food's name before a match only through its brand
         *     ("brown rice" never offers the brand "Rice Browns" first);
         *  3. a named brand: the brand's words appear in the query as a run
         *     ("chobani greek yogurt", "coke zero"), the query has 4 or more
         *     characters (shorter is a word being typed), and no generic food
         *     is an exact or head match ("peanut butter" stays a food);
         *  4. generic before Branded;
         *  5. USDA's restaurant and brand rows in the generic tables
         *     ("CHICK-FIL-A, ...") after plain foods, unless the query names them;
         *  6. match tier (exact, head, whole-word prefix, full text, similar),
         *     verified, source tier, whole words, plain forms, similarity,
         *     popularity, shorter names.
         * Head includes the name's first two segments when they are exactly
         * the query's words in any order ("Rice, brown" for "brown rice").
         */
        private val SEARCH_SQL = """
            WITH generic AS (
                SELECT f.* FROM foods f
                WHERE f.tier = 1
                  AND (lower(f.name) LIKE ? OR f.search @@ to_tsquery('simple', ?))
            ),
            direct AS (
                SELECT * FROM generic
                UNION ALL
                SELECT f.* FROM foods f
                WHERE f.tier > 1
                  AND (? OR (SELECT count(*) FROM generic) < ?)
                  AND (lower(f.name) LIKE ? OR f.search @@ to_tsquery('simple', ?))
            ),
            candidates AS (
                SELECT * FROM direct
                UNION ALL
                SELECT f.* FROM foods f
                WHERE ((SELECT count(*) FROM direct) < ?
                       OR NOT EXISTS (SELECT 1 FROM direct d
                                      WHERE d.tier = 1 OR lower(d.brand) LIKE ANY (string_to_array(?, '|'))))
                  AND lower(f.name) OPERATOR(extensions.%) ?
                  AND NOT (lower(f.name) LIKE ? OR f.search @@ to_tsquery('simple', ?))
            ),
            p AS (
                SELECT string_to_array(?, '|') AS variants,
                       ?::text AS prefix_regex,
                       to_tsquery('simple', ?) AS prefix_ts,
                       to_tsquery('simple', ?) AS whole_ts,
                       string_to_array(?, '|') AS query_words,
                       ?::text AS normalized,
                       ?::boolean AS brandable,
                       string_to_array(?, '|') AS like_words,
                       ' ' || ${wordsOf("?::text")} || ' ' AS phrase
            ),
            named AS (
                SELECT c.*, lower(c.name) AS lname,
                       CASE WHEN split_part(lower(c.name), ', ', 1) = ANY ('{$CATEGORIES}'::text[])
                            THEN substr(lower(c.name), strpos(c.name, ', ') + 2)
                            ELSE lower(c.name)
                       END AS core
                FROM candidates c
            ),
            scored AS (
                SELECT n.id, n.source, n.source_id, n.name, n.brand, n.barcode,
                       n.kcal_100g, n.protein_100g, n.carbs_100g, n.fat_100g, n.fiber_100g,
                       n.micros::text AS micros, n.portions::text AS portions, n.popularity,
                       n.tier, n.verified,
                       CASE
                           WHEN n.lname = ANY (p.variants) THEN 0
                           WHEN btrim(split_part(n.lname, ',', 1)) = ANY (p.variants)
                             OR btrim(split_part(n.core, ',', 1)) = ANY (p.variants) THEN 1
                           WHEN btrim(split_part(n.core, ',', 1)) = ANY (p.query_words)
                             AND array_remove(regexp_split_to_array(substring(n.core FROM '^[^,]*(?:,[^,]*)?'),
                                                                    '[^[:alnum:]]+'), '') <@ p.query_words
                             AND to_tsvector('simple', substring(n.core FROM '^[^,]*(?:,[^,]*)?')) @@ p.whole_ts THEN 1
                           WHEN n.lname ~ p.prefix_regex THEN 2
                           WHEN n.search @@ p.prefix_ts THEN 3
                           ELSE 4
                       END AS match_tier,
                       n.search @@ p.whole_ts AS whole_words,
                       CASE WHEN p.brandable AND lower(n.brand) LIKE ANY (p.like_words)
                            THEN strpos(p.phrase, ' ' || ${wordsOf("n.brand")} || ' ') > 0
                            ELSE false
                       END AS brand_hit,
                       CASE WHEN strpos(n.name, ',') > 0 AND split_part(n.name, ',', 1) !~ '[a-z]{2}'
                                 AND split_part(n.name, ',', 1) ~ '[A-Z]{2}'
                            THEN strpos(p.phrase, ' ' || ${wordsOf("split_part(n.name, ',', 1)")} || ' ') = 0
                            ELSE false
                       END AS caps_unnamed,
                       n.lname ~ '\m(raw|nfs|plain|whole|brewed|cooked)\M' AS staple,
                       extensions.similarity(n.lname, p.normalized) AS sim,
                       p.prefix_ts AS q_prefix_ts,
                       p.normalized AS q_normalized
                FROM named n CROSS JOIN p
            ),
            ranked AS (
                SELECT s.*,
                       s.match_tier <= 2 OR (s.match_tier = 3
                           AND (s.brand IS NULL OR to_tsvector('simple', s.name) @@ s.q_prefix_ts)) AS name_hit,
                       s.brand_hit AND NOT bool_or(s.tier = 1 AND s.match_tier <= 1) OVER () AS brand_named,
                       s.tier = 1 AND s.match_tier = 4
                           AND extensions.word_similarity(s.q_normalized, lower(s.name)) >= 0.45
                           AND NOT bool_or(s.tier = 1 AND s.match_tier < 4) OVER ()
                           AND NOT bool_or(s.brand_hit) OVER () AS rescue
                FROM scored s
            )
            SELECT r.* FROM ranked r
            ORDER BY NOT (r.match_tier < 4 OR r.rescue),
                     (r.name_hit OR r.rescue) DESC,
                     r.brand_named DESC,
                     (r.tier > 1),
                     r.caps_unnamed,
                     r.match_tier,
                     r.verified DESC,
                     r.tier,
                     r.whole_words DESC,
                     r.staple DESC,
                     round(r.sim::numeric, 1) DESC,
                     r.popularity DESC,
                     length(r.name),
                     r.name
            LIMIT ?
        """.trimIndent()
    }
}

/**
 * Everything the search statement binds, derived once from a normalized
 * query. Only letters, digits and single spaces reach a tsquery or a regex, so
 * the user's text can never change the syntax of either.
 */
internal class SearchTerms private constructor(
    val normalized: String,
    words: List<String>,
) {
    /** The query as words, with its last word singular / plural: `boiled eggs` → `boiled eggs`, `boiled egg`. */
    val phraseVariants: List<String> = variantsOf(words.last()).map { (words.dropLast(1) + it).joinToString(" ") }

    /** `^(eggs|egg)($|[^[:alnum:]])`: the name starts with the query as whole words. */
    val wordPrefixRegex: String = "^(" + phraseVariants.joinToString("|") + ")($|[^[:alnum:]])"

    /** `LIKE 'eggs%'`, with LIKE's wildcards escaped. Only used to reach the trigram index. */
    val likePrefix: String = normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    /** `(eggs:* | egg:*) & (white:* | whites:*)`: every word starts some word of the name. */
    val prefixTsQuery: String = words.joinToString(" & ") { w ->
        variantsOf(w).joinToString(" | ", "(", ")") { "$it:*" }
    }

    /** `(eggs | egg) & (white | whites)`: every word appears whole. */
    val wholeWordTsQuery: String = words.joinToString(" & ") { w -> variantsOf(w).joinToString(" | ", "(", ")") }

    /** `chobani|chobanis|yogurt|yogurts`: the query's words and their variants. */
    val queryWords: String = words.flatMap(::variantsOf).distinct().joinToString("|")

    /** The query's words, space separated. The statement compares brands against them as a run. */
    val phrase: String = words.joinToString(" ")

    /**
     * `%chobani%|%yogurt%`: LIKE patterns for a brand that carries one of the
     * query's words, filler aside. A cheap test the statement runs before the
     * exact one, since a named brand has every one of its words in the query.
     */
    val likeWords: String = words.filter { it !in BRAND_FILLER }.flatMap(::variantsOf).distinct().joinToString("|") { "%$it%" }

    /**
     * Whether the query may name a brand: 4 or more letters and digits. One
     * to three is a word still being typed, and "avo" means avocado more
     * often than the brand "Avo's".
     */
    val canNameBrand: Boolean get() = length >= MIN_BRAND_QUERY

    /** Letters and digits in the query: `m&m` is 2. */
    val length: Int = words.sumOf { it.length }

    companion object {
        private const val MIN_BRAND_QUERY = 4

        /** What a brand may carry that nobody types ("Inc.", "Foods", the s of "Campbell's"). */
        val BRAND_FILLER = listOf(
            "the", "and", "of", "s", "inc", "llc", "ltd", "co", "corp", "corporation", "company", "companies",
            "foods", "food", "brand", "brands", "sales", "usa", "us", "international", "products", "group",
        )

        private val WORD = Regex("[\\p{L}\\p{N}]+")

        fun of(normalized: String): SearchTerms? {
            val words = WORD.findAll(normalized).map { it.value }.take(8).toList()
            return if (words.isEmpty()) null else SearchTerms(normalized, words)
        }

        /**
         * A word plus a naive singular or plural, because the `simple` text
         * config does no stemming and USDA names mix both ("Bananas, raw",
         * "Banana, raw").
         */
        fun variantsOf(word: String): List<String> {
            val variants = linkedSetOf(word)
            when {
                word.length > 4 && word.endsWith("ies") -> variants += word.dropLast(3) + "y"
                word.length > 4 && word.endsWith("oes") -> variants += word.dropLast(2)
                word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> variants += word.dropLast(1)
                word.length > 2 && !word.endsWith("s") && word.all { it.isLetter() } -> variants += word + "s"
            }
            return variants.toList()
        }
    }
}
