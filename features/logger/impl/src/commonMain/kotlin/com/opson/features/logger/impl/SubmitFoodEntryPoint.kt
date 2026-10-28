package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.label
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.screen
import com.opson.libraries.navigation.serializableType
import com.opson.libraries.scanner.BarcodeCamera
import com.opson.libraries.scanner.MealPhotoPicker
import com.opson.libraries.ui.snackbar.showSnackBar
import com.opson.libraries.ui.system.confirm
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.submit_done_kept
import opson.libraries.resources.generated.resources.submit_done_sent
import org.jetbrains.compose.resources.getString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.reflect.typeOf

/**
 * "Add this food" as a full screen. Only a barcode miss and a search with no
 * results open it, both behind `release.submitFood`; they navigate to
 * [SubmitFoodRoute] from the logger's own entry point.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class SubmitFoodEntryPoint(
    private val viewModelFactory: (SubmitFoodRoute) -> SubmitFoodViewModel,
    private val barcodeCamera: BarcodeCamera,
    // A provider, so nothing about photos is built at launch.
    private val mealPhotoPicker: () -> MealPhotoPicker,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<SubmitFoodRoute>(
            typeMap = mapOf(typeOf<MealSlot>() to serializableType<MealSlot>()),
        ) { backStackEntry ->
            SubmitFood(backStackEntry.toRoute<SubmitFoodRoute>(), router)
        }
    }

    @Composable
    private fun SubmitFood(route: SubmitFoodRoute, router: Router) {
        val viewModel: SubmitFoodViewModel = viewModel { viewModelFactory(route) }
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        val picker = remember { mealPhotoPicker() }
        // Each launcher answers for the side it was opened for; the view model says which.
        val frontCamera = picker.rememberCamera { viewModel.takeAction(SubmitFoodAction.PhotoAnswered(PackSide.Front, it, fromCamera = true)) }
        val labelCamera = picker.rememberCamera { viewModel.takeAction(SubmitFoodAction.PhotoAnswered(PackSide.Label, it, fromCamera = true)) }
        val frontLibrary = picker.rememberLibrary { viewModel.takeAction(SubmitFoodAction.PhotoAnswered(PackSide.Front, it, fromCamera = false)) }
        val labelLibrary = picker.rememberLibrary { viewModel.takeAction(SubmitFoodAction.PhotoAnswered(PackSide.Label, it, fromCamera = false)) }
        val requestPermission = barcodeCamera.rememberPermissionRequest { answer ->
            viewModel.takeAction(SubmitFoodAction.CameraPermissionAnswered(answer))
        }
        val haptics = LocalHapticFeedback.current

        viewModel.ObserveEvents { event ->
            when (event) {
                is SubmitFoodEvent.LaunchCamera -> if (event.side == PackSide.Front) frontCamera() else labelCamera()
                is SubmitFoodEvent.LaunchLibrary -> if (event.side == PackSide.Front) frontLibrary() else labelLibrary()
                SubmitFoodEvent.RequestCameraPermission -> requestPermission()
                is SubmitFoodEvent.Finished -> {
                    haptics.confirm()
                    val message = if (event.sent) Res.string.submit_done_sent else Res.string.submit_done_kept
                    showSnackBar(message = getString(message, event.name, getString(event.slot.label)))
                    router.goBack()
                }
                SubmitFoodEvent.Close -> router.goBack()
            }
        }

        SubmitFoodScreen(state = state, onAction = viewModel::takeAction)
    }
}
