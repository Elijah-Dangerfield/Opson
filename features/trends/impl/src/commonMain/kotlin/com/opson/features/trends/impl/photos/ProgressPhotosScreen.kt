package com.opson.features.trends.impl.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.HeaderIconButton
import com.opson.libraries.ui.components.MenuItem
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.PopoverMenu
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonStyle
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.Dimension
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.photos_compare
import opson.libraries.resources.generated.resources.photos_delete_all
import opson.libraries.resources.generated.resources.photos_delete_all_body
import opson.libraries.resources.generated.resources.photos_delete_all_title
import opson.libraries.resources.generated.resources.photos_empty
import opson.libraries.resources.generated.resources.photos_more
import opson.libraries.resources.generated.resources.photos_saved_pair
import opson.libraries.resources.generated.resources.photos_saved_title
import opson.libraries.resources.generated.resources.photos_take
import opson.libraries.resources.generated.resources.photos_title
import opson.libraries.resources.generated.resources.trends_loading_cd
import opson.libraries.resources.generated.resources.trends_weight_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Progress photos (design sync 2, 8.4): months of photos with a date tag
 * each, Compare and Take photos pinned under them. Nothing but the lock panel
 * until the phone's lock is passed.
 */
@Composable
fun ProgressPhotosScreen(
    state: ProgressPhotosState,
    onAction: (ProgressPhotosAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(
        modifier = modifier,
        containerColor = AppTheme.colors.bg.color,
        bottomBar = { if (state.lock == PhotoLockState.Unlocked) Actions(state, onAction) },
    ) { padding ->
        ProgressPhotosContent(state, onAction, Modifier.padding(padding))
    }
    if (state.confirmDeleteAll) {
        DeleteDialog(
            title = stringResource(Res.string.photos_delete_all_title),
            body = stringResource(Res.string.photos_delete_all_body),
            onConfirm = { onAction(ProgressPhotosAction.DeleteAllConfirmed) },
            onCancel = { onAction(ProgressPhotosAction.DeleteAllCancelled) },
        )
    }
}

/**
 * The page without its scaffold, for previews and the leak test. A lazy grid: a year of weekly
 * sets is well over a hundred tiles, and only those on screen are composed, read and decoded.
 */
@Composable
internal fun ProgressPhotosContent(
    state: ProgressPhotosState,
    onAction: (ProgressPhotosAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Rows of a month sit a row apart; a new block (a card, a month) a section apart.
    val sectionGap = Modifier.padding(top = AppTheme.spacing.section - AppTheme.spacing.row)
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.section),
        horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        wide("header") { Header(state, onAction) }
        if (state.lock != PhotoLockState.Unlocked) {
            wide("lock") { LockPanel(state.lock, onUnlock = { onAction(ProgressPhotosAction.UnlockTapped) }, modifier = sectionGap) }
            return@LazyVerticalGrid
        }
        wide("privacy") { PrivacyCard(sectionGap) }
        if (state.loading) {
            wide("loading") {
                Box(sectionGap) { DelayedLoadingIndicator(contentDescription = stringResource(Res.string.trends_loading_cd)) }
            }
        }
        if (state.empty) {
            wide("empty") {
                Text(text = stringResource(Res.string.photos_empty), typography = AppTheme.type.body, color = AppTheme.colors.muted, modifier = sectionGap)
            }
        }
        state.months.forEach { month ->
            wide("month-${month.year}-${month.month.ordinal}", contentType = "month") { MonthLabel(month, sectionGap) }
            items(month.tiles, key = { "photo-${it.id}" }, contentType = { "photo" }) { tile ->
                Tile(tile, onClick = { onAction(ProgressPhotosAction.TileTapped(tile.id)) }, onAction = onAction)
            }
        }
        if (state.saved.isNotEmpty()) {
            wide("saved") {
                Text(
                    text = stringResource(Res.string.photos_saved_title),
                    typography = AppTheme.type.sectionLabel,
                    color = AppTheme.colors.muted,
                    modifier = sectionGap,
                )
            }
            state.saved.forEach { pair ->
                wide("pair-${pair.id}", contentType = "pair") { SavedPairView(pair, onAction) }
            }
        }
    }
}

/** A row the width of the grid: the header, a card, a month's name, a saved pair. */
private fun LazyGridScope.wide(key: String, contentType: String = key, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = contentType) { content() }

@Composable
private fun Header(state: ProgressPhotosState, onAction: (ProgressPhotosAction) -> Unit) {
    PageHeader(
        title = stringResource(Res.string.photos_title),
        backLabel = stringResource(Res.string.trends_weight_title),
        onBack = { onAction(ProgressPhotosAction.BackTapped) },
    ) {
        if (state.lock == PhotoLockState.Unlocked) {
            Box {
                HeaderIconButton(
                    icon = AppIcons.MoreVert.described(stringResource(Res.string.photos_more)),
                    onClick = { onAction(ProgressPhotosAction.MenuTapped) },
                    selected = state.menuOpen,
                )
                PopoverMenu(expanded = state.menuOpen, onDismissRequest = { onAction(ProgressPhotosAction.MenuDismissed) }) {
                    MenuItem(
                        label = stringResource(Res.string.photos_delete_all),
                        icon = AppIcons.Delete,
                        onClick = { onAction(ProgressPhotosAction.DeleteAllTapped) },
                        showDivider = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthLabel(month: PhotoMonth, modifier: Modifier = Modifier) {
    Text(
        text = if (month.showYear) "${DateCopy.longMonth(month.month)} ${month.year}" else DateCopy.longMonth(month.month),
        typography = AppTheme.type.sectionLabel,
        color = AppTheme.colors.muted,
        modifier = modifier,
    )
}

/**
 * A photo that asks for its thumbnail once it is composed, which in the lazy grid means once it
 * is on screen or about to be.
 */
@Composable
private fun Tile(tile: PhotoTile, onClick: () -> Unit, onAction: (ProgressPhotosAction) -> Unit, modifier: Modifier = Modifier) {
    if (tile.image == null) {
        LaunchedEffect(tile.id) { onAction(ProgressPhotosAction.ThumbnailWanted(tile.id)) }
    }
    PhotoFrame(image = tile.image, date = tile.date, pose = tile.pose, onClick = onClick, modifier = modifier.aspectRatio(PORTRAIT))
}

@Composable
private fun SavedPairView(pair: SavedPair, onAction: (ProgressPhotosAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimension.D200)) {
        Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
            listOf(pair.left, pair.right).forEach { tile ->
                Tile(tile, onClick = { onAction(ProgressPhotosAction.SavedPairTapped(pair.id)) }, onAction = onAction, modifier = Modifier.weight(1f))
            }
        }
        Text(
            text = stringResource(Res.string.photos_saved_pair, DateCopy.monthDay(pair.left.date), DateCopy.monthDay(pair.right.date)),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
    }
}

@Composable
private fun Actions(state: ProgressPhotosState, onAction: (ProgressPhotosAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = AppTheme.spacing.page, vertical = Dimension.D400),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        if (state.canCompare) {
            ButtonPrimary(
                onClick = { onAction(ProgressPhotosAction.CompareTapped) },
                icon = AppIcons.Compare,
                style = ButtonStyle.Tonal,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(Res.string.photos_compare))
            }
        }
        ButtonPrimary(
            onClick = { onAction(ProgressPhotosAction.TakePhotosTapped) },
            icon = AppIcons.PhotoCamera,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = stringResource(Res.string.photos_take))
        }
    }
}

private const val COLUMNS = 3
private const val PORTRAIT = 3f / 4f
private val TILE_GAP = Dimension.D400

// Previews. Both design ids live in this file, so they pair with
// `progress-photos-screen__progress-photos` and `…__progress-photos-compare`.

@Composable
private fun Phone(dark: Boolean = false, content: @Composable () -> Unit) = PreviewContent(darkTheme = dark, content = content)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProgressPhotosPreview() = Phone { ProgressPhotosScreen(ProgressPhotosFixtures.grid(), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProgressPhotosDarkPreview() = Phone(dark = true) { ProgressPhotosScreen(ProgressPhotosFixtures.grid(), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProgressPhotosEmptyPreview() = Phone { ProgressPhotosScreen(ProgressPhotosFixtures.grid(photos = emptyList()), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProgressPhotosLockedPreview() =
    Phone { ProgressPhotosScreen(ProgressPhotosFixtures.grid(lock = PhotoLockState.Locked), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProgressPhotosNoLockPreview() =
    Phone { ProgressPhotosScreen(ProgressPhotosFixtures.grid(lock = PhotoLockState.NoDeviceLock), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProgressPhotosComparePreview() = Phone { CompareScreen(ProgressPhotosFixtures.compare(), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProgressPhotosCompareSliderPreview() =
    Phone { CompareScreen(ProgressPhotosFixtures.compare(CompareMode.Slider), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProgressPhotosPhotoPreview() = Phone {
    PhotoScreen(PhotoState(lock = PhotoLockState.Unlocked, photo = ProgressPhotosFixtures.grid().months.first().tiles.first()), onAction = {})
}
