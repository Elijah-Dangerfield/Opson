package com.opson.libraries.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.get
import com.opson.libraries.navigation.floatingwindow.FloatingWindowHost
import com.opson.libraries.navigation.floatingwindow.FloatingWindowNavDestinationBuilder
import com.opson.libraries.navigation.floatingwindow.FloatingWindowNavigator
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@Serializable
internal class TabTestEntryRoute : Route()

@Serializable
internal class TabTestFirstRoute : TabRoute()

@Serializable
internal class TabTestSecondRoute : TabRoute()

@Serializable
internal class TabTestDetailRoute : Route()

@Serializable
internal class TabTestSheetRoute : Route()

/**
 * What a tab switch does to a real back stack. A `NavController` needs a host to exist, which is
 * why this is in the composition tier rather than a plain unit test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TabNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aTabGetsItsStackBackAfterVisitingAnotherTab() {
        val navController = host(startDestination = TabTestFirstRoute())

        act(navController) {
            switchTab(TabTestSecondRoute())
            navigate(TabTestDetailRoute())
        }
        val detailEntryId = assertNotNull(navController.currentBackStackEntry).id

        act(navController) { switchTab(TabTestFirstRoute()) }
        assertEquals(listOf("TabTestFirstRoute"), navController.stack())

        act(navController) { switchTab(TabTestSecondRoute()) }
        assertEquals(listOf("TabTestFirstRoute", "TabTestSecondRoute", "TabTestDetailRoute"), navController.stack())
        assertEquals(
            detailEntryId,
            navController.currentBackStackEntry?.id,
            "the detail screen came back as a new entry, so its state was not restored",
        )
    }

    @Test
    fun switchingTabsAfterEnteringFromAnotherFlowDoesNotPileTabsUp() {
        // The graph's start destination is the entry flow, which enterTab clears away. A switch
        // that pops to the start destination finds nothing to pop and stacks the tab on top.
        val navController = host(startDestination = TabTestEntryRoute())

        act(navController) { enterTab(TabTestFirstRoute()) }
        assertEquals(listOf("TabTestFirstRoute"), navController.stack())

        act(navController) {
            switchTab(TabTestSecondRoute())
            switchTab(TabTestFirstRoute())
            switchTab(TabTestSecondRoute())
        }
        assertEquals(listOf("TabTestFirstRoute", "TabTestSecondRoute"), navController.stack())

        act(navController) { navigate(TabTestDetailRoute()) }
        val detailEntryId = assertNotNull(navController.currentBackStackEntry).id
        act(navController) {
            switchTab(TabTestFirstRoute())
            switchTab(TabTestSecondRoute())
        }
        assertEquals(detailEntryId, navController.currentBackStackEntry?.id)
    }

    @Test
    fun theCurrentScreenIsTheOneUnderAnOpenSheet() {
        val navController = host(startDestination = TabTestFirstRoute())

        act(navController) { navigate(TabTestSheetRoute()) }

        val current = assertNotNull(navController.currentBackStackEntry)
        val screen = assertNotNull(navController.currentBackStack.value.currentScreenEntry())
        assertNotEquals(current.id, screen.id)
        assertEquals("TabTestFirstRoute", screen.routeName())
    }

    private fun host(startDestination: Route): NavHostController {
        val navigator = FloatingWindowNavigator()
        lateinit var navController: NavHostController
        compose.setContent {
            navController = rememberNavController(navigator)
            TabTestGraph(navController, startDestination)
            FloatingWindowHost(navigator)
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
    currentBackStack.value.filter { it.destination !is NavGraph }.map { it.routeName() }

private fun androidx.navigation.NavBackStackEntry.routeName(): String =
    destination.route.orEmpty().substringBefore('?').substringBefore('/').substringAfterLast('.')

@Composable
private fun TabTestGraph(navController: NavHostController, startDestination: Route) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        typeMap = baseRouteTypeMap,
    ) {
        screen<TabTestEntryRoute> { }
        screen<TabTestFirstRoute> { }
        screen<TabTestSecondRoute> { }
        screen<TabTestDetailRoute> { }
        destination(
            FloatingWindowNavDestinationBuilder(
                provider[FloatingWindowNavigator::class],
                TabTestSheetRoute::class,
                baseRouteTypeMap,
            ) { }
        )
    }
}
