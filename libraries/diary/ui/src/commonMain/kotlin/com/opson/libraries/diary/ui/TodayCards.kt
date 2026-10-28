package com.opson.libraries.diary.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.MacroPart
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NutrientAmounts
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.macroLine
import com.opson.libraries.nutrition.ui.MacroLine
import com.opson.libraries.nutrition.ui.NutrientBar
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.nutrition.ui.currentVisibilityPolicy
import com.opson.libraries.nutrition.ui.separator
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HeroCard
import com.opson.libraries.ui.components.HeroWashButton
import com.opson.libraries.ui.components.HeroWashPanel
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.dashedBorder
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.libraries.ui.components.SegmentedBar
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.RowIcon
import com.opson.libraries.ui.components.icon.RowIconSize
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/*
 * Today's and Day close's cards that carry a number (design v3, 2.1 to 2.10 and 4.1 to 4.5).
 * Every number arrives resolved as a Display and renders through the tagged nutrient texts and
 * bars, so a hidden number takes its label with it. Bars stop at full.
 */

/** One macro on a summary card: its label, the resolved amount, and its colour. */
data class SummaryMacro(val metric: Metric, val label: String, val display: Display, val color: ColorResource)

/** Paddings for [TodayCard]s that hold a list row or a checklist rather than free content. */
object TodayCardPadding {
    /** A card that is one [com.opson.libraries.ui.components.ListRow]: Close the day, the reveal link. */
    val List = PaddingValues(start = 20.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)

    /** A checklist: the title at the top, the last row's own padding at the bottom. */
    val Checklist = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp)
}

/** A card inside the page padding: radius 22, the surface, 20 inside. Today's v3 cards. */
@Composable
fun TodayCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(AppTheme.spacing.row),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    EdgeCard(
        modifier = modifier,
        edgeToEdge = false,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        onClick = onClick,
        content = content,
    )
}

@Composable
private fun SurfaceBar(macro: SummaryMacro, height: Int) {
    NutrientBar(
        metric = macro.metric,
        display = macro.display,
        color = macro.color.color,
        trackColor = AppTheme.colors.track.color,
        modifier = Modifier.fillMaxWidth().height(height.dp),
    )
}

/** Nutrient focus (2.5): "Your focus today", then each visible nutrient as a dot, its name, its word and a bar. */
@Composable
fun FocusSummaryCard(
    title: String,
    rows: List<SummaryMacro>,
    modifier: Modifier = Modifier,
) {
    TodayCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        SummaryLabel(title)
        rows.forEach { macro ->
            NutrientVisible(macro.display) {
                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(12.dp).background(macro.color, CircleShape))
                        Text(text = macro.label, typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
                        NutrientText(macro.metric, macro.display, typography = AppTheme.type.bodyBold.ExtraBold, color = AppTheme.colors.ink)
                    }
                    SurfaceBar(macro, height = 14)
                }
            }
        }
    }
}

/** A label, a big line and an optional body (2.4): "So far today / Three meals". */
@Composable
fun StatementSummaryCard(
    label: String,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
) {
    TodayCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SummaryLabel(label)
        Text(text = title, typography = AppTheme.type.subH1, color = AppTheme.colors.ink)
        if (body != null) Text(text = body, typography = AppTheme.type.body, color = AppTheme.colors.inkSoft)
    }
}

/**
 * Today's water card: the water drop, "Water" and its amount (nothing when hidden), the glasses
 * as segments, and the + that opens the water sheet.
 */
@Composable
fun WaterCard(
    title: String,
    display: Display,
    filled: Int,
    total: Int,
    showSegments: Boolean,
    onAdd: (() -> Unit)?,
    addIcon: IconResource,
    modifier: Modifier = Modifier,
) {
    TodayCard(modifier = modifier, contentPadding = PaddingValues(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            RowIcon(icon = AppIcons.WaterDrop, color = AppTheme.colors.nutrients.water.content)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(text = title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
                    NutrientText(Metric.Water, display, typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.muted, maxLines = 1)
                }
                if (showSegments) SegmentedBar(filled = filled, total = total)
            }
            // No + on a day closed with Close the day: it takes nothing until it is reopened.
            onAdd?.let { RoundIconButton(icon = addIcon, onClick = it, size = MinTouchTarget) }
        }
    }
}

/** One logged item on a [TodayMealCard]. */
data class MealCardItem(
    val key: String,
    val name: String,
    val sub: String,
    val calories: Display,
)

/**
 * A meal's header: its icon in [iconColor], name, a muted line ([subtitle], then the macro line when there is one,
 * "8:10 am · 13P 80C 8F"), its energy and [trailing] (the 48 +). Shared by every meal card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealHeader(
    icon: IconResource,
    iconColor: ColorResource,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    calories: Display = Display.Hidden,
    macros: List<MacroPart> = emptyList(),
    macroStyle: MacroLineStyle = MacroLineStyle.Compact,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        RowIcon(icon = icon, color = iconColor)
        Column(Modifier.weight(1f)) {
            Text(text = title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            FlowRow {
                if (subtitle != null) Text(text = subtitle, typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.muted)
                if (subtitle != null && macros.isNotEmpty()) Text(text = SUBTITLE_SEPARATOR, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
                MacroLine(parts = macros, separator = macroStyle.separator, typography = AppTheme.type.secondary.Tabular)
            }
        }
        NutrientText(Metric.Calories, calories, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.muted, maxLines = 1)
        trailing()
    }
}

private const val SUBTITLE_SEPARATOR = " · "

/**
 * A meal with its items (2.1): the [MealHeader] with [headerTrailing] (the + , or Move to… on
 * Uncategorized), each item drawn by [itemRow] (a plain row, a swipe row, a tickable row while
 * selecting, or its own confirmation), then the meal's [note].
 * [onHeaderClick] makes the header a target: while selecting it toggles the meal's foods.
 * [dropLabel] marks the card as where dragged foods will land (13.3c): "Move to Dinner".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayMealCard(
    icon: IconResource,
    iconColor: ColorResource,
    title: String,
    time: String?,
    calories: Display,
    macros: List<MacroPart>,
    macroStyle: MacroLineStyle,
    items: List<MealCardItem>,
    note: MealCardNote?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onHeaderClick: (() -> Unit)? = null,
    headerTrailing: @Composable RowScope.() -> Unit = {},
    dropLabel: String? = null,
    itemRow: @Composable (MealCardItem) -> Unit,
) {
    TodayCard(
        modifier = modifier.then(
            if (highlighted || dropLabel != null) Modifier.border(2.dp, AppTheme.colors.accent.color, AppTheme.shapes.card) else Modifier,
        ),
        contentPadding = PaddingValues(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MealHeader(
            icon,
            iconColor,
            title,
            time,
            calories = calories,
            macros = macros,
            macroStyle = macroStyle,
            modifier = if (onHeaderClick != null) {
                Modifier.semantics { role = Role.Button }.bounceClick(scaleDown = 0.99f, onClick = onHeaderClick)
            } else {
                Modifier
            },
            trailing = headerTrailing,
        )
        Column(Modifier.padding(end = 6.dp)) {
            items.forEach { item -> itemRow(item) }
        }
        if (note != null) MealNoteBlock(note, Modifier.padding(end = 6.dp))
        if (dropLabel != null) DropZone(dropLabel)
    }
}

/** A meal's Note as its card shows it: [hunger] ("Hunger level: Hungry") over the [text]. Either may be absent. */
data class MealCardNote(val hunger: String?, val text: String?)

/**
 * A meal's Note under its foods, in body text so it reads as what was written rather than a
 * caption: a line, the note icon under the meal's icon, and the hunger and words lined up with
 * the meal's name.
 */
@Composable
fun MealNoteBlock(note: MealCardNote, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = AppTheme.colors.line)
        Row(Modifier.padding(top = AppTheme.spacing.row), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Box(Modifier.width(RowIconSize), contentAlignment = Alignment.TopCenter) {
                RowIcon(icon = AppIcons.EditNote, color = AppTheme.colors.muted, size = NoteIconSize)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                note.hunger?.let { Text(text = it, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.inkSoft) }
                note.text?.let { Text(text = it, typography = AppTheme.type.body, color = AppTheme.colors.ink) }
            }
        }
    }
}

private val NoteIconSize = 22.dp

/** Where dragged foods will land (13.3c): a dashed accent box saying "Move to Dinner". */
@Composable
fun DropZone(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(AppTheme.shapes.inputLarge)
            .background(AppTheme.colors.accentTint)
            .dashedBorder(AppTheme.colors.accent.color, radius = DropZoneRadius)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, typography = AppTheme.type.bodyBold.ExtraBold, color = AppTheme.colors.accent)
    }
}

private val DropZoneRadius = 16.dp

/** A plain food row on a [TodayMealCard]: opens the food's sheet. [trailing] is its ⋮. */
@Composable
fun MealCardRow(item: MealCardItem, onClick: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    DiaryItemRow(
        name = item.name,
        sub = item.sub,
        calories = item.calories,
        onClick = onClick,
        showDivider = false,
        dividerAbove = true,
        contentPadding = PaddingValues(vertical = AppTheme.spacing.row),
        trailing = trailing,
    )
}

/**
 * A meal as one compact card: the [MealHeader] and anything [below] it (suggestions, the foods).
 * [dropLabel] marks it as where dragged foods will land, as on [TodayMealCard].
 */
@Composable
fun MealRowCard(
    icon: IconResource,
    iconColor: ColorResource,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    calories: Display = Display.Hidden,
    trailing: @Composable RowScope.() -> Unit = {},
    dropLabel: String? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    TodayCard(
        modifier = modifier.then(
            if (dropLabel != null) Modifier.border(2.dp, AppTheme.colors.accent.color, AppTheme.shapes.card) else Modifier,
        ),
        contentPadding = PaddingValues(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        MealHeader(icon, iconColor, title, subtitle, calories = calories, trailing = trailing)
        if (below != null) Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row), content = below)
        if (dropLabel != null) DropZone(dropLabel)
    }
}

/** A line of food names under a line, inside a [MealRowCard]: a planned meal's foods. */
@Composable
fun FoodsLine(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        HorizontalDivider(color = AppTheme.colors.line)
        Text(text = text, typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.padding(top = AppTheme.spacing.row))
    }
}

/**
 * Day close with calories shown (4.1): the figure big with its unit, the range as a sentence
 * ([rangeLine]), and the four macros in their colours below a line.
 */
@Composable
fun DayTotalsCard(
    eaten: Display,
    unitLabel: String,
    rangeLine: @Composable () -> Unit,
    macros: List<SummaryMacro>,
    modifier: Modifier = Modifier,
) {
    // The range line sits right under the figure, as v3 draws it. The divider brings its own space.
    TodayCard(
        modifier = modifier,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NutrientText(Metric.Calories, eaten, typography = AppTheme.type.figureDisplay, color = AppTheme.colors.ink)
            NutrientVisible(eaten) {
                Text(text = unitLabel, typography = AppTheme.type.cardTitle, color = AppTheme.colors.muted, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        rangeLine()
        if (macros.isNotEmpty()) {
            HorizontalDivider(color = AppTheme.colors.line, modifier = Modifier.padding(vertical = 14.dp))
            Row(Modifier.fillMaxWidth()) {
                macros.forEach { macro ->
                    NutrientVisible(macro.display) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = macro.label, typography = AppTheme.type.secondarySmall.Bold, color = macro.color)
                            NutrientText(macro.metric, macro.display, typography = AppTheme.type.cardTitle.Tabular, color = AppTheme.colors.ink)
                        }
                    }
                }
            }
        }
    }
}

/** A plain sentence under a Day close figure, the range inside it: "Inside your range of 1,900 to 2,300". */
@Composable
fun RangeSentence(range: Display, phrase: @Composable (String) -> String) {
    NutrientPhrase(metric = Metric.Calories, display = range, phrase = phrase, typography = AppTheme.type.body.Tabular, color = AppTheme.colors.ink)
}

/**
 * Day close's big-day reset (4.2, 4.3) on the hero: the message, tomorrow's unchanged range with
 * [whyNotLabel] after it ("Why not cut back?"), then either the commitment buttons ([onCommit] for
 * both; they save nothing) or [committedText], and [supportLabel] ("talk to someone"), which shows
 * whenever the card does.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BigDayHeroCard(
    body: String,
    tomorrow: Display,
    tomorrowPhrase: @Composable (String) -> String,
    commitLabels: List<String>,
    onCommit: () -> Unit,
    committedText: String?,
    modifier: Modifier = Modifier,
    whyNotLabel: String? = null,
    onWhyNot: () -> Unit = {},
    supportLabel: String? = null,
    onSupport: () -> Unit = {},
) {
    HeroCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.page)) {
        Text(text = body, typography = AppTheme.type.heroTitle.Bold, color = AppTheme.colors.onHero)
        // Once committed, the acknowledgement takes the place of tomorrow's line, as v3 draws it.
        if (committedText == null) {
            FlowRow(verticalArrangement = Arrangement.Center) {
                NutrientPhrase(
                    metric = Metric.Calories,
                    display = tomorrow,
                    phrase = tomorrowPhrase,
                    typography = AppTheme.type.body.Tabular,
                    color = AppTheme.colors.onHero.withAlpha(LABEL_ALPHA),
                )
                if (whyNotLabel != null) {
                    Text(
                        text = whyNotLabel,
                        typography = AppTheme.type.bodyBold,
                        color = AppTheme.colors.onHero,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .semantics { role = Role.Button }
                            .bounceClick(scaleDown = 0.97f, onClick = onWhyNot)
                            .defaultMinSize(minHeight = MinTouchTarget)
                            .padding(start = 4.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
        }
        if (commitLabels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                commitLabels.forEach { HeroWashButton(text = it, onClick = onCommit, modifier = Modifier.weight(1f)) }
            }
        }
        if (committedText != null) {
            HeroWashPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(icon = AppIcons.Favorite, size = 26.dp, color = AppTheme.colors.onHero)
                    Text(text = committedText, typography = AppTheme.type.bodyBold.ExtraBold, color = AppTheme.colors.onHero)
                }
            }
        }
        if (supportLabel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { role = Role.Button }
                    .bounceClick(scaleDown = 0.98f, onClick = onSupport)
                    .defaultMinSize(minHeight = MinTouchTarget),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(icon = AppIcons.SupportAgent, size = 26.dp, color = AppTheme.colors.onHero)
                Text(text = supportLabel, typography = AppTheme.type.bodyBold.ExtraBold, color = AppTheme.colors.onHero, modifier = Modifier.weight(1f))
                Icon(icon = AppIcons.ChevronRight, size = 24.dp, color = AppTheme.colors.onHero)
            }
        }
    }
}

/** Day close with nothing numeric on show (4.5): "Logged today" and the four meals as chips, a check on the ones eaten. */
@Composable
fun LoggedMealsCard(
    label: String,
    meals: List<Pair<MealSlot, String>>,
    done: Set<MealSlot>,
    modifier: Modifier = Modifier,
) {
    TodayCard(modifier = modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        Text(text = label, typography = AppTheme.type.secondaryStrong.Bold, color = AppTheme.colors.muted)
        meals.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                pair.forEach { (slot, title) ->
                    val tone = slot.tileColors()
                    val isDone = slot in done
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(AppTheme.shapes.inputLarge)
                            .background(if (isDone) tone.container else AppTheme.colors.soft)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(if (isDone) tone.content else AppTheme.colors.line),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isDone) Icon(icon = AppIcons.Check, size = 14.dp, color = AppTheme.colors.surface)
                        }
                        Text(text = title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
                    }
                }
            }
        }
    }
}

/** A label and a value on one line in a card: Day close's "Meals  4 of 4". */
@Composable
fun LabelValueCard(label: String, value: String, modifier: Modifier = Modifier) {
    TodayCard(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
            Text(text = value, typography = AppTheme.type.cardTitle.Tabular, color = AppTheme.colors.ink, textAlign = TextAlign.End)
        }
    }
}

/** A label over one line in a card (Day close's soft "Today / Partway through"). */
@Composable
fun LabelledLineCard(
    label: String,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    TodayCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, typography = AppTheme.type.secondaryStrong.Bold, color = AppTheme.colors.muted)
        value()
    }
}

private const val LABEL_ALPHA = 0.72f

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun TodayCardsPreview() {
    PreviewLightDark {
        val policy = currentVisibilityPolicy()
        val n = AppTheme.colors.nutrients
        val lunch = NutrientAmounts(kcal = 515.0, protein = 30.0, carbs = 50.0, fat = 20.0)
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
            WaterCard("Water", policy.resolve(Metric.Water, 750.0, Surface.Today), 3, 8, true, {}, AppIcons.Add)
            TodayMealCard(
                icon = AppIcons.LunchDining,
                iconColor = n.fat.content,
                title = "Lunch",
                time = "12:40 PM",
                calories = policy.resolve(Metric.Calories, 515.0, Surface.Today),
                macros = policy.macroLine(lunch, Surface.Today, style = MacroLineStyle.Compact),
                macroStyle = MacroLineStyle.Compact,
                items = listOf(MealCardItem("1", "Chicken adobo", "1 thigh · Estimate", policy.resolve(Metric.Calories, 310.0, Surface.Today, FormatContext(bare = true)))),
                note = MealCardNote(hunger = "Hunger level: Hungry", text = "Ate outside with Sam."),
                headerTrailing = { RoundIconButton(icon = AppIcons.Add, onClick = {}, size = MinTouchTarget) },
                itemRow = { MealCardRow(it, onClick = {}) },
            )
            MealRowCard(
                icon = AppIcons.DinnerDining,
                iconColor = n.protein.content,
                title = "Dinner",
                subtitle = "Usually around 7:00 PM",
                trailing = { RoundIconButton(icon = AppIcons.Add, onClick = {}, size = MinTouchTarget) },
            )
        }
    }
}
