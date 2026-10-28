package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.logger.SubmitEntry
import com.opson.features.logger.SubmitFoodRoute
import com.opson.features.today.DayClosedRoute
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.label
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.nullableSerializableType
import com.opson.libraries.navigation.screen
import com.opson.libraries.navigation.serializableType
import com.opson.libraries.scanner.BarcodeCamera
import com.opson.libraries.scanner.MealPhotoPicker
import com.opson.libraries.ui.snackbar.showSnackBar
import com.opson.libraries.ui.system.confirm
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.submit_done_kept
import opson.libraries.resources.generated.resources.submit_done_saved
import opson.libraries.resources.generated.resources.submit_done_saved_sent
import opson.libraries.resources.generated.resources.submit_done_sent
import org.jetbrains.compose.resources.getString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.reflect.typeOf

/**
 * "Add a packaged food" as a full screen, behind `release.submitFood`. A barcode miss, search (no
 * results, or "Can't find it?" under them) and the + sheet open it to log to a meal; the Foods tab's
 * Add a food opens it to save only. [SubmitFoodRoute] is in the logger's api for that reason.
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
            typeMap = mapOf(
                typeOf<MealSlot?>() to nullableSerializableType<MealSlot>(),
                typeOf<SubmitEntry>() to serializableType<SubmitEntry>(),
            ),
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
                    val slot = event.slot
                    val message = when {
                        slot == null && event.sent -> getString(Res.string.submit_done_saved_sent, event.name)
                        slot == null -> getString(Res.string.submit_done_saved, event.name)
                        event.sent -> getString(Res.string.submit_done_sent, event.name, getString(slot.label))
                        else -> getString(Res.string.submit_done_kept, event.name, getString(slot.label))
                    }
                    showSnackBar(message = message)
                    // From Add a food, the form it was opened over is done with too: back to the Foods tab, where the food now is.
                    if (route.entry == SubmitEntry.Foods) {
                        router.batch {
                            goBack()
                            goBack()
                        }
                    } else {
                        router.goBack()
                    }
                }
                SubmitFoodEvent.Close -> router.goBack()
                // The closed-day sheet takes this screen's place; the food is in their foods to log once it reopens.
                is SubmitFoodEvent.DayClosed -> router.batch {
                    goBack()
                    navigate(DayClosedRoute(event.day.toString()))
                }
            }
        }

        SubmitFoodScreen(state = state, onAction = viewModel::takeAction)
    }
}
