package com.opson.libraries.foods

import com.opson.libraries.nutrition.MacroEnergyCheck
import com.opson.libraries.nutrition.Metric
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Measured signals behind a submission's confidence. Every one is a number or flag computed from the photo's text and the values sent. */
data class LabelSignals(
    val panelFound: Boolean,
    /** The draft's [LabelDraft.headlineFound]; 0 with no draft. */
    val headlineFound: Int,
    /** Lowest [LabelDraft.fieldConfidence] over the headline fields the draft filled. */
    val ocrMinConfidence: Double?,
    /** |declared kcal - macro kcal| / max(declared, 1) on the submitted facts, in the column [LabelChecks]' energy check reads. */
    val atwaterGap: Double?,
    /** Worst relative gap between per 100 × serving / 100 and per serving over the nutrients both columns have. */
    val servingGap: Double?,
    /** Nutrient columns the panel prints. */
    val columns: Int,
    val asPrepared: Boolean,
    val dualLanguage: Boolean,
    /** Submitted headline values found among the OCR number tokens. */
    val grounded: Int,
    /** Submitted headline values there were to look for. */
    val groundable: Int,
    /** Form keys of the submitted headline values not found. */
    val ungrounded: List<String>,
    /** Headline fields the draft filled and the person changed. */
    val edited: Int,
    /** Median line height as a share of image height. */
    val medianLineHeight: Double?,
    val checksFailed: Int,
    val blockingFailed: Boolean,
)

/**
 * How far to trust a label submission, from [LabelSignals] measured on the
 * photo's OCR text and the values sent. Every number in [score] is a
 * hand-set starting point; once about 1,000 labelled outcomes exist they get
 * replaced by a fitted model (`docs/plans/feedback-2026-10-29/ai-options.md`).
 */
object LabelConfidence {

    /** Without a panel the numbers came from somewhere other than this photo's table. Worth a look, never trusted. */
    private const val NO_PANEL_CAP = 0.3

    /** A blocking check means the submission cannot stand as sent. */
    private const val BLOCKING_CAP = 0.1

    /** Vision and ML Kit line confidences run from about 0.3 (barely legible) to 1.0. */
    private const val OCR_FLOOR = 0.3

    /** The factor at [OCR_FLOOR]: a blurry read halves trust rather than ending it, since grounding still has to pass. */
    private const val OCR_FLOOR_FACTOR = 0.5

    /** With no OCR line behind any headline field there is nothing to say the text was read well, or badly. */
    private const val OCR_UNKNOWN_FACTOR = 0.7

    /** [MacroEnergyCheck]'s relative tolerance: past it the energy and macros disagree, which is more often a misread digit than a label fault. */
    private const val ATWATER_GAP = 0.12
    private const val ATWATER_PENALTY = 0.6

    /** [LabelChecks]' per serving against per 100 tolerance. A gap past it is a wrong serving or a wrong column. */
    private const val SERVING_GAP = 0.10
    private const val SERVING_PENALTY = 0.7

    /** Reading the wrong column is the most common plausible error, and grounding cannot catch it: both columns are in the photo. */
    private const val COLUMNS_PENALTY = 0.9

    /** An as-prepared column is a second set of real numbers for the same rows. */
    private const val PREPARED_PENALTY = 0.9

    /** A second language doubles the tokens a wrong number can ground against by chance. */
    private const val DUAL_LANGUAGE_PENALTY = 0.95

    /** Text under about 1.2% of the image height is a far or small shot, where 1 and 7, 5 and 6 start trading places. */
    private const val TINY_TEXT = 0.012
    private const val TINY_TEXT_PENALTY = 0.85

    /** A typed kcal can be grounded by the kJ figure: within 1%, the label's own kJ rounding. */
    private const val KJ_MATCH = 0.01

    private const val GRAMS_PER_OZ = 28.3495
    private const val ML_PER_FL_OZ = 29.5735

    /** [LabelChecks]' sugar alcohols at 2.4 kcal a gram, and [MacroEnergyCheck]'s fiber at 2. */
    private const val POLYOL_KCAL_PER_G = 2.4
    private const val CARB_KCAL_PER_G = 4.0
    private const val FIBER_KCAL_PER_G = 2.0

    fun signals(draft: LabelDraft?, lines: List<LabelTextLine>, submitted: LabelFacts): LabelSignals {
        val layout = draft?.layout ?: LabelTextParser.parse(lines).layout
        val numbers = LabelNumbers.scan(lines)
        val found = LabelDraft.HEADLINE.mapNotNull { key -> groundedField(key, submitted, numbers)?.let { key to it } }
        val checks = LabelChecks.check(submitted)
        return LabelSignals(
            panelFound = layout.panelFound,
            headlineFound = draft?.headlineFound ?: 0,
            ocrMinConfidence = draft?.let { d -> LabelDraft.HEADLINE.mapNotNull { d.fieldConfidence[it] }.minOrNull() },
            atwaterGap = atwaterGap(submitted),
            servingGap = servingGap(submitted),
            columns = layout.columns,
            asPrepared = layout.asPrepared,
            dualLanguage = layout.dualLanguage,
            grounded = found.count { it.second },
            groundable = found.size,
            ungrounded = found.filterNot { it.second }.map { it.first },
            edited = draft?.let { edited(it.facts, submitted) } ?: 0,
            medianLineHeight = median(lines.map { it.height }.filter { it > 0 }),
            checksFailed = checks.size,
            blockingFailed = checks.any { it.code.blocking },
        )
    }

    /**
     * 0 to 1. Gates first (no panel, a blocking check), then the share of
     * headline values grounded in the photo, times the OCR confidence mapped
     * from 0.3..1.0 onto 0.5..1.0, times a penalty for each risk the layout
     * or the residuals show. The reason for each number is on its constant.
     * The edits and non-blocking checks are logged, not scored: an edit can
     * be a fix, and the checks that matter are already the two residuals.
     */
    fun score(signals: LabelSignals): Double {
        val cap = when {
            signals.blockingFailed -> BLOCKING_CAP
            !signals.panelFound -> NO_PANEL_CAP
            else -> 1.0
        }
        val grounding = if (signals.groundable == 0) 0.0 else signals.grounded.toDouble() / signals.groundable
        var score = grounding * ocrFactor(signals.ocrMinConfidence)
        if ((signals.atwaterGap ?: 0.0) > ATWATER_GAP) score *= ATWATER_PENALTY
        if ((signals.servingGap ?: 0.0) > SERVING_GAP) score *= SERVING_PENALTY
        if (signals.columns >= 2) score *= COLUMNS_PENALTY
        if (signals.asPrepared) score *= PREPARED_PENALTY
        if (signals.dualLanguage) score *= DUAL_LANGUAGE_PENALTY
        if ((signals.medianLineHeight ?: 1.0) < TINY_TEXT) score *= TINY_TEXT_PENALTY
        return min(score, cap).coerceIn(0.0, 1.0)
    }

    /**
     * Whether [value] (in the unit the label printed it, or kcal also as
     * kJ × 4.184) appears among the number tokens of [lines]: decimal commas
     * normalised, "<1", "0.5g", "12g" glued to units, rounding to the printed
     * precision.
     */
    fun grounded(value: Double, lines: List<LabelTextLine>): Boolean = groundedIn(value, LabelNumbers.scan(lines), energy = true)

    private fun groundedIn(value: Double, numbers: List<LabelNumber>, energy: Boolean): Boolean = numbers.any { number ->
        when (number.unit) {
            NumberUnit.Percent -> false
            NumberUnit.Kilojoule -> printedAs(value, number) || (energy && abs(value * LabelChecks.KJ_PER_KCAL - number.value) <= KJ_MATCH * number.value)
            else -> printedAs(value, number)
        }
    }

    /** [value] rounds to [number] at its printed precision; "<1" also stands for the half the parser reads it as. */
    private fun printedAs(value: Double, number: LabelNumber): Boolean {
        val scale = 10.0.pow(number.decimals)
        fun rounded(v: Double) = floor(v * scale + 0.5 + 1e-9)
        val printed = rounded(number.value)
        return rounded(value) == printed || (number.lessThan && rounded(value * 2) == printed)
    }

    /** Whether the headline field [key] of [facts] is in the photo, or null when it was not submitted. */
    private fun groundedField(key: String, facts: LabelFacts, numbers: List<LabelNumber>): Boolean? {
        if (key == LabelDraft.SERVING_SIZE) return facts.servingSize?.let { groundedServing(it, facts.servingUnit, numbers) }
        val nutrient = LabelNutrient.fromWire(key) ?: return null
        val values = listOfNotNull(facts.perServing[nutrient], facts.per100[nutrient]).ifEmpty { return null }
        return values.any { groundedIn(it, numbers, energy = nutrient == LabelNutrient.Energy) }
    }

    private fun groundedServing(size: Double, unit: ServingUnit?, numbers: List<LabelNumber>): Boolean = numbers.any { number ->
        val grams = unit != ServingUnit.Milliliter
        val millilitres = unit != ServingUnit.Gram
        when (number.unit) {
            NumberUnit.Gram -> grams && printedAs(size, number)
            NumberUnit.Millilitre -> millilitres && printedAs(size, number)
            NumberUnit.Ounce -> grams && abs(size - number.value * GRAMS_PER_OZ) <= KJ_MATCH * size
            NumberUnit.FluidOunce -> millilitres && abs(size - number.value * ML_PER_FL_OZ) <= KJ_MATCH * size
            else -> false
        }
    }

    /**
     * The energy gap in the column [LabelChecks] checks (per serving when it
     * has energy and all three macros, else per 100), with the same grams:
     * sugar alcohols at 2.4 kcal, and the smallest gap over the ways
     * [MacroEnergyCheck] counts fiber, so a label the check passes reads as a
     * small gap here too.
     */
    private fun atwaterGap(facts: LabelFacts): Double? {
        val column = listOf(facts.perServing, facts.per100).firstOrNull { values ->
            LabelNutrient.REQUIRED.all { it in values }
        } ?: return null
        val declared = column.getValue(LabelNutrient.Energy)
        val polyols = column[LabelNutrient.SugarAlcohols] ?: 0.0
        val grams = mapOf(
            Metric.Protein to column.getValue(LabelNutrient.Protein),
            Metric.Carbs to max(0.0, column.getValue(LabelNutrient.Carbs) - polyols * (1 - POLYOL_KCAL_PER_G / CARB_KCAL_PER_G)),
            Metric.Fat to column.getValue(LabelNutrient.Fat),
        )
        val macro = MacroEnergyCheck.macroKcal(grams) ?: return null
        val fiber = (column[LabelNutrient.Fiber] ?: 0.0) * FIBER_KCAL_PER_G
        return listOf(macro, macro - fiber, macro + fiber).minOf { abs(declared - it) } / max(declared, 1.0)
    }

    /** The gap per nutrient, 0 when it is within the label's rounding ([LabelChecks]' absolute allowance). */
    private fun servingGap(facts: LabelFacts): Double? {
        val size = facts.servingSize?.takeIf { it > 0 } ?: return null
        val shared = facts.perServing.keys.intersect(facts.per100.keys).ifEmpty { return null }
        return shared.maxOf { key ->
            val printed = facts.perServing.getValue(key)
            val expected = facts.per100.getValue(key) * size / 100.0
            val gap = abs(printed - expected)
            if (gap <= rounding(key)) 0.0 else gap / max(printed, expected)
        }
    }

    private fun rounding(key: LabelNutrient): Double = when (key.unit) {
        LabelUnit.Kcal -> 10.0
        LabelUnit.Gram -> 1.0
        LabelUnit.Milligram -> 10.0
        LabelUnit.Microgram -> 5.0
    }

    /** A field the draft left empty and the person filled is not an edit; one it filled and they changed or cleared is. */
    private fun edited(draft: LabelFacts, submitted: LabelFacts): Int {
        val serving = draft.servingSize?.let { !same(it, submitted.servingSize) || draft.servingUnit != submitted.servingUnit } ?: false
        val nutrients = LabelNutrient.REQUIRED.count { key ->
            listOf(draft.perServing[key] to submitted.perServing[key], draft.per100[key] to submitted.per100[key])
                .any { (drafted, sent) -> drafted != null && !same(drafted, sent) }
        }
        return nutrients + if (serving) 1 else 0
    }

    /** Equal beyond rounding: within 1%, or 0.05 for small numbers. */
    private fun same(a: Double, b: Double?): Boolean = b != null && abs(a - b) <= max(0.05, 0.01 * max(abs(a), abs(b)))

    private fun ocrFactor(confidence: Double?): Double {
        confidence ?: return OCR_UNKNOWN_FACTOR
        val share = ((confidence - OCR_FLOOR) / (1 - OCR_FLOOR)).coerceIn(0.0, 1.0)
        return OCR_FLOOR_FACTOR + (1 - OCR_FLOOR_FACTOR) * share
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
