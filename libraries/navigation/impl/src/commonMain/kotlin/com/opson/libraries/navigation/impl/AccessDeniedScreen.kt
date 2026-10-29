package com.opson.libraries.navigation.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextAlign
import com.opson.libraries.core.Catching
import com.opson.libraries.core.doNothing
import com.opson.libraries.ui.time.DateCopy
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.VerticalSpacerD1600
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.access_denied_appeal
import opson.libraries.resources.generated.resources.access_denied_banned_message
import opson.libraries.resources.generated.resources.access_denied_banned_title
import opson.libraries.resources.generated.resources.access_denied_message
import opson.libraries.resources.generated.resources.access_denied_suspended_message
import opson.libraries.resources.generated.resources.access_denied_suspended_title
import opson.libraries.resources.generated.resources.access_denied_title
import opson.libraries.resources.generated.resources.access_denied_until
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Blocking surface for the locked `403` access-denied envelope. The copy is
 * keyed off the machine-readable [reason] (the server never sends copy on the
 * wire); unknown tokens fall back to a generic block message. Back is
 * swallowed — a banned/suspended user has nowhere to go behind this.
 * [restoredOn] is the lift date as [accessRestoredDate] spells it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AccessDeniedScreen(
    reason: String,
    restoredOn: String?,
    appealUrl: String?,
    onAppeal: (String) -> Unit,
) {
    BackHandler { doNothing() }
    val (title, message) = when (reason) {
        "banned" -> stringResource(Res.string.access_denied_banned_title) to
            stringResource(Res.string.access_denied_banned_message)
        "suspended" -> stringResource(Res.string.access_denied_suspended_title) to
            stringResource(Res.string.access_denied_suspended_message)
        else -> stringResource(Res.string.access_denied_title) to
            stringResource(Res.string.access_denied_message)
    }
    Screen { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Dimension.D1000),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = title,
                typography = AppTheme.typography.Display.D1000,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(Dimension.D500))
            Text(
                text = message,
                typography = AppTheme.typography.Body.B400,
                textAlign = TextAlign.Center,
            )
            if (restoredOn != null) {
                Spacer(modifier = Modifier.height(Dimension.D400))
                Text(
                    text = stringResource(Res.string.access_denied_until, restoredOn),
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.weight(2f))

            if (appealUrl != null) {
                ButtonSecondary(
                    onClick = { onAppeal(appealUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(Res.string.access_denied_appeal))
                }
                VerticalSpacerD1600()
            }
        }
    }
}

@Preview
@Composable
private fun AccessDeniedScreenPreview_Banned() {
    PreviewContent {
        AccessDeniedScreen(
            reason = "banned",
            restoredOn = null,
            appealUrl = "https://example.com/appeal",
            onAppeal = {},
        )
    }
}

@Preview
@Composable
private fun AccessDeniedScreenPreview_Suspended_WithUntil() {
    PreviewContent {
        AccessDeniedScreen(
            reason = "suspended",
            restoredOn = "Aug 1, 2026",
            appealUrl = null,
            onAppeal = {},
        )
    }
}

/**
 * The server's ISO-8601 lift time as a date on the phone's calendar, `Aug 1, 2026`, or `null`
 * when it doesn't parse, so the screen leaves the line out rather than printing a raw timestamp.
 */
internal fun accessRestoredDate(until: String, timeZone: TimeZone): String? =
    Catching { Instant.parse(until) }.getOrNull()
        ?.toLocalDateTime(timeZone)?.date
        ?.let(DateCopy::shortMonthDayYear)
