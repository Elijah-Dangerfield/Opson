package com.opson.features.logger.impl

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.foods.CarbBasis
import com.opson.libraries.foods.LabelCheckCode
import com.opson.libraries.foods.LabelDraft
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.LabelParse
import com.opson.libraries.foods.ServingUnit
import com.opson.libraries.foods.SubmitResult
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.MealPhoto
import com.opson.libraries.scanner.PhotoPickResult
import kotlinx.datetime.LocalDate

/** Which photo: the front (name, brand), the nutrition label, or in Builder mode the ingredients list. */
enum class PackSide { Front, Label, Ingredients }

/**
 * What "Add a packaged food" draws, built by [SubmitFoodStateBuilder]. The route is
 * `SubmitFoodRoute` in the logger's api, so the Foods tab can open it too.
 */
data class SubmitFoodState(
    /** The meal it is logged to, or null when it is only saved to the person's foods (from the Foods tab). */
    val slot: MealSlot? = null,
    val stage: SubmitStage = SubmitStage.Photos(canRead = false),
    val front: MealPhoto? = null,
    val label: MealPhoto? = null,
    /** False where there is no camera (the iOS simulator): only the library is offered. */
    val canTakePhoto: Boolean = true,
    /** The photo picked for this side could not be read (an iCloud photo that would not download, say). */
    val photoFailed: PackSide? = null,
    /** A Builder adding a food: the ingredients slot, and the label read on the phone (`release.builders`). */
    val builderMode: Boolean = false,
    val ingredients: MealPhoto? = null,
    /** The label is being read on the phone rather than by the server. */
    val readingOnPhone: Boolean = false,
)

sealed interface SubmitStage {
    /** Two photos to take, retake or pick. [canRead] once both are there. */
    data class Photos(val canRead: Boolean) : SubmitStage

    data object Reading : SubmitStage

    data class Review(val form: SubmitForm) : SubmitStage

    /** Logging it and sending it. */
    data object Saving : SubmitStage

    /** The label photo shows no nutrition panel. */
    data object NoLabel : SubmitStage

    data object TooMany : SubmitStage

    data class ReadFailed(val offline: Boolean) : SubmitStage

    data class CameraOff(val canAskAgain: Boolean) : SubmitStage

    /** Logged and in the person's foods, but the copy for review did not go. */
    data class SendFailed(val offline: Boolean, val tooMany: Boolean) : SubmitStage
}

/** The editable form. Every value is the text the person sees and types, never reformatted under them. */
data class SubmitForm(
    val name: FormField,
    val brand: FormField,
    val barcode: FormField,
    /** A code the scanner read is not retyped. */
    val barcodeLocked: Boolean,
    val servingSize: FormField,
    val servingUnit: ServingUnit,
    val servingLabel: String?,
    val servingsPerContainer: FormField,
    /** The label's numbers, or null when the person's mode keeps calories out of sight. */
    val numbers: FormNumbers?,
    /** What stands in for [numbers] in a mode that hides them. */
    val hidden: HiddenNumbers?,
    val checks: List<CheckRow>,
    val notice: ReviewNotice?,
    val canSubmit: Boolean,
    /** The first field that keeps [canSubmit] false, for the line under the button. */
    val missing: List<String>,
)

/**
 * One field. [needsLook] when the model was unsure of it (confidence under
 * [SubmitFoodStateBuilder.LOW_CONFIDENCE]); [flagged] when a failing check names it;
 * [missing] when it is required and empty.
 */
data class FormField(
    val key: String,
    val text: String,
    val needsLook: Boolean = false,
    val flagged: Boolean = false,
    val missing: Boolean = false,
)

data class FormNumbers(val main: List<NutrientField>, val more: List<NutrientField>)

data class NutrientField(val nutrient: LabelNutrient, val field: FormField, val unit: String)

/** In a mode that hides calories: whether the numbers add up, said without them. */
data class HiddenNumbers(val addsUp: Boolean, val incomplete: Boolean)

/**
 * A failing check as a line with its fixes. [fields] are the form keys it
 * names; [difference] is "40 kcal" for the energy check; [fix] is the figure
 * "Use 234 kcal" writes and [keep] the one "Keep 280 kcal" leaves, both formatted
 * with their unit. An impossible amount has neither: it has to be changed.
 */
data class CheckRow(
    val code: LabelCheckCode,
    val fields: List<String>,
    val difference: String?,
    val fix: String?,
    val keep: String?,
    val kept: Boolean,
)

enum class ReviewNotice {
    /** The server cannot read labels right now: fill it in from the photo. */
    Unavailable,

    /** The person chose to type it in. */
    ByHand,

    /** Builder mode: the phone read the label, and every number wants a look against the pack. */
    ReadOnPhone,
}

sealed interface SubmitFoodAction {
    data class PolicyArrived(val policy: VisibilityPolicy) : SubmitFoodAction
    data class TakePhotoTapped(val side: PackSide) : SubmitFoodAction
    data class ChooseFromLibraryTapped(val side: PackSide) : SubmitFoodAction
    data class CameraPermissionAnswered(val permission: CameraPermission) : SubmitFoodAction
    data object AllowCameraTapped : SubmitFoodAction
    data object OpenSettingsTapped : SubmitFoodAction
    data class PhotoAnswered(val side: PackSide, val result: PhotoPickResult, val fromCamera: Boolean) : SubmitFoodAction
    data object ReadTapped : SubmitFoodAction
    data class ReadArrived(val parse: LabelParse) : SubmitFoodAction

    /** Builder mode: the phone read the panel well enough to fill the form without the server. */
    data class ReadOnPhoneArrived(val draft: LabelDraft) : SubmitFoodAction
    data object TypeItInTapped : SubmitFoodAction
    data object TryAgainTapped : SubmitFoodAction
    data object RetakeLabelTapped : SubmitFoodAction

    /** A field typed into. [key] is a [FormField.key]: `name`, `serving_size`, a [LabelNutrient.wire]. */
    data class FieldTyped(val key: String, val text: String) : SubmitFoodAction
    data class ServingUnitPicked(val unit: ServingUnit) : SubmitFoodAction

    /** "Use 234 kcal": the figure the numbers point to. */
    data class FixTapped(val code: LabelCheckCode, val field: String) : SubmitFoodAction

    /** "Keep 280 kcal": the label says so. */
    data class KeepTapped(val code: LabelCheckCode, val field: String) : SubmitFoodAction
    data object SubmitTapped : SubmitFoodAction
    data class SubmitArrived(val result: SubmitResult) : SubmitFoodAction
    data object BackTapped : SubmitFoodAction
    data object DoneTapped : SubmitFoodAction
}

sealed interface SubmitFoodEvent {
    data class LaunchCamera(val side: PackSide) : SubmitFoodEvent
    data class LaunchLibrary(val side: PackSide) : SubmitFoodEvent
    data object RequestCameraPermission : SubmitFoodEvent

    /**
     * [name] is saved to the person's foods and logged to [slot], or only saved when [slot] is null;
     * [sent] when the copy for review went too. The screen closes with a word about which.
     */
    data class Finished(val name: String, val slot: MealSlot?, val sent: Boolean, val published: Boolean = false) : SubmitFoodEvent
    data object Close : SubmitFoodEvent

    /**
     * The day was closed (Close the day, maybe on another phone), so the food is in the person's
     * foods but not logged. The review copy was sent ([sent]) all the same; the closed-day sheet
     * takes this screen's place.
     */
    data class DayClosed(val day: LocalDate, val sent: Boolean) : SubmitFoodEvent
}

/** Where the flow stands, as the view model keeps it. */
sealed interface SubmitStep {
    data object Photos : SubmitStep
    data object Reading : SubmitStep
    data object Review : SubmitStep
    data object Saving : SubmitStep
    data object NoLabel : SubmitStep
    data object TooMany : SubmitStep
    data class ReadFailed(val offline: Boolean) : SubmitStep
    data class CameraOff(val canAskAgain: Boolean) : SubmitStep
    data class SendFailed(val offline: Boolean, val tooMany: Boolean) : SubmitStep
}

/**
 * Everything the state is built from. [texts] are the fields as typed;
 * [per100] is what the label printed per 100 g or ml, kept to check the
 * serving against and dropped for a nutrient once the person types it.
 * [extras] are the nutrients the label had beyond the main list, in order.
 */
data class SubmitModel(
    /** Null: saved to the person's foods without logging it anywhere. */
    val slot: MealSlot?,
    val barcodeLocked: Boolean,
    val canTakePhoto: Boolean,
    val step: SubmitStep = SubmitStep.Photos,
    val front: MealPhoto? = null,
    val label: MealPhoto? = null,
    val parseId: String? = null,
    val confidence: Map<String, Double> = emptyMap(),
    val texts: Map<String, String> = emptyMap(),
    val servingUnit: ServingUnit = ServingUnit.Gram,
    /** The household measure the label printed ("1 bar"), kept as read. */
    val servingLabel: String? = null,
    val basis: CarbBasis = CarbBasis.Unknown,
    val per100: Map<LabelNutrient, Double> = emptyMap(),
    val extras: List<LabelNutrient> = emptyList(),
    /** Nutrients worked out from [per100] rather than printed per serving: they follow the serving size. */
    val derived: Set<LabelNutrient> = emptySet(),
    val kept: Set<Pair<LabelCheckCode, String>> = emptySet(),
    val notice: ReviewNotice? = null,
    /** The fields the model filled, as it filled them, to tell an edit from a read. */
    val read: Map<String, String> = emptyMap(),
    val photoFailed: PackSide? = null,
    val builderMode: Boolean = false,
    val ingredients: MealPhoto? = null,
    val readingOnPhone: Boolean = false,
)
