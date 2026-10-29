package com.opson.libraries.ui.components.dialog.bottomsheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.LocalContentColor
import com.opson.libraries.ui.system.clearFocusOnTapOutside
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.color.ProvideContentColor
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.ds_sheet_close
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.material3.BottomSheetDefaults as MaterialSheetDefaults

/**
 * The app's modal bottom sheet, on Material3's `ModalBottomSheet`.
 *
 * **The sheet's measured height must not change while it is being dragged.** Material3 derives the
 * Expanded anchor from the sheet's measured height, recomputes the anchors on every measure pass,
 * and snaps the sheet to the recomputed anchor whenever the new anchors differ from the old. A
 * drag that starts on scrolling content reaches the sheet through nested scroll, which moves it
 * without holding the drag lock, so that snap always wins: every frame the finger moves the sheet,
 * the re-measure puts it back. Dragging down to close jumps back to the top, and once released the
 * settle animation restarts on every frame and the sheet bounces without ever closing.
 *
 * What moves the height is the top inset. Material pads the content by the status bar minus the
 * sheet's offset, so a sheet whose top rests inside the status bar gets a different padding, and a
 * different height, at every offset. Short content rests far below the status bar, which is why
 * this stays invisible until the first long sheet. A 93% sheet rests about 57 points down on a
 * 393 by 852 point iPhone, inside its 59 point inset, so pinning the fraction was not enough while
 * the padding sat outside it. So this composable:
 *
 * 1. applies the insets itself, inside the pinned height, where a change in padding moves the
 *    content and not the sheet ([scrollableContent], [heightFraction], or a height in [modifier]);
 * 2. stops a sheet nothing pins at the status bar, so it never rests where the padding varies.
 *    That covers a list or scroll inside a child composable, which no lint rule can see.
 *
 * `BottomSheetDragTest` drives a drag frame by frame on that iPhone's geometry, for each shape.
 * Before this, the fraction and the unpinned sheet stayed under the finger and then never went idle.

 * Two things that look like the cause and are not, recorded so nobody retries them: it is not
 * overscroll (a null `LocalOverscrollFactory` changed nothing) and it is not navigation (one
 * navigate and one back per reproduction).
 *
 * @param modifier applied to the sheet's whole content area, before the system bar padding, so a
 * height modifier here pins the sheet the way [heightFraction] does (the logger grows with
 * [sheetHeightFraction]).
 * @param scrollableContent when the content can be taller than the screen, pass `true` and let the
 * sheet own the scrolling instead of wrapping [content] in your own `Column(verticalScroll(...))`.
 * It also pins the sheet to the full available height.
 * @param heightFraction pins the sheet to this fraction of the available height instead of letting
 * the content decide, for sheets the design draws at a fixed height (the logger is
 * [BottomSheetDefaults.FullHeightFraction]), and for a lazy list, which cannot sit inside
 * [scrollableContent]'s scroll.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun BottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrollableContent: Boolean = false,
    heightFraction: Float? = null,
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
) = SheetLayout(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    scrollableContent = scrollableContent,
    heightFraction = heightFraction,
    showDragHandle = showDragHandle,
    backgroundColor = backgroundColor,
    contentColor = contentColor,
    state = state,
    sheetGesturesEnabled = sheetGesturesEnabled,
    shouldDismissOnBackPress = shouldDismissOnBackPress,
    shouldDismissOnClickOutside = shouldDismissOnClickOutside,
    contentAlignment = contentAlignment,
    dragHandle = dragHandle,
    windowInsets = { MaterialSheetDefaults.windowInsets },
    content = content,
)

/**
 * [BottomSheet] with the system bar insets passed in, so a composition test can give the sheet the
 * status bar Robolectric does not draw.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun SheetLayout(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    scrollableContent: Boolean,
    heightFraction: Float?,
    showDragHandle: Boolean,
    backgroundColor: ColorResource,
    contentColor: ColorResource,
    state: BottomSheetState,
    sheetGesturesEnabled: Boolean,
    shouldDismissOnBackPress: Boolean,
    shouldDismissOnClickOutside: Boolean,
    contentAlignment: Alignment.Horizontal,
    dragHandle: @Composable (() -> Unit)?,
    windowInsets: @Composable () -> WindowInsets,
    content: @Composable ColumnScope.() -> Unit,
) {
    require(heightFraction == null || heightFraction in 0f..1f) {
        "heightFraction is a fraction of the available height, got $heightFraction"
    }
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
        scrimColor = AppTheme.colors.scrim.color,
        shape = AppTheme.shapes.sheet,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = shouldDismissOnBackPress,
            shouldDismissOnClickOutside = shouldDismissOnClickOutside
        ),
        tonalElevation = 0.dp,
        // Both drawn below instead, inside the pinned height: see the KDoc on BottomSheet.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = null,
    ) {
        val insets = windowInsets()
        val height = when {
            heightFraction != null -> Modifier.fillMaxHeight(heightFraction)
            scrollableContent -> Modifier.fillMaxHeight()
            else -> Modifier
        }
        // Material still consumes the sheet's offset from the top inset above this, so the padding
        // here shrinks as the sheet's top leaves the status bar, exactly as Material's own did.
        Column(
            modifier = height
                .then(modifier)
                .belowTopInset(insets)
                .fillMaxWidth()
                .windowInsetsPadding(insets)
                // The sheet is a window of its own, out of reach of the app root's.
                .clearFocusOnTapOutside(),
        ) {
            if (showDragHandle) {
                val close = stringResource(Res.string.ds_sheet_close)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .semantics(mergeDescendants = true) {
                            if (sheetGesturesEnabled) {
                                dismiss(close) {
                                    state.dismiss()
                                    true
                                }
                            }
                        },
                ) {
                    dragHandle?.invoke() ?: SheetHandle(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                }
            }
            val scrollState = rememberScrollState()
            ProvideContentColor(contentColor) {
                Column(
                    // The rest of a pinned height, or the content's own height when nothing pins it.
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(if (scrollableContent) Modifier.verticalScroll(scrollState) else Modifier),
                    horizontalAlignment = contentAlignment,
                    content = content
                )
            }
        }
    }
}

/**
 * Stops a sheet whose height comes from its content at the top inset, so it never rests where
 * Material's offset-dependent padding could change its height mid-drag (see [BottomSheet]). Short
 * content never gets there and is untouched; content that would have is cut at the status bar,
 * which is where a sheet that does not scroll ends anyway. A pinned height (a fraction, the full
 * height, or a caller's fixed size) arrives as a minimum and is left alone, since it is stable
 * wherever it rests.
 */
private fun Modifier.belowTopInset(insets: WindowInsets): Modifier = layout { measurable, constraints ->
    val cap = if (constraints.hasBoundedHeight) {
        maxOf(constraints.minHeight, constraints.maxHeight - insets.getTop(this))
    } else {
        constraints.maxHeight
    }
    val placeable = measurable.measure(constraints.copy(maxHeight = cap))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
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
