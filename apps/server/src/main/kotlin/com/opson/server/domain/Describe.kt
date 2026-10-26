package com.opson.server.domain

import java.util.UUID

/** Units the "Describe it" parser understands, with the words that name them. */
enum class DescribeUnit(
    val wire: String,
    val words: Set<String>,
    /** Grams per one unit for mass units; null for household measures. */
    val gramsPerUnit: Double?,
    /** Words that identify this unit inside a USDA portion label ("1 cup, sliced"). */
    val portionWords: Set<String>,
) {
    Gram("g", setOf("g", "gm", "gr", "gram", "grams", "gramme", "grammes"), 1.0, emptySet()),
    Kilogram("kg", setOf("kg", "kgs", "kilo", "kilos", "kilogram", "kilograms"), 1_000.0, emptySet()),
    Ounce("oz", setOf("oz", "ozs", "ounce", "ounces"), 28.3495, setOf("oz", "ounce")),
    Cup("cup", setOf("cup", "cups"), null, setOf("cup")),
    Tablespoon("tbsp", setOf("tbsp", "tbsps", "tbs", "tablespoon", "tablespoons"), null, setOf("tbsp", "tablespoon")),
    Teaspoon("tsp", setOf("tsp", "tsps", "teaspoon", "teaspoons"), null, setOf("tsp", "teaspoon")),
    Slice("slice", setOf("slice", "slices"), null, setOf("slice")),
    Piece("piece", setOf("piece", "pieces", "pc", "pcs"), null, setOf("piece")),
    Plate("plate", setOf("plate", "plates", "plateful", "platefuls"), null, setOf("plate")),
    Bowl("bowl", setOf("bowl", "bowls", "bowlful", "bowlfuls"), null, setOf("bowl")),
    Serving("serving", setOf("serving", "servings"), null, setOf("serving")),
    ;

    companion object {
        fun fromWord(word: String): DescribeUnit? = entries.firstOrNull { word in it.words }
    }
}

enum class PortionSize(val wire: String, val words: Set<String>) {
    Small("small", setOf("small")),
    Medium("medium", setOf("medium", "med")),
    Large("large", setOf("large")),
    ;

    companion object {
        fun fromWord(word: String): PortionSize? = entries.firstOrNull { word in it.words }
    }
}

/** One phrase of a description, before it is matched to a food. */
data class ParsedPhrase(
    val text: String,
    val quantity: Double,
    val unit: DescribeUnit?,
    val size: PortionSize?,
    val foodText: String,
)

/**
 * The heuristic half of `POST /v1/describe`: "2 eggs and a slice of toast" →
 * phrases with a quantity, an optional unit or size, and the words to search
 * the catalog for. Pure and deterministic; no AI, no I/O.
 *
 * Phrases split on newlines, commas, semicolons, "+", and the words "and",
 * "with" and "plus". A leading quantity can be digits ("2", "1.5"), a fraction
 * ("1/2", "1 1/2", "½"), a number word (one to twelve), "a" / "an", "half",
 * "a couple of", or "N and a half". Units follow [DescribeUnit]; sizes follow
 * [PortionSize]. A phrase with no quantity means one.
 */
object DescribeParser {
    const val MAX_TEXT = 1_000
    const val MAX_PHRASES = 20

    private val NUMBER_WORDS = mapOf(
        "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0,
        "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "eleven" to 11.0, "twelve" to 12.0,
    )
    private val UNICODE_FRACTIONS = mapOf(
        '½' to "1/2", '⅓' to "1/3", '⅔' to "2/3", '¼' to "1/4", '¾' to "3/4", '⅛' to "1/8",
    )
    private val ARTICLES = setOf("a", "an", "the", "some", "of")
    private val SPLIT = Regex("""\n|,|;|\+|\band\b|\bwith\b|\bplus\b""")
    private val AND_A_HALF = Regex("""\b(\d+|${NUMBER_WORDS.keys.joinToString("|")})\s+and\s+a\s+half\b""")
    private val FRACTION = Regex("""^(\d+)/(\d+)$""")
    private val DECIMAL = Regex("""^\d+(\.\d+)?$""")

    fun parse(text: String): List<ParsedPhrase> {
        val normalized = normalize(text)
        return SPLIT.split(normalized)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull(::parsePhrase)
            .take(MAX_PHRASES)
    }

    private fun normalize(text: String): String {
        var s = text.take(MAX_TEXT).lowercase()
        UNICODE_FRACTIONS.forEach { (glyph, ascii) -> s = s.replace(glyph.toString(), " $ascii ") }
        s = s.replace("&", " and ")
        // "1 and a half cups" would otherwise split on "and"; fold it to 1.5 first.
        s = AND_A_HALF.replace(s) { m ->
            val whole = m.groupValues[1].toDoubleOrNull() ?: NUMBER_WORDS.getValue(m.groupValues[1])
            formatNumber(whole + 0.5)
        }
        // "200g" -> "200 g", "8oz" -> "8 oz"
        s = s.replace(Regex("""(\d)(\p{L})"""), "$1 $2")
        return s.replace(Regex("""[^\p{L}\p{N}/.\n,;+'\- ]"""), " ")
    }

    private fun parsePhrase(phrase: String): ParsedPhrase? {
        val tokens = phrase.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        val quantity = takeQuantity(tokens)

        var unit: DescribeUnit? = null
        var size: PortionSize? = null
        // Up to two of: a size word, a unit word, "of" — "a large bowl of soup".
        repeat(3) {
            val word = tokens.firstOrNull() ?: return@repeat
            when {
                size == null && PortionSize.fromWord(word) != null -> {
                    size = PortionSize.fromWord(word)
                    tokens.removeAt(0)
                }

                unit == null && DescribeUnit.fromWord(word) != null -> {
                    unit = DescribeUnit.fromWord(word)
                    tokens.removeAt(0)
                }

                word == "of" -> tokens.removeAt(0)
            }
        }
        while (tokens.isNotEmpty() && tokens.first() in ARTICLES) tokens.removeAt(0)

        val foodText = tokens.joinToString(" ").trim(' ', '.', '-', '\'')
        if (foodText.isEmpty()) return null
        return ParsedPhrase(
            text = phrase,
            quantity = quantity ?: 1.0,
            unit = unit,
            size = size,
            foodText = foodText,
        )
    }

    /** Consumes a leading quantity from [tokens]; null when there is none. */
    private fun takeQuantity(tokens: MutableList<String>): Double? {
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
            (first == "a" || first == "an") && second == "half" -> consume(2, 0.5)
            first == "half" && (second == "a" || second == "an") -> consume(2, 0.5)
            first == "half" -> consume(1, 0.5)
            first == "a" && second == "couple" && third == "of" -> consume(3, 2.0)
            first == "couple" && second == "of" -> consume(2, 2.0)
            first == "a" || first == "an" -> consume(1, 1.0)
            else -> null
        }
    }

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

    /** Leading amount of a portion label: "1 cup" → 1, "2 tablespoon" → 2, "1/2 cup" → 0.5. */
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
)

interface FoodDescriber {
    suspend fun describe(text: String): List<DescribedItem>
}
