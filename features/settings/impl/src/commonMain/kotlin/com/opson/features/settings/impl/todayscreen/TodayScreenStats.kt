package com.opson.features.settings.impl.todayscreen

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SettingsCard
import com.opson.libraries.diary.ui.SummaryStat
import com.opson.libraries.diary.ui.SummaryStats
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.checkbox.SquareCheck
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_stat_deficit_body
import opson.libraries.resources.generated.resources.today_stat_expended_body
import opson.libraries.resources.generated.resources.today_stat_hidden
import opson.libraries.resources.generated.resources.today_summary_move_down
import opson.libraries.resources.generated.resources.today_summary_move_up
import opson.libraries.resources.generated.resources.today_summary_page_count
import opson.libraries.resources.generated.resources.today_summary_page_one
import opson.libraries.resources.generated.resources.today_summary_reset
import opson.libraries.resources.generated.resources.today_summary_suggested
import opson.libraries.resources.generated.resources.today_summary_suggested_plain
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/*
 * The older summary card's stats on More › Today screen (design sync 2, 2.1e), which Edit Today's
 * cards used to carry: the suggestion from the onboarding goals with Reset, then page 1's stats to
 * tick, untick and drag into order. Only what the levels allow can be ticked; a stat they hide
 * shows a lock and opens Display, so this never shows more numbers by itself.
 */

@Composable
internal fun StatsContent(stats: StatsSection, onAction: (TodayScreenSettingsAction) -> Unit) {
    SuggestionBanner(stats, onAction)
    SettingsCard {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.today_summary_page_one),
                typography = AppTheme.type.bodyBold,
                color = AppTheme.colors.inkSoft,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.today_summary_page_count, stats.ticked, SummaryStats.MAX),
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.muted,
                maxLines = 1,
            )
        }
        var rowHeight by remember { mutableIntStateOf(0) }
        stats.rows.forEachIndexed { index, row ->
            HorizontalDivider(color = AppTheme.colors.line)
            StatRowItem(
                row = row,
                index = index,
                lastIndex = stats.rows.lastIndex,
                rowHeight = rowHeight,
                onAction = onAction,
                modifier = Modifier.onSizeChanged { rowHeight = it.height },
            )
        }
    }
}

/** "Suggested from your goals: Changing my weight" and Reset, which goes back to the suggestion. */
@Composable
private fun SuggestionBanner(stats: StatsSection, onAction: (TodayScreenSettingsAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.inputLarge)
            .background(AppTheme.colors.accentTint)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .defaultMinSize(minHeight = MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon = AppIcons.AutoAwesome, size = 22.dp, color = AppTheme.colors.accent)
        Text(
            text = stats.goal?.let { stringResource(Res.string.today_summary_suggested, stringResource(it)) }
                ?: stringResource(Res.string.today_summary_suggested_plain),
            typography = AppTheme.type.bodyBold,
            color = AppTheme.colors.accent,
            modifier = Modifier.weight(1f),
        )
        // Always offered; with nothing chosen it simply keeps the suggestion.
        Text(
            text = stringResource(Res.string.today_summary_reset),
            typography = AppTheme.type.bodyBold.ExtraBold,
            color = AppTheme.colors.accent,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .semantics { role = Role.Button }
                .bounceClick(scaleDown = 0.95f, onClick = { onAction(TodayScreenSettingsAction.StatsReset) })
                .defaultMinSize(minHeight = MinTouchTarget)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        )
    }
}

/**
 * One stat: the drag handle, its name and a line under it, and the tick, or a lock when the levels
 * hide it. Dragging the handle moves it a row per row height; Move up and Move down do the same
 * for TalkBack and VoiceOver.
 */
@Composable
private fun StatRowItem(
    row: StatRow,
    index: Int,
    lastIndex: Int,
    rowHeight: Int,
    onAction: (TodayScreenSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableFloatStateOf(0f) }
    val moveUp = stringResource(Res.string.today_summary_move_up)
    val moveDown = stringResource(Res.string.today_summary_move_down)
    val hidden = stringResource(Res.string.today_stat_hidden)
    val subtitle = when {
        row.locked -> hidden
        row.stat == SummaryStat.Expended -> stringResource(Res.string.today_stat_expended_body)
        row.stat == SummaryStat.DeficitSoFar -> stringResource(Res.string.today_stat_deficit_body)
        else -> null
    }
    val onTap = {
        onAction(if (row.locked) TodayScreenSettingsAction.LinkTapped(SettingLink.Display) else TodayScreenSettingsAction.StatToggled(row.stat))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = drag }
            .semantics(mergeDescendants = true) {
                role = Role.Checkbox
                if (row.locked) {
                    stateDescription = hidden
                } else {
                    toggleableState = if (row.ticked) ToggleableState.On else ToggleableState.Off
                }
                customActions = listOfNotNull(
                    CustomAccessibilityAction(moveUp) { onAction(TodayScreenSettingsAction.StatMoved(index, index - 1)); true }.takeIf { index > 0 },
                    CustomAccessibilityAction(moveDown) { onAction(TodayScreenSettingsAction.StatMoved(index, index + 1)); true }.takeIf { index < lastIndex },
                )
            }
            .bounceClick(scaleDown = 0.99f, onClick = onTap)
            .defaultMinSize(minHeight = 64.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = MinTouchTarget)
                .semantics { hideFromAccessibility() }
                .pointerInput(index, rowHeight) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            val steps = if (rowHeight > 0) (drag / rowHeight).roundToInt() else 0
                            drag = 0f
                            val to = (index + steps).coerceIn(0, lastIndex)
                            if (to != index) onAction(TodayScreenSettingsAction.StatMoved(index, to))
                        },
                        onDragCancel = { drag = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            drag += amount
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon = AppIcons.DragIndicator, size = 24.dp, color = if (row.locked) AppTheme.colors.line else AppTheme.colors.muted)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(row.stat.label),
                typography = AppTheme.type.cardTitle,
                color = if (row.locked) AppTheme.colors.muted else AppTheme.colors.ink,
            )
            if (subtitle != null) Text(text = subtitle, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        if (row.locked) {
            Icon(icon = AppIcons.LockOutlined, size = 24.dp, color = AppTheme.colors.muted, modifier = Modifier.padding(end = 6.dp))
        } else {
            SquareCheck(checked = row.ticked)
        }
    }
}
