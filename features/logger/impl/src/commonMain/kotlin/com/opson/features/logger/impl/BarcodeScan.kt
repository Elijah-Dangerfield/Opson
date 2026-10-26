package com.opson.features.logger.impl

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.foods.Food
import com.opson.libraries.navigation.Route
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.ScannedCode
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * The barcode scanner, a full screen over the logger sheet: only the logger
 * opens it, so the route lives in the impl like [AmountSheetRoute]. [dayIso]
 * and [slot] are where a found food is logged. Behind [BarcodeScanEnabled].
 */
@Serializable
data class BarcodeScanRoute(val dayIso: String, val slot: MealSlot) : Route()

/**
 * What the scanner draws. No nutrition number is ever in it: a found food
 * leaves for the amount sheet before anything about it is shown.
 */
data class BarcodeScanState(
    val slot: MealSlot = MealSlot.Dinner,
    val stage: ScanStage = ScanStage.Starting,
)

sealed interface ScanStage {
    /** The permission is being read, or the system prompt is up. */
    data object Starting : ScanStage

    /** The camera is live and the hint is showing. */
    data object Scanning : ScanStage

    /** A code was read. [slow] after 400 ms: almost certainly a first lookup at an outside source. */
    data class LookingUp(val slow: Boolean) : ScanStage

    /** Found nowhere. [code] is shown so the person can see what was read. */
    data class Miss(val code: String, val name: String, val canSave: Boolean) : ScanStage

    /** Offline with nothing cached, or no source could answer. Not a miss. */
    data object Unavailable : ScanStage

    data class NoCamera(val canAskAgain: Boolean) : ScanStage
}

sealed interface BarcodeScanAction {
    data class PermissionChecked(val permission: CameraPermission) : BarcodeScanAction
    data class PermissionAnswered(val permission: CameraPermission) : BarcodeScanAction

    /** Back on screen, from Settings for instance. */
    data object Resumed : BarcodeScanAction
    data class CodeRead(val code: ScannedCode) : BarcodeScanAction
    data class LookupSlow(val code: String) : BarcodeScanAction
    data class FoodFound(val code: String, val food: Food) : BarcodeScanAction
    data class FoodNotFound(val code: String) : BarcodeScanAction
    data class LookupFailed(val code: String) : BarcodeScanAction
    data class MissNameTyped(val text: String) : BarcodeScanAction
    data object SaveNameTapped : BarcodeScanAction
    data object SearchInsteadTapped : BarcodeScanAction
    data object ScanAgainTapped : BarcodeScanAction
    data object AllowCameraTapped : BarcodeScanAction
    data object OpenSettingsTapped : BarcodeScanAction
    data object CloseTapped : BarcodeScanAction
}

sealed interface BarcodeScanEvent {
    /** Show the system camera prompt; the answer comes back as [BarcodeScanAction.PermissionAnswered]. */
    data object RequestPermission : BarcodeScanEvent

    /** A hit: this screen gives way to the amount sheet for [foodId], over the logger. */
    data class OpenAmount(val foodId: String, val day: LocalDate, val slot: MealSlot) : BarcodeScanEvent

    /** Back to the logger, which shows anything just picked in its footer. */
    data object Close : BarcodeScanEvent
}

/** Where a scan stands, as the view model tracks it. */
sealed interface Lookup {
    data object Idle : Lookup
    data class Running(val code: String, val slow: Boolean = false) : Lookup
    data class Missed(val code: String) : Lookup
    data class Failed(val code: String) : Lookup
}

/** Everything the scanner's state is built from. */
data class ScanModel(
    val permission: CameraPermission? = null,
    val lookup: Lookup = Lookup.Idle,
    val name: String = "",
)

/** The scanner's state as a pure function of [ScanModel], like the logger's [LoggerStateBuilder]. */
object BarcodeScanStateBuilder {

    fun build(slot: MealSlot, model: ScanModel): BarcodeScanState = BarcodeScanState(
        slot = slot,
        stage = when (val permission = model.permission) {
            null, CameraPermission.NotAsked -> ScanStage.Starting
            is CameraPermission.Denied -> ScanStage.NoCamera(canAskAgain = permission.canAskAgain)
            CameraPermission.Granted -> when (val lookup = model.lookup) {
                Lookup.Idle -> ScanStage.Scanning
                is Lookup.Running -> ScanStage.LookingUp(slow = lookup.slow)
                is Lookup.Missed -> ScanStage.Miss(code = lookup.code, name = model.name, canSave = model.name.isNotBlank())
                is Lookup.Failed -> ScanStage.Unavailable
            }
        },
    )
}
