package com.opson.features.logger.impl

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.FoodPickRow
import com.opson.libraries.diary.ui.FoodRowsCard
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.CircularLoadingIndicator
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.MenuItem
import com.opson.libraries.ui.components.PopoverMenu
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonStyle
import com.opson.libraries.ui.components.chip.ActionChip
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.chip.PillChipSize
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.IconSize
import com.opson.libraries.ui.components.icon.IconTile
import com.opson.libraries.ui.components.icon.IconTileSize
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.SearchField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.color.ToneColors
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.barcode_scan_cd
import opson.libraries.resources.generated.resources.logger_add_to
import opson.libraries.resources.generated.resources.logger_add_to_this_meal
import opson.libraries.resources.generated.resources.logger_add_to_title
import opson.libraries.resources.generated.resources.logger_basket_count
import opson.libraries.resources.generated.resources.logger_close_cd
import opson.libraries.resources.generated.resources.logger_empty_body
import opson.libraries.resources.generated.resources.logger_empty_caption
import opson.libraries.resources.generated.resources.logger_empty_describe
import opson.libraries.resources.generated.resources.logger_empty_save
import opson.libraries.resources.generated.resources.logger_empty_title
import opson.libraries.resources.generated.resources.logger_first_run
import opson.libraries.resources.generated.resources.logger_for_meal
import opson.libraries.resources.generated.resources.logger_matches
import opson.libraries.resources.generated.resources.logger_meal_picker_cd
import opson.libraries.resources.generated.resources.logger_method_ate
import opson.libraries.resources.generated.resources.logger_method_describe
import opson.libraries.resources.generated.resources.photo_method
import opson.libraries.resources.generated.resources.logger_method_note
import opson.libraries.resources.generated.resources.logger_method_number
import opson.libraries.resources.generated.resources.logger_method_saved
import opson.libraries.resources.generated.resources.logger_method_yesterday
import opson.libraries.resources.generated.resources.logger_method_yesterday_cd
import opson.libraries.resources.generated.resources.logger_not_here
import opson.libraries.resources.generated.resources.logger_not_here_describe
import opson.libraries.resources.generated.resources.logger_not_here_or
import opson.libraries.resources.generated.resources.logger_not_here_save
import opson.libraries.resources.generated.resources.logger_offline_results
import opson.libraries.resources.generated.resources.logger_recent
import opson.libraries.resources.generated.resources.logger_search_clear_cd
import opson.libraries.resources.generated.resources.logger_search_placeholder
import opson.libraries.resources.generated.resources.logger_searching_cd
import opson.libraries.resources.generated.resources.logger_this_meal
import opson.libraries.resources.generated.resources.logger_usual_around
import opson.libraries.resources.generated.resources.logger_usual_at
import opson.libraries.resources.generated.resources.search_badge_branded
import opson.libraries.resources.generated.resources.search_badge_off
import opson.libraries.resources.generated.resources.search_badge_usda
import opson.libraries.resources.generated.resources.search_badge_yours
import opson.libraries.resources.generated.resources.search_off_credit
import opson.libraries.resources.generated.resources.search_options_cd
import opson.libraries.resources.generated.resources.search_tab_all
import opson.libraries.resources.generated.resources.search_tab_beverages
import opson.libraries.resources.generated.resources.search_tab_common
import opson.libraries.resources.generated.resources.search_tab_custom
import opson.libraries.resources.generated.resources.search_tab_empty_custom
import opson.libraries.resources.generated.resources.search_tab_empty_favorites
import opson.libraries.resources.generated.resources.search_tab_favorites
import opson.libraries.resources.generated.resources.search_tab_hint_beverages
import opson.libraries.resources.generated.resources.search_tab_hint_common
import opson.libraries.resources.generated.resources.search_tab_hint_restaurant
import opson.libraries.resources.generated.resources.search_tab_restaurant
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The logger sheet's body. On [LoggerPane.Search]: the title with the meal
 * dropdown, search, the method chips, the picks and the pinned footer. Describe
 * it and Saved meals are the other two panes. Stateless; the entry point puts
 * it in a `BottomSheet` with a fixed height, which is what lets the list scroll
 * inside it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoggerSheetContent(
    state: LoggerState,
    onAction: (LoggerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.pane != LoggerPane.Search) { onAction(LoggerAction.BackTapped) }
    Column(modifier = modifier.fillMaxSize()) {
        when (state.pane) {
            LoggerPane.Search -> SearchPane(state, onAction)
            LoggerPane.Describe -> DescribePane(state, onAction)
            LoggerPane.Saved -> SavedPane(state, onAction)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchPane(state: LoggerState, onAction: (LoggerAction) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        LoggerTitle(state, onAction)
        SearchField(
            value = state.query,
            onValueChange = { onAction(LoggerAction.SearchTyped(it)) },
            placeholder = stringResource(Res.string.logger_search_placeholder),
            focusOnOpen = state.focusSearch,
            onClear = { onAction(LoggerAction.SearchCleared) },
            clearDescription = stringResource(Res.string.logger_search_clear_cd),
            modifier = Modifier
                .padding(horizontal = AppTheme.spacing.page)
                .onFocusChanged { if (it.isFocused) onAction(LoggerAction.SearchFocused) },
            trailing = if (state.canScan) {
                { BarcodeButton(onClick = { onAction(LoggerAction.BarcodeTapped) }) }
            } else {
                null
            },
            action = state.options?.let { options ->
                { OptionsButton(active = options.active, onClick = { onAction(LoggerAction.SearchOptionsTapped) }) }
            },
        )
        state.options?.tabs?.let { tabs -> SearchTabs(tabs, state.options.tab, onAction) }
        if (state.methods.isNotEmpty()) {
            // One row that scrolls sideways, so the list starts sooner; the design lets the last chip run off the edge.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppTheme.spacing.chip)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AppTheme.spacing.page),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
            ) {
                state.methods.forEach { method -> MethodChip(method, onAction) }
            }
        }
        if (state.quickMethods.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = AppTheme.spacing.chip),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
            ) {
                state.quickMethods.forEach { method -> MethodChip(method, onAction) }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = AppTheme.spacing.page,
                end = AppTheme.spacing.page,
                top = AppTheme.spacing.section,
                bottom = AppTheme.spacing.page,
            ),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            if (!state.loading) body(state.body, describable = !state.pickingForMeal, onAction)
        }
        state.basket?.let { BasketBar(it, state.pickingForMeal, onAction) }
    }
}

/**
 * Close, "Add to" over the meal with its dropdown, and the day on the right. Picking for a meal
 * being put together, the title is "This meal", with no dropdown and no day.
 */
@Composable
private fun LoggerTitle(state: LoggerState, onAction: (LoggerAction) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val canPick = state.slots.size > 1 && !state.pickingForMeal
    val meal = stringResource(if (state.pickingForMeal) Res.string.logger_this_meal else state.slot.label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.chip, end = AppTheme.spacing.page, bottom = AppTheme.spacing.row),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
    ) {
        RoundIconButton(
            icon = AppIcons.Close.described(stringResource(Res.string.logger_close_cd)),
            onClick = { onAction(LoggerAction.CloseTapped) },
            size = AppTheme.spacing.touchTarget,
            iconSize = IconSize.Medium.dp,
            containerColor = AppTheme.colors.bg,
            contentColor = AppTheme.colors.ink,
        )
        Box(modifier = Modifier.weight(1f)) {
            val pickerDescription = stringResource(Res.string.logger_meal_picker_cd, meal)
            Column(
                modifier = if (canPick) {
                    Modifier
                        .semantics {
                            role = Role.DropdownList
                            contentDescription = pickerDescription
                        }
                        .bounceClick(scaleDown = 0.98f) { picking = true }
                } else {
                    Modifier
                },
            ) {
                Text(
                    text = stringResource(Res.string.logger_add_to_title),
                    typography = AppTheme.type.secondarySmallStrong,
                    color = AppTheme.colors.muted,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                    Text(text = meal, typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink, maxLines = 1)
                    if (canPick) Icon(icon = AppIcons.ExpandMore, size = IconSize.Medium, color = AppTheme.colors.ink)
                }
            }
            PopoverMenu(
                expanded = picking,
                onDismissRequest = { picking = false },
                alignment = Alignment.TopStart,
            ) {
                state.slots.forEachIndexed { index, slot ->
                    MenuItem(
                        label = stringResource(slot.label),
                        onClick = {
                            picking = false
                            onAction(LoggerAction.SlotPicked(slot))
                        },
                        icon = if (slot == state.slot) AppIcons.Check else null,
                        showDivider = index < state.slots.lastIndex,
                    )
                }
            }
        }
        if (!state.typing && !state.pickingForMeal) {
            Text(text = DateCopy.label(state.day), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.muted, maxLines = 1)
        }
    }
}

@Composable
private fun MethodChip(method: LoggerMethod, onAction: (LoggerAction) -> Unit) {
    val colors = AppTheme.colors
    val nutrients = colors.nutrients
    val plain = ToneColors(content = colors.ink, container = colors.soft)
    val chip = when (method) {
        LoggerMethod.Photo -> Chip(stringResource(Res.string.photo_method), AppIcons.PhotoCamera, nutrients.protein, LoggerAction.PhotoTapped)
        LoggerMethod.DescribeIt -> Chip(stringResource(Res.string.logger_method_describe), AppIcons.Mic, nutrients.fat, LoggerAction.DescribeItTapped)
        is LoggerMethod.Yesterday -> Chip(
            stringResource(Res.string.logger_method_yesterday),
            AppIcons.ContentCopy,
            nutrients.carbs,
            LoggerAction.YesterdayTapped,
            description = stringResource(Res.string.logger_method_yesterday_cd, stringResource(method.slot.sentenceName)),
        )
        LoggerMethod.SavedMeals -> Chip(stringResource(Res.string.logger_method_saved), AppIcons.Bookmark, nutrients.fiber, LoggerAction.SavedMealsTapped)
        LoggerMethod.AteNoDetails -> Chip(
            stringResource(Res.string.logger_method_ate),
            AppIcons.CheckCircle,
            ToneColors(content = colors.accent, container = colors.accentTint),
            LoggerAction.AteNoDetailsTapped,
        )
        LoggerMethod.JustANumber -> Chip(stringResource(Res.string.logger_method_number), AppIcons.Dialpad, plain, LoggerAction.JustANumberTapped)
        LoggerMethod.AddANote -> Chip(stringResource(Res.string.logger_method_note), AppIcons.EditNote, plain, LoggerAction.AddANoteTapped)
    }
    ActionChip(
        label = chip.label,
        icon = chip.icon,
        tone = chip.tone,
        onClick = { onAction(chip.action) },
        modifier = chip.description?.let { description -> Modifier.semantics { contentDescription = description } } ?: Modifier,
    )
}

private data class Chip(
    val label: String,
    val icon: IconResource,
    val tone: ToneColors,
    val action: LoggerAction,
    val description: String? = null,
)

/** [describable] is false when picking for a meal: Describe writes to the diary, so a miss offers only the name. */
private fun LazyListScope.body(body: LoggerBody, describable: Boolean, onAction: (LoggerAction) -> Unit) {
    when (body) {
        LoggerBody.FirstRun -> item(key = "first-run") {
            Text(
                text = stringResource(Res.string.logger_first_run),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
        }
        LoggerBody.Searching -> item(key = "searching") { SearchingIndicator() }
        is LoggerBody.NoResults -> {
            if (body.offline) item(key = "offline") { OfflineCaption() }
            item(key = "no-results") { NoResults(body.query, describable, onAction) }
        }
        is LoggerBody.Results -> {
            if (body.offline) item(key = "offline") { OfflineCaption() }
            item(key = "matches") { SectionLabel(pluralStringResource(Res.plurals.logger_matches, body.rows.size, body.rows.size)) }
            item(key = "results") { FoodRows(body.rows, onAction) }
            item(key = "not-here") { NotHere(describable, onAction) }
            // Open Food Facts' licence asks for this wherever its foods appear; each of its rows names it too.
            if (body.openFoodFacts) item(key = "off-credit") { Caption(stringResource(Res.string.search_off_credit)) }
        }
        is LoggerBody.Listed -> item(key = "listed") { FoodRows(body.rows, onAction) }
        is LoggerBody.TabEmpty -> item(key = "tab-empty") { Caption(stringResource(body.tab.emptyCopy())) }
        is LoggerBody.TabHint -> item(key = "tab-hint") { Caption(stringResource(body.tab.hintCopy())) }
        is LoggerBody.Browse -> {
            if (body.usual.isNotEmpty()) {
                item(key = "usual-label") {
                    val around = body.usualAround
                    SectionLabel(
                        if (around != null) {
                            stringResource(Res.string.logger_usual_around, DateCopy.time(around.hour, around.minute))
                        } else {
                            stringResource(Res.string.logger_usual_at, stringResource(body.slot.sentenceName))
                        },
                    )
                }
                item(key = "usual") { FoodRows(body.usual, onAction) }
            }
            if (body.recent.isNotEmpty()) {
                item(key = "recent-label") { SectionLabel(stringResource(Res.string.logger_recent)) }
                item(key = "recent") { FoodRows(body.recent, onAction) }
            }
        }
    }
}

/** Shown only once a search has run past [SEARCHING_INDICATOR_DELAY], so fast answers don't flash a spinner. */
@Composable
private fun SearchingIndicator() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SEARCHING_INDICATOR_DELAY)
        visible = true
    }
    val searchingDescription = stringResource(Res.string.logger_searching_cd)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.spacing.section)
            .semantics { contentDescription = searchingDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (visible) CircularLoadingIndicator()
    }
}

private val SEARCHING_INDICATOR_DELAY = 500.milliseconds

@Composable
private fun OfflineCaption() {
    Text(text = stringResource(Res.string.logger_offline_results), typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
}

@Composable
private fun FoodRows(rows: List<FoodRowUi>, onAction: (LoggerAction) -> Unit) {
    FoodRowsCard {
        rows.forEachIndexed { index, row ->
            FoodPickRow(
                name = row.name,
                subtitle = row.subtitle,
                amount = row.amount,
                macros = row.macros,
                calories = row.calories,
                picked = row.picked,
                onOpen = { onAction(LoggerAction.FoodNameTapped(row)) },
                onToggle = { onAction(LoggerAction.FoodToggleTapped(row)) },
                iconSeed = row.key,
                kind = row.kind,
                showDivider = index < rows.lastIndex,
                source = row.badge?.let { stringResource(it.label) },
                sourceAccent = row.badge == SourceBadge.YourFood,
                verified = row.verified,
            )
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text = text, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
}

/** The search options button at the end of the search bar; tinted while a sort or database other than the default is on. */
@Composable
private fun OptionsButton(active: Boolean, onClick: () -> Unit) {
    val description = stringResource(Res.string.search_options_cd)
    IconTile(
        icon = AppIcons.Tune,
        contentColor = if (active) AppTheme.colors.accent else AppTheme.colors.ink,
        containerColor = if (active) AppTheme.colors.accentTint else AppTheme.colors.soft,
        size = IconTileSize.Large,
        modifier = Modifier
            .bounceClick(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
    )
}

/** All, Favorites, Custom, Common, Restaurant, Beverages: one row that scrolls sideways. */
@Composable
private fun SearchTabs(tabs: List<SearchTab>, selected: SearchTab, onAction: (LoggerAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppTheme.spacing.chip)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppTheme.spacing.page),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
    ) {
        tabs.forEach { tab ->
            PillChip(
                label = stringResource(tab.label),
                selected = tab == selected,
                onClick = { onAction(LoggerAction.TabPicked(tab)) },
                size = PillChipSize.Compact,
                role = Role.Tab,
            )
        }
    }
}

private val SearchTab.label: StringResource
    get() = when (this) {
        SearchTab.All -> Res.string.search_tab_all
        SearchTab.Favorites -> Res.string.search_tab_favorites
        SearchTab.Custom -> Res.string.search_tab_custom
        SearchTab.Common -> Res.string.search_tab_common
        SearchTab.Restaurant -> Res.string.search_tab_restaurant
        SearchTab.Beverages -> Res.string.search_tab_beverages
    }

/** Before typing on a tab that searches the catalog. */
private fun SearchTab.hintCopy(): StringResource = when (this) {
    SearchTab.Restaurant -> Res.string.search_tab_hint_restaurant
    SearchTab.Beverages -> Res.string.search_tab_hint_beverages
    else -> Res.string.search_tab_hint_common
}

/** A Favorites or Custom tab with nothing in it yet. */
private fun SearchTab.emptyCopy(): StringResource =
    if (this == SearchTab.Favorites) Res.string.search_tab_empty_favorites else Res.string.search_tab_empty_custom

private val SourceBadge.label: StringResource
    get() = when (this) {
        SourceBadge.Usda -> Res.string.search_badge_usda
        SourceBadge.UsdaBranded -> Res.string.search_badge_branded
        SourceBadge.OpenFoodFacts -> Res.string.search_badge_off
        SourceBadge.YourFood -> Res.string.search_badge_yours
    }

/** "Not here? Describe it or save the name", under the results. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotHere(describable: Boolean, onAction: (LoggerAction) -> Unit) {
    val muted = AppTheme.type.secondary
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(Res.string.logger_not_here), typography = muted, color = AppTheme.colors.muted)
        if (describable) {
            InlineLink(stringResource(Res.string.logger_not_here_describe)) { onAction(LoggerAction.DescribeTermTapped) }
            Text(text = stringResource(Res.string.logger_not_here_or), typography = muted, color = AppTheme.colors.muted)
        }
        InlineLink(stringResource(Res.string.logger_not_here_save)) { onAction(LoggerAction.SaveNameTapped) }
    }
}

/** A bold accent text button with a 48 dp tall touch area: "Describe it", "Stop asking this". */
@Composable
internal fun InlineLink(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.97f, onClick = onClick)
            .defaultMinSize(minHeight = AppTheme.spacing.touchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.accent)
    }
}

/** v3's square barcode button inside the empty search bar: a soft tile, ink glyph, a full touch target. */
@Composable
private fun BarcodeButton(onClick: () -> Unit) {
    val description = stringResource(Res.string.barcode_scan_cd)
    IconTile(
        icon = AppIcons.BarcodeScanner,
        contentColor = AppTheme.colors.ink,
        containerColor = AppTheme.colors.soft,
        size = IconTileSize.Large,
        modifier = Modifier
            .bounceClick(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
    )
}

/** A miss is never a dead end: describe it, or keep just the name. */
@Composable
private fun NoResults(query: String, describable: Boolean, onAction: (LoggerAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppTheme.spacing.section * 3, start = AppTheme.spacing.row, end = AppTheme.spacing.row),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        IconTile(
            icon = AppIcons.SearchOff,
            contentColor = AppTheme.colors.muted,
            containerColor = AppTheme.colors.soft,
            size = IconTileSize.Huge,
        )
        Text(
            text = stringResource(Res.string.logger_empty_title, query),
            typography = AppTheme.type.sheetTitle,
            color = AppTheme.colors.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.logger_empty_body),
            typography = AppTheme.type.body,
            color = AppTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            if (describable) {
                ButtonPrimary(
                    onClick = { onAction(LoggerAction.DescribeTermTapped) },
                    icon = AppIcons.Mic,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.logger_empty_describe, query))
                }
            }
            ButtonPrimary(
                onClick = { onAction(LoggerAction.SaveNameTapped) },
                style = if (describable) ButtonStyle.Tonal else ButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.logger_empty_save))
            }
        }
        Text(
            text = stringResource(Res.string.logger_empty_caption),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BasketBar(basket: BasketFooter, pickingForMeal: Boolean, onAction: (LoggerAction) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = AppTheme.colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppTheme.spacing.page,
                    end = AppTheme.spacing.page,
                    top = AppTheme.spacing.row,
                    bottom = AppTheme.spacing.page,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(Res.plurals.logger_basket_count, basket.count, basket.count),
                    typography = AppTheme.type.cardTitle,
                    color = AppTheme.colors.ink,
                    maxLines = 1,
                )
                Text(
                    text = basket.names,
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NutrientText(
                metric = Metric.Calories,
                display = basket.calories,
                typography = AppTheme.type.numeric,
                color = AppTheme.colors.muted,
                maxLines = 1,
            )
            ButtonPrimary(onClick = { onAction(LoggerAction.AddToMealTapped) }) {
                Text(
                    if (pickingForMeal) {
                        stringResource(Res.string.logger_add_to_this_meal)
                    } else {
                        stringResource(Res.string.logger_add_to, stringResource(basket.slot.sentenceName))
                    },
                )
            }
        }
    }
}

/** "For dinner", the overline on the Describe and Saved panes. */
@Composable
internal fun forMeal(slot: MealSlot): String = stringResource(Res.string.logger_for_meal, stringResource(slot.sentenceName))
