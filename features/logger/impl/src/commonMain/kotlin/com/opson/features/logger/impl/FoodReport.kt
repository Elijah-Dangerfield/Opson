package com.opson.features.logger.impl

import com.opson.libraries.foods.FoodReportReason
import com.opson.libraries.foods.FoodReportResult
import com.opson.libraries.foods.SuggestedFoodValues
import com.opson.libraries.navigation.Route
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.MealPhoto
import com.opson.libraries.scanner.PhotoPickResult
import kotlinx.serialization.Serializable
import kotlin.math.round

/**
 * "Report a problem" on a catalog food, a sheet over its detail (the ⋮ menu, behind
 * `release.foodReports`). [amountText] and [amountGrams] are the amount the detail had on screen,
 * which suggested numbers are read for; 0 grams is unknown, and the numbers are then per 100 g.
 */
@Serializable
data class FoodReportRoute(
    val foodId: String,
    val name: String,
    val amountText: String? = null,
    val amountGrams: Int = 0,
) : Route()

/** Where the detail hands a catalog food to the report sheet. */
data class ReportTarget(val foodId: String, val name: String, val amountText: String?, val amountGrams: Double?) {
    fun route() = FoodReportRoute(foodId, name, amountText, amountGrams?.let { round(it).toInt() }?.takeIf { it > 0 } ?: 0)
}

/** What the report sheet draws, built by [FoodReportStateBuilder]. */
data class FoodReportState(
    val name: String,
    val reasons: List<FoodReportReason> = FoodReportReason.entries,
    val reason: FoodReportReason? = null,
    /** The right numbers, for a reason about them; null for any other reason. */
    val values: ReportValues? = null,
    val photo: MealPhoto? = null,
    /** False where there is no camera (the iOS simulator): only the library is offered. */
    val canTakePhoto: Boolean = true,
    val photoFailed: Boolean = false,
    /** The camera was refused; the library still works. */
    val cameraOff: Boolean = false,
    val note: String = "",
    val sending: Boolean = false,
    val problem: ReportProblem? = null,
    val canSend: Boolean = false,
)

/**
 * The optional "right numbers". [basis] is the amount they are for ("8 fl oz (240 ml)"), null
 * for 100 g. [numbers] is false in a mode that keeps calories and macros out of sight, where only
 * the serving is asked.
 */
data class ReportValues(
    val basis: ReportBasis?,
    val numbers: Boolean,
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val serving: String = "",
)

/**
 * The amount the numbers are for, as the detail had it ("1 cup"), and its weight in grams when the
 * amount does not say it already. The sheet words the two together, "1 cup (81 g)".
 */
data class ReportBasis(val amount: String, val grams: Long? = null)

/** Why the last send did not go. The sheet keeps everything typed, to try again. */
enum class ReportProblem { Offline, Failed, TooMany, Gone }

sealed interface FoodReportAction {
    data class PolicyArrived(val policy: VisibilityPolicy) : FoodReportAction
    data class ReasonPicked(val reason: FoodReportReason) : FoodReportAction

    /** [key] is one of [FoodReportStateBuilder.FIELDS]. */
    data class FieldTyped(val key: String, val text: String) : FoodReportAction
    data class NoteTyped(val text: String) : FoodReportAction
    data object TakePhotoTapped : FoodReportAction
    data object ChooseFromLibraryTapped : FoodReportAction
    data class CameraPermissionAnswered(val permission: CameraPermission) : FoodReportAction
    data object OpenSettingsTapped : FoodReportAction
    data class PhotoAnswered(val result: PhotoPickResult) : FoodReportAction
    data object RemovePhotoTapped : FoodReportAction
    data object SendTapped : FoodReportAction
}

sealed interface FoodReportEvent {
    data object LaunchCamera : FoodReportEvent
    data object LaunchLibrary : FoodReportEvent
    data object RequestCameraPermission : FoodReportEvent

    /** The server has it: the sheet closes with a thank-you. [applied] when it corrected the food at once. */
    data class Sent(val name: String, val applied: Boolean) : FoodReportEvent
}

/** What the sheet is built from, as the view model keeps it. */
data class FoodReportModel(
    val name: String,
    val amountText: String?,
    val amountGrams: Double?,
    val canTakePhoto: Boolean,
    val reason: FoodReportReason? = null,
    val fields: Map<String, String> = emptyMap(),
    val note: String = "",
    val photo: MealPhoto? = null,
    val photoFailed: Boolean = false,
    val cameraOff: Boolean = false,
    val sending: Boolean = false,
    val problem: ReportProblem? = null,
)

/** The report sheet's state and what it sends, as pure functions. */
object FoodReportStateBuilder {
    const val KCAL = "kcal"
    const val PROTEIN = "protein"
    const val CARBS = "carbs"
    const val FAT = "fat"
    const val SERVING = "serving"
    val FIELDS = listOf(KCAL, PROTEIN, CARBS, FAT, SERVING)
    private val NUMBERS = listOf(KCAL, PROTEIN, CARBS, FAT)

    /** The reasons that are about numbers, so the sheet asks for the right ones. */
    private val ABOUT_NUMBERS = setOf(FoodReportReason.WrongCalories, FoodReportReason.WrongNutrients, FoodReportReason.WrongServing)
    private const val MAX_NOTE = 1_000

    /** Calories and every macro are on show: a mode that hides them never asks for them either. */
    fun numbersShown(policy: VisibilityPolicy): Boolean =
        listOf(Metric.Calories, Metric.Protein, Metric.Carbs, Metric.Fat).all { policy.effectiveLevel(it, Surface.Logger) == Visibility.Show }

    fun build(model: FoodReportModel, policy: VisibilityPolicy): FoodReportState = FoodReportState(
        name = model.name,
        reason = model.reason,
        values = model.reason?.takeIf { it in ABOUT_NUMBERS }?.let {
            val shown = numbersShown(policy)
            ReportValues(
                basis = basis(model),
                numbers = shown,
                kcal = model.fields[KCAL].orEmpty().takeIf { shown }.orEmpty(),
                protein = model.fields[PROTEIN].orEmpty().takeIf { shown }.orEmpty(),
                carbs = model.fields[CARBS].orEmpty().takeIf { shown }.orEmpty(),
                fat = model.fields[FAT].orEmpty().takeIf { shown }.orEmpty(),
                serving = model.fields[SERVING].orEmpty(),
            )
        },
        photo = model.photo,
        canTakePhoto = model.canTakePhoto,
        photoFailed = model.photoFailed,
        cameraOff = model.cameraOff,
        note = model.note,
        sending = model.sending,
        problem = model.problem,
        canSend = model.reason != null && !model.sending && model.problem != ReportProblem.Gone,
    )

    /** "8 fl oz (240 ml)" as the detail had it, its weight added when the amount does not say it. */
    fun basis(model: FoodReportModel): ReportBasis? {
        val text = model.amountText?.trim()?.ifEmpty { null } ?: return null
        val grams = model.amountGrams ?: return null
        if (Regex("""\d\s*(g|ml)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) return ReportBasis(text)
        return ReportBasis(text, round(grams).toLong())
    }

    /**
     * What goes to the server: only the fields the person filled in, and only the numbers the
     * reason asks for. Numbers that do not read are left out rather than sent wrong.
     */
    fun suggested(model: FoodReportModel, policy: VisibilityPolicy): SuggestedFoodValues? {
        if (model.reason !in ABOUT_NUMBERS) return null
        val shown = numbersShown(policy)
        fun number(key: String) = model.fields[key]?.trim()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { shown && it >= 0 }
        val values = SuggestedFoodValues(
            basisLabel = model.amountText?.takeIf { model.amountGrams != null },
            basisGrams = model.amountGrams,
            kcal = number(KCAL),
            protein = number(PROTEIN),
            carbs = number(CARBS),
            fat = number(FAT),
            servingLabel = model.fields[SERVING]?.trim()?.ifEmpty { null },
        )
        return values.takeUnless { it.isEmpty }
    }

    fun note(text: String): String = text.take(MAX_NOTE)

    /** A field as it is typed: a number keeps digits and one decimal point, the serving is free text. */
    fun typed(key: String, text: String): String = if (key in NUMBERS) {
        val cleaned = text.filter { it.isDigit() || it == '.' || it == ',' }
        val point = cleaned.indexOfFirst { it == '.' || it == ',' }
        if (point < 0) cleaned.take(MAX_DIGITS) else (cleaned.substring(0, point + 1) + cleaned.substring(point + 1).filter(Char::isDigit)).take(MAX_DIGITS)
    } else {
        text.take(MAX_SERVING)
    }

    fun problemOf(result: FoodReportResult): ReportProblem? = when (result) {
        is FoodReportResult.Sent -> null
        FoodReportResult.TooMany -> ReportProblem.TooMany
        FoodReportResult.Gone -> ReportProblem.Gone
        is FoodReportResult.Failed -> if (result.offline) ReportProblem.Offline else ReportProblem.Failed
    }

    private const val MAX_DIGITS = 8
    private const val MAX_SERVING = 80
}
