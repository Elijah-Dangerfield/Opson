package com.opson.features.settings.impl

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.home.FeedbackRoute
import com.opson.features.onboarding.OnboardingRoute
import com.opson.features.settings.EnergyTodayRoute
import com.opson.features.settings.FastingRoute
import com.opson.features.settings.CreditsRoute
import com.opson.features.settings.FindSupportRoute
import com.opson.features.settings.IntegrationsRoute
import com.opson.features.settings.LeaveOutDayRoute
import com.opson.features.settings.LookAndFeelRoute
import com.opson.features.settings.DisplayRoute
import com.opson.features.settings.EachNumberRoute
import com.opson.features.settings.ModesRoute
import com.opson.features.settings.ProviderSummaryRoute
import com.opson.features.settings.MoreRoute
import com.opson.features.settings.PauseBeforeMoreRoute
import com.opson.features.settings.RemindersRoute
import com.opson.features.settings.MicroTargetRoute
import com.opson.features.settings.MicroTargetsRoute
import com.opson.features.settings.RestingEnergyRoute
import com.opson.features.settings.SupportRoute
import com.opson.features.settings.TargetsRoute
import com.opson.features.settings.TodayScreenChoiceRoute
import com.opson.features.settings.TodayScreenSettingsRoute
import com.opson.features.settings.WaterSettingsRoute
import com.opson.features.settings.WidgetsRoute
import com.opson.features.settings.impl.energy.EnergyTodayEvent
import com.opson.features.settings.impl.energy.EnergyTodayScreen
import com.opson.features.settings.impl.energy.EnergyTodayViewModel
import com.opson.features.settings.impl.energy.RestingEnergyEvent
import com.opson.features.settings.impl.energy.RestingEnergyScreen
import com.opson.features.settings.impl.energy.RestingEnergyViewModel
import com.opson.features.settings.impl.fasting.FastingEvent
import com.opson.features.settings.impl.fasting.FastingScreen
import com.opson.features.settings.impl.fasting.FastingViewModel
import com.opson.features.settings.impl.integrations.IntegrationsAction
import com.opson.features.settings.impl.integrations.IntegrationsEvent
import com.opson.features.settings.impl.integrations.IntegrationsScreen
import com.opson.features.settings.impl.integrations.IntegrationsViewModel
import com.opson.features.settings.impl.integrations.LeaveOutEvent
import com.opson.features.settings.impl.integrations.LeaveOutSheetContent
import com.opson.features.settings.impl.integrations.LeaveOutViewModel
import com.opson.features.settings.impl.look.LookAndFeelViewModel
import com.opson.features.settings.impl.modes.EachNumberScreen
import com.opson.features.settings.impl.modes.ModesEvent
import com.opson.features.settings.impl.display.DisplayScreen
import com.opson.features.settings.impl.modes.ModesViewModel
import com.opson.features.settings.impl.more.MoreAction
import com.opson.features.settings.impl.more.MoreEvent
import com.opson.features.settings.impl.more.MoreScreen
import com.opson.features.settings.impl.more.MoreVersion
import com.opson.features.settings.impl.micros.MicroTargetEvent
import com.opson.features.settings.impl.micros.MicroTargetSheetContent
import com.opson.features.settings.impl.micros.MicroTargetViewModel
import com.opson.features.settings.impl.micros.MicroTargetsEvent
import com.opson.features.settings.impl.micros.MicroTargetsScreen
import com.opson.features.settings.impl.micros.MicroTargetsViewModel
import com.opson.features.settings.impl.more.MoreViewModel
import com.opson.features.settings.impl.support.PRIVACY_URL
import com.opson.features.settings.impl.support.TERMS_URL
import com.opson.libraries.core.BuildInfo
import com.opson.features.settings.impl.pause.PauseAction
import com.opson.features.settings.impl.pause.PauseEvent
import com.opson.features.settings.impl.pause.PauseSheetContent
import com.opson.features.settings.impl.pause.PauseViewModel
import com.opson.features.settings.impl.reminders.RemindersAction
import com.opson.features.settings.impl.reminders.RemindersEvent
import com.opson.features.settings.impl.reminders.RemindersScreen
import com.opson.features.settings.impl.reminders.RemindersViewModel
import com.opson.features.settings.impl.summary.ProviderSummaryEvent
import com.opson.features.settings.impl.summary.ProviderSummaryScreen
import com.opson.features.settings.impl.summary.ProviderSummaryViewModel
import com.opson.features.settings.impl.summary.SUMMARY_MIME_TYPE
import com.opson.features.settings.impl.support.CreditsEvent
import com.opson.features.settings.impl.support.CreditsScreen
import com.opson.features.settings.impl.support.CreditsViewModel
import com.opson.features.settings.impl.support.FindSupportEvent
import com.opson.features.settings.impl.support.FindSupportScreen
import com.opson.features.settings.impl.support.FindSupportViewModel
import com.opson.features.settings.impl.support.SupportEvent
import com.opson.features.settings.impl.support.SupportScreen
import com.opson.features.settings.impl.support.SupportViewModel
import com.opson.features.settings.impl.targets.TargetsEvent
import com.opson.features.settings.impl.targets.TargetsScreen
import com.opson.features.settings.impl.targets.TargetsViewModel
import com.opson.features.settings.impl.todayscreen.TodayScreenChoice
import com.opson.features.settings.impl.todayscreen.TodayScreenChoiceEvent
import com.opson.features.settings.impl.todayscreen.TodayScreenChoiceSheetContent
import com.opson.features.settings.impl.todayscreen.TodayScreenChoiceViewModel
import com.opson.features.settings.impl.todayscreen.TodayScreenSettingsEvent
import com.opson.features.settings.impl.todayscreen.TodayScreenSettingsScreen
import com.opson.features.settings.impl.todayscreen.TodayScreenSettingsViewModel
import com.opson.features.settings.impl.water.WaterSettingsEvent
import com.opson.features.settings.impl.water.WaterSettingsScreen
import com.opson.features.settings.impl.water.WaterSettingsViewModel
import com.opson.features.settings.impl.widgets.WidgetsEvent
import com.opson.features.settings.impl.widgets.WidgetsScreen
import com.opson.features.settings.impl.widgets.WidgetsViewModel
import com.opson.features.settings.toRoute
import com.opson.features.trends.NutrientsRange
import com.opson.features.trends.NutrientsRoute
import com.opson.libraries.diary.DataExport
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.NavigationOptions
import com.opson.libraries.navigation.OnTabReselected
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.bottomSheet
import com.opson.libraries.navigation.screen
import com.opson.libraries.nutrition.Micronutrient
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
    private val restingEnergyViewModel: () -> RestingEnergyViewModel,
    private val energyTodayViewModel: () -> EnergyTodayViewModel,
    private val todayScreenSettingsViewModel: () -> TodayScreenSettingsViewModel,
    private val todayScreenChoiceViewModel: (TodayScreenChoice) -> TodayScreenChoiceViewModel,
    private val waterSettingsViewModel: (backToMore: Boolean) -> WaterSettingsViewModel,
    private val lookAndFeelViewModel: () -> LookAndFeelViewModel,
    private val supportViewModel: () -> SupportViewModel,
    private val fastingViewModel: () -> FastingViewModel,
    private val remindersViewModel: () -> RemindersViewModel,
    private val integrationsViewModel: () -> IntegrationsViewModel,
    private val leaveOutViewModel: () -> LeaveOutViewModel,
    private val widgetsViewModel: () -> WidgetsViewModel,
    private val findSupportViewModel: () -> FindSupportViewModel,
    private val creditsViewModel: () -> CreditsViewModel,
    private val microTargetsViewModel: (backToTodayScreen: Boolean) -> MicroTargetsViewModel,
    private val microTargetViewModel: (Micronutrient) -> MicroTargetViewModel,
    private val providerSummaryViewModel: () -> ProviderSummaryViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<MoreRoute> {
            val viewModel: MoreViewModel = viewModel { moreViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    MoreEvent.OpenDisplay -> router.navigate(DisplayRoute())
                    MoreEvent.OpenTargets -> router.navigate(TargetsRoute())
                    MoreEvent.OpenTodayScreen -> router.navigate(TodayScreenSettingsRoute())
                    MoreEvent.OpenWater -> router.navigate(WaterSettingsRoute(fromMore = true))
                    MoreEvent.OpenSupport -> router.navigate(SupportRoute())
                    MoreEvent.OpenFasting -> router.navigate(FastingRoute())
                    MoreEvent.OpenReminders -> router.navigate(RemindersRoute())
                    MoreEvent.OpenHealth -> router.navigate(IntegrationsRoute())
                    MoreEvent.OpenWidgets -> router.navigate(WidgetsRoute())
                    MoreEvent.OpenPrivacy -> router.openWebLink(PRIVACY_URL)
                    MoreEvent.OpenTerms -> router.openWebLink(TERMS_URL)
                    MoreEvent.OpenCredits -> router.navigate(CreditsRoute())
                    is MoreEvent.OpenContributed -> router.navigate(event.route)
                    // The same exit as deleting the account: whatever the signed-out entry is,
                    // onboarding owns it, so More only hands over with nothing behind it.
                    MoreEvent.GoToOnboarding -> router.navigate(OnboardingRoute(), NavigationOptions(clearBackStack = true))
                }
            }
            LifecycleResumeEffect(viewModel) {
                viewModel.takeAction(MoreAction.ScreenResumed)
                onPauseOrDispose {}
            }
            val scrollState = rememberScrollState()
            router.OnTabReselected(MoreRoute::class) { scrollState.animateScrollTo(0) }
            MoreScreen(
                state = state,
                onAction = viewModel::takeAction,
                version = remember { MoreVersion.line(BuildInfo.versionName, BuildInfo.buildNumber, BuildInfo.releaseChannel, BuildInfo.commitSha) },
                scrollState = scrollState,
            )
        }

        // One page, three names: ModesRoute and LookAndFeelRoute were pages of their own, and
        // other features (and saved back stacks) still name them.
        screen<DisplayRoute> { DisplayPage(router) }
        screen<ModesRoute> { DisplayPage(router) }
        screen<LookAndFeelRoute> { DisplayPage(router) }
        screen<EachNumberRoute> {
            val viewModel: ModesViewModel = viewModel { modesViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is ModesEvent.ShowToast -> show(event.toast)
                    is ModesEvent.OpenPause -> router.navigate(event.change.toRoute())
                    ModesEvent.OpenEachNumber -> Unit
                    ModesEvent.Close -> router.goBack()
                }
            }
            EachNumberScreen(state = state, onAction = viewModel::takeAction)
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
                    PauseEvent.OpenFindSupport -> router.navigate(FindSupportRoute())
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
                    TargetsEvent.OpenRestingEnergy -> router.navigate(RestingEnergyRoute())
                    TargetsEvent.OpenModes -> router.navigate(ModesRoute())
                    TargetsEvent.OpenIntegrations -> router.navigate(IntegrationsRoute())
                    TargetsEvent.OpenMicroTargets -> router.navigate(MicroTargetsRoute())
                    TargetsEvent.Close -> router.goBack()
                }
            }
            TargetsScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<MicroTargetsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<MicroTargetsRoute>()
            val viewModel: MicroTargetsViewModel = viewModel { microTargetsViewModel(route.fromTodayScreen) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is MicroTargetsEvent.OpenNutrient -> router.navigate(MicroTargetRoute(event.micro.key))
                    MicroTargetsEvent.OpenRestingEnergy -> router.navigate(RestingEnergyRoute())
                    MicroTargetsEvent.OpenModes -> router.navigate(ModesRoute())
                    MicroTargetsEvent.Close -> router.goBack()
                }
            }
            MicroTargetsScreen(state = state, onAction = viewModel::takeAction)
        }

        bottomSheet<MicroTargetRoute> { backStackEntry, sheetState ->
            val micro = Micronutrient.fromKey(backStackEntry.toRoute<MicroTargetRoute>().micro)
            if (micro == null) {
                // A key this build does not track has nothing to set.
                LaunchedEffect(Unit) { router.goBack() }
                return@bottomSheet
            }
            val viewModel: MicroTargetViewModel = viewModel { microTargetViewModel(micro) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    MicroTargetEvent.Dismiss -> sheetState.dismiss()
                }
            }
            BottomSheet(onDismissRequest = { router.goBack() }, state = sheetState, backgroundColor = AppTheme.colors.bg, scrollableContent = true) {
                MicroTargetSheetContent(state = state, onAction = viewModel::takeAction)
            }
        }

        // Registered whatever release.goalsAndEnergy says: nothing links here while it is off,
        // and a stale back stack entry should land on the page rather than crash.
        screen<RestingEnergyRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<RestingEnergyRoute>()
            val viewModel: RestingEnergyViewModel = viewModel { restingEnergyViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    RestingEnergyEvent.OpenIntegrations -> router.navigate(IntegrationsRoute())
                    RestingEnergyEvent.Close -> router.goBack()
                }
            }
            RestingEnergyScreen(state = state, onAction = viewModel::takeAction, fromEnergyToday = route.fromEnergyToday)
        }

        // Registered whatever release.goalsAndEnergy says, as the pages above.
        screen<TodayScreenSettingsRoute> {
            val viewModel: TodayScreenSettingsViewModel = viewModel { todayScreenSettingsViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is TodayScreenSettingsEvent.OpenChoice -> router.navigate(TodayScreenChoiceRoute(event.choice.wire))
                    TodayScreenSettingsEvent.OpenMicroTargets -> router.navigate(MicroTargetsRoute(fromTodayScreen = true))
                    TodayScreenSettingsEvent.Close -> router.goBack()
                }
            }
            TodayScreenSettingsScreen(state = state, onAction = viewModel::takeAction)
        }

        bottomSheet<TodayScreenChoiceRoute> { backStackEntry, sheetState ->
            val choice = TodayScreenChoice.fromWire(backStackEntry.toRoute<TodayScreenChoiceRoute>().choice)
            val viewModel: TodayScreenChoiceViewModel = viewModel { todayScreenChoiceViewModel(choice) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    // Animate the sheet down first; its dismiss callback pops the route.
                    TodayScreenChoiceEvent.Dismiss -> sheetState.dismiss()
                }
            }
            BottomSheet(onDismissRequest = { router.goBack() }, state = sheetState, backgroundColor = AppTheme.colors.bg) {
                TodayScreenChoiceSheetContent(state = state, onAction = viewModel::takeAction)
            }
        }

        screen<EnergyTodayRoute> {
            val viewModel: EnergyTodayViewModel = viewModel { energyTodayViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is EnergyTodayEvent.OpenNutrients -> router.navigate(NutrientsRoute(event.day.toString(), NutrientsRange.Day))
                    EnergyTodayEvent.OpenRestingEnergy -> router.navigate(RestingEnergyRoute(fromEnergyToday = true))
                    EnergyTodayEvent.Close -> router.goBack()
                }
            }
            EnergyTodayScreen(state = state, onAction = viewModel::takeAction)
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

        screen<FastingRoute> {
            val viewModel: FastingViewModel = viewModel { fastingViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is FastingEvent.ShowToast -> show(event.toast)
                    FastingEvent.Close -> router.goBack()
                }
            }
            FastingScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<RemindersRoute> {
            val viewModel: RemindersViewModel = viewModel { remindersViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            // Back from the system's notification settings, permission may have changed.
            LifecycleResumeEffect(viewModel) {
                viewModel.takeAction(RemindersAction.ScreenResumed)
                onPauseOrDispose { }
            }
            viewModel.ObserveEvents { event ->
                when (event) {
                    RemindersEvent.Close -> router.goBack()
                }
            }
            RemindersScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<SupportRoute> {
            val viewModel: SupportViewModel = viewModel { supportViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is SupportEvent.OpenLink -> router.openWebLink(event.url)
                    is SupportEvent.ShareExport -> router.shareFile(event.name, DataExport.MIME_TYPE, event.bytes)
                    is SupportEvent.ShowToast -> show(event.toast)
                    SupportEvent.OpenFeedback -> router.navigate(FeedbackRoute())
                    SupportEvent.OpenFindSupport -> router.navigate(FindSupportRoute())
                    SupportEvent.OpenProviderSummary -> router.navigate(ProviderSummaryRoute())
                    SupportEvent.OpenCredits -> router.navigate(CreditsRoute())
                    SupportEvent.GoToOnboarding -> router.navigate(
                        OnboardingRoute(),
                        NavigationOptions(clearBackStack = true),
                    )
                    SupportEvent.Close -> router.goBack()
                }
            }
            SupportScreen(state = state, onAction = viewModel::takeAction)
        }

        // Registered whatever release.health says: nothing links here while it is off, and a
        // stale back stack entry should land on the page rather than crash.
        screen<IntegrationsRoute> {
            val viewModel: IntegrationsViewModel = viewModel { integrationsViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is IntegrationsEvent.OpenLink -> router.openWebLink(event.url)
                    is IntegrationsEvent.ShowToast -> show(event.toast)
                    IntegrationsEvent.OpenLeaveOut -> router.navigate(LeaveOutDayRoute())
                    IntegrationsEvent.Close -> router.goBack()
                }
            }
            // Back from Health Connect's own screen: the access may have changed there.
            LifecycleResumeEffect(viewModel) {
                viewModel.takeAction(IntegrationsAction.ScreenResumed)
                onPauseOrDispose {}
            }
            IntegrationsScreen(state = state, onAction = viewModel::takeAction)
        }

        // Registered with IntegrationsRoute, for the same reason: only that page links here.
        bottomSheet<LeaveOutDayRoute> { _, sheetState ->
            val viewModel: LeaveOutViewModel = viewModel { leaveOutViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    // Animate the sheet down first; its dismiss callback pops the route.
                    LeaveOutEvent.Dismiss -> sheetState.dismiss()
                }
            }
            BottomSheet(
                onDismissRequest = { router.goBack() },
                state = sheetState,
                backgroundColor = AppTheme.colors.bg,
            ) {
                LeaveOutSheetContent(state = state, onAction = viewModel::takeAction)
            }
        }

        // Registered whatever release.widgets says, like Integrations: only the More row links
        // here, and that row is behind the flag.
        screen<WidgetsRoute> {
            val viewModel: WidgetsViewModel = viewModel { widgetsViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    WidgetsEvent.Close -> router.goBack()
                }
            }
            WidgetsScreen(state = state, onAction = viewModel::takeAction)
        }

        // Registered whatever release.findSupport says: only flagged entry points link here.
        // Nothing on this page logs, and App.kt keeps the route out of crash and jank attribution.
        screen<FindSupportRoute> {
            val viewModel: FindSupportViewModel = viewModel { findSupportViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    FindSupportEvent.Close -> router.goBack()
                }
            }
            FindSupportScreen(state = state, onAction = viewModel::takeAction)
        }

        // Registered whatever release.providerSummary says: only Support links here, and only with it on.
        screen<ProviderSummaryRoute> {
            val viewModel: ProviderSummaryViewModel = viewModel { providerSummaryViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is ProviderSummaryEvent.SharePdf -> router.shareFile(event.name, SUMMARY_MIME_TYPE, event.bytes)
                    ProviderSummaryEvent.Close -> router.goBack()
                }
            }
            ProviderSummaryScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<CreditsRoute> {
            val viewModel: CreditsViewModel = viewModel { creditsViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is CreditsEvent.OpenLink -> router.openWebLink(event.url)
                    CreditsEvent.Close -> router.goBack()
                }
            }
            CreditsScreen(state = state, onAction = viewModel::takeAction)
        }
    }

    /** More › Display: the Numbers half's view model owns the events, back included. */
    @Composable
    private fun DisplayPage(router: Router) {
        val modes: ModesViewModel = viewModel { modesViewModel() }
        val look: LookAndFeelViewModel = viewModel { lookAndFeelViewModel() }
        val modesState by modes.stateFlow.collectAsStateWithLifecycle()
        val lookState by look.stateFlow.collectAsStateWithLifecycle()
        modes.ObserveEvents { event ->
            when (event) {
                is ModesEvent.ShowToast -> show(event.toast)
                is ModesEvent.OpenPause -> router.navigate(event.change.toRoute())
                ModesEvent.OpenEachNumber -> router.navigate(EachNumberRoute())
                ModesEvent.Close -> router.goBack()
            }
        }
        DisplayScreen(modes = modesState, look = lookState, onModesAction = modes::takeAction, onLookAction = look::takeAction)
    }

    private suspend fun show(toast: Toast) {
        showSnackBar(message = getString(toast.message, *toast.args.toTypedArray()))
    }
}
