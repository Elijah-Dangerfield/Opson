package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.opson.libraries.ui.components.CircularProgressIndicator
import com.opson.libraries.ui.components.ScanFrame
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonStyle
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconTile
import com.opson.libraries.ui.components.icon.IconTileSize
import com.opson.libraries.ui.components.text.SingleLineField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.barcode_allow_camera
import opson.libraries.resources.generated.resources.barcode_camera_body
import opson.libraries.resources.generated.resources.barcode_camera_body_ask
import opson.libraries.resources.generated.resources.barcode_camera_title
import opson.libraries.resources.generated.resources.barcode_code
import opson.libraries.resources.generated.resources.barcode_hint
import opson.libraries.resources.generated.resources.barcode_looking_up
import opson.libraries.resources.generated.resources.barcode_miss_body
import opson.libraries.resources.generated.resources.barcode_name_placeholder
import opson.libraries.resources.generated.resources.barcode_not_found
import opson.libraries.resources.generated.resources.barcode_open_settings
import opson.libraries.resources.generated.resources.barcode_save_name
import opson.libraries.resources.generated.resources.barcode_scan_again
import opson.libraries.resources.generated.resources.barcode_search_instead
import opson.libraries.resources.generated.resources.barcode_title
import opson.libraries.resources.generated.resources.barcode_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * The scanner: a camera window with a frame to aim at, and under it whatever
 * the scan has come to. Stateless; [viewfinder] is the live camera in the app
 * and a plain placeholder in previews and tests, so nothing here touches a
 * device. Shows no nutrition number in any stage.
 */
@Composable
fun BarcodeScanScreen(
    state: BarcodeScanState,
    onAction: (BarcodeScanAction) -> Unit,
    viewfinder: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(
        modifier = modifier,
        containerColor = AppTheme.colors.bg.color,
        contentWindowInsets = WindowInsets.systemBars,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.row),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
        ) {
            SheetHeader(title = stringResource(Res.string.barcode_title), onClose = { onAction(BarcodeScanAction.CloseTapped) })
            CameraWindow(state.stage, viewfinder)
            StageContent(state.stage, onAction)
        }
    }
}

@Composable
private fun CameraWindow(stage: ScanStage, viewfinder: @Composable (Modifier) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(WINDOW_RATIO)
            .clip(AppTheme.shapes.card)
            .background(AppTheme.colors.soft),
        contentAlignment = Alignment.Center,
    ) {
        when (stage) {
            ScanStage.Scanning -> {
                viewfinder(Modifier.fillMaxSize())
                ScanFrame(modifier = Modifier.fillMaxWidth(FRAME_WIDTH).aspectRatio(FRAME_RATIO))
            }
            is ScanStage.LookingUp -> {
                // The camera keeps running under the scrim; reads are ignored until there is an answer.
                viewfinder(Modifier.fillMaxSize())
                Box(modifier = Modifier.fillMaxSize().background(AppTheme.colors.scrim))
                CircularProgressIndicator(color = AppTheme.colors.onHero.color)
            }
            else -> IconTile(
                icon = AppIcons.BarcodeScanner,
                contentColor = AppTheme.colors.muted,
                containerColor = AppTheme.colors.surface,
                size = IconTileSize.Huge,
            )
        }
    }
}

@Composable
private fun StageContent(stage: ScanStage, onAction: (BarcodeScanAction) -> Unit) {
    when (stage) {
        ScanStage.Starting -> Unit
        ScanStage.Scanning -> Caption(stringResource(Res.string.barcode_hint))
        is ScanStage.LookingUp -> if (stage.slow) Caption(stringResource(Res.string.barcode_looking_up))
        is ScanStage.Miss -> Miss(stage, onAction)
        ScanStage.Unavailable -> Options(
            body = stringResource(Res.string.barcode_unavailable),
            primary = stringResource(Res.string.barcode_scan_again),
            onPrimary = { onAction(BarcodeScanAction.ScanAgainTapped) },
            onAction = onAction,
        )
        is ScanStage.NoCamera -> Options(
            title = stringResource(Res.string.barcode_camera_title),
            body = stringResource(if (stage.canAskAgain) Res.string.barcode_camera_body_ask else Res.string.barcode_camera_body),
            primary = stringResource(if (stage.canAskAgain) Res.string.barcode_allow_camera else Res.string.barcode_open_settings),
            onPrimary = {
                onAction(if (stage.canAskAgain) BarcodeScanAction.AllowCameraTapped else BarcodeScanAction.OpenSettingsTapped)
            },
            onAction = onAction,
        )
    }
}

/** Not found: the name first, because logging by name is the recovery path, then search, then another try. */
@Composable
private fun Miss(stage: ScanStage.Miss, onAction: (BarcodeScanAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Column {
            Text(
                text = stringResource(Res.string.barcode_not_found),
                typography = AppTheme.type.sheetTitle,
                color = AppTheme.colors.ink,
            )
            Text(
                text = stringResource(Res.string.barcode_code, stage.code),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
        }
        Text(
            text = stringResource(Res.string.barcode_miss_body),
            typography = AppTheme.type.body,
            color = AppTheme.colors.inkSoft,
        )
        SingleLineField(
            value = stage.name,
            onValueChange = { onAction(BarcodeScanAction.MissNameTyped(it)) },
            placeholder = stringResource(Res.string.barcode_name_placeholder),
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { if (stage.canSave) onAction(BarcodeScanAction.SaveNameTapped) }),
        )
        ButtonPrimary(
            onClick = { onAction(BarcodeScanAction.SaveNameTapped) },
            enabled = stage.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.barcode_save_name))
        }
        ButtonPrimary(
            onClick = { onAction(BarcodeScanAction.SearchInsteadTapped) },
            style = ButtonStyle.Tonal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.barcode_search_instead))
        }
        ButtonGhost(onClick = { onAction(BarcodeScanAction.ScanAgainTapped) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.barcode_scan_again))
        }
    }
}

/** A short message with one way forward, and search as the way around. */
@Composable
private fun Options(
    body: String,
    primary: String,
    onPrimary: () -> Unit,
    onAction: (BarcodeScanAction) -> Unit,
    title: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        title?.let { Text(text = it, typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink) }
        Text(text = body, typography = AppTheme.type.body, color = AppTheme.colors.inkSoft)
        ButtonPrimary(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(primary) }
        ButtonPrimary(
            onClick = { onAction(BarcodeScanAction.SearchInsteadTapped) },
            style = ButtonStyle.Tonal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.barcode_search_instead))
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        typography = AppTheme.type.body,
        color = AppTheme.colors.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The camera window: a little wider than tall, so the hint and a miss's buttons fit under it. */
private const val WINDOW_RATIO = 1.15f

/** The aiming frame: most of the window's width, the shape of a retail barcode. */
private const val FRAME_WIDTH = 0.78f
private const val FRAME_RATIO = 1.8f
