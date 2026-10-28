package com.opson.libraries.navigation

import kotlinx.serialization.Serializable

@Serializable
data class BlockingErrorRoute(
    val title: String,
    val subtitle: String,
    val errorCode: Int? = null,
    val logId: String? = null,
    val contextMessage: String? = null,
) : Route(
    enter = AnimationType.FadeIn,
    exit = AnimationType.FadeOut,
    popExit = AnimationType.FadeOut,
), NavigableWhileBlocked

/**
 * Blocking screen shown when the auth server rejected the device's session
 * mid-run (token refresh failed, or the client declared the session
 * unrecoverable). Pushed on top of the stack, and its one way on is signing
 * in. [wasAnonymous] picks the copy: a claimed account is asked to sign in
 * again, a legacy guest is told their log can't be recovered.
 */
@Serializable
data class SessionExpiredRoute(
    val wasAnonymous: Boolean = false,
) : Route(
    enter = AnimationType.FadeIn,
    exit = AnimationType.FadeOut,
    popExit = AnimationType.FadeOut,
), NavigableWhileBlocked

/**
 * Blocking screen shown when the server returned the locked `403`
 * access-denied envelope (banned / suspended). Distinct from [BlockingErrorRoute]
 * because the copy is keyed off a machine-readable [reason] (the client
 * localizes; the server never sends copy on the wire) and the screen exposes an
 * optional appeal link.
 *
 *  - [reason] is the wire token (e.g. `"banned"` / `"suspended"`); unknown
 *    values fall back to a generic block message.
 *  - [until] is the ISO-8601 timestamp when the block lifts; null = indefinite.
 *  - [appealUrl] opens in a browser via [Router.openWebLink]; null hides the
 *    appeal button.
 */
@Serializable
data class AccessDeniedRoute(
    val reason: String,
    val until: String? = null,
    val appealUrl: String? = null,
) : Route(
    enter = AnimationType.FadeIn,
    exit = AnimationType.FadeOut,
    popExit = AnimationType.FadeOut,
), NavigableWhileBlocked

@Serializable
data class ErrorDialogRoute(
    val title: String,
    val subtitle: String,
    val actionTitle: String,
    val action: ErrorDialogAction = ErrorDialogAction.Dismiss,
    val errorCode: Int? = null,
    val logId: String? = null,
    val contextMessage: String? = null,
) : Route(
    enter = AnimationType.SlideUp,
    exit = AnimationType.SlideDown,
    popExit = AnimationType.SlideDown,
)

@Serializable
sealed interface ErrorDialogAction {
    @Serializable
data object Dismiss : ErrorDialogAction

    @Serializable
data object GoBack : ErrorDialogAction

    @Serializable
data class Navigate(val route: Route) : ErrorDialogAction
}
