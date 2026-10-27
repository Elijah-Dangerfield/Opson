package com.opson.server.domain

import java.util.UUID

/**
 * Units the "Describe it" parser understands, with the words that name them.
 * [wire] is what `/v1/describe` sends as `unit`; 0.1 shows it as text, so new
 * units only ever add values.
 */
enum class DescribeUnit(
    val wire: String,
    val words: Set<String>,
    /** Grams per one unit for mass units; null for household measures. */
    val gramsPerUnit: Double?,
    /** Words that identify this unit inside a USDA portion label ("1 cup, sliced"). */
    val portionWords: Set<String>,
    /**
     * What one of this unit is when the food lists no portion with its name: a
     * bowl of cereal is about a cup and a half. Null to fall back to the
     * food's typical portion.
     */
    val household: HouseholdMeasure? = null,
) {
    Gram("g", setOf("g", "gm", "gr", "gram", "grams", "gramme", "grammes"), 1.0, emptySet()),
    Kilogram("kg", setOf("kg", "kgs", "kilo", "kilos", "kilogram", "kilograms"), 1_000.0, emptySet()),
    Ounce("oz", setOf("oz", "ozs", "ounce", "ounces"), 28.3495, setOf("oz", "ounce")),
    Pound("lb", setOf("lb", "lbs", "pound", "pounds"), 453.592, setOf("lb", "pound")),
    Milliliter("ml", setOf("ml", "mls", "milliliter", "milliliters", "millilitre", "millilitres"), 1.0, emptySet()),
    FluidOunce("fl oz", setOf("floz"), null, setOf("fl oz"), HouseholdMeasure("fl oz", 1.0, grams = 30.0)),
    Cup("cup", setOf("cup", "cups"), null, setOf("cup")),
    Tablespoon("tbsp", setOf("tbsp", "tbsps", "tbs", "tablespoon", "tablespoons"), null, setOf("tbsp", "tablespoon")),
    Teaspoon("tsp", setOf("tsp", "tsps", "teaspoon", "teaspoons"), null, setOf("tsp", "teaspoon")),
    Slice("slice", setOf("slice", "slices"), null, setOf("slice", "piece")),
    Piece("piece", setOf("piece", "pieces", "pc", "pcs", "count", "ct"), null, setOf("piece")),
    Strip("strip", setOf("strip", "strips", "rasher", "rashers"), null, setOf("strip", "slice")),
    Link("link", setOf("link", "links"), null, setOf("link")),
    Patty("patty", setOf("patty", "patties"), null, setOf("patty")),
    Bar("bar", setOf("bar", "bars"), null, setOf("bar")),
    Scoop("scoop", setOf("scoop", "scoops"), null, setOf("scoop"), HouseholdMeasure("cup", 0.5)),
    Glass("glass", setOf("glass", "glasses"), null, setOf("glass", "cup"), HouseholdMeasure("cup", 1.0, grams = 240.0)),
    Mug("mug", setOf("mug", "mugs"), null, setOf("mug"), HouseholdMeasure("cup", 1.5, grams = 360.0)),
    Can("can", setOf("can", "cans", "tin", "tins"), null, setOf("can"), HouseholdMeasure("fl oz", 12.0, grams = 360.0)),
    Bottle("bottle", setOf("bottle", "bottles"), null, setOf("bottle"), HouseholdMeasure("fl oz", 16.0, grams = 480.0)),
    Handful("handful", setOf("handful", "handfuls"), 30.0, emptySet()),
    Packet("packet", setOf("packet", "packets", "pack", "packs", "package", "packages", "sachet"), null, setOf("packet", "package")),
    Container("container", setOf("container", "containers", "tub", "tubs"), null, setOf("container")),
    Plate("plate", setOf("plate", "plates", "plateful", "platefuls"), null, setOf("plate"), HouseholdMeasure("cup", 2.0)),
    Bowl("bowl", setOf("bowl", "bowls", "bowlful", "bowlfuls"), null, setOf("bowl"), HouseholdMeasure("cup", 1.5)),
    Serving("serving", setOf("serving", "servings", "portion", "portions", "helping", "helpings", "order", "orders"), null, setOf("serving")),
    ;

    /** A weight or volume said only with a number ("200 g"); everything else is counted ("a cup of"). */
    val isMeasure: Boolean get() = gramsPerUnit != null && this != Handful

    companion object {
        fun fromWord(word: String): DescribeUnit? = entries.firstOrNull { word in it.words }
    }
}

/**
 * A household amount by another name: a bowl is [count] of the portion named
 * [portionWord], or [grams] when the food has no such portion.
 */
data class HouseholdMeasure(val portionWord: String, val count: Double, val grams: Double? = null)

enum class PortionSize(val wire: String, val words: Set<String>, /** How a bowl or a plate of this size scales. */ val scale: Double) {
    Small("small", setOf("small", "little", "kids", "kid's", "mini"), 0.75),
    Medium("medium", setOf("medium", "med", "regular", "grande"), 1.0),
    // "A tall glass" is a big one; a Starbucks tall is their small, which the parser handles with the brand.
    Large("large", setOf("large", "big", "huge", "venti", "xl", "jumbo", "giant", "tall"), 1.5),
    ;

    companion object {
        fun fromWord(word: String): PortionSize? = entries.firstOrNull { word in it.words }
    }
}

/**
 * One phrase of a description, before it is matched to a food. [gramsHint]
 * is a weight for the whole amount from somewhere other than the words (a
 * photo estimate); matching uses it when no portion of the food fits the
 * unit or size. Typed text never has one.
 */
data class ParsedPhrase(
    val text: String,
    /** How many, when the words say; null means a typical serving ("some toast", "eggs"). */
    val quantity: Double?,
    val unit: DescribeUnit?,
    val size: PortionSize?,
    val foodText: String,
    val gramsHint: Double? = null,
    /** A restaurant the words named ("from Chick-fil-A"): its foods are preferred when the catalog has them. */
    val brand: Brand? = null,
    /** Said after "with" ("coffee with milk"): a food that has a smaller amount as an addition gets it. */
    val addition: Boolean = false,
)

/**
 * The heuristic half of `POST /v1/describe`: "2 eggs and a slice of toast" →
 * phrases with a quantity, an optional unit or size, and the words to search
 * the catalog for. Pure and deterministic; no AI, no I/O.
 *
 * Built for how people talk and how speech recognition writes it down, not
 * for tidy lists: "I had um two eggs and some toast for breakfast", "a coffee
 * no wait a latte", "8 piece grilled nuggets from chick fil a".
 *  - Sentences split on full stops and newlines; phrases on commas,
 *    semicolons, "+", and the words "and", "with", "plus", "also" and
 *    "then". A food named with "and" ("mac and cheese") stays whole
 *    ([DescribeVocabulary.COMPOUNDS]). What follows "with" is an
 *    [addition][ParsedPhrase.addition] ("coffee with milk").
 *  - A correction ("no wait", "actually", "I mean", "make that") drops what
 *    came before it in the phrase, or the phrase before when it stands alone.
 *    "No butter" and "without cheese" drop that food.
 *  - Filler goes: "I had", "um", "for breakfast", "leftover".
 *  - A restaurant ("from McDonald's", "at chickfila", "Starbucks latte") is
 *    read through [Brands], misspellings included, and applies to the whole
 *    sentence. Known misrecognitions are fixed first ("big mack", "nuggest").
 *  - A leading quantity can be digits ("2", "1.5", "2-3"), a fraction ("1/2",
 *    "1 1/2", "½"), a number word (one to twenty, a dozen), "a" / "an",
 *    "half", "a couple of", "a few", or "N and a half". "Some", "a bit of" and
 *    no quantity at all mean a typical serving: [ParsedPhrase.quantity] null.
 *  - Units follow [DescribeUnit]; sizes follow [PortionSize].
 */
object DescribeParser {
    const val MAX_TEXT = 1_000
    const val MAX_PHRASES = 20

    private val NUMBER_WORDS = mapOf(
        "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0,
        "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "eleven" to 11.0, "twelve" to 12.0,
        "thirteen" to 13.0, "fourteen" to 14.0, "fifteen" to 15.0, "sixteen" to 16.0, "seventeen" to 17.0,
        "eighteen" to 18.0, "nineteen" to 19.0, "twenty" to 20.0, "dozen" to 12.0,
    )
    private val UNICODE_FRACTIONS = mapOf(
        '½' to "1/2", '⅓' to "1/3", '⅔' to "2/3", '¼' to "1/4", '¾' to "3/4", '⅛' to "1/8",
    )
    private val ARTICLES = setOf("a", "an", "the", "of", "my", "our")

    /** Words that mean "a normal amount", which is what no quantity means too. */
    private val VAGUE = listOf(
        "a little bit of", "a little bit", "a bit of", "a little", "a touch of", "a splash of", "a dash of",
        "some", "a lot of", "lots of", "a bunch of", "bunch of", "plenty of", "any",
    )

    private val SENTENCE = Regex("""\n|[!?]|\.(?!\d)""")
    private val CLAUSE = Regex("""[,;]""")
    private val JOINER = Regex("""\s*\+\s*|\b(and|with|plus|also|then|along with|alongside)\b""")
    private val AND_A_HALF = Regex("""\b(\d+|${NUMBER_WORDS.keys.joinToString("|")})\s+and\s+a\s+half\b""")
    private val FRACTION = Regex("""^(\d+)/(\d+)$""")
    private val DECIMAL = Regex("""^\d+(\.\d+)?$""")
    private val RANGE = Regex("""\b(\d+(?:\.\d+)?)\s*(?:-|to|or)\s*\d+(?:\.\d+)?\b""")
    private val WORD_RANGE = Regex("""\b(${NUMBER_WORDS.keys.joinToString("|")})\s+(?:to|or)\s+(?:${NUMBER_WORDS.keys.joinToString("|")})\b""")
    private val NEGATION = Regex("""(?:^|\s)(?:no|without|hold the|minus|except)\s+(?!wait\b|sorry\b)\S.*$""")

    fun parse(text: String): List<ParsedPhrase> {
        val phrases = mutableListOf<ParsedPhrase>()
        for (sentence in SENTENCE.split(normalize(text))) {
            val (brand, rest) = Brands.extract(sentence)
            val sentencePhrases = mutableListOf<ParsedPhrase>()
            for (clause in CLAUSE.split(rest)) {
                var addition = false
                var start = 0
                val parts = mutableListOf<Pair<String, Boolean>>()
                for (joiner in JOINER.findAll(clause)) {
                    parts += clause.substring(start, joiner.range.first) to addition
                    if (joiner.value.trim().let { it == "with" || it.startsWith("along") }) addition = true
                    start = joiner.range.last + 1
                }
                parts += clause.substring(start) to addition
                for ((raw, isAddition) in parts) {
                    val corrected = correction(raw)
                    if (corrected.dropsPrevious) sentencePhrases.removeLastOrNull()
                    val part = NEGATION.replace(corrected.text, "")
                    parsePhrase(part, isAddition)?.let { sentencePhrases += it }
                }
            }
            phrases += sentencePhrases.map { phrase ->
                when {
                    brand == null -> phrase
                    // Starbucks' tall is its smallest cup, not a big one.
                    brand == Brands.Starbucks && phrase.text.split(' ').contains("tall") -> phrase.copy(brand = brand, size = PortionSize.Small)
                    else -> phrase.copy(brand = brand)
                }
            }
        }
        return phrases.take(MAX_PHRASES)
    }

    private data class Corrected(val text: String, val dropsPrevious: Boolean)

    /**
     * "a coffee no wait a latte" keeps "a latte"; a part that starts with the
     * correction ("no wait, a latte" after "a coffee,") replaces the phrase
     * before it instead.
     */
    private fun correction(part: String): Corrected {
        val match = DescribeVocabulary.CORRECTION.findAll(part).lastOrNull() ?: return Corrected(part, dropsPrevious = false)
        val before = part.substring(0, match.range.first)
        val after = part.substring(match.range.last + 1)
        val beforeHasFood = DescribeVocabulary.stripFiller(before).isNotBlank()
        return Corrected(after, dropsPrevious = !beforeHasFood)
    }

    private fun normalize(text: String): String {
        var s = text.take(MAX_TEXT).lowercase()
        UNICODE_FRACTIONS.forEach { (glyph, ascii) -> s = s.replace(glyph.toString(), " $ascii ") }
        s = s.replace('’', '\'').replace('‘', '\'').replace("&", " and ").replace("w/", " with ")
        // "200g" -> "200 g", "8oz" -> "8 oz", "8pc" -> "8 pc"
        s = s.replace(Regex("""(\d)(\p{L})"""), "$1 $2")
        s = s.replace(Regex("""\bfl\.?\s*oz\b|\bfluid\s+ounces?\b"""), "floz")
        s = DescribeVocabulary.fixMisheard(s)
        // "1 and a half cups" would otherwise split on "and"; fold it to 1.5 first.
        s = AND_A_HALF.replace(s) { m ->
            val whole = m.groupValues[1].toDoubleOrNull() ?: NUMBER_WORDS.getValue(m.groupValues[1])
            formatNumber(whole + 0.5)
        }
        s = s.replace(Regex("""\bhalf\s+(?:a\s+)?dozen\b"""), "6")
        // "2-3 eggs", "two or three eggs": the first number.
        s = RANGE.replace(s) { it.groupValues[1] }
        s = WORD_RANGE.replace(s) { it.groupValues[1] }
        s = DescribeVocabulary.protectCompounds(s)
        return s.replace(Regex("""[^\p{L}\p{N}/.\n,;+'_\- !?]"""), " ")
    }

    private fun parsePhrase(raw: String, addition: Boolean): ParsedPhrase? {
        val phrase = DescribeVocabulary.stripFiller(raw).replace('_', ' ').trim()
        if (phrase.isEmpty()) return null
        val tokens = phrase.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        var quantity = takeQuantity(tokens)

        var unit: DescribeUnit? = null
        var size: PortionSize? = null
        // Up to three of: a size word, a unit word, "of" ("a large bowl of soup", "a cup of").
        repeat(4) {
            val word = tokens.firstOrNull() ?: return@repeat
            when {
                size == null && PortionSize.fromWord(word) != null && tokens.size > 1 && "$word ${tokens[1]}" !in SIZE_NAMED_FOODS -> {
                    size = PortionSize.fromWord(word)
                    tokens.removeAt(0)
                }

                unit == null && DescribeUnit.fromWord(word) != null && tokens.size > 1 -> {
                    unit = DescribeUnit.fromWord(word)
                    tokens.removeAt(0)
                }

                word in ARTICLES -> tokens.removeAt(0)
            }
        }
        // "two slices of toast" said the count before the unit; "slice of 2" never happens.
        if (quantity == null && unit != null) quantity = takeQuantity(tokens)
        while (tokens.isNotEmpty() && tokens.first() in ARTICLES) tokens.removeAt(0)

        val foodText = tokens.joinToString(" ").trim(' ', '.', '-', '\'')
        if (foodText.isEmpty() || foodText.all { !it.isLetter() }) return null
        // "a cup", "200 g", "2 large": an amount with no food.
        if (tokens.all { DescribeUnit.fromWord(it) != null || PortionSize.fromWord(it) != null }) return null
        // A unit said with no number is one of it: "a cup of coffee" took "a", "cup of coffee" takes nothing.
        val stated = quantity ?: if (unit != null && unit?.isMeasure == false) 1.0 else null
        return ParsedPhrase(
            text = raw.trim().replace('_', ' ').replace(Regex("\\s+"), " "),
            quantity = stated,
            unit = unit,
            size = size,
            foodText = foodText,
            addition = addition,
        )
    }

    /** Consumes a leading quantity from [tokens]; null when there is none or it is vague ("some"). */
    private fun takeQuantity(tokens: MutableList<String>): Double? {
        val joined = tokens.joinToString(" ")
        VAGUE.firstOrNull { joined == it || joined.startsWith("$it ") }?.let { vague ->
            repeat(vague.split(' ').size) { tokens.removeAt(0) }
            return null
        }
        val first = tokens.firstOrNull() ?: return null
        val second = tokens.getOrNull(1)
        val third = tokens.getOrNull(2)
        fun consume(n: Int, value: Double): Double {
            repeat(n) { tokens.removeAt(0) }
            return value
        }
        number(first)?.let { whole ->
            // "1 1/2"
            val fraction = second?.let { FRACTION.matchEntire(it) }?.let { fractionValue(it) }
            return if (fraction != null && DECIMAL.matches(first)) consume(2, whole + fraction) else consume(1, whole)
        }
        return when {
            // "an 8 piece": the number is the count, the article is grammar.
            (first == "a" || first == "an") && second != null && number(second) != null -> {
                tokens.removeAt(0)
                takeQuantity(tokens)
            }
            (first == "a" || first == "an") && second == "half" -> consume(2, 0.5)
            first == "half" && (second == "a" || second == "an") -> consume(2, 0.5)
            first == "half" && second == "of" -> consume(2, 0.5)
            first == "half" -> consume(1, 0.5)
            (first == "a" && second == "couple" && third == "of") || (first == "a" && second == "few" && third == "of") ->
                consume(3, if (second == "few") FEW else 2.0)
            first == "a" && (second == "couple" || second == "pair") -> consume(2, 2.0)
            first == "a" && second == "few" -> consume(2, FEW)
            first == "couple" && second == "of" -> consume(2, 2.0)
            first == "couple" || first == "few" -> consume(1, if (first == "few") FEW else 2.0)
            first == "a" || first == "an" -> consume(1, 1.0)
            else -> null
        }
    }

    private const val FEW = 3.0

    /** Foods whose name starts with a size word: a Big Mac is not a large mac. */
    private val SIZE_NAMED_FOODS = setOf("big mac", "big gulp", "jumbo jack", "little caesars")

    private fun number(token: String): Double? {
        NUMBER_WORDS[token]?.let { return it }
        if (DECIMAL.matches(token)) return token.toDoubleOrNull()?.takeIf { it > 0 }
        return FRACTION.matchEntire(token)?.let { fractionValue(it) }
    }

    private fun fractionValue(match: MatchResult): Double? {
        val (numerator, denominator) = match.destructured
        val d = denominator.toDouble()
        return if (d == 0.0) null else numerator.toDouble() / d
    }

    /** Leading amount of a portion label: "1 cup" → 1, "2 tablespoon" → 2, "1/2 cup" → 0.5, "0.5 lb" → 0.5. */
    fun labelAmount(label: String): Double {
        val tokens = label.lowercase().split(Regex("\\s+"))
        val first = tokens.firstOrNull() ?: return 1.0
        val whole = number(first) ?: return 1.0
        val fraction = tokens.getOrNull(1)?.let { FRACTION.matchEntire(it) }?.let { fractionValue(it) }
        val amount = if (fraction != null && DECIMAL.matches(first)) whole + fraction else whole
        return if (amount > 0) amount else 1.0
    }

    /** "0.5" → "1/2", "1.5" → "1 1/2", "2" → "2", "0.3" → "0.3". */
    fun formatNumber(value: Double): String {
        val whole = value.toLong()
        val rest = value - whole
        val fraction = when {
            rest < 0.01 -> ""
            kotlin.math.abs(rest - 0.25) < 0.01 -> "1/4"
            kotlin.math.abs(rest - 0.5) < 0.01 -> "1/2"
            kotlin.math.abs(rest - 0.75) < 0.01 -> "3/4"
            kotlin.math.abs(rest - 1.0 / 3) < 0.01 -> "1/3"
            kotlin.math.abs(rest - 2.0 / 3) < 0.01 -> "2/3"
            else -> return String.format(java.util.Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
        }
        return when {
            fraction.isEmpty() -> whole.toString()
            whole == 0L -> fraction
            else -> "$whole $fraction"
        }
    }
}

enum class DescribeConfidence(val wire: String) { High("high"), Medium("medium"), Low("low") }

/**
 * One described item. Every amount here is an estimate by construction; the
 * client labels it "Estimate" whatever [confidence] says, and logs it with
 * precision `estimate`. [foodId] is null when nothing in the catalog matched.
 * [food] is the matched food itself and [alternatives] the next best foods
 * for the same words, so the app can change the amount or swap the food
 * without asking again.
 */
data class DescribedItem(
    val text: String,
    val name: String,
    val foodId: UUID?,
    val quantity: Double,
    val unit: DescribeUnit?,
    val amountText: String,
    val portionLabel: String?,
    val grams: Double?,
    val nutrients: Nutrients?,
    val micros: Map<String, Double>?,
    val confidence: DescribeConfidence,
    val food: Food? = null,
    val alternatives: List<Food> = emptyList(),
)

interface FoodDescriber {
    suspend fun describe(text: String): List<DescribedItem>
}
