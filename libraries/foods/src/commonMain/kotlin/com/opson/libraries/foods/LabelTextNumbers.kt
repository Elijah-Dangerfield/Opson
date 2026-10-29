package com.opson.libraries.foods

internal enum class NumberUnit {
    None, Gram, Milligram, Microgram, Kilojoule, Kilocalorie, InternationalUnit, Percent,
    Millilitre, Kilogram, Litre, Centilitre, Ounce, FluidOunce, Other,

    /** "Trace" printed in place of a number: nothing worth counting. */
    Trace,
}

/** A number printed on a label, as printed: [decimals] is its precision, [lessThan] a "<" or "less than" before it. */
internal data class LabelNumber(val value: Double, val decimals: Int, val unit: NumberUnit, val lessThan: Boolean = false) {

    /**
     * This number as an amount of [target], the server's `toUnit` minus %DV:
     * "less than" halves it, IU only for vitamin D, and a number with no unit
     * is not an amount of anything.
     */
    fun amountIn(target: LabelUnit, nutrient: LabelNutrient?): Double? {
        val printed = if (lessThan) value / 2 else value
        if (UNIT_OF[unit] == target) return printed
        val grams = when (unit) {
            NumberUnit.Trace -> return 0.0
            NumberUnit.Gram -> printed
            NumberUnit.Milligram -> printed / 1_000.0
            NumberUnit.Microgram -> printed / 1_000_000.0
            NumberUnit.InternationalUnit -> if (nutrient == LabelNutrient.VitaminD) printed / VITAMIN_D_IU_PER_UG / 1_000_000.0 else return null
            else -> return null
        }
        return grams / (target.gramsPer ?: return null)
    }

    private companion object {
        const val VITAMIN_D_IU_PER_UG = 40.0
        val UNIT_OF = mapOf(
            NumberUnit.Gram to LabelUnit.Gram,
            NumberUnit.Milligram to LabelUnit.Milligram,
            NumberUnit.Microgram to LabelUnit.Microgram,
        )
    }
}

/**
 * Reads numbers off [LabelVocabulary.fold]ed label text: "12,5 g", "0.5g",
 * "<1g", "less than 1 g", "1650kJ/392kcal", "1,200mg", "1 590 kJ", "trace".
 */
internal object LabelNumbers {

    private val DECIMAL = setOf('.', ',')

    private val UNITS: Map<String, NumberUnit> = mapOf(
        "g" to NumberUnit.Gram, "gr" to NumberUnit.Gram, "grams" to NumberUnit.Gram,
        "mg" to NumberUnit.Milligram,
        "mcg" to NumberUnit.Microgram, "ug" to NumberUnit.Microgram,
        "kj" to NumberUnit.Kilojoule,
        "kcal" to NumberUnit.Kilocalorie, "cal" to NumberUnit.Kilocalorie,
        "iu" to NumberUnit.InternationalUnit, "ie" to NumberUnit.InternationalUnit, "ui" to NumberUnit.InternationalUnit,
        "ml" to NumberUnit.Millilitre, "kg" to NumberUnit.Kilogram, "l" to NumberUnit.Litre, "cl" to NumberUnit.Centilitre,
        "oz" to NumberUnit.Ounce,
    )

    /** The units OCR glues a misread digit to: "Og" is 0 g, "lg" is 1 g. */
    private val REPAIR_UNITS = setOf("g", "mg", "mcg", "ug", "kj", "kcal")
    private val LOOKALIKES = mapOf('o' to '0', 'l' to '1', 'i' to '1', '|' to '1')
    private val LESS_THAN = listOf("<", "less than", "lt", "moins de", "weniger als", "inferieur a", "meno di", "minder dan", "menos de")
    private val TRACE = setOf("trace", "traces", "spuren")

    /** Units a thousands separator can come with. Grams never run to four digits on a label, so "1,250 g" is 1.25. */
    private val THOUSANDS_UNITS = setOf(NumberUnit.Kilojoule, NumberUnit.Kilocalorie, NumberUnit.Milligram, NumberUnit.None)

    /**
     * Turns the letters OCR reads for digits back into digits where they sit
     * in a number: "Og" to "0g", "l2g" to "12g", "23O" to "230". A letter
     * inside a word ("sal", "calcio") is left alone.
     */
    fun repair(text: String): String {
        val chars = text.toCharArray()
        for (i in chars.indices) {
            val digit = LOOKALIKES[text[i]] ?: continue
            val prev = text.getOrNull(i - 1)
            val next = text.getOrNull(i + 1)
            val afterDigit = prev?.isDigit() == true || (prev != null && prev in DECIMAL && text.getOrNull(i - 2)?.isDigit() == true)
            val beforeDigit = next?.isDigit() == true || (next != null && next in DECIMAL && text.getOrNull(i + 2)?.isDigit() == true)
            val unitNext = unitAt(text, i + 1)
            val ends = next == null || !next.isLetter() || unitNext
            if ((afterDigit && ends) || (prev?.isLetter() != true && (beforeDigit || unitNext))) chars[i] = digit
        }
        return chars.concatToString()
    }

    private fun unitAt(text: String, at: Int): Boolean {
        var end = at
        while (end < text.length && text[end].isLetter()) end++
        return end > at && text.substring(at, end) in REPAIR_UNITS
    }

    /** Every number in [text], left to right. A number glued to a word before it ("b12", "e330") is part of the word. */
    fun scan(text: String): List<LabelNumber> = buildList {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val boundary = i == 0 || !text[i - 1].isLetterOrDigit()
            when {
                c.isDigit() && boundary -> i = read(text, i, this)
                c.isLetter() && boundary -> {
                    var end = i
                    while (end < text.length && text[end].isLetter()) end++
                    if (text.substring(i, end) in TRACE) add(LabelNumber(0.0, 0, NumberUnit.Trace))
                    i = end
                }
                else -> i++
            }
        }
    }

    /**
     * Every number in [lines], each line cut at its row labels first as the
     * parser cuts it, so "Calories 110 % Daily Value" is 110 and not 110%.
     */
    fun scan(lines: List<LabelTextLine>): List<LabelNumber> = lines.flatMap { line ->
        val text = repair(LabelVocabulary.fold(line.text))
        val labels = LabelVocabulary.matches(text)
        val starts = listOf(0) + labels.map { it.end }
        val ends = labels.map { it.start } + text.length
        starts.zip(ends).flatMap { (from, to) -> scan(text.substring(from, to)) }
    }

    private fun read(text: String, start: Int, into: MutableList<LabelNumber>): Int {
        var i = start
        while (i < text.length && text[i].isDigit()) i++
        var whole = text.substring(start, i)
        var fraction = ""
        if (i + 1 < text.length && text[i] in DECIMAL && text[i + 1].isDigit()) {
            var end = i + 1
            while (end < text.length && text[end].isDigit()) end++
            fraction = text.substring(i + 1, end)
            i = end
        } else if (spaceThousands(text, start, i)) {
            whole += text.substring(i + 1, i + 4)
            i += 4
        }
        val (unit, end) = unit(text, i)
        val thousands = fraction.length == 3 && whole.length in 1..3 && whole != "0" && unit in THOUSANDS_UNITS
        val value = if (thousands) "$whole$fraction".toDouble() else "$whole.${fraction.ifEmpty { "0" }}".toDouble()
        into += LabelNumber(value, if (thousands) 0 else fraction.length, unit, lessThan(text, start))
        return end
    }

    /** "1 590 kJ": French tables group thousands with a space. Only trusted in front of an energy unit. */
    private fun spaceThousands(text: String, start: Int, end: Int): Boolean {
        if (end - start !in 1..3 || end + 4 > text.length || text[end] != ' ') return false
        if (!text.substring(end + 1, end + 4).all { it.isDigit() }) return false
        if (end + 4 < text.length && text[end + 4].isDigit()) return false
        return unit(text, end + 4).first in setOf(NumberUnit.Kilojoule, NumberUnit.Kilocalorie)
    }

    private fun unit(text: String, from: Int): Pair<NumberUnit, Int> {
        var i = from
        while (i < text.length && text[i] == ' ' && i - from < 2) i++
        if (i < text.length && text[i] == '%') return NumberUnit.Percent to i + 1
        val start = i
        while (i < text.length && text[i].isLetter()) i++
        val letters = text.substring(start, i)
        if (letters == "fl") {
            var j = i
            while (j < text.length && text[j] == ' ') j++
            if (text.startsWith("oz", j) && (j + 2 == text.length || !text[j + 2].isLetter())) return NumberUnit.FluidOunce to j + 2
        }
        return when {
            letters.isEmpty() -> NumberUnit.None to from
            else -> (UNITS[letters] ?: NumberUnit.Other) to i
        }
    }

    private fun lessThan(text: String, start: Int): Boolean {
        val before = text.substring(0, start).trimEnd()
        return LESS_THAN.any { word ->
            before.endsWith(word) && (word == "<" || before.length == word.length || !before[before.length - word.length - 1].isLetter())
        }
    }

}
