package com.opson.server.domain

import java.util.UUID

/**
 * How much a source's numbers can be trusted, best first. Wire values match
 * `foods.tier` (V10). Tiers 1 to 3 are [verified][FoodTier.verified]:
 * someone accountable stands behind the numbers.
 */
enum class FoodTier(val wire: Int) {
    /** USDA Foundation, SR Legacy, FNDDS and NEVO: generic foods, lab or survey data. */
    Reference(1),

    /** USDA Branded Foods: the manufacturer's nutrition label. */
    Label(2),

    /** Paid sources cached under a licence (Nutritionix, later NCCDB). */
    Licensed(3),

    /** Open Food Facts, and later foods people submit. */
    Community(4),
    ;

    val verified: Boolean get() = this != Community

    companion object {
        fun fromWire(value: Int): FoodTier = entries.firstOrNull { it.wire == value } ?: Community
    }
}

/** Where a catalog food came from. Wire values match the `foods.source` CHECK (V20). */
enum class CatalogSource(val wire: String, val tier: FoodTier) {
    UsdaFoundation("usda_foundation", FoodTier.Reference),
    UsdaSr("usda_sr", FoodTier.Reference),
    UsdaFndds("usda_fndds", FoodTier.Reference),

    /** The Dutch food composition table (RIVM), from scripts/import_nevo_foods.py. Search gates it ([SearchFilter.nevo]). */
    Nevo("nevo", FoodTier.Reference),
    UsdaBranded("usda_branded", FoodTier.Label),
    Nutritionix("nutritionix", FoodTier.Licensed),
    OpenFoodFacts("off", FoodTier.Community),

    /**
     * A food someone added from photos of its pack (V25). Tier 4 like Open
     * Food Facts until a reviewer approves it, which sets `verified`; only its
     * submitter sees it before then.
     */
    UserSubmitted("user_submitted", FoodTier.Community),
    ;

    companion object {
        fun fromWire(value: String): CatalogSource? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * A household measure: `{label: "1 cup, sliced", grams: 150}`. [grams] is
 * null for a serving the label states only by volume ("1 can (355 ml)"):
 * there is no honest gram weight without a density, so the client falls back
 * to weighing.
 */
data class Portion(val label: String, val grams: Double?)

/**
 * A catalog food. Nutrients are per 100 g and null when the source does not
 * say. For a branded product labelled in ml they are per 100 ml, which is
 * what the label states. [micros] keys carry their unit (`sodium_mg`,
 * `vitamin_d_ug`); see scripts/import_usda_foods.py for the full list.
 */
data class Food(
    val id: UUID,
    val source: CatalogSource,
    val sourceId: String,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val per100g: Nutrients,
    val micros: Map<String, Double>,
    val portions: List<Portion>,
    val tier: FoodTier = source.tier,
    val verified: Boolean = tier.verified,
    /**
     * Where review stands, for a food someone added from its pack ([CatalogSource.UserSubmitted]).
     * Every other row is approved. Only the submitter is ever handed a pending or rejected one.
     */
    val reviewStatus: SubmissionStatus = SubmissionStatus.Approved,
    /** What the energy guard did to the stored numbers on the way out ([FoodEnergy.served]); empty for most foods. */
    val energyFlags: Set<EnergyFlag> = emptySet(),
    /** The reviewer's correction laid over the source's numbers, when there is one ([FoodCorrection.applyTo]). */
    val correction: CorrectionMark? = null,
    /**
     * A person checked it against its pack, or a reviewer stood behind it ([FoodCheck]): the
     * app's Verified mark. Worked out as it is served, never stored.
     */
    val checked: Boolean = false,
)

/**
 * Which of a barcode's foods to answer with. [candidates] are every row for
 * the code, best tier first (a Branded row, then an Open Food Facts copy).
 * The best tier wins unless the energy guard caught it out and a lower tier
 * did not: USDA Branded has Monster Energy Zero Ultra at 0 kcal (its label
 * rounded 2 kcal a serving down to 0), Open Food Facts at 10 kcal a can.
 */
object BarcodeChoice {
    private val DOUBTFUL = setOf(EnergyFlag.ZeroWithMacros, EnergyFlag.EnergyWithoutMacros)

    fun best(candidates: List<Food>): Food? =
        candidates.firstOrNull { it.energyFlags.none(DOUBTFUL::contains) } ?: candidates.firstOrNull()

    /**
     * Every candidate, [best] first and the rest in their own order, each row
     * once (the same source and id twice is one row). What a scan offers to
     * choose from, so the first is always the food a single answer would be.
     */
    fun ordered(candidates: List<Food>): List<Food> {
        val distinct = candidates.distinctBy { it.source to it.id }
        val best = best(distinct) ?: return emptyList()
        return listOf(best) + distinct.filterNot { it.source == best.source && it.id == best.id }
    }
}

/**
 * A food's category, from USDA's own (V17). Wire values match the
 * `foods_category_check` CHECK. Most foods have none.
 */
enum class FoodCategory(val wire: String) {
    Restaurant("restaurant"),
    Beverage("beverage"),
    BabyFood("baby_food"),
    Supplement("supplement"),
}

/** How well a search result matched the query, best first. */
enum class MatchTier { Exact, Head, Prefix, FullText, Similar }

/**
 * The Logger's search options (`category`, `source`, `sort` on
 * `/v1/foods/search`). [None] is the search 0.1 calls, and must stay exactly
 * that: same statement, same results.
 *
 * [nevo] lets NEVO's foods in. RIVM allows them only with its citation, so
 * they go to a client that shows it (`credits=nevo`), never to 0.1, which
 * can't. Describe searches with [None], so it never picks them either.
 */
data class SearchFilter(
    val category: SearchCategory? = null,
    val source: SearchSource? = null,
    val sort: SearchSort = SearchSort.BestMatch,
    val nevo: Boolean = false,
) {
    /** Whether Open Food Facts' own table can answer: it has no categories, and `usda` excludes it. */
    val allowsOpenFoodFacts: Boolean get() = category == null && source != SearchSource.Usda

    companion object {
        val None = SearchFilter()
    }
}

/** The `category` param. [Common] is tiers 1 and 2, the generic and label foods. */
enum class SearchCategory(val wire: String) {
    Restaurant("restaurant"),
    Beverage("beverage"),
    Common("common"),
    ;

    companion object {
        fun fromWire(value: String): SearchCategory? = entries.firstOrNull { it.wire == value }
    }
}

/** The `source` param. */
enum class SearchSource(val wire: String) {
    Usda("usda"),
    OpenFoodFacts("off"),
    ;

    companion object {
        fun fromWire(value: String): SearchSource? = entries.firstOrNull { it.wire == value }
    }
}

/** The `sort` param. [Name] orders the best matches A to Z; it doesn't page through every match by name. */
enum class SearchSort(val wire: String) {
    BestMatch("best"),
    Name("name"),
    ;

    companion object {
        fun fromWire(value: String): SearchSort? = entries.firstOrNull { it.wire == value }
    }
}

data class FoodMatch(val food: Food, val tier: MatchTier, val similarity: Double)

/**
 * The shared catalog. Search ranking (docs/system-design.md §7): a query that
 * names a product's brand ranks that brand first; otherwise generic (reference
 * tier) foods come before branded ones, and within that, exact name, then
 * prefix, then full text, then trigram similarity; verified and lower source
 * tiers first at the same match; popularity breaks ties.
 */
interface FoodCatalogRepository {
    /**
     * Ranked matches for [query]. When [record] is true, one anonymous
     * `food_search_events` row is written with the normalized query and the
     * result count (no user id, by design).
     *
     * [openFoodFacts] asks for `off_foods` rows as well. They are only added
     * when the server's `FOOD_SEARCH_OFF_ENABLED` is on, ranked after generic
     * and Branded rows of the same match, products sold in [country] (ISO
     * 3166-1 alpha-2, when known) first. Rank by country, never filter by it.
     *
     * [filter] narrows by category or source and can order by name;
     * [SearchFilter.None] is the unfiltered search.
     */
    suspend fun search(
        query: String,
        limit: Int,
        record: Boolean = true,
        country: String? = null,
        openFoodFacts: Boolean = false,
        filter: SearchFilter = SearchFilter.None,
    ): List<FoodMatch>

    /** Whether search can add Open Food Facts' own table (`FOOD_SEARCH_OFF_ENABLED`). */
    val searchesOpenFoodFacts: Boolean get() = false

    suspend fun findById(id: UUID): Food?

    /**
     * Each of [ids] as [findById] would serve it, for a page that names many foods at once (a
     * recipe's ingredients). An id with no food is left out. The database does it in one
     * transaction, whatever the count.
     */
    suspend fun findByIds(ids: Collection<UUID>): Map<UUID, Food> =
        ids.distinct().mapNotNull { id -> findById(id)?.let { id to it } }.toMap()

    /**
     * The food for a scanned code in any of its equivalent forms
     * ([Barcodes.equivalents]). When two rows carry the same product under
     * different forms (an Open Food Facts copy and the Branded Foods row),
     * the better tier wins, unless its energy is in doubt and the other's
     * is not ([BarcodeChoice]). With `FOOD_SEARCH_OFF_ENABLED` on, a code
     * `foods` lacks is looked up in `off_foods` too, so only a product the
     * monthly export lacks costs a live Open Food Facts call.
     */
    suspend fun findByBarcode(barcode: String): Food?

    /**
     * Every food for the code, as [findByBarcode] weighs them: its answer
     * first, then the other rows best tier first ([BarcodeChoice.ordered]).
     * Empty when the catalog has none. What a scan that asks for every match
     * is given (`?matches=all`).
     */
    suspend fun findAllByBarcode(barcode: String): List<Food> = listOfNotNull(findByBarcode(barcode))

    /**
     * Insert or refresh a product an outside source returned, keyed on
     * `(source, source_id = barcode)`. Returns the stored row, whose id is
     * stable across refreshes.
     */
    suspend fun upsertExternalProduct(source: CatalogSource, product: ExternalProduct): Food

    /**
     * Drops the search answers this server keeps ([SearchResultCache]), so a
     * correction shows in search here at once. Other machines catch up
     * within `FOOD_SEARCH_CACHE_SECONDS`.
     */
    fun forgetCachedSearches() {}

    /** One anonymous `barcode_lookups` row. */
    suspend fun recordBarcodeLookup(barcode: String, outcome: BarcodeOutcome)

    companion object {
        const val DEFAULT_LIMIT = 25
        const val MAX_LIMIT = 50
        const val MAX_QUERY = 100

        /** Lowercase, trimmed, single-spaced, capped. What search matches on and what gets recorded. */
        fun normalizeQuery(raw: String): String =
            raw.lowercase().trim().replace(Regex("\\s+"), " ").take(MAX_QUERY)
    }
}

enum class BarcodeOutcome(val wire: String) {
    Catalog("catalog"),
    OpenFoodFacts("off"),
    Nutritionix("nutritionix"),
    Miss("miss"),
    Error("error"),
}

/** A product as an outside source describes it, already mapped to per-100 g values. */
data class ExternalProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val per100g: Nutrients,
    val micros: Map<String, Double>,
    val portions: List<Portion>,
)

/** What one outside source said about one barcode. */
sealed interface FoodSourceResult {
    data class Found(val product: ExternalProduct) : FoodSourceResult
    data object NotFound : FoodSourceResult

    /** Timeout, network failure, a 5xx or a rate limit. Not cached, not a miss. */
    data class Unavailable(val cause: Throwable?) : FoodSourceResult
}

/** Open Food Facts `api/v2/product/{code}.json`. No key; descriptive User-Agent. */
interface OpenFoodFactsClient {
    suspend fun product(barcode: String): FoodSourceResult
}

/**
 * An outside source asked when a barcode is not in our catalog. Our own
 * catalog serves every search and every scan it can; these only fill gaps,
 * one product at a time.
 */
interface FoodSource {
    /** The `foods.source` a cached answer is stored under, and so its tier. */
    val catalogSource: CatalogSource

    /** What `barcode_lookups` records when this source answers. */
    val outcome: BarcodeOutcome

    /**
     * Whether an answer may be stored in `foods`. False for a source whose
     * terms do not allow keeping its data: its answers are served once and
     * asked for again next time.
     */
    val cacheable: Boolean

    suspend fun lookup(barcode: String): FoodSourceResult
}

/** The outside sources that are switched on, best tier first. */
fun interface LiveFoodSources {
    fun inTierOrder(): List<FoodSource>
}

sealed interface BarcodeLookupResult {
    /**
     * [food] is the answer; [matches] every food the lookup found for the
     * code, [food] first. Only the catalog finds more than one: an outside
     * source is asked on a miss, and the first that knows the code answers.
     */
    data class Found(val food: Food, val matches: List<Food> = listOf(food)) : BarcodeLookupResult
    data object NotFound : BarcodeLookupResult
    data object Unavailable : BarcodeLookupResult
}

/** Catalog first, then each enabled outside source in tier order, else not found. */
interface BarcodeLookup {
    suspend fun lookup(barcode: String): BarcodeLookupResult

    companion object {
        /** EAN-8 through GTIN-14, digits only. */
        fun isValidBarcode(code: String): Boolean = code.length in 8..14 && code.all { it in '0'..'9' }
    }
}

/**
 * The forms one product code can arrive or be stored in. Leading zeros never
 * change a GTIN, so a UPC-A (12 digits), its EAN-13 and its GTIN-14 are the
 * same product: Branded Foods rows store the 14-digit form, Open Food Facts
 * rows the code as it was scanned. An 8-digit code is either an EAN-8 or a
 * UPC-E, the compressed UPC-A printed on small US packs, so both readings are
 * offered.
 */
object Barcodes {
    fun equivalents(code: String): List<String> {
        val forms = linkedSetOf(code)
        if (!BarcodeLookup.isValidBarcode(code)) return forms.toList()
        val significant = code.trimStart('0')
        listOf(8, 12, 13, 14).filter { significant.length <= it }.forEach { forms += significant.padStart(it, '0') }
        upcEToUpcA(code)?.let { upcA -> listOf(12, 13, 14).forEach { forms += upcA.padStart(it, '0') } }
        return forms.toList()
    }

    /** GS1 check digit for the digits before it. */
    fun checkDigit(body: String): Int {
        val total = body.reversed().withIndex().sumOf { (i, c) -> (c - '0') * if (i % 2 == 0) 3 else 1 }
        return (10 - total % 10) % 10
    }

    /** The 12-digit UPC-A an 8-digit UPC-E (number system 0 or 1) stands for, or null. */
    fun upcEToUpcA(code: String): String? {
        if (code.length != 8 || code.any { it !in '0'..'9' } || code[0] !in "01") return null
        val d = code.substring(1, 7)
        val body = when (d[5]) {
            '0', '1', '2' -> d.substring(0, 2) + d[5] + "0000" + d.substring(2, 5)
            '3' -> d.substring(0, 3) + "00000" + d.substring(3, 5)
            '4' -> d.substring(0, 4) + "00000" + d[4]
            else -> d.substring(0, 5) + "0000" + d[5]
        }
        val upcA = code[0] + body + code[7]
        return upcA.takeIf { checkDigit(it.dropLast(1)) == it.last() - '0' }
    }

    /**
     * The 14-digit GTIN a scanned code stands for, or null when it has none.
     * The same rule as `normalize_barcode` in scripts/import_off_foods.py, so
     * a miss lines up with the rows the importers store: an 8-digit code is
     * read as a UPC-E first, then any 8, 12, 13 or 14 digit code with a valid
     * check digit is zero-padded to 14. A code whose check digit fails, or a
     * placeholder that is mostly zeros, has no GTIN.
     */
    fun gtin14(code: String): String? {
        if (!BarcodeLookup.isValidBarcode(code)) return null
        val gtin = upcEToUpcA(code)?.padStart(GTIN_LENGTH, '0')
            ?: code.takeIf { it.length in GTIN_LENGTHS && checkDigit(it.dropLast(1)) == it.last() - '0' }
                ?.padStart(GTIN_LENGTH, '0')
        return gtin?.takeIf { it.trimStart('0').length >= MIN_SIGNIFICANT_DIGITS }
    }

    private const val GTIN_LENGTH = 14
    private val GTIN_LENGTHS = setOf(8, 12, 13, 14)

    /** As the importers: no assigned GTIN is mostly zeros, so "00000000000000" and friends are placeholders. */
    private const val MIN_SIGNIFICANT_DIGITS = 7
}
