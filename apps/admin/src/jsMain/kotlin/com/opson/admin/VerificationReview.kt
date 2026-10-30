package com.opson.admin

/** Pure helpers for the verifications queue, so the diff and the labels are testable without a browser. */
internal object VerificationReview {

    /**
     * Every field, the food as served now beside what the Builder read on the pack (per 100 g, as
     * accepting stores it). The form starts at the Builder's values.
     */
    fun rows(verification: FoodVerificationDto): List<ReportDiffRow> =
        ReportDiff.rows(verification.now ?: verification.snapshot?.asNow(), listOfNotNull(verification.valuesPer100))

    /** "Calories wrong", "Ingredients or allergens missing": what the Builder flagged, wrong first. */
    fun flags(verification: FoodVerificationDto): List<String> =
        verification.wrong.map { "${fieldLabel(it)} wrong" } + verification.missing.map { "${fieldLabel(it)} missing" }

    fun fieldLabel(code: String): String = when (code) {
        "calories" -> "Calories"
        "macros" -> "Protein, carbs or fat"
        "serving" -> "Serving size"
        "name_brand" -> "Name or brand"
        "ingredients" -> "Ingredients or allergens"
        "barcode" -> "Barcode"
        else -> code
    }

    /**
     * Why it is waiting: the Builder said something was wrong, the label's text did not show the
     * food's numbers (3 of 4 found), or it did and the match waits for a second Builder or a
     * reviewer to agree before the food shows as Verified.
     */
    fun why(verification: FoodVerificationDto): String = when {
        verification.wrong.isNotEmpty() || verification.missing.isNotEmpty() || verification.note != null || verification.values != null ->
            "The Builder said something was wrong"
        verification.outcome == "confirmed" ->
            "The label's text showed all ${verification.groundable} numbers. Accept it if the photos agree, or wait for a second Builder"
        else -> "The label's text showed ${verification.grounded} of ${verification.groundable} numbers"
    }

    /** The barcode the Builder read, when it differs from the food's: a reviewer attaches it by hand. */
    fun barcodeNote(verification: FoodVerificationDto): String? {
        val read = verification.values?.barcode ?: return null
        val now = verification.now?.barcode
        return if (now == null || now.trimStart('0') != read.trimStart('0')) "Barcode on the pack: $read (the food has ${now ?: "none"})" else null
    }

    /** Busiest first, with the totals for the last row: "reports 12 accepted, 3 rejected". */
    fun totals(counts: List<ReviewerCountDto>): ReviewerCountDto = ReviewerCountDto(
        reviewer = "Everyone",
        reportsAccepted = counts.sumOf { it.reportsAccepted },
        reportsRejected = counts.sumOf { it.reportsRejected },
        reportsDuplicate = counts.sumOf { it.reportsDuplicate },
        verificationsAccepted = counts.sumOf { it.verificationsAccepted },
        verificationsRejected = counts.sumOf { it.verificationsRejected },
    )

    private fun SnapshotDto.asNow() = FoodNowDto(name = name, brand = brand, source = source, per100g = per100g, serving = serving)
}
