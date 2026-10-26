package com.opson.features.today.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.ui.CompactAmount
import com.opson.libraries.diary.ui.CompactSummaryBar
import com.opson.libraries.diary.ui.CompactText
import com.opson.libraries.diary.ui.EnergyPage
import com.opson.libraries.diary.ui.ExpendedSoFarLine
import com.opson.libraries.diary.ui.ListSummaryCard
import com.opson.libraries.diary.ui.LockedSummaryCard
import com.opson.libraries.diary.ui.MacrosPage
import com.opson.libraries.diary.ui.ShareCell
import com.opson.libraries.diary.ui.StatCell
import com.opson.libraries.diary.ui.StatementSummaryCard
import com.opson.libraries.diary.ui.SummaryCard
import com.opson.libraries.diary.ui.SummaryFooter
import com.opson.libraries.diary.ui.SummaryMacro
import com.opson.libraries.diary.ui.SummaryMacroColumns
import com.opson.libraries.diary.ui.SummaryStatsRow
import com.opson.libraries.diary.ui.TodayCard
import com.opson.libraries.diary.ui.TodayCardPadding
import com.opson.libraries.diary.ui.FocusSummaryCard
import com.opson.libraries.diary.ui.SoftSummaryCard
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.MacroLine
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.nutrition.ui.separator
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.ListRowChevron
import com.opson.libraries.ui.components.PageIndicator
import com.opson.libraries.ui.components.RangeBarSegment
import com.opson.libraries.ui.components.SwipePages
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.time.RelativeDay
import com.opson.system.AppTheme
import com.opson.system.color.ToneColors
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.integrations_today_expended
import opson.libraries.resources.generated.resources.today_collapse_cd
import opson.libraries.resources.generated.resources.today_compact_meals
import opson.libraries.resources.generated.resources.today_count_value
import opson.libraries.resources.generated.resources.today_counted_four
import opson.libraries.resources.generated.resources.today_counted_label
import opson.libraries.resources.generated.resources.today_counted_label_other_day
import opson.libraries.resources.generated.resources.today_counted_next
import opson.libraries.resources.generated.resources.today_counted_none
import opson.libraries.resources.generated.resources.today_counted_one
import opson.libraries.resources.generated.resources.today_counted_three
import opson.libraries.resources.generated.resources.today_counted_two
import opson.libraries.resources.generated.resources.today_counts_meals_one
import opson.libraries.resources.generated.resources.today_counts_meals_other
import opson.libraries.resources.generated.resources.today_customise_cd
import opson.libraries.resources.generated.resources.today_expand_cd
import opson.libraries.resources.generated.resources.today_explorer_new_empty
import opson.libraries.resources.generated.resources.today_focus_title
import opson.libraries.resources.generated.resources.today_macros_of
import opson.libraries.resources.generated.resources.today_macros_title
import opson.libraries.resources.generated.resources.today_new_count_one
import opson.libraries.resources.generated.resources.today_new_count_other
import opson.libraries.resources.generated.resources.today_new_this_month
import opson.libraries.resources.generated.resources.today_revealed_body
import opson.libraries.resources.generated.resources.today_revealed_title
import opson.libraries.resources.generated.resources.today_soft_energy
import opson.libraries.resources.generated.resources.today_soft_meals
import opson.libraries.resources.generated.resources.today_sticky_cd
import opson.libraries.resources.generated.resources.today_summary_eaten
import opson.libraries.resources.generated.resources.today_summary_kcal
import opson.libraries.resources.generated.resources.today_summary_nothing_yet
import opson.libraries.resources.generated.resources.today_summary_nutrients
import opson.libraries.resources.generated.resources.today_summary_range
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Today's summary card in each of its forms (design sync 2, 2.1 to 2.8), and the one-line bar it
 * shrinks into while scrolled or collapsed (2.1c, 2.1d). Stateless: the page, the collapse and
 * the stats arrive in [TodayState]; taps go back as [TodayAction]s.
 */

@Composable
internal fun Summary(summary: TodaySummary, state: TodayState, onAction: (TodayAction) -> Unit) {
    when (summary) {
        is TodaySummary.Energy -> EnergySummary(summary, state, onAction)
        is TodaySummary.Soft -> SoftSummaryCard(
            energyLabel = stringResource(Res.string.today_soft_energy),
            energy = summary.energy,
            mealsLabel = summary.mealCount?.let { stringResource(Res.string.today_soft_meals) },
            mealsValue = summary.mealCount?.let { stringResource(Res.string.today_count_value, it.done, it.total) },
            mealsDone = summary.mealCount?.done ?: 0,
            mealsTotal = summary.mealCount?.total ?: 0,
            lead = summary.lead?.toSummaryMacro(),
            rows = summary.rows.map { it.toSummaryMacro() },
        )
        is TodaySummary.Focus -> FocusSummaryCard(title = stringResource(Res.string.today_focus_title), rows = summary.rows.map { it.toSummaryMacro() })
        is TodaySummary.Locked -> LockedSummaryCard(title = stringResource(summary.title), body = stringResource(summary.body))
        TodaySummary.Revealed -> TodayCard(
            onClick = { onAction(TodayAction.RevealedTapped) },
            contentPadding = TodayCardPadding.List,
            verticalArrangement = Arrangement.Top,
        ) {
            ListRow(
                title = stringResource(Res.string.today_revealed_title),
                subtitle = stringResource(Res.string.today_revealed_body),
                icon = AppIcons.Lock,
                iconTone = ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint),
            ) {
                ListRowChevron()
            }
        }
        is TodaySummary.Counted -> StatementSummaryCard(
            label = stringResource(if (state.dayLabel == RelativeDay.Today) Res.string.today_counted_label else Res.string.today_counted_label_other_day),
            title = stringResource(countWord(summary.done)),
            body = if (summary.next != null && summary.nextAround != null && state.dayLabel == RelativeDay.Today) {
                stringResource(Res.string.today_counted_next, stringResource(summary.next.label), summary.nextAround.shortClock())
            } else {
                null
            },
        )
        is TodaySummary.NewFoods -> ListSummaryCard(
            title = stringResource(Res.string.today_new_this_month),
            trailing = stringResource(if (summary.foods.size == 1) Res.string.today_new_count_one else Res.string.today_new_count_other, summary.foods.size),
            rows = summary.foods.map { it.name to it.seen },
            emptyText = stringResource(Res.string.today_explorer_new_empty),
        )
    }
}

/**
 * The calories-shown card (2.1, 2.1b): the energy page and, when on, the macros page, swiping in
 * one card of one height, with the dots, Nutrients and the collapse chevron under both.
 */
@Composable
private fun EnergySummary(summary: TodaySummary.Energy, state: TodayState, onAction: (TodayAction) -> Unit) {
    val tools = state.summaryTools
    val macrosPage = summary.macrosPage
    val pageCount = if (macrosPage != null) 2 else 1
    SummaryCard(
        onLongClick = if (tools) ({ onAction(TodayAction.SummaryLongPressed) }) else null,
        longClickLabel = stringResource(Res.string.today_customise_cd),
    ) {
        if (macrosPage != null) {
            SwipePages(page = state.summaryPage, pageCount = pageCount, onPageChange = { onAction(TodayAction.SummaryPageShown(it)) }) { page ->
                if (page == 0) EnergyPageContent(summary) else MacrosPageContent(macrosPage)
            }
        } else {
            EnergyPageContent(summary)
        }
        SummaryFooter(
            nutrientsLabel = stringResource(Res.string.today_summary_nutrients),
            onNutrients = { onAction(TodayAction.NutrientsTapped) },
            pageCount = pageCount,
            page = state.summaryPage,
            onPageSelected = { onAction(TodayAction.SummaryPageShown(it)) },
            collapseIcon = if (tools && !summary.empty) AppIcons.ExpandLess.described(stringResource(Res.string.today_collapse_cd)) else null,
            onCollapse = { onAction(TodayAction.CollapseTapped) },
            leading = {
                // Expended has its own stat once there are stats; until then it is the line under the bar.
                if (summary.stats.isEmpty()) ExpendedSoFarLine(stringResource(Res.string.integrations_today_expended), summary.expended)
            },
        )
    }
}

@Composable
private fun EnergyPageContent(summary: TodaySummary.Energy) {
    EnergyPage(
        eatenLabel = stringResource(Res.string.today_summary_eaten),
        eaten = summary.eaten,
        unitLabel = stringResource(Res.string.today_summary_kcal),
        rangeLabel = stringResource(Res.string.today_summary_range),
        range = summary.range,
        rangeStart = summary.rangeStart,
        rangeEnd = summary.rangeEnd,
        segments = summary.bar.toSegments(),
        emptyTitle = if (summary.empty) stringResource(Res.string.today_summary_nothing_yet) else null,
    ) {
        if (summary.stats.isNotEmpty()) {
            SummaryStatsRow(summary.stats.map { it.toCell() })
        } else {
            SummaryMacroColumns(summary.macros.map { it.toSummaryMacro() })
        }
    }
}

@Composable
private fun MacrosPageContent(page: MacrosPage) {
    MacrosPage(
        title = stringResource(Res.string.today_macros_title),
        ofLine = {
            NutrientPhrase(
                metric = Metric.Calories,
                display = page.eaten,
                phrase = { stringResource(Res.string.today_macros_of, it) },
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.inkSoft,
            )
        },
        macros = page.rows.map { it.toSummaryMacro() },
        shares = page.shares.map { ShareCell(it.metric, stringResource(it.label), it.share, it.weight, it.metric.color()) },
    )
}

@Composable
private fun StatTile.toCell(): StatCell = when (val value = value) {
    is StatValue.Amount -> StatCell(stringResource(stat.label), value.metric, value.display)
    is StatValue.Count -> StatCell(stringResource(stat.label), null, Display.Hidden, count = stringResource(Res.string.today_count_value, value.done, value.total))
}

/**
 * The summary as one line: the sticky bar (with the page dots) or the collapsed card (with the
 * chevron to open it again). Parts a level hides are dropped before they are joined, so no
 * separator stands where a number would have been.
 */
@Composable
internal fun CompactLineBar(
    line: CompactLine,
    onClick: () -> Unit,
    clickLabel: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val parts = line.parts.filterNot { it is CompactPart.Amount && it.display is Display.Hidden }
    CompactSummaryBar(
        line = {
            parts.forEachIndexed { index, part ->
                if (index > 0) CompactText(text = SEPARATOR, strong = false)
                CompactPartText(part)
            }
        },
        onClick = onClick,
        clickLabel = clickLabel,
        eaten = line.bar?.eaten ?: Display.Hidden,
        rangeStart = line.bar?.rangeStart ?: 0f,
        rangeEnd = line.bar?.rangeEnd ?: 0f,
        segments = line.bar?.parts?.toSegments().orEmpty(),
        trailing = trailing,
        modifier = modifier,
    )
}

/** The sticky bar for [page], its dots on the right; a tap goes back to the top. */
@Composable
internal fun StickyBar(compact: CompactSummary, page: Int, onScrollToTop: () -> Unit, modifier: Modifier = Modifier) {
    val pages = compact.pages
    val shown = page.coerceIn(0, pages.lastIndex)
    CompactLineBar(
        line = pages[shown],
        onClick = onScrollToTop,
        clickLabel = stringResource(Res.string.today_sticky_cd),
        modifier = modifier,
        trailing = { if (pages.size > 1) PageIndicator(pageCount = pages.size, currentPage = shown, onPageSelected = { onScrollToTop() }) },
    )
}

/** The collapsed card: one line, pinned under the header, with the chevron that opens it again. */
@Composable
internal fun CollapsedSummary(compact: CompactSummary, onAction: (TodayAction) -> Unit, modifier: Modifier = Modifier) {
    val expand = stringResource(Res.string.today_expand_cd)
    CompactLineBar(
        line = compact.collapsed,
        onClick = { onAction(TodayAction.ExpandTapped) },
        clickLabel = expand,
        modifier = modifier,
        trailing = {
            RoundIconButton(
                icon = AppIcons.ExpandMore.described(expand),
                onClick = { onAction(TodayAction.ExpandTapped) },
                size = 40.dp,
                touchTarget = 48.dp,
                modifier = Modifier.padding(start = 4.dp),
                containerColor = AppTheme.colors.soft,
                contentColor = AppTheme.colors.ink,
            )
        },
    )
}

@Composable
private fun CompactPartText(part: CompactPart) {
    when (part) {
        is CompactPart.Amount -> {
            part.label?.let { CompactText(text = stringResource(it) + " ", strong = false) }
            CompactAmount(part.metric, part.display)
        }
        is CompactPart.Macros -> MacroLine(
            parts = part.parts,
            separator = MacroLineStyle.Compact.separator,
            typography = AppTheme.type.bodyBold.ExtraBold.Tabular,
            color = AppTheme.colors.ink,
        )
        is CompactPart.Meals -> CompactText(stringResource(Res.string.today_compact_meals, part.done, part.total))
        is CompactPart.Logged -> CompactText(
            stringResource(if (part.meals == 1) Res.string.today_counts_meals_one else Res.string.today_counts_meals_other, part.meals),
        )
    }
}

@Composable
private fun List<BarPart>.toSegments(): List<RangeBarSegment> = map { part ->
    RangeBarSegment(part.weight, part.metric?.color() ?: AppTheme.colors.muted)
}

internal fun countWord(done: Int): StringResource = when (done) {
    0 -> Res.string.today_counted_none
    1 -> Res.string.today_counted_one
    2 -> Res.string.today_counted_two
    3 -> Res.string.today_counted_three
    else -> Res.string.today_counted_four
}

@Composable
internal fun MacroTile.toSummaryMacro(): SummaryMacro = SummaryMacro(metric, stringResource(label), display, metric.color())

@Composable
internal fun Metric.color(): ColorResource = when (this) {
    Metric.Protein -> AppTheme.colors.nutrients.protein.content
    Metric.Carbs -> AppTheme.colors.nutrients.carbs.content
    Metric.Fat -> AppTheme.colors.nutrients.fat.content
    else -> AppTheme.colors.nutrients.fiber.content
}

private const val SEPARATOR = " · "
