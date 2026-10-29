package com.opson

import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavGraphBuilder
import com.opson.libraries.core.BuildInfo
import com.opson.libraries.navigation.BugReportRoute
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.ShakeDialogRoute
import com.opson.libraries.navigation.dialog
import com.opson.libraries.navigation.toRouteOrNull
import com.opson.libraries.networking.NetworkInspector
import com.opson.libraries.ui.components.dialog.ShakeDialog
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.bug_report_context_shake
import org.jetbrains.compose.resources.stringResource
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class ShakeDialogEntryPoint(
    private val networkInspector: NetworkInspector,
    private val shakeHandler: ShakeHandler,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        dialog<ShakeDialogRoute> { backStackEntry, dialogState ->
            val route = backStackEntry.toRouteOrNull<ShakeDialogRoute>()

            // The handler latches "a dialog is up" when it navigates here and
            // has no other way to learn it came down. Hanging the release off
            // the destination leaving the back stack covers every exit —
            // including system back and a scrim tap, which no button callback
            // sees.
            DisposableEffect(Unit) {
                onDispose { shakeHandler.onDialogDismissed() }
            }
            val shakeContext = stringResource(Res.string.bug_report_context_shake)

            ShakeDialog(
                state = dialogState,
                headline = route?.headline ?: "I felt that.",
                subtext = route?.subtext,
                onDismiss = { router.goBack() },
                onReportBug = {
                    router.goBack()
                    router.navigate(
                        BugReportRoute(contextMessage = shakeContext, shakeScreenshot = true)
                    )
                },
                // Debug-only: reuse the shake gesture to also open the
                // WiretapKMP network inspector. Hidden in release (and the
                // inspector itself is the noop there).
                onOpenNetworkInspector = if (BuildInfo.isDebug) {
                    {
                        router.goBack()
                        networkInspector.open()
                    }
                } else {
                    null
                },
            )
        }
    }
}
