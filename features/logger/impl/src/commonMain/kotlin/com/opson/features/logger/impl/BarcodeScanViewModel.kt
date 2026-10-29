package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.BarcodeMissReporter
import com.opson.features.builder.BuildersEnabled
import com.opson.libraries.foods.BuilderProgram
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.Gtin
import com.opson.libraries.scanner.BarcodeFormat
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.CameraPermissions
import com.opson.libraries.scanner.ScannedCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * The scanner: camera permission, one lookup per read, and what a miss
 * offers. A hit hands the food to the food detail; a miss is reported once
 * so the catalog's gaps show up on a dashboard, then offers "Add this food"
 * (photos of the pack, with `release.submitFood`) and "Save the name only".
 * The miss is also held for [MissedBarcodePairing], in case search finds it.
 *
 * The camera reads the same pack many times a second, so only a read that
 * arrives while scanning, and that is a real GTIN, starts a lookup, and only
 * that read buzzes ([BarcodeScanEvent.Scanned]).
 */
@Inject
class BarcodeScanViewModel(
    @Assisted route: BarcodeScanRoute,
    private val foodCatalog: FoodCatalog,
    private val missReporter: BarcodeMissReporter,
    private val permissions: CameraPermissions,
    private val customFoods: CustomFoodRepository,
    private val logger: FoodLogger,
    private val telemetry: BarcodeTelemetry,
    private val pairing: MissedBarcodePairing,
    submitFood: SubmitFoodEnabled,
    builders: BuilderProgram,
    buildersEnabled: BuildersEnabled,
) : SEAViewModel<BarcodeScanState, BarcodeScanEvent, BarcodeScanAction>(
    initialStateArg = BarcodeScanState(slot = route.slot),
) {
    private val day: LocalDate = LocalDate.parse(route.dayIso)
    private val slot: MealSlot = route.slot
    private val overSearch: Boolean = route.overSearch
    private var model = ScanModel(canAddFood = submitFood(), canReadLabel = buildersEnabled.value && builders.isBuilder)
    private var slowJob: Job? = null

    /**
     * The name is logged and the scanner is closing: a second Save queued behind the first would log it
     * again. A closed day writes nothing, so it clears this.
     */
    private var saved = false

    init {
        viewModelScope.launch { takeAction(BarcodeScanAction.PermissionChecked(permissions.current())) }
    }

    override suspend fun handleAction(action: BarcodeScanAction) {
        when (action) {
            is BarcodeScanAction.PermissionChecked -> permissionChecked(action)
            is BarcodeScanAction.PermissionAnswered -> permissionAnswered(action)
            BarcodeScanAction.Resumed -> {
                // Only coming back from Settings changes anything. Re-asking here would loop, since
                // the system prompt itself pauses and resumes the screen on Android.
                if (model.permission is CameraPermission.Denied && permissions.current() == CameraPermission.Granted) {
                    action.rebuild { copy(permission = CameraPermission.Granted) }
                    telemetry.scanStarted(asked = false)
                }
            }
            is BarcodeScanAction.ReadLabelChanged -> if (model.canReadLabel) action.rebuild { copy(readLabel = action.on) }
            is BarcodeScanAction.CodeRead -> codeRead(action, action.code)
            is BarcodeScanAction.LookupSlow -> {
                val lookup = model.lookup
                if (lookup is Lookup.Running && lookup.code == action.code) action.rebuild { copy(lookup = lookup.copy(slow = true)) }
            }
            is BarcodeScanAction.FoodFound -> {
                val lookup = model.lookup
                if (lookup !is Lookup.Running || lookup.code != action.code) return
                slowJob?.cancel()
                telemetry.hit(action.food.source, slow = lookup.slow, typed = lookup.typed)
                sendEvent(
                    if (model.canReadLabel && model.readLabel) {
                        BarcodeScanEvent.OpenCheck(action.food.id, action.food.name)
                    } else {
                        BarcodeScanEvent.OpenFoodDetail(action.food.id, day, slot)
                    },
                )
            }
            is BarcodeScanAction.FoodNotFound -> {
                val lookup = model.lookup
                if (lookup !is Lookup.Running || lookup.code != action.code) return
                slowJob?.cancel()
                action.rebuild { copy(lookup = Lookup.Missed(action.code), name = "") }
                telemetry.miss(typed = lookup.typed)
                pairing.missed(action.code)
                viewModelScope.launch { missReporter.report(action.code) }
            }
            is BarcodeScanAction.LookupFailed -> {
                if (!isRunning(action.code)) return
                slowJob?.cancel()
                action.rebuild { copy(lookup = Lookup.Failed(action.code)) }
            }
            is BarcodeScanAction.MissNameTyped -> action.rebuild { copy(name = action.text) }
            BarcodeScanAction.SaveNameTapped -> saveName()
            BarcodeScanAction.AddFoodTapped -> (model.lookup as? Lookup.Missed)?.let { missed ->
                if (model.canAddFood) sendEvent(BarcodeScanEvent.OpenSubmitFood(day, slot, missed.code))
            }
            BarcodeScanAction.SearchInsteadTapped -> sendEvent(if (overSearch) BarcodeScanEvent.Close else BarcodeScanEvent.OpenSearch(day, slot))
            BarcodeScanAction.ScanAgainTapped -> action.rebuild { copy(lookup = Lookup.Idle, name = "", typed = null) }
            BarcodeScanAction.AllowCameraTapped -> sendEvent(BarcodeScanEvent.RequestPermission)
            BarcodeScanAction.OpenSettingsTapped -> permissions.openSettings()
            BarcodeScanAction.CloseTapped -> sendEvent(BarcodeScanEvent.Close)
            BarcodeScanAction.CameraUnavailable -> if (model.permission == CameraPermission.Granted && !model.cameraUnavailable) {
                action.rebuild { copy(cameraUnavailable = true) }
                telemetry.cameraUnavailable()
            }
            is BarcodeScanAction.CameraStarted -> if (model.hasTorch != action.hasTorch) {
                action.rebuild { copy(hasTorch = action.hasTorch) }
            }
            BarcodeScanAction.TorchTapped -> if (model.hasTorch) action.rebuild { copy(torchOn = !torchOn) }
            // Composing the viewfinder again starts a new camera session.
            BarcodeScanAction.RetryCameraTapped -> action.rebuild { copy(cameraUnavailable = false) }
            BarcodeScanAction.TypeCodeTapped -> if (model.lookup == Lookup.Idle) {
                action.rebuild { copy(typed = "", typedMisread = false) }
            }
            is BarcodeScanAction.CodeTyped -> if (model.typed != null) {
                val digits = action.text.filter { it in '0'..'9' }.take(BarcodeScanStateBuilder.TYPED_LENGTHS.last)
                action.rebuild { copy(typed = digits, typedMisread = false) }
            }
            BarcodeScanAction.LookUpTypedTapped -> lookUpTyped(action)
            BarcodeScanAction.TypingCancelled -> action.rebuild { copy(typed = null, typedMisread = false) }
        }
    }

    /** The digits under the barcode, through the same lookup as a scan. A wrong check digit is a typo to fix. */
    private suspend fun lookUpTyped(action: BarcodeScanAction) {
        val code = model.typed ?: return
        if (model.lookup != Lookup.Idle || code.length !in BarcodeScanStateBuilder.TYPED_LENGTHS) return
        if (Gtin.normalize(code) == null) {
            action.rebuild { copy(typedMisread = true) }
            return
        }
        action.rebuild { copy(typed = null, typedMisread = false) }
        lookUp(action, code, typed = true)
    }

    private suspend fun permissionChecked(action: BarcodeScanAction.PermissionChecked) {
        action.rebuild { copy(permission = action.permission) }
        when (val permission = action.permission) {
            CameraPermission.Granted -> telemetry.scanStarted(asked = false)
            CameraPermission.NotAsked -> sendEvent(BarcodeScanEvent.RequestPermission)
            is CameraPermission.Denied -> telemetry.permissionDenied(canAskAgain = permission.canAskAgain)
        }
    }

    private suspend fun permissionAnswered(action: BarcodeScanAction.PermissionAnswered) {
        action.rebuild { copy(permission = action.permission) }
        when (val permission = action.permission) {
            CameraPermission.Granted -> telemetry.scanStarted(asked = true)
            is CameraPermission.Denied -> telemetry.permissionDenied(canAskAgain = permission.canAskAgain)
            CameraPermission.NotAsked -> Unit
        }
    }

    private suspend fun codeRead(action: BarcodeScanAction, read: ScannedCode) {
        val scanning = model.permission == CameraPermission.Granted && !model.cameraUnavailable && model.typed == null
        if (!scanning || model.lookup != Lookup.Idle) return
        // Some decoders report a UPC-E as its six digits alone.
        val code = if (read.format == BarcodeFormat.UpcE) Gtin.completeUpcE(read.value) else read.value
        // A failed check digit is a misread, not a product: keep scanning.
        if (Gtin.normalize(code) == null) return
        // The first valid read is taken at once: the check digit already rules out a misread, so
        // waiting for the same code twice would only make the person hold still longer.
        sendEvent(BarcodeScanEvent.Scanned)
        lookUp(action, code, typed = false)
    }

    private suspend fun lookUp(action: BarcodeScanAction, code: String, typed: Boolean) {
        action.rebuild { copy(lookup = Lookup.Running(code, typed = typed)) }
        slowJob = viewModelScope.launch {
            delay(SLOW_AFTER)
            takeAction(BarcodeScanAction.LookupSlow(code))
        }
        viewModelScope.launch {
            // The code as read: the server matches every equivalent form, and an outside source gets it as printed.
            val result = foodCatalog.barcode(code)
            val food = result.getOrNull()
            takeAction(
                when {
                    food != null -> BarcodeScanAction.FoodFound(code, food)
                    result.isSuccess -> BarcodeScanAction.FoodNotFound(code)
                    else -> BarcodeScanAction.LookupFailed(code)
                },
            )
        }
    }

    private suspend fun saveName() {
        val name = model.name.trim()
        if (saved || model.lookup !is Lookup.Missed || name.isEmpty()) return
        saved = true
        when (val logged = logger.log(day, slot, listOf(nameOnlyPick(name, customFoods.observeAll().first(), customFoods)))) {
            is DayWrite.DayClosed -> {
                // Nothing was written and the scanner stays open, so Save works again once the day is reopened.
                saved = false
                sendEvent(BarcodeScanEvent.DayClosed(logged.day))
            }
            else -> sendEvent(BarcodeScanEvent.Close)
        }
    }

    private fun isRunning(code: String): Boolean = (model.lookup as? Lookup.Running)?.code == code

    private suspend fun BarcodeScanAction.rebuild(change: ScanModel.() -> ScanModel) {
        model = model.change()
        val current = model
        updateState { BarcodeScanStateBuilder.build(slot, current) }
    }

    companion object {
        /** A catalog hit lands well inside this; past it, an outside source is being asked. */
        val SLOW_AFTER = 400.milliseconds
    }
}
