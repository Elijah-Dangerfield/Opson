package com.opson.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewBundleTest {

    private fun summary(
        reasons: List<String> = listOf("not_trusted", "no_cloud_read"),
        confidence: Double? = 0.9,
        failedChecks: List<String> = emptyList(),
        hasPhotos: Boolean = true,
        lane: String? = "review",
        status: String = "pending",
        id: String = "s1",
    ) = SubmissionSummaryDto(
        id = id,
        createdAtEpochMs = 0,
        status = status,
        origin = "barcode",
        name = "Granola bar",
        failedChecks = failedChecks,
        hasPhotos = hasPhotos,
        lane = lane,
        reasons = reasons,
        confidence = confidence,
    )

    private val facts = LabelFactsDto(
        name = "Granola bar",
        brand = "Oatly Good",
        servingSize = 40.0,
        servingUnit = "g",
        perServing = mapOf("energy_kcal" to 190.0, "protein_g" to 4.0, "carbs_g" to 29.0, "fat_g" to 7.0),
        per100 = mapOf("energy_kcal" to 475.0),
    )

    private val detail = SubmissionDetailDto(
        summary = summary(),
        foodId = "f1",
        submitted = facts,
        capture = CaptureDto(engine = "vision", source = "camera", lines = listOf(CaptureLineDto("Calories 190", 0.98)), draft = facts),
        routing = RoutingDto(lane = "review", wouldAutoApprove = false, trust = "anyone", mode = "shadow", grounding = GroundingDto(5, 5)),
    )

    @Test
    fun aCleanPhoneReadingCanBeBulkApproved() {
        assertTrue(ReviewBundle.eligible(summary()))
        assertTrue(ReviewBundle.eligible(summary(reasons = listOf("not_trusted", "mode:shadow"))))
        assertTrue(ReviewBundle.eligible(summary(reasons = listOf("not_trusted", "low_confidence"), confidence = 0.85)))
    }

    @Test
    fun anythingAboutThePhotoOrTheNumbersKeepsItForAPerson() {
        listOf("no_capture", "not_from_camera", "check:energy_macros_mismatch", "not_grounded:fat_g", "headline_incomplete", "cloud_differs:fat_g", "cloud_unreadable")
            .forEach { reason -> assertFalse(ReviewBundle.eligible(summary(reasons = listOf("not_trusted", reason))), reason) }
        assertFalse(ReviewBundle.eligible(summary(reasons = listOf("low_confidence"), confidence = 0.6)))
        assertFalse(ReviewBundle.eligible(summary(reasons = listOf("low_confidence"), confidence = null)))
        assertFalse(ReviewBundle.eligible(summary(failedChecks = listOf("missing_required"))))
        assertFalse(ReviewBundle.eligible(summary(hasPhotos = false)), "nothing to check it against")
        assertFalse(ReviewBundle.eligible(summary(lane = null)), "never routed, so the phone reading was never compared")
        assertFalse(ReviewBundle.eligible(summary(status = "approved")))
    }

    @Test
    fun theBundleCarriesTheReadingAndPhotoUrlsAndRoundTrips() {
        val item = ReviewBundle.item("https://opson.example/", detail, "2026-11-02T09:00:00.000Z")
        assertEquals("https://opson.example/v1/admin/food-submissions/s1/photos/label", item.photos["label"])
        assertEquals(listOf("front", "label", "ingredients"), item.photos.keys.toList())
        assertEquals("Calories 190", item.ocrLines.single().text)
        assertEquals("5 of 5 headline numbers found in the photo's text", item.grounding)
        assertNull(item.decision.action)

        val text = ReviewBundle.encode(ReviewBundle.bundle("Dev", "2026-11-02T09:00:00.000Z", listOf(item)))
        assertTrue(ReviewBundle.FORMAT in text)
        val filled = text.replace("\"action\": null", "\"action\": \"approve\"")
        val parsed = ReviewBundle.parse(filled)
        assertNull(parsed.problem)
        val decision = parsed.decisions.single()
        assertEquals("s1", decision.id)
        assertEquals("approve", decision.action)
        assertNull(decision.problem)
    }

    @Test
    fun anUndecidedItemIsLeftOut() {
        val text = ReviewBundle.encode(ReviewBundle.bundle("Dev", "2026-11-02T09:00:00.000Z", listOf(ReviewBundle.item("https://x", detail, "t"))))
        assertTrue(ReviewBundle.parse(text).decisions.isEmpty())
    }

    @Test
    fun badDecisionsSayWhy() {
        val parsed = ReviewBundle.parse(
            """
            {"decisions": [
              {"id": "a", "action": "reject"},
              {"id": "b", "action": "maybe"},
              {"id": "c", "action": "reject", "reason": "Not a label", "code": "nonsense"},
              {"id": "d", "action": "approve", "code": "blurry"},
              {"action": "approve"},
              {"id": "e", "action": "Reject", "reason": "Blurry", "code": "blurry"},
              {"id": "e", "action": "skip"}
            ]}
            """.trimIndent(),
        )
        val problems = parsed.decisions.map { it.problem }
        assertEquals("A reject needs a reason.", problems[0])
        assertTrue(problems[1]!!.startsWith("Unknown action"))
        assertTrue(problems[2]!!.startsWith("Unknown reject code"))
        assertEquals("A code only goes with a reject.", problems[3])
        assertEquals("No id.", problems[4])
        assertNull(problems[5], "actions are read in any case")
        assertEquals("Listed twice.", problems[6])
    }

    @Test
    fun aFileThatIsNotABundleIsRefused() {
        assertNotNull(ReviewBundle.parse("not json").problem)
        assertNotNull(ReviewBundle.parse("""{"something": []}""").problem)
    }

    @Test
    fun thePlanChecksEachIdAgainstTheQueue() {
        val decisions = ReviewBundle.parse(
            """
            {"decisions": [
              {"id": "s1", "action": "approve", "food": {"perServing": {"energy_kcal": 200}}},
              {"id": "gone", "action": "approve"},
              {"id": "done", "action": "reject", "reason": "Blurry"},
              {"id": "s2", "action": "skip"}
            ]}
            """.trimIndent(),
        ).decisions
        val statuses = mapOf("s1" to "pending", "done" to "approved", "s2" to "pending")
        val rows = ReviewBundle.plan(decisions, statusOf = { statuses[it] }, nameOf = { "Granola bar" }, baseOf = { facts })

        assertNull(rows[0].problem)
        assertTrue(rows[0].applicable)
        assertEquals(listOf("Calories: 190 → 200"), rows[0].changes)
        val food = assertNotNull(rows[0].food)
        assertEquals("Granola bar", food.name, "fields the corrections leave out stay")
        assertNull(food.per100["energy_kcal"], "a corrected nutrient drops its per 100 figure")
        assertEquals("No submission with this id here.", rows[1].problem)
        assertEquals("Already approved.", rows[2].problem)
        assertFalse(rows[3].applicable, "a skip does nothing")
    }

    @Test
    fun theFileIsNamedForTheEnvAndDay() {
        assertEquals("opson-review-prod-2026-11-02.json", ReviewBundle.fileName("Prod", "2026-11-02T09:00:00.000Z"))
        assertEquals("1970-01-01T00:00:00.000Z", isoTime(0))
    }
}
