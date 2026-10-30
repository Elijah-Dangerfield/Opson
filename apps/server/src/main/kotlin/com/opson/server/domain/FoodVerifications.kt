package com.opson.server.domain

import java.util.UUID
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** A Builder's check of a catalog food against the pack in their hand. */
enum class VerificationOutcome(val wire: String) {
    /** Every headline number of the food was found in the label's text: it counts as accepted. */
    Confirmed("confirmed"),

    /** Some weren't. Nothing changes and nothing counts; a real difference goes through Report a problem. */
    Unconfirmed("unconfirmed"),
}

/** Where a verification that needs a person stands (V33). Null on one decided on arrival. */
enum class VerificationStatus(val wire: String) {
    Open("open"),

    /** A reviewer stood behind it, with a correction over the food when the Builder's values were right. */
    Accepted("accepted"),
    Rejected("rejected"),
    ;

    companion object {
        fun fromWire(value: String?): VerificationStatus? = entries.firstOrNull { it.wire == value }
    }
}

/** What a Builder can say was wrong or missing on a food. Wire values are the app's. */
enum class VerificationField(val wire: String) {
    Calories("calories"),
    Macros("macros"),
    Serving("serving"),
    NameBrand("name_brand"),
    Ingredients("ingredients"),
    Barcode("barcode"),
    ;

    companion object {
        fun fromWire(value: String?): VerificationField? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * The values a Builder says are right, read on the phone and edited: [suggested] for its basis
 * ("1 bar", 40 g) as a report's are, and the name, brand and barcode on the pack.
 */
data class VerificationValues(
    val name: String? = null,
    val brand: String? = null,
    val barcode: String? = null,
    val suggested: SuggestedValues = SuggestedValues(),
) {
    val isEmpty: Boolean get() = name == null && brand == null && barcode == null && suggested.isEmpty

    /** As a correction would store it: per 100, with the name and brand. A barcode is not a correction's. */
    fun toCorrection(): CorrectionValues = suggested.toCorrection().copy(name = name, brand = brand)
}

/** "Something was wrong": which fields, the right values, a note. */
data class VerificationProblem(
    val wrong: Set<VerificationField> = emptySet(),
    val missing: Set<VerificationField> = emptySet(),
    val values: VerificationValues? = null,
    val note: String? = null,
)

@OptIn(ExperimentalTime::class)
data class FoodVerification(
    val id: UUID,
    val foodId: UUID,
    val userId: UserId,
    val createdAt: Instant,
    val outcome: VerificationOutcome,
    val grounded: Int,
    val groundable: Int,
    val engine: String?,
    /** Null for a check decided on arrival; [VerificationStatus.Open] while it waits for a reviewer. */
    val status: VerificationStatus? = null,
    val problem: VerificationProblem? = null,
    /** The food as the Builder saw it, kept with one that waits for a reviewer. */
    val snapshot: FoodSnapshot? = null,
    val resolvedAt: Instant? = null,
    val resolvedBy: String? = null,
    val resolution: String? = null,
    val correctionId: UUID? = null,
    /** Which photos are kept: only with one that waits or waited for a reviewer. */
    val photos: Set<PackSide> = emptySet(),
)

sealed interface VerifyOutcome {
    data class Recorded(val verification: FoodVerification) : VerifyOutcome

    /** The same id again from the same person: the first answer. */
    data class Existing(val verification: FoodVerification) : VerifyOutcome
    data object Conflict : VerifyOutcome
}

sealed interface VerificationDecision {
    /** [correction] when the reviewer's values went over the food. */
    data class Accepted(val verification: FoodVerification, val correction: FoodCorrection?) : VerificationDecision
    data class Rejected(val verification: FoodVerification) : VerificationDecision
    data object NotFound : VerificationDecision

    /** Decided already, or one that never needed a person. */
    data object NotOpen : VerificationDecision
}

@OptIn(ExperimentalTime::class)
interface FoodVerificationRepository {
    /**
     * Records the check. A second confirmation of the same food by the same person is kept as
     * unconfirmed, so checking one pack twice never counts twice toward Pro. [photos] are kept
     * only with one that waits for a reviewer ([VerificationStatus.Open]).
     */
    suspend fun record(verification: FoodVerification, photos: Map<PackSide, ReportPhoto> = emptyMap()): VerifyOutcome

    suspend fun get(id: UUID): FoodVerification?

    /** Waiting for a reviewer, oldest first. */
    suspend fun open(limit: Int): List<FoodVerification>

    suspend fun countOpen(): Long

    /** Decided by a reviewer, newest first. */
    suspend fun decided(limit: Int): List<FoodVerification>

    suspend fun photo(id: UUID, side: PackSide): StoredPhoto?

    /**
     * A reviewer stands behind it. With [values], a correction over the existing food ([base] is
     * the food as served now) in the same transaction, through the corrections overlay. The
     * outcome becomes confirmed, unless this person already has a confirmed check of the food.
     */
    suspend fun accept(id: UUID, values: CorrectionValues?, base: FoodSnapshot?, by: String, note: String?): VerificationDecision

    suspend fun reject(id: UUID, reason: String?, by: String): VerificationDecision

    /** Deletes the photos of verifications decided before [before]. */
    suspend fun purgeDecidedPhotos(before: Instant): Int

    /** Every reviewer's decisions since [since], on reports and verifications alike, busiest first. */
    suspend fun reviewerCounts(since: Instant): List<ReviewerCount>
}

/** One reviewer's decisions over a window: the Database cleanup counts in the console. */
data class ReviewerCount(
    val reviewer: String,
    val reportsAccepted: Int = 0,
    val reportsRejected: Int = 0,
    val reportsDuplicate: Int = 0,
    val verificationsAccepted: Int = 0,
    val verificationsRejected: Int = 0,
) {
    val total: Int get() = reportsAccepted + reportsRejected + reportsDuplicate + verificationsAccepted + verificationsRejected
}

/** Limits on `POST /v1/foods/{id}/verifications` with a problem and photos (V33). */
object FoodVerificationLimits {
    const val MAX_NOTE = FoodReportLimits.MAX_NOTE
    const val MAX_NAME = FoodReportLimits.MAX_NAME
    const val MAX_BARCODE = 32
}

/**
 * Whether a label's text shows a catalog food's numbers. The catalog keeps per 100 g; a label
 * may print per 100, per serving, or both, so each headline value is looked for per 100 and,
 * when the food has a serving in grams, per serving too. Rounded as a label would print them
 * (whole kcal, grams to a tenth), which [LabelGrounding] already allows for.
 */
object VerificationCheck {
    /** Energy, protein, carbs and fat: a food missing one can't be confirmed. */
    const val HEADLINE = 4

    fun ground(per100: Nutrients, servingGrams: Double?, lines: List<CaptureLine>): Grounding {
        val values = listOf(
            NutrientKey.Energy.wire to per100.kcal,
            NutrientKey.Protein.wire to per100.protein,
            NutrientKey.Carbs.wire to per100.carbs,
            NutrientKey.Fat.wire to per100.fat,
        )
        val missing = mutableListOf<String>()
        var groundable = 0
        for ((key, value) in values) {
            value ?: continue
            groundable++
            val tokens = LabelGrounding.tokensFor(key, lines)
            val forms = listOfNotNull(value, servingGrams?.takeIf { it > 0 }?.let { printed(value * it / 100.0) })
            if (forms.none { LabelGrounding.grounded(it, tokens, isEnergy = key == NutrientKey.Energy.wire) }) missing += key
        }
        return Grounding(groundable - missing.size, groundable, missing)
    }

    private fun printed(value: Double): Double = if (value >= 10) value.roundToLong().toDouble() else (value * 10).roundToLong() / 10.0
}
