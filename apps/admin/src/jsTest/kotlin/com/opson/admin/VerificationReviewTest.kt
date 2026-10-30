package com.opson.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerificationReviewTest {

    private val now = FoodNowDto(
        name = "Maple granola",
        brand = "Hillside",
        source = "usda_branded",
        barcode = "0012345678905",
        per100g = Per100Dto(kcal = 418.2, protein = 5.45, carbs = 67.3, fat = 14.5),
        serving = ServingDto("2/3 cup", 55.0),
    )

    private fun verification(
        wrong: List<String> = listOf("calories"),
        missing: List<String> = listOf("ingredients"),
        values: VerificationValuesDto? = VerificationValuesDto(basisLabel = "2/3 cup", basisGrams = 55.0, kcal = 250.0, barcode = "12345678905"),
        per100: CorrectionValuesDto? = CorrectionValuesDto(kcal = 454.55),
        note: String? = "New recipe",
        foodNow: FoodNowDto? = now,
    ) = FoodVerificationDto(
        id = "v1",
        foodId = "f1",
        userId = "u1",
        createdAtEpochMs = 1,
        outcome = "unconfirmed",
        grounded = 3,
        groundable = 4,
        status = "open",
        wrong = wrong,
        missing = missing,
        values = values,
        valuesPer100 = per100,
        note = note,
        snapshot = SnapshotDto(name = "Maple granola", brand = "Hillside", source = "usda_branded", per100g = now.per100g, serving = now.serving),
        now = foodNow,
    )

    @Test
    fun theTableIsTheFoodAsServedBesideWhatTheBuilderRead() {
        val rows = VerificationReview.rows(verification()).associateBy { it.key }
        assertEquals("418.2", rows.getValue("kcal").now)
        assertEquals(listOf("454.55"), rows.getValue("kcal").suggested)
        assertEquals("454.55", rows.getValue("kcal").proposed)
        assertTrue(rows.getValue("kcal").differs)
        assertEquals("", rows.getValue("protein").proposed, "nothing read, so the source keeps it")
        val values = ReportDiff.values(rows.values.toList(), emptyMap())
        assertEquals(CorrectionValuesDto(kcal = 454.55), values)
        assertEquals("Calories 418.2 → 454.55", ReportDiff.describe(rows.values.toList(), values!!))
    }

    @Test
    fun aFoodGoneFromTheCatalogStillShowsWhatTheBuilderSaw() {
        val rows = VerificationReview.rows(verification(foodNow = null)).associateBy { it.key }
        assertEquals("418.2", rows.getValue("kcal").now)
    }

    @Test
    fun flagsSayWhichFieldsWereWrongOrMissing() {
        assertEquals(listOf("Calories wrong", "Ingredients or allergens missing"), VerificationReview.flags(verification()))
        assertEquals("The Builder said something was wrong", VerificationReview.why(verification()))
        val unread = verification(wrong = emptyList(), missing = emptyList(), values = null, per100 = null, note = null)
        assertEquals("The label's text showed 3 of 4 numbers", VerificationReview.why(unread))
    }

    @Test
    fun aBarcodeOnThePackIsNotedOnlyWhenItDiffers() {
        assertNull(VerificationReview.barcodeNote(verification()), "the same code, leading zeros aside")
        val other = verification(values = VerificationValuesDto(barcode = "5000000000001"))
        assertEquals("Barcode on the pack: 5000000000001 (the food has 0012345678905)", VerificationReview.barcodeNote(other))
    }

    @Test
    fun reviewerTotalsAddUpEveryone() {
        val counts = listOf(
            ReviewerCountDto("elijah", reportsAccepted = 2, verificationsAccepted = 3),
            ReviewerCountDto("sam", reportsRejected = 1, verificationsRejected = 1),
        )
        val total = VerificationReview.totals(counts)
        assertEquals(ReviewerCountDto("Everyone", 2, 1, 0, 3, 1), total)
    }

    @Test
    fun reportsShowEveryReasonFromAnyServer() {
        val snapshot = SnapshotDto(name = "x", source = "off")
        val v33 = FoodReportDto("r", 1, "missing_info", snapshot = snapshot, status = "open", reasons = listOf("missing_info", "wrong_numbers"))
        val older = FoodReportDto("r", 1, "wrong_calories", snapshot = snapshot, status = "open")
        assertEquals(listOf("missing_info", "wrong_numbers"), v33.allReasons)
        assertEquals(listOf("wrong_calories"), older.allReasons)
        assertEquals("Missing information", ReportDiff.reasonLabel("missing_info"))
        assertEquals("Wrong numbers", ReportDiff.reasonLabel("wrong_numbers"))
    }
}
