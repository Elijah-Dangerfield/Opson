package com.opson.libraries.navigation

import com.opson.libraries.ui.snackbar.SnackBarPresenter
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logging.KLog

data class NavigationOptions(
    val clearBackStack: Boolean = false,
    val launchSingleTop: Boolean = false,
    val restoreState: Boolean = false,
)

interface Router {

    fun navigate(route: Route, options: NavigationOptions = NavigationOptions())

    /**
     * Compile-time guard: a [TabRoute] goes through [switchTab] or [enterTab]. Pushed with plain
     * [navigate] it lands on top of the current tab's stack, and every tab switch after that saves
     * and restores the wrong stack.
     */
    @Deprecated(
        message = "A TabRoute goes through switchTab(), or enterTab() from outside the tabs.",
        replaceWith = ReplaceWith("switchTab(route)"),
        level = DeprecationLevel.ERROR,
    )
    fun navigate(route: TabRoute, options: NavigationOptions = NavigationOptions()): Nothing =
        throw UnsupportedOperationException("Compile-time guard only, use switchTab(route)")

    fun goBack()

    fun popBackTo(route: Route, inclusive: Boolean)

    /**
     * Switch tabs from inside the tab system: saves the current tab's stack and restores the
     * target's, if it has one. For bottom-bar taps, and for a feature that needs to land on
     * another tab.
     */
    fun switchTab(route: TabRoute)

    /**
     * Enter the tab system from outside it (onboarding, sign-in, a fresh guest session). Clears
     * the back stack so Back cannot return to the flow that led here. Once inside, use
     * [switchTab].
     */
    fun enterTab(route: TabRoute)

    /**
     * Runs several navigation calls as one queued command, so the tail cannot be stranded if
     * the caller's scope dies between them:
     * ```
     * router.batch {
     *     switchTab(TodayRoute())
     *     navigate(LoggerRoute(slot = "lunch"))
     * }
     * ```
     */
    fun batch(block: RouterBatch.() -> Unit)

    fun openWebLink(url: String)

    /**
     * Opens the platform share sheet for a file built in memory, for exports. The user picks where
     * it goes (Files, Mail, AirDrop, Drive); the app never sends it anywhere itself. Failure to
     * open the sheet is logged, like [openWebLink], since there is nothing a screen can do about it.
     */
    fun shareFile(name: String, mimeType: String, bytes: ByteArray)
}

/**
 * The navigation primitives available inside [Router.batch]. Each call runs against the
 * controller immediately rather than being queued on its own.
 */
interface RouterBatch {
    fun navigate(route: Route, options: NavigationOptions = NavigationOptions())

    @Deprecated(
        message = "A TabRoute goes through switchTab() inside a batch too.",
        replaceWith = ReplaceWith("switchTab(route)"),
        level = DeprecationLevel.ERROR,
    )
    fun navigate(route: TabRoute, options: NavigationOptions = NavigationOptions()): Nothing =
        throw UnsupportedOperationException("Compile-time guard only, use switchTab(route)")

    fun switchTab(route: TabRoute)

    fun goBack()

    fun popBackTo(route: Route, inclusive: Boolean)
}

fun <T> Catching<T>.blockingScreenOnError(
    router: Router,
    title: String = "This is super embarrassing",
    subtitle: String = "Our intern Ryan seems to have left a bug in the app. Sorry, you'll need to kill and restart the app.",
    logId: String? = null,
    includeErrorMessage: Boolean = false,
): Catching<T> = this.onFailure {
    val errorCode = it.toKnownErrorCode()
    val resolvedLogId = logId ?: KLog.e(it)?.raw
    router.navigate(
        BlockingErrorRoute(
            title = title,
            subtitle = subtitle,
            errorCode = errorCode,
            logId = resolvedLogId,
            contextMessage = it.message.takeIf { includeErrorMessage }
        )
    )
}

fun <T> Catching<T>.dialogOnError(
    router: Router,
    title: String = "Oops something went wrong",
    subtitle: String = "Please try again",
    actionTitle: String = "Okay",
    action: ErrorDialogAction = ErrorDialogAction.Dismiss,
    logId: String? = null,
    includeErrorMessage: Boolean = false,
    ): Catching<T> = this.onFailure {
    val errorCode = it.toKnownErrorCode()
    val resolvedLogId = logId ?: KLog.e(it)?.raw
    router.navigate(
        ErrorDialogRoute(
            title = title,
            subtitle = subtitle,
            actionTitle = actionTitle,
            action = action,
            errorCode = errorCode,
            logId = resolvedLogId,
            contextMessage = it.message.takeIf { includeErrorMessage }
        )
    )
}

fun <T> Catching<T>.toastOnError(
    title: String = "Oops something went wrong",
    subtitle: String = "Please try again",
): Catching<T> = this.onFailure {
    SnackBarPresenter.show(
        title = title,
        message = subtitle,
    )
}