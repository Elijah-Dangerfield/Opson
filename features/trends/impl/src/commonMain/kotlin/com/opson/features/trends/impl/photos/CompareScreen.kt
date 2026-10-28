package com.opson.features.trends.impl.photos

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.HeaderIconButton
import com.opson.libraries.ui.components.MenuItem
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.PopoverMenu
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.TextArea
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.photos_choice
import opson.libraries.resources.generated.resources.photos_compare
import opson.libraries.resources.generated.resources.photos_more
import opson.libraries.resources.generated.resources.photos_note
import opson.libraries.resources.generated.resources.photos_note_hint
import opson.libraries.resources.generated.resources.photos_pick_left
import opson.libraries.resources.generated.resources.photos_pick_right
import opson.libraries.resources.generated.resources.photos_remove_comparison
import opson.libraries.resources.generated.resources.photos_save_comparison
import opson.libraries.resources.generated.resources.photos_side_by_side
import opson.libraries.resources.generated.resources.photos_slider
import opson.libraries.resources.generated.resources.photos_slider_handle
import opson.libraries.resources.generated.resources.photos_title
import opson.libraries.resources.generated.resources.trends_loading_cd
import org.jetbrains.compose.resources.stringResource

/**
 * Compare (design sync 2, 8.5): the two photos, then a date picker for each,
 * the Side by side / Slider control and an optional note on the later one.
 * No number is drawn on or near a photo.
 */
@Composable
fun CompareScreen(
    state: CompareState,
    onAction: (CompareAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        CompareContent(state, onAction, Modifier.padding(padding).verticalScroll(rememberScrollState()))
    }
}

/** The page without its scaffold, for previews and the leak test. */
@Composable
internal fun CompareContent(
    state: CompareState,
    onAction: (CompareAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        PageHeader(
            title = stringResource(Res.string.photos_compare),
            backLabel = stringResource(Res.string.photos_title),
            onBack = { onAction(CompareAction.BackTapped) },
        ) {
            if (state.lock == PhotoLockState.Unlocked && state.left != null && state.right != null) {
                Box {
                    HeaderIconButton(
                        icon = AppIcons.MoreVert.described(stringResource(Res.string.photos_more)),
                        onClick = { onAction(CompareAction.MenuTapped) },
                        selected = state.menuOpen,
                    )
                    PopoverMenu(expanded = state.menuOpen, onDismissRequest = { onAction(CompareAction.MenuDismissed) }) {
                        if (state.savedId == null) {
                            MenuItem(
                                label = stringResource(Res.string.photos_save_comparison),
                                icon = AppIcons.BookmarkAdd,
                                onClick = { onAction(CompareAction.SaveComparisonTapped) },
                                showDivider = false,
                            )
                        } else {
                            MenuItem(
                                label = stringResource(Res.string.photos_remove_comparison),
                                icon = AppIcons.Delete,
                                onClick = { onAction(CompareAction.RemoveComparisonTapped) },
                                showDivider = false,
                            )
                        }
                    }
                }
            }
        }
        if (state.lock != PhotoLockState.Unlocked) {
            LockPanel(state.lock, onUnlock = { onAction(CompareAction.UnlockTapped) })
            return@Column
        }
        if (state.loading) {
            DelayedLoadingIndicator(contentDescription = stringResource(Res.string.trends_loading_cd))
            return@Column
        }
        when (state.mode) {
            CompareMode.SideBySide -> Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                PhotoFrame(state.left?.image, state.left?.date, state.left?.pose, Modifier.weight(1f).aspectRatio(SIDE_BY_SIDE))
                PhotoFrame(state.right?.image, state.right?.date, state.right?.pose, Modifier.weight(1f).aspectRatio(SIDE_BY_SIDE))
            }
            CompareMode.Slider -> SliderFrame(state, onAction)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            DatePicker(state, CompareSide.Left, onAction, Modifier.weight(1f))
            DatePicker(state, CompareSide.Right, onAction, Modifier.weight(1f))
        }
        SegmentedControl(
            options = CompareMode.entries,
            selected = state.mode,
            onSelect = { onAction(CompareAction.ModePicked(it)) },
            label = {
                stringResource(
                    when (it) {
                        CompareMode.SideBySide -> Res.string.photos_side_by_side
                        CompareMode.Slider -> Res.string.photos_slider
                    },
                )
            },
            minHeight = MinTouchTarget,
        )
        state.right?.let { right ->
            EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding, verticalArrangement = Arrangement.spacedBy(Dimension.D200)) {
                Text(
                    text = stringResource(Res.string.photos_note, DateCopy.monthDay(right.date)),
                    typography = AppTheme.type.secondaryStrong.Bold,
                    color = AppTheme.colors.muted,
                )
                TextArea(
                    value = state.note,
                    onValueChange = { onAction(CompareAction.NoteTyped(it)) },
                    placeholder = stringResource(Res.string.photos_note_hint),
                    minLines = 1,
                    borderColor = AppTheme.colors.surface,
                )
            }
        }
    }
}

/**
 * One frame: the earlier photo underneath, the later one over it from the
 * handle rightwards. Dragging anywhere on the frame moves the handle.
 */
@Composable
private fun SliderFrame(state: CompareState, onAction: (CompareAction) -> Unit) {
    val latestAt by rememberUpdatedState(state.sliderAt)
    val description = stringResource(Res.string.photos_slider_handle)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(SLIDER)
            .clip(AppTheme.shapes.card)
            .background(AppTheme.colors.soft)
            .pointerInput(Unit) {
                detectTapGestures { offset -> onAction(CompareAction.SliderMoved(offset.x / size.width)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, drag -> onAction(CompareAction.SliderMoved(latestAt + drag / size.width)) }
            }
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(state.sliderAt, 0f..1f)
                setProgress { value ->
                    onAction(CompareAction.SliderMoved(value))
                    true
                }
            },
    ) {
        val full = maxWidth
        val split = full * state.sliderAt
        PhotoImage(state.left?.image, Modifier.fillMaxSize())
        // The later photo, laid out at full width and cut at the handle, so it never squeezes.
        Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(full - split).clipToBounds()) {
            FullWidth(full) { PhotoImage(state.right?.image, Modifier.fillMaxSize()) }
        }
        Box(
            Modifier
                .offset(x = split - HANDLE_LINE / 2)
                .width(HANDLE_LINE)
                .fillMaxHeight()
                .background(AppTheme.colors.surface),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = split - HANDLE / 2)
                .size(HANDLE)
                .clip(CircleShape)
                .background(AppTheme.colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon = AppIcons.SwapHoriz, size = Dimension.D800, color = AppTheme.colors.ink)
        }
        state.left?.let { DateTag(DateCopy.monthDay(it.date), Modifier.align(Alignment.BottomStart).padding(Dimension.D400)) }
        state.right?.let { DateTag(DateCopy.monthDay(it.date), Modifier.align(Alignment.BottomEnd).padding(Dimension.D400)) }
    }
}

/** Lays [content] out at [width], aligned to the end of a narrower parent. */
@Composable
private fun FullWidth(width: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val full = width.roundToPx()
        val placeables = measurables.map { it.measure(Constraints.fixed(full, constraints.maxHeight)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { it.place(constraints.maxWidth - full, 0) }
        }
    }
}

@Composable
private fun DatePicker(state: CompareState, side: CompareSide, onAction: (CompareAction) -> Unit, modifier: Modifier = Modifier) {
    val photo = if (side == CompareSide.Left) state.left else state.right
    val label = stringResource(if (side == CompareSide.Left) Res.string.photos_pick_left else Res.string.photos_pick_right)
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppTheme.shapes.card)
                .background(AppTheme.colors.surface)
                .bounceClick(scaleDown = 0.98f, onClick = { onAction(CompareAction.PickerTapped(side)) })
                .semantics { contentDescription = label }
                .padding(horizontal = Dimension.D600, vertical = Dimension.D500),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = photo?.let { DateCopy.monthDay(it.date) }.orEmpty(),
                typography = AppTheme.type.cardTitle,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            Icon(icon = AppIcons.ArrowDropDown, size = Dimension.D900, color = AppTheme.colors.ink)
        }
        PopoverMenu(
            expanded = state.picker == side,
            onDismissRequest = { onAction(CompareAction.PickerDismissed) },
            alignment = if (side == CompareSide.Left) Alignment.TopStart else Alignment.TopEnd,
        ) {
            state.choices.forEachIndexed { index, choice ->
                MenuItem(
                    label = stringResource(Res.string.photos_choice, DateCopy.monthDay(choice.date), choice.pose.label()),
                    icon = if (choice.id == photo?.id) AppIcons.Check else null,
                    onClick = { onAction(CompareAction.PhotoPicked(side, choice.id)) },
                    showDivider = index != state.choices.lastIndex,
                )
            }
        }
    }
}

private const val SIDE_BY_SIDE = 0.6f
private const val SLIDER = 0.75f
private val HANDLE = 40.dp
private val HANDLE_LINE = 3.dp
