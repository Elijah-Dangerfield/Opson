package com.opson.libraries.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
internal class UnloggedTestHomeRoute : Route()

@Serializable
@UnloggedRoute
internal class UnloggedTestScreenRoute : Route()

@Serializable
@UnloggedRoute
internal data class UnloggedTestSheetRoute(val dayIso: String) : Route()

/**
 * A route marked [UnloggedRoute] is known as one on its destination, whether a screen or a sheet
 * and with arguments or none, so the app shell can keep its name out of crash and jank reports.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UnloggedRouteTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun onlyTheMarkedRoutes_areUnlogged() {
        lateinit var navController: NavHostController
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = UnloggedTestHomeRoute(), typeMap = baseRouteTypeMap) {
                screen<UnloggedTestHomeRoute> { }
                screen<UnloggedTestScreenRoute> { }
                screen<UnloggedTestSheetRoute> { }
            }
        }
        compose.waitForIdle()

        val seen = mutableListOf<Boolean>()
        fun current() = compose.runOnIdle { seen += navController.currentDestination!!.isUnlogged() }
        current()
        compose.runOnIdle { navController.navigate(UnloggedTestScreenRoute()) }
        current()
        compose.runOnIdle { navController.navigate(UnloggedTestSheetRoute("2026-10-30")) }
        current()

        assertEquals(listOf(false, true, true), seen)
        assertFalse(navController.graph.findStartDestination().isUnlogged())
        assertTrue(navController.currentDestination!!.isUnlogged())
    }
}

private fun androidx.navigation.NavGraph.findStartDestination() = findNode(startDestinationId)!!
