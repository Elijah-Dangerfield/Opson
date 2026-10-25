package com.opson.features.home.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.Button
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.VerticalSpacerD500
import com.opson.system.VerticalSpacerD800
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.home_greeting
import opson.libraries.resources.generated.resources.home_report_bug
import opson.libraries.resources.generated.resources.home_send_feedback
import opson.libraries.resources.generated.resources.home_subtitle
import opson.libraries.resources.generated.resources.home_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToFeedback: () -> Unit,
    onNavigateToBugReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    Screen(modifier = modifier) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.home_title),
                typography = AppTheme.typography.Heading.H700,
                color = AppTheme.colors.text,
                textAlign = TextAlign.Center,
            )

            VerticalSpacerD800()

            if (state.userName != null) {
                Text(
                    text = stringResource(Res.string.home_greeting, state.userName.orEmpty()),
                    typography = AppTheme.typography.Body.B600,
                    color = AppTheme.colors.textSecondary,
                )
                VerticalSpacerD500()
            }

            Text(
                text = stringResource(Res.string.home_subtitle),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            VerticalSpacerD800()

            Button(onClick = onNavigateToFeedback) {
                Text(stringResource(Res.string.home_send_feedback))
            }

            VerticalSpacerD500()

            Button(onClick = onNavigateToBugReport) {
                Text(stringResource(Res.string.home_report_bug))
            }
        }
    }
}