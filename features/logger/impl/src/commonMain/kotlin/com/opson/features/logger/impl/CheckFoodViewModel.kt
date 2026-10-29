package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.FoodCheckResult
import com.opson.libraries.foods.FoodChecks
import com.opson.libraries.foods.FoodReport
import com.opson.libraries.foods.FoodReportReason
import com.opson.libraries.foods.FoodReportResult
import com.opson.libraries.foods.FoodReports
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.SuggestedFoodValues
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.CameraPermissions
import com.opson.libraries.scanner.MealPhotoPicker
import com.opson.libraries.scanner.PhotoPickResult
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Check against the pack: a photo of the label, read on the phone, compared with what Opson has.
 * A match sends the label's text (never the photo) to the server, which looks for the food's
 * numbers in it before the check counts. A difference becomes a correction through Report a
 * problem, with the photo and the numbers the label gave, for a reviewer to accept. Both ids are
 * made once, so a retry never files twice.
 */
@OptIn(ExperimentalUuidApi::class)
@Inject
@Suppress("LongParameterList")
class CheckFoodViewModel(
    @Assisted private val route: CheckFoodRoute,
    private val catalog: FoodCatalog,
    private val phoneReader: PhoneLabelReader,
    private val checks: FoodChecks,
    private val reports: FoodReports,
    private val permissions: CameraPermissions,
    picker: MealPhotoPicker,
    policySource: VisibilityPolicySource,
) : SEAViewModel<CheckFoodState, CheckFoodEvent, CheckFoodAction>(
    initialStateArg = CheckFoodState(name = route.name, canTakePhoto = picker.canTakePhoto),
) {
    private val checkId = Uuid.random().toString()
    private val reportId = Uuid.random().toString()
    private var policy: VisibilityPolicy = policySource.policy.value
    private var model = CheckModel(name = route.name, canTakePhoto = picker.canTakePhoto)
    private var reading: PhoneReading? = null

    init {
        viewModelScope.launch { policySource.policy.collect { takeAction(CheckFoodAction.PolicyArrived(it)) } }
    }

    override suspend fun handleAction(action: CheckFoodAction) {
        when (action) {
            is CheckFoodAction.PolicyArrived -> {
                policy = action.policy
                action.change { this }
            }
            CheckFoodAction.TakePhotoTapped -> when (permissions.current()) {
                CameraPermission.Granted -> sendEvent(CheckFoodEvent.LaunchCamera)
                CameraPermission.NotAsked -> sendEvent(CheckFoodEvent.RequestCameraPermission)
                is CameraPermission.Denied -> action.change { copy(cameraOff = true) }
            }
            CheckFoodAction.ChooseFromLibraryTapped -> sendEvent(CheckFoodEvent.LaunchLibrary)
            is CheckFoodAction.CameraPermissionAnswered -> if (action.permission == CameraPermission.Granted) {
                action.change { copy(cameraOff = false) }
                sendEvent(CheckFoodEvent.LaunchCamera)
            } else {
                action.change { copy(cameraOff = true) }
            }
            CheckFoodAction.OpenSettingsTapped -> permissions.openSettings()
            is CheckFoodAction.PhotoAnswered -> when (val result = action.result) {
                is PhotoPickResult.Picked -> read(action, result.photo)
                PhotoPickResult.Cancelled -> Unit
                PhotoPickResult.Failed -> action.change { copy(step = CheckStep.Unreadable) }
            }
            CheckFoodAction.RetakeTapped -> {
                reading = null
                action.change { copy(step = CheckStep.Intro, photo = null, problem = null) }
            }
            CheckFoodAction.SendCheckTapped -> sendCheck(action)
            CheckFoodAction.SendCorrectionTapped -> sendCorrection(action)
            CheckFoodAction.DoneTapped -> sendEvent(CheckFoodEvent.Close)
        }
    }

    private suspend fun read(action: CheckFoodAction, photo: com.opson.libraries.scanner.MealPhoto) {
        action.change { copy(step = CheckStep.Reading, photo = photo, problem = null) }
        val food = catalog.detail(route.foodId).getOrNull()
        if (food == null) {
            action.change { copy(step = CheckStep.Intro, problem = CheckProblem.Gone) }
            return
        }
        when (val read = phoneReader.read(photo.jpeg)) {
            is PhoneRead.Read -> {
                reading = read.reading
                val comparison = CheckFoodCompare.compare(food.per100g, read.reading.draft)
                action.change { copy(step = comparison?.let(CheckStep::Compared) ?: CheckStep.Unreadable) }
            }
            PhoneRead.Unavailable -> action.change { copy(step = CheckStep.Intro, problem = CheckProblem.Unavailable) }
            PhoneRead.Failed -> action.change { copy(step = CheckStep.Unreadable) }
        }
    }

    private suspend fun sendCheck(action: CheckFoodAction) {
        val reading = reading ?: return
        if (model.busy) return
        action.change { copy(busy = true, problem = null) }
        val result = checks.check(checkId, route.foodId, reading.engine, reading.lines)
        action.change {
            when (result) {
                FoodCheckResult.Confirmed -> copy(busy = false, step = CheckStep.Done(CheckDone.Confirmed))
                is FoodCheckResult.Unconfirmed -> copy(busy = false, step = CheckStep.Done(CheckDone.Unconfirmed))
                else -> copy(busy = false, problem = CheckFoodStateBuilder.problemOf(result))
            }
        }
    }

    private suspend fun sendCorrection(action: CheckFoodAction) {
        val comparison = (model.step as? CheckStep.Compared)?.comparison ?: return
        val reading = reading ?: return
        if (model.busy) return
        action.change { copy(busy = true, problem = null) }
        val facts = reading.draft.facts
        val result = reports.send(
            FoodReport(
                id = reportId,
                foodId = route.foodId,
                reason = if (LabelNutrient.Energy in comparison.differing) FoodReportReason.WrongCalories else FoodReportReason.WrongNutrients,
                // Per 100 g, as the label's own per 100 column or worked out from its serving.
                suggested = SuggestedFoodValues(
                    basisLabel = "100 g",
                    basisGrams = 100.0,
                    kcal = comparison.pack[LabelNutrient.Energy],
                    protein = comparison.pack[LabelNutrient.Protein],
                    carbs = comparison.pack[LabelNutrient.Carbs],
                    fat = comparison.pack[LabelNutrient.Fat],
                    servingLabel = facts.servingLabel,
                    servingGrams = comparison.servingGrams,
                ),
                note = CORRECTION_NOTE,
                photo = model.photo?.jpeg,
            ),
        )
        action.change {
            if (result is FoodReportResult.Sent) {
                copy(busy = false, step = CheckStep.Done(CheckDone.Corrected))
            } else {
                copy(busy = false, problem = CheckFoodStateBuilder.problemOf(result))
            }
        }
    }

    private suspend fun CheckFoodAction.change(transform: CheckModel.() -> CheckModel) {
        model = model.transform()
        val current = model
        val currentPolicy = policy
        updateState { CheckFoodStateBuilder.build(current, currentPolicy) }
    }

    internal companion object {
        /** For the reviewer, in the report's note: where these numbers came from. */
        const val CORRECTION_NOTE = "Checked against the pack by a Builder: these are the label's numbers, read on the phone."
    }
}
