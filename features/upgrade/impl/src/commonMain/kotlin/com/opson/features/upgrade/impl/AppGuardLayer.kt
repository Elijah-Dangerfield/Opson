package com.opson.features.upgrade.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.features.upgrade.AppGuard
import com.opson.features.upgrade.AppGuardState
import com.opson.features.upgrade.ConnectionBanner
import com.opson.features.upgrade.ConnectionStrip
import com.opson.features.upgrade.blocksApp
import com.opson.libraries.core.BuildInfo
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import kotlinx.coroutines.launch
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.shell_tab_today
import opson.libraries.resources.generated.resources.upgrade_available_body
import opson.libraries.resources.generated.resources.upgrade_available_cta
import opson.libraries.resources.generated.resources.upgrade_available_dismiss_cd
import opson.libraries.resources.generated.resources.upgrade_debug_clear_overrides
import opson.libraries.resources.generated.resources.upgrade_maintenance_logging_continues
import opson.libraries.resources.generated.resources.upgrade_maintenance_title
import opson.libraries.resources.generated.resources.upgrade_maintenance_try_again
import opson.libraries.resources.generated.resources.upgrade_offline_back
import opson.libraries.resources.generated.resources.upgrade_offline_logging_works
import opson.libraries.resources.generated.resources.upgrade_offline_needs_connection
import opson.libraries.resources.generated.resources.upgrade_required_body
import opson.libraries.resources.generated.resources.upgrade_required_cta
import opson.libraries.resources.generated.resources.upgrade_required_reassurance
import opson.libraries.resources.generated.resources.upgrade_required_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The guard around the whole app. [content] is the app; it stays composed underneath a stop so
 * the back stack is intact when the stop lifts.
 *
 * The state is collected here, so a guard change recomposes this and not the shell that calls it.
 * A strip (maintenance banner, offline, update offer) sits above the app and takes the status bar
 * with it, so screens drawn under it do not pad for the status bar twice. It pushes the app down
 * rather than covering it, so nothing on a screen is ever hidden behind it.
 */
@Composable
fun AppGuardLayer(
    guard: AppGuard,
    connection: ConnectionBanner,
    onOpenUrl: (String) -> Unit,
    onClearOverrides: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state by guard.state.collectAsState()
    val strip by connection.strip.collectAsState()
    val scope = rememberCoroutineScope()
    var retrying by remember { mutableStateOf(false) }

    AppGuardContent(
        state = state,
        connection = strip,
        retrying = retrying,
        onRetry = {
            if (!retrying) {
                retrying = true
                scope.launch {
                    try {
                        guard.retry()
                    } finally {
                        retrying = false
                    }
                }
            }
        },
        onOpenStore = {
            guard.storeOpened()
            onOpenUrl(guard.storeUrl)
        },
        onDismissUpdate = guard::dismissUpdate,
        onClearOverrides = onClearOverrides,
        content = content,
    )
}

/**
 * One strip at a time, in this order: the operator's maintenance message (offline, it already
 * says logging continues), then the offline strip, then the update offer, which can wait for a
 * connection anyway.
 */
private enum class Strip { Maintenance, Connection, Update }

private class LastStrip(var strip: Strip? = null, var connection: ConnectionStrip = ConnectionStrip.Hidden)

private fun strip(state: AppGuardState, connection: ConnectionStrip): Strip? = when {
    state is AppGuardState.MaintenanceBanner -> Strip.Maintenance
    connection != ConnectionStrip.Hidden -> Strip.Connection
    state is AppGuardState.UpdateAvailable -> Strip.Update
    else -> null
}

@Composable
internal fun AppGuardContent(
    state: AppGuardState,
    connection: ConnectionStrip = ConnectionStrip.Hidden,
    retrying: Boolean,
    onRetry: () -> Unit,
    onOpenStore: () -> Unit,
    onDismissUpdate: () -> Unit,
    onClearOverrides: () -> Unit,
    content: @Composable () -> Unit,
) {
    val showing = strip(state, connection)
    // What the strip said last, so it keeps its words while it animates away. Plain fields, not
    // state: they only follow what this composition already read.
    val last = remember { LastStrip() }
    if (showing != null) last.strip = showing
    if (connection != ConnectionStrip.Hidden) last.connection = connection
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showing != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                when (last.strip) {
                    Strip.Maintenance -> (state as? AppGuardState.MaintenanceBanner)?.let { MaintenanceStrip(it) }
                    Strip.Connection -> ConnectionStripView(last.connection)
                    Strip.Update -> UpdateStrip(onOpenStore = onOpenStore, onDismiss = onDismissUpdate)
                    null -> Unit
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (showing != null) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
            ) {
                content()
            }
        }

        AnimatedVisibility(visible = state.blocksApp, enter = fadeIn(), exit = fadeOut()) {
            when (state) {
                is AppGuardState.MaintenanceBlocking -> StopScreen(
                    title = stringResource(Res.string.upgrade_maintenance_title),
                    body = state.message,
                    onClearOverrides = onClearOverrides,
                ) {
                    ButtonSecondary(onClick = onRetry, enabled = !retrying, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.upgrade_maintenance_try_again))
                    }
                }
                AppGuardState.UpgradeRequired -> StopScreen(
                    title = stringResource(Res.string.upgrade_required_title),
                    body = stringResource(Res.string.upgrade_required_body),
                    onClearOverrides = onClearOverrides,
                ) {
                    ButtonPrimary(onClick = onOpenStore, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.upgrade_required_cta))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.upgrade_required_reassurance),
                        typography = AppTheme.type.secondary,
                        color = AppTheme.colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun MaintenanceStrip(state: AppGuardState.MaintenanceBanner) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.soft)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = state.message, typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
        if (state.loggingContinues) {
            Text(
                text = stringResource(Res.string.upgrade_maintenance_logging_continues),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.inkSoft,
            )
        }
    }
}

/**
 * Offline, said calmly on the soft fill like the maintenance strip: what still works, never a
 * warning colour. Read out when it changes, without taking focus.
 */
@Composable
private fun ConnectionStripView(strip: ConnectionStrip) {
    val text = when (strip) {
        is ConnectionStrip.Offline -> stringResource(
            if (strip.loggingWorks) Res.string.upgrade_offline_logging_works else Res.string.upgrade_offline_needs_connection,
        )
        ConnectionStrip.BackOnline -> stringResource(Res.string.upgrade_offline_back)
        ConnectionStrip.Hidden -> return
    }
    Text(
        text = text,
        typography = AppTheme.type.body,
        color = AppTheme.colors.ink,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.soft)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun UpdateStrip(onOpenStore: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.soft)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.upgrade_available_body),
            typography = AppTheme.type.body,
            color = AppTheme.colors.ink,
            modifier = Modifier.weight(1f),
        )
        ButtonGhost(onClick = onOpenStore) {
            Text(stringResource(Res.string.upgrade_available_cta))
        }
        IconButton(
            icon = AppIcons.Close.described(stringResource(Res.string.upgrade_available_dismiss_cd)),
            onClick = onDismiss,
        )
    }
}

/**
 * A calm full-screen stop. It swallows every touch that misses its buttons, so nothing reaches the
 * app drawn underneath.
 */
@Composable
private fun StopScreen(
    title: String,
    body: String,
    onClearOverrides: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.bg)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 32.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text(text = title, typography = AppTheme.type.h1, color = AppTheme.colors.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(text = body, typography = AppTheme.type.body, color = AppTheme.colors.inkSoft, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1.4f))
            actions()
            if (BuildInfo.isDebug) {
                ButtonGhost(onClick = onClearOverrides) {
                    Text(stringResource(Res.string.upgrade_debug_clear_overrides))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PreviewApp() {
    Box(Modifier.fillMaxSize().background(AppTheme.colors.bg), contentAlignment = Alignment.Center) {
        Text(stringResource(Res.string.shell_tab_today), typography = AppTheme.type.h1, color = AppTheme.colors.ink)
    }
}

@Composable
private fun GuardPreview(state: AppGuardState, connection: ConnectionStrip = ConnectionStrip.Hidden) {
    PreviewContent {
        AppGuardContent(
            state = state,
            connection = connection,
            retrying = false,
            onRetry = {},
            onOpenStore = {},
            onDismissUpdate = {},
            onClearOverrides = {},
        ) { PreviewApp() }
    }
}

@Preview
@Composable
private fun AppGuardPreview_MaintenanceBlocking() {
    GuardPreview(AppGuardState.MaintenanceBlocking("We're moving to a bigger database. Back by 3pm."))
}

@Preview
@Composable
private fun AppGuardPreview_MaintenanceOffline() {
    GuardPreview(AppGuardState.MaintenanceBanner("We're moving to a bigger database. Back by 3pm.", loggingContinues = true))
}

@Preview
@Composable
private fun AppGuardPreview_MaintenanceBanner() {
    GuardPreview(AppGuardState.MaintenanceBanner("Syncing is slow this morning. Nothing is lost.", loggingContinues = false))
}

@Preview
@Composable
private fun AppGuardPreview_UpgradeRequired() {
    GuardPreview(AppGuardState.UpgradeRequired)
}

@Preview
@Composable
private fun AppGuardPreview_UpdateAvailable() {
    GuardPreview(AppGuardState.UpdateAvailable)
}

@Preview
@Composable
private fun AppGuardPreview_Offline() {
    GuardPreview(AppGuardState.Normal, ConnectionStrip.Offline(loggingWorks = true))
}

@Preview
@Composable
private fun AppGuardPreview_OfflineBeforeOnboarding() {
    GuardPreview(AppGuardState.Normal, ConnectionStrip.Offline(loggingWorks = false))
}

@Preview
@Composable
private fun AppGuardPreview_BackOnline() {
    GuardPreview(AppGuardState.Normal, ConnectionStrip.BackOnline)
}

@Preview
@Composable
private fun AppGuardPreview_Normal() {
    GuardPreview(AppGuardState.Normal)
}
