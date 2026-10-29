@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.builder.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.builder.BuilderProgramRoute
import com.opson.features.builder.BuildersEnabled
import com.opson.features.logger.CheckPackRoute
import com.opson.features.logger.SubmitEntry
import com.opson.features.logger.SubmitFoodRoute
import com.opson.features.settings.MoreRowContribution
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.foods.ApplicationStatus
import com.opson.libraries.foods.BuilderProgram
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Route
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.bottomSheet
import com.opson.libraries.navigation.screen
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetState
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.system.confirm
import com.opson.system.AppTheme
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.builder_more_row
import opson.libraries.resources.generated.resources.builder_more_row_sub
import opson.libraries.resources.generated.resources.builder_more_row_sub_applied
import opson.libraries.resources.generated.resources.builder_more_row_sub_member
import opson.libraries.resources.generated.resources.builder_more_section
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * More › Builders, with the application and the rules as sheets over it. Registered whatever
 * `release.builders` says, as other flagged pages are: only the More row and the prompts link
 * here, and they check the flag. View models send events; only this class navigates.
 */
@HiddenFromObjC
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class BuilderFeatureEntryPoint(
    private val viewModel: (BuilderProgramRoute) -> BuilderProgramViewModel,
    private val applyViewModel: (BuilderApplyRoute) -> BuilderApplyViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<BuilderProgramRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<BuilderProgramRoute>()
            val model: BuilderProgramViewModel = viewModel { viewModel(route) }
            val state by model.stateFlow.collectAsStateWithLifecycle()
            model.ObserveEvents { event ->
                when (event) {
                    BuilderProgramEvent.Close -> router.goBack()
                    is BuilderProgramEvent.OpenApply -> router.navigate(BuilderApplyRoute(event.via))
                    BuilderProgramEvent.OpenRules -> router.navigate(BuilderRulesRoute())
                    BuilderProgramEvent.AddFood -> router.navigate(SubmitFoodRoute(entry = SubmitEntry.Builders))
                    is BuilderProgramEvent.CheckFood -> router.navigate(CheckPackRoute(event.dayIso))
                }
            }
            BuilderProgramScreen(state = state, onAction = model::takeAction)
        }

        bottomSheet<BuilderApplyRoute> { backStackEntry, sheetState ->
            ApplySheet(backStackEntry.toRoute<BuilderApplyRoute>(), sheetState, router)
        }

        bottomSheet<BuilderRulesRoute> { _, sheetState ->
            BottomSheet(
                onDismissRequest = { router.goBack() },
                state = sheetState,
                showDragHandle = true,
                scrollableContent = true,
                backgroundColor = AppTheme.colors.bg,
            ) {
                BuilderRulesSheetContent(onTerms = { router.openWebLink(TERMS_URL) }, onClose = { sheetState.dismiss() })
            }
        }
    }

    @Composable
    private fun ApplySheet(route: BuilderApplyRoute, sheetState: BottomSheetState, router: Router) {
        val model: BuilderApplyViewModel = viewModel { applyViewModel(route) }
        val state by model.stateFlow.collectAsStateWithLifecycle()
        val haptics = LocalHapticFeedback.current
        model.ObserveEvents { event ->
            when (event) {
                // The page underneath already shows it received.
                BuilderApplyEvent.Sent -> {
                    haptics.confirm()
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
            BuilderApplySheetContent(state = state, onAction = model::takeAction, onClose = { sheetState.dismiss() })
        }
    }

    companion object {
        /** The terms page, which carries the Builder rules in full (`legal/terms.md`). */
        const val TERMS_URL = "https://nightjarlabs.llc/opson/terms"
    }
}

/** More › Opson Builders › Builders, while `release.builders` is on. */
@HiddenFromObjC
@Inject
@ContributesBinding(AppScope::class, multibinding = true)
class BuilderMoreRow(
    private val enabled: BuildersEnabled,
    private val program: BuilderProgram,
) : MoreRowContribution {
    override val section = Res.string.builder_more_section
    override val title = Res.string.builder_more_row
    override val subtitle
        get() = when {
            program.isBuilder -> Res.string.builder_more_row_sub_member
            program.standing.value?.application?.status == ApplicationStatus.Pending -> Res.string.builder_more_row_sub_applied
            else -> Res.string.builder_more_row_sub
        }
    override val icon = AppIcons.Science

    override fun route(): Route = BuilderProgramRoute()

    override fun visible(): Boolean = enabled.value
}
