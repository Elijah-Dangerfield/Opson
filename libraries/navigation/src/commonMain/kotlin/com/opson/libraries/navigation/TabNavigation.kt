package com.opson.libraries.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import com.opson.libraries.navigation.floatingwindow.FloatingWindowNavigator

/*
 * The NavController half of the tab API. The router owns the queue and the blocking-error gate;
 * what a tab switch does to the back stack lives here, where the composition test tier in this
 * module can drive it against a real NavController.
 */

/**
 * The [Router.switchTab] operation: pop to the tab system's root saving what was above it, then
 * push [route] restoring whatever it saved last time.
 *
 * Pops to the bottom of the back stack rather than to the graph's start destination, which is
 * the usual recipe. The start destination is fixed when the graph is built, and for someone who
 * onboards in this session it is the onboarding screen, which [enterTab] has already cleared
 * away. `popUpTo` an entry that is not on the stack is silently ignored, so with the usual recipe
 * nothing is ever popped or saved: every tap stacks another tab on top and Back walks through
 * them. It looks fine until the next cold start, which is why it is easy to ship.
 */
fun NavController.switchTab(route: TabRoute) {
    val root = currentBackStack.value.firstOrNull { it.destination !is NavGraph }
    navigate(route) {
        if (root != null) {
            popUpTo(root.destination.id) { saveState = true }
        }
        launchSingleTop = true
        restoreState = true
    }
}

/** The [Router.enterTab] operation: clear everything, then land on [route] as the new root. */
fun NavController.enterTab(route: TabRoute) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/**
 * The topmost entry that is a screen: not a graph, and not a sheet or dialog. A sheet draws over
 * the screen it opened from, so chrome that belongs to that screen (the bottom bar) reads this
 * rather than the current entry, or it would vanish behind the scrim every time a sheet opens.
 */
fun List<NavBackStackEntry>.currentScreenEntry(): NavBackStackEntry? =
    lastOrNull { it.destination !is NavGraph && it.destination !is FloatingWindowNavigator.Destination }
