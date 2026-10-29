package com.opson.features.home.impl.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.PhotoThumbnail
import com.opson.libraries.ui.components.SectionCard
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetDefaults
import com.opson.libraries.ui.components.dialog.bottomsheet.rememberBottomSheetState
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.report_attachments_title
import opson.libraries.resources.generated.resources.report_device_line
import opson.libraries.resources.generated.resources.report_device_note
import opson.libraries.resources.generated.resources.report_device_title
import opson.libraries.resources.generated.resources.report_log_empty
import opson.libraries.resources.generated.resources.report_log_include
import opson.libraries.resources.generated.resources.report_log_lines
import opson.libraries.resources.generated.resources.report_log_off
import opson.libraries.resources.generated.resources.report_log_sheet_body
import opson.libraries.resources.generated.resources.report_log_title
import opson.libraries.resources.generated.resources.report_log_view
import opson.libraries.resources.generated.resources.report_screenshot_add
import opson.libraries.resources.generated.resources.report_screenshot_cd
import opson.libraries.resources.generated.resources.report_screenshot_failed
import opson.libraries.resources.generated.resources.report_screenshot_note
import opson.libraries.resources.generated.resources.report_screenshot_remove
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * "Sent with it": the screenshots, the app log and the version line a report carries, each one
 * visible before it goes, and the first two removable. The log opens in a sheet showing the exact
 * text that will be sent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportAttachmentsSection(
    attachments: ReportAttachments,
    onAction: (AttachmentAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = stringResource(Res.string.report_attachments_title), modifier = modifier) {
        Screenshots(attachments, onAction)
        LogRow(attachments, onAction)
        attachments.device?.let { device ->
            ListRow(
                title = stringResource(Res.string.report_device_title),
                subtitle = stringResource(Res.string.report_device_line, device.version, device.phone),
                icon = AppIcons.Info,
            )
            Text(
                text = stringResource(Res.string.report_device_note),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
        }
    }
    if (attachments.showingLog) {
        LogSheet(lines = attachments.logLines, onClose = { onAction(AttachmentAction.LogClosed) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Screenshots(attachments: ReportAttachments, onAction: (AttachmentAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        if (attachments.screenshots.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                attachments.screenshots.forEachIndexed { index, shot ->
                    val number = index + 1
                    Box {
                        PhotoThumbnail(jpeg = shot.jpeg, contentDescription = stringResource(Res.string.report_screenshot_cd, number))
                        RoundIconButton(
                            icon = AppIcons.Close.described(stringResource(Res.string.report_screenshot_remove, number)),
                            onClick = { onAction(AttachmentAction.ScreenshotRemoved(index)) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                            size = 28.dp,
                            iconSize = 18.dp,
                            containerColor = AppTheme.colors.ink,
                            contentColor = AppTheme.colors.bg,
                        )
                    }
                }
            }
            Text(
                text = stringResource(Res.string.report_screenshot_note),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
        }
        if (attachments.canAddScreenshot) {
            ButtonSecondary(
                onClick = { onAction(AttachmentAction.AddScreenshotTapped) },
                icon = AppIcons.PhotoLibrary,
                size = ButtonSize.Small,
            ) { Text(stringResource(Res.string.report_screenshot_add)) }
        }
        if (attachments.pickFailed) {
            Text(
                text = stringResource(Res.string.report_screenshot_failed),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.ink,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun LogRow(attachments: ReportAttachments, onAction: (AttachmentAction) -> Unit) {
    val hasLog = attachments.logLines.isNotEmpty()
    val includeLabel = stringResource(Res.string.report_log_include)
    ListRow(
        title = stringResource(Res.string.report_log_title),
        subtitle = when {
            !hasLog -> stringResource(Res.string.report_log_empty)
            !attachments.includeLog -> stringResource(Res.string.report_log_off)
            else -> stringResource(Res.string.report_log_lines, attachments.logLines.size)
        },
        icon = AppIcons.Article,
    ) {
        if (hasLog) {
            FlatButton(
                text = stringResource(Res.string.report_log_view),
                onClick = { onAction(AttachmentAction.ViewLogTapped) },
                tone = FlatButtonTone.AccentText,
            )
            AppSwitch(
                checked = attachments.includeLog,
                onCheckedChange = { onAction(AttachmentAction.LogIncluded(it)) },
                modifier = Modifier.semantics { contentDescription = includeLabel },
            )
        }
    }
}

/**
 * The log, a line a row: up to 500 lines, so a lazy list in a pinned sheet rather than one long
 * text that lays out all of them at once.
 */
@Composable
private fun LogSheet(lines: List<String>, onClose: () -> Unit) {
    val state = rememberBottomSheetState()
    BottomSheet(
        onDismissRequest = onClose,
        state = state,
        heightFraction = BottomSheetDefaults.FullHeightFraction,
        showDragHandle = true,
        backgroundColor = AppTheme.colors.bg,
    ) {
        Column(modifier = Modifier.padding(horizontal = AppTheme.spacing.page)) {
            SheetHeader(title = stringResource(Res.string.report_log_title), onClose = { state.dismiss() })
            Text(
                text = stringResource(Res.string.report_log_sheet_body),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
                modifier = Modifier.padding(vertical = AppTheme.spacing.row),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(lines) { _, line ->
                    Text(
                        text = line,
                        typography = AppTheme.type.secondarySmall,
                        color = AppTheme.colors.ink,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun ReportAttachmentsSectionPreview() {
    PreviewContent {
        ReportAttachmentsSection(
            attachments = ReportAttachments(
                screenshots = listOf(Screenshot(byteArrayOf(1, 2, 3))),
                log = "2026-10-29T10:01:02Z INFO Today: day loaded\n2026-10-29T10:01:04Z DEBUG Sync: pushed 2",
                device = ReportAttachments.Device(version = "0.2.0 (702)", phone = "iPhone15,4, iOS 18.1"),
            ),
            onAction = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(widthDp = 390)
@Composable
private fun ReportAttachmentsSectionLogOffPreview() {
    PreviewContent {
        ReportAttachmentsSection(
            attachments = ReportAttachments(
                log = "2026-10-29T10:01:02Z INFO Today: day loaded",
                includeLog = false,
                device = ReportAttachments.Device(version = "0.2.0 (702)", phone = "Google Pixel 8, Android 15"),
            ),
            onAction = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
