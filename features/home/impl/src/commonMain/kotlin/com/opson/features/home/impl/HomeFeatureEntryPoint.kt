package com.opson.features.home.impl

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.home.FeedbackRoute
import com.opson.features.home.impl.bugreport.BugReportEvent
import com.opson.features.home.impl.bugreport.BugReportScreen
import com.opson.features.home.impl.bugreport.BugReportViewModel
import com.opson.features.home.impl.feedback.FeedbackEvent
import com.opson.features.home.impl.feedback.FeedbackScreen
import com.opson.features.home.impl.feedback.FeedbackViewModel
import com.opson.features.profile.BugReportRoute
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.screen
import com.opson.libraries.ui.snackbar.showSnackBar
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.bug_report_thanks
import opson.libraries.resources.generated.resources.feedback_thanks
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Duration.Companion.seconds

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class HomeFeatureEntryPoint(
    private val feedbackViewModelFactory: () -> FeedbackViewModel,
    private val bugReportViewModelFactory: (logId: String?, errorCode: Int?, contextMessage: String?) -> BugReportViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<FeedbackRoute> {
            val viewModel: FeedbackViewModel = viewModel { feedbackViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value
            viewModel.ObserveEvents { event ->
                when (event) {
                    FeedbackEvent.Sent -> thankAndClose(router, Res.string.feedback_thanks)
                    FeedbackEvent.Close -> router.goBack()
                }
            }
            FeedbackScreen(
                state = state,
                onAction = viewModel::takeAction,
            )
        }

        screen<BugReportRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<BugReportRoute>()
            val viewModel: BugReportViewModel = viewModel {
                bugReportViewModelFactory(route.logId, route.errorCode, route.contextMessage)
            }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value
            viewModel.ObserveEvents { event ->
                when (event) {
                    BugReportEvent.Sent -> thankAndClose(router, Res.string.bug_report_thanks)
                    BugReportEvent.Close -> router.goBack()
                }
            }
            BugReportScreen(
                state = state,
                onAction = viewModel::takeAction,
            )
        }
    }

    private suspend fun thankAndClose(router: Router, thanks: StringResource) {
        showSnackBar(message = getString(thanks), delayBy = 1.seconds)
        router.goBack()
    }
}
