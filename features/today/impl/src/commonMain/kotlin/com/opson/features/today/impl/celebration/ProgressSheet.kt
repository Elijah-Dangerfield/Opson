package com.opson.features.today.impl.celebration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.CelebrationStyle
import com.opson.libraries.diary.Milestone
import com.opson.libraries.diary.StreakDay
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.TodayCard
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.background
import androidx.compose.foundation.border
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.celebrate_more_to
import opson.libraries.resources.generated.resources.progress_badge_ahead_cd
import opson.libraries.resources.generated.resources.progress_badge_reached_cd
import opson.libraries.resources.generated.resources.progress_day_bridged
import opson.libraries.resources.generated.resources.progress_day_counted
import opson.libraries.resources.generated.resources.progress_day_quiet
import opson.libraries.resources.generated.resources.progress_day_rest
import opson.libraries.resources.generated.resources.progress_day_today
import opson.libraries.resources.generated.resources.progress_days
import opson.libraries.resources.generated.resources.progress_forgiven
import opson.libraries.resources.generated.resources.progress_gentle_body
import opson.libraries.resources.generated.resources.progress_gentle_counts_off
import opson.libraries.resources.generated.resources.progress_longest
import opson.libraries.resources.generated.resources.progress_milestones
import opson.libraries.resources.generated.resources.progress_no_run
import opson.libraries.resources.generated.resources.progress_rest
import opson.libraries.resources.generated.resources.progress_run_label
import opson.libraries.resources.generated.resources.progress_this_month
import opson.libraries.resources.generated.resources.progress_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Your progress. [counts] is null in the gentle style, which shows a kind line and no numbers.
 * Nothing here is a nutrition number, in any mode.
 */
data class ProgressState(
    val loading: Boolean = true,
    val style: CelebrationStyle = CelebrationStyle.Counts,
    val counts: ProgressCounts? = null,
)

/** The run, the longest run, the days showed up this month, the next milestone, the last week and the badges. */
data class ProgressCounts(
    val current: Int,
    val longest: Int,
    val thisMonth: Int,
    val next: Milestone?,
    val toNext: Int?,
    val week: List<WeekDot>,
    val badges: List<Badge>,
)

data class WeekDot(val date: LocalDate, val day: StreakDay)

data class Badge(val milestone: Milestone, val reached: Boolean)

object ProgressStateBuilder {
    fun build(celebrating: Celebrating): ProgressState {
        val progress = celebrating.progress
        if (celebrating.style != CelebrationStyle.Counts) return ProgressState(loading = false, style = celebrating.style)
        val week = progress.recent.mapIndexed { index, day ->
            WeekDot(progress.today.minus(progress.recent.lastIndex - index, DateTimeUnit.DAY), day)
        }
        return ProgressState(
            loading = false,
            style = CelebrationStyle.Counts,
            counts = ProgressCounts(
                current = progress.current,
                longest = progress.longest,
                thisMonth = progress.showedUpThisMonth,
                next = progress.next,
                toNext = progress.daysToNext,
                week = week,
                badges = Milestone.entries.map { Badge(it, it in progress.reached) },
            ),
        )
    }
}

sealed interface ProgressAction {
    data class Arrived(val celebrating: Celebrating?) : ProgressAction
    data object CloseTapped : ProgressAction
}

sealed interface ProgressEvent {
    data object Dismiss : ProgressEvent
}

/** Your progress, from the streak in Today's header. Celebrations turned off while it is open close it. */
@Inject
class ProgressViewModel(
    source: CelebrationsSource,
    private val telemetry: CelebrationTelemetry = CelebrationTelemetry.None,
) : SEAViewModel<ProgressState, ProgressEvent, ProgressAction>(initialStateArg = ProgressState()) {
    private var opened = false

    init {
        viewModelScope.launch { source.observe().collect { takeAction(ProgressAction.Arrived(it)) } }
    }

    override suspend fun handleAction(action: ProgressAction) {
        when (action) {
            is ProgressAction.Arrived -> {
                val celebrating = action.celebrating ?: return sendEvent(ProgressEvent.Dismiss)
                if (!opened) {
                    opened = true
                    telemetry.progressOpened(celebrating.style)
                }
                action.updateState { ProgressStateBuilder.build(celebrating) }
            }
            ProgressAction.CloseTapped -> sendEvent(ProgressEvent.Dismiss)
        }
    }
}

@Composable
fun ProgressSheetContent(state: ProgressState, onAction: (ProgressAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(title = stringResource(Res.string.progress_title), onClose = { onAction(ProgressAction.CloseTapped) })
        if (state.loading) return@Column
        val counts = state.counts
        if (counts == null) {
            Gentle()
        } else {
            Run(counts)
            Stats(counts)
            Badges(counts.badges)
        }
        Text(
            text = stringResource(Res.string.progress_rest),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun Gentle() {
    TodayCard {
        Icon(icon = AppIcons.AutoAwesome, size = 24.dp, color = AppTheme.colors.accent)
        Text(text = stringResource(Res.string.progress_gentle_body), typography = AppTheme.type.lead, color = AppTheme.colors.ink)
    }
    Text(
        text = stringResource(Res.string.progress_gentle_counts_off),
        typography = AppTheme.type.secondary,
        color = AppTheme.colors.muted,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun Run(counts: ProgressCounts) {
    TodayCard(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        if (counts.current == 0) {
            Text(text = stringResource(Res.string.progress_no_run), typography = AppTheme.type.lead, color = AppTheme.colors.ink)
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                Icon(icon = AppIcons.AutoAwesome, size = 28.dp, color = AppTheme.colors.accent, modifier = Modifier.padding(bottom = 4.dp))
                Text(text = counts.current.toString(), typography = AppTheme.type.figureDisplay, color = AppTheme.colors.ink)
                Text(
                    text = pluralStringResource(Res.plurals.progress_run_label, counts.current),
                    typography = AppTheme.type.bodyStrong,
                    color = AppTheme.colors.muted,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            val next = counts.next
            val toNext = counts.toNext
            if (next != null && toNext != null) {
                Text(
                    text = pluralStringResource(Res.plurals.celebrate_more_to, toNext, toNext, stringResource(next.nextName)),
                    typography = AppTheme.type.body,
                    color = AppTheme.colors.inkSoft,
                )
            }
        }
        WeekStrip(counts.week)
        Text(text = stringResource(Res.string.progress_forgiven), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
    }
}

/** The last seven days, today last: a filled dot for a day showed up, a ring for a rest or a forgiven day. */
@Composable
private fun WeekStrip(week: List<WeekDot>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEach { dot ->
            val name = DateCopy.longWeekday(dot.date.dayOfWeek)
            val description = stringResource(
                when (dot.day) {
                    StreakDay.Counted -> Res.string.progress_day_counted
                    StreakDay.Rest -> Res.string.progress_day_rest
                    StreakDay.Bridged -> Res.string.progress_day_bridged
                    StreakDay.Missed -> Res.string.progress_day_quiet
                    StreakDay.Pending -> Res.string.progress_day_today
                },
                name,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            ) {
                Dot(dot.day)
                Text(text = DateCopy.narrowWeekday(dot.date.dayOfWeek), typography = AppTheme.type.captionStrong, color = AppTheme.colors.muted)
            }
        }
    }
}

@Composable
private fun Dot(day: StreakDay) {
    val colors = AppTheme.colors
    val base = Modifier.size(DotSize).clip(CircleShape)
    when (day) {
        StreakDay.Counted -> Box(base.background(colors.accent), contentAlignment = Alignment.Center) {
            Icon(icon = AppIcons.Check, size = 16.dp, color = colors.onAccent)
        }
        StreakDay.Rest -> Box(base.background(colors.soft), contentAlignment = Alignment.Center) {
            Icon(icon = AppIcons.BeachAccess, size = 14.dp, color = colors.muted)
        }
        StreakDay.Bridged -> Box(base.border(2.dp, colors.accentTint.color, CircleShape))
        StreakDay.Missed -> Box(base.background(colors.track))
        StreakDay.Pending -> Box(base.border(2.dp, colors.line.color, CircleShape))
    }
}

@Composable
private fun Stats(counts: ProgressCounts) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Stat(stringResource(Res.string.progress_longest), pluralStringResource(Res.plurals.progress_days, counts.longest, counts.longest), Modifier.weight(1f))
        Stat(stringResource(Res.string.progress_this_month), pluralStringResource(Res.plurals.progress_days, counts.thisMonth, counts.thisMonth), Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    TodayCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
        Text(text = value, typography = AppTheme.type.cardTitle.Tabular, color = AppTheme.colors.ink)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Badges(badges: List<Badge>) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        SectionLabel(stringResource(Res.string.progress_milestones))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
        ) {
            badges.forEach { badge ->
                val label = stringResource(badge.milestone.badgeLabel)
                val description = stringResource(if (badge.reached) Res.string.progress_badge_reached_cd else Res.string.progress_badge_ahead_cd, label)
                val colors = AppTheme.colors
                Row(
                    modifier = Modifier
                        .clip(AppTheme.shapes.inputLarge)
                        .background(if (badge.reached) colors.accentTint else colors.soft)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clearAndSetSemantics { contentDescription = description },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        icon = if (badge.reached) AppIcons.WorkspacePremium else AppIcons.Lock,
                        size = 16.dp,
                        color = if (badge.reached) colors.accent else colors.muted,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        typography = AppTheme.type.secondaryStrong,
                        color = if (badge.reached) colors.ink else colors.muted,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

private val DotSize = 28.dp

/** Fixtures for the previews and the leak test: a run of nine with one forgiven day and a rest. */
internal object ProgressFixtures {
    private val today = LocalDate(2026, 10, 29)

    val counts: ProgressState = ProgressState(
        loading = false,
        counts = ProgressCounts(
            current = 9,
            longest = 21,
            thisMonth = 17,
            next = Milestone.Weeks2,
            toNext = 5,
            week = listOf(StreakDay.Counted, StreakDay.Bridged, StreakDay.Counted, StreakDay.Rest, StreakDay.Counted, StreakDay.Counted, StreakDay.Pending)
                .mapIndexed { index, day -> WeekDot(today.minus(6 - index, DateTimeUnit.DAY), day) },
            badges = Milestone.entries.map { Badge(it, it in setOf(Milestone.FirstWrapUp, Milestone.Days3, Milestone.Week1, Milestone.Weeks2, Milestone.FirstCheckIn)) },
        ),
    )

    val fresh: ProgressState = ProgressState(
        loading = false,
        counts = ProgressCounts(
            current = 0,
            longest = 0,
            thisMonth = 0,
            next = Milestone.Days3,
            toNext = 3,
            week = List(7) { index -> WeekDot(today.minus(6 - index, DateTimeUnit.DAY), if (index == 6) StreakDay.Pending else StreakDay.Missed) },
            badges = Milestone.entries.map { Badge(it, false) },
        ),
    )

    val gentle: ProgressState = ProgressState(loading = false, style = CelebrationStyle.Gentle)
}

@Composable
private fun ProgressPreviewFrame(state: ProgressState, dark: Boolean = false) {
    PreviewContent(darkTheme = dark) { ProgressSheetContent(state, onAction = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProgressSheetPreview() = ProgressPreviewFrame(ProgressFixtures.counts)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProgressSheetDarkPreview() = ProgressPreviewFrame(ProgressFixtures.counts, dark = true)

/** Nothing yet: no run, every milestone ahead. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProgressSheetFreshPreview() = ProgressPreviewFrame(ProgressFixtures.fresh)

/** After numbers have been hard: a kind line, no counts. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProgressSheetGentlePreview() = ProgressPreviewFrame(ProgressFixtures.gentle)
