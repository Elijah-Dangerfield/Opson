package com.opson.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewFormTest {

    private val read = LabelFactsDto(
        name = "Oats & honey granola bar",
        servingSize = 40.0,
        servingUnit = "g",
        perServing = mapOf("energy_kcal" to 190.0, "protein_g" to 4.0, "carbs_g" to 29.0, "fat_g" to 7.0),
    )
    private val detail = SubmissionDetailDto(
        summary = SubmissionSummaryDto(id = "s1", createdAtEpochMs = 0, status = "pending", origin = "barcode"),
        foodId = "f1",
        parsed = LabelReadingDto(read, confidence = mapOf("protein_g" to 0.5, "name" to 0.95)),
        submitted = read.copy(perServing = read.perServing + ("energy_kcal" to 270.0) + ("calcium_mg" to 20.0)),
    )

    @Test
    fun rowsSetWhatWasReadBesideWhatWasSent() {
        val rows = ReviewForm.rows(detail).associateBy { it.key }

        assertEquals("190", rows.getValue("energy_kcal").read)
        assertEquals("270", rows.getValue("energy_kcal").sent)
        assertTrue(rows.getValue("energy_kcal").needsLook, "the person changed it")
        assertTrue(rows.getValue("protein_g").needsLook, "the model was unsure")
        assertFalse(rows.getValue("name").needsLook)
        assertEquals("g", rows.getValue("serving_size").unit)
        assertTrue("calcium_mg" in rows, "a nutrient anyone gave is listed")
        assertFalse("iron_mg" in rows, "one nobody gave is not")
    }

    @Test
    fun aNutrientGivenPer100IsShownPerServing() {
        val per100 = LabelFactsDto(servingSize = 30.0, per100 = mapOf("energy_kcal" to 380.0))
        assertEquals("114", ReviewForm.text(per100, "energy_kcal"))
    }

    @Test
    fun editsReplaceValuesAndAnEditedNutrientDropsItsPer100() {
        val base = LabelFactsDto(
            name = "x",
            servingSize = 30.0,
            servingUnit = "g",
            perServing = mapOf("energy_kcal" to 150.0),
            per100 = mapOf("energy_kcal" to 400.0, "fat_g" to 10.0),
            acknowledged = listOf("energy_macros_mismatch"),
        )

        val edited = ReviewForm.facts(base, mapOf("energy_kcal" to "120", "name" to " Corn flakes ", "brand" to "", "barcode" to "0123 4567 8905"), unit = "ml")

        assertEquals(120.0, edited.perServing["energy_kcal"])
        assertNull(edited.per100["energy_kcal"])
        assertEquals(10.0, edited.per100["fat_g"])
        assertEquals("Corn flakes", edited.name)
        assertNull(edited.brand)
        assertEquals("012345678905", edited.barcode)
        assertEquals("ml", edited.servingUnit)
        assertEquals(emptyList(), edited.acknowledged, "what the person kept is theirs, not the reviewer's")
        assertNull(ReviewForm.facts(base, mapOf("energy_kcal" to " ")).perServing["energy_kcal"])
    }

    @Test
    fun checksReadAsSentences() {
        assertEquals(
            "Calories 270 vs 234 from 4/4/9 (off by 36 kcal)",
            ReviewForm.describe(LabelCheckDto("energy_macros_mismatch", listOf("energy_kcal"), expected = 234.0, actual = 270.0)),
        )
        assertEquals("Missing: Name, Calories", ReviewForm.describe(LabelCheckDto("missing_required", listOf("name", "energy_kcal"))))
        assertEquals("1.08", ReviewForm.number(1.08))
    }

    @Test
    fun routingReasonsReadAsPlainWords() {
        assertEquals("Not Trusted yet", ReviewForm.reasonLabel("not_trusted"))
        assertEquals("Fat not found in the photo's text", ReviewForm.reasonLabel("not_grounded:fat_g"))
        assertEquals("Second read differs on calories", ReviewForm.reasonLabel("cloud_differs:energy_kcal"))
        assertEquals("Typed or read by the server", ReviewForm.reasonLabel("no_capture"))
        assertEquals("Photo from the library", ReviewForm.reasonLabel("not_from_camera"))
        assertEquals("Would auto-approve (shadow)", ReviewForm.reasonLabel("mode:shadow"))
        assertEquals("Check: calories don't match the macros", ReviewForm.reasonLabel("check:energy_macros_mismatch"))
        assertEquals("Check: brand_new_check", ReviewForm.reasonLabel("check:brand_new_check"))
        assertEquals("some_future_code", ReviewForm.reasonLabel("some_future_code"), "an unknown code is shown as sent")
    }

    @Test
    fun aRowSaysItsLaneAndTheBadgeCarriesTheMode() {
        val routed = SubmissionSummaryDto(
            id = "s1", createdAtEpochMs = 0, status = "approved", origin = "barcode",
            lane = "auto", confidence = 0.914, audit = "pending",
        )
        assertEquals("Auto-approved, 91%, audit pending", ReviewForm.laneLine(routed))
        assertNull(ReviewForm.laneLine(routed.copy(lane = null)), "a submission from before routing has no line")
        assertEquals("Would auto-approve (shadow)", ReviewForm.autoApproveBadge(listOf("mode:shadow")))
        assertEquals("Would auto-approve", ReviewForm.autoApproveBadge(emptyList()))
    }

    @Test
    fun groundingAndTheSecondReadAreOneLineEach() {
        assertEquals(
            "4 of 5 headline numbers found in the photo's text; missing Fat",
            ReviewForm.groundingLine(GroundingDto(grounded = 4, groundable = 5, missing = listOf("fat_g"))),
        )
        assertEquals("m agrees, 1.2 s", ReviewForm.cloudLine(CloudCheckDto("m", readable = true, agrees = true, latencyMs = 1200)))
        assertEquals(
            "m differs on calories, fat, 900 ms",
            ReviewForm.cloudLine(CloudCheckDto("m", readable = true, agrees = false, differs = listOf("energy_kcal", "fat_g"), latencyMs = 900)),
        )
    }

    @Test
    fun thePhoneDraftGetsItsOwnColumnAndFlagsAChangedField() {
        val withDraft = detail.copy(
            capture = CaptureDto(engine = "vision", source = "camera", draft = read.copy(perServing = read.perServing + ("fat_g" to 9.0))),
        )
        val rows = ReviewForm.rows(withDraft).associateBy { it.key }
        assertEquals("9", rows.getValue("fat_g").draft)
        assertTrue(rows.getValue("fat_g").needsLook, "the person changed what the phone drafted")
        assertNull(ReviewForm.rows(detail).first().draft, "no capture, no draft")
    }
}
