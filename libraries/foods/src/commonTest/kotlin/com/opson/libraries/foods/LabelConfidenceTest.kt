package com.opson.libraries.foods

import com.opson.libraries.foods.LabelTextFixtures.lines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LabelConfidenceTest {

    private fun submitted(draft: LabelDraft) = draft.facts.copy(name = "Granola")

    private fun cleanUs(): LabelSignals {
        val draft = LabelTextParser.parse(LabelTextFixtures.US_SINGLE)
        return LabelConfidence.signals(draft, LabelTextFixtures.US_SINGLE, submitted(draft))
    }

    @Test
    fun aCleanGroundedSubmissionScoresHigh() {
        val signals = cleanUs()
        assertEquals(5, signals.grounded)
        assertEquals(5, signals.groundable)
        assertEquals(emptyList(), signals.ungrounded)
        assertEquals(0, signals.edited)
        assertEquals(0, signals.checksFailed)
        assertEquals(1.0, signals.ocrMinConfidence)
        assertEquals(1, signals.columns)
        assertTrue(signals.atwaterGap!! < 0.12)
        assertTrue(LabelConfidence.score(signals) > 0.85)
    }

    @Test
    fun aTypedNumberNotInThePhotoIsUngroundedAndScoresLower() {
        val draft = LabelTextParser.parse(LabelTextFixtures.US_SINGLE)
        val typo = submitted(draft).let { it.copy(perServing = it.perServing + (LabelNutrient.Protein to 5.0)) }
        val signals = LabelConfidence.signals(draft, LabelTextFixtures.US_SINGLE, typo)
        assertEquals(listOf(LabelNutrient.Protein.wire), signals.ungrounded)
        assertEquals(4, signals.grounded)
        assertEquals(1, signals.edited)
        assertTrue(LabelConfidence.score(signals) < LabelConfidence.score(cleanUs()))
    }

    @Test
    fun anAtwaterMismatchLowersTheScore() {
        // 495 is in the photo (the serving's kJ), so it grounds; the macros say 394.
        val draft = LabelTextParser.parse(LabelTextFixtures.EU_TWO_COLUMNS)
        val sent = submitted(draft).let { it.copy(perServing = emptyMap(), per100 = it.per100 + (LabelNutrient.Energy to 495.0)) }
        val signals = LabelConfidence.signals(draft, LabelTextFixtures.EU_TWO_COLUMNS, sent)
        assertEquals(emptyList(), signals.ungrounded)
        assertTrue(signals.atwaterGap!! > 0.12)
        val clean = LabelConfidence.signals(draft, LabelTextFixtures.EU_TWO_COLUMNS, submitted(draft))
        assertTrue(LabelConfidence.score(signals) < LabelConfidence.score(clean))
    }

    @Test
    fun twoColumnsLowerTheScore() {
        val draft = LabelTextParser.parse(LabelTextFixtures.EU_TWO_COLUMNS)
        val signals = LabelConfidence.signals(draft, LabelTextFixtures.EU_TWO_COLUMNS, submitted(draft))
        assertEquals(2, signals.columns)
        assertEquals(0.0, signals.servingGap)
        assertTrue(LabelConfidence.score(signals) < LabelConfidence.score(signals.copy(columns = 1)))
    }

    @Test
    fun aBlockingCheckCapsTheScore() {
        val draft = LabelTextParser.parse(LabelTextFixtures.US_SINGLE)
        val signals = LabelConfidence.signals(draft, LabelTextFixtures.US_SINGLE, draft.facts)
        assertTrue(signals.blockingFailed)
        assertTrue(LabelConfidence.score(signals) <= 0.1)
    }

    @Test
    fun noPanelCapsTheScore() {
        val sent = LabelFacts(name = "Bar", servingSize = 40.0, servingUnit = ServingUnit.Gram, perServing = mapOf(LabelNutrient.Protein to 2.99))
        val signals = LabelConfidence.signals(null, LabelTextFixtures.RECEIPT, sent)
        assertFalse(signals.panelFound)
        assertEquals(0, signals.headlineFound)
        assertTrue(LabelConfidence.score(signals) <= 0.3)
    }

    @Test
    fun theOtherPenaltiesEachLowerTheScore() {
        val clean = cleanUs()
        val base = LabelConfidence.score(clean)
        listOf(
            clean.copy(servingGap = 0.2),
            clean.copy(asPrepared = true),
            clean.copy(dualLanguage = true),
            clean.copy(medianLineHeight = 0.008),
            clean.copy(ocrMinConfidence = 0.5),
        ).forEach { assertTrue(LabelConfidence.score(it) < base, "$it") }
    }

    @Test
    fun groundingNormalisesHowNumbersArePrinted() {
        assertTrue(LabelConfidence.grounded(4.5, lines("davon gesättigte Fettsäuren 4,5 g")))
        assertTrue(LabelConfidence.grounded(12.5, lines("Fat 12,5 g")))
        assertFalse(LabelConfidence.grounded(12.0, lines("Fat 12,5 g")))
        assertTrue(LabelConfidence.grounded(380.0, lines("Brennwert 1590 kJ")))
        assertTrue(LabelConfidence.grounded(1590.0 / 4.184, lines("Brennwert 1590 kJ")))
        assertTrue(LabelConfidence.grounded(12.0, lines("Total Sugars 12g")))
        assertFalse(LabelConfidence.grounded(13.0, lines("Total Sugars 12g")))
        assertTrue(LabelConfidence.grounded(0.5, lines("Dietary Fiber <1g")))
        assertTrue(LabelConfidence.grounded(1.0, lines("Dietary Fiber <1g")))
        assertTrue(LabelConfidence.grounded(0.0, lines("Trans Fat Og")))
        assertFalse(LabelConfidence.grounded(10.0, lines("Sodium 920mg 10%")))
    }

    @Test
    fun theServingGroundsAgainstThePrintedServing() {
        val draft = LabelTextParser.parse(LabelTextFixtures.CANADIAN)
        val signals = LabelConfidence.signals(draft, LabelTextFixtures.CANADIAN, submitted(draft))
        assertEquals(5, signals.grounded)
        assertTrue(signals.dualLanguage)
        val wrongServing = submitted(draft).copy(servingSize = 240.0)
        assertEquals(listOf(LabelDraft.SERVING_SIZE), LabelConfidence.signals(draft, LabelTextFixtures.CANADIAN, wrongServing).ungrounded)
    }
}
