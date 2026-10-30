package com.opson.features.logger.impl

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.foods.BarcodeMatch
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.navigation.Route
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.ScannedCode
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * The barcode scanner, a full screen: only the add sheet and search
 * open it, so the route lives in the impl like [FoodDetailRoute]. [dayIso]
 * and [slot] are where a found food is logged; [overSearch] when search opened it, so "Search
 * instead" goes back to it rather than opening another. Behind [BarcodeScanEnabled]. [checkPack]
 * is a Builder checking packs (`CheckPackRoute`): Also read the label starts on, a miss adds the
 * food without logging it, and "Search instead" goes back to where they came from.
 */
@Serializable
data class BarcodeScanRoute(
    val dayIso: String,
    val slot: MealSlot,
    val overSearch: Boolean = false,
    val checkPack: Boolean = false,
) : Route()

/**
 * What the scanner draws. A found food leaves for the food detail before
 * anything about it is shown, unless the code matched several foods that
 * differ: then "Pick a match" lists them, with their numbers as the person's
 * mode shows them ([ScanStage.Matches]). No other stage names a number.
 */
data class BarcodeScanState(
    val slot: MealSlot = MealSlot.Dinner,
    val stage: ScanStage = ScanStage.Starting,
    val torch: Torch = Torch.None,
    /** The camera finds the scene too dark and the light is off: the hint suggests it. */
    val suggestLight: Boolean = false,
    /**
     * A Builder's "Also read the label" (`release.builders`): null when not offered. On, a code
     * that finds a food opens Verify this food instead of the detail, so the next tap
     * photographs the label. Nothing is read from the scanner's frames.
     */
    val readLabel: Boolean? = null,
)

/** The light for a dim kitchen. [None] when there is no live camera, or it has no torch: no button then. */
enum class Torch { None, Off, On }

sealed interface ScanStage {
    /** The permission is being read, or the system prompt is up. */
    data object Starting : ScanStage

    /** The camera is live and the hint is showing, with typing the code and search as ways out. */
    data object Scanning : ScanStage

    /**
     * A code was read or typed. [slow] after 400 ms: almost certainly a first
     * lookup at an outside source. [camera] when the live camera stays under
     * the spinner; a typed code with no camera shows the placeholder.
     */
    data class LookingUp(val slow: Boolean, val camera: Boolean = true) : ScanStage

    /**
     * Typing the digits printed under the barcode. [canLookUp] once there are
     * 8 to 14 of them; [misread] after a look-up whose check digit failed.
     */
    data class Typing(val code: String, val canLookUp: Boolean, val misread: Boolean) : ScanStage

    /** Allowed, but the camera cannot run: none on the device, a failed start, or another app has it. */
    data object CameraUnavailable : ScanStage

    /**
     * Found nowhere. [code] is shown so the person can see what was read.
     * [canAddFood] offers photographing the pack (`release.submitFood`).
     */
    data class Miss(val code: String, val name: String, val canSave: Boolean, val canAddFood: Boolean = false) : ScanStage

    /** Offline with nothing cached, or no source could answer. Not a miss. */
    data object Unavailable : ScanStage

    /**
     * The code is in more than one database and they differ: "Pick a match". [rows] are in the
     * server's order, the suggested match first; foods that are the same within rounding are one
     * row. [openFoodFacts] when a row is Open Food Facts', so its licence's credit goes under them.
     */
    data class Matches(val code: String, val rows: List<MatchRowUi>, val openFoodFacts: Boolean) : ScanStage

    data class NoCamera(val canAskAgain: Boolean) : ScanStage
}

sealed interface BarcodeScanAction {
    data class PermissionChecked(val permission: CameraPermission) : BarcodeScanAction
    data class PermissionAnswered(val permission: CameraPermission) : BarcodeScanAction

    /** Back on screen, from Settings for instance. */
    data object Resumed : BarcodeScanAction

    /** Also read the label, on or off. */
    data class ReadLabelChanged(val on: Boolean) : BarcodeScanAction
    data class CodeRead(val code: ScannedCode) : BarcodeScanAction
    data class LookupSlow(val code: String) : BarcodeScanAction
    /** Everything the code matched, the server's answer first; never empty. */
    data class FoodFound(val code: String, val matches: List<BarcodeMatch>) : BarcodeScanAction

    /** A row of "Pick a match". */
    data class MatchTapped(val foodId: String) : BarcodeScanAction

    /** The person's mode changed, or was read for the first time. */
    data class PolicyArrived(val policy: VisibilityPolicy) : BarcodeScanAction
    data class FoodNotFound(val code: String) : BarcodeScanAction
    data class LookupFailed(val code: String) : BarcodeScanAction
    data class MissNameTyped(val text: String) : BarcodeScanAction
    data object SaveNameTapped : BarcodeScanAction

    /** "Add this food" on a miss: photograph the pack. */
    data object AddFoodTapped : BarcodeScanAction
    data object SearchInsteadTapped : BarcodeScanAction
    data object ScanAgainTapped : BarcodeScanAction
    data object AllowCameraTapped : BarcodeScanAction
    data object OpenSettingsTapped : BarcodeScanAction
    data object CloseTapped : BarcodeScanAction

    /** The camera reported it cannot run. */
    data object CameraUnavailable : BarcodeScanAction

    /** The camera is up, and says whether it has a torch. */
    data class CameraStarted(val hasTorch: Boolean) : BarcodeScanAction

    /** The camera has found the scene too dark to read well for a moment ([low]), or lit again. */
    data class LowLightChanged(val low: Boolean) : BarcodeScanAction
    data object TorchTapped : BarcodeScanAction
    data object RetryCameraTapped : BarcodeScanAction
    data object TypeCodeTapped : BarcodeScanAction
    data class CodeTyped(val text: String) : BarcodeScanAction
    data object LookUpTypedTapped : BarcodeScanAction
    data object TypingCancelled : BarcodeScanAction
}

sealed interface BarcodeScanEvent {
    /** Show the system camera prompt; the answer comes back as [BarcodeScanAction.PermissionAnswered]. */
    data object RequestPermission : BarcodeScanEvent

    /** The camera's read was taken and is being looked up: say so with a haptic, once per scan. */
    data object Scanned : BarcodeScanEvent

    /**
     * A hit, or a match picked: this screen gives way to the food detail for [foodId].
     * [alreadyVerified] names it when a Builder reading labels scanned a food someone already
     * checked: there is nothing to verify, and a snackbar says so.
     */
    data class OpenFoodDetail(val foodId: String, val day: LocalDate, val slot: MealSlot, val alreadyVerified: String? = null) : BarcodeScanEvent

    /** A hit with Also read the label on: Verify this food, in this screen's place. */
    data class OpenCheck(val foodId: String, val name: String) : BarcodeScanEvent

    /** Search, in this screen's place: "Search instead" when the + sheet opened the scanner. */
    data class OpenSearch(val day: LocalDate, val slot: MealSlot) : BarcodeScanEvent

    /** Back to where the scanner was opened from. A name saved on a miss is logged by then. */
    data object Close : BarcodeScanEvent

    /** "Add this food": the pack's photos, in this screen's place, for the code that missed. */
    /** [logs] is false for a Builder checking packs: the food is added, not eaten. */
    data class OpenSubmitFood(val day: LocalDate, val slot: MealSlot, val code: String, val logs: Boolean = true) : BarcodeScanEvent

    /** The day is closed (Close the day, maybe on another phone): the name saved on a miss was not logged. */
    data class DayClosed(val day: LocalDate) : BarcodeScanEvent
}

/** Where a scan stands, as the view model tracks it. */
sealed interface Lookup {
    data object Idle : Lookup
    data class Running(val code: String, val slow: Boolean = false, val typed: Boolean = false) : Lookup
    data class Missed(val code: String) : Lookup

    /** Found in more than one database, with numbers that differ. [groups] as [BarcodeMatches.groups] folds them. */
    data class Matched(val code: String, val groups: List<List<BarcodeMatch>>) : Lookup
    data class Failed(val code: String) : Lookup
}

/** Everything the scanner's state is built from. */
data class ScanModel(
    val permission: CameraPermission? = null,
    val lookup: Lookup = Lookup.Idle,
    val name: String = "",
    /** The code being typed by hand, or null while not typing. */
    val typed: String? = null,
    val typedMisread: Boolean = false,
    val cameraUnavailable: Boolean = false,
    val hasTorch: Boolean = false,
    /** Kept through a lookup and "Scan again", so a dim kitchen stays lit until it is turned off. */
    val torchOn: Boolean = false,
    /** The camera's last word on the light: too dark to read well. */
    val lowLight: Boolean = false,
    /** `release.submitFood`, read once when the scanner opens. */
    val canAddFood: Boolean = false,
    /** `release.builders` and an active Builder, read once when the scanner opens. */
    val canReadLabel: Boolean = false,
    val readLabel: Boolean = false,
)

/** The scanner's state as a pure function of [ScanModel], like the logger's [LoggerStateBuilder]. */
object BarcodeScanStateBuilder {

    /** Typed codes run the same lookup as scanned ones: 8 (EAN-8, UPC-E) to 14 (GTIN-14) digits. */
    val TYPED_LENGTHS = 8..14

    /**
     * A lookup in progress or answered wins, since a typed code can be looked
     * up with no camera at all; then typing; then the camera's own state.
     */
    fun build(slot: MealSlot, model: ScanModel, policy: VisibilityPolicy, day: LocalDate? = null): BarcodeScanState {
        val cameraLive = model.permission == CameraPermission.Granted && !model.cameraUnavailable
        val typed = model.typed
        val stage = when (val lookup = model.lookup) {
            is Lookup.Running -> ScanStage.LookingUp(slow = lookup.slow, camera = cameraLive)
            is Lookup.Matched -> {
                // Bare, as search's rows, for the day the food would go on. Whether that day is closed is not
                // read here, so a delayed number opens no sooner than it does on search.
                val rows = BarcodeMatches.rows(lookup.groups, policy, FormatContext(bare = true, day = day))
                ScanStage.Matches(lookup.code, rows, openFoodFacts = rows.any { SourceBadge.OpenFoodFacts in it.sources })
            }
            is Lookup.Missed -> ScanStage.Miss(
                code = lookup.code,
                name = model.name,
                canSave = model.name.isNotBlank(),
                canAddFood = model.canAddFood,
            )
            is Lookup.Failed -> ScanStage.Unavailable
            Lookup.Idle -> when {
                typed != null -> ScanStage.Typing(typed, canLookUp = typed.length in TYPED_LENGTHS, misread = model.typedMisread)
                else -> when (val permission = model.permission) {
                    null, CameraPermission.NotAsked -> ScanStage.Starting
                    is CameraPermission.Denied -> ScanStage.NoCamera(canAskAgain = permission.canAskAgain)
                    CameraPermission.Granted -> if (model.cameraUnavailable) ScanStage.CameraUnavailable else ScanStage.Scanning
                }
            }
        }
        val torch = torch(stage, model)
        return BarcodeScanState(
            slot = slot,
            stage = stage,
            torch = torch,
            // Only while aiming, and only when there is a light to turn on.
            suggestLight = stage == ScanStage.Scanning && torch == Torch.Off && model.lowLight,
            readLabel = model.readLabel.takeIf { model.canReadLabel && stage == ScanStage.Scanning },
        )
    }

    /** Offered wherever the live camera shows, which includes under a lookup's scrim. */
    private fun torch(stage: ScanStage, model: ScanModel): Torch {
        val cameraShowing = stage == ScanStage.Scanning || (stage is ScanStage.LookingUp && stage.camera)
        return when {
            !cameraShowing || !model.hasTorch -> Torch.None
            model.torchOn -> Torch.On
            else -> Torch.Off
        }
    }
}
