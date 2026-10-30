package com.opson.libraries.navigation

import kotlinx.coroutines.flow.SharedFlow

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
     * The bottom bar's tap on the tab already showing. Anything above the tab's root (a pushed
     * screen, a sheet) is popped and the root shows again; when the root is already on top,
     * [tabReselects] tells the tab instead, so it can scroll its list back to the top.
     */
    fun reselectTab(route: TabRoute)

    /**
     * A tap on the showing tab while its root was already on top. Collect it with
     * [OnTabReselected] rather than directly. Nothing is replayed: a tab that subscribes later
     * must not scroll for a tap it never saw.
     */
    val tabReselects: SharedFlow<TabRoute>

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
     *     navigate(LoggerRoute(slot = MealSlot.Lunch))
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

    /**
     * Opens the platform share sheet for a line of text, such as a link: Messages, Mail, Copy,
     * AirDrop. Like [shareFile], the person picks where it goes.
     */
    fun shareText(text: String)
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