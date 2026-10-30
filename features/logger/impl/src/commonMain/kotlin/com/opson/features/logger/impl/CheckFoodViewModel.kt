package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.FoodCheckResult
import com.opson.libraries.foods.FoodChecks
import com.opson.libraries.foods.FoodReport
import com.opson.libraries.foods.FoodReportResult
import com.opson.libraries.foods.FoodReports
import com.opson.libraries.foods.FoodVerification
import com.opson.libraries.foods.VerifyProblem
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
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
 * Verify this food: a photo of the front of the pack (optional) and one of its nutrition label,
 * read on the phone (no server AI) and compared with what Opson has. "Something was wrong" says
 * which parts were wrong or missing, with the right values pre-filled from the label and a note.
 *
 * One request carries it all: the label's text, the answers and both photos. The server looks for
 * the food's numbers in the text: a match with nothing wrong shows as Verified once a second
 * Builder's check or a reviewer agrees (an older server marks it at once); anything else waits for a
 * reviewer, who corrects the food that is already there. A
 * server from before reviews ignores the answers, so they go as a Report a problem instead, as the
 * app did before. Both ids are made once for a set of photos, so a retry never files twice; Take
 * the photos again is a new check, with new ids, which the server can't mistake for the old one.
 */
@OptIn(ExperimentalUuidApi::class)
@Inject
@Suppress("LongParameterList", "TooManyFunctions")
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
    private var checkId = Uuid.random().toString()
    private var reportId = Uuid.random().toString()
    private var policy: VisibilityPolicy = policySource.policy.value
    private var model = CheckModel(name = route.name, canTakePhoto = picker.canTakePhoto)
    private var reading: PhoneReading? = null

    /** What the form opened with, so turning "Something was wrong" off and on again starts from the label. */
    private var prefilled: VerifyForm = VerifyForm()

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
            CheckFoodAction.SkipFrontTapped -> if (model.step == CheckStep.Photos(PackSide.Front)) {
                action.change { copy(step = CheckStep.Photos(PackSide.Label)) }
            }
            is CheckFoodAction.CameraPermissionAnswered -> if (action.permission == CameraPermission.Granted) {
                action.change { copy(cameraOff = false) }
                sendEvent(CheckFoodEvent.LaunchCamera)
            } else {
                action.change { copy(cameraOff = true) }
            }
            CheckFoodAction.OpenSettingsTapped -> permissions.openSettings()
            is CheckFoodAction.PhotoAnswered -> when (val result = action.result) {
                is PhotoPickResult.Picked -> photographed(action, result.photo)
                PhotoPickResult.Cancelled -> Unit
                PhotoPickResult.Failed -> if (model.step == CheckStep.Photos(PackSide.Label)) action.change { copy(step = CheckStep.Unreadable) }
            }
            is CheckFoodAction.SomethingWrongChanged -> if (model.step is CheckStep.Compared) {
                action.change { copy(somethingWrong = action.on, form = if (action.on && !somethingWrong) prefilled else form) }
            }
            is CheckFoodAction.IssuePicked -> action.form {
                copy(issues = issues + (action.field to if (issue(action.field) == action.issue) FieldIssue.None else action.issue))
            }
            is CheckFoodAction.ValueTyped -> if (action.key in VerifyForm.KEYS) {
                action.form { copy(values = values + (action.key to CheckFoodStateBuilder.typed(action.key, action.text))) }
            }
            is CheckFoodAction.NoteTyped -> action.form { copy(note = CheckFoodStateBuilder.note(action.text)) }
            CheckFoodAction.RetakeTapped -> {
                reading = null
                checkId = Uuid.random().toString()
                reportId = Uuid.random().toString()
                action.change { copy(step = CheckStep.Photos(PackSide.Front), front = null, label = null, somethingWrong = false, problem = problem.keepGone()) }
            }
            CheckFoodAction.SendTapped -> send(action)
            CheckFoodAction.DoneTapped -> sendEvent(CheckFoodEvent.Close)
        }
    }

    private suspend fun photographed(action: CheckFoodAction, photo: MealPhoto) {
        when (model.step) {
            CheckStep.Photos(PackSide.Front) -> action.change { copy(front = photo, step = CheckStep.Photos(PackSide.Label)) }
            CheckStep.Photos(PackSide.Label), CheckStep.Unreadable -> read(action, photo)
            else -> Unit
        }
    }

    private suspend fun read(action: CheckFoodAction, photo: MealPhoto) {
        action.change { copy(step = CheckStep.Reading, label = photo, problem = null) }
        val food = catalog.detail(route.foodId).getOrNull()
        if (food == null) {
            action.change { copy(step = CheckStep.Photos(PackSide.Label), problem = CheckProblem.Gone) }
            return
        }
        when (val read = phoneReader.read(photo.jpeg)) {
            is PhoneRead.Read -> compared(action, food, read.reading)
            PhoneRead.Unavailable -> action.change { copy(step = CheckStep.Photos(PackSide.Label), problem = CheckProblem.Unavailable) }
            PhoneRead.Failed -> action.change { copy(step = CheckStep.Unreadable) }
        }
    }

    /** A difference turns "Something was wrong" on, with the parts that differ already marked. */
    private suspend fun compared(action: CheckFoodAction, food: Food, read: PhoneReading) {
        val comparison = CheckFoodCompare.compare(food.per100g, read.draft)
        if (comparison == null) {
            action.change { copy(step = CheckStep.Unreadable) }
            return
        }
        reading = read
        val (form, basis) = CheckFoodStateBuilder.prefill(food, read.draft, comparison)
        prefilled = form
        action.change { copy(step = CheckStep.Compared(comparison), somethingWrong = !comparison.matches, form = form, basis = basis) }
    }

    private suspend fun send(action: CheckFoodAction) {
        val reading = reading ?: return
        if (model.step !is CheckStep.Compared || !CheckFoodStateBuilder.canSend(model)) return
        action.change { copy(busy = true, problem = null) }
        val problem = CheckFoodStateBuilder.problem(model)
        val result = checks.verify(
            FoodVerification(
                id = checkId,
                foodId = route.foodId,
                engine = reading.engine,
                lines = reading.lines,
                problem = problem,
                front = model.front?.jpeg,
                label = model.label?.jpeg,
            ),
        )
        val outcome = when (result) {
            FoodCheckResult.InReview -> CheckDone.InReview
            FoodCheckResult.AwaitingAgreement -> CheckDone.AwaitingAgreement
            // A server from before reviews took the check and dropped the answers: they go as a report.
            FoodCheckResult.Confirmed, is FoodCheckResult.Unconfirmed -> if (problem != null) {
                return report(action, problem)
            } else if (result == FoodCheckResult.Confirmed) {
                CheckDone.Confirmed
            } else {
                CheckDone.Unconfirmed
            }
            else -> null
        }
        action.change {
            if (outcome != null) copy(busy = false, step = CheckStep.Done(outcome)) else copy(busy = false, problem = CheckFoodStateBuilder.problemOf(result))
        }
    }

    private suspend fun report(action: CheckFoodAction, problem: VerifyProblem) {
        val result = reports.send(
            FoodReport(
                id = reportId,
                foodId = route.foodId,
                reasons = CheckFoodStateBuilder.reasonsOf(problem),
                suggested = CheckFoodStateBuilder.suggestedOf(problem),
                note = listOfNotNull(CORRECTION_NOTE, problem.note).joinToString("\n"),
                photo = model.label?.jpeg,
                frontPhoto = model.front?.jpeg,
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

    /** A food that is gone stays gone; any other problem is for the next try to settle. */
    private fun CheckProblem?.keepGone(): CheckProblem? = takeIf { it == CheckProblem.Gone }

    private suspend fun CheckFoodAction.form(transform: VerifyForm.() -> VerifyForm) {
        if (!model.somethingWrong || model.busy) return
        change { copy(form = form.transform()) }
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
