package com.opson.features.today.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.FlipFigure
import com.opson.libraries.diary.Reading
import com.opson.libraries.diary.ui.CompactAmount
import com.opson.libraries.diary.ui.CompactSummaryBar
import com.opson.libraries.diary.ui.CompactText
import com.opson.libraries.diary.ui.EnergyPage
import com.opson.libraries.diary.ui.ExpendedSoFarLine
import com.opson.libraries.diary.ui.KeyNutrientCell
import com.opson.libraries.diary.ui.KeyNutrientsPage
import com.opson.libraries.diary.ui.MacrosPage
import com.opson.libraries.diary.ui.ShareCell
import com.opson.libraries.diary.ui.StatCell
import com.opson.libraries.diary.ui.StatementSummaryCard
import com.opson.libraries.diary.ui.SummaryCard
import com.opson.libraries.diary.ui.SummaryFooter
import com.opson.libraries.diary.ui.SummaryMacro
import com.opson.libraries.diary.ui.SummaryMacroColumns
import com.opson.libraries.diary.ui.SummaryStatsRow
import com.opson.libraries.diary.ui.FocusSummaryCard
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.MacroLine
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.nutrition.ui.separator
import com.opson.libraries.ui.components.FlipControl
import com.opson.libraries.ui.components.PageIndicator
import com.opson.libraries.ui.components.RangeBarSegment
import com.opson.libraries.ui.components.SwipePages
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.time.RelativeDay
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.integrations_today_expended
import opson.libraries.resources.generated.resources.today_compact_meals
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
import opson.libraries.resources.generated.resources.today_count_value
import opson.libraries.resources.generated.resources.today_customise_cd
import opson.libraries.resources.generated.resources.today_flip_reached
import opson.libraries.resources.generated.resources.today_flip_show_burned
import opson.libraries.resources.generated.resources.today_flip_show_deficit_so_far
import opson.libraries.resources.generated.resources.today_flip_show_drunk
import opson.libraries.resources.generated.resources.today_flip_show_grams
import opson.libraries.resources.generated.resources.today_flip_show_grams_left
import opson.libraries.resources.generated.resources.today_flip_show_percent
import opson.libraries.resources.generated.resources.today_flip_show_to_go
import opson.libraries.resources.generated.resources.today_focus_title
import opson.libraries.resources.generated.resources.today_macros_eaten
import opson.libraries.resources.generated.resources.today_macros_left
import opson.libraries.resources.generated.resources.today_macros_of
import opson.libraries.resources.generated.resources.today_macros_percent
import opson.libraries.resources.generated.resources.today_macros_title
import opson.libraries.resources.generated.resources.today_nutrients_see_all
import opson.libraries.resources.generated.resources.today_stat_water_to_go
import opson.libraries.resources.generated.resources.today_sticky_cd
import opson.libraries.resources.generated.resources.today_summary_eaten
import opson.libraries.resources.generated.resources.today_summary_kcal
import opson.libraries.resources.generated.resources.today_summary_nothing_yet
import opson.libraries.resources.generated.resources.today_summary_nutrients
import opson.libraries.resources.generated.resources.today_summary_goal
import opson.libraries.resources.generated.resources.today_summary_range
import opson.libraries.resources.generated.resources.trends_micro_of
import opson.libraries.resources.generated.resources.trends_micro_of_limit
import opson.libraries.resources.generated.resources.trends_micro_not_listed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Today's summary card in each of its forms (design sync 2, 2.1 to 2.8), and the one-line bar it
 * shrinks into once scrolled away (2.1c). Stateless: the page and the stats arrive in
 * [TodayState]; taps go back as [TodayAction]s.
 */

@Composable
internal fun Summary(summary: TodaySummary, state: TodayState, onAction: (TodayAction) -> Unit) {
    when (summary) {
        is TodaySummary.Energy -> EnergySummary(summary, state, onAction)
        is TodaySummary.Soft -> SoftSummaryCard(summary, onAction)
        is TodaySummary.Focus -> FocusSummaryCard(title = stringResource(Res.string.today_focus_title), rows = summary.rows.map { it.toSummaryMacro() })
        is TodaySummary.Locked -> LaterLockedCard(summary)
        is TodaySummary.Counted -> StatementSummaryCard(
            label = stringResource(if (state.dayLabel == RelativeDay.Today) Res.string.today_counted_label else Res.string.today_counted_label_other_day),
            title = stringResource(countWord(summary.done)),
            body = if (summary.next != null && summary.nextAround != null && state.dayLabel == RelativeDay.Today) {
                stringResource(Res.string.today_counted_next, stringResource(summary.next.label), summary.nextAround.shortClock())
            } else {
                null
            },
        )
    }
}

/**
 * The calories-shown card (2.1, 2.1b): the energy page, then the macros page and the nutrients page
 * when there are, swiping in one card that resizes to each page ([SwipePages]), with the page dots
 * centred under the card. On the nutrients page a tap anywhere on the card opens the Nutrients
 * screen, as See all does.
 */
@Composable
private fun EnergySummary(summary: TodaySummary.Energy, state: TodayState, onAction: (TodayAction) -> Unit) {
    val pageCount = summary.pages.size
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        EnergyCard(summary, state, onAction)
        if (pageCount > 1) {
            PageIndicator(
                pageCount = pageCount,
                currentPage = state.summaryPage,
                onPageSelected = { onAction(TodayAction.SummaryPageShown(it)) },
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun EnergyCard(summary: TodaySummary.Energy, state: TodayState, onAction: (TodayAction) -> Unit) {
    val tools = state.summaryTools
    val pages = summary.pages
    val onNutrients = pages.getOrNull(state.summaryPage) == SummaryPage.Nutrients
    val card = summary.card
    // Design sync 4's card carries Burned itself, so its footer has room for the meals. Expended
    // has its own stat once there are stats; until then it is the line under the bar.
    val footer = card?.meals != null || (card == null && summary.stats.isEmpty() && summary.expended !is Display.Hidden)
    SummaryCard(
        onLongClick = if (tools) ({ onAction(TodayAction.SummaryLongPressed) }) else null,
        longClickLabel = stringResource(Res.string.today_customise_cd),
        onClick = if (onNutrients) ({ onAction(TodayAction.NutrientsTapped) }) else null,
        clickLabel = stringResource(Res.string.today_nutrients_see_all),
        // With nothing under the pages the card closes as evenly as it opens.
        contentPadding = PaddingValues(start = CardInset, end = CardInset, top = CardInset, bottom = if (footer) FooterBottom else CardInset),
    ) {
        if (pages.size > 1) {
            SwipePages(page = state.summaryPage, pageCount = pages.size, onPageChange = { onAction(TodayAction.SummaryPageShown(it)) }) { page ->
                when (pages[page]) {
                    SummaryPage.Energy -> FirstPage(summary, onAction)
                    SummaryPage.Macros -> summary.macrosPage?.let { MacrosPageContent(it) }
                    SummaryPage.Nutrients -> summary.nutrientsPage?.let { NutrientsPageContent(it, onAction) }
                }
            }
        } else {
            FirstPage(summary, onAction)
        }
        if (footer) {
            SummaryFooter(
                leading = {
                    if (card != null) {
                        CardMeals(card)
                    } else {
                        ExpendedSoFarLine(stringResource(Res.string.integrations_today_expended), summary.expended)
                    }
                },
            )
        }
    }
}

private val CardInset = 20.dp
private val FooterBottom = 12.dp

/** Page 1: design sync 4's card when built, else design sync 2's energy page. */
@Composable
private fun FirstPage(summary: TodaySummary.Energy, onAction: (TodayAction) -> Unit) {
    val card = summary.card
    if (card != null) {
        EnergyCardContent(card, onAction, macros = { MacroRow(summary, onAction) })
    } else {
        EnergyPageContent(summary, onAction)
    }
}

/** The macros under the bar; a tap reads the whole row another way when [TodaySummary.Energy.macroFlip] says it can. */
@Composable
private fun MacroRow(summary: TodaySummary.Energy, onAction: (TodayAction) -> Unit) {
    val flip = summary.macroFlip
    SummaryMacroColumns(
        macros = summary.macros.map { it.toSummaryMacro() },
        flip = flip?.control(onAction),
        reading = flip?.let {
            stringResource(
                when (it.current) {
                    Reading.Left -> Res.string.today_macros_left
                    Reading.Percent -> Res.string.today_macros_percent
                    else -> Res.string.today_macros_eaten
                },
            )
        },
    )
}

/** What a tap on a flipping figure does, and what a screen reader offers after the figure's text. */
@Composable
private fun FlipState.control(onAction: (TodayAction) -> Unit): FlipControl =
    FlipControl(stringResource(showLabel())) { onAction(TodayAction.FigureFlipped(figure)) }

private fun FlipState.showLabel(): StringResource = when (figure) {
    FlipFigure.Macros -> when (next) {
        Reading.Left -> Res.string.today_flip_show_grams_left
        Reading.Percent -> Res.string.today_flip_show_percent
        else -> Res.string.today_flip_show_grams
    }
    FlipFigure.DeficitSoFar -> if (next == Reading.Burned) Res.string.today_flip_show_burned else Res.string.today_flip_show_deficit_so_far
    FlipFigure.Water -> if (next == Reading.Left) Res.string.today_flip_show_to_go else Res.string.today_flip_show_drunk
}

@Composable
private fun EnergyPageContent(summary: TodaySummary.Energy, onAction: (TodayAction) -> Unit) {
    EnergyPage(
        eatenLabel = stringResource(Res.string.today_summary_eaten),
        eaten = summary.eaten,
        unitLabel = stringResource(Res.string.today_summary_kcal),
        rangeLabel = stringResource(if (summary.singleGoal) Res.string.today_summary_goal else Res.string.today_summary_range),
        range = summary.range,
        rangeStart = summary.rangeStart,
        rangeEnd = summary.rangeEnd,
        segments = summary.bar.toSegments(),
        emptyTitle = if (summary.empty) stringResource(Res.string.today_summary_nothing_yet) else null,
    ) {
        MacroRow(summary, onAction)
        SummaryStatsRow(summary.stats.map { it.toCell(onAction) })
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
private fun NutrientsPageContent(page: NutrientsPage, onAction: (TodayAction) -> Unit) {
    KeyNutrientsPage(
        title = stringResource(Res.string.today_summary_nutrients),
        seeAllLabel = stringResource(Res.string.today_nutrients_see_all),
        onSeeAll = { onAction(TodayAction.NutrientsTapped) },
        rows = page.rows.map { row ->
            KeyNutrientCell(
                label = stringResource(row.label),
                reference = stringResource(if (row.isLimit) Res.string.trends_micro_of_limit else Res.string.trends_micro_of, row.reference),
                display = row.display,
                // No tap of its own: the whole page opens the Nutrients screen, which lists each one's foods.
                notListed = if (row.notListed) stringResource(Res.string.trends_micro_not_listed) else null,
            )
        },
    )
}

@Composable
private fun StatTile.toCell(onAction: (TodayAction) -> Unit): StatCell {
    val control = flip?.control(onAction)
    return when (val value = value) {
        is StatValue.Amount -> StatCell(stringResource(label), value.metric, value.display, flip = control)
        is StatValue.Count -> StatCell(
            stringResource(label),
            null,
            Display.Hidden,
            count = stringResource(Res.string.today_count_value, value.done, value.total),
            flip = control,
        )
        is StatValue.ToGo -> StatCell(
            stringResource(label),
            null,
            Display.Hidden,
            count = if (value.left > 0) stringResource(Res.string.today_stat_water_to_go, value.left) else stringResource(Res.string.today_flip_reached),
            flip = control,
        )
    }
}

/**
 * The summary as one line, the sticky bar with the page dots. Parts a level hides are dropped
 * before they are joined, so no separator stands where a number would have been.
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

@Composable
private fun CompactPartText(part: CompactPart) {
    when (part) {
        is CompactPart.Amount -> {
            part.label?.let { CompactText(text = stringResource(it) + " ", strong = false) }
            val phrase = part.phrase
            if (phrase == null) {
                CompactAmount(part.metric, part.display)
            } else {
                NutrientPhrase(
                    metric = part.metric,
                    display = part.display,
                    phrase = { stringResource(phrase, it) },
                    typography = AppTheme.type.bodyBold.ExtraBold.Tabular,
                    color = AppTheme.colors.ink,
                )
            }
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
internal fun MacroTile.toSummaryMacro(): SummaryMacro =
    SummaryMacro(metric, stringResource(label), display, metric.color(), bar = bar, word = word?.let { stringResource(it) })

@Composable
internal fun Metric.color(): ColorResource = when (this) {
    Metric.Protein -> AppTheme.colors.nutrients.protein.content
    Metric.Carbs -> AppTheme.colors.nutrients.carbs.content
    Metric.Fat -> AppTheme.colors.nutrients.fat.content
    else -> AppTheme.colors.nutrients.fiber.content
}

private const val SEPARATOR = " · "
