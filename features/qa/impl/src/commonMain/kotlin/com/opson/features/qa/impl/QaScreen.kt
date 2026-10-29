package com.opson.features.qa.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.OutlinedTextField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.qa_check_in
import opson.libraries.resources.generated.resources.qa_check_in_sub
import opson.libraries.resources.generated.resources.qa_clear_caches
import opson.libraries.resources.generated.resources.qa_clear_caches_sub
import opson.libraries.resources.generated.resources.qa_clear_overrides
import opson.libraries.resources.generated.resources.qa_copy_hint
import opson.libraries.resources.generated.resources.qa_flags_body
import opson.libraries.resources.generated.resources.qa_none
import opson.libraries.resources.generated.resources.qa_notifications_body
import opson.libraries.resources.generated.resources.qa_overridden
import opson.libraries.resources.generated.resources.qa_prompts
import opson.libraries.resources.generated.resources.qa_prompts_sub
import opson.libraries.resources.generated.resources.qa_reset_onboarding
import opson.libraries.resources.generated.resources.qa_reset_onboarding_sub
import opson.libraries.resources.generated.resources.qa_section_account
import opson.libraries.resources.generated.resources.qa_section_build
import opson.libraries.resources.generated.resources.qa_section_flags
import opson.libraries.resources.generated.resources.qa_section_notifications
import opson.libraries.resources.generated.resources.qa_section_shortcuts
import opson.libraries.resources.generated.resources.qa_set
import opson.libraries.resources.generated.resources.qa_title
import opson.libraries.resources.generated.resources.settings_back_more
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun QaScreen(state: QaState, onAction: (QaAction) -> Unit, modifier: Modifier = Modifier) {
    QaPage(modifier = modifier) {
        PageHeader(
            title = stringResource(Res.string.qa_title),
            backLabel = stringResource(Res.string.settings_back_more),
            onBack = { onAction(QaAction.BackTapped) },
        )
        Note(stringResource(Res.string.qa_copy_hint))

        QaSectionLabel(stringResource(Res.string.qa_section_build))
        InfoCard(state.build, onAction)

        QaSectionLabel(stringResource(Res.string.qa_section_account))
        InfoCard(state.identity, onAction)

        QaSectionLabel(stringResource(Res.string.qa_section_notifications))
        Note(stringResource(Res.string.qa_notifications_body))
        QaCard {
            state.reminders.forEachIndexed { index, reminder ->
                QaRow(
                    title = stringResource(reminder.label),
                    icon = AppIcons.Notifications,
                    showDivider = index < state.reminders.lastIndex,
                    onClick = { onAction(QaAction.FireTapped(reminder)) },
                )
            }
        }

        QaSectionLabel(stringResource(Res.string.qa_section_shortcuts))
        QaCard {
            ToolRow(Res.string.qa_check_in, Res.string.qa_check_in_sub, state.busy, divider = true) { onAction(QaAction.CheckInTapped) }
            ToolRow(Res.string.qa_prompts, Res.string.qa_prompts_sub, busy = false, divider = true) { onAction(QaAction.PromptsTapped) }
            ToolRow(Res.string.qa_reset_onboarding, Res.string.qa_reset_onboarding_sub, state.busy, divider = true) {
                onAction(QaAction.ResetOnboardingTapped)
            }
            ToolRow(Res.string.qa_clear_caches, Res.string.qa_clear_caches_sub, state.busy, divider = false) { onAction(QaAction.ClearCachesTapped) }
        }

        QaSectionLabel(stringResource(Res.string.qa_section_flags))
        Note(stringResource(Res.string.qa_flags_body))
        QaCard {
            QaRow(
                title = stringResource(Res.string.qa_clear_overrides),
                icon = AppIcons.DeleteOutline,
                onClick = { onAction(QaAction.ClearOverridesTapped) },
            )
        }
        state.config.groupBy { it.group }.forEach { (group, rows) ->
            QaSectionLabel(group)
            QaCard {
                rows.forEachIndexed { index, row ->
                    ConfigRow(row, onAction)
                    if (index < rows.lastIndex) HorizontalDivider(color = AppTheme.colors.line)
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        typography = AppTheme.type.secondarySmall,
        color = AppTheme.colors.muted,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** Tap a row to copy its value: the user id is what a tester reads back in a bug report. */
@Suppress("DEPRECATION") // LocalClipboard is suspend-only; a tap handler has no scope to spare.
@Composable
private fun InfoCard(rows: List<QaInfo>, onAction: (QaAction) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val none = stringResource(Res.string.qa_none)
    QaCard {
        rows.forEachIndexed { index, info ->
            val shown = info.valueText?.let { stringResource(it, *info.valueArgs.toTypedArray()) } ?: info.value
            QaRow(
                title = stringResource(info.label),
                icon = null,
                subtitle = shown ?: none,
                showDivider = index < rows.lastIndex,
                onClick = shown?.takeIf { info.copyable }?.let { value ->
                    {
                        clipboard.setText(AnnotatedString(value))
                        onAction(QaAction.Copied(info.label))
                    }
                },
                trailing = if (info.copyable) {
                    { Icon(icon = AppIcons.ContentCopy, size = 18.dp, color = AppTheme.colors.muted) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun ToolRow(
    title: StringResource,
    subtitle: StringResource,
    busy: Boolean,
    divider: Boolean,
    onClick: () -> Unit,
) {
    QaRow(
        title = stringResource(title),
        icon = null,
        subtitle = stringResource(subtitle),
        showDivider = divider,
        onClick = if (busy) null else onClick,
    )
}

/** A flag is a switch; a number or a string is a field and Set; anything else is shown as is. */
@Composable
private fun ConfigRow(row: QaConfigRow, onAction: (QaAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(text = row.name, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
                Text(text = row.path, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
                if (row.overridden) {
                    Text(text = stringResource(Res.string.qa_overridden), typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.accent)
                }
            }
            when (row.kind) {
                QaConfigKind.Flag -> AppSwitch(
                    checked = row.value.toBooleanStrictOrNull() == true,
                    onCheckedChange = { onAction(QaAction.FlagToggled(row.path, it)) },
                )
                QaConfigKind.ReadOnly -> Text(text = row.value, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
                else -> Unit
            }
        }
        if (row.kind != QaConfigKind.Flag && row.kind != QaConfigKind.ReadOnly) ValueField(row, onAction)
    }
}

@Composable
private fun ValueField(row: QaConfigRow, onAction: (QaAction) -> Unit) {
    // Keyed on the live value, so an override or a refetch replaces whatever was being typed.
    var draft by remember(row.path, row.value) { mutableStateOf(row.value) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (row.kind == QaConfigKind.Text) KeyboardType.Text else KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        )
        FlatButton(
            text = stringResource(Res.string.qa_set),
            onClick = { onAction(QaAction.ValueSet(row, draft)) },
            tone = FlatButtonTone.AccentText,
            enabled = draft != row.value && QaStateBuilder.parse(row.kind, draft) != null,
        )
    }
}

/** A beta build, signed in, with a flag overridden. */
@Preview
@Composable
private fun QaPreview() {
    PreviewContent { QaScreen(QaPreviews.state(), onAction = {}) }
}

@Preview
@Composable
private fun QaGuestDarkPreview() {
    PreviewContent(darkTheme = true) {
        QaScreen(QaPreviews.state(AuthState.Authenticated(userId = QaPreviews.USER_ID, isAnonymous = true, email = null)), onAction = {})
    }
}

/** Fake state for the previews: no config library, no platform, only what the screen draws. */
internal object QaPreviews {
    const val USER_ID = "3f2a9c1e-5b7d-4e8a-9f60-1c2d3e4f5a6b"

    fun state(
        auth: AuthState? = AuthState.Authenticated(userId = USER_ID, isAnonymous = false, email = "sam@example.com"),
    ): QaState = QaState(
        build = QaStateBuilder.build(
            QaStateBuilder.BuildFacts("0.2.0", 702, "beta", "4a8b4639", "main", "iOS"),
        ),
        identity = QaStateBuilder.identity(auth, "https://opson-server-prod.fly.dev", "https://gfmstgbrdwqepmeycyid.supabase.co"),
        config = listOf(
            QaConfigRow("Reminders (0.2)", "release.reminders", "release", "true", QaConfigKind.Flag, overridden = true),
            QaConfigRow("Check-in", "release.checkIn", "release", "false", QaConfigKind.Flag, overridden = false),
            QaConfigRow("Config refresh throttle", "config.refreshThrottleMs", "config", "0", QaConfigKind.Long, overridden = false),
        ),
    )
}
