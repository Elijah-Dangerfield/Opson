package com.opson.libraries.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.dropShadow
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The card the Add and Day menus are drawn on: 268dp wide, surface, radius 18, the popover
 * shadow. Fill it with [MenuItem]s. Use [PopoverMenu] to show it anchored to a button.
 */
@Composable
fun MenuPopover(
    modifier: Modifier = Modifier,
    width: Dp = 268.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = AppTheme.shapes.popover
    Column(
        modifier = modifier
            .width(width)
            .dropShadow(AppTheme.shadows.popover, shape)
            .clip(shape)
            .background(AppTheme.colors.surface)
            .padding(vertical = 6.dp),
        content = content,
    )
}

/**
 * One menu row: label on the left, icon on the right, a line under it. [showDivider] should be
 * false on the last row. [labelColor] lets a two-step action ("Tap again to clear") stand out.
 */
@Composable
fun MenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconResource? = null,
    iconColor: ColorResource = AppTheme.colors.accent,
    labelColor: ColorResource = AppTheme.colors.ink,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .bounceClick(scaleDown = 0.98f, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Text(text = label, typography = AppTheme.type.menuItem, color = labelColor, modifier = Modifier.weight(1f))
            if (icon != null) Icon(icon = icon, size = 22.dp, color = iconColor)
        }
        if (showDivider) HorizontalDivider(color = AppTheme.colors.line)
    }
}

object MenuPopoverDefaults {
    /** Today's ⋮ menu, whose longest row is "Copy this day to tomorrow". */
    val WideWidth: Dp = 320.dp
}

/**
 * A menu row with the icon first (design v3, 2.11): the icon in [iconColor], then the label, 56
 * tall, no line. Groups are split with [MenuGroupDivider].
 */
@Composable
fun MenuIconItem(
    label: String,
    icon: IconResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconColor: ColorResource = AppTheme.colors.accent,
    labelColor: ColorResource = AppTheme.colors.ink,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .bounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon = icon, size = 24.dp, color = iconColor)
        Text(text = label, typography = AppTheme.type.bodyBold, color = labelColor, modifier = Modifier.weight(1f))
    }
}

/** The full-width line between groups of [MenuIconItem]s. */
@Composable
fun MenuGroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(vertical = 6.dp), color = AppTheme.colors.line)
}

/**
 * Shows a [MenuPopover] below and end-aligned to the composable that hosts it, so place this
 * inside the same `Box` as the button that opens it. Dismisses on an outside tap or back.
 * [alignment] `TopStart` hangs it from the start instead (the logger's meal dropdown).
 *
 * It grows out of the corner it hangs from and shrinks back into it the same way, however it
 * closes: an outside tap, back, or a row that sets [expanded] false. The popup stays composed
 * until that exit has played, so [content] must still draw once [expanded] is false; a menu whose
 * rows come from nullable state wants the other overload.
 *
 * The outside tap is caught by a window-sized layer of its own under the menu, not by the
 * popup's dismiss-on-outside-click. On iOS that event never arrives for a popup opened inside a
 * bottom sheet, which left the logger's meal dropdown open whatever was tapped around it.
 */
@Composable
fun PopoverMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: IntOffset = IntOffset.Zero,
    alignment: Alignment = Alignment.TopEnd,
    width: Dp = 268.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A preview has no frames to animate in, so one drawn open starts open.
    val inPreview = LocalInspectionMode.current
    val visibility = remember { MutableTransitionState(inPreview && expanded) }
    visibility.targetState = expanded
    if (!visibility.currentState && !visibility.targetState) return

    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val layoutDirection = LocalLayoutDirection.current
    val origin = remember(alignment, layoutDirection) { alignment.transformOrigin(layoutDirection) }
    // Gone as soon as the menu is on its way out, so the next tap reaches the screen again.
    // Composed first, so it sits under the menu and only gets the taps that miss it.
    if (visibility.targetState && !inPreview) {
        OutsideTapCatcher(onDismiss = { if (visibility.targetState) currentOnDismissRequest() })
    }
    Popup(
        alignment = alignment,
        offset = offset,
        // Not focusable: a focusable popup takes every touch outside it as well, on iOS, and
        // the catcher underneath would never see one. Back and outside taps are the catcher's.
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(PopoverMotion) + scaleIn(PopoverMotion, initialScale = POPOVER_HIDDEN_SCALE, transformOrigin = origin),
            exit = fadeOut(PopoverMotion) + scaleOut(PopoverMotion, targetScale = POPOVER_HIDDEN_SCALE, transformOrigin = origin),
        ) {
            // Room for the shadow, which a popup window would otherwise clip.
            MenuPopover(modifier = modifier.padding(start = 24.dp, end = 12.dp, bottom = 40.dp), width = width, content = content)
        }
    }
}

/**
 * A transparent popup over the whole window that closes the menu on the first press anywhere
 * outside it, or on back. The press goes no further, as with any menu: tapping outside closes
 * the menu and does nothing else.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun OutsideTapCatcher(onDismiss: () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    Popup(
        popupPositionProvider = WindowOrigin,
        onDismissRequest = { dismiss() },
        properties = windowCoveringPopupProperties(),
    ) {
        // iOS's back swipe reaches the layer on top, this one, and a popup has no back handling of
        // its own there. Android's back goes to the focused popup window, which is also this one.
        BackHandler { dismiss() }
        Box(
            Modifier
                .fillMaxSize()
                .testTag(POPOVER_OUTSIDE_TAG)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        dismiss()
                    }
                },
        )
    }
}

private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

/** What a composition test taps to land outside an open [PopoverMenu]. */
internal const val POPOVER_OUTSIDE_TAG = "popover-outside"

/**
 * Focusable, so it takes back and the outside taps, and as large as the window, safe areas
 * included, so a tap anywhere around the menu lands on it.
 */
internal expect fun windowCoveringPopupProperties(): PopupProperties

/**
 * [PopoverMenu] for a menu whose rows come from state that is null while it is closed, as
 * Today's ⋮ menu is. Open while [menu] is set; the last [menu] keeps drawing while the popover
 * animates away, so it does not shrink as an empty card.
 */
@Composable
fun <T : Any> PopoverMenu(
    menu: T?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: IntOffset = IntOffset.Zero,
    alignment: Alignment = Alignment.TopEnd,
    width: Dp = 268.dp,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    // A plain holder rather than state: remembering the last menu must not schedule a recomposition.
    val last = remember { LastMenu<T>() }
    if (menu != null) last.value = menu
    PopoverMenu(
        expanded = menu != null,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        alignment = alignment,
        width = width,
    ) {
        last.value?.let { content(it) }
    }
}

private class LastMenu<T : Any> {
    var value: T? = null
}

/** The corner [this] hangs a popover from, as a scale pivot: `TopEnd` is the top right in LTR. */
private fun Alignment.transformOrigin(layoutDirection: LayoutDirection): TransformOrigin {
    val corner = align(IntSize.Zero, IntSize(2, 2), layoutDirection)
    return TransformOrigin(corner.x / 2f, corner.y / 2f)
}

/** The same curve both ways, so closing reads as opening played backwards. */
private val PopoverMotion = tween<Float>(durationMillis = 160, easing = FastOutSlowInEasing)
private const val POPOVER_HIDDEN_SCALE = 0.9f

@Preview(widthDp = 390)
@Composable
private fun MenuPopoverPreview() {
    PreviewLightDark {
        MenuPopover(modifier = Modifier.padding(bottom = 24.dp)) {
            MenuItem(label = "Food", icon = AppIcons.Restaurant, onClick = {})
            MenuItem(label = "Scan a barcode", icon = AppIcons.BarcodeScanner, onClick = {})
            MenuItem(label = "Glass of water", icon = AppIcons.WaterDrop, onClick = {})
            MenuItem(label = "Note", icon = AppIcons.EditNote, onClick = {}, showDivider = false)
        }
    }
}
