package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.bottomSheet
import com.opson.libraries.scanner.BarcodeCamera
import com.opson.libraries.scanner.MealPhotoPicker
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetState
import com.opson.system.AppTheme
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Check against the pack as a sheet, behind `release.builders`: the food detail's ⋮ for an active
 * Builder, and the scanner after a code when Also read the label is on. Registered whatever the
 * flag says; only those two link here, and both check it.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class CheckFoodEntryPoint(
    private val viewModelFactory: (CheckFoodRoute) -> CheckFoodViewModel,
    private val barcodeCamera: BarcodeCamera,
    // A provider, so nothing about photos is built at launch.
    private val mealPhotoPicker: () -> MealPhotoPicker,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        bottomSheet<CheckFoodRoute> { backStackEntry, sheetState ->
            CheckFoodSheet(backStackEntry.toRoute<CheckFoodRoute>(), sheetState, router)
        }
    }

    @Composable
    private fun CheckFoodSheet(route: CheckFoodRoute, sheetState: BottomSheetState, router: Router) {
        val viewModel: CheckFoodViewModel = viewModel { viewModelFactory(route) }
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        val picker = remember { mealPhotoPicker() }
        val camera = picker.rememberCamera { viewModel.takeAction(CheckFoodAction.PhotoAnswered(it)) }
        val library = picker.rememberLibrary { viewModel.takeAction(CheckFoodAction.PhotoAnswered(it)) }
        val requestPermission = barcodeCamera.rememberPermissionRequest { answer ->
            viewModel.takeAction(CheckFoodAction.CameraPermissionAnswered(answer))
        }

        viewModel.ObserveEvents { event ->
            when (event) {
                CheckFoodEvent.LaunchCamera -> camera()
                CheckFoodEvent.LaunchLibrary -> library()
                CheckFoodEvent.RequestCameraPermission -> requestPermission()
                CheckFoodEvent.Close -> sheetState.dismiss()
            }
        }

        BottomSheet(
            onDismissRequest = { router.goBack() },
            state = sheetState,
            showDragHandle = true,
            scrollableContent = true,
            backgroundColor = AppTheme.colors.bg,
        ) {
            CheckFoodSheetContent(state = state, onAction = viewModel::takeAction, onClose = { sheetState.dismiss() })
        }
    }
}
