package com.opson.server.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubmissionRouterTest {

    /** A US granola panel as the phone's OCR returns it. */
    private val lines = listOf(
        "Nutrition Facts",
        "8 servings per container",
        "Serving size 2/3 cup (55g)",
        "Calories 230",
        "Total Fat 8g 10%",
        "Sodium 160mg 7%",
        "Total Carbohydrate 37g 13%",
        "Dietary Fiber 4g",
        "Protein 3g",
    ).map { CaptureLine(it, 0.95) }

    private val facts = LabelFacts(
        name = "Granola",
        servingSize = 55.0,
        servingUnit = ServingUnit.Gram,
        basis = CarbBasis.Us,
        perServing = mapOf(
            NutrientKey.Energy to 230.0,
            NutrientKey.Fat to 8.0,
            NutrientKey.Carbs to 37.0,
            NutrientKey.Fiber to 4.0,
            NutrientKey.Protein to 3.0,
            NutrientKey.Sodium to 160.0,
        ),
    )

    private val capture = LabelCapture("vision", CaptureSource.Camera, lines, facts, score = 0.97, signalsJson = null)
    private val agreeing = CloudCheck("claude-haiku-5-5", readable = true, agrees = true, differs = emptyList(), latencyMs = 900)

    private fun decide(
        trust: TrustLevel = TrustLevel.Trusted,
        mode: AutoApproveMode = AutoApproveMode.On,
        capture: LabelCapture? = this.capture,
        facts: LabelFacts = this.facts,
        cloud: CloudCheck? = agreeing,
        cloudConfigured: Boolean = true,
    ): RoutingDecision {
        val checks = LabelChecks.check(facts)
        val grounding = capture?.let { LabelGrounding.check(facts, it.lines) }
        return SubmissionRouter.decide(trust, mode, capture, checks, grounding, cloud, cloudConfigured)
    }

    @Test
    fun everyHeadlineNumberOfACleanPanelIsGrounded() {
        val grounding = LabelGrounding.check(facts, lines)

        assertEquals(Grounding(grounded = 5, groundable = 5, missing = emptyList()), grounding)
    }

    @Test
    fun aNumberTypedFromSomewhereElseIsNotGrounded() {
        val typed = facts.copy(perServing = facts.perServing + (NutrientKey.Protein to 7.0))

        assertEquals(listOf("protein_g"), LabelGrounding.check(typed, lines).missing)
    }

    @Test
    fun aNumberOnAnotherRowDoesNotGroundAField() {
        // "2/3 cup" holds a 3, but not on the protein row.
        val typed = facts.copy(perServing = facts.perServing + (NutrientKey.Protein to 2.0))

        assertEquals(listOf("protein_g"), LabelGrounding.check(typed, lines).missing)
    }

    @Test
    fun groundingReadsDecimalCommasKilojoulesAndCommonOcrSlips() {
        val tokens = LabelGrounding.tokens(listOf("Fett 12,5 g", "Brennwert 1590 kJ", "Trans Fat Og", "Sodium l60mg", "Potassium 1,200mg").map { CaptureLine(it, 1.0) })

        assertTrue(LabelGrounding.grounded(12.5, tokens))
        assertTrue(LabelGrounding.grounded(380.0, tokens, isEnergy = true))
        assertTrue(LabelGrounding.grounded(0.0, tokens))
        assertTrue(LabelGrounding.grounded(160.0, tokens))
        assertTrue(LabelGrounding.grounded(1200.0, tokens))
        assertFalse(LabelGrounding.grounded(13.0, tokens))
    }

    @Test
    fun aTrustedBuildersCleanCameraScanThatTheCloudAgreesWithIsAutoApproved() {
        val decision = decide()

        assertEquals(Lane.Auto, decision.lane)
        assertTrue(decision.wouldAutoApprove)
        assertEquals(emptyList(), decision.reasons)
    }

    @Test
    fun inShadowModeTheSameScanGoesToAPersonWithTheShadowVerdictKept() {
        val decision = decide(mode = AutoApproveMode.Shadow)

        assertEquals(Lane.Review, decision.lane)
        assertTrue(decision.wouldAutoApprove)
        assertEquals(listOf("mode:shadow"), decision.reasons)
    }

    @Test
    fun aBuilderWhoIsNotTrustedYetIsReviewed() {
        val decision = decide(trust = TrustLevel.Builder)

        assertEquals(Lane.Review, decision.lane)
        assertFalse(decision.wouldAutoApprove)
        assertEquals(listOf("not_trusted"), decision.reasons)
    }

    @Test
    fun aCloudReadThatDisagreesSendsItToAPersonNamingTheField() {
        val decision = decide(cloud = agreeing.copy(agrees = false, differs = listOf("energy_kcal")))

        assertEquals(Lane.Review, decision.lane)
        assertTrue("cloud_differs:energy_kcal" in decision.reasons)
    }

    @Test
    fun anUngroundedNumberCapsConfidenceAndIsNamed() {
        val typed = facts.copy(perServing = facts.perServing + (NutrientKey.Protein to 7.0))
        val decision = decide(facts = typed)

        assertTrue("not_grounded:protein_g" in decision.reasons)
        assertTrue((decision.confidence ?: 1.0) <= 0.5)
    }

    @Test
    fun withoutACloudReaderOnlyAVeryConfidentPhoneReadingQualifies() {
        assertEquals(Lane.Auto, decide(cloud = null, cloudConfigured = false).lane)
        val unsure = decide(capture = capture.copy(score = 0.9), cloud = null, cloudConfigured = false)
        assertEquals(Lane.Review, unsure.lane)
        assertEquals(listOf("low_confidence"), unsure.reasons)
    }

    @Test
    fun aFailedCloudCallOnAServerThatHasOneIsNotAPass() {
        val decision = decide(cloud = null, cloudConfigured = true)

        assertTrue("no_cloud_read" in decision.reasons)
        assertEquals(Lane.Review, decision.lane)
    }

    @Test
    fun aPhotoFromTheLibraryOrNoPhoneReadingIsReviewed() {
        assertTrue("not_from_camera" in decide(capture = capture.copy(source = CaptureSource.Library)).reasons)
        assertTrue("no_capture" in decide(capture = null).reasons)
    }

    @Test
    fun aFailingCheckIsReviewedAndListedFirst() {
        val off = facts.copy(perServing = facts.perServing + (NutrientKey.Energy to 400.0))
        val decision = decide(facts = off, capture = capture.copy(lines = lines + CaptureLine("Calories 400", 0.9)))

        assertEquals(Lane.Review, decision.lane)
        assertTrue(decision.reasons.any { it.startsWith("check:") })
    }

    @Test
    fun theFirstTwentyAutoApprovalsAreAllAuditedThenTenPercent() {
        assertTrue(SubmissionRouter.audits(autoApprovedSinceTrusted = 0, rate = 0.1, draw = 0.99))
        assertTrue(SubmissionRouter.audits(autoApprovedSinceTrusted = 19, rate = 0.1, draw = 0.99))
        assertFalse(SubmissionRouter.audits(autoApprovedSinceTrusted = 20, rate = 0.1, draw = 0.5))
        assertTrue(SubmissionRouter.audits(autoApprovedSinceTrusted = 200, rate = 0.1, draw = 0.05))
    }

    @Test
    fun twoReadsAgreeOnlyWhenEveryHeadlineFieldMatches() {
        assertEquals(emptyList(), HeadlineAgreement.readsDiffer(facts, facts.copy(name = null)))
        val misread = facts.copy(perServing = facts.perServing + (NutrientKey.Protein to 8.0))
        assertEquals(listOf("protein_g"), HeadlineAgreement.readsDiffer(facts, misread))
        val blind = facts.copy(servingSize = null, servingUnit = null, perServing = emptyMap())
        assertEquals(Headline.KEYS, HeadlineAgreement.readsDiffer(facts, blind))
    }

    @Test
    fun aReviewerTweakWithinLabelRoundingStillCountsAsAgreement() {
        val rounded = facts.copy(perServing = facts.perServing + (NutrientKey.Energy to 235.0) + (NutrientKey.Fat to 8.5))
        val changed = facts.copy(perServing = facts.perServing + (NutrientKey.Energy to 300.0))

        assertTrue(HeadlineAgreement.reviewerKept(facts, rounded))
        assertFalse(HeadlineAgreement.reviewerKept(facts, changed))
    }
}
