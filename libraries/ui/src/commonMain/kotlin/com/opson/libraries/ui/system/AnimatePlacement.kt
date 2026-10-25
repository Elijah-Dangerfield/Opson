package com.opson.libraries.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch

/**
 * Slides this element whenever its parent places it somewhere new, instead of letting it teleport.
 *
 * It knows nothing about *why* the element moved: a changed alignment, a reordered row, a tooltip
 * following a new anchor all look the same from here. Put it on the thing that should travel and
 * let whatever positions it stay a plain, synchronous layout.
 *
 * That split is the whole point. Animating inside the positioning component is the tempting version
 * and it goes wrong the same way every time: an animation needs a coroutine, a layout pass cannot
 * start one, so the placement maths gets dragged up into composition where the content's measured
 * size is not known yet — and out fall a sentinel for "not measured yet", a hidden first frame, and
 * a measure → state → layout loop.
 *
 * Two details that are easy to get wrong:
 *
 * - **The first placement snaps.** There is nowhere to travel from, and springing in from the
 *   parent's origin is an entrance animation nobody asked for. [Animatable] starts at the first
 *   placement, so the offset it yields on that pass is exactly zero.
 * - **The spring is read inside `offset { }`, never in composition.** A `by animateTo…` read in a
 *   composable body would recompose this whole subtree on every frame of the travel. Read from the
 *   layout lambda, the movement invalidates placement only.
 */
@Composable
fun Modifier.animatePlacement(
    animationSpec: FiniteAnimationSpec<IntOffset> = spring(
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    ),
): Modifier {
    val scope = rememberCoroutineScope()
    var placedAt by remember { mutableStateOf(IntOffset.Zero) }
    var travel by remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }

    return this
        .onPlaced { placedAt = it.positionInParent().round() }
        .offset {
            val animation = travel
                ?: Animatable(placedAt, IntOffset.VectorConverter).also { travel = it }
            if (animation.targetValue != placedAt) {
                scope.launch { animation.animateTo(placedAt, animationSpec) }
            }
            // The parent has already placed us at `placedAt`; offsetting by the difference draws
            // the element where the spring has got to so far, and zero once it arrives.
            animation.value - placedAt
        }
}
