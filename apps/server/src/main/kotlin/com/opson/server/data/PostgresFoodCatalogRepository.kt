package com.opson.server.data

import com.opson.server.db.BarcodeLookupsTable
import com.opson.server.db.Database
import com.opson.server.db.FoodSearchEventsTable
import com.opson.server.db.FoodsTable
import com.opson.server.db.toJavaInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.BarcodeOutcome
import com.opson.server.domain.Food
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.FoodMatch
import com.opson.server.domain.FoodSource
import com.opson.server.domain.MatchTier
import com.opson.server.domain.Nutrients
import com.opson.server.domain.OpenFoodFactsProduct
import com.opson.server.domain.Portion
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.TextColumnType
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
 *  - head: the name's first comma segment is. USDA names read
 *    "Egg, whole, raw", so this is what makes "eggs" find eggs;
 *  - prefix: the name starts with the query as whole words ("banana" finds
 *    "Banana chips" but "toast" does not find "Toaster pastries");
 *  - full text: every query word starts a word of the name or brand;
 *  - similar: trigram similarity only, for typos.
 * Within a tier: generic before branded, names containing the query words as
 * whole words first, then the plain form of a food ("raw", "NFS", "plain",
 * "whole", "brewed", "cooked") over prepared dishes, so "eggs" offers
 * "Egg, whole, raw" before "Egg, creamed"; then similarity to the whole name rounded to one decimal
 * (so near-equal matches fall through to popularity), then popularity, then
 * shorter names.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresFoodCatalogRepository(
    private val database: Database,
    private val clock: Clock,
) : FoodCatalogRepository {

    override suspend fun search(query: String, limit: Int, record: Boolean): List<FoodMatch> =
        database.transaction {
            val normalized = FoodCatalogRepository.normalizeQuery(query)
            val terms = SearchTerms.of(normalized)
            val matches = if (terms == null) {
                emptyList()
            } else {
                runSearch(terms, limit.coerceIn(1, FoodCatalogRepository.MAX_LIMIT))
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
        val variants = terms.phraseVariants.joinToString("|")
        val args = listOf<Pair<IColumnType<*>, Any?>>(
            text to variants, // exact tier
            text to variants, // head tier
            text to terms.wordPrefixRegex, // prefix tier
            text to terms.prefixTsQuery, // full-text tier
            text to terms.wholeWordTsQuery, // whole-word bonus
            text to terms.normalized, // similarity
            text to terms.likePrefix, // where: prefix
            text to terms.prefixTsQuery, // where: full text
            text to terms.normalized, // where: trigram
            IntegerColumnType() to limit,
        )
        val results = mutableListOf<FoodMatch>()
        TransactionManager.current().exec(
            stmt = SEARCH_SQL,
            args = args,
            explicitStatementType = StatementType.SELECT,
        ) { rs ->
            while (rs.next()) {
                val tier = MatchTier.entries[rs.getInt("tier")]
                results += FoodMatch(rs.toFood(), tier, rs.getDouble("sim"))
            }
        }
        return results
    }

    override suspend fun findById(id: UUID): Food? = database.transaction {
        FoodsTable.selectAll().where { FoodsTable.id eq id }.singleOrNull()?.toFood()
    }

    override suspend fun findByBarcode(barcode: String): Food? = database.transaction {
        FoodsTable.selectAll().where { FoodsTable.barcode eq barcode }.singleOrNull()?.toFood()
    }

    override suspend fun upsertOpenFoodFactsProduct(product: OpenFoodFactsProduct): Food = database.transaction {
        FoodsTable.upsert(
            FoodsTable.sourceKind,
            FoodsTable.sourceId,
            // Keep the id a device may already hold, and the popularity users earned it.
            onUpdateExclude = listOf(FoodsTable.id),
        ) {
            it[id] = UUID.randomUUID()
            it[sourceKind] = FoodSource.OpenFoodFacts.wire
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
            .where { (FoodsTable.sourceKind eq FoodSource.OpenFoodFacts.wire) and (FoodsTable.sourceId eq product.barcode) }
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
        source = FoodSource.fromWire(this[FoodsTable.sourceKind]) ?: FoodSource.UsdaSr,
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
    )

    private fun ResultSet.toFood(): Food = Food(
        id = getObject("id", UUID::class.java),
        source = FoodSource.fromWire(getString("source")) ?: FoodSource.UsdaSr,
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
    )

    private fun ResultSet.nullableFloat(column: String): Double? {
        val value = getFloat(column)
        return if (wasNull()) null else value.clean()
    }

    @Serializable
    private data class PortionJson(val label: String, val grams: Double)

    internal companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        private val MICROS_SERIALIZER = MapSerializer(String.serializer(), Double.serializer())
        private val PORTIONS_SERIALIZER = ListSerializer(PortionJson.serializer())

        fun decodeMicros(json: String): Map<String, Double> = JSON.decodeFromString(MICROS_SERIALIZER, json)

        fun decodePortions(json: String): List<Portion> =
            JSON.decodeFromString(PORTIONS_SERIALIZER, json).map { Portion(it.label, it.grams) }

        /** The column order here is what [toFood] on a ResultSet reads. */
        private val SEARCH_SQL = """
            SELECT * FROM (
                SELECT f.id, f.source, f.source_id, f.name, f.brand, f.barcode,
                       f.kcal_100g, f.protein_100g, f.carbs_100g, f.fat_100g, f.fiber_100g,
                       f.micros::text AS micros, f.portions::text AS portions, f.popularity,
                       CASE
                           WHEN lower(f.name) = ANY (string_to_array(?, '|')) THEN 0
                           WHEN btrim(split_part(lower(f.name), ',', 1)) = ANY (string_to_array(?, '|')) THEN 1
                           WHEN lower(f.name) ~ ? THEN 2
                           WHEN f.search @@ to_tsquery('simple', ?) THEN 3
                           ELSE 4
                       END AS tier,
                       f.search @@ to_tsquery('simple', ?) AS whole_words,
                       lower(f.name) ~ '\m(raw|nfs|plain|whole|brewed|cooked)\M' AS staple,
                       extensions.similarity(lower(f.name), ?) AS sim
                FROM foods f
                WHERE lower(f.name) LIKE ?
                   OR f.search @@ to_tsquery('simple', ?)
                   OR lower(f.name) OPERATOR(extensions.%) ?
            ) m
            ORDER BY m.tier,
                     (m.source = 'off'),
                     m.whole_words DESC,
                     m.staple DESC,
                     round(m.sim::numeric, 1) DESC,
                     m.popularity DESC,
                     length(m.name),
                     m.name
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

    companion object {
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
