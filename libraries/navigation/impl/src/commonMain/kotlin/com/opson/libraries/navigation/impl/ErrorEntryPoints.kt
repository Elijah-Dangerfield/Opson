package com.opson.libraries.navigation.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.onboarding.OnboardingRoute
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.AccessDeniedRoute
import com.opson.libraries.navigation.BlockingErrorRoute
import com.opson.libraries.navigation.BugReportRoute
import com.opson.libraries.navigation.ErrorDialogAction
import com.opson.libraries.navigation.ErrorDialogRoute
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.NavigationOptions
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.SessionExpiredRoute
import com.opson.libraries.navigation.dialog
import com.opson.libraries.navigation.screen
import com.opson.libraries.navigation.serializableType
import com.opson.libraries.navigation.toRouteOrNull
import kotlinx.datetime.TimeZone
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.reflect.typeOf

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class ErrorEntryPoints(
    private val sessionExpiredViewModelFactory: () -> SessionExpiredViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<SessionExpiredRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SessionExpiredRoute>()
            val viewModel: SessionExpiredViewModel = viewModel { sessionExpiredViewModelFactory() }
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()

            viewModel.ObserveEvents { event ->
                when (event) {
                    // Tear down happened in the VM: land on onboarding's
                    // sign-in step with a clean stack (Back must not resurrect
                    // the dead session's screens).
                    SessionExpiredViewModel.Event.NavigateToSignIn -> router.navigate(
                        OnboardingRoute(signInFirst = true),
                        NavigationOptions(launchSingleTop = true, clearBackStack = true),
                    )
                }
            }

            SessionExpiredScreen(
                wasAnonymous = route.wasAnonymous,
                working = state.working,
                onSignIn = { viewModel.takeAction(SessionExpiredViewModel.Action.SignInAgain) },
            )
        }

        screen<AccessDeniedRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AccessDeniedRoute>()
            val restoredOn = remember(route.until) {
                route.until?.let { accessRestoredDate(it, TimeZone.currentSystemDefault()) }
            }
            AccessDeniedScreen(
                reason = route.reason,
                restoredOn = restoredOn,
                appealUrl = route.appealUrl,
                onAppeal = { url -> router.openWebLink(url) },
            )
        }

        screen<BlockingErrorRoute>(
            typeMap = mapOf()
        ) { backStackEntry ->
            val route = backStackEntry.toRouteOrNull<BlockingErrorRoute>() ?: DEFAULT_BLOCKING_ERROR
            BlockingErrorScreen(
                title = route.title,
                subtitle = route.subtitle,
                errorCode = route.errorCode ?: DEFAULT_BLOCKING_ERROR.errorCode,
                onReportToDevelopers = {
                    router.navigate(
                        BugReportRoute(
                            logId = route.logId,
                            errorCode = route.errorCode ?: DEFAULT_BLOCKING_ERROR.errorCode,
                            contextMessage = route.contextMessage ?: route.subtitle,
                        )
                    )
                }
            )
        }

        dialog<ErrorDialogRoute>(
            typeMap = mapOf(typeOf<ErrorDialogAction>() to serializableType<ErrorDialogAction>(),
            )
        ) { backStackEntry, dialogState ->
            val route = backStackEntry.toRouteOrNull<ErrorDialogRoute>() ?: DEFAULT_ERROR_DIALOG
            var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

            fun dismissWithAction(action: () -> Unit) {
                pendingAction = action
                dialogState.dismiss()
            }

            ErrorDialog(
                state = dialogState,
                title = route.title,
                subtitle = route.subtitle,
                actionTitle = route.actionTitle,
                errorCode = route.errorCode ?: DEFAULT_ERROR_DIALOG.errorCode,
                onDismissRequest = {
                    val action = pendingAction ?: router::goBack
                    pendingAction = null
                    action()
                },
                onAction = {
                    dismissWithAction { handleDialogAction(route.action, router) }
                },
                onReportToDeveloper = {
                    dismissWithAction {
                        router.navigate(
                            BugReportRoute(
                                logId = route.logId,
                                errorCode = route.errorCode ?: DEFAULT_ERROR_DIALOG.errorCode,
                                contextMessage = route.contextMessage ?: route.subtitle,
                            )
                        )
                    }
                }
            )
        }
    }
}

private fun handleDialogAction(action: ErrorDialogAction, router: Router) {
    when (action) {
        ErrorDialogAction.Dismiss,
        ErrorDialogAction.GoBack -> router.goBack()

        is ErrorDialogAction.Navigate -> {
            router.goBack()
            router.navigate(action.route)
        }
    }
}

private val DEFAULT_BLOCKING_ERROR = BlockingErrorRoute(
    title = "Something went wrong",
    subtitle = "Please try again.",
    errorCode = 1000,
    contextMessage = null,
)

private val DEFAULT_ERROR_DIALOG = ErrorDialogRoute(
    title = "Oops",
    subtitle = "Looks like something broke. Please try again.",
    actionTitle = "Dismiss",
    errorCode = 1000,
    contextMessage = null,
)
