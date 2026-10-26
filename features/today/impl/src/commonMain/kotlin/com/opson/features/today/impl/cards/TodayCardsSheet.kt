package com.opson.features.today.impl.cards

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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.viewModelScope
import com.opson.features.today.impl.SummaryStat
import com.opson.features.today.impl.SummaryStats
import com.opson.features.today.impl.TodaySummaryEnabled
import com.opson.features.today.impl.Toast
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.NumbersChanger
import com.opson.libraries.diary.NumbersOutcome
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.fastingTimerOn
import com.opson.libraries.diary.ui.TodayCard
import com.opson.libraries.diary.ui.TodayCardPadding
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.MinTouchTarget
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.checkbox.SquareCheck
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.color.ToneColors
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.fasting_cards_body
import opson.libraries.resources.generated.resources.fasting_title
import opson.libraries.resources.generated.resources.today_cards_groups
import opson.libraries.resources.generated.resources.today_cards_groups_body
import opson.libraries.resources.generated.resources.today_cards_section
import opson.libraries.resources.generated.resources.today_cards_title
import opson.libraries.resources.generated.resources.today_cards_water
import opson.libraries.resources.generated.resources.today_cards_water_body
import opson.libraries.resources.generated.resources.today_stat_deficit_body
import opson.libraries.resources.generated.resources.today_stat_expended_body
import opson.libraries.resources.generated.resources.today_stat_hidden
import opson.libraries.resources.generated.resources.today_summary_card
import opson.libraries.resources.generated.resources.today_summary_done
import opson.libraries.resources.generated.resources.today_summary_move_down
import opson.libraries.resources.generated.resources.today_summary_move_up
import opson.libraries.resources.generated.resources.today_summary_page_count
import opson.libraries.resources.generated.resources.today_summary_page_one
import opson.libraries.resources.generated.resources.today_summary_page_two
import opson.libraries.resources.generated.resources.today_summary_page_two_body
import opson.libraries.resources.generated.resources.today_summary_reset
import opson.libraries.resources.generated.resources.today_summary_suggested
import opson.libraries.resources.generated.resources.today_summary_suggested_plain
import opson.libraries.resources.generated.resources.today_toast_saved
import opson.libraries.resources.generated.resources.today_toast_saved_fewer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * "Edit Today's cards", from the ⋮ menu or a long press on the summary card (design v3 2.11,
 * design sync 2 2.1e).
 *
 * With `release.todaySummary` the sheet opens on the summary card: page 1's stats, suggested from
 * the onboarding goals and then the person's own (tick, untick, drag to reorder, Reset), and the
 * macros page. Only what the levels allow can be ticked; a stat they hide shows a lock and opens
 * Modes and numbers, so this sheet never shows more numbers by itself.
 *
 * Under it, the cards: meal groups, and the water card, which moved here from the Day menu's
 * "Hide water". Hiding water applies at once through [NumbersChanger]. Showing it again is a
 * change that shows more, and v3 keeps those in Modes and numbers behind the pause, so turning the
 * switch on opens Modes. Fasting (0.2) moved here too: its card switch, only while fasting is
 * offered and its timer is on. [TodayCardsState.fasting] is null when the row is not there at all.
 */
data class TodayCardsState(
    val mealGroups: Boolean = true,
    val water: Boolean = true,
    val fasting: Boolean? = null,
    /** The summary card section (`release.todaySummary`); null with the flag off. */
    val summary: SummarySection? = null,
)

/** [goal] is the onboarding goal the suggestion came from ("Changing my weight"), null for the default. */
data class SummarySection(
    val goal: StringResource?,
    val stats: List<StatRow>,
    val ticked: Int,
    val macrosPage: Boolean,
)

/** One stat in the sheet: ticked or not, or [locked] by the levels. */
data class StatRow(val stat: SummaryStat, val ticked: Boolean, val locked: Boolean)

sealed interface TodayCardsAction {
    data class InputsArrived(val settings: Settings, val policy: VisibilityPolicy) : TodayCardsAction
    data object MealGroupsToggled : TodayCardsAction
    data object WaterToggled : TodayCardsAction
    data object FastingToggled : TodayCardsAction
    data class StatToggled(val stat: SummaryStat) : TodayCardsAction

    /** A drag (or Move up and Move down) took the row at [from] to [to] in the sheet's list. */
    data class StatMoved(val from: Int, val to: Int) : TodayCardsAction
    data object StatsReset : TodayCardsAction
    data object MacrosPageToggled : TodayCardsAction
    data object LockedStatTapped : TodayCardsAction
    data object CloseTapped : TodayCardsAction
}

sealed interface TodayCardsEvent {
    data class OpenPause(val change: NumbersChange) : TodayCardsEvent
    data object OpenModes : TodayCardsEvent
    data class ShowToast(val toast: Toast) : TodayCardsEvent
    data object Dismiss : TodayCardsEvent
}

object TodayCardsStateBuilder {
    fun build(
        settings: Settings,
        policy: VisibilityPolicy,
        fastingReleased: Boolean = false,
        summaryOn: Boolean = false,
    ): TodayCardsState =
        TodayCardsState(
            mealGroups = settings.mealGroups,
            water = policy.level(Metric.Water) != Visibility.Hide,
            fasting = settings.fastToday.takeIf { settings.fastingTimerOn(fastingReleased) },
            summary = if (summaryOn) summary(settings, policy) else null,
        )

    private fun summary(settings: Settings, policy: VisibilityPolicy): SummarySection {
        val chosen = SummaryStats.chosen(settings).filter { SummaryStats.allowed(it, policy) }.toSet()
        val rows = SummaryStats.listed(settings, policy).map { stat ->
            val locked = !SummaryStats.allowed(stat, policy)
            StatRow(stat = stat, ticked = !locked && stat in chosen, locked = locked)
        }
        return SummarySection(
            goal = SummaryStats.goalLabel(settings),
            stats = rows,
            ticked = rows.count { it.ticked },
            macrosPage = settings.todayMacrosPage,
        )
    }
}

@Inject
class TodayCardsViewModel(
    private val settingsRepository: SettingsRepository,
    private val policySource: VisibilityPolicySource,
    private val numbersChanger: NumbersChanger,
    private val fastingRelease: FastingRelease,
    private val summaryEnabled: TodaySummaryEnabled,
) : SEAViewModel<TodayCardsState, TodayCardsEvent, TodayCardsAction>(initialStateArg = TodayCardsState()) {

    init {
        viewModelScope.launch {
            combine(settingsRepository.settings, policySource.policy) { s, p -> TodayCardsAction.InputsArrived(s, p) }.collect(::takeAction)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun handleAction(action: TodayCardsAction) {
        when (action) {
            is TodayCardsAction.InputsArrived -> action.updateState {
                TodayCardsStateBuilder.build(action.settings, action.policy, fastingRelease.isOn(), summaryEnabled())
            }
            TodayCardsAction.FastingToggled -> if (settingsRepository.settings.value.fastingTimerOn(fastingRelease.isOn())) {
                settingsRepository.update { it.copy(fastToday = !it.fastToday) }
            }
            TodayCardsAction.MealGroupsToggled -> settingsRepository.update { it.copy(mealGroups = !it.mealGroups) }
            TodayCardsAction.WaterToggled -> waterToggled()
            is TodayCardsAction.StatToggled -> statToggled(action.stat)
            is TodayCardsAction.StatMoved -> statMoved(action.from, action.to)
            TodayCardsAction.StatsReset -> settingsRepository.update { it.copy(todaySummaryStats = null) }
            TodayCardsAction.MacrosPageToggled -> settingsRepository.update { it.copy(todayMacrosPage = !it.todayMacrosPage) }
            TodayCardsAction.LockedStatTapped -> sendEvent(TodayCardsEvent.OpenModes)
            TodayCardsAction.CloseTapped -> sendEvent(TodayCardsEvent.Dismiss)
        }
    }

    private suspend fun waterToggled() {
        if (policySource.policy.value.level(Metric.Water) == Visibility.Hide) {
            sendEvent(TodayCardsEvent.OpenModes)
            return
        }
        val change = NumbersChange.Override(Metric.Water, Visibility.Hide)
        when (val outcome = numbersChanger.request(change)) {
            NumbersOutcome.NeedsPause -> sendEvent(TodayCardsEvent.OpenPause(change))
            is NumbersOutcome.Applied -> sendEvent(
                TodayCardsEvent.ShowToast(Toast(if (outcome.fewer) Res.string.today_toast_saved_fewer else Res.string.today_toast_saved)),
            )
        }
    }

    /** Ticks or unticks [stat]; a fourth tick does nothing, page 1 has room for three. A locked stat never ticks. */
    private suspend fun statToggled(stat: SummaryStat) {
        val policy = policySource.policy.value
        if (!SummaryStats.allowed(stat, policy)) return
        settingsRepository.update { settings ->
            val chosen = SummaryStats.chosen(settings).filter { SummaryStats.allowed(it, policy) }
            val next = when {
                stat in chosen -> chosen - stat
                chosen.size < SummaryStats.MAX -> chosen + stat
                else -> return@update settings
            }
            settings.copy(todaySummaryStats = next.map { it.wire })
        }
    }

    /** Moves the row at [from] to [to]; what is ticked keeps its new order. */
    private suspend fun statMoved(from: Int, to: Int) {
        val policy = policySource.policy.value
        settingsRepository.update { settings ->
            val listed = SummaryStats.listed(settings, policy).toMutableList()
            if (from !in listed.indices || to !in listed.indices || from == to) return@update settings
            listed.add(to, listed.removeAt(from))
            val chosen = SummaryStats.chosen(settings).filter { SummaryStats.allowed(it, policy) }.toSet()
            settings.copy(todaySummaryStats = listed.filter { it in chosen }.map { it.wire })
        }
    }
}

@Composable
fun TodayCardsSheetContent(
    state: TodayCardsState,
    onAction: (TodayCardsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        val summary = state.summary
        if (summary == null) {
            SheetHeader(title = stringResource(Res.string.today_cards_title), onClose = { onAction(TodayCardsAction.CloseTapped) })
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(Res.string.today_summary_card), typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
                FlatButton(
                    text = stringResource(Res.string.today_summary_done),
                    onClick = { onAction(TodayCardsAction.CloseTapped) },
                    tone = FlatButtonTone.AccentText,
                    typography = AppTheme.type.button,
                )
            }
            SummarySectionContent(summary, onAction)
            Text(text = stringResource(Res.string.today_cards_section), typography = AppTheme.type.secondaryStrong.Bold, color = AppTheme.colors.muted)
        }
        CardsCard(state, onAction)
    }
}

@Composable
private fun SummarySectionContent(summary: SummarySection, onAction: (TodayCardsAction) -> Unit) {
    SuggestionBanner(summary, onAction)
    TodayCard(contentPadding = TodayCardPadding.Checklist, verticalArrangement = Arrangement.Top) {
        Row(Modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.row), verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(Res.string.today_summary_page_one), typography = AppTheme.type.bodyBold, color = AppTheme.colors.inkSoft, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.today_summary_page_count, summary.ticked, SummaryStats.MAX),
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.muted,
                maxLines = 1,
            )
        }
        var rowHeight by remember { mutableIntStateOf(0) }
        summary.stats.forEachIndexed { index, row ->
            HorizontalDivider(color = AppTheme.colors.line)
            StatRowItem(
                row = row,
                index = index,
                lastIndex = summary.stats.lastIndex,
                rowHeight = rowHeight,
                onAction = onAction,
                modifier = Modifier.onSizeChanged { rowHeight = it.height },
            )
        }
    }
    TodayCard(contentPadding = TodayCardPadding.List, verticalArrangement = Arrangement.Top) {
        ListRow(
            title = stringResource(Res.string.today_summary_page_two),
            subtitle = stringResource(Res.string.today_summary_page_two_body),
        ) {
            AppSwitch(checked = summary.macrosPage, onCheckedChange = { onAction(TodayCardsAction.MacrosPageToggled) })
        }
    }
}

/** "Suggested from your goals: Changing my weight" and Reset, which goes back to the suggestion. */
@Composable
private fun SuggestionBanner(summary: SummarySection, onAction: (TodayCardsAction) -> Unit) {
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
            text = summary.goal?.let { stringResource(Res.string.today_summary_suggested, stringResource(it)) }
                ?: stringResource(Res.string.today_summary_suggested_plain),
            typography = AppTheme.type.bodyBold,
            color = AppTheme.colors.accent,
            modifier = Modifier.weight(1f),
        )
        // Always offered, as the design draws it; with nothing chosen it simply keeps the suggestion.
        Text(
            text = stringResource(Res.string.today_summary_reset),
            typography = AppTheme.type.bodyBold.ExtraBold,
            color = AppTheme.colors.accent,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .semantics { role = Role.Button }
                .bounceClick(scaleDown = 0.95f, onClick = { onAction(TodayCardsAction.StatsReset) })
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
    onAction: (TodayCardsAction) -> Unit,
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
    val onTap = { onAction(if (row.locked) TodayCardsAction.LockedStatTapped else TodayCardsAction.StatToggled(row.stat)) }
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
                    CustomAccessibilityAction(moveUp) { onAction(TodayCardsAction.StatMoved(index, index - 1)); true }.takeIf { index > 0 },
                    CustomAccessibilityAction(moveDown) { onAction(TodayCardsAction.StatMoved(index, index + 1)); true }.takeIf { index < lastIndex },
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
                            if (to != index) onAction(TodayCardsAction.StatMoved(index, to))
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

@Composable
private fun CardsCard(state: TodayCardsState, onAction: (TodayCardsAction) -> Unit) {
    TodayCard(contentPadding = TodayCardPadding.List, verticalArrangement = Arrangement.Top) {
        ListRow(
            title = stringResource(Res.string.today_cards_groups),
            subtitle = stringResource(Res.string.today_cards_groups_body),
            icon = AppIcons.ViewAgenda,
            iconTone = ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint),
        ) {
            AppSwitch(checked = state.mealGroups, onCheckedChange = { onAction(TodayCardsAction.MealGroupsToggled) })
        }
        HorizontalDivider(color = AppTheme.colors.line)
        ListRow(
            title = stringResource(Res.string.today_cards_water),
            subtitle = stringResource(Res.string.today_cards_water_body),
            icon = AppIcons.WaterDrop,
            iconTone = AppTheme.colors.nutrients.water,
        ) {
            AppSwitch(checked = state.water, onCheckedChange = { onAction(TodayCardsAction.WaterToggled) })
        }
        state.fasting?.let { on ->
            HorizontalDivider(color = AppTheme.colors.line)
            ListRow(
                title = stringResource(Res.string.fasting_title),
                subtitle = stringResource(Res.string.fasting_cards_body),
                icon = AppIcons.Schedule,
                iconTone = AppTheme.colors.nutrients.fiber,
            ) {
                AppSwitch(checked = on, onCheckedChange = { onAction(TodayCardsAction.FastingToggled) })
            }
        }
    }
}
