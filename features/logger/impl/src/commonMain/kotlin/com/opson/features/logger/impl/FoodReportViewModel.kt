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
import com.opson.libraries.scanner.MealPhoto
import com.opson.libraries.scanner.MealPhotoPicker
import com.opson.libraries.scanner.PhotoPickResult
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * "Report a problem" on a catalog food: what is wrong (as many reasons as apply), the right numbers
 * for the amount the detail showed, photos of the front and the back of the pack and a note, all
 * but a reason optional. Online only, like adding a
 * pack: a report that cannot go keeps the sheet open with everything in it. The id is made once,
 * so trying again never files it twice. A report that went through is a moment a prompt may follow
 * ([PromptEvent.ReportSent], and [PromptEvent.FoodReportSent], which the Builder prompt follows).
 */
@OptIn(ExperimentalUuidApi::class)
@Inject
class FoodReportViewModel(
    @Assisted private val route: FoodReportRoute,
    private val reports: FoodReports,
    private val permissions: CameraPermissions,
    private val promptEvents: PromptEvents,
    private val phoneReader: PhoneLabelReader,
    picker: MealPhotoPicker,
    policySource: VisibilityPolicySource,
) : SEAViewModel<FoodReportState, FoodReportEvent, FoodReportAction>(
    initialStateArg = FoodReportState(name = route.name, canTakePhoto = picker.canTakePhoto),
) {
    private val reportId = Uuid.random().toString()

    /** Which photo the camera or the library was opened for. */
    private var side = PackSide.Label
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
            is FoodReportAction.ReasonToggled -> action.change {
                copy(picked = if (action.reason in picked) picked - action.reason else picked + action.reason, problem = problem.keepGone())
            }
            is FoodReportAction.FieldTyped -> if (action.key in FoodReportStateBuilder.FIELDS) {
                action.change {
                    copy(fields = fields + (action.key to FoodReportStateBuilder.typed(action.key, action.text)), fromPhoto = fromPhoto - action.key)
                }
            }
            is FoodReportAction.NoteTyped -> action.change { copy(note = FoodReportStateBuilder.note(action.text)) }
            is FoodReportAction.TakePhotoTapped -> {
                side = action.side
                when (permissions.current()) {
                    CameraPermission.Granted -> sendEvent(FoodReportEvent.LaunchCamera)
                    CameraPermission.NotAsked -> sendEvent(FoodReportEvent.RequestCameraPermission)
                    is CameraPermission.Denied -> action.change { copy(cameraOff = true) }
                }
            }
            is FoodReportAction.ChooseFromLibraryTapped -> {
                side = action.side
                sendEvent(FoodReportEvent.LaunchLibrary)
            }
            is FoodReportAction.CameraPermissionAnswered -> when (action.permission) {
                CameraPermission.Granted -> {
                    action.change { copy(cameraOff = false) }
                    sendEvent(FoodReportEvent.LaunchCamera)
                }
                else -> action.change { copy(cameraOff = true) }
            }
            FoodReportAction.OpenSettingsTapped -> permissions.openSettings()
            is FoodReportAction.PhotoAnswered -> when (val result = action.result) {
                is PhotoPickResult.Picked -> if (side == PackSide.Front) {
                    action.change { copy(front = result.photo, photoFailed = false) }
                } else {
                    action.change { copy(photo = result.photo, photoFailed = false) }
                    readLabel(result.photo)
                }
                PhotoPickResult.Cancelled -> Unit
                PhotoPickResult.Failed -> action.change { copy(photoFailed = true) }
            }
            is FoodReportAction.LabelReadArrived -> if (model.photo == action.photo && action.facts != null) {
                action.change { FoodReportStateBuilder.fromLabel(this, action.facts) }
            }
            is FoodReportAction.RemovePhotoTapped -> action.change {
                if (action.side == PackSide.Front) copy(front = null, photoFailed = false) else copy(photo = null, photoFailed = false, fromPhoto = emptySet())
            }
            FoodReportAction.SendTapped -> send(action)
        }
    }

    /**
     * Reads the label photo on the phone, as Add a packaged food does, to fill in the numbers the
     * person has not typed. Nothing is sent until they send the report, and the photo goes with it
     * for whoever reviews the report.
     */
    private fun readLabel(photo: MealPhoto) {
        viewModelScope.launch {
            val draft = (phoneReader.read(photo.jpeg) as? PhoneRead.Read)?.reading?.draft
            val facts = draft?.takeIf { SubmitFoodForm.partly(it) && it.layout.panelFound }?.facts
            takeAction(FoodReportAction.LabelReadArrived(photo, facts))
        }
    }

    private suspend fun send(action: FoodReportAction) {
        val reasons = FoodReportStateBuilder.reasons(model)
        if (reasons.isEmpty() || model.sending) return
        action.change { copy(sending = true, problem = null) }
        val result = reports.send(
            FoodReport(
                id = reportId,
                foodId = route.foodId,
                reasons = reasons,
                suggested = FoodReportStateBuilder.suggested(model, policy),
                note = model.note.trim().ifEmpty { null },
                photo = model.photo?.jpeg,
                frontPhoto = model.front?.jpeg,
            ),
        )
        if (result is FoodReportResult.Sent) {
            promptEvents.emit(PromptEvent.ReportSent)
            promptEvents.emit(PromptEvent.FoodReportSent)
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
