package com.opson.features.trends.impl.photos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.core.Catching
import com.opson.libraries.progressphotos.PhotoBytes
import com.opson.libraries.progressphotos.Pose
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.dialog.Dialog
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.background
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.photos_cancel
import opson.libraries.resources.generated.resources.photos_delete
import opson.libraries.resources.generated.resources.photos_locked_body
import opson.libraries.resources.generated.resources.photos_locked_title
import opson.libraries.resources.generated.resources.photos_no_lock_body
import opson.libraries.resources.generated.resources.photos_no_lock_title
import opson.libraries.resources.generated.resources.photos_pose_front
import opson.libraries.resources.generated.resources.photos_pose_side
import opson.libraries.resources.generated.resources.photos_privacy
import opson.libraries.resources.generated.resources.photos_tile
import opson.libraries.resources.generated.resources.photos_unlock
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

/**
 * A photo, cropped to fill its frame, with its date as a tag in the corner.
 * The tag is a label laid over the frame, never drawn into the picture. With
 * no image (not read yet, or a preview) it draws quiet diagonal stripes.
 */
@Composable
internal fun PhotoFrame(
    image: PhotoBytes?,
    date: LocalDate?,
    pose: Pose?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val description = if (date != null && pose != null) stringResource(Res.string.photos_tile, DateCopy.monthDay(date), pose.label()) else ""
    Box(
        modifier = modifier
            .clip(AppTheme.shapes.card)
            .background(AppTheme.colors.soft)
            .then(if (onClick != null) Modifier.bounceClick(scaleDown = 0.98f, onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
    ) {
        PhotoImage(image, Modifier.fillMaxSize())
        if (date != null) DateTag(DateCopy.monthDay(date), Modifier.align(Alignment.BottomStart).padding(Dimension.D400))
    }
}

/**
 * The bytes, decoded once per photo off the main thread (a full photo takes long enough to drop
 * frames); stripes until then, and when there are none or they don't decode. Previews decode
 * straight away, since a screenshot doesn't wait for a background decode.
 */
@Composable
internal fun PhotoImage(image: PhotoBytes?, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val inspecting = LocalInspectionMode.current
    val bitmap = produceState(initialValue = if (inspecting) image?.decoded() else null, image) {
        value = image?.let { withContext(Dispatchers.Default) { it.decoded() } }
    }.value
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, alpha = alpha, modifier = modifier)
    } else {
        Stripes(modifier)
    }
}

private fun PhotoBytes.decoded(): ImageBitmap? = Catching { jpeg.decodeToImageBitmap() }.getOrNull()

@Composable
private fun Stripes(modifier: Modifier) {
    val stripe = AppTheme.colors.line.color
    Canvas(modifier) {
        val step = STRIPE_STEP.dp.toPx()
        val width = step / 2
        var x = -size.height
        while (x < size.width) {
            drawLine(stripe, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = width)
            x += step
        }
    }
}

@Composable
internal fun DateTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        typography = AppTheme.type.secondarySmallStrong.Bold,
        color = AppTheme.colors.ink,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(TAG_CORNER.dp))
            .background(AppTheme.colors.surface)
            .padding(horizontal = Dimension.D300, vertical = Dimension.D100),
    )
}

/** Stored only on this phone, behind its lock. */
@Composable
internal fun PrivacyCard(modifier: Modifier = Modifier) {
    EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding, modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon = AppIcons.LockOutlined, size = Dimension.D900, color = AppTheme.colors.accent)
            Text(
                text = stringResource(Res.string.photos_privacy),
                typography = AppTheme.type.body,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * What every photo screen shows instead of photos until the phone's lock is
 * passed, or when the phone has no lock to pass.
 */
@Composable
internal fun LockPanel(lock: PhotoLockState, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val noLock = lock == PhotoLockState.NoDeviceLock
    EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row), modifier = Modifier.fillMaxWidth()) {
            Icon(icon = AppIcons.Lock, size = Dimension.D900, color = AppTheme.colors.accent)
            Text(
                text = stringResource(if (noLock) Res.string.photos_no_lock_title else Res.string.photos_locked_title),
                typography = AppTheme.type.sheetTitle,
                color = AppTheme.colors.ink,
            )
            Text(
                text = stringResource(if (noLock) Res.string.photos_no_lock_body else Res.string.photos_locked_body),
                typography = AppTheme.type.body,
                color = AppTheme.colors.muted,
            )
            if (!noLock) {
                ButtonPrimary(onClick = onUnlock, enabled = lock != PhotoLockState.Unlocking, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(Res.string.photos_unlock))
                }
            }
        }
    }
}

/** "Delete …?" with Cancel as the safe choice, as Support's delete dialog. */
@Composable
internal fun DeleteDialog(title: String, body: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.bg, AppTheme.shapes.card)
                .padding(start = Dimension.D800, end = Dimension.D800, top = Dimension.D800, bottom = Dimension.D600),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Icon(icon = AppIcons.Delete, size = Dimension.D900, color = AppTheme.colors.muted)
            Text(text = title, typography = AppTheme.type.dayLabel, color = AppTheme.colors.ink, textAlign = TextAlign.Center)
            Text(text = body, typography = AppTheme.type.body, color = AppTheme.colors.muted, textAlign = TextAlign.Center)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Dimension.D100),
                horizontalArrangement = Arrangement.spacedBy(Dimension.D100, Alignment.End),
            ) {
                FlatButton(
                    text = stringResource(Res.string.photos_cancel),
                    onClick = onCancel,
                    tone = FlatButtonTone.AccentText,
                    typography = AppTheme.type.button,
                )
                FlatButton(
                    text = stringResource(Res.string.photos_delete),
                    onClick = onConfirm,
                    tone = FlatButtonTone.Text,
                    typography = AppTheme.type.button,
                )
            }
        }
    }
}

@Composable
internal fun Pose.label(): String = stringResource(
    when (this) {
        Pose.Front -> Res.string.photos_pose_front
        Pose.Side -> Res.string.photos_pose_side
    },
)

private const val STRIPE_STEP = 22
private const val TAG_CORNER = 8
