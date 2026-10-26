package com.opson.features.trends.impl

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import com.opson.features.trends.TrendsRoute
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.screen
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class TrendsFeatureEntryPoint(
    private val trendsViewModelFactory: () -> TrendsViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<TrendsRoute> {
            val viewModel: TrendsViewModel = viewModel { trendsViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            TrendsScreen(state = state, onAction = viewModel::takeAction)
        }
    }
}
