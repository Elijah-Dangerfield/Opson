package com.opson.features.home.impl

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.home.FeedbackRoute
import com.opson.features.home.impl.bugreport.BugReportAction
import com.opson.features.home.impl.bugreport.BugReportEvent
import com.opson.features.home.impl.bugreport.BugReportScreen
import com.opson.features.home.impl.bugreport.BugReportViewModel
import com.opson.features.home.impl.feedback.FeedbackAction
import com.opson.features.home.impl.feedback.FeedbackEvent
import com.opson.features.home.impl.report.AttachmentAction
import com.opson.libraries.scanner.MealPhotoPicker
import com.opson.libraries.scanner.PhotoPickResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.opson.features.home.impl.feedback.FeedbackScreen
import com.opson.features.home.impl.feedback.FeedbackViewModel
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.BugReportRoute
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.screen
import com.opson.libraries.ui.snackbar.showSnackBar
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.bug_report_thanks
import opson.libraries.resources.generated.resources.feedback_queued
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
    private val bugReportViewModelFactory: (
        logId: String?,
        errorCode: Int?,
        contextMessage: String?,
        shakeScreenshot: Boolean,
    ) -> BugReportViewModel,
    private val photoPicker: () -> MealPhotoPicker,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<FeedbackRoute> {
            val viewModel: FeedbackViewModel = viewModel { feedbackViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value
            val pickScreenshot = rememberScreenshotPicker { viewModel.takeAction(FeedbackAction.Attachment(it)) }
            viewModel.ObserveEvents { event ->
                when (event) {
                    is FeedbackEvent.Sent -> thankAndClose(router, if (event.queued) Res.string.feedback_queued else Res.string.feedback_thanks)
                    FeedbackEvent.Close -> router.goBack()
                    FeedbackEvent.PickScreenshot -> pickScreenshot()
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
                bugReportViewModelFactory(route.logId, route.errorCode, route.contextMessage, route.shakeScreenshot)
            }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value
            val pickScreenshot = rememberScreenshotPicker { viewModel.takeAction(BugReportAction.Attachment(it)) }
            viewModel.ObserveEvents { event ->
                when (event) {
                    is BugReportEvent.Sent -> thankAndClose(router, if (event.queued) Res.string.feedback_queued else Res.string.bug_report_thanks)
                    BugReportEvent.Close -> router.goBack()
                    BugReportEvent.PickScreenshot -> pickScreenshot()
                }
            }
            BugReportScreen(
                state = state,
                onAction = viewModel::takeAction,
            )
        }
    }

    /** The system photo picker, answering as an [AttachmentAction]. Cancelling changes nothing. */
    @Composable
    private fun rememberScreenshotPicker(onAnswer: (AttachmentAction) -> Unit): () -> Unit {
        val picker = remember { photoPicker() }
        return picker.rememberLibrary { result ->
            when (result) {
                is PhotoPickResult.Picked -> onAnswer(AttachmentAction.ScreenshotPicked(result.photo.jpeg))
                PhotoPickResult.Failed -> onAnswer(AttachmentAction.ScreenshotPickFailed)
                PhotoPickResult.Cancelled -> Unit
            }
        }
    }

    private suspend fun thankAndClose(router: Router, thanks: StringResource) {
        showSnackBar(message = getString(thanks), delayBy = 1.seconds)
        router.goBack()
    }
}
