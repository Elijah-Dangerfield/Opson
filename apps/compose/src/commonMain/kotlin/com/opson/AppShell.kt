package com.opson

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import com.opson.features.foods.FoodsRoute
import com.opson.features.logger.LoggerRoute
import com.opson.features.settings.MoreRoute
import com.opson.features.today.TodayRoute
import com.opson.features.trends.TrendsRoute
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.TabBar
import com.opson.libraries.navigation.TabRoute
import com.opson.libraries.navigation.currentScreenEntry
import com.opson.libraries.ui.components.BottomNavBar
import com.opson.libraries.ui.components.BottomNavItem
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.described
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.shell_add
import opson.libraries.resources.generated.resources.shell_tab_foods
import opson.libraries.resources.generated.resources.shell_tab_more
import opson.libraries.resources.generated.resources.shell_tab_today
import opson.libraries.resources.generated.resources.shell_tab_trends
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The four bottom-bar tabs, in bar order. The center + is not one of them: it opens the logger
 * sheet over whichever tab is showing.
 */
internal enum class ShellTab(
    val label: StringResource,
    val icon: IconResource,
) {
    Today(Res.string.shell_tab_today, AppIcons.CalendarToday),
    Trends(Res.string.shell_tab_trends, AppIcons.Insights),
    Foods(Res.string.shell_tab_foods, AppIcons.Restaurant),
    More(Res.string.shell_tab_more, AppIcons.MoreHoriz),
    ;

    fun route(): TabRoute = when (this) {
        Today -> TodayRoute()
        Trends -> TrendsRoute()
        Foods -> FoodsRoute()
        More -> MoreRoute()
    }
}

internal fun NavDestination.shellTab(): ShellTab? = when {
    hasRoute<TodayRoute>() -> ShellTab.Today
    hasRoute<TrendsRoute>() -> ShellTab.Trends
    hasRoute<FoodsRoute>() -> ShellTab.Foods
    hasRoute<MoreRoute>() -> ShellTab.More
    else -> null
}

/** Tab to tab is a swap, not a push, so the host animates it as nothing at all. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.isSwitchingTabs(): Boolean {
    val from = initialState.destination.shellTab() ?: return false
    val to = targetState.destination.shellTab() ?: return false
    return from != to
}

/**
 * The bottom bar. Shown only while the screen on top is a tab root; a pushed screen hides it,
 * and a sheet or dialog does not, because those draw over the tab under a scrim. A tab can also
 * put it away for a while through [TabBar] (Today, while selecting foods).
 *
 * Its back stack read lives here rather than in the composable that hosts the `NavHost`, so a
 * navigation recomposes the bar and nothing above it.
 */
@Composable
internal fun ShellBottomBar(
    navController: NavHostController,
    router: Router,
    tabBar: TabBar,
) {
    val backStack by navController.currentBackStack.collectAsState()
    val heldAway by tabBar.hidden.collectAsState()
    val currentTab = backStack.currentScreenEntry()?.destination?.shellTab()

    // While the bar slides out there is no current tab; keep drawing the last one so the bar
    // does not flash to nothing-selected on its way off screen.
    // A plain holder rather than state: remembering the last tab must not schedule a recomposition.
    val lastTab = remember { LastTab() }
    val shownTab = currentTab?.also { lastTab.value = it } ?: lastTab.value

    AnimatedVisibility(
        visible = currentTab != null && !heldAway,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        BottomNavBar(
            items = ShellTab.entries.map { tab ->
                BottomNavItem(label = stringResource(tab.label), icon = tab.icon, selected = tab == shownTab)
            },
            onItemClick = { index ->
                val tab = ShellTab.entries[index]
                if (tab != currentTab) router.switchTab(tab.route())
            },
            onAddClick = { router.navigate(LoggerRoute()) },
            addIcon = AppIcons.Add.described(stringResource(Res.string.shell_add)),
        )
    }
}

private class LastTab {
    var value: ShellTab = ShellTab.Today
}
