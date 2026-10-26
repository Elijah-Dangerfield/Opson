package com.opson.features.settings.impl

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import com.opson.features.settings.MoreRoute
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
class SettingsFeatureEntryPoint(
    private val moreViewModelFactory: () -> MoreViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<MoreRoute> {
            val viewModel: MoreViewModel = viewModel { moreViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            MoreScreen(state = state, onAction = viewModel::takeAction)
        }
    }
}
