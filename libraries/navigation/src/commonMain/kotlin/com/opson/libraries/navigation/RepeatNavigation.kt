package com.opson.libraries.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.toRoute
import com.opson.libraries.core.Catching
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Navigates to [route] unless that exact route, arguments included, is already
 * the entry on top. A quick double tap sends the same navigate twice before the
 * first push has drawn, and without this the same screen or sheet stacks twice.
 * Returns whether it navigated.
 *
 * Routes are compared by their serialized form, not `equals`: most routes are
 * plain classes, and two instances of one are never equal.
 */
fun NavController.navigateUnlessShowing(route: Route, builder: NavOptionsBuilder.() -> Unit = {}): Boolean {
    if (isShowing(route)) return false
    navigate(route, builder)
    return true
}

private fun NavController.isShowing(route: Route): Boolean {
    val entry = currentBackStackEntry ?: return false
    if (!entry.destination.hasRoute(route::class)) return false
    val shown = Catching<Route> { entry.toRoute(route::class) }.getOrNull() ?: return false
    return Catching { encoded(shown) == encoded(route) }.getOrDefault(false)
}

@OptIn(InternalSerializationApi::class)
private fun encoded(route: Route): String {
    @Suppress("UNCHECKED_CAST")
    val serializer = route::class.serializer() as KSerializer<Route>
    return Json.encodeToString(serializer, route)
}
