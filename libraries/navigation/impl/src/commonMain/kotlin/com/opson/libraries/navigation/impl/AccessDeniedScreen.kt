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
import com.opson.libraries.core.doNothing
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
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AccessDeniedScreen(
    reason: String,
    until: String?,
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
            if (until != null) {
                Spacer(modifier = Modifier.height(Dimension.D400))
                Text(
                    text = stringResource(Res.string.access_denied_until, until),
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
            until = null,
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
            until = "2026-08-01T00:00:00Z",
            appealUrl = null,
            onAppeal = {},
        )
    }
}
