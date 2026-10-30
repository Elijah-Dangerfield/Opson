package com.opson.admin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * The weekly pass's two shortcuts, pure so they can be tested without a browser
 * (docs/practices/food-review-runbook.md):
 *
 *  - **Bulk approve** takes the pending foods whose phone reading passed every check.
 *  - **The bundle** is the pending queue as a JSON file the owner hands to an AI tool of their
 *    own (Claude Code on their subscription, say) to pre-check offline. Its filled-in decisions
 *    come back through Import, are shown for a look, and are applied only after a confirm. Nothing
 *    here calls a model; the server never does either.
 */
internal object ReviewBundle {

    const val FORMAT = "opson-review-bundle/1"

    /** The prompt the owner hands their AI tool with a bundle. */
    const val PROMPT_PATH = "scripts/prompts/weekly-food-review.md"

    const val INSTRUCTIONS =
        "Pending foods from Opson's review queue. For each item, compare the photos and the OCR text with " +
            "`submitted`, then fill in `decision`: approve, reject (with a reason) or skip. Follow $PROMPT_PATH. " +
            "Run `scripts/review_bundle.py photos` on this file first to download the photos next to it. " +
            "Import the filled file in the admin console's Review tab; nothing is applied until you confirm there."

    /** The server's limit on a reject reason. */
    const val MAX_REASON = 500

    /** A confidence at or above this can be bulk approved even with a `low_confidence` reason. */
    const val BULK_MIN_CONFIDENCE = 0.85

    /** Reasons that say who sent it or how the lane is set, not whether the numbers are right. */
    private val NEUTRAL_REASONS = setOf("not_trusted", "no_cloud_read")

    /** The photo kinds a submission can have. The ingredients photo is optional. */
    val PHOTO_KINDS = listOf("front", "label", "ingredients")

    val ACTIONS = setOf("approve", "reject", "skip")

    /**
     * Whether [summary] can go in "Approve N that passed every check": pending, its photos still
     * there, every check passing as it stands, routed (so the phone's reading was compared at all),
     * and every reason one that says nothing about the numbers. A `low_confidence` reason is
     * allowed only at [BULK_MIN_CONFIDENCE] or more. Anything about the photo or the reading
     * (`no_capture`, `not_from_camera`, `check:*`, `not_grounded:*`, `headline_incomplete`,
     * `cloud_*`) keeps it for a person.
     */
    fun eligible(summary: SubmissionSummaryDto): Boolean {
        if (summary.status != "pending" || !summary.hasPhotos || summary.failedChecks.isNotEmpty() || summary.lane == null) return false
        return summary.reasons.all { reason ->
            reason in NEUTRAL_REASONS ||
                reason.startsWith("mode:") ||
                (reason == "low_confidence" && (summary.confidence ?: 0.0) >= BULK_MIN_CONFIDENCE)
        }
    }

    /** The photo URLs on [baseUrl] for [id]. They need the admin token: `review_bundle.py photos` fetches them. */
    fun photoUrls(baseUrl: String, id: String): Map<String, String> =
        PHOTO_KINDS.associateWith { kind -> "${baseUrl.trimEnd('/')}/v1/admin/food-submissions/$id/photos/$kind" }

    /** One pending submission as the bundle carries it, with an empty decision to fill in. */
    fun item(baseUrl: String, detail: SubmissionDetailDto, createdAt: String): BundleItemDto = BundleItemDto(
        id = detail.summary.id,
        createdAt = createdAt,
        origin = detail.summary.origin,
        barcode = detail.summary.barcode,
        name = detail.summary.name,
        brand = detail.summary.brand,
        submitted = detail.reviewed ?: detail.submitted,
        phoneDraft = detail.capture?.draft,
        ocrEngine = detail.capture?.engine,
        photoFrom = detail.capture?.source,
        ocrLines = detail.capture?.lines.orEmpty().map { BundleLineDto(it.text, it.confidence) },
        checks = detail.checks.map(ReviewForm::describe),
        reasons = detail.summary.reasons.map(ReviewForm::reasonLabel),
        grounding = detail.routing?.grounding?.let(ReviewForm::groundingLine),
        confidence = detail.summary.confidence,
        photos = if (detail.summary.hasPhotos) photoUrls(baseUrl, detail.summary.id) else emptyMap(),
    )

    fun bundle(env: String, exportedAt: String, items: List<BundleItemDto>): ReviewBundleDto =
        ReviewBundleDto(env = env, exportedAt = exportedAt, items = items)

    fun encode(bundle: ReviewBundleDto): String = adminJson.encodeToString(ReviewBundleDto.serializer(), bundle)

    /** `opson-review-prod-2026-11-02.json`. */
    fun fileName(env: String, exportedAt: String): String = "opson-review-${env.lowercase()}-${exportedAt.take(DATE_LENGTH)}.json"

    private const val DATE_LENGTH = 10

    /**
     * The decisions in a filled file: a whole bundle (items with a `decision`) or a bare
     * `{ "decisions": [{ "id": ..., "action": ... }] }`. An item whose decision has no action yet
     * is left out: nobody decided it. Anything malformed comes back as a [ParsedImport.problem]
     * for the whole file, or a [ParsedDecision.problem] for one entry.
     */
    fun parse(text: String): ParsedImport {
        val root = parseJsonOrNull(text) as? JsonObject ?: return ParsedImport(emptyList(), "That file isn't a JSON object.")
        val entries: List<Pair<JsonObject, JsonObject>> = when {
            root["items"] is JsonArray -> (root["items"] as JsonArray).mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val decision = obj["decision"] as? JsonObject ?: return@mapNotNull null
                obj to decision
            }
            root["decisions"] is JsonArray -> (root["decisions"] as JsonArray).mapNotNull { (it as? JsonObject)?.let { obj -> obj to obj } }
            else -> return ParsedImport(emptyList(), "No `items` or `decisions` in that file.")
        }
        val seen = mutableSetOf<String>()
        val decisions = entries.mapNotNull { (holder, decision) ->
            val id = holder.string("id")
            val action = decision.string("action")?.trim()?.lowercase()
            if (action.isNullOrEmpty()) return@mapNotNull null
            val food = decision["food"]?.takeIf { it is JsonObject }?.let { element ->
                Catching { adminJson.decodeFromJsonElement<LabelFactsDto>(element) }.getOrNull()
                    ?: return@mapNotNull ParsedDecision(id.orEmpty(), action, problem = "The food corrections aren't readable.")
            }
            val parsed = ParsedDecision(
                id = id.orEmpty(),
                action = action,
                food = food,
                reason = decision.string("reason")?.trim()?.ifEmpty { null },
                code = decision.string("code")?.trim()?.ifEmpty { null },
                note = decision.string("note")?.trim()?.ifEmpty { null },
            )
            val problem = when {
                id.isNullOrBlank() -> "No id."
                !seen.add(id) -> "Listed twice."
                else -> problem(parsed)
            }
            parsed.copy(problem = problem)
        }
        return ParsedImport(decisions, null)
    }

    /** What is wrong with one decision on its own, or null. */
    fun problem(decision: ParsedDecision): String? = when {
        decision.action !in ACTIONS -> "Unknown action \"${decision.action}\". Use approve, reject or skip."
        decision.action == "reject" && decision.reason == null -> "A reject needs a reason."
        (decision.reason?.length ?: 0) > MAX_REASON -> "The reason is over $MAX_REASON characters."
        decision.code != null && ReviewForm.REJECT_CODES.none { it.first == decision.code } -> "Unknown reject code \"${decision.code}\"."
        decision.code != null && decision.action != "reject" -> "A code only goes with a reject."
        else -> null
    }

    /**
     * Each decision against the queue as it stands. [statusOf] gives an id's status (`pending`,
     * `approved`, ...) or null for an id this server doesn't have; [baseOf] the food as it stands,
     * for the before and after of any corrections.
     */
    fun plan(
        decisions: List<ParsedDecision>,
        statusOf: (String) -> String?,
        nameOf: (String) -> String?,
        baseOf: (String) -> LabelFactsDto?,
    ): List<ImportRow> = decisions.map { decision ->
        val status = if (decision.id.isBlank()) null else statusOf(decision.id)
        val problem = decision.problem ?: when {
            status == null -> "No submission with this id here."
            status != "pending" -> "Already $status."
            else -> null
        }
        val base = baseOf(decision.id)
        val food = if (decision.action == "approve" && base != null && decision.food != null) merged(base, decision.food) else null
        ImportRow(
            decision = decision,
            name = nameOf(decision.id) ?: decision.food?.name,
            food = food,
            changes = if (food != null && base != null) changes(base, food) else emptyList(),
            problem = problem,
        )
    }

    /**
     * [corrections] laid over [base]: a field it gives replaces the one there, one it leaves out
     * stays, so an AI tool only has to write what it changes. A nutrient given per serving drops
     * its per 100 figure, as a reviewer's edit does, so the checks judge the corrected number.
     */
    fun merged(base: LabelFactsDto, corrections: LabelFactsDto): LabelFactsDto = base.copy(
        name = corrections.name ?: base.name,
        brand = corrections.brand ?: base.brand,
        barcode = corrections.barcode?.filter(Char::isDigit)?.ifEmpty { null } ?: base.barcode,
        servingSize = corrections.servingSize ?: base.servingSize,
        servingUnit = corrections.servingUnit ?: base.servingUnit,
        servingLabel = corrections.servingLabel ?: base.servingLabel,
        servingsPerContainer = corrections.servingsPerContainer ?: base.servingsPerContainer,
        basis = corrections.basis.takeIf { it != "unknown" } ?: base.basis,
        perServing = base.perServing + corrections.perServing,
        per100 = (base.per100 - corrections.perServing.keys) + corrections.per100,
        acknowledged = emptyList(),
    )

    /** "Calories: 280 → 230" for every field [food] changes from [base]. */
    fun changes(base: LabelFactsDto, food: LabelFactsDto): List<String> {
        val keys = listOf("name", "brand", "barcode", "serving_size", "servings_per_container") +
            ReviewForm.NUTRIENTS.map { it.first }
        val fields = keys.mapNotNull { key ->
            val before = ReviewForm.text(base, key)
            val after = ReviewForm.text(food, key)
            if (before == after) null else "${ReviewForm.labelOf(key)}: ${before ?: "none"} → ${after ?: "none"}"
        }
        val unit = if ((base.servingUnit ?: "g") != (food.servingUnit ?: "g")) {
            listOf("Serving unit: ${base.servingUnit ?: "none"} → ${food.servingUnit ?: "none"}")
        } else {
            emptyList()
        }
        return fields + unit
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}

/** An epoch time as ISO 8601 in UTC, as the bundle writes times. */
internal fun isoTime(epochMs: Long): String {
    val ms = epochMs.toDouble()
    return js("new Date(ms).toISOString()") as String
}

/** A pending queue as a file for an offline pre-check. See [ReviewBundle]. */
@Serializable
internal data class ReviewBundleDto(
    val format: String = ReviewBundle.FORMAT,
    val env: String,
    val exportedAt: String,
    val instructions: String = ReviewBundle.INSTRUCTIONS,
    val decisionSchema: String = DECISION_SCHEMA,
    val items: List<BundleItemDto>,
) {
    companion object {
        const val DECISION_SCHEMA =
            "decision: { action: approve | reject | skip, food: optional corrections in the shape of `submitted`, " +
                "only the fields to change, " +
                "reason: required for reject, code: optional reject code (blurry, not_food, duplicate, wrong_column, " +
                "wrong_values, fake_data, copied_data, other), note: optional, why }"
    }
}

@Serializable
internal data class BundleItemDto(
    val id: String,
    val createdAt: String,
    val origin: String,
    val barcode: String? = null,
    val name: String? = null,
    val brand: String? = null,
    /** The food as it stands: what the person sent, or a reviewer's saved corrections. */
    val submitted: LabelFactsDto,
    /** What the phone's own reading drafted before the person changed anything. */
    val phoneDraft: LabelFactsDto? = null,
    val ocrEngine: String? = null,
    /** `camera` or `library`. */
    val photoFrom: String? = null,
    val ocrLines: List<BundleLineDto> = emptyList(),
    /** The checks that fail as it stands, in words. */
    val checks: List<String> = emptyList(),
    /** Why routing kept it for a person, in words. */
    val reasons: List<String> = emptyList(),
    val grounding: String? = null,
    val confidence: Double? = null,
    /** `front`, `label`, `ingredients` to URLs that need the admin token. The ingredients one may not exist. */
    val photos: Map<String, String> = emptyMap(),
    /** Filled in by `review_bundle.py photos`: the same kinds to files beside the bundle. */
    val localPhotos: Map<String, String> = emptyMap(),
    val decision: BundleDecisionDto = BundleDecisionDto(),
)

@Serializable
internal data class BundleLineDto(val text: String, val confidence: Double = 1.0)

@Serializable
internal data class BundleDecisionDto(
    val action: String? = null,
    val food: LabelFactsDto? = null,
    val reason: String? = null,
    val code: String? = null,
    val note: String? = null,
)

internal data class ParsedImport(val decisions: List<ParsedDecision>, val problem: String?)

internal data class ParsedDecision(
    val id: String,
    val action: String,
    val food: LabelFactsDto? = null,
    val reason: String? = null,
    val code: String? = null,
    val note: String? = null,
    val problem: String? = null,
)

/** One line of the import preview. Only rows without a [problem] can be applied. */
internal data class ImportRow(
    val decision: ParsedDecision,
    val name: String?,
    /** For an approve with corrections: the whole food as it will be approved. */
    val food: LabelFactsDto? = null,
    val changes: List<String>,
    val problem: String?,
) {
    val applicable: Boolean get() = problem == null && decision.action != "skip"
}
