package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.logger.SubmitEntry
import com.opson.features.logger.SubmitFoodRoute
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.FoodLabels
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.FoodSubmission
import com.opson.libraries.foods.LabelCheckCode
import com.opson.libraries.foods.LabelParse
import com.opson.libraries.foods.SubmissionOrigin
import com.opson.libraries.foods.SubmitResult
import com.opson.libraries.foods.SubmittedFoods
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.CameraPermissions
import com.opson.libraries.scanner.MealPhotoPicker
import com.opson.libraries.scanner.PhotoPickResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import kotlin.uuid.Uuid

/**
 * "Add a packaged food": two photos, the server's reading, the person's
 * corrections, then the food saved as their own custom food at once (and
 * logged, when the route names a meal) and a copy sent to Opson's food
 * database for review. Saving comes first and never waits on the network: if
 * the review copy cannot go, the person still has their food, and can try
 * sending it again. Once the copy is in, [SubmittedFoods] marks their food
 * unverified until a reviewer approves it.
 *
 * The submission id is made once, so trying again is the same submission on
 * the server. Nothing here runs until the screen opens; every way in is
 * behind `release.submitFood`.
 */
@Inject
@Suppress("TooManyFunctions", "LongParameterList")
class SubmitFoodViewModel(
    @Assisted route: SubmitFoodRoute,
    private val labels: FoodLabels,
    private val customFoods: CustomFoodRepository,
    private val logger: FoodLogger,
    private val permissions: CameraPermissions,
    private val telemetry: SubmitFoodTelemetry,
    private val submittedFoods: SubmittedFoods,
    picker: MealPhotoPicker,
    policySource: VisibilityPolicySource,
) : SEAViewModel<SubmitFoodState, SubmitFoodEvent, SubmitFoodAction>(
    initialStateArg = SubmitFoodState(slot = route.slot?.takeIf { route.logs }, canTakePhoto = picker.canTakePhoto),
) {
    /** Where it is logged; both null from the Foods tab, which only saves it. */
    private val day: LocalDate? = route.dayIso?.takeIf { route.logs }?.let(LocalDate::parse)
    private val slot: MealSlot? = route.slot?.takeIf { route.logs }
    private val barcode: String? = route.barcode
    private val entry: SubmitEntry = route.entry

    /** The server's origin says only whether a code was scanned; [entry] is the finer word, for telemetry. */
    private val origin = if (route.barcode != null) SubmissionOrigin.Barcode else SubmissionOrigin.Search
    private val submissionId = Uuid.random().toString()

    private var model = SubmitModel(slot = slot, barcodeLocked = barcode != null, canTakePhoto = picker.canTakePhoto)
    private var policy: VisibilityPolicy = policySource.policy.value
    private var lastRead: LabelParse.Read? = null
    private var pendingSide: PackSide = PackSide.Front
    private var readJob: Job? = null

    /** Saved to their foods (and logged) once, whatever happens to the review copy after. */
    private var saved = false
    private var customFoodId: String? = null
    private var committed: Committed? = null

    /** The day refused the log: closed with Close the day. */
    private var closedDay: LocalDate? = null
    private var finished = false

    init {
        telemetry.started(origin, entry)
        viewModelScope.launch { policySource.policy.collect { takeAction(SubmitFoodAction.PolicyArrived(it)) } }
    }

    override suspend fun handleAction(action: SubmitFoodAction) {
        when (action) {
            is SubmitFoodAction.PolicyArrived -> {
                policy = action.policy
                action.rebuild()
            }
            is SubmitFoodAction.TakePhotoTapped -> {
                pendingSide = action.side
                when (val permission = permissions.current()) {
                    CameraPermission.Granted -> sendEvent(SubmitFoodEvent.LaunchCamera(action.side))
                    CameraPermission.NotAsked -> sendEvent(SubmitFoodEvent.RequestCameraPermission)
                    is CameraPermission.Denied -> action.change { copy(step = SubmitStep.CameraOff(permission.canAskAgain)) }
                }
            }
            is SubmitFoodAction.ChooseFromLibraryTapped -> {
                pendingSide = action.side
                sendEvent(SubmitFoodEvent.LaunchLibrary(action.side))
            }
            is SubmitFoodAction.CameraPermissionAnswered -> when (val permission = action.permission) {
                CameraPermission.Granted -> {
                    action.change { copy(step = SubmitStep.Photos) }
                    sendEvent(SubmitFoodEvent.LaunchCamera(pendingSide))
                }
                CameraPermission.NotAsked -> action.change { copy(step = SubmitStep.CameraOff(canAskAgain = true)) }
                is CameraPermission.Denied -> action.change { copy(step = SubmitStep.CameraOff(permission.canAskAgain)) }
            }
            SubmitFoodAction.AllowCameraTapped -> sendEvent(SubmitFoodEvent.RequestCameraPermission)
            SubmitFoodAction.OpenSettingsTapped -> permissions.openSettings()
            is SubmitFoodAction.PhotoAnswered -> photoAnswered(action)
            SubmitFoodAction.ReadTapped -> read(action)
            is SubmitFoodAction.ReadArrived -> readArrived(action)
            SubmitFoodAction.TypeItInTapped -> if (model.step != SubmitStep.Review) {
                action.change { SubmitFoodForm.fromReading(this, lastRead, barcode).copy(step = SubmitStep.Review, notice = ReviewNotice.ByHand) }
            }
            SubmitFoodAction.TryAgainTapped -> when (model.step) {
                is SubmitStep.ReadFailed, SubmitStep.TooMany -> read(action)
                is SubmitStep.SendFailed -> send(action)
                else -> Unit
            }
            SubmitFoodAction.RetakeLabelTapped -> action.change { copy(label = null, step = SubmitStep.Photos) }
            is SubmitFoodAction.FieldTyped -> if (model.step == SubmitStep.Review) {
                if (action.key == SubmitFoodForm.BARCODE && model.barcodeLocked) return
                action.change { SubmitFoodForm.typed(this, action.key, action.text) }
            }
            is SubmitFoodAction.ServingUnitPicked -> if (model.step == SubmitStep.Review) action.change { copy(servingUnit = action.unit) }
            is SubmitFoodAction.FixTapped -> {
                val check = SubmitFoodForm.checks(model).firstOrNull { it.code == action.code && it.fields.first() == action.field } ?: return
                action.change { SubmitFoodForm.fix(this, check) }
            }
            is SubmitFoodAction.KeepTapped -> action.change { copy(kept = kept + (action.code to action.field)) }
            SubmitFoodAction.SubmitTapped -> submit(action)
            is SubmitFoodAction.SubmitArrived -> submitArrived(action)
            SubmitFoodAction.BackTapped -> back(action)
            SubmitFoodAction.DoneTapped -> if (model.step is SubmitStep.SendFailed) finish(sent = false)
        }
    }

    private suspend fun photoAnswered(action: SubmitFoodAction.PhotoAnswered) {
        when (val result = action.result) {
            is PhotoPickResult.Picked -> {
                telemetry.photoTaken(action.side, action.fromCamera)
                action.change {
                    when (action.side) {
                        PackSide.Front -> copy(front = result.photo, step = SubmitStep.Photos, photoFailed = null)
                        PackSide.Label -> copy(label = result.photo, step = SubmitStep.Photos, photoFailed = null)
                    }
                }
            }
            // Said, not swallowed: a photo that would not load otherwise looks like a tap that did nothing.
            PhotoPickResult.Failed -> action.change { copy(step = SubmitStep.Photos, photoFailed = action.side) }
            PhotoPickResult.Cancelled -> Unit
        }
    }

    private suspend fun read(action: SubmitFoodAction) {
        val front = model.front ?: return
        val label = model.label ?: return
        readJob?.cancel()
        action.change { copy(step = SubmitStep.Reading) }
        readJob = viewModelScope.launch {
            takeAction(SubmitFoodAction.ReadArrived(labels.parse(front.jpeg, label.jpeg)))
        }
    }

    private suspend fun readArrived(action: SubmitFoodAction.ReadArrived) {
        if (model.step != SubmitStep.Reading) return
        when (val parse = action.parse) {
            is LabelParse.Read -> {
                lastRead = parse
                val unsure = parse.confidence.count { it.value < SubmitFoodStateBuilder.LOW_CONFIDENCE }
                if (!parse.readable) {
                    telemetry.read(ReadOutcome.NoLabel, parse.checks.size, unsure)
                    action.change { copy(step = SubmitStep.NoLabel) }
                } else {
                    telemetry.read(ReadOutcome.Read, parse.checks.size, unsure)
                    action.change { SubmitFoodForm.fromReading(this, parse, barcode).copy(step = SubmitStep.Review, notice = null) }
                }
            }
            LabelParse.Unavailable -> {
                telemetry.read(ReadOutcome.Unavailable, 0, 0)
                action.change { SubmitFoodForm.fromReading(this, null, barcode).copy(step = SubmitStep.Review, notice = ReviewNotice.Unavailable) }
            }
            LabelParse.TooMany -> {
                telemetry.read(ReadOutcome.TooMany, 0, 0)
                action.change { copy(step = SubmitStep.TooMany) }
            }
            is LabelParse.Failed -> {
                telemetry.read(if (parse.offline) ReadOutcome.Offline else ReadOutcome.Error, 0, 0)
                action.change { copy(step = SubmitStep.ReadFailed(parse.offline)) }
            }
        }
    }

    private suspend fun submit(action: SubmitFoodAction) {
        if (model.step != SubmitStep.Review || !SubmitFoodStateBuilder.canSubmit(model, policy)) return
        action.change { copy(step = SubmitStep.Saving) }
        if (!saved) {
            saved = true
            when (val logged = saveAsCustomFood()) {
                is DayWrite.Written -> committed = logged.value
                is DayWrite.DayClosed -> closedDay = logged.day
                null -> Unit
            }
        }
        send(action)
    }

    /**
     * The person's own food, saved now: the review is for everyone else. With a meal it is logged
     * too, and a closed day keeps the food and refuses the log. Without one there is nothing to log.
     */
    private suspend fun saveAsCustomFood(): DayWrite<Committed>? {
        val custom = customFoods.create(SubmitFoodForm.customFood(model))
        customFoodId = custom.id
        val day = day ?: return null
        val slot = slot ?: return null
        val ref = FoodRef.Custom(custom.id)
        return logger.log(day, slot, listOf(LogPick(key = ref.key, ref = ref, draft = custom.toDraft(), source = PickSource.Label)))
    }

    private suspend fun send(action: SubmitFoodAction) {
        val front = model.front ?: return
        val label = model.label ?: return
        action.change { copy(step = SubmitStep.Saving) }
        val facts = SubmitFoodForm.facts(model)
        val submission = FoodSubmission(
            id = submissionId,
            parseId = model.parseId,
            origin = origin,
            barcode = barcode,
            front = front.jpeg,
            label = label.jpeg,
            facts = facts,
            acknowledged = acknowledged(),
            customFoodId = customFoodId,
        )
        viewModelScope.launch {
            val result = labels.submit(submission)
            // Marked before the screen closes, so the food reads as unverified wherever it shows next.
            if (result is SubmitResult.Submitted) customFoodId?.let { submittedFoods.sent(it, result.submissionId, result.foodId) }
            takeAction(SubmitFoodAction.SubmitArrived(result))
        }
    }

    /** The checks the person kept their numbers through that still fail: what review should look at first. */
    private fun acknowledged(): List<LabelCheckCode> {
        val failing = SubmitFoodForm.checks(model).map { it.code to it.fields.first() }.toSet()
        return model.kept.filter { it in failing }.map { it.first }.distinct()
    }

    private suspend fun submitArrived(action: SubmitFoodAction.SubmitArrived) {
        if (model.step != SubmitStep.Saving) return
        closedDay?.let { day ->
            // Nothing was logged, so there is no "Added" to show and no send to retry from here.
            if (!finished) {
                finished = true
                reportSubmitted(sent = action.result is SubmitResult.Submitted)
                sendEvent(SubmitFoodEvent.DayClosed(day, sent = action.result is SubmitResult.Submitted))
            }
            return
        }
        when (val result = action.result) {
            is SubmitResult.Submitted -> finish(sent = true)
            SubmitResult.TooMany -> action.change { copy(step = SubmitStep.SendFailed(offline = false, tooMany = true)) }
            is SubmitResult.Failed -> action.change { copy(step = SubmitStep.SendFailed(offline = result.offline, tooMany = false)) }
        }
    }

    private suspend fun finish(sent: Boolean) {
        if (finished) return
        finished = true
        reportSubmitted(sent)
        sendEvent(SubmitFoodEvent.Finished(SubmitFoodForm.facts(model).name.orEmpty(), committed?.slot ?: slot, sent))
    }

    private fun reportSubmitted(sent: Boolean) = telemetry.submitted(
        origin = origin,
        entry = entry,
        edited = SubmitFoodForm.edited(model),
        kept = acknowledged().size,
        numbersShown = SubmitFoodStateBuilder.numbersShown(policy),
        sent = sent,
    )

    private suspend fun back(action: SubmitFoodAction) {
        when (model.step) {
            SubmitStep.Photos -> sendEvent(SubmitFoodEvent.Close)
            SubmitStep.Saving -> Unit
            is SubmitStep.SendFailed -> finish(sent = false)
            SubmitStep.Reading -> {
                readJob?.cancel()
                action.change { copy(step = SubmitStep.Photos) }
            }
            else -> action.change { copy(step = SubmitStep.Photos) }
        }
    }

    override fun onCleared() {
        // Swiped away after logging, with the review copy unsent, still counts as submitted.
        when {
            finished -> Unit
            saved -> reportSubmitted(sent = false)
            else -> telemetry.abandoned(stageName(model.step))
        }
        super.onCleared()
    }

    private fun stageName(step: SubmitStep): String = when (step) {
        SubmitStep.Photos -> if (model.front != null || model.label != null) "photos_some" else "photos_none"
        SubmitStep.Reading -> "reading"
        SubmitStep.Review -> "review"
        SubmitStep.Saving -> "saving"
        SubmitStep.NoLabel -> "no_label"
        SubmitStep.TooMany -> "too_many"
        is SubmitStep.ReadFailed -> "read_failed"
        is SubmitStep.CameraOff -> "camera_off"
        is SubmitStep.SendFailed -> "send_failed"
    }

    private suspend fun SubmitFoodAction.rebuild() {
        val current = model
        val currentPolicy = policy
        updateState { SubmitFoodStateBuilder.build(current, currentPolicy) }
    }

    private suspend fun SubmitFoodAction.change(change: SubmitModel.() -> SubmitModel) {
        model = model.change()
        rebuild()
    }
}
