package com.opson.libraries.navigation.floatingwindow

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.get
import com.opson.libraries.navigation.AnimationType
import com.opson.libraries.navigation.Route
import com.opson.libraries.navigation.baseRouteTypeMap
import com.opson.libraries.navigation.screen
import com.opson.libraries.navigation.serializableType
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.typeOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Serializable
internal class HostTestHomeRoute : Route()

@Serializable
internal class HostTestSheetRoute : Route()

private const val SHEET_TAG = "floating-window-test-sheet"

/**
 * The two claims [FloatingWindowHost] makes that are *about composition*, which is why they are
 * here and not in a view-model test.
 *
 * First, that a window popped before it ever composes is still released. The host completes a
 * navigator transition from `onDispose`, and what never composes never disposes, so without the
 * sweep `NavController` holds that entry below CREATED and never clears its `ViewModelStore`,
 * pinning it and everything it references for the life of the process. Second, that a window which
 * does compose gets its own back stack entry as the owner of its state, so a sheet's `ViewModel`
 * dies with the sheet rather than with the activity.
 *
 * **Not covered, and not an oversight: the `windowsToDispose` guard in the sweep.** Deleting it
 * leaves both tests green, and nothing driving this host from outside can turn it red. The guard
 * only bites if the sweep runs while an entry is off the back stack *and* still composed, and that
 * state does not survive into the sweep: leaving the back stack is what removes the window from the
 * composition, Compose runs `onDispose` during that same apply pass, and the sweep body is a
 * coroutine dispatched afterwards, by which point the entry is out of `windowsToDispose` and
 * already complete. Checked rather than assumed: instrumenting the guard to report every time it
 * suppressed a completion printed nothing across a single pop, two windows popped in one
 * `NavController` call, a pop and a push in the same block, and a pop stepped frame by frame with
 * the clock held. It stays because it is upstream `DialogHost`'s shape, and because it goes back to
 * being load-bearing the moment the `DisposableEffect` moves into a subcomposition (as upstream's
 * does) and disposal stops landing in that same pass.
 *
 * Two more mutations survive, for reasons worth knowing before you trust this file. Either
 * visibility path alone: `PopulateVisibleList`'s `ON_START` branch and `rememberVisibleList`'s own
 * filter are redundant with each other on this flow, so breaking one still shows the window
 * (breaking both is red). And the sweep's back stack check, because completing the transition of an
 * entry that is still on the stack promotes it to RESUMED rather than destroying it, which is
 * invisible to a test that only asserts on release.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingWindowHostTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun anEntryPoppedBeforeItComposesIsReleasedWithItsViewModelStore() {
        val navigator = FloatingWindowNavigator()
        lateinit var navController: NavHostController

        compose.setContent {
            navController = rememberNavController(navigator)
            FloatingWindowTestGraph(navController)
            FloatingWindowHost(navigator)
        }
        compose.waitForIdle()

        var popped: NavBackStackEntry? = null
        var probe: StateProbe? = null
        // One block, so no recomposition can run between the push and the pop. That is the whole
        // condition: the sheet is gone again before the host ever sees it, so there is no
        // DisposableEffect to complete its transition.
        compose.runOnIdle {
            navController.navigate(HostTestSheetRoute())
            val entry = navController.currentBackStackEntry
            popped = entry
            probe = entry?.let { ViewModelProvider.create(it, StateProbe.Factory)[StateProbe::class] }
            navController.popBackStack()
        }
        compose.waitForIdle()

        val entry = assertNotNull(popped, "navigate() pushed no entry")
        assertEquals(
            Lifecycle.State.DESTROYED,
            entry.lifecycle.currentState,
            "the popped entry is still marked in transition, so NavController keeps it alive",
        )
        assertTrue(
            assertNotNull(probe).cleared,
            "the entry's ViewModelStore was never cleared: it and everything its view models " +
                "hold are pinned for the life of the process",
        )
    }

    @Test
    fun aWindowThatComposesOwnsItsStateThroughItsOwnEntry() {
        val navigator = FloatingWindowNavigator()
        lateinit var navController: NavHostController
        var sheetProbe: StateProbe? = null
        var sheetOwner: Any? = null

        compose.setContent {
            navController = rememberNavController(navigator)
            FloatingWindowTestGraph(navController) { entry ->
                sheetProbe = viewModel(factory = StateProbe.Factory)
                sheetOwner = entry
                BasicText("sheet", Modifier.testTag(SHEET_TAG))
            }
            FloatingWindowHost(navigator)
        }
        compose.waitForIdle()

        compose.runOnIdle { navController.navigate(HostTestSheetRoute()) }
        compose.waitForIdle()

        compose.onNodeWithTag(SHEET_TAG).assertIsDisplayed()
        val entry = assertNotNull(navController.currentBackStackEntry)
        assertSame(entry, sheetOwner, "the host composed the window against the wrong entry")
        assertFalse(
            assertNotNull(sheetProbe).cleared,
            "the window's state was torn down while it was on screen",
        )

        compose.runOnIdle { navController.popBackStack() }
        compose.waitForIdle()

        compose.onNodeWithTag(SHEET_TAG).assertDoesNotExist()
        assertEquals(
            Lifecycle.State.DESTROYED,
            entry.lifecycle.currentState,
            "the window is off the screen but its entry was never released",
        )
        assertTrue(
            assertNotNull(sheetProbe).cleared,
            "the window's state outlived the window, so it is scoped to the activity rather than " +
                "to the back stack entry it was shown for",
        )
    }
}

/** Reports whether the `ViewModelStore` it was put in has been cleared. */
private class StateProbe : ViewModel() {
    var cleared = false
        private set

    override fun onCleared() {
        cleared = true
    }

    companion object {
        val Factory = viewModelFactory { initializer { StateProbe() } }
    }
}

@Composable
private fun FloatingWindowTestGraph(
    navController: NavHostController,
    sheetContent: @Composable (NavBackStackEntry) -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = HostTestHomeRoute(),
        typeMap = mapOf(typeOf<AnimationType>() to serializableType<AnimationType>()),
    ) {
        screen<HostTestHomeRoute> { }
        // The real `bottomSheet` / `dialog` builders wrap the content in a design system sheet.
        // Registering the destination directly keeps the subject the host's own bookkeeping rather
        // than the sheet's chrome.
        destination(
            FloatingWindowNavDestinationBuilder(
                provider[FloatingWindowNavigator::class],
                HostTestSheetRoute::class,
                baseRouteTypeMap,
            ) { entry -> sheetContent(entry) }
        )
    }
}
