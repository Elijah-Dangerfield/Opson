package com.opson.libraries.diary.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.ui.NutrientBar
import com.opson.libraries.nutrition.ui.NutrientFigure
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.nutrition.ui.currentVisibilityPolicy
import com.opson.libraries.nutrition.ui.testTag
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.libraries.ui.components.RangeBar
import com.opson.libraries.ui.components.RangeBarDefaults
import com.opson.libraries.ui.components.RangeBarSegment
import com.opson.libraries.ui.components.SwipePages
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.typography.TypographyResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/*
 * Today's summary card in design sync 2 (2.1 to 2.1e): one neutral surface in every palette,
 * colour only on the macros. The energy page and the macros page swipe inside the same card
 * ([SwipePages]), each at its own height, with the dots under the card. When the card scrolls away
 * it shrinks into [CompactSummaryBar], the sticky bar.
 */

/**
 * The card around the summary: the surface colour, 22 radius, and a long press that opens the
 * customise sheet ([onLongClick], read out as [longClickLabel]). [onClick] makes a tap anywhere on
 * the card do something (read out as [clickLabel]); a link or row inside that handles its own tap
 * keeps it. Null leaves either press alone. Both go through one gesture detector, so a long press
 * is never also a tap.
 */
@Composable
fun SummaryCard(
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val press = if (onLongClick == null && onClick == null) {
        Modifier
    } else {
        Modifier
            .pointerInput(onLongClick, onClick) {
                detectTapGestures(
                    onTap = onClick?.let { tap -> { tap() } },
                    onLongPress = onLongClick?.let { press -> { press() } },
                )
            }
            .semantics {
                if (onLongClick != null) onLongClick(label = longClickLabel) { onLongClick(); true }
                if (onClick != null) onClick(label = clickLabel) { onClick(); true }
            }
    }
    TodayCard(modifier = modifier.then(press), contentPadding = contentPadding, verticalArrangement = Arrangement.Top, content = content)
}

/** One stat in the row under the energy bar: "Expended / 1,990 kcal", "Meals / 3 of 4". */
data class StatCell(val label: String, val metric: Metric?, val display: Display, val count: String? = null)

/**
 * Page 1 (2.1): "Eaten" over the figure, your range on the right, the eaten bar split by macro
 * with the range as a dashed box, then [below] (the stats row, or the Expended line on an empty
 * morning). [emptyTitle] ("Nothing yet") stands in for the figure. The bar exists only with
 * [eaten].
 */
@Composable
fun EnergyPage(
    eatenLabel: String,
    eaten: Display,
    unitLabel: String,
    rangeLabel: String,
    range: Display,
    rangeStart: Float,
    rangeEnd: Float,
    segments: List<RangeBarSegment>,
    modifier: Modifier = Modifier,
    emptyTitle: String? = null,
    below: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                SummaryLabel(eatenLabel)
                if (emptyTitle != null) {
                    Text(text = emptyTitle, typography = AppTheme.type.subH1, color = AppTheme.colors.ink)
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NutrientText(Metric.Calories, eaten, typography = AppTheme.type.figureDisplay, color = AppTheme.colors.ink)
                        NutrientVisible(eaten) {
                            Text(text = unitLabel, typography = AppTheme.type.cardTitle, color = AppTheme.colors.inkSoft, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                }
            }
            NutrientVisible(range) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    SummaryLabel(rangeLabel)
                    NutrientText(Metric.Calories, range, typography = AppTheme.type.cardTitle.Tabular, color = AppTheme.colors.ink)
                }
            }
        }
        NutrientVisible(eaten) {
            RangeBar(
                fill = (eaten as? Display.Shown)?.fraction ?: 0f,
                rangeStart = rangeStart,
                rangeEnd = rangeEnd,
                colors = RangeBarDefaults.summary(),
                segments = segments,
                modifier = Modifier.padding(top = AppTheme.spacing.row).testTag(Metric.Calories.testTag()),
            )
        }
        below()
    }
}

/** The muted small label over a figure: "Eaten", "Your range", "Expended". */
@Composable
fun SummaryLabel(text: String, modifier: Modifier = Modifier, color: ColorResource = AppTheme.colors.muted) {
    Text(text = text, typography = AppTheme.type.secondaryStrong.Bold, color = color, modifier = modifier, maxLines = 1)
}

/** The stats under the bar, a line above them, each a label over its figure. Nothing when empty. */
@Composable
fun SummaryStatsRow(stats: List<StatCell>, modifier: Modifier = Modifier) {
    if (stats.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = AppTheme.colors.line, modifier = Modifier.padding(vertical = AppTheme.spacing.section))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            stats.forEach { stat ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SummaryLabel(stat.label)
                    if (stat.count != null) {
                        CountFigure(stat.count)
                    } else if (stat.metric != null) {
                        NutrientFigure(stat.metric, stat.display, typography = AppTheme.type.cardTitle.Tabular, restTypography = AppTheme.type.secondary)
                    }
                }
            }
        }
    }
}

/** "3 of 4" with the first number in [typography] and the rest in [restTypography], as the design sets counts. */
@Composable
fun CountFigure(
    text: String,
    modifier: Modifier = Modifier,
    typography: TypographyResource = AppTheme.type.cardTitle.Tabular,
    restTypography: TypographyResource = AppTheme.type.secondary,
) {
    val split = text.indexOf(' ')
    if (split <= 0) {
        Text(text = text, typography = typography, color = AppTheme.colors.ink, modifier = modifier)
    } else {
        Row(modifier, verticalAlignment = Alignment.Bottom) {
            Text(text = text.substring(0, split), typography = typography, color = AppTheme.colors.ink, modifier = Modifier.alignByBaseline())
            Text(text = text.substring(split), typography = restTypography, color = AppTheme.colors.muted, modifier = Modifier.alignByBaseline())
        }
    }
}

/**
 * The macros as columns under the bar, each its name, amount and bar. Four (fiber too) sit closer
 * with a smaller name, so "Protein" still fits a phone's quarter of the card.
 */
@Composable
fun SummaryMacroColumns(macros: List<SummaryMacro>, modifier: Modifier = Modifier) {
    if (macros.isEmpty()) return
    val four = macros.size > 3
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = AppTheme.colors.line, modifier = Modifier.padding(vertical = AppTheme.spacing.section))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (four) AppTheme.spacing.row else AppTheme.spacing.page)) {
            macros.forEach { macro ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroName(macro, typography = if (four) AppTheme.type.secondaryStrong.Bold else AppTheme.type.bodyBold)
                    NutrientFigure(macro.metric, macro.display, typography = AppTheme.type.cardTitle.Tabular, restTypography = AppTheme.type.secondary)
                    MacroBar(macro, height = 6)
                }
            }
        }
    }
}

@Composable
private fun MacroName(macro: SummaryMacro, typography: TypographyResource = AppTheme.type.bodyBold) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(macro.color, CircleShape))
        Text(text = macro.label, typography = typography, color = AppTheme.colors.ink, maxLines = 1)
    }
}

@Composable
private fun MacroBar(macro: SummaryMacro, height: Int) {
    NutrientBar(
        metric = macro.metric,
        display = macro.display,
        color = macro.color.color,
        trackColor = AppTheme.colors.track.color,
        modifier = Modifier.fillMaxWidth().height(height.dp),
    )
}

/** One macro's share of the energy on the macros page: "Carbs 52%", and its weight for the split bar. */
data class ShareCell(val metric: Metric, val label: String, val share: Display, val weight: Float, val color: ColorResource)

/**
 * Page 2 (2.1b): "Macros" with "of 1,245 kcal" on the right ([ofLine], nothing when hidden),
 * the macros two by two with their bars, then the split bar and each share. A macro hidden by
 * the levels is not in [macros] or [shares], so its row is gone and the split recalculates.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MacrosPage(
    title: String,
    ofLine: @Composable () -> Unit,
    macros: List<SummaryMacro>,
    shares: List<ShareCell>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SummaryLabel(title, modifier = Modifier.weight(1f))
            ofLine()
        }
        macros.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.page)) {
                pair.forEach { macro ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            Box(Modifier.weight(1f)) { MacroName(macro) }
                            NutrientFigure(macro.metric, macro.display, typography = AppTheme.type.cardTitle.Tabular, restTypography = AppTheme.type.secondary)
                        }
                        MacroBar(macro, height = 8)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (shares.isNotEmpty()) {
            SplitBar(shares.map { RangeBarSegment(it.weight, it.color) }, shares.first().metric)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.section), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shares.forEach { share ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).background(share.color, CircleShape))
                        Text(text = share.label, typography = AppTheme.type.secondary, color = AppTheme.colors.inkSoft)
                        NutrientText(share.metric, share.share, typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.inkSoft)
                    }
                }
            }
        }
    }
}

/**
 * One row of [KeyNutrientsPage]: "Sodium", "of a 2,300 mg limit", its percent through the micros
 * level. [notListed] ("Not listed") stands in for the bar and percent when no food lists it.
 * [onClick] lists the foods behind it; null when there are none.
 */
data class KeyNutrientCell(
    val label: String,
    val reference: String,
    val display: Display,
    val notListed: String? = null,
    val onClickLabel: String? = null,
    val onClick: (() -> Unit)? = null,
)

/**
 * The last page: "Nutrients" with [seeAllLabel] on the right, which opens the Nutrients screen, then
 * a few vitamins and minerals, each its name over what the percent is of, a bar and the percent.
 */
@Composable
fun KeyNutrientsPage(
    title: String,
    seeAllLabel: String,
    onSeeAll: () -> Unit,
    rows: List<KeyNutrientCell>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SummaryLabel(title, modifier = Modifier.weight(1f))
            SummaryLink(text = seeAllLabel, onClick = onSeeAll)
        }
        rows.forEach { row ->
            val onClick = row.onClick
            val tap = if (onClick == null) {
                Modifier
            } else {
                Modifier.semantics { onClick(label = row.onClickLabel) { onClick(); true } }.bounceClick(scaleDown = 0.98f, onClick = onClick)
            }
            Row(
                modifier = Modifier.fillMaxWidth().then(tap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = row.label, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, maxLines = 1)
                    Text(text = row.reference, typography = AppTheme.type.secondary, color = AppTheme.colors.muted, maxLines = 1)
                }
                if (row.notListed != null) {
                    Text(text = row.notListed, typography = AppTheme.type.secondary, color = AppTheme.colors.muted, maxLines = 1)
                    return@Row
                }
                NutrientBar(
                    metric = Metric.Micros,
                    display = row.display,
                    color = AppTheme.colors.nutrients.fat.content.color,
                    trackColor = AppTheme.colors.track.color,
                    modifier = Modifier.width(KeyNutrientBarWidth).height(6.dp),
                )
                NutrientText(Metric.Micros, row.display, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink, maxLines = 1)
            }
        }
    }
}

private val KeyNutrientBarWidth = 72.dp

/** The macros laid end to end, a full bar split by their weights. Tagged with the first macro. */
@Composable
private fun SplitBar(segments: List<RangeBarSegment>, tag: Metric) {
    RangeBar(
        fill = 1f,
        rangeStart = 0f,
        rangeEnd = 0f,
        colors = RangeBarDefaults.summary(),
        segments = segments,
        height = 12.dp,
        modifier = Modifier.testTag(tag.testTag()),
    )
}

/**
 * Under every page: [leading], then a Nutrients link when there is a [nutrientsLabel]. The page
 * dots sit centred under the card, not here. The calories card has no link: its last page opens
 * the Nutrients screen. There is no collapse chevron any more: the card shrinks to the sticky bar
 * as it scrolls away, which is the only collapsing it does (owner feedback, 2026-10-29).
 */
@Composable
fun SummaryFooter(
    modifier: Modifier = Modifier,
    nutrientsLabel: String? = null,
    onNutrients: () -> Unit = {},
    leading: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { leading() }
        if (nutrientsLabel != null) SummaryLink(text = nutrientsLabel, onClick = onNutrients)
    }
}

/** A bold ink link with a chevron: "Nutrients ›". At least 48 tall. */
@Composable
fun SummaryLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = MinTouchTarget)
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.96f, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = text, typography = AppTheme.type.bodyBold.ExtraBold, color = AppTheme.colors.ink)
        Icon(icon = AppIcons.ChevronRight, size = 20.dp, color = AppTheme.colors.ink)
    }
}

/** "⌚ Expended so far 410 kcal" on the card's surface: nothing at all when hidden. */
@Composable
fun ExpendedSoFarLine(label: String, expended: Display, modifier: Modifier = Modifier) {
    NutrientVisible(expended) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon = AppIcons.Watch, size = 20.dp, color = AppTheme.colors.muted)
            Text(text = label, typography = AppTheme.type.secondary, color = AppTheme.colors.inkSoft, maxLines = 1)
            NutrientText(Metric.EnergyOut, expended, typography = AppTheme.type.secondaryStrong.Bold.Tabular, color = AppTheme.colors.ink, maxLines = 1)
        }
    }
}

/**
 * The summary as one line (2.1c, 2.1d): [line] (the feature's parts, joined by " · "), the eaten
 * bar under it when there is one ([eaten] with the range box), and [trailing] (the dots while
 * sticky). [onClick] scrolls to the top.
 */
@Composable
fun CompactSummaryBar(
    line: @Composable RowScope.() -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    eaten: Display = Display.Hidden,
    rangeStart: Float = 0f,
    rangeEnd: Float = 0f,
    segments: List<RangeBarSegment> = emptyList(),
    clickLabel: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, AppTheme.shapes.card, clip = false)
            .clip(AppTheme.shapes.card)
            .background(AppTheme.colors.surface)
            .semantics {
                role = Role.Button
                if (clickLabel != null) onClick(label = clickLabel) { onClick(); true }
            }
            .bounceClick(scaleDown = 0.99f, onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = line)
            trailing()
        }
        NutrientVisible(eaten) {
            RangeBar(
                fill = (eaten as? Display.Shown)?.fraction ?: 0f,
                rangeStart = rangeStart,
                rangeEnd = rangeEnd,
                colors = RangeBarDefaults.summary(),
                segments = segments,
                height = 14.dp,
                modifier = Modifier.padding(end = 8.dp).testTag(Metric.Calories.testTag()),
            )
        }
    }
}

/** A part of a [CompactSummaryBar] line in its type: bold ink, one line. */
@Composable
fun CompactText(text: String, modifier: Modifier = Modifier, strong: Boolean = true) {
    Text(
        text = text,
        typography = if (strong) AppTheme.type.bodyBold.ExtraBold.Tabular else AppTheme.type.secondaryStrong.Bold,
        color = if (strong) AppTheme.colors.ink else AppTheme.colors.inkSoft,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** An amount in a [CompactSummaryBar] line, tagged; nothing when hidden. */
@Composable
fun CompactAmount(metric: Metric, display: Display, modifier: Modifier = Modifier) {
    NutrientText(metric, display, typography = AppTheme.type.bodyBold.ExtraBold.Tabular, color = AppTheme.colors.ink, maxLines = 1, modifier = modifier)
}

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun SummaryCardPreview() {
    PreviewLightDark {
        val policy = currentVisibilityPolicy()
        val n = AppTheme.colors.nutrients
        val eaten = policy.resolve(Metric.Calories, 1245.0, Surface.Today, FormatContext(of = 2760.0, bare = true))
        val segments = listOf(RangeBarSegment(0.16f, n.protein.content), RangeBarSegment(0.52f, n.carbs.content), RangeBarSegment(0.32f, n.fat.content))
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
            SummaryCard {
                SwipePages(page = 0, pageCount = 2, onPageChange = {}) { page ->
                    if (page == 0) {
                        EnergyPage(
                            eatenLabel = "Eaten",
                            eaten = eaten,
                            unitLabel = "kcal",
                            rangeLabel = "Your range",
                            range = policy.resolve(Metric.Calories, 1900.0, Surface.Today, FormatContext(bare = true)),
                            rangeStart = 0.69f,
                            rangeEnd = 0.83f,
                            segments = segments,
                        ) {
                            SummaryStatsRow(
                                listOf(
                                    StatCell("Expended", Metric.EnergyOut, policy.resolve(Metric.EnergyOut, 1990.0, Surface.Today)),
                                    StatCell("To your range", Metric.Calories, policy.resolve(Metric.Calories, 655.0, Surface.Today)),
                                    StatCell("Meals", null, Display.Hidden, count = "3 of 4"),
                                ),
                            )
                        }
                    } else {
                        MacrosPage(
                            title = "Macros",
                            ofLine = {},
                            macros = listOf(
                                SummaryMacro(Metric.Protein, "Protein", policy.resolve(Metric.Protein, 51.0, Surface.Today, FormatContext(of = 120.0, goal = GoalStyle.Slash)), n.protein.content),
                            ),
                            shares = emptyList(),
                        )
                    }
                }
                SummaryFooter()
            }
            CompactSummaryBar(
                line = { CompactAmount(Metric.Calories, policy.resolve(Metric.Calories, 1245.0, Surface.Today)) },
                onClick = {},
                eaten = eaten,
                rangeStart = 0.69f,
                rangeEnd = 0.83f,
                segments = segments,
            )
        }
    }
}
