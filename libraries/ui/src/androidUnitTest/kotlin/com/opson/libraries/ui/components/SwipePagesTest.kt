package com.opson.libraries.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.opson.system.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * A dot tapped and then the dot it came from, before the slide has landed, leaves the pages where
 * they started. The first slide was cancelled part way, and nothing put the drag back, so the
 * pages sat offset by however far it had got. The height while sliding is in
 * `SwipePagesHeightTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SwipePagesTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun goingBackMidSlide_landsThePagesWhereTheyStarted() {
        var page by mutableIntStateOf(0)
        compose.setContent {
            AppTheme {
                Box(Modifier.width(300.dp)) {
                    SwipePages(page = page, pageCount = 2, onPageChange = { page = it }) { index ->
                        Box(Modifier.fillMaxWidth().height(100.dp).testTag("page$index"))
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false

        page = 1
        compose.waitForIdle()
        repeat(MID_SLIDE_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val mid = compose.onNodeWithTag("page0", useUnmergedTree = true).getUnclippedBoundsInRoot().left
        assertTrue(mid < 0.dp && mid > (-300).dp, "part way there: $mid")
        page = 0
        compose.waitForIdle()
        repeat(SETTLED_FRAMES) { compose.mainClock.advanceTimeByFrame() }

        compose.onNodeWithTag("page0", useUnmergedTree = true).assertLeftPositionInRootIsEqualTo(0.dp)
        compose.onNodeWithTag("page1", useUnmergedTree = true).assertLeftPositionInRootIsEqualTo(300.dp)
    }

    private companion object {
        const val MID_SLIDE_FRAMES = 4
        const val SETTLED_FRAMES = 120
    }
}
