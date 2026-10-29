package com.opson.admin

import kotlin.math.abs
import kotlin.math.round

/**
 * One field of a submission, side by side: what the model read (and how sure
 * it was), what the phone's own reading drafted, what the person sent, and
 * what stands now (the reviewer's edit, or what was sent). [key] is the
 * server's field key (`name`, `serving_size`, `energy_kcal`).
 */
internal data class ReviewRow(
    val key: String,
    val label: String,
    val unit: String,
    val read: String?,
    val confidence: Double?,
    val sent: String?,
    val current: String,
    val draft: String? = null,
) {
    /** The model was unsure, or the person changed what it or the phone read. */
    val needsLook: Boolean
        get() = (confidence ?: 1.0) < LOW_CONFIDENCE || (read != null && read != sent) || (draft != null && draft != sent)

    companion object {
        const val LOW_CONFIDENCE = 0.7
    }
}

/** Pure helpers for the review tab, so the side-by-side and the edits are testable without a browser. */
internal object ReviewForm {

    private val TEXT_FIELDS = listOf(
        Triple("name", "Name", ""),
        Triple("brand", "Brand", ""),
        Triple("barcode", "Barcode", ""),
        Triple("serving_size", "Serving size", ""),
        Triple("servings_per_container", "Servings per pack", ""),
    )

    /** Every nutrient the server knows, in label order, with the unit its key names. */
    val NUTRIENTS: List<Triple<String, String, String>> = listOf(
        Triple("energy_kcal", "Calories", "kcal"),
        Triple("protein_g", "Protein", "g"),
        Triple("carbs_g", "Carbs", "g"),
        Triple("sugars_g", "Sugars", "g"),
        Triple("added_sugars_g", "Added sugars", "g"),
        Triple("sugar_alcohols_g", "Sugar alcohols", "g"),
        Triple("fiber_g", "Fiber", "g"),
        Triple("fat_g", "Fat", "g"),
        Triple("saturated_fat_g", "Saturated fat", "g"),
        Triple("trans_fat_g", "Trans fat", "g"),
        Triple("cholesterol_mg", "Cholesterol", "mg"),
        Triple("sodium_mg", "Sodium", "mg"),
        Triple("potassium_mg", "Potassium", "mg"),
        Triple("calcium_mg", "Calcium", "mg"),
        Triple("iron_mg", "Iron", "mg"),
        Triple("magnesium_mg", "Magnesium", "mg"),
        Triple("phosphorus_mg", "Phosphorus", "mg"),
        Triple("zinc_mg", "Zinc", "mg"),
        Triple("copper_mg", "Copper", "mg"),
        Triple("manganese_mg", "Manganese", "mg"),
        Triple("selenium_ug", "Selenium", "mcg"),
        Triple("vitamin_a_rae_ug", "Vitamin A", "mcg"),
        Triple("vitamin_c_mg", "Vitamin C", "mg"),
        Triple("vitamin_d_ug", "Vitamin D", "mcg"),
        Triple("vitamin_e_mg", "Vitamin E", "mg"),
        Triple("vitamin_k_ug", "Vitamin K", "mcg"),
        Triple("thiamin_mg", "Thiamin", "mg"),
        Triple("riboflavin_mg", "Riboflavin", "mg"),
        Triple("niacin_mg", "Niacin", "mg"),
        Triple("vitamin_b6_mg", "Vitamin B6", "mg"),
        Triple("folate_dfe_ug", "Folate", "mcg"),
        Triple("vitamin_b12_ug", "Vitamin B12", "mcg"),
        Triple("choline_mg", "Choline", "mg"),
    )

    /** The four nutrients approval needs, shown even when nobody filled them in. */
    private val REQUIRED = setOf("energy_kcal", "protein_g", "carbs_g", "fat_g")

    /** The submission's fields, per serving, with every nutrient anyone gave and the required ones. */
    fun rows(detail: SubmissionDetailDto): List<ReviewRow> {
        val read = detail.parsed?.facts
        val draft = detail.capture?.draft
        val current = detail.reviewed ?: detail.submitted
        val present = listOfNotNull(read, draft, detail.submitted, current).flatMap { it.perServing.keys + it.per100.keys }.toSet()
        val fields = TEXT_FIELDS + NUTRIENTS.filter { it.first in present || it.first in REQUIRED }
        return fields.map { (key, label, unit) ->
            ReviewRow(
                key = key,
                label = label,
                unit = if (key == "serving_size") current.servingUnit.orEmpty() else unit,
                read = read?.let { text(it, key) },
                confidence = detail.parsed?.confidence?.get(key),
                sent = text(detail.submitted, key),
                current = text(current, key).orEmpty(),
                draft = draft?.let { text(it, key) },
            )
        }
    }

    /** [key]'s value in [facts] as text; a nutrient per serving, worked out from per 100 where needed. */
    fun text(facts: LabelFactsDto, key: String): String? = when (key) {
        "name" -> facts.name
        "brand" -> facts.brand
        "barcode" -> facts.barcode
        "serving_size" -> facts.servingSize?.let(::number)
        "servings_per_container" -> facts.servingsPerContainer?.let(::number)
        else -> (facts.perServing[key] ?: facts.per100[key]?.let { per100 -> facts.servingSize?.let { per100 * it / 100.0 } })?.let(::number)
    }

    /**
     * [base] with the reviewer's [edits] (field key to text). A blank field
     * clears the value. An edited nutrient drops its per 100 figure, which the
     * reviewer is now overriding, so the checks judge what they typed.
     */
    fun facts(base: LabelFactsDto, edits: Map<String, String>, unit: String? = base.servingUnit): LabelFactsDto {
        var facts = base.copy(servingUnit = unit, acknowledged = emptyList())
        edits.forEach { (key, raw) ->
            val text = raw.trim()
            val value = text.toDoubleOrNull()
            facts = when (key) {
                "name" -> facts.copy(name = text.ifEmpty { null })
                "brand" -> facts.copy(brand = text.ifEmpty { null })
                "barcode" -> facts.copy(barcode = text.filter(Char::isDigit).ifEmpty { null })
                "serving_size" -> facts.copy(servingSize = value)
                "servings_per_container" -> facts.copy(servingsPerContainer = value)
                else -> facts.copy(
                    perServing = if (value == null) facts.perServing - key else facts.perServing + (key to value),
                    per100 = facts.per100 - key,
                )
            }
        }
        return facts
    }

    /** A failed check as a reviewer reads it: what is off, and by how much. */
    fun describe(check: LabelCheckDto): String {
        val fields = check.fields.joinToString(", ") { key -> labelOf(key) }
        return when (check.code) {
            "missing_required" -> "Missing: $fields"
            "impossible_value" -> "Impossible: $fields"
            "energy_macros_mismatch" -> {
                val off = abs((check.actual ?: 0.0) - (check.expected ?: 0.0))
                "Calories ${number(check.actual)} vs ${number(check.expected)} from 4/4/9 (off by ${number(off)} kcal)"
            }
            "energy_looks_like_kj" -> "Calories ${number(check.actual)} look like kJ: ${number(check.expected)} kcal"
            "energy_units_disagree" -> "The label's kJ and kcal disagree: kJ says ${number(check.expected)} kcal, the kcal line ${number(check.actual)}"
            "serving_per_100_mismatch" -> "Per serving vs per 100 for $fields: ${number(check.actual)} printed, ${number(check.expected)} expected"
            "sodium_unit" -> "Sodium looks 1000x off: ${number(check.actual)} mg, maybe ${number(check.expected)} mg"
            else -> "${check.code}: $fields"
        }
    }

    /** The reject codes the server takes, with what the select shows. */
    val REJECT_CODES: List<Pair<String, String>> = listOf(
        "blurry" to "Blurry photo",
        "not_food" to "Not a food",
        "duplicate" to "Duplicate",
        "wrong_column" to "Wrong column",
        "wrong_values" to "Wrong values",
        "fake_data" to "Fake data",
        "copied_data" to "Copied data",
        "other" to "Other",
    )

    /** The codes that end the submitter's chance of Trusted. */
    val TRUST_ENDING_CODES: Set<String> = setOf("fake_data", "copied_data")

    fun rejectCodeLabel(code: String): String = REJECT_CODES.firstOrNull { it.first == code }?.second ?: code

    /**
     * A routing reason as a reviewer reads it. The server sends codes (`not_grounded:fat_g`,
     * `check:energy_macros_mismatch`); one it adds later is shown as sent.
     */
    fun reasonLabel(code: String): String {
        val field = code.substringAfter(':', "")
        return when {
            code == "not_trusted" -> "Not Trusted yet"
            code == "no_capture" -> "Typed or read by the server"
            code == "not_from_camera" -> "Photo from the library"
            code == "low_confidence" -> "Low confidence"
            code == "headline_incomplete" -> "Not every headline number was given"
            code == "no_cloud_read" -> "No second read"
            code == "cloud_unreadable" -> "Second read couldn't read the photo"
            code == "mode:shadow" -> "Would auto-approve (shadow)"
            code == "mode:off" -> "Would auto-approve (lane off)"
            code.startsWith("not_grounded:") -> "${labelOf(field)} not found in the photo's text"
            code.startsWith("cloud_differs:") -> "Second read differs on ${labelOf(field).lowercase()}"
            code.startsWith("check:") -> checkLabel(field)
            else -> code
        }
    }

    private fun checkLabel(code: String): String = when (code) {
        "missing_required" -> "Check: a required field is missing"
        "impossible_value" -> "Check: an impossible value"
        "energy_macros_mismatch" -> "Check: calories don't match the macros"
        "energy_looks_like_kj" -> "Check: calories look like kJ"
        "energy_units_disagree" -> "Check: kJ and kcal disagree"
        "serving_per_100_mismatch" -> "Check: per serving and per 100 disagree"
        "sodium_unit" -> "Check: sodium looks 1000x off"
        else -> "Check: $code"
    }

    /** "Review lane, 82%": where routing sent it and how sure it was. Null for a submission from before routing. */
    fun laneLine(summary: SubmissionSummaryDto): String? {
        val lane = summary.lane ?: return null
        val name = when (lane) {
            "review" -> "Review lane"
            "auto" -> "Auto-approved"
            "owner" -> "Owner, skipped the queue"
            else -> lane
        }
        val confidence = summary.confidence?.let { "${percent(it)}%" } ?: "no confidence"
        val audit = summary.audit?.let { ", audit $it" }.orEmpty()
        return "$name, $confidence$audit"
    }

    /**
     * The badge on a row the lane would have approved. In shadow or off the server adds a
     * `mode:` reason saying why it still came to a person; the badge carries it instead.
     */
    fun autoApproveBadge(reasons: List<String>): String =
        reasons.firstOrNull { it.startsWith("mode:") }?.let(::reasonLabel) ?: "Would auto-approve"

    /** A 0..1 share as a whole percent. */
    fun percent(share: Double): Int = round(share * 100).toInt()

    /** The photo's text against the headline fields: "4 of 5 headline numbers found ...; missing Fat". */
    fun groundingLine(grounding: GroundingDto): String {
        val missing = grounding.missing.joinToString(", ") { labelOf(it) }
        return "${grounding.grounded} of ${grounding.groundable} headline numbers found in the photo's text" +
            if (missing.isEmpty()) "" else "; missing $missing"
    }

    /** The second read: "gemini-x agrees, 1.2 s" or "gemini-x differs on calories, fat, 900 ms". */
    fun cloudLine(cloud: CloudCheckDto): String {
        val verdict = when {
            !cloud.readable -> "couldn't read the photo"
            cloud.agrees -> "agrees"
            cloud.differs.isEmpty() -> "disagrees"
            else -> "differs on " + cloud.differs.joinToString(", ") { labelOf(it).lowercase() }
        }
        val latency = if (cloud.latencyMs >= 1000) "${number(cloud.latencyMs / 1000.0)} s" else "${cloud.latencyMs} ms"
        return "${cloud.model} $verdict, $latency"
    }

    fun labelOf(key: String): String =
        (TEXT_FIELDS + NUTRIENTS).firstOrNull { it.first == key }?.second ?: key

    /** Up to two decimals, no trailing zeros. */
    fun number(value: Double?): String {
        if (value == null) return "?"
        val rounded = round(value * 100) / 100
        val whole = rounded.toLong()
        return if (rounded == whole.toDouble()) whole.toString() else rounded.toString()
    }
}
