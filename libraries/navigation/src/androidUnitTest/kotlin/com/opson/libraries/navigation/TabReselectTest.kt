package com.opson.libraries.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** A reselect reaches the tab it was for and no other. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TabReselectTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun onlyTheReselectedTabHearsIt() {
        val reselects = MutableSharedFlow<TabRoute>(extraBufferCapacity = 1)
        var first = 0
        var second = 0
        compose.setContent {
            OnTabReselected(reselects, TabTestFirstRoute::class) { first++ }
            OnTabReselected(reselects, TabTestSecondRoute::class) { second++ }
        }
        compose.waitForIdle()

        compose.runOnIdle { reselects.tryEmit(TabTestSecondRoute()) }
        compose.waitForIdle()

        assertEquals(0, first)
        assertEquals(1, second)
    }
}
