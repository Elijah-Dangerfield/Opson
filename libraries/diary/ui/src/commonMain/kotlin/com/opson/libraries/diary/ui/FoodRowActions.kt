package com.opson.libraries.diary.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.currentVisibilityPolicy
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.MinTouchTarget
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.checkbox.SquareCheck
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

/*
 * What a food row on Today can do besides open (design sync 2, 2.12 to 2.14): swipe left to
 * reveal Delete, turn into an inline "Remove …?" in place, and show a tick while foods are being
 * selected. The rows only draw; whether a row is open, confirming or picked is the screen's state.
 */

/** The name, its line under it and the energy on the right, the body every food row shares. */
@Composable
private fun FoodRowBody(name: String, sub: String, calories: Display, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, typography = AppTheme.type.body, color = AppTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(text = sub, typography = AppTheme.type.secondarySmall.Tabular, color = AppTheme.colors.muted)
        }
        NutrientText(metric = Metric.Calories, display = calories, typography = AppTheme.type.numericSmall, color = AppTheme.colors.ink, maxLines = 1)
    }
}

/**
 * A food that swipes left to reveal [deleteLabel] (2.13). A short swipe stops with the button
 * showing ([onOpened]); past half the row goes straight to [onDelete], the confirmation; a swipe
 * back closes it ([onClosed]). [open] says where it rests, so one row open at a time is the
 * screen's to keep. The button is out of the accessibility tree: the row offers [deleteLabel] as
 * a custom action instead, which TalkBack, Switch Access and VoiceOver all list.
 *
 * [onLongClick] starts selecting (2.12); null when that is off.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeFoodRow(
    name: String,
    sub: String,
    calories: Display,
    open: Boolean,
    deleteLabel: String,
    onClick: () -> Unit,
    onOpened: () -> Unit,
    onClosed: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    swipeEnabled: Boolean = true,
) {
    val density = LocalDensity.current
    val reveal = with(density) { SwipeReveal.toPx() }
    var width by remember { mutableIntStateOf(0) }
    var drag by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(open) {
        val target = if (open) -reveal else 0f
        if (drag != target) animate(drag, target) { value, _ -> drag = value }
    }
    val dragState = rememberDraggableState { delta -> drag = (drag + delta).coerceIn(-width.toFloat(), 0f) }
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = AppTheme.colors.line)
        Box(Modifier.fillMaxWidth().clipToBounds().onSizeChanged { width = it.width }) {
            Row(
                modifier = Modifier.matchParentSize().semantics { hideFromAccessibility() },
                horizontalArrangement = Arrangement.End,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(SwipeReveal)
                        .background(AppTheme.colors.ink)
                        .bounceClick(scaleDown = 0.98f, onClick = onDelete),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon = AppIcons.Delete, size = 24.dp, color = AppTheme.colors.bg)
                    Text(text = deleteLabel, typography = AppTheme.type.secondaryStrong.Bold, color = AppTheme.colors.bg)
                }
            }
            FoodRowBody(
                name = name,
                sub = sub,
                calories = calories,
                modifier = Modifier
                    .offset { IntOffset(drag.roundToInt(), 0) }
                    .fillMaxWidth()
                    .background(AppTheme.colors.surface)
                    .defaultMinSize(minHeight = MinTouchTarget)
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        enabled = swipeEnabled,
                        onDragStopped = {
                            scope.launch {
                                when {
                                    drag < -width / 2f -> {
                                        animate(drag, 0f) { value, _ -> drag = value }
                                        onDelete()
                                    }
                                    drag < -reveal / 2f -> {
                                        animate(drag, -reveal) { value, _ -> drag = value }
                                        onOpened()
                                    }
                                    else -> {
                                        animate(drag, 0f) { value, _ -> drag = value }
                                        onClosed()
                                    }
                                }
                            }
                        },
                    )
                    .combinedClickable(
                        onClick = { if (open) onClosed() else onClick() },
                        onLongClick = onLongClick,
                        onLongClickLabel = longClickLabel,
                    )
                    .semantics {
                        if (swipeEnabled) customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
                    }
                    .padding(PaddingValues(vertical = AppTheme.spacing.row)),
            )
        }
    }
}

/**
 * A food while foods are being selected (2.12): the tick, the food, tinted when [selected]. The
 * whole row toggles.
 */
@Composable
fun SelectableFoodRow(
    name: String,
    sub: String,
    calories: Display,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(SelectShape)
            .background(if (selected) AppTheme.colors.accentTint.withAlpha(SELECTED_TINT_ALPHA) else AppTheme.colors.surface)
            .semantics {
                role = Role.Checkbox
                this.selected = selected
                toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
            }
            .bounceClick(scaleDown = 0.99f, onClick = onToggle)
            .defaultMinSize(minHeight = MinTouchTarget)
            .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        SquareCheck(checked = selected)
        FoodRowBody(name = name, sub = sub, calories = calories, modifier = Modifier.weight(1f))
    }
}

/**
 * A row turned into its own confirmation (2.14): [question] with [keepLabel] and [removeLabel],
 * in place, so nothing else on the page moves. Also the select panel's "Remove 3 foods?".
 */
@Composable
fun RemoveConfirmRow(
    question: String,
    keepLabel: String,
    removeLabel: String,
    onKeep: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(AppTheme.shapes.inputLarge)
            .background(AppTheme.colors.soft)
            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = question, typography = AppTheme.type.bodyBold.ExtraBold, color = AppTheme.colors.ink)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            FlatButton(text = keepLabel, onClick = onKeep, tone = FlatButtonTone.AccentText, centered = true, typography = AppTheme.type.button)
            FlatButton(
                text = removeLabel,
                onClick = onRemove,
                tone = FlatButtonTone.Ink,
                centered = true,
                shape = CircleShape,
                minHeight = FlatButtonDefaults.TallMinHeight,
                typography = AppTheme.type.button,
            )
        }
    }
}

/** How far a short swipe opens a row: the Delete button's width. */
val SwipeReveal = 96.dp

private val SelectShape = RoundedCornerShape(16.dp)
private const val SELECTED_TINT_ALPHA = 0.55f

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun FoodRowActionsPreview() {
    PreviewLightDark {
        val policy = currentVisibilityPolicy()
        Column(Modifier.background(AppTheme.colors.surface).padding(16.dp)) {
            SwipeFoodRow(
                name = "Banana",
                sub = "1 medium · Estimate",
                calories = policy.resolve(Metric.Calories, 105.0, Surface.Today),
                open = true,
                deleteLabel = "Delete",
                onClick = {},
                onOpened = {},
                onClosed = {},
                onDelete = {},
            )
            RemoveConfirmRow(question = "Remove banana from breakfast?", keepLabel = "Keep", removeLabel = "Remove", onKeep = {}, onRemove = {})
            SelectableFoodRow(
                name = "Oat porridge with milk",
                sub = "1 bowl · Weighed",
                calories = policy.resolve(Metric.Calories, 300.0, Surface.Today),
                selected = true,
                onToggle = {},
            )
            SelectableFoodRow(
                name = "Coffee with milk",
                sub = "1 mug · Estimate",
                calories = policy.resolve(Metric.Calories, 40.0, Surface.Today),
                selected = false,
                onToggle = {},
            )
        }
    }
}
