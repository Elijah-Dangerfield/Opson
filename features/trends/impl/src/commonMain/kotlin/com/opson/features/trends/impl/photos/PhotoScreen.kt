package com.opson.features.trends.impl.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.HeaderIconButton
import com.opson.libraries.ui.components.MenuItem
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.PopoverMenu
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.photos_delete_one
import opson.libraries.resources.generated.resources.photos_delete_one_body
import opson.libraries.resources.generated.resources.photos_delete_one_title
import opson.libraries.resources.generated.resources.photos_more
import opson.libraries.resources.generated.resources.photos_title
import opson.libraries.resources.generated.resources.trends_loading_cd
import org.jetbrains.compose.resources.stringResource

/** One photo, large, its date as the title. Delete is in ⋮. */
@Composable
fun PhotoScreen(
    state: PhotoState,
    onAction: (PhotoAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
                .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            val photo = state.photo.takeIf { state.lock == PhotoLockState.Unlocked }
            PageHeader(
                title = photo?.let { DateCopy.longMonthDay(it.date) } ?: stringResource(Res.string.photos_title),
                backLabel = stringResource(Res.string.photos_title),
                onBack = { onAction(PhotoAction.BackTapped) },
            ) {
                if (photo != null) {
                    Box {
                        HeaderIconButton(
                            icon = AppIcons.MoreVert.described(stringResource(Res.string.photos_more)),
                            onClick = { onAction(PhotoAction.MenuTapped) },
                            selected = state.menuOpen,
                        )
                        PopoverMenu(expanded = state.menuOpen, onDismissRequest = { onAction(PhotoAction.MenuDismissed) }) {
                            MenuItem(
                                label = stringResource(Res.string.photos_delete_one),
                                icon = AppIcons.Delete,
                                onClick = { onAction(PhotoAction.DeleteTapped) },
                                showDivider = false,
                            )
                        }
                    }
                }
            }
            if (photo == null && state.lock == PhotoLockState.Unlocked) {
                // Unlocked, the photo's date not read yet: never the lock panel, which would say it's locked.
                DelayedLoadingIndicator(contentDescription = stringResource(Res.string.trends_loading_cd))
            } else if (photo == null) {
                LockPanel(state.lock, onUnlock = { onAction(PhotoAction.UnlockTapped) })
            } else {
                PhotoFrame(photo.image, photo.date, photo.pose, Modifier.fillMaxWidth().aspectRatio(PORTRAIT))
                if (state.note.isNotBlank()) {
                    Text(text = state.note, typography = AppTheme.type.body, color = AppTheme.colors.ink)
                }
            }
        }
    }
    if (state.confirmDelete) {
        DeleteDialog(
            title = stringResource(Res.string.photos_delete_one_title),
            body = stringResource(Res.string.photos_delete_one_body),
            onConfirm = { onAction(PhotoAction.DeleteConfirmed) },
            onCancel = { onAction(PhotoAction.DeleteCancelled) },
        )
    }
}

private const val PORTRAIT = 3f / 4f
