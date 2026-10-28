package com.opson.libraries.foods

/**
 * Adding a food from photos of its pack (0.2, behind `release.submitFood`):
 * the server reads the front and the nutrition label, the person corrects
 * what it read, and the food goes to the server for review. Online only; the
 * person's own copy of the food is a custom food, saved on the phone first.
 */
interface FoodLabels {
    /** [front] and [label] are JPEGs the device already shrank. Nothing is stored by this call. */
    suspend fun parse(front: ByteArray, label: ByteArray): LabelParse

    suspend fun submit(submission: FoodSubmission): SubmitResult
}

sealed interface LabelParse {
    /**
     * What the label says. [confidence] is the model's, 0 to 1, by field key
     * (`name`, `serving_size`, a [LabelNutrient.wire]); [readable] is false
     * when the label photo showed no nutrition panel. [parseId] goes back with
     * the submission.
     */
    data class Read(
        val parseId: String,
        val facts: LabelFacts,
        val confidence: Map<String, Double>,
        val checks: List<LabelCheck>,
        val readable: Boolean,
    ) : LabelParse

    /** The server has no way to read labels right now. The form can still be filled in by hand. */
    data object Unavailable : LabelParse

    data object TooMany : LabelParse

    data class Failed(val offline: Boolean) : LabelParse
}

/** Where the person started: a barcode nothing knew, or a search with no results. */
enum class SubmissionOrigin(val wire: String) {
    Barcode("barcode"),
    Search("search"),
}

/**
 * One food for review. [id] is made once, so a retry is the same submission.
 * [acknowledged] are the checks the person saw and kept their numbers through.
 */
class FoodSubmission(
    val id: String,
    val parseId: String?,
    val origin: SubmissionOrigin,
    val barcode: String?,
    val front: ByteArray,
    val label: ByteArray,
    val facts: LabelFacts,
    val acknowledged: List<LabelCheckCode>,
)

sealed interface SubmitResult {
    data class Submitted(val submissionId: String, val foodId: String) : SubmitResult

    data object TooMany : SubmitResult

    data class Failed(val offline: Boolean) : SubmitResult
}

/**
 * Tells the server a barcode missed and the person then found [foodId] by
 * searching, so a reviewer can give that food the barcode. Fire and forget,
 * like [BarcodeMissReporter]: a lost report only undercounts.
 */
interface BarcodeLinkReporter {
    suspend fun report(barcode: String, foodId: String)
}
