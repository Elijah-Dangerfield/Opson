package com.opson.libraries.ui.system

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The smallest touch target any control may have (design v3: "Touch targets are at least 48 dp"). */
val MinTouchTarget: Dp = 48.dp

/**
 * Grows this element's layout to at least [size] in both directions, with the content centred
 * and drawn at its own size. Put it first in a control's chain, before the size, background and
 * click modifiers, so a 40 dp chip or a 30 dp switch keeps its look and still takes a 48 dp tap.
 *
 * Unlike Material's `minimumInteractiveComponentSize`, this one is unconditional: our buttons
 * turn Material's enforcement off for the Material pieces inside them, and that must not shrink
 * the button's own target.
 */
fun Modifier.minTouchTarget(size: Dp = MinTouchTarget): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val min = size.roundToPx()
    val width = maxOf(placeable.width, min).coerceIn(constraints.minWidth, constraints.maxWidth)
    val height = maxOf(placeable.height, min).coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(width, height) {
        placeable.place((width - placeable.width) / 2, (height - placeable.height) / 2)
    }
}
