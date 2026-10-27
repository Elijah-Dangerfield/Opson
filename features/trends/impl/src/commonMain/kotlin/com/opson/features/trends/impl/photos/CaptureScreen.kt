package com.opson.features.trends.impl.photos

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.HeaderIconButton
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.photos_camera_allow
import opson.libraries.resources.generated.resources.photos_camera_body
import opson.libraries.resources.generated.resources.photos_camera_settings
import opson.libraries.resources.generated.resources.photos_camera_title
import opson.libraries.resources.generated.resources.photos_camera_unavailable
import opson.libraries.resources.generated.resources.photos_capture_done
import opson.libraries.resources.generated.resources.photos_capture_failed
import opson.libraries.resources.generated.resources.photos_capture_flip
import opson.libraries.resources.generated.resources.photos_capture_hint
import opson.libraries.resources.generated.resources.photos_capture_saved
import opson.libraries.resources.generated.resources.photos_capture_shutter
import opson.libraries.resources.generated.resources.photos_title
import opson.libraries.resources.generated.resources.trends_back
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The camera for a set: the pose being taken, the live camera with the last
 * photo of that pose faint over it so the two line up, and a shutter.
 * [viewfinder] is the live camera; previews pass a plain box.
 */
@Composable
fun CaptureScreen(
    state: CaptureState,
    onAction: (CaptureAction) -> Unit,
    viewfinder: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color, contentWindowInsets = WindowInsets.systemBars) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = AppTheme.spacing.page),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = Dimension.D200)) {
                HeaderIconButton(
                    icon = AppIcons.Close.described(stringResource(Res.string.trends_back, stringResource(Res.string.photos_title))),
                    onClick = { onAction(CaptureAction.BackTapped) },
                )
                Text(
                    text = state.pose.label(),
                    typography = AppTheme.type.sheetTitle,
                    color = AppTheme.colors.ink,
                    modifier = Modifier.weight(1f).padding(horizontal = Dimension.D400),
                )
                if (state.cameraOn) {
                    HeaderIconButton(
                        icon = AppIcons.Cameraswitch.described(stringResource(Res.string.photos_capture_flip)),
                        onClick = { onAction(CaptureAction.FlipTapped) },
                    )
                }
            }
            when {
                state.lock != PhotoLockState.Unlocked -> LockPanel(state.lock, onUnlock = { onAction(CaptureAction.UnlockTapped) })
                state.permission is CameraPermission.Denied -> PermissionPanel(state.permission, onAction)
                state.cameraUnavailable -> Message(stringResource(Res.string.photos_camera_unavailable))
                state.permission == CameraPermission.Granted -> Camera(state, onAction, viewfinder)
            }
        }
    }
}

@Composable
private fun Camera(state: CaptureState, onAction: (CaptureAction) -> Unit, viewfinder: @Composable (Modifier) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(PORTRAIT)
            .clip(AppTheme.shapes.card)
            .background(AppTheme.colors.soft),
    ) {
        viewfinder(Modifier.fillMaxSize())
        if (state.ghost != null) PhotoImage(state.ghost, Modifier.fillMaxSize(), alpha = GHOST_ALPHA)
    }
    val note = when {
        state.failed -> stringResource(Res.string.photos_capture_failed)
        state.saved.isNotEmpty() -> stringResource(Res.string.photos_capture_saved, state.saved.last().label())
        state.ghost != null -> stringResource(Res.string.photos_capture_hint)
        else -> null
    }
    Text(text = note.orEmpty(), typography = AppTheme.type.body, color = AppTheme.colors.muted, modifier = Modifier.fillMaxWidth())
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        Shutter(enabled = !state.busy, onClick = { onAction(CaptureAction.ShutterTapped) })
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (state.saved.isNotEmpty()) {
                FlatButton(
                    text = stringResource(Res.string.photos_capture_done),
                    onClick = { onAction(CaptureAction.DoneTapped) },
                    tone = FlatButtonTone.AccentText,
                    centered = true,
                    modifier = Modifier.padding(start = Dimension.D400),
                )
            }
        }
    }
}

@Composable
private fun Shutter(enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(Res.string.photos_capture_shutter)
    Box(
        modifier = Modifier
            .size(SHUTTER)
            .clip(CircleShape)
            .border(SHUTTER_RING, AppTheme.colors.accent.color, CircleShape)
            .padding(SHUTTER_RING * 2)
            .clip(CircleShape)
            .background(if (enabled) AppTheme.colors.accent else AppTheme.colors.track)
            .bounceClick(scaleDown = 0.94f, onClick = { if (enabled) onClick() })
            .semantics {
                contentDescription = label
                role = Role.Button
            },
    )
}

@Composable
private fun PermissionPanel(permission: CameraPermission.Denied, onAction: (CaptureAction) -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding) {
        Text(text = stringResource(Res.string.photos_camera_title), typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink)
        Text(text = stringResource(Res.string.photos_camera_body), typography = AppTheme.type.body, color = AppTheme.colors.muted)
        if (permission.canAskAgain) {
            ButtonPrimary(onClick = { onAction(CaptureAction.AllowCameraTapped) }, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(Res.string.photos_camera_allow))
            }
        } else {
            ButtonSecondary(onClick = { onAction(CaptureAction.OpenSettingsTapped) }, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(Res.string.photos_camera_settings))
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding) {
        Text(text = text, typography = AppTheme.type.body, color = AppTheme.colors.ink)
    }
}

private const val PORTRAIT = 3f / 4f
private const val GHOST_ALPHA = 0.3f
private val SHUTTER = 76.dp
private val SHUTTER_RING = 3.dp

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CapturePreview() = PreviewContent {
    CaptureScreen(
        state = CaptureState(lock = PhotoLockState.Unlocked, permission = CameraPermission.Granted),
        onAction = {},
        viewfinder = { Box(it.background(AppTheme.colors.hero)) },
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CaptureDeniedPreview() = PreviewContent {
    CaptureScreen(
        state = CaptureState(lock = PhotoLockState.Unlocked, permission = CameraPermission.Denied(canAskAgain = false)),
        onAction = {},
        viewfinder = {},
    )
}
