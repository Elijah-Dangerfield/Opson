package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.core.AppState
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.MealPhotoEstimator
import com.opson.libraries.foods.PhotoEstimate
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.CameraPermissions
import com.opson.libraries.scanner.MealPhoto
import com.opson.libraries.scanner.MealPhotoPicker
import com.opson.libraries.scanner.PhotoPickResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Photo estimate: a note, a photo, the server's guess at what is on the
 * plate, and the person's corrections. The guess never gets the last word:
 * every line can be swapped, resized or removed, and all of it is logged as
 * an estimate. Logging is [FoodLogger]'s, the same as every other way in
 * the logger.
 *
 * Nothing here runs until the sheet opens, and only Photo on the + sheet and
 * search's "Photograph it" open it, both behind `release.photo`.
 */
@Inject
@Suppress("TooManyFunctions", "LongParameterList")
class PhotoEstimateViewModel(
    @Assisted route: PhotoEstimateRoute,
    private val estimator: MealPhotoEstimator,
    private val foodCatalog: FoodCatalog,
    private val permissions: CameraPermissions,
    private val logger: FoodLogger,
    private val settingsRepository: SettingsRepository,
    private val appState: AppState,
    private val telemetry: PhotoTelemetry,
    diary: DiaryRepository,
    picker: MealPhotoPicker,
    policySource: VisibilityPolicySource,
    calendar: LocalCalendar,
) : SEAViewModel<PhotoEstimateState, PhotoEstimateEvent, PhotoEstimateAction>(
    initialStateArg = PhotoEstimateState(slot = route.slot, canTakePhoto = picker.canTakePhoto),
) {
    private val day: LocalDate = LocalDate.parse(route.dayIso)
    private val slot: MealSlot = route.slot

    private var model = PhotoModel(slot = slot, canTakePhoto = picker.canTakePhoto)
    private var policy: VisibilityPolicy = policySource.policy.value
    private var inputs: PhotoInputs? = null

    private var estimateJob: Job? = null
    private var searchJob: Job? = null
    private var nextKey = 0

    /** Logged, and the sheets are closing: a second Add queued behind the first would log the lines again. */
    private var logged = false

    init {
        viewModelScope.launch {
            diary.observeDay(day).collect { takeAction(PhotoEstimateAction.InputsArrived(PhotoInputs(calendar.today(), it))) }
        }
        viewModelScope.launch {
            policySource.policy.collect { takeAction(PhotoEstimateAction.PolicyArrived(it)) }
        }
    }

    override suspend fun handleAction(action: PhotoEstimateAction) {
        when (action) {
            is PhotoEstimateAction.InputsArrived -> {
                inputs = action.inputs
                action.rebuild()
            }
            is PhotoEstimateAction.PolicyArrived -> {
                policy = action.policy
                val householdOnly = PhotoEstimateStateBuilder.householdOnly(policy)
                action.change { copy(rows = rows.map { PhotoRows.forPolicy(it, householdOnly) }) }
            }
            is PhotoEstimateAction.NoteTyped -> action.change { copy(note = action.text.take(NOTE_LIMIT)) }
            PhotoEstimateAction.TakePhotoTapped -> when (val permission = permissions.current()) {
                CameraPermission.Granted -> sendEvent(PhotoEstimateEvent.LaunchCamera)
                CameraPermission.NotAsked -> sendEvent(PhotoEstimateEvent.RequestCameraPermission)
                is CameraPermission.Denied -> action.change { copy(step = PhotoStep.CameraOff(permission.canAskAgain)) }
            }
            PhotoEstimateAction.ChooseFromLibraryTapped -> sendEvent(PhotoEstimateEvent.LaunchLibrary)
            is PhotoEstimateAction.CameraPermissionAnswered -> when (val permission = action.permission) {
                CameraPermission.Granted -> {
                    action.change { copy(step = PhotoStep.Start) }
                    sendEvent(PhotoEstimateEvent.LaunchCamera)
                }
                CameraPermission.NotAsked -> action.change { copy(step = PhotoStep.CameraOff(canAskAgain = true)) }
                is CameraPermission.Denied -> action.change { copy(step = PhotoStep.CameraOff(permission.canAskAgain)) }
            }
            PhotoEstimateAction.AllowCameraTapped -> sendEvent(PhotoEstimateEvent.RequestCameraPermission)
            PhotoEstimateAction.OpenSettingsTapped -> permissions.openSettings()
            is PhotoEstimateAction.PhotoAnswered -> photoAnswered(action)
            is PhotoEstimateAction.EstimateArrived -> estimateArrived(action)
            is PhotoEstimateAction.RowAmountTapped -> action.change {
                val editable = rows.any { it.key == action.key && it.editable }
                copy(editing = if (editing == action.key || !editable) null else action.key)
            }
            is PhotoEstimateAction.LessTapped -> action.updateRow(action.key) { PhotoRows.less(it) }
            is PhotoEstimateAction.MoreTapped -> action.updateRow(action.key) { PhotoRows.more(it) }
            is PhotoEstimateAction.UnitPicked -> action.updateRow(action.key) {
                PhotoRows.unitPicked(it, action.index, PhotoEstimateStateBuilder.householdOnly(policy))
            }
            is PhotoEstimateAction.RowRemoveTapped -> action.change {
                copy(rows = rows.filterNot { it.key == action.key }, editing = editing.takeUnless { it == action.key })
            }
            is PhotoEstimateAction.RowNameTapped -> model.rows.firstOrNull { it.key == action.key }?.let { row ->
                // Starts from the line's own name, so the nearest alternatives are already there.
                val query = shortName(row.name)
                action.change { copy(swap = PhotoSwap(replacing = row.key, query = query), editing = null) }
                search(query)
            }
            PhotoEstimateAction.AddMissedTapped -> action.change { copy(swap = PhotoSwap(replacing = null), editing = null) }
            is PhotoEstimateAction.SwapTyped -> if (model.swap != null) {
                action.change { copy(swap = swap?.copy(query = action.text)) }
                search(action.text)
            }
            is PhotoEstimateAction.SwapResultsArrived -> {
                val swap = model.swap ?: return
                if (action.query == swap.query.trim()) action.change { copy(swap = swap.copy(results = SwapAnswer(action.query, action.foods, action.offline))) }
            }
            is PhotoEstimateAction.SwapPicked -> action.swapPicked(action.id)
            PhotoEstimateAction.TryAgainTapped -> model.photo?.let { action.estimate(it) } ?: action.change { copy(step = PhotoStep.Start) }
            PhotoEstimateAction.AnotherPhotoTapped -> {
                estimateJob?.cancel()
                action.change { copy(step = PhotoStep.Start, photo = null, rows = emptyList(), editing = null, swap = null) }
            }
            PhotoEstimateAction.DescribeInsteadTapped -> sendEvent(PhotoEstimateEvent.OpenDescribe(day, slot))
            PhotoEstimateAction.BackTapped -> if (model.swap != null) {
                searchJob?.cancel()
                action.change { copy(swap = null) }
            } else {
                sendEvent(PhotoEstimateEvent.Dismiss)
            }
            PhotoEstimateAction.AddToMealTapped -> addToMeal()
        }
    }

    private suspend fun PhotoEstimateAction.rebuild() {
        val current = model
        val currentInputs = inputs
        val currentPolicy = policy
        updateState { PhotoEstimateStateBuilder.build(current, currentInputs, currentPolicy) }
    }

    private suspend fun PhotoEstimateAction.change(change: PhotoModel.() -> PhotoModel) {
        model = model.change()
        rebuild()
    }

    private suspend fun PhotoEstimateAction.updateRow(key: String, update: (PhotoRow) -> PhotoRow) =
        change { copy(rows = rows.map { if (it.key == key) update(it) else it }) }

    private suspend fun photoAnswered(action: PhotoEstimateAction.PhotoAnswered) {
        when (val result = action.result) {
            is PhotoPickResult.Picked -> {
                telemetry.started(fromCamera = action.fromCamera)
                action.change { copy(photo = result.photo, rows = emptyList(), editing = null, swap = null) }
                action.estimate(result.photo)
            }
            // Backing out of the camera or the picker changes nothing.
            PhotoPickResult.Cancelled -> Unit
            PhotoPickResult.Failed -> {
                telemetry.failed(PhotoFailure.Error)
                action.change { copy(step = PhotoStep.Failed(offline = false)) }
            }
        }
    }

    private suspend fun PhotoEstimateAction.estimate(photo: MealPhoto) {
        estimateJob?.cancel()
        val note = model.note.trim()
        change { copy(step = PhotoStep.Estimating, usedNote = note.isNotEmpty()) }
        estimateJob = viewModelScope.launch {
            val estimate = estimator.estimate(photo.jpeg, note.ifEmpty { null })
            val foods = if (estimate is PhotoEstimate.Items) details(estimate) else emptyMap()
            takeAction(PhotoEstimateAction.EstimateArrived(estimate, foods))
        }
    }

    /** The catalog food behind each matched line, so its amount can be edited in its own units. */
    private suspend fun details(items: PhotoEstimate.Items): Map<String, Food> = coroutineScope {
        items.items.mapNotNull { it.foodId }.distinct()
            .map { id -> async { foodCatalog.detail(id).getOrNull() } }
            .awaitAll()
            .filterNotNull()
            .associateBy { it.id }
    }

    private suspend fun estimateArrived(action: PhotoEstimateAction.EstimateArrived) {
        if (model.step != PhotoStep.Estimating) return
        when (val estimate = action.estimate) {
            is PhotoEstimate.Items -> {
                if (estimate.items.isEmpty()) {
                    telemetry.failed(PhotoFailure.NothingFound)
                    action.change { copy(step = PhotoStep.NothingFound) }
                    return
                }
                val householdOnly = PhotoEstimateStateBuilder.householdOnly(policy)
                val rows = estimate.items.map { item ->
                    PhotoRows.fromEstimate(newKey(), item, item.foodId?.let(action.foods::get), householdOnly)
                }
                telemetry.estimated(items = rows.size, matched = rows.count { it.food != null })
                action.change { copy(step = PhotoStep.Review, rows = rows) }
            }
            PhotoEstimate.Unavailable -> {
                telemetry.failed(PhotoFailure.Unavailable)
                action.change { copy(step = PhotoStep.Unavailable) }
            }
            PhotoEstimate.TooMany -> {
                telemetry.failed(PhotoFailure.TooMany)
                action.change { copy(step = PhotoStep.TooMany) }
            }
            is PhotoEstimate.Failed -> {
                val offline = estimate.offline || appState.isOffline.value
                telemetry.failed(if (offline) PhotoFailure.Offline else PhotoFailure.Error)
                action.change { copy(step = PhotoStep.Failed(offline)) }
            }
        }
    }

    private fun search(text: String) {
        searchJob?.cancel()
        val query = text.trim()
        if (query.isEmpty()) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            val found = foodCatalog.search(query, SEARCH_LIMIT).getOrNull()
            takeAction(PhotoEstimateAction.SwapResultsArrived(query, found?.foods.orEmpty(), offline = found?.fromCache ?: true))
        }
    }

    private suspend fun PhotoEstimateAction.swapPicked(id: String) {
        val swap = model.swap ?: return
        val food = swap.results?.foods?.firstOrNull { it.id == id } ?: return
        val householdOnly = PhotoEstimateStateBuilder.householdOnly(policy)
        val replacing = swap.replacing?.let { key -> model.rows.firstOrNull { it.key == key } }
        searchJob?.cancel()
        change {
            val row = PhotoRows.picked(replacing?.key ?: newKey(), food, replacing, householdOnly)
            val updated = if (replacing == null) rows + row else rows.map { if (it.key == replacing.key) row else it }
            copy(rows = updated, swap = null)
        }
    }

    private suspend fun addToMeal() {
        val rows = model.rows
        if (logged || model.step != PhotoStep.Review || model.swap != null) return
        val committed = when (val result = logger.log(day, slot, rows.map { it.toLogPick() }) ?: return) {
            is DayWrite.Written -> result.value
            is DayWrite.DayClosed -> {
                sendEvent(PhotoEstimateEvent.DayClosed(result.day))
                return
            }
        }
        logged = true
        telemetry.added(items = rows.size)
        sendEvent(PhotoEstimateEvent.Logged(day, committed))
    }

    private fun newKey(): String = "photo:${nextKey++}"

    companion object {
        const val NOTE_LIMIT = 300
        const val SEARCH_LIMIT = 15
        val SEARCH_DEBOUNCE = 250.milliseconds
    }
}
