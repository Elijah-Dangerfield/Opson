package com.opson.features.foods.impl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.ui.label
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.ToneIcon
import com.opson.libraries.ui.components.icon.ToneIconSize
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
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
import opson.libraries.resources.generated.resources.recipes_empty
import opson.libraries.resources.generated.resources.recipes_ingredient_count
import opson.libraries.resources.generated.resources.recipes_makes
import opson.libraries.resources.generated.resources.recipes_new
import opson.libraries.resources.generated.resources.recipes_open_cd
import opson.libraries.resources.generated.resources.recipes_scan
import opson.libraries.resources.generated.resources.recipes_section
import opson.libraries.resources.generated.resources.recipes_shared_tag
import opson.libraries.resources.generated.resources.repeat_empty
import opson.libraries.resources.generated.resources.repeat_new
import opson.libraries.resources.generated.resources.repeat_open_cd
import opson.libraries.resources.generated.resources.repeat_paused
import opson.libraries.resources.generated.resources.repeat_section
import opson.libraries.resources.generated.resources.repeat_until
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.opson.libraries.ui.components.BuilderCard
import opson.libraries.resources.generated.resources.builder_verify_foods_title
import opson.libraries.resources.generated.resources.builder_verify_foods_body
import opson.libraries.resources.generated.resources.foods_favorites
import opson.libraries.resources.generated.resources.foods_favorites_empty

/**
 * The Foods tab (design v3, 11.1): the title, a search bar that stays put while the list
 * scrolls, Create a meal and Add a food side by side, a Builder's Verify a food, then Favorites,
 * Saved meals, Repeating (`release.repeats`), Your foods and Recent, and the food data credit at
 * the end. Stateless; the entry point routes its events.
 */
@Composable
fun FoodsScreen(
    state: FoodsState,
    onAction: (FoodsAction) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(
                start = AppTheme.spacing.page,
                end = AppTheme.spacing.page,
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
    if (state.verifyOn) {
        item(key = "verify") {
            BuilderCard(
                title = stringResource(Res.string.builder_verify_foods_title),
                body = stringResource(Res.string.builder_verify_foods_body),
                onClick = { onAction(FoodsAction.VerifyTapped) },
                modifier = Modifier.padding(bottom = FoodsDefaults.CardGap),
            )
        }
    }
    if (state.loading) return
    rowsCard("favorites", Res.string.foods_favorites, state.favorites, onAction, empty = Res.string.foods_favorites_empty)
    if (state.savedMeals.isNotEmpty()) rowsCard("saved", Res.string.foods_saved, state.savedMeals, onAction)
    if (state.recipesOn) recipesCard(state.recipes, onAction)
    if (state.repeatsOn) repeatsCard(state.repeats, onAction)
    rowsCard("yours", Res.string.foods_yours, state.yourFoods, onAction, empty = Res.string.foods_yours_empty)
    if (state.recent.isNotEmpty()) rowsCard("recent", Res.string.foods_recent, state.recent, onAction)
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
        ToneIcon(icon = icon, tone = tone, size = ToneIconSize.Small)
        Spacer(Modifier.height(8.dp))
        Text(text = title, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
        Text(text = sub, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
    }
}

/**
 * A Foods card whose rows are lazy items of their own, so a long Your foods composes only the rows
 * on screen and an edit to one row recomposes that row. The card is drawn a slice per item: the
 * label carries its top corners, the last row its bottom corners and the gap below.
 */
private fun LazyListScope.rowsCard(
    section: String,
    label: StringResource,
    rows: List<FoodsRowUi>,
    onAction: (FoodsAction) -> Unit,
    empty: StringResource? = null,
) {
    item(key = "$section:label", contentType = "foods-card-label") {
        CardSlice(top = true, bottom = rows.isEmpty() && empty == null) { FoodsCardLabel(stringResource(label)) }
    }
    if (rows.isEmpty()) {
        if (empty != null) item(key = "$section:empty", contentType = "foods-card-note") { CardSlice(bottom = true) { FoodsCardNote(stringResource(empty)) } }
        return
    }
    itemsIndexed(rows, key = { _, row -> "$section:${row.key}" }, contentType = { _, _ -> "foods-row" }) { index, row ->
        CardSlice(bottom = index == rows.lastIndex) {
            FoodsRow(
                name = row.name,
                sub = row.sub,
                calories = row.calories,
                buttonIcon = AppIcons.Add,
                buttonDescription = stringResource(Res.string.foods_add_cd, row.name),
                onButton = { onAction(FoodsAction.RowTapped(row.target)) },
                onClick = { onAction(FoodsAction.RowTapped(row.target)) },
                showDivider = index < rows.lastIndex,
                unverified = row.unverified,
                verified = row.verified,
            )
        }
    }
}

/**
 * Repeating: each repeat with its meal and days, then New repeat. Drawn even with none, since
 * this card is the way in; it says what a repeat is until there is one.
 */
private fun LazyListScope.repeatsCard(rows: List<RepeatRowUi>, onAction: (FoodsAction) -> Unit) {
    item(key = "repeats:label", contentType = "foods-card-label") {
        CardSlice(top = true) { FoodsCardLabel(stringResource(Res.string.repeat_section)) }
    }
    if (rows.isEmpty()) {
        item(key = "repeats:empty", contentType = "foods-card-note") { CardSlice { FoodsCardNote(stringResource(Res.string.repeat_empty)) } }
    }
    itemsIndexed(rows, key = { _, row -> "repeats:${row.id}" }, contentType = { _, _ -> "foods-row" }) { _, row ->
        CardSlice {
            FoodsRow(
                name = row.name,
                sub = repeatSub(row),
                calories = row.calories,
                buttonIcon = AppIcons.EditOutline,
                buttonDescription = stringResource(Res.string.repeat_open_cd, row.name),
                onButton = { onAction(FoodsAction.RepeatTapped(row.id)) },
                onClick = { onAction(FoodsAction.RepeatTapped(row.id)) },
            )
        }
    }
    item(key = "repeats:new", contentType = "foods-card-action") {
        CardSlice(bottom = true) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .semantics { role = Role.Button }
                    .bounceClick(scaleDown = 0.98f, onClick = { onAction(FoodsAction.NewRepeatTapped) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
            ) {
                Icon(icon = AppIcons.Add, size = 24.dp, color = AppTheme.colors.accent)
                Text(text = stringResource(Res.string.repeat_new), typography = AppTheme.type.cardTitle, color = AppTheme.colors.accent)
            }
        }
    }
}

/**
 * Recipes (`release.recipes`): each recipe with what it makes and one serving's calories, then New
 * recipe and Scan a recipe code. Drawn even with none, since this card is the way in.
 */
private fun LazyListScope.recipesCard(rows: List<RecipeRowUi>, onAction: (FoodsAction) -> Unit) {
    item(key = "recipes:label", contentType = "foods-card-label") {
        CardSlice(top = true) { FoodsCardLabel(stringResource(Res.string.recipes_section)) }
    }
    if (rows.isEmpty()) {
        item(key = "recipes:empty", contentType = "foods-card-note") { CardSlice { FoodsCardNote(stringResource(Res.string.recipes_empty)) } }
    }
    itemsIndexed(rows, key = { _, row -> "recipes:${row.id}" }, contentType = { _, _ -> "foods-row" }) { _, row ->
        CardSlice {
            FoodsRow(
                name = row.name,
                sub = listOfNotNull(
                    stringResource(Res.string.recipes_makes, row.servings),
                    pluralStringResource(Res.plurals.recipes_ingredient_count, row.ingredientCount, row.ingredientCount),
                    stringResource(Res.string.recipes_shared_tag).takeIf { row.shared },
                ).joinToString(" · "),
                calories = row.perServing,
                buttonIcon = AppIcons.ChevronRight,
                buttonDescription = stringResource(Res.string.recipes_open_cd, row.name),
                onButton = { onAction(FoodsAction.RecipeTapped(row.id)) },
                onClick = { onAction(FoodsAction.RecipeTapped(row.id)) },
            )
        }
    }
    item(key = "recipes:new", contentType = "foods-card-action") {
        CardSlice { ActionLine(AppIcons.Add, stringResource(Res.string.recipes_new), onClick = { onAction(FoodsAction.NewRecipeTapped) }) }
    }
    item(key = "recipes:scan", contentType = "foods-card-action") {
        CardSlice(bottom = true) {
            ActionLine(AppIcons.QrCodeScanner, stringResource(Res.string.recipes_scan), onClick = { onAction(FoodsAction.ScanRecipeTapped) })
        }
    }
}

/** "Breakfast · Weekdays · until Nov 12", or "Paused · Breakfast · Weekdays". */
@Composable
private fun repeatSub(row: RepeatRowUi): String = listOfNotNull(
    stringResource(Res.string.repeat_paused).takeIf { row.paused },
    stringResource(row.slot.label),
    daysLabel(row.days),
    row.endDay?.let { stringResource(Res.string.repeat_until, DateCopy.monthDay(it)) },
).joinToString(" · ")

/** One slice of a [rowsCard]: the card's surface and row inset, with its top or bottom corners when it ends the card. */
@Composable
private fun CardSlice(top: Boolean = false, bottom: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val card = AppTheme.shapes.card
    val shape = when {
        top && bottom -> card
        card !is CornerBasedShape -> RectangleShape
        else -> card.copy(
            topStart = if (top) card.topStart else ZeroCornerSize,
            topEnd = if (top) card.topEnd else ZeroCornerSize,
            bottomEnd = if (bottom) card.bottomEnd else ZeroCornerSize,
            bottomStart = if (bottom) card.bottomStart else ZeroCornerSize,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (bottom) FoodsDefaults.CardGap else 0.dp)
            .clip(shape)
            .background(AppTheme.colors.surface)
            .padding(horizontal = FoodsDefaults.CardRowInset),
        content = content,
    )
}
