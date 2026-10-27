package com.opson.server.data

import com.opson.server.di.ServerScope
import com.opson.server.domain.Brand
import com.opson.server.domain.Brands
import com.opson.server.domain.CommonFood
import com.opson.server.domain.CommonFoods
import com.opson.server.domain.DescribeConfidence
import com.opson.server.domain.DescribeParser
import com.opson.server.domain.DescribeUnit
import com.opson.server.domain.DescribedItem
import com.opson.server.domain.Food
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.FoodDescriber
import com.opson.server.domain.FoodMatch
import com.opson.server.domain.FoodTier
import com.opson.server.domain.MatchTier
import com.opson.server.domain.Nutrients
import com.opson.server.domain.ParsedPhrase
import com.opson.server.domain.Portion
import com.opson.server.domain.PortionSize
import com.opson.server.domain.ServingSpec
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.round
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Turns [DescribeParser] phrases into items, each with the food it most
 * likely means, the next best foods to swap to, and an amount.
 *
 * Which food, in order:
 *  1. a restaurant the words named ("from Chick-fil-A"): its own foods,
 *     when the catalog has one whose name carries the food's words;
 *  2. a bare everyday word ([CommonFoods]): "toast" is plain toast, "eggs"
 *     are eggs, not French toast or pickled eggs;
 *  3. search, generic foods first as search ranks them, preferring a food
 *     whose name adds no preparation the person didn't say (fried, dried,
 *     pickled, French...). A phrase nothing matches is tried again without
 *     its leading words ("grilled nuggets" → "nuggets").
 * A run of foods with no "and" between them ("eggs bacon toast", how speech
 * recognition often writes it) is split into its foods when every word is an
 * everyday food and the whole run is not itself a food.
 *
 * How much, in order:
 *  1. a mass unit ("200 g", "3 oz", "a handful");
 *  2. a portion whose label names the unit or size ("1 cup, sliced", "1 large");
 *  3. a household measure the food has no portion for ("a bowl" is a cup and a half);
 *  4. a weight the photo estimate guessed;
 *  5. the amount "some" means: the [CommonFood]'s serving, or one of the food's
 *     typical portion ("1 banana", "1 medium", "1 piece"), times the count said.
 * Each step down lowers [DescribeConfidence]. Searches here are not recorded
 * as `food_search_events`: they are not something a person typed into search.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class CatalogFoodDescriber(
    private val catalog: FoodCatalogRepository,
    private val clock: Clock = Clock.System,
) : FoodDescriber {

    private val named = ConcurrentHashMap<String, Pair<Food?, Instant>>()

    override suspend fun describe(text: String): List<DescribedItem> = describePhrases(DescribeParser.parse(text))

    /** Matches phrases built elsewhere (a photo estimate) exactly as typed text is matched. */
    suspend fun describePhrases(phrases: List<ParsedPhrase>): List<DescribedItem> =
        joinWith(phrases).flatMap { splitRunOn(it) }.take(DescribeParser.MAX_PHRASES).map { describeOne(it) }

    private suspend fun describeOne(phrase: ParsedPhrase): DescribedItem {
        phrase.brand?.let { brand -> fromBrand(phrase, brand)?.let { return it } }
        CommonFoods.lookup(phrase.foodText)?.let { common -> fromCommon(phrase, common, MatchTier.Exact)?.let { return it } }
        val matches = search(phrase.foodText)
        val pick = choose(phrase.foodText, matches, phrase.brand)
        if (pick != null && covers(phrase.foodText, pick.food)) {
            return matched(phrase, pick.food, pick.tier, serving = null, alternatives = alternativesTo(phrase.foodText, pick.food, matches))
        }
        // Nothing carries every word said: the everyday food the words end in ("grilled nuggets" is nuggets)
        // is a better guess than a stray match on one word, and the stray match is still one tap away.
        everydayTail(phrase.foodText)?.let { common -> fromCommon(phrase, common, MatchTier.Similar, matches)?.let { return it } }
        if (pick != null) {
            return matched(phrase, pick.food, pick.tier, serving = null, alternatives = alternativesTo(phrase.foodText, pick.food, matches))
        }
        val trimmed = searchShorter(phrase.foodText)
        val fallback = choose(phrase.foodText, trimmed, phrase.brand) ?: return unmatched(phrase)
        return matched(phrase, fallback.food, MatchTier.Similar, serving = null, alternatives = alternativesTo(phrase.foodText, fallback.food, trimmed))
    }

    /**
     * "a Whopper with cheese" is one food the catalog knows by that name, not
     * a Whopper and a slice of cheese. An addition joins the food before it
     * when that food isn't an everyday one and a catalog food carries both.
     * "Coffee with milk" stays two lines: coffee is everyday.
     */
    private suspend fun joinWith(phrases: List<ParsedPhrase>): List<ParsedPhrase> {
        val joined = mutableListOf<ParsedPhrase>()
        for (phrase in phrases) {
            val previous = joined.lastOrNull()
            if (phrase.addition && previous != null && !previous.addition && CommonFoods.lookup(previous.foodText) == null) {
                val combined = "${previous.foodText} with ${phrase.foodText}"
                val query = previous.brand?.let { "${it.search} $combined" } ?: combined
                if (search(query).any { covers(combined, it.food) }) {
                    joined[joined.lastIndex] = previous.copy(text = "${previous.text} with ${phrase.text}", foodText = combined)
                    continue
                }
            }
            joined += phrase
        }
        return joined
    }

    /** The everyday food the words end in: "grilled nuggets" is nuggets, "iced oat milk latte" an oat milk latte. */
    private fun everydayTail(foodText: String): CommonFood? {
        val words = foodText.split(' ').filter { it.isNotBlank() }
        return (1 until words.size).firstNotNullOfOrNull { CommonFoods.lookup(words.drop(it).joinToString(" ")) }
    }

    /** A food from the named restaurant whose name carries the food's words, if the catalog has one. */
    private suspend fun fromBrand(phrase: ParsedPhrase, brand: Brand): DescribedItem? {
        val withBrand = search("${brand.search} ${phrase.foodText}")
        val plain = search(phrase.foodText)
        val brandFoods = (withBrand + plain).distinctBy { it.food.id }
            .filter { it.food.mentions(brand) && covers(phrase.foodText, it.food) }
        val pick = brandFoods.firstOrNull() ?: return null
        val alternatives = alternativesTo(phrase.foodText, pick.food, brandFoods + plain)
        return matched(phrase, pick.food, pick.tier, serving = null, alternatives = alternatives)
    }

    /**
     * [common]'s food, its curated variants first among the alternatives,
     * then what search found for the words ([found], or a search when the
     * curated ones are too few).
     */
    private suspend fun fromCommon(
        phrase: ParsedPhrase,
        common: CommonFood,
        tier: MatchTier,
        found: List<FoodMatch>? = null,
    ): DescribedItem? {
        val food = namedFood(common.name) ?: return null
        val curated = common.alternatives.mapNotNull { namedFood(it) }
        val searched = when {
            found != null -> found
            curated.size < MAX_ALTERNATIVES -> search(phrase.foodText)
            else -> emptyList()
        }
        val alternatives = (curated + alternativesTo(phrase.foodText, food, searched))
            .filter { it.id != food.id }
            .distinctBy { it.name.lowercase() }
            .take(MAX_ALTERNATIVES)
        val serving = if (phrase.addition) common.asAddition ?: common.serving else common.serving
        return matched(phrase, food, tier, serving, alternatives)
    }

    /**
     * "eggs bacon toast" → eggs, bacon, toast. Only when every word belongs to
     * an everyday food, and the whole run is not a food the catalog knows by
     * that name ("chicken salad" stays one food).
     */
    private suspend fun splitRunOn(phrase: ParsedPhrase): List<ParsedPhrase> {
        if (phrase.brand != null || CommonFoods.lookup(phrase.foodText) != null) return listOf(phrase)
        val pieces = commonCover(phrase.foodText.split(' ').filter { it.isNotBlank() }) ?: return listOf(phrase)
        if (pieces.size < 2) return listOf(phrase)
        val whole = search(phrase.foodText).firstOrNull()
        if (whole != null && whole.tier <= MatchTier.Prefix && whole.food.tier == FoodTier.Reference) return listOf(phrase)
        return pieces.mapIndexed { index, words ->
            if (index == 0) {
                phrase.copy(text = words, foodText = words)
            } else {
                ParsedPhrase(text = words, quantity = null, unit = null, size = null, foodText = words, addition = phrase.addition)
            }
        }
    }

    /** The words as a run of everyday foods, longest first, or null when a word belongs to none. */
    private fun commonCover(words: List<String>): List<String>? {
        val pieces = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            val end = (words.size downTo i + 1).firstOrNull { CommonFoods.lookup(words.subList(i, it).joinToString(" ")) != null }
                ?: return null
            pieces += words.subList(i, end).joinToString(" ")
            i = end
        }
        return pieces
    }

    private suspend fun search(query: String): List<FoodMatch> =
        catalog.search(query, limit = SEARCH_LIMIT, record = false)

    /** A search for the last words of [foodText], fewer each time, until something matches. */
    private suspend fun searchShorter(foodText: String): List<FoodMatch> {
        val words = foodText.split(' ').filter { it.isNotBlank() }
        for (from in 1 until words.size) {
            val shorter = words.drop(from).joinToString(" ")
            if (shorter.length < MIN_FALLBACK_QUERY) break
            search(shorter).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return emptyList()
    }

    /**
     * The match to describe [foodText] with: of the top results in the same
     * league as the first (generic, or not), the earliest that carries every
     * word said, belongs to no restaurant other than the one said, and whose
     * name adds the fewest preparations the person didn't say.
     */
    private fun choose(foodText: String, matches: List<FoodMatch>, brand: Brand? = null): FoodMatch? {
        val top = matches.firstOrNull() ?: return null
        val said = words(foodText)
        val pool = matches.take(CHOOSE_POOL).filter { (it.food.tier == FoodTier.Reference) == (top.food.tier == FoodTier.Reference) }
        return pool.withIndex().minWith(
            compareBy(
                { !covers(foodText, it.value.food) },
                { it.value.food.fromAnotherRestaurant(brand) },
                { unsaid(it.value.food, said) },
                { it.index },
            ),
        ).value
    }

    /**
     * The next best foods for [foodText], most of the words said first, then
     * search order. A food that carries under half of them ("Cheese, with
     * wine" for a quarter pounder with cheese) is noise, not an alternative.
     */
    private fun alternativesTo(foodText: String, food: Food, matches: List<FoodMatch>): List<Food> =
        matches.map { it.food }
            .filter { it.id != food.id && !it.name.equals(food.name, ignoreCase = true) }
            .distinctBy { it.name.lowercase() }
            .map { it to coverage(foodText, it) }
            .filter { (_, share) -> share >= MIN_ALTERNATIVE_COVERAGE }
            .sortedByDescending { (_, share) -> share }
            .map { (alternative, _) -> alternative }
            .take(MAX_ALTERNATIVES)

    /** A food by its exact USDA name, kept for a few hours: these names are a short, fixed list. */
    private suspend fun namedFood(name: String): Food? {
        val now = clock.now()
        named[name]?.let { (food, at) -> if (now - at < NAMED_TTL) return food }
        val food = catalog.search(name, limit = NAMED_LIMIT, record = false)
            .firstOrNull { it.food.name.equals(name, ignoreCase = true) && it.food.tier == FoodTier.Reference }
            ?.food
        named[name] = food to now
        return food
    }

    private fun unmatched(phrase: ParsedPhrase): DescribedItem {
        val quantity = phrase.quantity ?: 1.0
        val grams = phrase.unit?.gramsPerUnit?.let { it * quantity } ?: phrase.gramsHint
        return DescribedItem(
            text = phrase.text,
            name = phrase.foodText,
            foodId = null,
            quantity = quantity,
            unit = phrase.unit,
            amountText = saidAmount(phrase),
            portionLabel = null,
            grams = grams?.let(::round1),
            nutrients = null,
            micros = null,
            confidence = DescribeConfidence.Low,
        )
    }

    private fun matched(phrase: ParsedPhrase, food: Food, tier: MatchTier, serving: ServingSpec?, alternatives: List<Food>): DescribedItem {
        val resolved = resolveGrams(phrase, food, serving)
        val grams = round1(resolved.grams)
        val factor = grams / 100.0
        val portion = resolved.portion
        val count = portion?.let { round(grams / perUnit(it) * 100) / 100 } ?: phrase.quantity ?: 1.0
        return DescribedItem(
            text = phrase.text,
            name = food.name,
            foodId = food.id,
            quantity = count,
            unit = phrase.unit,
            amountText = portion?.let { "${DescribeParser.formatNumber(count)} ${plural(unitWords(it.label), count)}" } ?: saidAmount(phrase),
            portionLabel = portion?.label,
            grams = grams,
            nutrients = Nutrients(
                kcal = food.per100g.kcal?.let { round1(it * factor) },
                protein = food.per100g.protein?.let { round1(it * factor) },
                carbs = food.per100g.carbs?.let { round1(it * factor) },
                fat = food.per100g.fat?.let { round1(it * factor) },
                fiber = food.per100g.fiber?.let { round1(it * factor) },
            ),
            micros = food.micros.mapValues { (_, v) -> round3(v * factor) },
            confidence = confidence(tier, resolved.how),
            food = food,
            alternatives = alternatives,
        )
    }

    private enum class How { Mass, Portion, Household, Estimated, Serving, Guess }

    private data class Resolved(val grams: Double, val portion: Portion?, val how: How)

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun resolveGrams(phrase: ParsedPhrase, food: Food, serving: ServingSpec?): Resolved {
        val unit = phrase.unit
        val qty = phrase.quantity
        unit?.gramsPerUnit?.let { return Resolved((qty ?: 1.0) * it, null, How.Mass) }

        // A serving known only by volume ("1 can (355 ml)") has no grams to scale by.
        val weighed = food.portions.filter { it.grams != null && it.grams > 0 && !it.isJunk() }
        val size = phrase.size
        val unitWords = unit?.portionWords.orEmpty()
        if (unitWords.isNotEmpty() || size != null) {
            // With no size said, a medium or regular one before a thin or a large one ("3 strips of bacon").
            val candidates = weighed.filter { p ->
                (unitWords.isEmpty() || unitWords.any { w -> p.label.hasWord(w) }) &&
                    (size == null || size.words.any { w -> p.label.hasWord(w) })
            }.sortedBy { p -> size != null || PortionSize.Medium.words.none { p.label.hasWord(it) } }
            // "a large coffee" has a "1 large"; "a large bowl" has no large bowl, so size scales the bowl below.
            val sized = candidates.firstOrNull()
                ?: weighed.takeIf { size != null && unitWords.isNotEmpty() }
                    ?.firstOrNull { p -> unitWords.any { w -> p.label.hasWord(w) } }
            if (sized != null) {
                val scale = if (candidates.isEmpty()) size?.scale ?: 1.0 else 1.0
                return Resolved((qty ?: 1.0) * scale * perUnit(sized), sized, How.Portion)
            }
        }
        unit?.household?.let { measure ->
            val scale = (qty ?: 1.0) * measure.count * (size?.scale ?: 1.0)
            weighed.firstOrNull { it.label.hasWord(measure.portionWord) && !it.label.contains("dry") }?.let {
                return Resolved(scale * perUnit(it), it, How.Household)
            }
            measure.grams?.let { return Resolved((qty ?: 1.0) * it * (size?.scale ?: 1.0), null, How.Household) }
        }
        // An estimated weight beats guessing that the amount is one serving.
        phrase.gramsHint?.takeIf { it > 0 }?.let { return Resolved(it, null, How.Estimated) }

        // "Medium fries" when the food has no medium portion: the serving, scaled by the size.
        val sizeScale = size?.scale ?: 1.0
        val spec = serving?.let { s -> weighed.firstOrNull { it.label.startsWith(s.label, ignoreCase = true) }?.let { it to s.count * sizeScale } }
        // A count said out loud counts the food's own units ("two eggs" is 2 × 1 egg, not 2 × the serving).
        val (portion, count) = when {
            spec != null && qty != null -> spec.first to qty
            spec != null -> spec
            else -> (typicalPortion(phrase.foodText, food, weighed) ?: return Resolved((qty ?: 1.0) * GUESS_GRAMS, null, How.Guess)) to (qty ?: 1.0)
        }
        return Resolved(count * perUnit(portion), portion, How.Serving)
    }

    /**
     * The portion that stands for "one" of a food: one named for what was said
     * ("1 McDonald's Big Mac"), one of the thing itself ("1 banana",
     * "1 nugget"), else a medium or regular one, else something counted
     * ("1 piece", "1 item"), else a cup, else the first listed.
     */
    private fun typicalPortion(foodText: String, food: Food, portions: List<Portion>): Portion? {
        val nameWords = words(food.name)
        val said = words(foodText) - JOINING_WORDS
        return portions.firstOrNull { p -> said.size > 1 && words(p.label).containsAll(said) }
            ?: portions.firstOrNull { p -> labelWords(p.label).let { it.size == 1 && it.first() in nameWords } }
            ?: portions.firstOrNull { p -> PortionSize.Medium.words.any { p.label.hasWord(it) } }
            // Something counted, unless one is a crumb ("1 piece" of broccoli is a floret).
            ?: portions.firstOrNull { p -> VOLUME_OR_MASS.none { p.label.hasWord(it) } && perUnit(p) >= MIN_COUNTED_GRAMS }
            ?: portions.firstOrNull { p -> p.label.hasWord("cup") && !p.label.contains("dry") }
            ?: portions.firstOrNull()
    }

    private fun perUnit(portion: Portion): Double =
        checkNotNull(portion.grams) { "only weighed portions reach here" } / DescribeParser.labelAmount(portion.label)

    private fun confidence(tier: MatchTier, how: How): DescribeConfidence {
        val matchScore = when (tier) {
            MatchTier.Exact, MatchTier.Head, MatchTier.Prefix -> 2
            MatchTier.FullText -> 1
            MatchTier.Similar -> 0
        }
        val amountPenalty = when (how) {
            How.Mass, How.Portion -> 0
            How.Household, How.Estimated, How.Serving -> 1
            How.Guess -> 2
        }
        return when (matchScore - amountPenalty) {
            2 -> DescribeConfidence.High
            1 -> DescribeConfidence.Medium
            else -> DescribeConfidence.Low
        }
    }

    private fun saidAmount(phrase: ParsedPhrase): String = buildString {
        append(DescribeParser.formatNumber(phrase.quantity ?: 1.0))
        phrase.size?.let { append(' ').append(it.wire) }
        phrase.unit?.let { append(' ').append(it.wire) }
    }

    private companion object {
        const val SEARCH_LIMIT = 25
        const val NAMED_LIMIT = 10
        const val CHOOSE_POOL = 15
        const val MAX_ALTERNATIVES = 3
        const val MIN_FALLBACK_QUERY = 3
        const val GUESS_GRAMS = 100.0
        const val MIN_COUNTED_GRAMS = 20.0
        val NAMED_TTL = 6.hours

        val VOLUME_OR_MASS = listOf("cup", "tbsp", "tablespoon", "tsp", "teaspoon", "oz", "ounce", "fl", "ml", "lb", "g", "quart", "pint")

        /**
         * Words in a food's name that say how it was made or sold. A name
         * carrying one the person didn't say is a worse guess for a bare word:
         * "toast" is not "French toast", "eggs" are not "Egg, whole, pickled".
         */
        val PREPARATIONS = setOf(
            "french", "fried", "pickled", "dried", "dehydrated", "powder", "powdered", "frozen", "canned", "deviled",
            "boiled", "poached", "scrambled", "hard-boiled", "breaded", "battered", "coated", "sweetened", "flavored",
            "decaffeinated", "instant", "baby", "toddler", "infant", "imitation", "substitute", "diet", "school",
            "restaurant", "fast", "reduced", "low", "lowfat", "nonfat", "free", "light", "glazed", "candied", "creamed",
            "stuffed", "concentrate", "undiluted", "dry", "mix", "ingredient", "with", "melba", "shrimp", "anisette",
            "babyfood", "dip", "sauce", "flavor", "chip", "bar", "soup", "pie", "cake", "puff", "spray", "unprepared",
        )

        /** Portions that are bookkeeping, not an amount anyone eats: "Guideline amount per...", school cartons. */
        val JUNK_PORTIONS = listOf("guideline", "school", "not specified", "as ingredient", "yields", "cubic inch", "surface inch", "crust not eaten")

        fun Portion.isJunk(): Boolean = JUNK_PORTIONS.any { label.contains(it, ignoreCase = true) }

        fun words(text: String): Set<String> =
            text.lowercase().split(Regex("[^a-z0-9'-]+")).filter { it.isNotBlank() }.map(::singular).toSet()

        fun singular(word: String): String = when {
            word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
            word.length > 3 && word.endsWith("es") && (word.endsWith("ches") || word.endsWith("shes") || word.endsWith("oes")) -> word.dropLast(2)
            word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
            else -> word
        }

        /** How many preparations [food]'s name adds that [said] doesn't have. */
        fun unsaid(food: Food, said: Set<String>): Int = (words(food.name) - said).count { it in PREPARATIONS }

        /** The words of a portion label after its number: "1 large or thick slice" → large, or, thick, slice. */
        fun labelWords(label: String): List<String> =
            label.lowercase().substringBefore('(').substringBefore(',').split(' ')
                .filter { w -> w.isNotBlank() && w.none { it.isDigit() } && w != "/" }
                .map(::singular)

        /** "1 cup (8 fl oz)" → "cup", "1 medium slice (yield after cooking)" → "medium slice", "1 item 7.6 oz" → "item". */
        fun unitWords(label: String): String = label.substringBefore('(').substringBefore(',').trim()
            .split(' ').dropWhile { w -> w.any { it.isDigit() } }.takeWhile { w -> w.none { it.isDigit() } }
            .joinToString(" ").ifEmpty { label }

        /** "2 egg" reads "2 eggs"; the last word only, as people say "2 medium slices". */
        fun plural(unit: String, count: Double): String = when {
            count <= 1.0 || unit.endsWith("s") || unit.endsWith("oz") -> unit
            // "2 large" (eggs): a size is not a thing to count.
            PortionSize.entries.any { size -> unit.substringAfterLast(' ') in size.words } || unit.endsWith("regular") -> unit
            unit.endsWith("ch") || unit.endsWith("sh") -> "${unit}es"
            else -> "${unit}s"
        }

        /** The share of [foodText]'s words the food's name carries. */
        fun coverage(foodText: String, food: Food): Double {
            val said = words(foodText) - JOINING_WORDS
            if (said.isEmpty()) return 1.0
            val nameWords = words("${food.name} ${food.brand.orEmpty()}")
            return said.count { word -> nameWords.any { close(word, it) } }.toDouble() / said.size
        }

        const val MIN_ALTERNATIVE_COVERAGE = 0.5

        /** Whether the food's name or brand carries the brand, however the catalog spells it. */
        fun Food.mentions(brand: Brand): Boolean = compact("$name ${this.brand.orEmpty()}").contains(brand.key)

        /** "CHICK-FIL-A, chicken sandwich" when someone said a chicken sandwich, or one from Wendy's. */
        fun Food.fromAnotherRestaurant(said: Brand?): Boolean = Brands.all.any { it != said && mentions(it) }

        fun compact(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

        /**
         * Every word of [foodText] is in the food's name, allowing for the
         * spellings speech gets nearly right ("nugget" for "nuggets", one letter off).
         */
        fun covers(foodText: String, food: Food): Boolean {
            val nameWords = words("${food.name} ${food.brand.orEmpty()}")
            return (words(foodText) - JOINING_WORDS).all { said -> nameWords.any { close(said, it) } }
        }

        /** Words that join a food's parts and need not appear in its catalog name. */
        val JOINING_WORDS = setOf("with", "and", "on", "in", "of", "a", "the", "or")

        fun close(a: String, b: String): Boolean = a == b ||
            (minOf(a.length, b.length) >= MIN_PREFIX && (a.startsWith(b) || b.startsWith(a))) ||
            (minOf(a.length, b.length) >= MIN_EDIT && editDistance(a, b) <= 1)

        private const val MIN_PREFIX = 4
        private const val MIN_EDIT = 5

        fun editDistance(a: String, b: String): Int {
            var previous = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val current = IntArray(b.length + 1)
                current[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
                }
                previous = current
            }
            return previous[b.length]
        }

        fun String.hasWord(word: String): Boolean =
            Regex("""\b${Regex.escape(word)}s?\b""").containsMatchIn(lowercase())

        fun round1(value: Double) = round(value * 10) / 10
        fun round3(value: Double) = round(value * 1000) / 1000
    }
}
