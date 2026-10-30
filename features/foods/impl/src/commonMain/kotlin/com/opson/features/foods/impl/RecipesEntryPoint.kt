package com.opson.features.foods.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.foods.LogRecipeRoute
import com.opson.features.foods.PublicRecipeRoute
import com.opson.features.foods.RecipeEditRoute
import com.opson.features.foods.RecipeRoute
import com.opson.features.foods.RecipeShareRoute
import com.opson.features.foods.ReportRecipeRoute
import com.opson.features.foods.ScanRecipeRoute
import com.opson.features.logger.FoodSearchRoute
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.bottomSheet
import com.opson.libraries.navigation.screen
import com.opson.libraries.scanner.BarcodeCamera
import com.opson.libraries.scanner.BarcodeFormat
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetState
import com.opson.libraries.ui.system.confirm
import com.opson.system.AppTheme
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Recipes and sharing them (`release.recipes`, docs/plans/recipes.md): making and editing one, its
 * page, the share sheet, someone's shared recipe, logging a serving, reporting, and the scanner for
 * a recipe's QR code. The Foods tab's Recipes card, search and a recipe link are the ways in, and
 * each checks the flag, so these routes are registered whatever it says.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class RecipesEntryPoint(
    private val editViewModelFactory: (RecipeEditRoute) -> RecipeEditViewModel,
    private val viewViewModelFactory: (RecipeRoute) -> RecipeViewModel,
    private val shareViewModelFactory: (RecipeShareRoute) -> RecipeShareViewModel,
    private val publicViewModelFactory: (PublicRecipeRoute) -> PublicRecipeViewModel,
    private val logViewModelFactory: (LogRecipeRoute) -> LogRecipeViewModel,
    private val reportViewModelFactory: (ReportRecipeRoute) -> ReportRecipeViewModel,
    private val scanViewModelFactory: () -> ScanRecipeViewModel,
    private val barcodeCamera: BarcodeCamera,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<RecipeEditRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<RecipeEditRoute>()
            val viewModel: RecipeEditViewModel = viewModel { editViewModelFactory(route) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is RecipeEditEvent.OpenPicker -> router.navigate(FoodSearchRoute(pickForMeal = event.requestId))
                    is RecipeEditEvent.ShowToast -> show(event.toast)
                    // A new recipe's page takes the form's place; an edit goes back to the page it came from.
                    is RecipeEditEvent.Saved -> if (event.created) {
                        router.batch {
                            goBack()
                            navigate(RecipeRoute(event.recipeId))
                        }
                    } else {
                        router.goBack()
                    }
                    RecipeEditEvent.Close -> router.goBack()
                }
            }
            RecipeEditScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<RecipeRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<RecipeRoute>()
            val viewModel: RecipeViewModel = viewModel { viewViewModelFactory(route) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is RecipeViewEvent.OpenLog -> router.navigate(LogRecipeRoute(recipeId = event.recipeId))
                    is RecipeViewEvent.OpenShare -> router.navigate(RecipeShareRoute(event.recipeId))
                    is RecipeViewEvent.OpenEdit -> router.navigate(RecipeEditRoute(event.recipeId))
                    is RecipeViewEvent.ShowToast -> show(event.toast)
                    RecipeViewEvent.Close -> router.goBack()
                }
            }
            RecipeScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<PublicRecipeRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PublicRecipeRoute>()
            val viewModel: PublicRecipeViewModel = viewModel { publicViewModelFactory(route) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is PublicRecipeEvent.OpenLog -> router.navigate(LogRecipeRoute(code = event.code))
                    is PublicRecipeEvent.OpenRecipe -> router.batch {
                        goBack()
                        navigate(RecipeRoute(event.recipeId))
                    }
                    is PublicRecipeEvent.OpenReport -> router.navigate(ReportRecipeRoute(event.code, event.name))
                    is PublicRecipeEvent.ShowToast -> show(event.toast)
                    PublicRecipeEvent.Close -> router.goBack()
                }
            }
            PublicRecipeScreen(state = state, onAction = viewModel::takeAction)
        }

        bottomSheet<RecipeShareRoute> { backStackEntry, sheetState ->
            ShareSheet(backStackEntry.toRoute(), sheetState, router)
        }

        bottomSheet<LogRecipeRoute> { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<LogRecipeRoute>()
            val viewModel: LogRecipeViewModel = viewModel { logViewModelFactory(route) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            val haptics = LocalHapticFeedback.current
            viewModel.ObserveEvents { event ->
                when (event) {
                    is LogRecipeEvent.ShowToast -> {
                        haptics.confirm()
                        show(event.toast)
                    }
                    LogRecipeEvent.Close -> sheetState.dismiss()
                }
            }
            BottomSheet(
                onDismissRequest = { router.goBack() },
                state = sheetState,
                showDragHandle = true,
                scrollableContent = true,
                backgroundColor = AppTheme.colors.bg,
            ) {
                LogRecipeSheetContent(state = state, onAction = viewModel::takeAction, onClose = { sheetState.dismiss() })
            }
        }

        bottomSheet<ReportRecipeRoute> { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<ReportRecipeRoute>()
            val viewModel: ReportRecipeViewModel = viewModel { reportViewModelFactory(route) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is ReportRecipeEvent.Sent -> {
                        show(event.toast)
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
                ReportRecipeSheetContent(state = state, onAction = viewModel::takeAction, onClose = { sheetState.dismiss() })
            }
        }

        screen<ScanRecipeRoute> {
            val viewModel: ScanRecipeViewModel = viewModel { scanViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            val requestPermission = barcodeCamera.rememberPermissionRequest { answer ->
                viewModel.takeAction(ScanRecipeAction.PermissionAnswered(answer))
            }
            LifecycleResumeEffect(viewModel) {
                viewModel.takeAction(ScanRecipeAction.Resumed)
                onPauseOrDispose { }
            }
            val haptics = LocalHapticFeedback.current
            viewModel.ObserveEvents { event ->
                when (event) {
                    ScanRecipeEvent.RequestPermission -> requestPermission()
                    ScanRecipeEvent.Scanned -> haptics.confirm()
                    // The recipe takes the scanner's place.
                    is ScanRecipeEvent.OpenRecipe -> router.batch {
                        goBack()
                        navigate(PublicRecipeRoute(event.code))
                    }
                    ScanRecipeEvent.Close -> router.goBack()
                }
            }
            ScanRecipeScreen(
                state = state,
                onAction = viewModel::takeAction,
                viewfinder = { modifier ->
                    barcodeCamera.Viewfinder(
                        formats = setOf(BarcodeFormat.Qr),
                        onCode = { viewModel.takeAction(ScanRecipeAction.CodeRead(it)) },
                        onUnavailable = { viewModel.takeAction(ScanRecipeAction.CameraUnavailable) },
                        scanArea = RecipeScanArea,
                        torchOn = false,
                        onTorchAvailable = {},
                        onLowLight = {},
                        focusPoint = null,
                        modifier = modifier,
                    )
                },
            )
        }
    }

    @Suppress("DEPRECATION") // LocalClipboard is suspend-only and takes a platform ClipEntry; this is one line of text.
    @Composable
    private fun ShareSheet(route: RecipeShareRoute, sheetState: BottomSheetState, router: Router) {
        val viewModel: RecipeShareViewModel = viewModel { shareViewModelFactory(route) }
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        val clipboard = LocalClipboardManager.current
        viewModel.ObserveEvents { event ->
            when (event) {
                is RecipeShareEvent.CopyLink -> clipboard.setText(AnnotatedString(event.link))
                is RecipeShareEvent.ShareLink -> router.shareText(event.link)
                is RecipeShareEvent.ShareQr -> router.shareFile(event.fileName, PNG, event.png)
                is RecipeShareEvent.ShowToast -> show(event.toast)
                RecipeShareEvent.Close -> sheetState.dismiss()
            }
        }
        BottomSheet(
            onDismissRequest = { router.goBack() },
            state = sheetState,
            showDragHandle = true,
            scrollableContent = true,
            backgroundColor = AppTheme.colors.bg,
        ) {
            RecipeShareSheetContent(state = state, onAction = viewModel::takeAction, onClose = { sheetState.dismiss() })
        }
    }

    private companion object {
        const val PNG = "image/png"
    }
}
