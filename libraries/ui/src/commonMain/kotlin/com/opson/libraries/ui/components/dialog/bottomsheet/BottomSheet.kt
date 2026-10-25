package com.opson.libraries.ui.components.dialog.bottomsheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.libraries.ui.system.LocalContentColor
import com.opson.system.color.ProvideContentColor
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.components.text.Text
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * @param scrollableContent when the content can be taller than the screen, pass `true` and let the
 * sheet own the scrolling instead of wrapping [content] in your own `Column(verticalScroll(...))`.
 *
 * That is not just convenience. Material3 derives the Expanded anchor from the sheet's *measured*
 * height, recomputes the anchors on every measure pass, and snaps to the recomputed target whenever
 * the new anchors differ from the old. A sheet whose height comes from its content can therefore be
 * re-measured mid-drag and yanked straight back to Expanded: dragging down to close jumps, and near
 * the top it bounces indefinitely without ever closing. Frame-by-frame on a device the offset
 * climbed 2 → 22 → 42 → 72 → 86 and then went to exactly 0.0 in two frames, eight times running,
 * with `targetValue` never leaving Expanded. Worse on iOS, present on both.
 *
 * Setting this pins the sheet to the full available height, so every recompute produces identical
 * anchors and `updateAnchors` has nothing to snap to.
 *
 * Short content measures stably and gives identical anchors anyway, which is why the bug stays
 * invisible until the first long sheet. Two things that look like the cause and are not, recorded
 * so nobody retries them: it is not overscroll (a null `LocalOverscrollFactory` changed nothing)
 * and it is not navigation (one navigate and one back per reproduction).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun BottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrollableContent: Boolean = false,
    showDragHandle: Boolean = false,
    backgroundColor: ColorResource = AppTheme.colors.background,
    contentColor: ColorResource = LocalContentColor.current,
    state: BottomSheetState = rememberBottomSheetState(),
    sheetGesturesEnabled: Boolean = true,
    shouldDismissOnBackPress: Boolean = true,
    shouldDismissOnClickOutside: Boolean = true,
    contentAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    dragHandle: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val dismissComplete by rememberUpdatedState(onDismissRequest)

    DisposableEffect(state, coroutineScope) {
        state.attachDismissController(coroutineScope) { dismissComplete() }
        onDispose { state.detachDismissController(coroutineScope) }
    }

    BackHandler(enabled = shouldDismissOnBackPress) {
        if (shouldDismissOnBackPress) {
            state.dismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { state.dismiss() },
        sheetState = state.materialSheetStateDelegate,
        containerColor = backgroundColor.color,
        sheetGesturesEnabled = sheetGesturesEnabled,
        scrimColor = AppTheme.colors.backgroundOverlay.color,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = shouldDismissOnBackPress,
            shouldDismissOnClickOutside = shouldDismissOnClickOutside
        ),
        tonalElevation = 0.dp,
        dragHandle = {
            if (showDragHandle) {
                dragHandle?.let {
                    it()
                } ?: DragHandle(
                    modifier = Modifier.fillMaxWidth(0.2f),
                    color = contentColor.color
                )
            }
        },
    ) {
        val scrollState = rememberScrollState()
        ProvideContentColor(contentColor) {
            Column(
                modifier = if (scrollableContent) {
                    Modifier.fillMaxHeight().verticalScroll(scrollState).then(modifier)
                } else {
                    modifier
                },
                horizontalAlignment = contentAlignment,
                content = content
            )
        }
    }
}

@Preview(heightDp = 500)
@Composable
private fun PreviewBottomSheet(
) {
    PreviewContent {
        com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheet(
            onDismissRequest = {},
            state = rememberBottomSheetState(BottomSheetValue.Expanded),
        ) {
            Text(
                text = "Content",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = Dimension.D1400,
                        horizontal = Dimension.D400
                    ),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(heightDp = 500)
@Composable
private fun PreviewScrollableBottomSheet() {
    PreviewContent {
        BottomSheet(
            onDismissRequest = {},
            scrollableContent = true,
            state = rememberBottomSheetState(BottomSheetValue.Expanded),
        ) {
            Text(
                text = "Content that is far taller than the sheet. ".repeat(60),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = Dimension.D1400,
                        horizontal = Dimension.D400
                    ),
                textAlign = TextAlign.Center
            )
        }
    }
}
