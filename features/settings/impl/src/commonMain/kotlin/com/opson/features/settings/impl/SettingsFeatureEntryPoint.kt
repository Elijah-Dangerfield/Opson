package com.opson.features.settings.impl

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.home.FeedbackRoute
import com.opson.features.onboarding.OnboardingRoute
import com.opson.features.settings.LookAndFeelRoute
import com.opson.features.settings.ModesRoute
import com.opson.features.settings.MoreRoute
import com.opson.features.settings.PauseBeforeMoreRoute
import com.opson.features.settings.SupportRoute
import com.opson.features.settings.TargetsRoute
import com.opson.features.settings.WaterSettingsRoute
import com.opson.features.settings.impl.look.LookAndFeelEvent
import com.opson.features.settings.impl.look.LookAndFeelScreen
import com.opson.features.settings.impl.look.LookAndFeelViewModel
import com.opson.features.settings.impl.modes.ModesEvent
import com.opson.features.settings.impl.modes.ModesScreen
import com.opson.features.settings.impl.modes.ModesViewModel
import com.opson.features.settings.impl.more.MoreEvent
import com.opson.features.settings.impl.more.MoreScreen
import com.opson.features.settings.impl.more.MoreViewModel
import com.opson.features.settings.impl.pause.PauseAction
import com.opson.features.settings.impl.pause.PauseEvent
import com.opson.features.settings.impl.pause.PauseSheetContent
import com.opson.features.settings.impl.pause.PauseViewModel
import com.opson.features.settings.impl.support.EXPORT_MIME_TYPE
import com.opson.features.settings.impl.support.SupportEvent
import com.opson.features.settings.impl.support.SupportScreen
import com.opson.features.settings.impl.support.SupportViewModel
import com.opson.features.settings.impl.targets.TargetsEvent
import com.opson.features.settings.impl.targets.TargetsScreen
import com.opson.features.settings.impl.targets.TargetsViewModel
import com.opson.features.settings.impl.water.WaterSettingsEvent
import com.opson.features.settings.impl.water.WaterSettingsScreen
import com.opson.features.settings.impl.water.WaterSettingsViewModel
import com.opson.features.settings.toRoute
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.NavigationOptions
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.bottomSheet
import com.opson.libraries.navigation.screen
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.opson.libraries.ui.snackbar.showSnackBar
import com.opson.system.AppTheme
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.getString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class SettingsFeatureEntryPoint(
    private val moreViewModel: () -> MoreViewModel,
    private val modesViewModel: () -> ModesViewModel,
    private val pauseViewModel: (NumbersChange) -> PauseViewModel,
    private val targetsViewModel: () -> TargetsViewModel,
    private val waterSettingsViewModel: (backToMore: Boolean) -> WaterSettingsViewModel,
    private val lookAndFeelViewModel: () -> LookAndFeelViewModel,
    private val supportViewModel: () -> SupportViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<MoreRoute> {
            val viewModel: MoreViewModel = viewModel { moreViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                router.navigate(
                    when (event) {
                        MoreEvent.OpenModes -> ModesRoute()
                        MoreEvent.OpenTargets -> TargetsRoute()
                        MoreEvent.OpenWater -> WaterSettingsRoute(fromMore = true)
                        MoreEvent.OpenLookAndFeel -> LookAndFeelRoute()
                        MoreEvent.OpenSupport -> SupportRoute()
                    },
                )
            }
            MoreScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<ModesRoute> {
            val viewModel: ModesViewModel = viewModel { modesViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is ModesEvent.ShowToast -> show(event.toast)
                    is ModesEvent.OpenPause -> router.navigate(event.change.toRoute())
                    ModesEvent.OpenTargets -> router.navigate(TargetsRoute())
                    ModesEvent.Close -> router.goBack()
                }
            }
            ModesScreen(state = state, onAction = viewModel::takeAction)
        }

        bottomSheet<PauseBeforeMoreRoute> { backStackEntry, sheetState ->
            val change = remember(backStackEntry) { backStackEntry.toRoute<PauseBeforeMoreRoute>().change() }
            if (change == null) {
                // Nothing to hold: a route no NumbersChange built. Leave rather than guess.
                LaunchedEffect(Unit) { router.goBack() }
                return@bottomSheet
            }
            val viewModel: PauseViewModel = viewModel { pauseViewModel(change) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is PauseEvent.ShowToast -> show(event.toast)
                    // Animate the sheet down first; its dismiss callback pops the route.
                    PauseEvent.Dismiss -> sheetState.dismiss()
                }
            }
            BottomSheet(
                onDismissRequest = {
                    viewModel.takeAction(PauseAction.SheetDismissed)
                    router.goBack()
                },
                state = sheetState,
                backgroundColor = AppTheme.colors.bg,
            ) {
                PauseSheetContent(state = state, onAction = viewModel::takeAction)
            }
        }

        screen<TargetsRoute> {
            val viewModel: TargetsViewModel = viewModel { targetsViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is TargetsEvent.ShowToast -> show(event.toast)
                    TargetsEvent.OpenModes -> router.navigate(ModesRoute())
                    TargetsEvent.Close -> router.goBack()
                }
            }
            TargetsScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<WaterSettingsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<WaterSettingsRoute>()
            val viewModel: WaterSettingsViewModel = viewModel { waterSettingsViewModel(route.fromMore) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is WaterSettingsEvent.ShowToast -> show(event.toast)
                    is WaterSettingsEvent.OpenPause -> router.navigate(event.change.toRoute())
                    WaterSettingsEvent.Close -> router.goBack()
                }
            }
            WaterSettingsScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<LookAndFeelRoute> {
            val viewModel: LookAndFeelViewModel = viewModel { lookAndFeelViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    LookAndFeelEvent.Close -> router.goBack()
                }
            }
            LookAndFeelScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<SupportRoute> {
            val viewModel: SupportViewModel = viewModel { supportViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is SupportEvent.OpenLink -> router.openWebLink(event.url)
                    is SupportEvent.ShareExport -> router.shareFile(event.name, EXPORT_MIME_TYPE, event.bytes)
                    is SupportEvent.ShowToast -> show(event.toast)
                    SupportEvent.OpenFeedback -> router.navigate(FeedbackRoute())
                    SupportEvent.GoToOnboarding -> router.navigate(
                        OnboardingRoute(),
                        NavigationOptions(clearBackStack = true),
                    )
                    SupportEvent.Close -> router.goBack()
                }
            }
            SupportScreen(state = state, onAction = viewModel::takeAction)
        }
    }

    private suspend fun show(toast: Toast) {
        showSnackBar(message = getString(toast.message, *toast.args.toTypedArray()))
    }
}
