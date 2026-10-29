package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.opson.libraries.ui.snackbar.showSnackBar
import com.opson.libraries.ui.system.confirm
import com.opson.system.AppTheme
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.food_report_applied
import opson.libraries.resources.generated.resources.food_report_sent
import org.jetbrains.compose.resources.getString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * "Report a problem" as a sheet over the food's detail, behind `release.foodReports`. Only the
 * detail's ⋮ opens it ([FoodReportRoute]); with the flag off that item opens the bug report.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class FoodReportEntryPoint(
    private val viewModelFactory: (FoodReportRoute) -> FoodReportViewModel,
    private val barcodeCamera: BarcodeCamera,
    // A provider, so nothing about photos is built at launch.
    private val mealPhotoPicker: () -> MealPhotoPicker,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        bottomSheet<FoodReportRoute> { backStackEntry, sheetState ->
            FoodReportSheet(backStackEntry.toRoute<FoodReportRoute>(), sheetState, router)
        }
    }

    @Composable
    private fun FoodReportSheet(route: FoodReportRoute, sheetState: BottomSheetState, router: Router) {
        val viewModel: FoodReportViewModel = viewModel { viewModelFactory(route) }
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        val picker = remember { mealPhotoPicker() }
        val camera = picker.rememberCamera { viewModel.takeAction(FoodReportAction.PhotoAnswered(it)) }
        val library = picker.rememberLibrary { viewModel.takeAction(FoodReportAction.PhotoAnswered(it)) }
        val requestPermission = barcodeCamera.rememberPermissionRequest { answer ->
            viewModel.takeAction(FoodReportAction.CameraPermissionAnswered(answer))
        }
        val haptics = LocalHapticFeedback.current

        viewModel.ObserveEvents { event ->
            when (event) {
                FoodReportEvent.LaunchCamera -> camera()
                FoodReportEvent.LaunchLibrary -> library()
                FoodReportEvent.RequestCameraPermission -> requestPermission()
                is FoodReportEvent.Sent -> {
                    haptics.confirm()
                    showSnackBar(message = getString(if (event.applied) Res.string.food_report_applied else Res.string.food_report_sent, event.name))
                    sheetState.dismiss()
                }
            }
        }

        BottomSheet(
            onDismissRequest = { router.goBack() },
            state = sheetState,
            showDragHandle = true,
            scrollableContent = true,
            backgroundColor = AppTheme.colors.bg,
        ) {
            FoodReportSheetContent(state = state, onAction = viewModel::takeAction, onClose = { sheetState.dismiss() })
        }
    }
}
