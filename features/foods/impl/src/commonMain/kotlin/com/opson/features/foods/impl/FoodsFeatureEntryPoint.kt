package com.opson.features.foods.impl

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import com.opson.features.foods.FoodsRoute
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
class FoodsFeatureEntryPoint(
    private val foodsViewModelFactory: () -> FoodsViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<FoodsRoute> {
            val viewModel: FoodsViewModel = viewModel { foodsViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            FoodsScreen(state = state, onAction = viewModel::takeAction)
        }
    }
}
