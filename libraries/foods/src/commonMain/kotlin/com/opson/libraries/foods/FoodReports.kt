package com.opson.libraries.foods

/**
 * What someone says is wrong with a catalog food; they can pick several. Wire values match the
 * server's. [legacyWire] is the nearest code a server from before several reasons knows, sent as
 * the single `reason` beside them.
 */
enum class FoodReportReason(val wire: String, val legacyWire: String = wire) {
    WrongNumbers("wrong_numbers", "wrong_nutrients"),
    MissingInfo("missing_info", "other"),
    WrongServing("wrong_serving"),
    WrongName("wrong_name"),
    Duplicate("duplicate"),
    NotFood("not_food"),
    Other("other"),
}

/**
 * The numbers someone read on the pack, for the amount the detail showed: [basisLabel] ("1 can")
 * weighing [basisGrams]. The server turns them into per 100 g. Every field is optional; a serving
 * is [servingLabel] and, when known, [servingGrams].
 */
data class SuggestedFoodValues(
    val basisLabel: String? = null,
    val basisGrams: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val servingLabel: String? = null,
    val servingGrams: Double? = null,
) {
    val isEmpty: Boolean get() = listOf(kcal, protein, carbs, fat, servingGrams).all { it == null } && servingLabel.isNullOrBlank()
}

/**
 * One report. [id] is made once, so a retry is the same report. [reasons] are in the order the
 * sheet lists them, never empty. [photo] is the back of the pack (its label) and [frontPhoto] the
 * front, both JPEGs the device already shrank and both optional.
 */
class FoodReport(
    val id: String,
    val foodId: String,
    val reasons: List<FoodReportReason>,
    val suggested: SuggestedFoodValues?,
    val note: String?,
    val photo: ByteArray?,
    val frontPhoto: ByteArray? = null,
) {
    init {
        require(reasons.isNotEmpty()) { "A report says what is wrong" }
    }
}

sealed interface FoodReportResult {
    /** The server has it. [applied] when this person's values went straight onto the food (owners and admins, later). */
    data class Sent(val applied: Boolean) : FoodReportResult

    data object TooMany : FoodReportResult

    /** The food is not in the catalog any more, or this server has no reports yet. Nothing to retry. */
    data object Gone : FoodReportResult

    data class Failed(val offline: Boolean) : FoodReportResult
}

/**
 * "Report a problem" on a catalog food: it goes to Opson's reviewers and never changes the food by
 * itself. Online only; a report that could not go keeps its sheet open with what was typed, to try
 * again.
 */
interface FoodReports {
    suspend fun send(report: FoodReport): FoodReportResult
}
