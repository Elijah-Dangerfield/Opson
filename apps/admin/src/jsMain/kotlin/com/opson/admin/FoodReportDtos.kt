package com.opson.admin

import kotlinx.serialization.Serializable

/*
 * The wire shapes of `/v1/admin/food-reports` and `/v1/admin/food-corrections`, mirrored from the
 * server's `FoodReportAdminRoutes.kt` and `FoodReportJson.kt`.
 */

@Serializable
data class SuggestedDto(
    val basisLabel: String? = null,
    val basisGrams: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val servingLabel: String? = null,
    val servingGrams: Double? = null,
)

/** What a correction sets, per 100 g (or ml). Null leaves the field as the source has it. */
@Serializable
data class CorrectionValuesDto(
    val name: String? = null,
    val brand: String? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val servingLabel: String? = null,
    val servingGrams: Double? = null,
)

@Serializable
data class Per100Dto(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
)

@Serializable
data class ServingDto(val label: String, val grams: Double? = null)

@Serializable
data class SnapshotDto(
    val name: String,
    val brand: String? = null,
    val source: String,
    val per100g: Per100Dto = Per100Dto(),
    val serving: ServingDto? = null,
)

@Serializable
data class FoodNowDto(
    val name: String,
    val brand: String? = null,
    val source: String,
    val barcode: String? = null,
    val per100g: Per100Dto = Per100Dto(),
    val serving: ServingDto? = null,
    val energyFlags: List<String> = emptyList(),
)

@Serializable
data class FoodReportDto(
    val id: String,
    val createdAtEpochMs: Long,
    val reason: String,
    val fromPerson: Boolean = true,
    val suggested: SuggestedDto? = null,
    val suggestedPer100: CorrectionValuesDto? = null,
    val note: String? = null,
    val snapshot: SnapshotDto,
    val status: String,
    val hasPhoto: Boolean = false,
    val resolvedAtEpochMs: Long? = null,
    val resolvedBy: String? = null,
    val resolution: String? = null,
    val correctionId: String? = null,
    val duplicateOf: String? = null,
    /** Every reason picked, [reason] first; empty from a server before V34. */
    val reasons: List<String> = emptyList(),
    val hasFrontPhoto: Boolean = false,
) {
    /** What the person picked, from any server. */
    val allReasons: List<String> get() = reasons.ifEmpty { listOf(reason) }
}

@Serializable
data class FoodCorrectionDto(
    val id: String,
    val createdAtEpochMs: Long,
    val createdBy: String,
    val values: CorrectionValuesDto,
    val base: SnapshotDto,
    val live: Boolean,
    val revertedAtEpochMs: Long? = null,
    val revertedBy: String? = null,
    val supersededBy: String? = null,
)

@Serializable
data class FoodReportGroupDto(
    val foodId: String,
    val name: String,
    val brand: String? = null,
    val source: String,
    val reportCount: Int,
    val oldestAtEpochMs: Long,
    val reasons: Map<String, Int> = emptyMap(),
    val hasPhoto: Boolean = false,
    val now: FoodNowDto? = null,
    val correction: FoodCorrectionDto? = null,
    val reports: List<FoodReportDto> = emptyList(),
)

@Serializable
data class FoodReportQueueDto(val openReports: Long, val foods: List<FoodReportGroupDto>)

@Serializable
data class FoodReportFoodDto(
    val foodId: String,
    val now: FoodNowDto? = null,
    val reports: List<FoodReportDto> = emptyList(),
    val corrections: List<FoodCorrectionDto> = emptyList(),
)

@Serializable
internal data class AcceptReportsDto(val reportIds: List<String>, val values: CorrectionValuesDto)

@Serializable
internal data class CloseReportsDto(val reportIds: List<String>, val reason: String? = null, val duplicateOf: String? = null)

/*
 * `/v1/admin/food-verifications` and `/v1/admin/food-review/reviewers`, mirrored from the server's
 * `FoodVerificationAdminRoutes.kt` and `FoodVerificationJson.kt`.
 */

/** What a Builder says is right, for its basis ([basisLabel], [basisGrams]). */
@Serializable
data class VerificationValuesDto(
    val name: String? = null,
    val brand: String? = null,
    val barcode: String? = null,
    val basisLabel: String? = null,
    val basisGrams: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val servingLabel: String? = null,
    val servingGrams: Double? = null,
)

@Serializable
data class FoodVerificationDto(
    val id: String,
    val foodId: String,
    val userId: String,
    val createdAtEpochMs: Long,
    val outcome: String,
    val grounded: Int = 0,
    val groundable: Int = 0,
    val status: String? = null,
    val wrong: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
    val values: VerificationValuesDto? = null,
    val valuesPer100: CorrectionValuesDto? = null,
    val note: String? = null,
    val snapshot: SnapshotDto? = null,
    val now: FoodNowDto? = null,
    val hasFrontPhoto: Boolean = false,
    val hasLabelPhoto: Boolean = false,
    val resolvedAtEpochMs: Long? = null,
    val resolvedBy: String? = null,
    val resolution: String? = null,
    val correctionId: String? = null,
)

@Serializable
data class FoodVerificationQueueDto(val open: Long, val verifications: List<FoodVerificationDto> = emptyList())

@Serializable
internal data class AcceptVerificationDto(val values: CorrectionValuesDto? = null, val note: String? = null)

@Serializable
internal data class RejectVerificationDto(val reason: String? = null)

@Serializable
data class ReviewerCountDto(
    val reviewer: String,
    val reportsAccepted: Int = 0,
    val reportsRejected: Int = 0,
    val reportsDuplicate: Int = 0,
    val verificationsAccepted: Int = 0,
    val verificationsRejected: Int = 0,
)

@Serializable
data class ReviewerCountsDto(val days: Int, val reviewers: List<ReviewerCountDto> = emptyList())
