package com.opson.libraries.foods

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LabelModelMergeTest {

    private val lines = listOf(
        "Nutrition Facts",
        "Serving size 2/3 cup (55g)",
        "Calories 230",
        "Total Fat 8g",
        "Total Carbohydrate 37g",
        "Protein 3g",
    ).map { LabelTextLine(it, 0.95) }

    private fun draft(facts: LabelFacts, confidence: Map<String, Double> = emptyMap()) = LabelDraft(
        facts = facts,
        layout = LabelLayout.NONE.copy(panelFound = true, perServingColumn = true),
        fieldConfidence = confidence,
    )

    @Test
    fun aGapTheParserLeftIsFilledWhenTheNumberIsPrinted() {
        val parsed = draft(
            LabelFacts(servingSize = 55.0, servingUnit = ServingUnit.Gram, perServing = mapOf(LabelNutrient.Energy to 230.0)),
            mapOf(LabelDraft.SERVING_SIZE to 0.95, LabelNutrient.Energy.wire to 0.95),
        )
        val model = LabelFacts(perServing = mapOf(LabelNutrient.Fat to 8.0, LabelNutrient.Protein to 3.0))

        val merged = LabelModelMerge.merge(parsed, model, lines)

        assertEquals(8.0, merged.facts.perServing[LabelNutrient.Fat])
        assertEquals(LabelModelMerge.FILLED, merged.fieldConfidence[LabelNutrient.Fat.wire])
        assertEquals(0.95, merged.fieldConfidence[LabelNutrient.Energy.wire], "agreeing fields keep their confidence")
    }

    @Test
    fun aNumberTheTextDoesNotHoldIsDropped() {
        val parsed = draft(LabelFacts())
        val model = LabelFacts(servingSize = 40.0, perServing = mapOf(LabelNutrient.Fat to 12.0))

        val merged = LabelModelMerge.merge(parsed, model, lines)

        assertNull(merged.facts.servingSize)
        assertNull(merged.facts.perServing[LabelNutrient.Fat])
    }

    @Test
    fun aDisagreementKeepsTheParsersNumberAndAsksForALook() {
        val parsed = draft(LabelFacts(perServing = mapOf(LabelNutrient.Carbs to 37.0)), mapOf(LabelNutrient.Carbs.wire to 0.9))
        val model = LabelFacts(perServing = mapOf(LabelNutrient.Carbs to 3.0))

        val merged = LabelModelMerge.merge(parsed, model, lines)

        assertEquals(37.0, merged.facts.perServing[LabelNutrient.Carbs])
        assertEquals(LabelModelMerge.DISPUTED, merged.fieldConfidence[LabelNutrient.Carbs.wire])
    }

    @Test
    fun aServingSizeOnlyTheModelFoundIsTakenInGrams() {
        val merged = LabelModelMerge.merge(draft(LabelFacts()), LabelFacts(servingSize = 55.0), lines)

        assertEquals(55.0, merged.facts.servingSize)
        assertEquals(ServingUnit.Gram, merged.facts.servingUnit)
        assertEquals(LabelModelMerge.FILLED, merged.fieldConfidence[LabelDraft.SERVING_SIZE])
    }
}
