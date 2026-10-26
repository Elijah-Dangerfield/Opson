package com.opson.libraries.diary

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/**
 * An amount of food the way people say it: "¼ cup", "1½ cups", "2 slices",
 * "150 g". Household measures get fractions and a plural unit; weights and
 * volumes in metric (and ounces) stay decimal and never pluralize.
 *
 * Amounts only, never nutrients: those go through the visibility policy.
 */
object HouseholdAmount {

    /** "0.25" is "¼", "1.5" is "1½", "2" is "2". A value with no common fraction keeps up to two decimals. */
    fun number(value: Double): String {
        val whole = floor(value + SNAP).toLong()
        val rest = value - whole
        val glyph = when {
            abs(rest) < SNAP -> return whole.toString()
            else -> FRACTIONS.firstOrNull { (fraction, _) -> abs(rest - fraction) < SNAP }?.second
        } ?: return decimal(value)
        return if (whole == 0L) glyph else "$whole$glyph"
    }

    /**
     * [quantity] of [unit]: `of(1.5, "cup")` is "1½ cups", `of(150.0, "g")` is
     * "150 g". A unit that is a whole phrase ("slice, thin") pluralizes only
     * its noun: "2 slices, thin".
     */
    fun of(quantity: Double, unit: String): String {
        val trimmed = unit.trim()
        return when {
            trimmed.isEmpty() -> number(quantity)
            isMetric(trimmed) -> "${decimal(quantity)} $trimmed"
            else -> "${number(quantity)} ${if (quantity > 1.0 + SNAP) plural(trimmed) else trimmed}"
        }
    }

    /**
     * [count] of a portion whose label carries its own amount: "1 cup" twice
     * is "2 cups", "0.25 cup" once is "¼ cup", "1/2 cup" three times is
     * "1½ cups". A label with no leading amount counts as one.
     */
    fun ofPortion(label: String, count: Double = 1.0): String {
        val (amount, unit) = split(label)
        return of(amount * count, unit)
    }

    /**
     * Text someone else wrote, or an older build saved ("1 1/2 cups", "0.25 cup", "2 bar",
     * "1.5 × 1 cup"), rewritten the same way when it starts with an amount. Anything else
     * ("a handful") comes back as it was.
     */
    fun tidy(text: String): String {
        val trimmed = text.trim()
        if (!LEADING_AMOUNT.containsMatchIn(trimmed)) return text
        // "1.5 × 1 cup": a count of a portion, as amounts were written before fractions.
        val times = trimmed.indexOf(TIMES)
        if (times > 0) {
            val count = split(trimmed.substring(0, times)).first
            return ofPortion(trimmed.substring(times + TIMES.length), count)
        }
        return ofPortion(trimmed)
    }

    /** The label without its leading amount: "1 cup" is "cup", "1/2 cup, sliced" is "cup, sliced". */
    fun unitOf(label: String): String = split(label).second

    /** The leading amount of a label: "1 cup" is 1, "0.25 cup" is 0.25, "1 1/2 cups" is 1.5, "slice" is 1. */
    fun amountOf(label: String): Double = split(label).first

    private fun split(label: String): Pair<Double, String> {
        val text = label.trim()
        val match = LEADING_AMOUNT.find(text) ?: return 1.0 to text
        val g = match.groupValues
        val amount = when {
            g[GROUP_FRACTION_NUM].isNotEmpty() -> ratio(g[GROUP_FRACTION_NUM], g[GROUP_FRACTION_DEN])
            g[GROUP_LONE_GLYPH].isNotEmpty() -> GLYPH_VALUES[g[GROUP_LONE_GLYPH]] ?: 0.0
            else -> (g[GROUP_WHOLE].toDoubleOrNull() ?: 0.0) + when {
                g[GROUP_MIXED_NUM].isNotEmpty() -> ratio(g[GROUP_MIXED_NUM], g[GROUP_MIXED_DEN])
                g[GROUP_GLYPH].isNotEmpty() -> GLYPH_VALUES[g[GROUP_GLYPH]] ?: 0.0
                else -> 0.0
            }
        }
        if (amount <= 0.0) return 1.0 to text
        return amount to text.substring(match.range.last + 1).trim()
    }

    private fun ratio(numerator: String, denominator: String): Double {
        val d = denominator.toDouble()
        return if (d == 0.0) 0.0 else numerator.toDouble() / d
    }

    private fun isMetric(unit: String): Boolean {
        val words = unit.lowercase().split(' ', ',', '(').filter(String::isNotEmpty)
        val head = words.firstOrNull() ?: return false
        return head in METRIC || (head == "fl" && words.getOrNull(1) == "oz")
    }

    /**
     * Pluralizes the noun of a unit phrase. Size words and all-caps tags in
     * front of it ("large egg", "NLEA serving") are skipped; a phrase that is
     * only a size ("medium") or an abbreviation stays as it is.
     */
    private fun plural(unit: String): String {
        val tokens = unit.split(' ').toMutableList()
        for (i in tokens.indices) {
            val token = tokens[i]
            val word = token.trimEnd(',', ';', ')', '.')
            if (word.isEmpty() || !word.all(Char::isLetter)) return unit
            val modifier = word.lowercase() in SIZES || (word.length > 1 && word.all(Char::isUpperCase))
            val endsPhrase = word.length < token.length
            if (modifier && !endsPhrase) continue
            if (modifier) return unit
            tokens[i] = pluralWord(word) + token.substring(word.length)
            return tokens.joinToString(" ")
        }
        return unit
    }

    private fun pluralWord(word: String): String {
        val lower = word.lowercase()
        return when {
            lower in NO_PLURAL -> word
            lower in IRREGULAR -> IRREGULAR.getValue(lower)
            lower.endsWith("ss") || lower.endsWith("x") || lower.endsWith("ch") || lower.endsWith("sh") -> word + "es"
            lower.endsWith("s") -> word
            lower.endsWith("y") && lower.length > 1 && lower[lower.length - 2] !in VOWELS -> word.dropLast(1) + "ies"
            else -> word + "s"
        }
    }

    private fun decimal(value: Double): String {
        val rounded = round(value * 100) / 100
        return if (rounded == floor(rounded)) rounded.toLong().toString() else rounded.toString()
    }

    private const val SNAP = 0.02
    private const val TIMES = " × "

    private val FRACTIONS = listOf(
        0.125 to "⅛",
        0.25 to "¼",
        1.0 / 3 to "⅓",
        0.5 to "½",
        2.0 / 3 to "⅔",
        0.75 to "¾",
    )
    private val GLYPH_VALUES = FRACTIONS.associate { (value, glyph) -> glyph to value }

    // "1 1/2", "1½", "0.25", "2", then "1/2", then "½"; the amount must end at a space or the end.
    private val LEADING_AMOUNT = Regex(
        "^(?:(\\d+(?:\\.\\d+)?)(?:\\s+(\\d+)/(\\d+)|\\s*([⅛¼⅓½⅔¾]))?|(\\d+)/(\\d+)|([⅛¼⅓½⅔¾]))(?=\\s|$)",
    )
    private const val GROUP_WHOLE = 1
    private const val GROUP_MIXED_NUM = 2
    private const val GROUP_MIXED_DEN = 3
    private const val GROUP_GLYPH = 4
    private const val GROUP_FRACTION_NUM = 5
    private const val GROUP_FRACTION_DEN = 6
    private const val GROUP_LONE_GLYPH = 7

    private val METRIC = setOf(
        "g", "gram", "grams", "kg", "mg", "mcg", "µg", "ml", "l", "cl", "dl",
        "oz", "ounce", "ounces", "lb", "lbs", "pound", "pounds",
    )
    private val SIZES = setOf(
        "small", "medium", "large", "extra", "jumbo", "mini", "whole", "big", "regular", "thick", "thin",
    )
    private val NO_PLURAL = setOf("tbsp", "tsp", "fl", "qt", "pt", "gal", "c", "t")
    private val IRREGULAR = mapOf("leaf" to "leaves", "loaf" to "loaves", "half" to "halves", "potato" to "potatoes", "tomato" to "tomatoes")
    private val VOWELS = setOf('a', 'e', 'i', 'o', 'u')
}
