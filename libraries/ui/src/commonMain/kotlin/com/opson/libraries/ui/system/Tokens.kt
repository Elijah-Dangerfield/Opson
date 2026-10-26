@file:Suppress("MagicNumber")

package com.opson.system

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Corner shapes from the handoff. Read through `AppTheme.shapes`. */
@Immutable
class AppShapes internal constructor() {
    /** Edge-to-edge cards. */
    val card: Shape = RoundedCornerShape(22.dp)

    /** Grouped lists inside a sheet (settings toggles, the meal menu). */
    val groupedList: Shape = RoundedCornerShape(20.dp)

    /** Bottom sheets: top corners only. */
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** The sticky colored block on Today: bottom corners only. */
    val heroBlock: Shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)

    /** Popover menus. */
    val popover: Shape = RoundedCornerShape(18.dp)

    /** Text inputs, small buttons, the segmented control track. */
    val input: Shape = RoundedCornerShape(12.dp)

    /** Search field, primary buttons, stepper buttons, banners. */
    val inputLarge: Shape = RoundedCornerShape(14.dp)

    /** Icon tiles at the default 38dp and larger. */
    val iconTile: Shape = RoundedCornerShape(12.dp)

    /** The selected thumb inside a segmented control. */
    val segmentThumb: Shape = RoundedCornerShape(10.dp)

    /** Chips, pills and round buttons. */
    val pill: Shape = RoundedCornerShape(percent = 50)

    /** Progress and segment bars. */
    val bar: Shape = RoundedCornerShape(3.dp)
}

/** Spacing from the handoff. Read through `AppTheme.spacing`. */
@Immutable
class AppSpacing internal constructor() {
    /** Horizontal padding of every page. */
    val page: Dp = 20.dp

    /** Gap between sections on a page. */
    val section: Dp = 14.dp

    /** Card content padding. */
    val cardHorizontal: Dp = 20.dp
    val cardVertical: Dp = 16.dp

    /** Gap between an icon tile and its text, and between row items. */
    val row: Dp = 12.dp

    /** Gap between chips. */
    val chip: Dp = 6.dp

    /** Minimum touch target: 48 dp in design v3, everywhere. Smaller visuals keep this much tappable area. */
    val touchTarget: Dp = 48.dp

    /** Round header and row buttons that the handoff draws at 40. */
    val compactTouchTarget: Dp = 40.dp
}

/** A CSS-style drop shadow: vertical offset, blur and color. */
@Immutable
data class ShadowToken(
    val offsetY: Dp,
    val blur: Dp,
    val color: Color,
)

/** Shadows from the handoff. Read through `AppTheme.shadows`. */
@Immutable
class AppShadows internal constructor() {
    /** Sticky Today block: `0 8px 20px rgba(30,15,40,.14)`. */
    val heroBlock = ShadowToken(offsetY = 8.dp, blur = 20.dp, color = Color(0x241E0F28))

    /** Popover menus: `0 14px 40px rgba(10,12,20,.25)`. */
    val popover = ShadowToken(offsetY = 14.dp, blur = 40.dp, color = Color(0x400A0C14))

    /** The raised center + button: `0 8px 20px rgba(20,20,30,.28)`. */
    val raised = ShadowToken(offsetY = 8.dp, blur = 20.dp, color = Color(0x4714141E))
}

/** Draws [token] behind the content, offset and blurred like the CSS it comes from. */
@Stable
fun Modifier.dropShadow(token: ShadowToken, shape: Shape): Modifier = dropShadow(
    shape = shape,
    shadow = Shadow(
        radius = token.blur,
        color = token.color,
        offset = DpOffset(x = 0.dp, y = token.offsetY),
    ),
)

internal val DefaultShapes = AppShapes()
internal val DefaultSpacing = AppSpacing()
internal val DefaultShadows = AppShadows()
