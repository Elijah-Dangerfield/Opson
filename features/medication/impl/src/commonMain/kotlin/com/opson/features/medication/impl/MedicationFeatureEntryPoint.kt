@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.medication.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.medication.LogDoseRoute
import com.opson.features.medication.MedicationRoute
import com.opson.features.medication.impl.dose.LogDoseEvent
import com.opson.features.medication.impl.dose.LogDoseSheetContent
import com.opson.features.medication.impl.dose.LogDoseViewModel
import com.opson.features.medication.impl.today.MedicationCardAction
import com.opson.features.medication.impl.today.MedicationCardEvent
import com.opson.features.medication.impl.today.MedicationCardViewModel
import com.opson.features.medication.impl.today.MedicationTodayCard
import com.opson.features.settings.MoreRowContribution
import com.opson.features.today.TodayCardContribution
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.medication.MedicationEnabled
import com.opson.libraries.medication.MedicationRepository
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Route
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.bottomSheet
import com.opson.libraries.navigation.screen
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.snackbar.showSnackBar
import com.opson.system.AppTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.medication_dose_deleted
import opson.libraries.resources.generated.resources.medication_more_row
import opson.libraries.resources.generated.resources.medication_more_row_sub
import opson.libraries.resources.generated.resources.medication_more_section
import opson.libraries.resources.generated.resources.medication_turn_off_body
import opson.libraries.resources.generated.resources.medication_undo
import org.jetbrains.compose.resources.getString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * More › Medication and the dose sheet. Registered whatever `release.medication` says, as other
 * flagged pages are: only the More row and the Today card link here, and both check the flag.
 * View models send events; only this class navigates.
 */
@HiddenFromObjC
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class MedicationFeatureEntryPoint(
    private val medicationViewModel: () -> MedicationViewModel,
    private val logDoseViewModel: (LogDoseRoute) -> LogDoseViewModel,
    // Providers, so the snackbar's Undo runs after the sheet that offered it has gone.
    private val appScope: () -> AppCoroutineScope,
    private val repository: () -> MedicationRepository,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<MedicationRoute> {
            val viewModel: MedicationViewModel = viewModel { medicationViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            // Back from the system's notification settings: say whether the reminder can fire now.
            LifecycleResumeEffect(viewModel) {
                viewModel.takeAction(MedicationAction.ScreenResumed)
                onPauseOrDispose { }
            }
            viewModel.ObserveEvents { event ->
                when (event) {
                    MedicationEvent.Close -> router.goBack()
                    is MedicationEvent.OpenDose -> router.navigate(LogDoseRoute(doseId = event.id))
                    MedicationEvent.Stopped -> showSnackBar(message = getString(Res.string.medication_turn_off_body))
                }
            }
            MedicationScreen(state = state, onAction = viewModel::takeAction)
        }

        bottomSheet<LogDoseRoute> { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<LogDoseRoute>()
            val viewModel: LogDoseViewModel = viewModel { logDoseViewModel(route) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    LogDoseEvent.Dismiss -> sheetState.dismiss()
                    is LogDoseEvent.Deleted -> {
                        sheetState.dismiss()
                        showSnackBar(
                            message = getString(Res.string.medication_dose_deleted),
                            actionLabel = getString(Res.string.medication_undo),
                            onAction = { appScope().launch { repository().restoreDose(event.id) } },
                        )
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
                LogDoseSheetContent(state = state, onAction = viewModel::takeAction)
            }
        }
    }
}

/** More › Health › Medication, while `release.medication` is on. Never a dose in its subtitle. */
@HiddenFromObjC
@Inject
@ContributesBinding(AppScope::class, multibinding = true)
class MedicationMoreRow(private val enabled: MedicationEnabled) : MoreRowContribution {
    override val section = Res.string.medication_more_section
    override val title = Res.string.medication_more_row
    override val subtitle = Res.string.medication_more_row_sub
    override val icon = AppIcons.Medication

    override fun route(): Route = MedicationRoute()

    override fun visible(): Boolean = enabled.value
}

/** Today's Medication card. It draws only on today, and only while tracking and "Show on Today" are on. */
@HiddenFromObjC
@Inject
@ContributesBinding(AppScope::class, multibinding = true)
class MedicationTodayCardContribution(
    private val cardViewModel: () -> MedicationCardViewModel,
) : TodayCardContribution {
    override val key = "medication"

    @Composable
    override fun card(day: LocalDate, router: Router): (@Composable () -> Unit)? {
        val viewModel: MedicationCardViewModel = viewModel(key = "today-medication-card") { cardViewModel() }
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        LaunchedEffect(day) { viewModel.takeAction(MedicationCardAction.DayShown(day)) }
        viewModel.ObserveEvents { event ->
            when (event) {
                is MedicationCardEvent.OpenSheet -> router.navigate(LogDoseRoute(doseId = event.doseId, via = LogDoseRoute.VIA_TODAY_CARD))
                MedicationCardEvent.OpenMedication -> router.navigate(MedicationRoute())
            }
        }
        val card = state.card ?: return null
        return remember(card) { { MedicationTodayCard(card, viewModel::takeAction) } }
    }
}
