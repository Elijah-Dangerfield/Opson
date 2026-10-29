package com.opson.libraries.ui.components.dialog.bottomsheet

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A drag that starts on a sheet's list closes the sheet, whatever pins the sheet's height.
 *
 * The phone is an iPhone 15's 393 by 852 points with its 59 point top inset and 34 point home
 * indicator, injected because Robolectric draws no system bars. That shape is the one that broke:
 * a 93% sheet rests about 57 points from the top, inside the top inset, where Material pads the
 * content by the inset minus the sheet's offset. Before the fix that padding sat outside the pinned
 * height, so every pixel the finger moved re-measured the sheet and Material snapped it back to
 * Expanded: the sheet stayed put under the finger, and once let go it bounced and never settled.
 *
 * The rows are placeholders nobody reads, hence the `VerifyStrings` suppression.
 */
@Suppress("VerifyStrings")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class BottomSheetDragTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun aDragOnTheListClosesASheetPinnedByHeightFraction() =
        assertDragCloses(heightFraction = BottomSheetDefaults.FullHeightFraction) { Rows() }

    @Test
    fun aDragOnTheListClosesASheetPinnedByItsModifier() = assertDragCloses(
        modifier = Modifier.sheetHeightFraction { BottomSheetDefaults.FullHeightFraction },
    ) { Rows() }

    @Test
    fun aDragOnTheContentClosesAScrollableSheet() = assertDragCloses(scrollableContent = true) {
        repeat(ROWS) { Text("Row $it", Modifier.height(48.dp).fillMaxWidth().then(if (it == 0) Modifier.testTag(LIST) else Modifier)) }
    }

    /**
     * The shape `ScrollInsideBottomSheet` cannot see: a list inside a child composable, in a sheet
     * nothing pins. Sixteen rows would leave the sheet's top resting inside the status bar.
     */
    @Test
    fun aDragOnANestedListClosesASheetNothingPins() = assertDragCloses { Rows(count = 16) }

    @OptIn(ExperimentalResourceApi::class)
    private fun assertDragCloses(
        modifier: Modifier = Modifier,
        heightFraction: Float? = null,
        scrollableContent: Boolean = false,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        var dismissed = 0
        var shown by mutableStateOf(true)
        lateinit var state: BottomSheetState
        compose.setContent {
            // Compose resources take the Android context from this effect, and only under inspection
            // mode (as in ShakeDialogTest). The handle's spoken label needs it; the sheet does not
            // run under inspection mode, which would open it already expanded.
            CompositionLocalProvider(LocalInspectionMode provides true) { PreviewContextConfigurationEffect() }
            PreviewContent {
                state = rememberBottomSheetState()
                // Gone once dismissed, as a sheet destination leaves the back stack.
                if (shown) SheetLayout(
                    onDismissRequest = {
                        dismissed++
                        shown = false
                    },
                    modifier = modifier,
                    scrollableContent = scrollableContent,
                    heightFraction = heightFraction,
                    showDragHandle = true,
                    backgroundColor = AppTheme.colors.bg,
                    contentColor = AppTheme.colors.ink,
                    state = state,
                    sheetGesturesEnabled = true,
                    shouldDismissOnBackPress = true,
                    shouldDismissOnClickOutside = true,
                    contentAlignment = Alignment.CenterHorizontally,
                    dragHandle = null,
                    windowInsets = { WindowInsets(top = 59.dp, bottom = 34.dp) },
                    content = content,
                )
            }
        }
        compose.waitForIdle()
        assertEquals(BottomSheetValue.Expanded, state.currentValue)
        val resting = state.offset

        // One small move per frame, as a finger arrives on a phone. A whole swipe injected between
        // two frames never re-measures mid-drag, and passed on the broken sheet too.
        compose.mainClock.autoAdvance = false
        val list = compose.onNodeWithTag(LIST, useUnmergedTree = true)
        list.performTouchInput { down(Offset(centerX, top + 4f)) }
        compose.mainClock.advanceTimeByFrame()
        val dragPx = with(compose.density) { DRAG.toPx() }
        repeat(STEPS) {
            list.performTouchInput { moveBy(Offset(0f, dragPx / STEPS)) }
            compose.mainClock.advanceTimeByFrame()
        }
        // The broken sheet stayed within a pixel of where it rested, and once let go never went idle.
        val travelled = state.offset - resting
        assertTrue(travelled > dragPx / 2, "the sheet followed the finger ${travelled}px of ${dragPx}px")
        list.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertEquals(BottomSheetValue.Hidden, state.currentValue)
        assertEquals(1, dismissed)
    }

    @Composable
    private fun Rows(count: Int = ROWS) {
        LazyColumn(Modifier.fillMaxWidth().testTag(LIST)) {
            items(count) { Text("Row $it", Modifier.height(48.dp).fillMaxWidth()) }
        }
    }

    private companion object {
        const val LIST = "list"
        const val ROWS = 60
        const val STEPS = 40
        val DRAG = 300.dp
    }
}
