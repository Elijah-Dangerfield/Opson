package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.libraries.scanner.MealPhoto
import com.opson.libraries.ui.components.PhotoThumbnail
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.button.ButtonStyle
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.food_report_photo_remove
import opson.libraries.resources.generated.resources.submit_choose
import opson.libraries.resources.generated.resources.submit_retake
import opson.libraries.resources.generated.resources.submit_take
import org.jetbrains.compose.resources.stringResource

/**
 * One photo of the pack, the front or the label: the thumbnail once there is one, what to
 * photograph, and the ways to take, choose or drop it. Report a problem and Verify this food both
 * ask for the same two. [onTake] is null where there is no camera, or it was refused; [onRemove]
 * null where the photo can't be dropped on its own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongParameterList")
internal fun PackPhotoSlot(
    photo: MealPhoto?,
    title: String,
    hint: String,
    description: String,
    onTake: (() -> Unit)?,
    onChoose: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = AppTheme.spacing.section),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        verticalAlignment = Alignment.Top,
    ) {
        PhotoThumbnail(jpeg = photo?.jpeg, contentDescription = description)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            Text(title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            Text(hint, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            if (onTake != null || onChoose != null || (photo != null && onRemove != null)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                    onTake?.let {
                        ButtonPrimary(
                            onClick = it,
                            icon = AppIcons.PhotoCamera,
                            size = ButtonSize.Small,
                            style = if (photo == null) ButtonStyle.Filled else ButtonStyle.Tonal,
                        ) { Text(stringResource(if (photo == null) Res.string.submit_take else Res.string.submit_retake)) }
                    }
                    onChoose?.let {
                        ButtonSecondary(onClick = it, icon = AppIcons.PhotoLibrary, size = ButtonSize.Small) {
                            Text(stringResource(Res.string.submit_choose))
                        }
                    }
                    if (photo != null && onRemove != null) {
                        ButtonGhost(onClick = onRemove, size = ButtonSize.Small) { Text(stringResource(Res.string.food_report_photo_remove)) }
                    }
                }
            }
        }
    }
}
