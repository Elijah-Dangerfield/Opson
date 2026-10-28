package com.opson.libraries.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.opson.libraries.flowroutines.ObserveWithLifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * `ObserveWithLifecycle` lives in `:libraries:flowroutines`, which has no composition tier;
 * every screen's `ObserveEvents` goes through it, and this module already runs compositions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ObserveWithLifecycleTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun anItemReachesTheHandlerFromTheLatestComposition() {
        val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val handledBy = mutableListOf<String>()
        var screen by mutableStateOf("first")
        compose.setContent {
            val current = screen
            ObserveWithLifecycle(events) { handledBy += current }
        }
        compose.waitForIdle()

        compose.runOnIdle { screen = "second" }
        compose.waitForIdle()
        compose.runOnIdle { events.tryEmit(Unit) }
        compose.waitForIdle()

        assertEquals(listOf("second"), handledBy)
    }
}
