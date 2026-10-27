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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.ui.FoodRowsCard
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.MenuItem
import com.opson.libraries.ui.components.PopoverMenu
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonStyle
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.chip.PillChipSize
import com.opson.libraries.ui.components.chip.PillChipStyle
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconSize
import com.opson.libraries.ui.components.icon.ToneIcon
import com.opson.libraries.ui.components.icon.ToneIconSize
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.SearchField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.adding_meal_chip_cd
import opson.libraries.resources.generated.resources.adding_no_foods_yet
import opson.libraries.resources.generated.resources.adding_no_saved_match
import opson.libraries.resources.generated.resources.adding_not_finding
import opson.libraries.resources.generated.resources.adding_not_finding_describe
import opson.libraries.resources.generated.resources.adding_not_finding_photo
import opson.libraries.resources.generated.resources.adding_not_finding_save
import opson.libraries.resources.generated.resources.adding_scope_all
import opson.libraries.resources.generated.resources.adding_scope_saved
import opson.libraries.resources.generated.resources.adding_scope_yours
import opson.libraries.resources.generated.resources.adding_search_back_cd
import opson.libraries.resources.generated.resources.barcode_scan_cd
import opson.libraries.resources.generated.resources.logger_add_to_this_meal
import opson.libraries.resources.generated.resources.logger_basket_count
import opson.libraries.resources.generated.resources.logger_empty_body
import opson.libraries.resources.generated.resources.logger_empty_caption
import opson.libraries.resources.generated.resources.logger_empty_describe
import opson.libraries.resources.generated.resources.logger_empty_save
import opson.libraries.resources.generated.resources.logger_empty_title
import opson.libraries.resources.generated.resources.logger_first_run
import opson.libraries.resources.generated.resources.logger_loading_cd
import opson.libraries.resources.generated.resources.logger_offline_results
import opson.libraries.resources.generated.resources.logger_recent
import opson.libraries.resources.generated.resources.logger_saved_empty
import opson.libraries.resources.generated.resources.logger_search_clear_cd
import opson.libraries.resources.generated.resources.logger_source_nevo
import opson.libraries.resources.generated.resources.logger_search_placeholder
import opson.libraries.resources.generated.resources.logger_searching_cd
import opson.libraries.resources.generated.resources.logger_usual_around
import opson.libraries.resources.generated.resources.logger_usual_at
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

/** Full-screen search (design sync 4, 13.4c) in its scaffold. */
@Composable
fun FoodSearchScreen(state: FoodSearchState, onAction: (SearchAction) -> Unit, modifier: Modifier = Modifier) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        FoodSearchContent(state, onAction, Modifier.padding(padding))
    }
}

/**
 * Back, the field with the barcode button, the chips (the meal, All, Your foods, Saved meals),
 * then the list. Stateless apart from the meal menu; previews and the leak test draw it
 * without the scaffold.
 */
@Composable
internal fun FoodSearchContent(state: FoodSearchState, onAction: (SearchAction) -> Unit, modifier: Modifier = Modifier) {
    // Whatever opens over search (options, scanner, photo, Describe, the meal menu) puts the
    // keyboard away first; left up, it covers half of what just opened.
    val focusManager = LocalFocusManager.current
    val away: (SearchAction) -> Unit = { action ->
        focusManager.clearFocus()
        onAction(action)
    }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppTheme.spacing.chip, end = AppTheme.spacing.page, top = AppTheme.spacing.chip),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
        ) {
            RoundIconButton(
                icon = AppIcons.ArrowBack.described(stringResource(Res.string.adding_search_back_cd)),
                onClick = { away(SearchAction.BackTapped) },
                size = AppTheme.spacing.touchTarget,
                iconSize = IconSize.Medium.dp,
                containerColor = AppTheme.colors.bg,
                contentColor = AppTheme.colors.ink,
            )
            SearchField(
                value = state.query,
                onValueChange = { onAction(SearchAction.SearchTyped(it)) },
                placeholder = stringResource(Res.string.logger_search_placeholder),
                focusOnOpen = state.focusSearch,
                onClear = { onAction(SearchAction.SearchCleared) },
                clearDescription = stringResource(Res.string.logger_search_clear_cd),
                modifier = Modifier.weight(1f),
                trailing = if (state.canScan) {
                    { BarcodeButton(onClick = { away(SearchAction.BarcodeTapped) }) }
                } else {
                    null
                },
                action = state.options?.let { options ->
                    { OptionsButton(active = options.active, onClick = { away(SearchAction.SearchOptionsTapped) }) }
                },
            )
        }
        Chips(state, onAction, onOpenMenu = { focusManager.clearFocus() })
        state.options?.tabs?.let { tabs -> SearchTabs(tabs, state.options.tab, onAction) }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = AppTheme.spacing.page,
                end = AppTheme.spacing.page,
                top = AppTheme.spacing.row,
                bottom = AppTheme.spacing.page,
            ),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            if (state.loading) {
                item(key = "loading") { DelayedLoadingIndicator(contentDescription = stringResource(Res.string.logger_loading_cd)) }
            } else {
                body(state, away, onAction)
            }
        }
        state.picks?.let { PicksBar(it, onAction) }
    }
}

/** The meal dropdown, then All, Your foods and Saved meals: one row that scrolls sideways. */
@Composable
private fun Chips(state: FoodSearchState, onAction: (SearchAction) -> Unit, onOpenMenu: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppTheme.spacing.row)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppTheme.spacing.page),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
    ) {
        if (!state.pickingForMeal) MealChip(state, onAction, onOpenMenu)
        state.scopes.forEach { scope ->
            PillChip(
                label = stringResource(scope.label),
                selected = scope == state.scope,
                onClick = { onAction(SearchAction.ScopePicked(scope)) },
                role = Role.Tab,
                style = PillChipStyle.Tint,
            )
        }
    }
}

@Composable
private fun MealChip(state: FoodSearchState, onAction: (SearchAction) -> Unit, onOpenMenu: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val meal = stringResource(state.slot.label)
    val canPick = state.slots.size > 1
    val description = stringResource(Res.string.adding_meal_chip_cd, meal)
    Box {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = AppTheme.spacing.touchTarget)
                .clip(AppTheme.shapes.input)
                .background(AppTheme.colors.accentTint)
                .semantics(mergeDescendants = true) {
                    role = Role.DropdownList
                    contentDescription = description
                }
                .bounceClick(scaleDown = 0.97f) {
                    if (canPick) {
                        onOpenMenu()
                        picking = true
                    }
                }
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = meal, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.accent, maxLines = 1)
            if (canPick) Icon(icon = AppIcons.ArrowDropDown, size = IconSize.Medium, color = AppTheme.colors.accent)
        }
        PopoverMenu(expanded = picking, onDismissRequest = { picking = false }, alignment = Alignment.TopStart) {
            state.slots.forEachIndexed { index, slot ->
                MenuItem(
                    label = stringResource(slot.label),
                    onClick = {
                        picking = false
                        onAction(SearchAction.SlotPicked(slot))
                    },
                    icon = if (slot == state.slot) AppIcons.Check else null,
                    showDivider = index < state.slots.lastIndex,
                )
            }
        }
    }
}

private fun LazyListScope.body(state: FoodSearchState, away: (SearchAction) -> Unit, onAction: (SearchAction) -> Unit) {
    when (val body = state.body) {
        SearchBody.FirstRun -> item(key = "first-run") { Caption(stringResource(Res.string.logger_first_run)) }
        SearchBody.Searching -> item(key = "searching") {
            DelayedLoadingIndicator(contentDescription = stringResource(Res.string.logger_searching_cd))
        }
        is SearchBody.NoResults -> {
            if (body.offline) item(key = "offline") { Caption(stringResource(Res.string.logger_offline_results)) }
            item(key = "no-results") { NoResults(state, body.query, away, onAction) }
        }
        is SearchBody.Results -> {
            if (body.offline) item(key = "offline") { Caption(stringResource(Res.string.logger_offline_results)) }
            item(key = "results", contentType = "rows") { FoodRows(body.rows, away, onAction) }
            item(key = "not-finding") { NotFinding(state, away, onAction) }
            // Open Food Facts' licence asks for this wherever its foods appear; each of its rows names it too.
            if (body.openFoodFacts) item(key = "off-credit") { Caption(stringResource(Res.string.search_off_credit)) }
            // RIVM's citation, word for word, wherever NEVO's foods show.
            if (body.nevo) item(key = "nevo-credit") { Caption(stringResource(Res.string.logger_source_nevo)) }
        }
        is SearchBody.Listed -> item(key = "listed", contentType = "rows") { FoodRows(body.rows, away, onAction) }
        SearchBody.NoFoodsYet -> item(key = "no-foods") { Caption(stringResource(Res.string.adding_no_foods_yet)) }
        is SearchBody.TabEmpty -> item(key = "tab-empty") { Caption(stringResource(body.tab.emptyCopy())) }
        is SearchBody.TabHint -> item(key = "tab-hint") { Caption(stringResource(body.tab.hintCopy())) }
        is SearchBody.SavedMeals -> item(key = "saved") {
            when {
                body.meals.isNotEmpty() -> FoodRowsCard {
                    body.meals.forEachIndexed { index, meal ->
                        AddMealRow(meal, onAdd = { onAction(SearchAction.SavedMealAddTapped(meal.id)) }, divider = index > 0)
                    }
                }
                state.typing -> Caption(stringResource(Res.string.adding_no_saved_match))
                else -> Caption(stringResource(Res.string.logger_saved_empty))
            }
        }
        is SearchBody.Browse -> {
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
                item(key = "usual", contentType = "rows") { FoodRows(body.usual, away, onAction) }
            }
            if (body.recent.isNotEmpty()) {
                item(key = "recent-label") { SectionLabel(stringResource(Res.string.logger_recent)) }
                item(key = "recent", contentType = "rows") { FoodRows(body.recent, away, onAction) }
            }
        }
    }
}

@Composable
private fun FoodRows(rows: List<FoodRowUi>, away: (SearchAction) -> Unit, onAction: (SearchAction) -> Unit) {
    FoodRowsCard {
        rows.forEachIndexed { index, row ->
            AddFoodRow(
                row = row,
                onOpen = { away(SearchAction.FoodNameTapped(row)) },
                onAdd = { onAction(SearchAction.AddTapped(row)) },
                divider = index > 0,
            )
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text = text, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
}

/** "Not finding it?" under the results: Photograph it, Describe it, Save the name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotFinding(state: FoodSearchState, away: (SearchAction) -> Unit, onAction: (SearchAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.groupedList)
            .background(AppTheme.colors.surface)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(text = stringResource(Res.string.adding_not_finding), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
            if (state.canPhotograph) InlineLink(stringResource(Res.string.adding_not_finding_photo)) { away(SearchAction.PhotographTapped) }
            if (state.canDescribe) InlineLink(stringResource(Res.string.adding_not_finding_describe)) { away(SearchAction.DescribeTapped) }
            InlineLink(stringResource(Res.string.adding_not_finding_save)) { onAction(SearchAction.SaveNameTapped) }
        }
    }
}

/** A miss is never a dead end (3.6): describe it, or keep just the name. */
@Composable
private fun NoResults(state: FoodSearchState, query: String, away: (SearchAction) -> Unit, onAction: (SearchAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppTheme.spacing.section * 2, start = AppTheme.spacing.row, end = AppTheme.spacing.row),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        ToneIcon(icon = AppIcons.SearchOff, color = AppTheme.colors.muted, size = ToneIconSize.Huge)
        Text(
            text = stringResource(Res.string.logger_empty_title, query),
            typography = AppTheme.type.sheetTitle,
            color = AppTheme.colors.ink,
            textAlign = TextAlign.Center,
        )
        Text(text = stringResource(Res.string.logger_empty_body), typography = AppTheme.type.body, color = AppTheme.colors.muted, textAlign = TextAlign.Center)
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            if (state.canDescribe) {
                ButtonPrimary(onClick = { away(SearchAction.DescribeTapped) }, icon = AppIcons.Mic, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.logger_empty_describe, query))
                }
            }
            ButtonPrimary(
                onClick = { onAction(SearchAction.SaveNameTapped) },
                style = if (state.canDescribe) ButtonStyle.Tonal else ButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.logger_empty_save))
            }
            if (state.canPhotograph) {
                ButtonPrimary(onClick = { away(SearchAction.PhotographTapped) }, style = ButtonStyle.Tonal, icon = AppIcons.PhotoCamera, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.adding_not_finding_photo))
                }
            }
        }
        Text(text = stringResource(Res.string.logger_empty_caption), typography = AppTheme.type.secondary, color = AppTheme.colors.muted, textAlign = TextAlign.Center)
    }
}

/** Create a meal's footer: what is picked, and "Add to meal". */
@Composable
private fun PicksBar(picks: PicksFooter, onAction: (SearchAction) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = AppTheme.colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = AppTheme.spacing.row, bottom = AppTheme.spacing.page),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(Res.plurals.logger_basket_count, picks.count, picks.count),
                    typography = AppTheme.type.cardTitle,
                    color = AppTheme.colors.ink,
                    maxLines = 1,
                )
                Text(text = picks.names, typography = AppTheme.type.secondary, color = AppTheme.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ButtonPrimary(onClick = { onAction(SearchAction.AddPicksTapped) }) {
                Text(stringResource(Res.string.logger_add_to_this_meal))
            }
        }
    }
}

/** The search options button at the end of the field; tinted while a sort or database other than the default is on. */
@Composable
private fun OptionsButton(active: Boolean, onClick: () -> Unit) {
    val description = stringResource(Res.string.search_options_cd)
    ToneIcon(
        icon = AppIcons.Tune,
        color = if (active) AppTheme.colors.accent else AppTheme.colors.ink,
        size = ToneIconSize.Large,
        modifier = Modifier
            .bounceClick(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
    )
}

/** The barcode button inside the empty field: an ink glyph with a full touch target. */
@Composable
private fun BarcodeButton(onClick: () -> Unit) {
    val description = stringResource(Res.string.barcode_scan_cd)
    ToneIcon(
        icon = AppIcons.BarcodeScanner,
        color = AppTheme.colors.ink,
        size = ToneIconSize.Large,
        modifier = Modifier
            .bounceClick(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
    )
}

/** All, Favorites, Custom, Common, Restaurant, Beverages (`release.searchOptions`): one row that scrolls sideways. */
@Composable
private fun SearchTabs(tabs: List<SearchTab>, selected: SearchTab, onAction: (SearchAction) -> Unit) {
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
                onClick = { onAction(SearchAction.TabPicked(tab)) },
                size = PillChipSize.Compact,
                role = Role.Tab,
            )
        }
    }
}

private val SearchScope.label: StringResource
    get() = when (this) {
        SearchScope.All -> Res.string.adding_scope_all
        SearchScope.YourFoods -> Res.string.adding_scope_yours
        SearchScope.SavedMeals -> Res.string.adding_scope_saved
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
