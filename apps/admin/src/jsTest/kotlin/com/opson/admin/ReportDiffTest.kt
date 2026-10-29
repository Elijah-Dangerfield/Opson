package com.opson.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportDiffTest {

    private val now = FoodNowDto(
        name = "Energy Drink",
        brand = "Monster",
        source = "usda_branded",
        per100g = Per100Dto(kcal = 2.1, protein = 0.0, carbs = 0.83, fat = 0.0),
        serving = ServingDto("8.0 fl oz (240 ml)", 240.0),
        energyFlags = listOf("zero_with_macros"),
    )
    private val snapshot = SnapshotDto(name = "Energy Drink", source = "usda_branded")

    private fun report(id: String, at: Long, fromPerson: Boolean, values: CorrectionValuesDto) = FoodReportDto(
        id = id,
        createdAtEpochMs = at,
        reason = if (fromPerson) "wrong_calories" else "energy_check",
        fromPerson = fromPerson,
        suggestedPer100 = values,
        snapshot = snapshot,
        status = "open",
    )

    private val group = FoodReportGroupDto(
        foodId = "f1",
        name = "Energy Drink",
        source = "usda_branded",
        reportCount = 3,
        oldestAtEpochMs = 1,
        now = now,
        reports = listOf(
            report("check", at = 1, fromPerson = false, values = CorrectionValuesDto(kcal = 2.1)),
            report("older", at = 2, fromPerson = true, values = CorrectionValuesDto(kcal = 2.0)),
            report("newer", at = 3, fromPerson = true, values = CorrectionValuesDto(kcal = 2.11, servingLabel = "1 can (473 ml)", servingGrams = 473.0)),
        ),
    )

    @Test
    fun eachFieldShowsWhatIsServedBesideEverySuggestion() {
        val rows = ReportDiff.rows(group).associateBy { it.key }
        assertEquals("2.1", rows.getValue("kcal").now)
        assertEquals(listOf("2.1", "2", "2.11"), rows.getValue("kcal").suggested)
        assertTrue(rows.getValue("kcal").differs)
        assertFalse(rows.getValue("protein").differs)
        assertEquals("8.0 fl oz (240 ml)", rows.getValue("servingLabel").now)
    }

    @Test
    fun theFormStartsAtTheNewestSuggestionFromAPerson() {
        val rows = ReportDiff.rows(group).associateBy { it.key }
        assertEquals("2.11", rows.getValue("kcal").proposed)
        assertEquals("1 can (473 ml)", rows.getValue("servingLabel").proposed)
        assertEquals("", rows.getValue("protein").proposed, "nobody suggested it, so the source keeps it")
    }

    @Test
    fun valuesAreTheFormWithTheReviewersEditsOverIt() {
        val rows = ReportDiff.rows(group)
        assertEquals(
            CorrectionValuesDto(kcal = 2.11, servingLabel = "1 can (473 ml)", servingGrams = 473.0),
            ReportDiff.values(rows, emptyMap()),
        )
        assertEquals(
            CorrectionValuesDto(kcal = 2.0, protein = 0.5, servingLabel = "1 can (473 ml)", servingGrams = 473.0),
            ReportDiff.values(rows, mapOf("kcal" to "2,0", "protein" to "0.5")),
        )
        assertNull(ReportDiff.values(rows, mapOf("kcal" to "ten")), "a number that does not read")
        assertNull(ReportDiff.values(rows, mapOf("kcal" to "-1")))
        assertNull(ReportDiff.values(rows, mapOf("kcal" to "", "servingLabel" to "", "servingGrams" to "")), "nothing to set")
    }

    @Test
    fun theConfirmSheetNamesOnlyWhatChanges() {
        val rows = ReportDiff.rows(group)
        assertEquals(
            "Calories 2.1 → 2.11 · Serving 8.0 fl oz (240 ml) → 1 can (473 ml) · Serving weight 240 → 473",
            ReportDiff.describe(rows, CorrectionValuesDto(kcal = 2.11, protein = 0.0, servingLabel = "1 can (473 ml)", servingGrams = 473.0)),
        )
    }

    @Test
    fun aCorrectionIsDescribedAgainstWhatItReplaced() {
        val correction = FoodCorrectionDto(
            id = "c1",
            createdAtEpochMs = 5,
            createdBy = "Elijah",
            values = CorrectionValuesDto(kcal = 2.11),
            base = SnapshotDto(name = "Energy Drink", source = "usda_branded", per100g = Per100Dto(kcal = 0.0)),
            live = true,
        )
        assertEquals("Calories 0 → 2.11", ReportDiff.describeCorrection(correction))
    }
}
