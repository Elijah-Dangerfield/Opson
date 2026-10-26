package com.opson.features.foods.impl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.IconTile
import com.opson.libraries.ui.components.icon.IconTileSize
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.color.ToneColors
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_add_cd
import opson.libraries.resources.generated.resources.foods_add_food
import opson.libraries.resources.generated.resources.foods_add_food_sub
import opson.libraries.resources.generated.resources.foods_create_meal
import opson.libraries.resources.generated.resources.foods_create_meal_sub
import opson.libraries.resources.generated.resources.foods_credit
import opson.libraries.resources.generated.resources.foods_recent
import opson.libraries.resources.generated.resources.foods_saved
import opson.libraries.resources.generated.resources.foods_search
import opson.libraries.resources.generated.resources.foods_title
import opson.libraries.resources.generated.resources.foods_yours
import opson.libraries.resources.generated.resources.foods_yours_empty
import org.jetbrains.compose.resources.stringResource

/**
 * The Foods tab (design v3, 11.1): the title, a search bar that stays put while the list
 * scrolls, Create a meal and Add a food side by side, then Saved meals, Your foods and Recent,
 * and the food data credit at the end. Stateless; the entry point routes its events.
 */
@Composable
fun FoodsScreen(
    state: FoodsState,
    onAction: (FoodsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = FoodsDefaults.PagePadding,
                end = FoodsDefaults.PagePadding,
                bottom = AppTheme.spacing.section * 2,
            ),
        ) {
            foodsContent(state, onAction)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.foodsContent(state: FoodsState, onAction: (FoodsAction) -> Unit) {
    item(key = "title") {
        Text(
            text = stringResource(Res.string.foods_title),
            typography = AppTheme.type.h1,
            color = AppTheme.colors.ink,
            modifier = Modifier.padding(start = 2.dp, top = 28.dp, bottom = 14.dp),
        )
    }
    stickyHeader(key = "search") { SearchBar(onClick = { onAction(FoodsAction.SearchTapped) }) }
    // Each item carries the gap below it rather than the list spacing them, so the gap under the
    // sticky search belongs to the search and stays page-coloured while rows scroll under it.
    item(key = "tiles") { Tiles(onAction, Modifier.padding(bottom = FoodsDefaults.CardGap)) }
    if (state.loading) return
    if (state.savedMeals.isNotEmpty()) {
        item(key = "saved") {
            RowsCard(stringResource(Res.string.foods_saved), state.savedMeals, onAction, Modifier.padding(bottom = FoodsDefaults.CardGap))
        }
    }
    item(key = "yours") {
        RowsCard(
            stringResource(Res.string.foods_yours),
            state.yourFoods,
            onAction,
            Modifier.padding(bottom = FoodsDefaults.CardGap),
            empty = stringResource(Res.string.foods_yours_empty),
        )
    }
    if (state.recent.isNotEmpty()) {
        item(key = "recent") {
            RowsCard(stringResource(Res.string.foods_recent), state.recent, onAction, Modifier.padding(bottom = FoodsDefaults.CardGap))
        }
    }
    // Open Food Facts rows are ODbL data, which asks for this credit wherever they show.
    item(key = "credit") {
        Text(
            text = stringResource(Res.string.foods_credit),
            typography = AppTheme.type.caption,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

/** A button drawn as a search field: search itself lives in the logger. Opaque, so rows scroll under it. */
@Composable
private fun SearchBar(onClick: () -> Unit) {
    val label = stringResource(Res.string.foods_search)
    Column(modifier = Modifier.fillMaxWidth().background(AppTheme.colors.bg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .clip(AppTheme.shapes.card)
                .background(AppTheme.colors.surface)
                .semantics { role = Role.Button }
                .bounceClick(scaleDown = 0.99f, onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon = AppIcons.Search, size = 24.dp, color = AppTheme.colors.ink)
            Text(text = label, typography = AppTheme.type.body, color = AppTheme.colors.muted, maxLines = 1)
        }
        // The gap below the bar is part of it, so a row scrolling up meets the page colour first.
        Spacer(Modifier.height(FoodsDefaults.CardGap))
    }
}

@Composable
private fun Tiles(onAction: (FoodsAction) -> Unit, modifier: Modifier = Modifier) {
    val nutrients = AppTheme.colors.nutrients
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Tile(
            icon = AppIcons.LocalDining,
            tone = nutrients.protein,
            title = stringResource(Res.string.foods_create_meal),
            sub = stringResource(Res.string.foods_create_meal_sub),
            onClick = { onAction(FoodsAction.CreateMealTapped) },
        )
        Tile(
            icon = AppIcons.PostAdd,
            tone = nutrients.fiber,
            title = stringResource(Res.string.foods_add_food),
            sub = stringResource(Res.string.foods_add_food_sub),
            onClick = { onAction(FoodsAction.AddFoodTapped) },
        )
    }
}

@Composable
private fun RowScope.Tile(icon: IconResource, tone: ToneColors, title: String, sub: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(AppTheme.shapes.card)
            .background(AppTheme.colors.surface)
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconTile(icon = icon, tone = tone, size = IconTileSize.Small)
        Spacer(Modifier.height(8.dp))
        Text(text = title, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
        Text(text = sub, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
    }
}

@Composable
private fun RowsCard(
    label: String,
    rows: List<FoodsRowUi>,
    onAction: (FoodsAction) -> Unit,
    modifier: Modifier = Modifier,
    empty: String? = null,
) {
    FoodsCard(modifier = modifier, label = label) {
        if (rows.isEmpty() && empty != null) FoodsCardNote(empty)
        rows.forEachIndexed { index, row ->
            FoodsRow(
                name = row.name,
                sub = row.sub,
                calories = row.calories,
                buttonIcon = AppIcons.Add,
                buttonDescription = stringResource(Res.string.foods_add_cd, row.name),
                onButton = { onAction(FoodsAction.RowTapped(row.target)) },
                onClick = { onAction(FoodsAction.RowTapped(row.target)) },
                showDivider = index < rows.lastIndex,
            )
        }
    }
}
