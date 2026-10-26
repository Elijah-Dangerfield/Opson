package com.opson.features.logger.impl

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.logger.LoggerRoute
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.bottomSheet
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetDefaults
import com.opson.system.AppTheme
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class LoggerFeatureEntryPoint(
    private val loggerViewModelFactory: (slot: String?) -> LoggerViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        bottomSheet<LoggerRoute> { backStackEntry, sheetState ->
            val route = backStackEntry.toRoute<LoggerRoute>()
            val viewModel: LoggerViewModel = viewModel { loggerViewModelFactory(route.slot) }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()

            viewModel.ObserveEvents { event ->
                when (event) {
                    // Animate the sheet down first; its dismiss callback pops the route.
                    LoggerEvent.Close -> sheetState.dismiss()
                }
            }

            BottomSheet(
                onDismissRequest = { router.goBack() },
                state = sheetState,
                heightFraction = BottomSheetDefaults.FullHeightFraction,
                backgroundColor = AppTheme.colors.bg,
            ) {
                LoggerScreen(state = state, onAction = viewModel::takeAction)
            }
        }
    }
}
