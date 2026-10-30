package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.logger.SubmitEntry
import com.opson.features.logger.SubmitFoodRoute
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.features.builder.BuildersEnabled
import com.opson.libraries.foods.BuilderProgram
import com.opson.libraries.foods.FoodLabels
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.FoodSubmission
import com.opson.libraries.foods.LabelCheckCode
import com.opson.libraries.foods.LabelChecks
import com.opson.libraries.foods.LabelConfidence
import com.opson.libraries.foods.LabelFacts
import com.opson.libraries.foods.SubmissionCapture
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
 * "Add a packaged food": two photos, the label read on the phone (for everyone,
 * never by the server: v1 has no server AI), the person's corrections, then
 * the food saved as their own custom food at once (and
 * logged, when the route names a meal) and a copy sent to Opson's food
 * database for review. Saving comes first and never waits on the network: if
 * the review copy cannot go, the person still has their food, and can try
 * sending it again. Once the copy is in, [SubmittedFoods] marks their food
 * unverified until a reviewer approves it. The copy carries the photos and what
 * the phone read off the label (the OCR lines and its draft), so the person
 * who reviews it later can see where every number came from.
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
    builders: BuilderProgram,
    buildersEnabled: BuildersEnabled,
    private val phoneReader: PhoneLabelReader,
) : SEAViewModel<SubmitFoodState, SubmitFoodEvent, SubmitFoodAction>(
    initialStateArg = SubmitFoodState(slot = route.slot?.takeIf { route.logs }, canTakePhoto = picker.canTakePhoto),
) {
    /** Builder mode (`release.builders`, an active Builder): an ingredients photo too. */
    private val builderMode = buildersEnabled.value && builders.isBuilder

    /** What the phone read off the label, sent with the submission so review can ground the numbers. */
    private var phoneReading: PhoneReading? = null
    private var labelFromCamera = false
    /** Where it is logged; both null from the Foods tab, which only saves it. */
    private val day: LocalDate? = route.dayIso?.takeIf { route.logs }?.let(LocalDate::parse)
    private val slot: MealSlot? = route.slot?.takeIf { route.logs }
    private val barcode: String? = route.barcode
    private val entry: SubmitEntry = route.entry

    /** The server's origin says only whether a code was scanned; [entry] is the finer word, for telemetry. */
    private val origin = if (route.barcode != null) SubmissionOrigin.Barcode else SubmissionOrigin.Search
    private val submissionId = Uuid.random().toString()

    private var model = SubmitModel(slot = slot, barcodeLocked = barcode != null, canTakePhoto = picker.canTakePhoto, builderMode = builderMode)
    private var policy: VisibilityPolicy = policySource.policy.value

    /** The name guessed from the front, kept for "Type it in" after a label the phone could not place. */
    private var guessedName: String? = null
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
            is SubmitFoodAction.ReadOnPhoneArrived -> readOnPhoneArrived(action)
            SubmitFoodAction.TypeItInTapped -> if (model.step != SubmitStep.Review) {
                action.change {
                    SubmitFoodForm.fromDraft(this, null, barcode, guessedName).copy(step = SubmitStep.Review, notice = ReviewNotice.ByHand)
                }
            }
            SubmitFoodAction.TryAgainTapped -> if (model.step is SubmitStep.SendFailed) send(action)
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
                if (action.side == PackSide.Label) {
                    labelFromCamera = action.fromCamera
                    phoneReading = null
                }
                action.change {
                    when (action.side) {
                        PackSide.Front -> copy(front = result.photo, step = SubmitStep.Photos, photoFailed = null)
                        PackSide.Label -> copy(label = result.photo, step = SubmitStep.Photos, photoFailed = null)
                        PackSide.Ingredients -> copy(ingredients = result.photo, step = SubmitStep.Photos, photoFailed = null)
                    }
                }
            }
            // Said, not swallowed: a photo that would not load otherwise looks like a tap that did nothing.
            PhotoPickResult.Failed -> action.change { copy(step = SubmitStep.Photos, photoFailed = action.side) }
            PhotoPickResult.Cancelled -> Unit
        }
    }

    /**
     * Reads the label, then the front for a name, both on the phone. The server is never asked:
     * whatever the phone could not read, the person fills in from their photos.
     */
    private suspend fun read(action: SubmitFoodAction) {
        val front = model.front ?: return
        val label = model.label ?: return
        readJob?.cancel()
        phoneReading = null
        action.change { copy(step = SubmitStep.Reading) }
        readJob = viewModelScope.launch {
            val onLabel = phoneReader.read(label.jpeg)
            val name = phoneReader.guessName(front.jpeg)
            takeAction(SubmitFoodAction.ReadOnPhoneArrived(onLabel, name))
        }
    }

    private suspend fun readOnPhoneArrived(action: SubmitFoodAction.ReadOnPhoneArrived) {
        if (model.step != SubmitStep.Reading) return
        guessedName = action.name
        val reading = (action.read as? PhoneRead.Read)?.reading
        phoneReading = reading
        val draft = reading?.draft
        val outcome = when {
            draft == null -> ReadOutcome.Unavailable
            SubmitFoodForm.usable(draft) -> ReadOutcome.OnPhone
            SubmitFoodForm.partly(draft) -> ReadOutcome.Partly
            else -> ReadOutcome.NoLabel
        }
        val checks = draft?.let { LabelChecks.check(it.facts).size } ?: 0
        val unsure = draft?.fieldConfidence?.count { it.value < SubmitFoodStateBuilder.LOW_CONFIDENCE } ?: 0
        telemetry.read(outcome, checks, unsure)
        if (outcome == ReadOutcome.NoLabel) return action.change { copy(step = SubmitStep.NoLabel) }
        val notice = when (outcome) {
            ReadOutcome.OnPhone -> ReviewNotice.ReadOnPhone
            ReadOutcome.Partly -> ReviewNotice.ReadPartly
            else -> ReviewNotice.Unavailable
        }
        action.change { SubmitFoodForm.fromDraft(this, draft, barcode, action.name).copy(step = SubmitStep.Review, notice = notice) }
    }

    /**
     * The phone's reading as sent with the submission, with the signals measured against what the
     * person kept, from everyone: it is what lets a reviewer check each number against the photo's text.
     */
    private fun capture(facts: LabelFacts): SubmissionCapture? {
        val reading = phoneReading ?: return null
        val signals = LabelConfidence.signals(reading.draft, reading.lines, facts)
        return SubmissionCapture(
            engine = reading.engine,
            fromCamera = labelFromCamera,
            lines = reading.lines,
            draft = reading.draft.facts,
            signals = signals,
            score = LabelConfidence.score(signals),
        )
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
            parseId = null,
            origin = origin,
            barcode = barcode,
            front = front.jpeg,
            label = label.jpeg,
            facts = facts,
            acknowledged = acknowledged(),
            customFoodId = customFoodId,
            ingredients = model.ingredients?.jpeg?.takeIf { builderMode },
            capture = capture(facts),
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
            is SubmitResult.Submitted -> finish(sent = true, published = result.lane == LANE_OWNER || result.lane == LANE_AUTO)
            SubmitResult.TooMany -> action.change { copy(step = SubmitStep.SendFailed(offline = false, tooMany = true)) }
            SubmitResult.Restricted -> action.change { copy(step = SubmitStep.SendFailed(offline = false, tooMany = false, restricted = true)) }
            is SubmitResult.Failed -> action.change { copy(step = SubmitStep.SendFailed(offline = result.offline, tooMany = false)) }
        }
    }

    private suspend fun finish(sent: Boolean, published: Boolean = false) {
        if (finished) return
        finished = true
        reportSubmitted(sent)
        sendEvent(SubmitFoodEvent.Finished(SubmitFoodForm.facts(model).name.orEmpty(), committed?.slot ?: slot, sent, published))
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

    private companion object {
        /** The server's lanes for a food that went straight into the catalog (V30). */
        const val LANE_OWNER = "owner"
        const val LANE_AUTO = "auto"
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
