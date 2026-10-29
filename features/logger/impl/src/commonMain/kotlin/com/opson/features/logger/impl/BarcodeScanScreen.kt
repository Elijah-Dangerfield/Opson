package com.opson.features.logger.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.scanner.FocusPoint
import com.opson.libraries.scanner.ScanArea
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.CircularProgressIndicator
import com.opson.libraries.ui.components.ScanFrame
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonStyle
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconButton
import com.opson.libraries.ui.components.icon.ToneIcon
import com.opson.libraries.ui.components.icon.ToneIconSize
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.SingleLineField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.builder_scan_read_label
import opson.libraries.resources.generated.resources.builder_scan_read_label_on
import opson.libraries.resources.generated.resources.barcode_allow_camera
import opson.libraries.resources.generated.resources.barcode_camera_body
import opson.libraries.resources.generated.resources.barcode_camera_body_ask
import opson.libraries.resources.generated.resources.barcode_camera_title
import opson.libraries.resources.generated.resources.barcode_camera_unavailable_body
import opson.libraries.resources.generated.resources.barcode_camera_unavailable_title
import opson.libraries.resources.generated.resources.barcode_cancel
import opson.libraries.resources.generated.resources.barcode_code
import opson.libraries.resources.generated.resources.barcode_hint
import opson.libraries.resources.generated.resources.barcode_light_off
import opson.libraries.resources.generated.resources.barcode_light_on
import opson.libraries.resources.generated.resources.barcode_look_up
import opson.libraries.resources.generated.resources.barcode_looking_up
import opson.libraries.resources.generated.resources.barcode_miss_body
import opson.libraries.resources.generated.resources.barcode_name_placeholder
import opson.libraries.resources.generated.resources.barcode_not_found
import opson.libraries.resources.generated.resources.barcode_open_settings
import opson.libraries.resources.generated.resources.barcode_save_name
import opson.libraries.resources.generated.resources.barcode_scan_again
import opson.libraries.resources.generated.resources.barcode_search_instead
import opson.libraries.resources.generated.resources.barcode_title
import opson.libraries.resources.generated.resources.barcode_try_camera
import opson.libraries.resources.generated.resources.barcode_type_body
import opson.libraries.resources.generated.resources.barcode_type_code
import opson.libraries.resources.generated.resources.barcode_type_misread
import opson.libraries.resources.generated.resources.barcode_type_placeholder
import opson.libraries.resources.generated.resources.barcode_unavailable
import opson.libraries.resources.generated.resources.submit_add_this_food
import opson.libraries.resources.generated.resources.submit_add_this_food_body
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** What the screen asks of the live camera: where to read, whether to light, and where a tap asked it to focus. */
data class ViewfinderRequest(val scanArea: ScanArea, val torchOn: Boolean, val focusPoint: FocusPoint?)

/**
 * The scanner: a camera window with a frame to aim at, and under it whatever
 * the scan has come to. [viewfinder] is the live camera in the app and a plain
 * placeholder in previews and tests, so nothing here touches a device. The
 * only state held here is where the preview was last tapped. Shows no
 * nutrition number in any stage.
 */
@Composable
fun BarcodeScanScreen(
    state: BarcodeScanState,
    onAction: (BarcodeScanAction) -> Unit,
    viewfinder: @Composable (ViewfinderRequest, Modifier) -> Unit,
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
            CameraWindow(state, onAction, viewfinder)
            state.readLabel?.let { on -> ReadLabelRow(on, onAction) }
            StageContent(state.stage, onAction)
        }
    }
}

@Composable
private fun CameraWindow(
    state: BarcodeScanState,
    onAction: (BarcodeScanAction) -> Unit,
    viewfinder: @Composable (ViewfinderRequest, Modifier) -> Unit,
) {
    val stage = state.stage
    var focusPoint by remember { mutableStateOf<FocusPoint?>(null) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(WINDOW_RATIO)
            .clip(AppTheme.shapes.card)
            .background(AppTheme.colors.soft),
        contentAlignment = Alignment.Center,
    ) {
        // One call site for the camera in every stage that shows it, so a read moving the screen
        // on to the lookup keeps the same session (and its focus and light) rather than restarting it.
        if (stage == ScanStage.Scanning || (stage is ScanStage.LookingUp && stage.camera)) {
            viewfinder(ViewfinderRequest(SCAN_AREA, torchOn = state.torch == Torch.On, focusPoint), Modifier.fillMaxSize())
            FocusLayer(onFocus = { focusPoint = it }, modifier = Modifier.fillMaxSize())
        }
        when (stage) {
            ScanStage.Scanning -> ScanFrame(modifier = Modifier.fillMaxWidth(FRAME_WIDTH).aspectRatio(FRAME_RATIO))
            is ScanStage.LookingUp -> if (stage.camera) {
                // The camera keeps running under the scrim; reads are ignored until there is an answer.
                Box(modifier = Modifier.fillMaxSize().background(AppTheme.colors.scrim))
                if (stage.slow) CircularProgressIndicator(color = AppTheme.colors.onHero.color) else ReadConfirmation()
            } else {
                CircularProgressIndicator()
            }
            else -> ToneIcon(
                icon = AppIcons.BarcodeScanner,
                color = AppTheme.colors.muted,
                size = ToneIconSize.Huge,
            )
        }
        if (state.torch != Torch.None) {
            TorchButton(
                on = state.torch == Torch.On,
                onClick = { onAction(BarcodeScanAction.TorchTapped) },
                modifier = Modifier.align(Alignment.TopEnd).padding(AppTheme.spacing.row),
            )
        }
    }
}

/**
 * Takes taps on the preview, hands each one on as a [FocusPoint], and draws a
 * ring where it landed that settles and fades. The ring is drawn, never
 * composed, so a tap redraws this layer and nothing else.
 */
@Composable
private fun FocusLayer(onFocus: (FocusPoint) -> Unit, modifier: Modifier) {
    val latestOnFocus by rememberUpdatedState(onFocus)
    var tappedAt by remember { mutableStateOf<Offset?>(null) }
    val ring = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val color = AppTheme.colors.onHero.color
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                tappedAt = offset
                latestOnFocus(FocusPoint(offset.x / size.width, offset.y / size.height))
                scope.launch {
                    ring.snapTo(0f)
                    ring.animateTo(1f, tween(durationMillis = RING_MILLIS, easing = LinearEasing))
                }
            }
        },
    ) {
        val at = tappedAt ?: return@Canvas
        val progress = ring.value
        if (progress >= 1f) return@Canvas
        // Closes in over the first third, like a lens settling, then fades over the last.
        val settle = (progress / RING_SETTLE).coerceAtMost(1f)
        val radius = RingRadius.toPx() * (RING_START_SCALE - (RING_START_SCALE - 1f) * settle)
        val alpha = ((1f - progress) / (1f - RING_FADE_FROM)).coerceIn(0f, 1f)
        drawCircle(color = color.copy(alpha = alpha), radius = radius, center = at, style = Stroke(width = RingStroke.toPx()))
    }
}

/**
 * The moment a code is caught: the frame snaps tight in the accent colour
 * with a tick in it, so a fast hit that goes straight to the food detail
 * still shows it worked. A slow lookup swaps this for the spinner at 400 ms.
 */
@Composable
private fun ReadConfirmation() {
    // Previews and screenshots start settled rather than on the first frame of the pop.
    val inspecting = LocalInspectionMode.current
    val pop = remember { Animatable(if (inspecting) 1f else 0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    ScanFrame(
        color = AppTheme.colors.accent,
        modifier = Modifier
            .fillMaxWidth(FRAME_WIDTH)
            .aspectRatio(FRAME_RATIO)
            .graphicsLayer {
                val scale = FRAME_SNAP_FROM - (FRAME_SNAP_FROM - 1f) * pop.value
                scaleX = scale
                scaleY = scale
            },
    )
    Icon(
        icon = AppIcons.CheckCircle,
        size = CheckSize,
        color = AppTheme.colors.accent,
        modifier = Modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
            alpha = pop.value.coerceIn(0f, 1f)
        },
    )
}

/** A Builder's Also read the label: the next screen after a code asks for a photo of the panel. */
@Composable
private fun ReadLabelRow(on: Boolean, onAction: (BarcodeScanAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.builder_scan_read_label), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
            if (on) Text(stringResource(Res.string.builder_scan_read_label_on), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        AppSwitch(checked = on, onCheckedChange = { onAction(BarcodeScanAction.ReadLabelChanged(it)) })
    }
}

@Composable
private fun TorchButton(on: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val icon = if (on) {
        AppIcons.FlashlightOn.described(stringResource(Res.string.barcode_light_off))
    } else {
        AppIcons.FlashlightOff.described(stringResource(Res.string.barcode_light_on))
    }
    IconButton(
        icon = icon,
        onClick = onClick,
        backgroundColor = if (on) AppTheme.colors.accent else AppTheme.colors.scrim,
        iconColor = if (on) AppTheme.colors.onAccent else AppTheme.colors.onHero,
        modifier = modifier,
    )
}

@Composable
private fun StageContent(stage: ScanStage, onAction: (BarcodeScanAction) -> Unit) {
    when (stage) {
        ScanStage.Starting -> Unit
        ScanStage.Scanning -> Scanning(onAction)
        is ScanStage.Typing -> Typing(stage, onAction)
        ScanStage.CameraUnavailable -> Options(
            title = stringResource(Res.string.barcode_camera_unavailable_title),
            body = stringResource(Res.string.barcode_camera_unavailable_body),
            primary = stringResource(Res.string.barcode_type_code),
            onPrimary = { onAction(BarcodeScanAction.TypeCodeTapped) },
            onAction = onAction,
            last = stringResource(Res.string.barcode_try_camera) to { onAction(BarcodeScanAction.RetryCameraTapped) },
        )
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
            last = stringResource(Res.string.barcode_type_code) to { onAction(BarcodeScanAction.TypeCodeTapped) },
        )
    }
}

/**
 * Not found. With `release.submitFood`, photographing the pack comes first: it is the way the
 * product gets into the catalog for everyone. Then the name, because logging by name is the
 * quick recovery path, then search, then another try.
 */
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
        if (stage.canAddFood) {
            ButtonPrimary(
                onClick = { onAction(BarcodeScanAction.AddFoodTapped) },
                icon = AppIcons.PostAdd,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.submit_add_this_food))
            }
            Text(
                text = stringResource(Res.string.submit_add_this_food_body),
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
            style = if (stage.canAddFood) ButtonStyle.Tonal else ButtonStyle.Filled,
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

/** The camera is live: the hint, and the ways out that don't need it. */
@Composable
private fun Scanning(onAction: (BarcodeScanAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Caption(stringResource(Res.string.barcode_hint))
        ButtonPrimary(
            onClick = { onAction(BarcodeScanAction.TypeCodeTapped) },
            style = ButtonStyle.Tonal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.barcode_type_code))
        }
        ButtonGhost(onClick = { onAction(BarcodeScanAction.SearchInsteadTapped) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.barcode_search_instead))
        }
    }
}

/** The digits under the barcode, typed by hand. Looked up exactly as a scan is. */
@Composable
private fun Typing(stage: ScanStage.Typing, onAction: (BarcodeScanAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Text(
            text = stringResource(Res.string.barcode_type_body),
            typography = AppTheme.type.body,
            color = AppTheme.colors.inkSoft,
        )
        NumberField(
            value = stage.code,
            onValueChange = { onAction(BarcodeScanAction.CodeTyped(it)) },
            placeholder = stringResource(Res.string.barcode_type_placeholder),
            allowDecimal = false,
            keyboardActions = KeyboardActions(onDone = { if (stage.canLookUp) onAction(BarcodeScanAction.LookUpTypedTapped) }),
        )
        if (stage.misread) {
            Text(
                text = stringResource(Res.string.barcode_type_misread),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
        }
        ButtonPrimary(
            onClick = { onAction(BarcodeScanAction.LookUpTypedTapped) },
            enabled = stage.canLookUp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.barcode_look_up))
        }
        ButtonPrimary(
            onClick = { onAction(BarcodeScanAction.SearchInsteadTapped) },
            style = ButtonStyle.Tonal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.barcode_search_instead))
        }
        ButtonGhost(onClick = { onAction(BarcodeScanAction.TypingCancelled) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.barcode_cancel))
        }
    }
}

/** A short message with one way forward, search as the way around, and optionally a [last] quieter option. */
@Composable
private fun Options(
    body: String,
    primary: String,
    onPrimary: () -> Unit,
    onAction: (BarcodeScanAction) -> Unit,
    title: String? = null,
    last: Pair<String, () -> Unit>? = null,
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
        last?.let { (label, onClick) ->
            ButtonGhost(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
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

/**
 * Where the camera reads: the frame with some room around it, so a code held
 * a little off centre or askew still reads. The frame's height as a share of
 * the window's follows from the two ratios.
 */
private const val SCAN_MARGIN = 0.12f
private val SCAN_AREA = ScanArea(
    width = (FRAME_WIDTH + SCAN_MARGIN).coerceAtMost(1f),
    height = (FRAME_WIDTH * WINDOW_RATIO / FRAME_RATIO + SCAN_MARGIN).coerceAtMost(1f),
)

/** The focus ring: a little larger than a fingertip, and gone before a second tap would come. */
private val RingRadius = 36.dp
private val RingStroke = 2.dp
private const val RING_MILLIS = 800
private const val RING_START_SCALE = 1.3f
private const val RING_SETTLE = 0.3f
private const val RING_FADE_FROM = 0.6f

/** The read confirmation: the frame closes in from a little wider, around a tick. */
private const val FRAME_SNAP_FROM = 1.08f
private val CheckSize = 56.dp
