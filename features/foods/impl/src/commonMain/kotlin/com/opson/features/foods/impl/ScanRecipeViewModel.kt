package com.opson.features.foods.impl

import com.opson.libraries.diary.RecipeLinks
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.CameraPermissions
import com.opson.libraries.scanner.ScannedCode
import me.tatarka.inject.annotations.Inject

/** Scan a recipe code: the camera once it may run, or why it can't. [notRecipe] after a QR code that isn't one of ours. */
data class ScanRecipeState(
    val permission: CameraPermission = CameraPermission.NotAsked,
    val cameraUnavailable: Boolean = false,
    val notRecipe: Boolean = false,
)

sealed interface ScanRecipeAction {
    data object Resumed : ScanRecipeAction
    data class PermissionAnswered(val answer: CameraPermission) : ScanRecipeAction
    data object AllowTapped : ScanRecipeAction
    data object SettingsTapped : ScanRecipeAction
    data class CodeRead(val code: ScannedCode) : ScanRecipeAction
    data object CameraUnavailable : ScanRecipeAction
    data object CloseTapped : ScanRecipeAction
}

sealed interface ScanRecipeEvent {
    data object RequestPermission : ScanRecipeEvent
    data object Scanned : ScanRecipeEvent

    /** A recipe's code was read: its page takes the scanner's place. */
    data class OpenRecipe(val code: String) : ScanRecipeEvent
    data object Close : ScanRecipeEvent
}

/**
 * The camera reads QR codes only here (`BarcodeFormat.Qr`); a food scan never does. The first
 * Opson recipe link it reads opens that recipe. Anything else says so and keeps reading.
 */
@Inject
class ScanRecipeViewModel(
    private val permissions: CameraPermissions,
) : SEAViewModel<ScanRecipeState, ScanRecipeEvent, ScanRecipeAction>(initialStateArg = ScanRecipeState(permission = permissions.current())) {

    private var opened = false

    override suspend fun handleAction(action: ScanRecipeAction) {
        when (action) {
            ScanRecipeAction.Resumed -> {
                val now = permissions.current()
                action.updateState { it.copy(permission = now) }
                if (now == CameraPermission.NotAsked) sendEvent(ScanRecipeEvent.RequestPermission)
            }
            is ScanRecipeAction.PermissionAnswered -> action.updateState { it.copy(permission = action.answer) }
            ScanRecipeAction.AllowTapped -> sendEvent(ScanRecipeEvent.RequestPermission)
            ScanRecipeAction.SettingsTapped -> permissions.openSettings()
            is ScanRecipeAction.CodeRead -> {
                if (opened) return
                val code = RecipeLinks.codeFrom(action.code.value)
                if (code == null) {
                    if (!state.notRecipe) action.updateState { it.copy(notRecipe = true) }
                    return
                }
                opened = true
                sendEvent(ScanRecipeEvent.Scanned)
                sendEvent(ScanRecipeEvent.OpenRecipe(code))
            }
            ScanRecipeAction.CameraUnavailable -> action.updateState { it.copy(cameraUnavailable = true) }
            ScanRecipeAction.CloseTapped -> sendEvent(ScanRecipeEvent.Close)
        }
    }
}
