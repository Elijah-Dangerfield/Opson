package com.opson.libraries.foods

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** One line the phone's OCR read off a label photo. Box as fractions of the image (0..1), origin top-left. */
data class LabelTextLine(
    val text: String,
    val confidence: Double = 1.0,
    val left: Double = 0.0,
    val top: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0,
)

/** What the parser saw of the panel's shape. */
data class LabelLayout(
    val panelFound: Boolean,
    val perServingColumn: Boolean,
    val per100Column: Boolean,
    /** An "as prepared" column exists. The draft keeps "as sold". */
    val asPrepared: Boolean,
    /** Two languages print the same panel, as on Canadian packs. */
    val dualLanguage: Boolean,
    /** Nutrient columns printed, at least 1. Per container and as prepared columns count: each is a column to misread. */
    val columns: Int = 1,
) {
    companion object {
        val NONE = LabelLayout(panelFound = false, perServingColumn = false, per100Column = false, asPrepared = false, dualLanguage = false)
    }
}

/** The parser's draft. Fields it could not place stay empty: a wrong value is worse than an empty one. */
data class LabelDraft(
    /** Serving, servings per container, basis and both columns; name and brand stay null. */
    val facts: LabelFacts,
    val layout: LabelLayout,
    /** Lowest OCR confidence of the line(s) behind each filled field, by form key: "serving_size" or a [LabelNutrient.wire]. */
    val fieldConfidence: Map<String, Double>,
) {
    /** How many of the five headline fields (serving size, energy, protein, carbs, fat) it filled. */
    val headlineFound: Int
        get() = (if (facts.servingSize != null) 1 else 0) +
            LabelNutrient.REQUIRED.count { it in facts.perServing || it in facts.per100 }

    companion object {
        const val SERVING_SIZE = "serving_size"

        /** The five headline fields by form key. */
        val HEADLINE: List<String> = listOf(SERVING_SIZE) + LabelNutrient.REQUIRED.map { it.wire }
    }
}

/**
 * Nutrition-panel OCR lines, in reading order, to a [LabelDraft]. The same
 * conversions as the server's `LabelOutputReader`, so a phone draft and a
 * server read of one photo agree: kJ becomes kcal only where no kcal is
 * printed, salt becomes sodium (÷ 2.5) unless sodium is printed, "less than"
 * halves the printed number, ounces become grams. %DV-only rows stay empty.
 *
 * Anything it cannot place without guessing (a row with more or fewer
 * numbers than the panel has columns, a value with no unit, a value line
 * that could belong to more than one row) stays empty.
 */
object LabelTextParser {
    fun parse(lines: List<LabelTextLine>): LabelDraft = LabelTextReading(lines).draft()
}

private enum class ColumnRole { Per100, Serving, Container }

private data class ServingRead(val size: Double, val unit: ServingUnit, val household: String?, val lines: List<Int>)

private data class Column(val role: ColumnRole, val prepared: Boolean, val serving: ServingRead?) {
    /** What makes two headers the same column: "per 1 cup (250 mL)" and "pour 1 tasse (250 mL)" are. */
    val key get() = listOf(role, prepared, serving?.size, serving?.unit)
}

/** One row: its label, the text after it that holds its numbers, and the lines that text came from. */
private class Segment(val phrase: LabelPhrase, var text: String, val lines: MutableList<Int>) {
    val numbers: List<LabelNumber> get() = LabelNumbers.scan(text)
}

private class LabelTextReading(private val source: List<LabelTextLine>) {

    private val text = source.map { LabelNumbers.repair(LabelVocabulary.fold(it.text)) }
    private val anchors = mutableListOf<Pair<String, LabelLanguage>>()
    private val seen = mutableListOf<LabelPhrase>()
    private val columns = mutableListOf<Column>()
    private val segments = mutableListOf<Segment>()

    /** Rows read with no numbers yet, waiting for a value-only line. */
    private val pending = mutableListOf<Segment>()
    private val consumed = mutableSetOf<Int>()
    private var servingLine: ServingRead? = null
    private var servingsPerContainer: Double? = null
    private var twoLanguagesInARow = false

    /** Column headers are read only above the first row, so a footnote's "prepared with milk" can't relabel a column. */
    private var inHeader = true

    init {
        text.indices.forEach(::read)
    }

    private fun read(index: Int) {
        if (index in consumed) return
        val line = text[index]
        anchors += LabelVocabulary.anchors(line)
        // Both run: one line can hold the serving size and the servings per container.
        if (readServing(index) or readServingsPerContainer(line)) return
        val matches = LabelVocabulary.matches(line)
        val prefix = line.substring(0, matches.firstOrNull()?.start ?: line.length)
        val headed = inHeader && readHeaders(prefix, index)
        if (matches.isEmpty()) {
            if (!headed && PER.find(line) == null) attachValues(index)
            return
        }
        addSegments(line, prefix, matches, index)
    }

    // region Serving

    private fun readServing(index: Int): Boolean {
        val line = text[index]
        val key = SERVING_KEYS.firstOrNull { line.hasPhrase(it) } ?: return false
        var read = servingOf(line.substringAfter(key), listOf(index))
        if (read == null && index + 1 < text.size) {
            read = servingOf(text[index + 1], listOf(index, index + 1))
            if (read != null) consumed += index + 1
        }
        if (servingLine == null) servingLine = read
        return true
    }

    private fun readServingsPerContainer(line: String): Boolean {
        val found = SERVINGS_PER_CONTAINER.firstNotNullOfOrNull { it.find(line) } ?: return false
        if (servingsPerContainer == null) {
            servingsPerContainer = found.groupValues[1].replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
        }
        return true
    }

    /**
     * The serving in [text]: grams or ml, the figure in brackets first
     * ("2/3 cup (55g)"), ounces converted as the server does. The household
     * measure is what comes before the brackets, when it is not the weight
     * itself.
     */
    private fun servingOf(text: String, lines: List<Int>): ServingRead? {
        val open = text.indexOf('(')
        val bracketed = if (open >= 0) LabelNumbers.scan(text.substring(open)) else emptyList()
        val number = (bracketed + LabelNumbers.scan(text)).firstOrNull { it.unit in SERVING_UNITS } ?: return null
        val (size, unit) = when (number.unit) {
            NumberUnit.Gram -> number.value to ServingUnit.Gram
            NumberUnit.Kilogram -> number.value * 1_000 to ServingUnit.Gram
            NumberUnit.Ounce -> number.value * GRAMS_PER_OZ to ServingUnit.Gram
            NumberUnit.Millilitre -> number.value to ServingUnit.Milliliter
            NumberUnit.Litre -> number.value * 1_000 to ServingUnit.Milliliter
            NumberUnit.Centilitre -> number.value * 10 to ServingUnit.Milliliter
            else -> number.value * ML_PER_FL_OZ to ServingUnit.Milliliter
        }
        if (size <= 0) return null
        val household = text.substring(0, if (open >= 0) open else text.length).trim(' ', ':', '-', '.', '/', '*')
            .takeIf { it.any(Char::isDigit) && LabelNumbers.scan(it).none { n -> n.unit in METRIC_UNITS } }
        return ServingRead(size, unit, household, lines)
    }

    // endregion

    // region Column headers

    private fun readHeaders(prefix: String, index: Int): Boolean {
        val found = headerColumns(prefix, index)
        if (found.isNotEmpty()) {
            columns += found
            return true
        }
        if (columns.isNotEmpty() && PREPARED.any { it in prefix }) {
            columns[columns.lastIndex] = columns.last().copy(prepared = true)
            return true
        }
        return false
    }

    /**
     * The columns a header names: "Per 100g", "Per 30g serving", "pro 100 g",
     * "Per 1 cup (250 mL) / pour 1 tasse (250 mL)". The same column printed
     * twice in a row on one line is one column in two languages.
     */
    private fun headerColumns(prefix: String, index: Int): List<Column> {
        val starts = PER.findAll(prefix).map { it.range.first }.toList()
        val chunks = if (starts.isEmpty()) {
            HUNDRED.findAll(prefix).map { it.value }.toList()
        } else {
            starts.mapIndexed { i, start -> prefix.substring(start, starts.getOrNull(i + 1) ?: prefix.length).substringBefore('/').substringBefore('|') }
        }
        val found = chunks.map { column(it, index) }
        return found.filterIndexed { i, column -> i == 0 || column.key != found[i - 1].key }
    }

    private fun column(chunk: String, index: Int): Column {
        val prepared = PREPARED.any { it in chunk }
        return when {
            HUNDRED.containsMatchIn(chunk) -> Column(ColumnRole.Per100, prepared, null)
            CONTAINER.any { chunk.hasPhrase(it) } -> Column(ColumnRole.Container, prepared, null)
            else -> Column(ColumnRole.Serving, prepared, servingOf(chunk.substringAfter(' ', ""), listOf(index)))
        }
    }

    // endregion

    // region Rows

    private fun addSegments(line: String, prefix: String, matches: List<PhraseMatch>, index: Int) {
        matches.forEach { seen += it.phrase }
        val rows = matches.filter { it.phrase.row != LabelRow.Skip }
        if (rows.any { a -> rows.any { b -> a.phrase.row == b.phrase.row && a.phrase.language != b.phrase.language } }) {
            twoLanguagesInARow = true
        }
        val values = matches.mapIndexed { i, match -> line.substring(match.end, matches.getOrNull(i + 1)?.start ?: line.length) }.toMutableList()
        moveIncludedAmounts(prefix, matches, values)

        val lineSegments = mutableListOf<Segment>()
        matches.forEachIndexed { i, match ->
            if (match.phrase.row == LabelRow.Skip) return@forEachIndexed
            val previous = lineSegments.lastOrNull()
            // "Fat / Lipides 8 g": the first label of a pair carries nothing.
            if (previous != null && previous.phrase.row == match.phrase.row && previous.numbers.isEmpty()) lineSegments.removeAt(lineSegments.lastIndex)
            lineSegments += Segment(match.phrase, values[i], mutableListOf(index))
        }
        if (lineSegments.isEmpty()) return
        inHeader = false
        segments += lineSegments
        if (lineSegments.any { it.numbers.isNotEmpty() }) pending.clear()
        pending += lineSegments.filter { it.numbers.isEmpty() }
    }

    /** "Includes 10g Added Sugars" prints the amount before its label. */
    private fun moveIncludedAmounts(prefix: String, matches: List<PhraseMatch>, values: MutableList<String>) {
        matches.forEachIndexed { i, match ->
            if (match.phrase.row != LabelRow.Of(LabelNutrient.AddedSugars) || amounts(values[i]).isNotEmpty()) return@forEachIndexed
            val before = if (i == 0) prefix else values[i - 1]
            val at = INCLUDES.find(before)?.range?.first ?: if (i == 0) 0 else return@forEachIndexed
            if (amounts(before.substring(at)).isEmpty()) return@forEachIndexed
            values[i] = before.substring(at) + values[i]
            if (i > 0) values[i - 1] = before.substring(0, at)
        }
    }

    private fun amounts(text: String) = LabelNumbers.scan(text).filter { it.unit in AMOUNT_UNITS }

    /**
     * A line of numbers with no label belongs to a row read without numbers:
     * the row level with it in the photo, or the row on the line just above
     * when that is the only row waiting. Two rows waiting and no geometry to
     * tell them apart is a guess, so nothing is placed. An energy row with kJ
     * only also takes a kcal line under it.
     */
    private fun attachValues(index: Int) {
        val line = text[index]
        val numbers = LabelNumbers.scan(line).ifEmpty { return }
        // Bare numbers only go to a row that says its unit: "Calories" / "230", or "kcal 392 118".
        val unitless = numbers.none { it.unit in AMOUNT_UNITS } && ENERGY_UNIT_FIRST.find(line) == null
        val target = alongside(index) ?: below(index, numbers) ?: return
        // Its numbers came, unusable; it is no longer waiting for the next line's.
        pending.remove(target)
        if (unitless && target.phrase.text !in KCAL_LABELS) return
        target.text += " $line"
        target.lines += index
    }

    private fun alongside(index: Int): Segment? {
        val box = source[index].takeIf { it.height > 0 } ?: return null
        return pending.filter { segment ->
            val label = source[segment.lines.first()]
            label.height > 0 && box.left >= label.left && abs(label.centre - box.centre) <= max(label.height, box.height) / 2
        }.minByOrNull { abs(source[it.lines.first()].centre - box.centre) }
    }

    private fun below(index: Int, numbers: List<LabelNumber>): Segment? {
        val last = segments.lastOrNull()?.takeIf { it.lines.last() == index - 1 } ?: return null
        if (pending.size == 1 && pending.single() === last) return last
        val units = numbers.map { it.unit }.filter { it in ENERGY_UNITS }.toSet()
        val has = last.numbers.map { it.unit }.filter { it in ENERGY_UNITS }.toSet()
        val completes = last.phrase.row == ENERGY && units.size == 1 && has.size == 1 && units != has
        return last.takeIf { completes }
    }

    private val LabelTextLine.centre get() = top + height / 2

    // endregion

    // region Draft

    private fun basis(): CarbBasis = when {
        anchors.any { it.first in LabelVocabulary.US_ANCHORS } || seen.any { it.text in LabelVocabulary.US_PHRASES } -> CarbBasis.Us
        anchors.isNotEmpty() || seen.any(LabelVocabulary::isEuPhrase) -> CarbBasis.Eu
        else -> CarbBasis.Unknown
    }

    fun draft(): LabelDraft {
        val basis = basis()
        val printed = columns.ifEmpty {
            // No header: a US panel's one column is per serving; an EU table must print per 100.
            listOf(Column(if (basis == CarbBasis.Us) ColumnRole.Serving else ColumnRole.Per100, prepared = false, serving = null))
        }
        val placed = Placed(
            servingColumn = printed.indexOfFirst { it.role == ColumnRole.Serving && !it.prepared },
            per100Column = printed.indexOfFirst { it.role == ColumnRole.Per100 && !it.prepared },
            count = printed.size,
        )
        segments.forEach { placed.add(it) }
        placed.finish()

        val panelFound = anchors.isNotEmpty() || LabelNutrient.REQUIRED.all { it in placed.perServing || it in placed.per100 }
        if (!panelFound) return LabelDraft(LabelFacts(), LabelLayout.NONE, emptyMap())

        val serving = servingLine ?: printed.firstOrNull { it.role == ColumnRole.Serving && !it.prepared && it.serving != null }?.serving
        val confidence = placed.confidence.toMutableMap()
        if (serving != null) {
            confidence[LabelDraft.SERVING_SIZE] = serving.lines.minOf { source[it].confidence }
        }
        return LabelDraft(
            facts = LabelFacts(
                servingSize = serving?.size,
                servingUnit = serving?.unit,
                servingLabel = serving?.household,
                servingsPerContainer = servingsPerContainer,
                basis = basis,
                perServing = placed.perServing,
                per100 = placed.per100,
            ),
            layout = LabelLayout(
                panelFound = true,
                perServingColumn = printed.any { it.role == ColumnRole.Serving },
                per100Column = printed.any { it.role == ColumnRole.Per100 },
                asPrepared = printed.any { it.prepared },
                dualLanguage = twoLanguagesInARow || anchors.map { it.second }.distinct().size >= 2,
                columns = printed.size,
            ),
            fieldConfidence = confidence,
        )
    }

    /** Values placed into the draft's two columns; every other column's numbers are read and dropped. */
    private inner class Placed(val servingColumn: Int, val per100Column: Int, val count: Int) {
        val perServing = mutableMapOf<LabelNutrient, Double>()
        val per100 = mutableMapOf<LabelNutrient, Double>()
        val confidence = mutableMapOf<String, Double>()
        private val kilojoules = mutableMapOf<Int, Pair<Double, Double>>()
        private val salt = mutableMapOf<Int, Pair<Double, Double>>()

        fun add(segment: Segment) {
            val sure = segment.lines.minOf { source[it].confidence }
            when (val row = segment.phrase.row) {
                ENERGY -> energy(segment, sure)
                LabelRow.Salt -> perColumn(segment, LabelUnit.Gram, null)?.forEachIndexed { i, grams -> salt.getOrPut(i) { grams to sure } }
                is LabelRow.Of -> perColumn(segment, row.nutrient.unit, row.nutrient)?.forEachIndexed { i, value -> put(i, row.nutrient, value, sure) }
                LabelRow.Skip -> Unit
            }
        }

        /** The row's amounts, one per column, or null when the count doesn't match and placing them would be a guess. */
        private fun perColumn(segment: Segment, unit: LabelUnit, nutrient: LabelNutrient?): List<Double>? =
            segment.numbers.filter { it.unit != NumberUnit.Percent }.mapNotNull { it.amountIn(unit, nutrient) }.takeIf { it.size == count }

        private fun energy(segment: Segment, sure: Double) {
            val default = when {
                segment.phrase.text in KCAL_LABELS -> NumberUnit.Kilocalorie
                else -> ENERGY_UNIT_FIRST.find(segment.text)?.groupValues?.get(1)?.let { if (it == "kj") NumberUnit.Kilojoule else NumberUnit.Kilocalorie }
            }
            val numbers = segment.numbers.map { if (it.unit == NumberUnit.None && default != null) it.copy(unit = default) else it }
            fun of(unit: NumberUnit) = numbers.filter { it.unit == unit }.map { if (it.lessThan) it.value / 2 else it.value }.takeIf { it.size == count }
            of(NumberUnit.Kilocalorie)?.forEachIndexed { i, kcal -> put(i, LabelNutrient.Energy, kcal, sure) }
            of(NumberUnit.Kilojoule)?.forEachIndexed { i, kj -> kilojoules.getOrPut(i) { kj to sure } }
        }

        private fun put(column: Int, nutrient: LabelNutrient, value: Double, sure: Double) {
            val into = when (column) {
                servingColumn -> perServing
                per100Column -> per100
                else -> return
            }
            if (nutrient in into) return
            into[nutrient] = value
            confidence[nutrient.wire] = min(confidence[nutrient.wire] ?: 1.0, sure)
        }

        fun finish() {
            kilojoules.forEach { (column, read) -> put(column, LabelNutrient.Energy, read.first / LabelChecks.KJ_PER_KCAL, read.second) }
            salt.forEach { (column, read) -> put(column, LabelNutrient.Sodium, read.first / SALT_PER_SODIUM * 1_000.0, read.second) }
        }
    }

    // endregion

    private companion object {
        const val GRAMS_PER_OZ = 28.3495
        const val ML_PER_FL_OZ = 29.5735

        /** Salt is sodium chloride: 2.5 g of salt carries 1 g of sodium. */
        const val SALT_PER_SODIUM = 2.5

        val ENERGY = LabelRow.Of(LabelNutrient.Energy)
        val KCAL_LABELS = setOf("calories", "calorie")
        val ENERGY_UNITS = setOf(NumberUnit.Kilojoule, NumberUnit.Kilocalorie)
        val AMOUNT_UNITS = setOf(
            NumberUnit.Gram, NumberUnit.Milligram, NumberUnit.Microgram, NumberUnit.Kilojoule,
            NumberUnit.Kilocalorie, NumberUnit.InternationalUnit, NumberUnit.Trace,
        )
        val METRIC_UNITS = setOf(
            NumberUnit.Gram, NumberUnit.Kilogram, NumberUnit.Millilitre, NumberUnit.Litre, NumberUnit.Centilitre,
        )
        val SERVING_UNITS = METRIC_UNITS + setOf(NumberUnit.Ounce, NumberUnit.FluidOunce)

        val SERVING_KEYS = listOf(
            "serving size", "serv size", "serv. size", "portion size", "portionsgrosse", "portionsgroesse",
            "taille de la portion", "taille d'une portion", "porzione da", "tamano de la porcion",
        )
        val SERVINGS_PER_CONTAINER = listOf(
            Regex("""(\d+(?:[.,]\d+)?)\s*servings?\s+per\s+(?:container|package|pack|bottle|can|bag|box)"""),
            Regex("""servings?\s+per\s+(?:container|package|pack|bottle|can|bag|box)\s*:?\s*(?:about\s+|approx\.?\s*)?(\d+(?:[.,]\d+)?)"""),
            Regex("""(\d+(?:[.,]\d+)?)\s*portions?\s+par\s+(?:contenant|emballage|paquet)"""),
            Regex("""(?:pack|package|packung|container)\s+(?:contains|enthalt)\s+(?:about\s+|approx\.?\s*|ca\.?\s*)?(\d+(?:[.,]\d+)?)\s*(?:servings|portions|portionen)"""),
        )

        /** Where a column header starts: "per", "pro", "pour", "je". */
        val PER = Regex("""\b(?:per|pro|pour|par|je|por|para|voor|ogni)\b""")
        val HUNDRED = Regex("""\b100\s?(?:g|gr|ml)\b""")
        val CONTAINER = listOf("container", "package", "contenant", "packung", "envase", "confezione")
        val PREPARED = listOf("prepared", "prepare", "zubereitet", "zubereitung", "preparato", "preparado", "bereid", "made up", "reconstituted")
        val INCLUDES = Regex("""\b(?:includes|include|incl|inkl)\b""")

        /** "kcal 392 118" or "Energy (kJ) 1650": the unit printed before its numbers. */
        val ENERGY_UNIT_FIRST = Regex("""^\W*(kj|kcal)\b""")
    }
}
