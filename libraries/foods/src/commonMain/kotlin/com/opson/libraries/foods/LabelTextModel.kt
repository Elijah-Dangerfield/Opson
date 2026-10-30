@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.foods

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.math.abs
import kotlin.math.max
import kotlin.native.HiddenFromObjC

/**
 * An on-device language model asked to pull a nutrition panel's numbers out of its OCR text, as a
 * second opinion beside [LabelTextParser]. On iOS 26 that is Apple's Foundation Models (free, on
 * the phone, nothing sent anywhere); everywhere else [LabelModelResult.Unavailable]. A prototype
 * behind `labels.onDeviceModel`: see docs/research/on-device-label-model.md.
 *
 * The model only ever sees text the phone already read, never the photo, and what it returns is
 * not trusted on its own: [LabelModelMerge] keeps only numbers that are printed in that text.
 */
@HiddenFromObjC
interface LabelTextModel {
    suspend fun extract(lines: List<LabelTextLine>): LabelModelResult
}

@HiddenFromObjC
sealed interface LabelModelResult {
    /** [model] names what read it, for the record sent with a submission. */
    data class Read(val facts: LabelFacts, val model: String) : LabelModelResult

    /** No model on this phone: an older OS, Apple Intelligence off, the model still downloading, or Android. */
    data object Unavailable : LabelModelResult

    data object Failed : LabelModelResult
}

/**
 * The parser's draft with a model's reading laid over it, deterministically. The parser stays the
 * reader of record; the model can only fill a gap or cast doubt:
 *  - a field the parser left empty takes the model's number when that number is printed in the
 *    OCR text, marked [FILLED] so the form asks for a look;
 *  - a field both read and disagree on keeps the parser's number, marked [DISPUTED];
 *  - a field both read alike keeps its confidence.
 * A number the text does not contain is the model inventing one, and is dropped.
 */
object LabelModelMerge {

    /** Confidence of a field only the model read: low enough that the form marks it "Check this". */
    const val FILLED = 0.5

    /** Confidence of a field the two readers disagree on. */
    const val DISPUTED = 0.4

    fun merge(draft: LabelDraft, model: LabelFacts, lines: List<LabelTextLine>): LabelDraft {
        val facts = draft.facts
        val confidence = draft.fieldConfidence.toMutableMap()
        fun printed(value: Double) = value.isFinite() && value >= 0.0 && LabelConfidence.grounded(value, lines)

        var servingSize = facts.servingSize
        var servingUnit = facts.servingUnit
        val modelSize = model.servingSize
        if (modelSize != null) {
            when {
                servingSize == null && printed(modelSize) -> {
                    servingSize = modelSize
                    servingUnit = model.servingUnit ?: ServingUnit.Gram
                    confidence[LabelDraft.SERVING_SIZE] = FILLED
                }
                servingSize != null && !close(servingSize, modelSize) -> confidence[LabelDraft.SERVING_SIZE] = DISPUTED
            }
        }

        fun column(parsed: Map<LabelNutrient, Double>, read: Map<LabelNutrient, Double>, keyed: Boolean): Map<LabelNutrient, Double> {
            val out = parsed.toMutableMap()
            read.forEach { (nutrient, value) ->
                val mine = parsed[nutrient]
                when {
                    mine == null && printed(value) -> {
                        out[nutrient] = value
                        if (keyed) confidence[nutrient.wire] = minOf(confidence[nutrient.wire] ?: 1.0, FILLED)
                    }
                    mine != null && !close(mine, value) -> confidence[nutrient.wire] = minOf(confidence[nutrient.wire] ?: 1.0, DISPUTED)
                }
            }
            return out
        }

        val perServing = column(facts.perServing, model.perServing, keyed = true)
        val per100 = column(facts.per100, model.per100, keyed = facts.perServing.isEmpty())
        return draft.copy(
            facts = facts.copy(
                servingSize = servingSize,
                servingUnit = servingUnit,
                servingsPerContainer = facts.servingsPerContainer ?: model.servingsPerContainer?.takeIf(::printed),
                perServing = perServing,
                per100 = per100,
            ),
            fieldConfidence = confidence,
        )
    }

    /** Label rounding: 1 unit or 2%, whichever is larger. */
    private fun close(a: Double, b: Double): Boolean = abs(a - b) <= max(1.0, 0.02 * max(abs(a), abs(b)))
}
