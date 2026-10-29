@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.qa.impl

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import com.opson.features.qa.QaMenuRoute
import com.opson.features.qa.QaPromptsRoute
import com.opson.features.settings.MoreRowContribution
import com.opson.features.today.CheckInRoute
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Route
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.screen
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.snackbar.showSnackBar
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.qa_more_row
import opson.libraries.resources.generated.resources.qa_more_row_sub
import opson.libraries.resources.generated.resources.qa_more_section
import org.jetbrains.compose.resources.getString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

// Hidden from the iOS header like MoreRowContribution, which says why. Swift never uses either.
@HiddenFromObjC
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class QaFeatureEntryPoint(
    private val qaViewModel: (QaStateBuilder.BuildFacts) -> QaViewModel,
    private val qaPromptsViewModel: () -> QaPromptsViewModel,
    private val qaAccess: QaAccess,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        // Not registered at all where the menu may not show (AGENTS.md, Known landmines: a
        // debug-only page reached by deep link needs its registration gated, not just its row).
        if (!qaAccess.allowed()) return
        screen<QaMenuRoute> {
            val viewModel: QaViewModel = viewModel { qaViewModel(QaBuild.current()) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is QaEvent.ShowToast -> showSnackBar(message = getString(event.toast.message, *event.toast.args.toTypedArray()))
                    is QaEvent.OpenCheckIn -> router.navigate(CheckInRoute(event.dayIso))
                    QaEvent.OpenPrompts -> router.navigate(QaPromptsRoute())
                    QaEvent.Close -> router.goBack()
                }
            }
            QaScreen(state = state, onAction = viewModel::takeAction)
        }
        screen<QaPromptsRoute> {
            val viewModel: QaPromptsViewModel = viewModel { qaPromptsViewModel() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            viewModel.ObserveEvents { event ->
                when (event) {
                    is QaPromptsEvent.ShowToast -> showSnackBar(message = getString(event.toast.message, *event.toast.args.toTypedArray()))
                    QaPromptsEvent.Close -> router.goBack()
                }
            }
            QaPromptsScreen(state = state, onAction = viewModel::takeAction)
        }
    }
}

/** More › Testing › QA menu, shown where [QaAccess] allows it. */
@HiddenFromObjC
@Inject
@ContributesBinding(AppScope::class, multibinding = true)
class QaMoreRow(private val qaAccess: QaAccess) : MoreRowContribution {
    override val section = Res.string.qa_more_section
    override val title = Res.string.qa_more_row
    override val subtitle = Res.string.qa_more_row_sub
    override val icon = AppIcons.Science

    override fun route(): Route = QaMenuRoute()

    override fun visible(): Boolean = qaAccess.allowed()
}
