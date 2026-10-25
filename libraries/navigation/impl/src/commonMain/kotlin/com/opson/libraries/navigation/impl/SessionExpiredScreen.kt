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
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.Dimension
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.session_expired_message
import opson.libraries.resources.generated.resources.session_expired_title
import opson.libraries.resources.generated.resources.session_lost_message
import opson.libraries.resources.generated.resources.session_lost_title
import opson.libraries.resources.generated.resources.session_sign_in_again
import opson.libraries.resources.generated.resources.session_start_fresh
import opson.libraries.resources.generated.resources.session_start_fresh_failed
import opson.libraries.resources.generated.resources.session_working
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Blocking surface for a server-rejected session. Back is swallowed — the
 * stack beneath is unusable until the user recovers. Claimed accounts get
 * "Sign in again"; anonymous guests get "Start fresh" (their session is
 * unrecoverable, so a new guest account is minted in place).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SessionExpiredScreen(
    wasAnonymous: Boolean,
    working: Boolean,
    startFreshFailed: Boolean,
    onSignInAgain: () -> Unit,
    onStartFresh: () -> Unit,
) {
    BackHandler { doNothing() }

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
                text = if (wasAnonymous) {
                    stringResource(Res.string.session_lost_title)
                } else {
                    stringResource(Res.string.session_expired_title)
                },
                typography = AppTheme.typography.Display.D1000,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(Dimension.D500))

            Text(
                text = if (wasAnonymous) {
                    stringResource(Res.string.session_lost_message)
                } else {
                    stringResource(Res.string.session_expired_message)
                },
                typography = AppTheme.typography.Body.B400,
                textAlign = TextAlign.Center,
            )

            if (startFreshFailed) {
                Spacer(modifier = Modifier.height(Dimension.D400))
                Text(
                    text = stringResource(Res.string.session_start_fresh_failed),
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.status.warning,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.weight(2f))

            ButtonPrimary(
                enabled = !working,
                onClick = if (wasAnonymous) onStartFresh else onSignInAgain,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        when {
                            working -> Res.string.session_working
                            wasAnonymous -> Res.string.session_start_fresh
                            else -> Res.string.session_sign_in_again
                        },
                    ),
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Preview
@Composable
private fun SessionExpiredScreenPreview_Claimed() {
    PreviewContent {
        SessionExpiredScreen(
            wasAnonymous = false,
            working = false,
            startFreshFailed = false,
            onSignInAgain = {},
            onStartFresh = {},
        )
    }
}

@Preview
@Composable
private fun SessionExpiredScreenPreview_Guest_Failed() {
    PreviewContent {
        SessionExpiredScreen(
            wasAnonymous = true,
            working = false,
            startFreshFailed = true,
            onSignInAgain = {},
            onStartFresh = {},
        )
    }
}
