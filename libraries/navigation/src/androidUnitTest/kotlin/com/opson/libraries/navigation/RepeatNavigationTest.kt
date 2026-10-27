package com.opson.libraries.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavGraph
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
internal class RepeatTestHomeRoute : Route()

@Serializable
internal class RepeatTestPlainRoute : Route()

@Serializable
internal data class RepeatTestDayRoute(val dayIso: String) : Route()

/**
 * A quick double tap sends the same navigate twice before the first push has
 * drawn. The second one must not stack the same screen again; a different
 * screen, or the same screen with different arguments, still goes through.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepeatNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aRepeatOfTheScreenOnTop_isDropped() {
        val navController = host()

        act(navController) {
            assertTrue(navigateUnlessShowing(RepeatTestPlainRoute()))
            assertFalse(navigateUnlessShowing(RepeatTestPlainRoute()))
        }

        assertEquals(listOf("RepeatTestHomeRoute", "RepeatTestPlainRoute"), navController.stack())
    }

    @Test
    fun theSameScreenWithOtherArguments_stillOpens() {
        val navController = host()

        act(navController) {
            navigateUnlessShowing(RepeatTestDayRoute("2026-10-26"))
            navigateUnlessShowing(RepeatTestDayRoute("2026-10-26"))
            navigateUnlessShowing(RepeatTestDayRoute("2026-10-27"))
        }

        assertEquals(
            listOf("RepeatTestHomeRoute", "RepeatTestDayRoute", "RepeatTestDayRoute"),
            navController.stack(),
        )
    }

    private fun host(): NavHostController {
        lateinit var navController: NavHostController
        compose.setContent {
            navController = rememberNavController()
            RepeatTestGraph(navController)
        }
        compose.waitForIdle()
        return navController
    }

    private fun act(navController: NavHostController, block: NavHostController.() -> Unit) {
        compose.runOnIdle { navController.block() }
        compose.waitForIdle()
    }
}

private fun NavHostController.stack(): List<String> =
    currentBackStack.value.filter { it.destination !is NavGraph }
        .map { it.destination.route.orEmpty().substringBefore('?').substringBefore('/').substringAfterLast('.') }

@Composable
private fun RepeatTestGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = RepeatTestHomeRoute(),
        typeMap = baseRouteTypeMap,
    ) {
        screen<RepeatTestHomeRoute> { }
        screen<RepeatTestPlainRoute> { }
        screen<RepeatTestDayRoute> { }
    }
}
