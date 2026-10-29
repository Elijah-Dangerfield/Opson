package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.FoodReport
import com.opson.libraries.foods.FoodReportResult
import com.opson.libraries.foods.FoodReports
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.prompts.PromptEvent
import com.opson.libraries.prompts.PromptEvents
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
 * "Report a problem" on a catalog food: what is wrong, the right numbers for the amount the detail
 * showed, a photo of the label and a note, all but the first optional. Online only, like adding a
 * pack: a report that cannot go keeps the sheet open with everything in it. The id is made once,
 * so trying again never files it twice. A report that went through is a moment a prompt may follow
 * ([PromptEvent.ReportSent]), which is where the Builder program asks.
 */
@OptIn(ExperimentalUuidApi::class)
@Inject
class FoodReportViewModel(
    @Assisted private val route: FoodReportRoute,
    private val reports: FoodReports,
    private val permissions: CameraPermissions,
    private val promptEvents: PromptEvents,
    picker: MealPhotoPicker,
    policySource: VisibilityPolicySource,
) : SEAViewModel<FoodReportState, FoodReportEvent, FoodReportAction>(
    initialStateArg = FoodReportState(name = route.name, canTakePhoto = picker.canTakePhoto),
) {
    private val reportId = Uuid.random().toString()
    private var policy: VisibilityPolicy = policySource.policy.value
    private var model = FoodReportModel(
        name = route.name,
        amountText = route.amountText,
        amountGrams = route.amountGrams.takeIf { it > 0 }?.toDouble(),
        canTakePhoto = picker.canTakePhoto,
    )

    init {
        viewModelScope.launch {
            policySource.policy.collect { takeAction(FoodReportAction.PolicyArrived(it)) }
        }
    }

    override suspend fun handleAction(action: FoodReportAction) {
        when (action) {
            is FoodReportAction.PolicyArrived -> {
                policy = action.policy
                action.change { this }
            }
            is FoodReportAction.ReasonPicked -> action.change { copy(reason = action.reason, problem = problem.keepGone()) }
            is FoodReportAction.FieldTyped -> if (action.key in FoodReportStateBuilder.FIELDS) {
                action.change { copy(fields = fields + (action.key to FoodReportStateBuilder.typed(action.key, action.text))) }
            }
            is FoodReportAction.NoteTyped -> action.change { copy(note = FoodReportStateBuilder.note(action.text)) }
            FoodReportAction.TakePhotoTapped -> when (val permission = permissions.current()) {
                CameraPermission.Granted -> sendEvent(FoodReportEvent.LaunchCamera)
                CameraPermission.NotAsked -> sendEvent(FoodReportEvent.RequestCameraPermission)
                is CameraPermission.Denied -> action.change { copy(cameraOff = true) }
            }
            FoodReportAction.ChooseFromLibraryTapped -> sendEvent(FoodReportEvent.LaunchLibrary)
            is FoodReportAction.CameraPermissionAnswered -> when (action.permission) {
                CameraPermission.Granted -> {
                    action.change { copy(cameraOff = false) }
                    sendEvent(FoodReportEvent.LaunchCamera)
                }
                else -> action.change { copy(cameraOff = true) }
            }
            FoodReportAction.OpenSettingsTapped -> permissions.openSettings()
            is FoodReportAction.PhotoAnswered -> when (val result = action.result) {
                is PhotoPickResult.Picked -> action.change { copy(photo = result.photo, photoFailed = false) }
                PhotoPickResult.Cancelled -> Unit
                PhotoPickResult.Failed -> action.change { copy(photoFailed = true) }
            }
            FoodReportAction.RemovePhotoTapped -> action.change { copy(photo = null, photoFailed = false) }
            FoodReportAction.SendTapped -> send(action)
        }
    }

    private suspend fun send(action: FoodReportAction) {
        val reason = model.reason ?: return
        if (model.sending) return
        action.change { copy(sending = true, problem = null) }
        val result = reports.send(
            FoodReport(
                id = reportId,
                foodId = route.foodId,
                reason = reason,
                suggested = FoodReportStateBuilder.suggested(model, policy),
                note = model.note.trim().ifEmpty { null },
                photo = model.photo?.jpeg,
            ),
        )
        if (result is FoodReportResult.Sent) {
            promptEvents.emit(PromptEvent.ReportSent)
            sendEvent(FoodReportEvent.Sent(route.name, result.applied))
        } else {
            action.change { copy(sending = false, problem = FoodReportStateBuilder.problemOf(result)) }
        }
    }

    /** A food that is gone stays gone; any other problem is for the next try to settle. */
    private fun ReportProblem?.keepGone(): ReportProblem? = takeIf { it == ReportProblem.Gone }

    private suspend fun FoodReportAction.change(transform: FoodReportModel.() -> FoodReportModel) {
        model = model.transform()
        updateState { FoodReportStateBuilder.build(model, policy) }
    }
}
