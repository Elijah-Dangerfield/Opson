package com.opson.server.data

import com.opson.server.domain.FoodMatch
import com.opson.server.domain.FoodTier
import com.opson.server.domain.MatchTier
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.TextColumnType
import java.util.Locale

/**
 * Name search over `off_foods`, the full Open Food Facts export (V12, about
 * 3.3 million products), and how its rows join the catalog's. Used only when
 * `FOOD_SEARCH_OFF_ENABLED` is on ([com.opson.server.config.FoodSourcesConfig]).
 *
 * The catalog statement scores every row a query matches. At 3.3 million rows
 * a common word matches tens of thousands and one or two letters hundreds of
 * thousands, which measured p99 5.7 s (docs/food-data-sources.md). So this
 * one bounds the candidates before it scores them:
 *  - queries under [MIN_QUERY] letters and digits don't search Open Food
 *    Facts at all (the catalog still answers them);
 *  - products named exactly the query, up to [CAP_EXACT], through the btree
 *    on lower(name) (V13), so an exact name is never crowded out;
 *  - products whose name or brand has every query word as a prefix, up to
 *    [CAP_WORDS]. The import writes rows most-scanned first, and a capped
 *    bitmap scan returns rows in physical order, so the cap keeps the
 *    products people scan most (scripts/import_off_foods.py);
 *  - both again among products sold in the caller's country, through the
 *    GIN on (countries_tags, search), so the country ranking has its own
 *    products to rank rather than whichever the global caps kept;
 *  - a typo fallback on trigram word similarity, up to [CAP_SIMILAR], only
 *    when those found fewer than the limit.
 * That is at most about 850 rows scored, whatever the query.
 * Then the same match tiers as the catalog, a match on the name before one
 * only through the brand, the caller's country first, whole words, and
 * scans. `scripts/bench_off_search.py` mirrors this statement; change both.
 */
internal object OpenFoodFactsSearch {
    const val MIN_QUERY = 3
    const val CAP_EXACT = 100
    const val CAP_WORDS = 300
    const val CAP_SIMILAR = 50

    fun searches(terms: SearchTerms): Boolean = terms.length >= MIN_QUERY

    fun args(terms: SearchTerms, limit: Int, countryTag: String?): List<Pair<IColumnType<*>, Any?>> {
        val text = TextColumnType()
        val int = IntegerColumnType()
        val variants = terms.phraseVariants.joinToString("|")
        return listOf(
            text to variants, // exact
            int to CAP_EXACT,
            text to terms.prefixTsQuery, // words
            int to CAP_WORDS,
            text to variants, // exact, sold in the caller's country
            text to countryTag,
            int to CAP_EXACT,
            text to terms.prefixTsQuery, // words, sold in the caller's country
            text to countryTag,
            int to CAP_WORDS,
            int to limit, // typo fallback below this many hits
            text to terms.normalized,
            int to CAP_SIMILAR,
            text to variants, // p.variants
            text to terms.wordPrefixRegex, // p.prefix_regex
            text to terms.prefixTsQuery, // p.prefix_ts
            text to terms.wholeWordTsQuery, // p.whole_ts
            text to countryTag, // p.country
            text to terms.normalized, // p.normalized
            int to limit,
        )
    }

    /** The column names here are what `ResultSet.toFood` in the repository reads. */
    val SQL = """
        WITH exact AS (
            SELECT o.id FROM off_foods o
            WHERE lower(o.name) = ANY (string_to_array(?, '|'))
            LIMIT ?
        ),
        words AS (
            SELECT o.id FROM off_foods o
            WHERE o.search @@ to_tsquery('simple', ?)
            LIMIT ?
        ),
        exact_here AS (
            SELECT o.id FROM off_foods o
            WHERE lower(o.name) = ANY (string_to_array(?, '|'))
              AND o.countries_tags @> ARRAY[?::text]
            LIMIT ?
        ),
        words_here AS (
            SELECT o.id FROM off_foods o
            WHERE o.search @@ to_tsquery('simple', ?)
              AND o.countries_tags @> ARRAY[?::text]
            LIMIT ?
        ),
        hits AS (
            SELECT id FROM exact UNION SELECT id FROM words
            UNION SELECT id FROM exact_here UNION SELECT id FROM words_here
        ),
        typos AS (
            SELECT o.id FROM off_foods o
            WHERE (SELECT count(*) FROM hits) < ?
              AND ? OPERATOR(extensions.<%) lower(o.name)
            LIMIT ?
        ),
        p AS (
            SELECT string_to_array(?, '|') AS variants,
                   ?::text AS prefix_regex,
                   to_tsquery('simple', ?) AS prefix_ts,
                   to_tsquery('simple', ?) AS whole_ts,
                   ?::text AS country,
                   ?::text AS normalized
        ),
        scored AS (
            SELECT o.id, o.source, o.barcode AS source_id, o.name, o.brand, o.barcode,
                   o.kcal_100g, o.protein_100g, o.carbs_100g, o.fat_100g, o.fiber_100g,
                   o.micros::text AS micros, o.portions::text AS portions, o.tier, o.verified, o.scans,
                   CASE
                       WHEN lower(o.name) = ANY (p.variants) THEN 0
                       WHEN btrim(split_part(lower(o.name), ',', 1)) = ANY (p.variants) THEN 1
                       WHEN lower(o.name) ~ p.prefix_regex THEN 2
                       WHEN o.search @@ p.prefix_ts THEN 3
                       ELSE 4
                   END AS match_tier,
                   o.brand IS NULL OR to_tsvector('simple', o.name) @@ p.prefix_ts AS name_words,
                   o.search @@ p.whole_ts AS whole_words,
                   coalesce(o.countries_tags @> ARRAY[p.country], false) AS in_country,
                   extensions.similarity(lower(o.name), p.normalized) AS sim
            FROM off_foods o CROSS JOIN p
            WHERE o.id IN (SELECT id FROM hits UNION SELECT id FROM typos)
        )
        SELECT s.* FROM scored s
        ORDER BY (s.match_tier = 4),
                 (s.match_tier <= 2 OR (s.match_tier = 3 AND s.name_words)) DESC,
                 s.in_country DESC,
                 s.match_tier,
                 s.whole_words DESC,
                 s.scans DESC,
                 round(s.sim::numeric, 1) DESC,
                 length(s.name),
                 s.name
        LIMIT ?
    """.trimIndent()

    val BY_ID_SQL = """
        SELECT o.id, o.source, o.barcode AS source_id, o.name, o.brand, o.barcode,
               o.kcal_100g, o.protein_100g, o.carbs_100g, o.fat_100g, o.fiber_100g,
               o.micros::text AS micros, o.portions::text AS portions, o.tier, o.verified
        FROM off_foods o WHERE o.id = ?
    """.trimIndent()

    /**
     * The catalog's results with Open Food Facts rows worked in. An Open Food
     * Facts row goes after every generic food that matched directly and every
     * other row with the same or a better match tier, and keeps its own order;
     * a typo match goes last. A product the catalog already has (same
     * barcode, from Branded Foods) is left out.
     */
    fun merge(catalog: List<FoodMatch>, openFoodFacts: List<FoodMatch>, limit: Int): List<FoodMatch> {
        val barcodes = catalog.mapNotNullTo(HashSet()) { it.food.barcode }
        // (match, came from off_foods). `foods` has source "off" rows too, the barcode cache.
        val merged = catalog.mapTo(mutableListOf()) { it to false }
        for (off in openFoodFacts) {
            if (off.food.barcode in barcodes) continue
            val after = if (off.tier == MatchTier.Similar) {
                merged.lastIndex
            } else {
                merged.indexOfLast { (row, fromOff) -> fromOff || row.staysAbove(off) }
            }
            merged.add(after + 1, off to true)
        }
        return merged.take(limit).map { it.first }
    }

    private fun FoodMatch.staysAbove(off: FoodMatch): Boolean =
        tier != MatchTier.Similar && (food.tier == FoodTier.Reference || tier <= off.tier)

    /**
     * Open Food Facts' tag for an ISO 3166-1 alpha-2 code: `FR` → `en:france`,
     * `US` → `en:united-states`. Their tags are the English name, lowercased
     * and hyphenated, with a few names of their own.
     */
    fun countryTag(iso2: String?): String? {
        val code = iso2?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all(Char::isLetter) } ?: return null
        TAG_OVERRIDES[code]?.let { return "en:$it" }
        val name = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH)
        if (name.isBlank() || name.equals(code, ignoreCase = true)) return null
        val slug = name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return slug.takeIf { it.isNotEmpty() }?.let { "en:$it" }
    }

    private val TAG_OVERRIDES = mapOf(
        "CZ" to "czech-republic",
        "HK" to "hong-kong",
        "MO" to "macau",
        "PS" to "state-of-palestine",
    )
}
