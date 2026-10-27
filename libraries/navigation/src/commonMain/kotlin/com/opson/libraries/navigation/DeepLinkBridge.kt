@file:OptIn(ExperimentalObjCName::class)

package com.opson.libraries.navigation

import kotlinx.coroutines.flow.Flow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Pipe for incoming deep-link URLs from the host platform into Compose
 * Navigation. Each platform pushes URLs in via [emit]; the app collects
 * [urls] and routes them with `NavController.handleDeepLink`.
 *
 * Why a bridge instead of direct calls: the platform entry points (iOS
 * `.onOpenURL`, Android `onNewIntent`) fire outside Compose. The bridge
 * decouples them from the NavController, which only exists for the lifetime
 * of the App composable.
 *
 * Both platforms emit here: iOS from `.onOpenURL`, Android from `MainActivity`
 * (NavHost only reads the launch intent, so a link arriving while the app is
 * open would otherwise be lost).
 *
 * Per-route registration uses the `deepLinks` parameter on `screen<Route>`, built with
 * [routeDeepLink] (a bare `navDeepLink` fails the graph build):
 *
 * ```
 * screen<ProfileRoute>(
 *     deepLinks = listOf(routeDeepLink<ProfileRoute>(basePath = "opson://profile"))
 * ) { ... }
 * ```
 */
@ObjCName("DeepLinkBridge", exact = true)
interface DeepLinkBridge {
    /** Each emitted URL, once, to whichever collector takes it first. Emitted before anyone collects, it waits. */
    val urls: Flow<String>
    fun emit(url: String)
}
