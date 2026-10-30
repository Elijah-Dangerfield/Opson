package com.opson.admin

import kotlin.math.round

/**
 * One field of a reported food: what is served now, what each report suggests (per 100 g, as
 * accepting stores it), and what the accept form starts at. [key] is the correction's field key.
 */
internal data class ReportDiffRow(
    val key: String,
    val label: String,
    val unit: String,
    val now: String?,
    val suggested: List<String>,
    val proposed: String,
) {
    /** A suggestion differs from what is served. */
    val differs: Boolean get() = suggested.any { it != now }
}

/** Pure helpers for the reports queue, so the diff and the accept values are testable without a browser. */
internal object ReportDiff {

    val FIELDS = listOf(
        Triple("name", "Name", ""),
        Triple("brand", "Brand", ""),
        Triple("kcal", "Calories", "kcal / 100"),
        Triple("protein", "Protein", "g / 100"),
        Triple("carbs", "Carbs", "g / 100"),
        Triple("fat", "Fat", "g / 100"),
        Triple("servingLabel", "Serving", ""),
        Triple("servingGrams", "Serving weight", "g"),
    )

    private val NUMBERS = setOf("kcal", "protein", "carbs", "fat", "servingGrams")

    /** Every field, now beside each suggestion. The form starts at the newest suggestion from a person, else the server's own. */
    fun rows(group: FoodReportGroupDto): List<ReportDiffRow> {
        val suggestions = group.reports.mapNotNull { it.suggestedPer100 }
        // Newest from a person first: they read the pack; the energy check only estimated.
        val ranked = group.reports.sortedWith(compareBy<FoodReportDto> { !it.fromPerson }.thenByDescending { it.createdAtEpochMs })
            .mapNotNull { it.suggestedPer100 }
        return rows(group.now, suggestions, ranked)
    }

    /**
     * [now] beside each of [suggestions]; the form starts at the first of [ranked] that sets a
     * field. A Builder's verification is the same table with one suggestion.
     */
    fun rows(now: FoodNowDto?, suggestions: List<CorrectionValuesDto>, ranked: List<CorrectionValuesDto> = suggestions): List<ReportDiffRow> =
        FIELDS.map { (key, label, unit) ->
            val served = nowText(now, key)
            ReportDiffRow(
                key = key,
                label = label,
                unit = unit,
                now = served,
                suggested = suggestions.mapNotNull { text(it, key) }.distinct(),
                proposed = ranked.firstNotNullOfOrNull { text(it, key) } ?: "",
            )
        }

    /**
     * The accept form's values, from what it starts at with the reviewer's [edits] over it. Blank
     * leaves a field as the source has it. Null when a number does not read, or nothing is set.
     */
    fun values(rows: List<ReportDiffRow>, edits: Map<String, String>): CorrectionValuesDto? {
        val typed = rows.associate { it.key to (edits[it.key] ?: it.proposed).trim() }
        fun number(key: String): Double? {
            val text = typed[key].orEmpty()
            if (text.isEmpty()) return null
            return text.replace(',', '.').toDoubleOrNull() ?: Double.NaN
        }
        val numbers = NUMBERS.associateWith(::number)
        if (numbers.values.any { it != null && (it.isNaN() || it < 0) }) return null
        val values = CorrectionValuesDto(
            name = typed["name"]?.ifEmpty { null },
            brand = typed["brand"]?.ifEmpty { null },
            kcal = numbers["kcal"],
            protein = numbers["protein"],
            carbs = numbers["carbs"],
            fat = numbers["fat"],
            servingLabel = typed["servingLabel"]?.ifEmpty { null },
            servingGrams = numbers["servingGrams"],
        )
        return values.takeUnless { it == CorrectionValuesDto() }
    }

    /** Only the fields that change something, for the confirm sheet: "Calories 0 → 2.1". */
    fun describe(rows: List<ReportDiffRow>, values: CorrectionValuesDto): String =
        rows.mapNotNull { row ->
            val to = text(values, row.key) ?: return@mapNotNull null
            if (to == row.now) null else "${row.label} ${row.now ?: "?"} → $to"
        }.joinToString(" · ").ifEmpty { "no change" }

    /** What a correction changed from the food as it was served before it: "Calories 0 → 2.1". */
    fun describeCorrection(correction: FoodCorrectionDto): String {
        val base = correction.base
        val before = FoodReportGroupDto(
            foodId = correction.id,
            name = base.name,
            source = base.source,
            reportCount = 0,
            oldestAtEpochMs = correction.createdAtEpochMs,
            now = FoodNowDto(base.name, base.brand, base.source, per100g = base.per100g, serving = base.serving),
        )
        return describe(rows(before), correction.values)
    }

    fun reasonLabel(reason: String): String = when (reason) {
        "wrong_calories" -> "Wrong calories"
        "wrong_nutrients" -> "Wrong nutrients"
        "wrong_numbers" -> "Wrong numbers"
        "missing_info" -> "Missing information"
        "wrong_serving" -> "Wrong serving size"
        "wrong_name" -> "Wrong name or brand"
        "duplicate" -> "Duplicate"
        "not_food" -> "Not a food"
        "energy_check" -> "Energy check (the server's own)"
        else -> "Other"
    }

    private fun nowText(now: FoodNowDto?, key: String): String? = now?.let {
        when (key) {
            "name" -> it.name
            "brand" -> it.brand
            "kcal" -> it.per100g.kcal?.let(::number)
            "protein" -> it.per100g.protein?.let(::number)
            "carbs" -> it.per100g.carbs?.let(::number)
            "fat" -> it.per100g.fat?.let(::number)
            "servingLabel" -> it.serving?.label
            "servingGrams" -> it.serving?.grams?.let(::number)
            else -> null
        }
    }

    private fun text(v: CorrectionValuesDto, key: String): String? = when (key) {
        "name" -> v.name
        "brand" -> v.brand
        "kcal" -> v.kcal?.let(::number)
        "protein" -> v.protein?.let(::number)
        "carbs" -> v.carbs?.let(::number)
        "fat" -> v.fat?.let(::number)
        "servingLabel" -> v.servingLabel
        "servingGrams" -> v.servingGrams?.let(::number)
        else -> null
    }

    /** Two decimals at most, no trailing zeros: 2.11, 0.8, 473. */
    fun number(value: Double): String {
        val rounded = round(value * HUNDRED) / HUNDRED
        return if (rounded == round(rounded)) rounded.toLong().toString() else rounded.toString()
    }

    private const val HUNDRED = 100.0
}
