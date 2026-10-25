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
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.Button
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.core.doNothing
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.VerticalSpacerD1000
import com.opson.system.VerticalSpacerD1200
import com.opson.system.VerticalSpacerD1600
import com.opson.system.VerticalSpacerD500
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.common_error_code
import opson.libraries.resources.generated.resources.common_report_to_developers
import opson.libraries.resources.generated.resources.common_report_to_developers_aside
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun BlockingErrorScreen(
    title: String,
    subtitle: String,
    errorCode: Int?,
    onReportToDevelopers: (() -> Unit)? = null,
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
                text = title,
                typography = AppTheme.typography.Display.D1000,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(Dimension.D500))
            Text(
                text = subtitle,
                typography = AppTheme.typography.Body.B400,
                textAlign = TextAlign.Center,
            )
            errorCode?.let {
                Spacer(modifier = Modifier.height(Dimension.D400))
                Text(
                    text = stringResource(Res.string.common_error_code, it),
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.weight(2f))

            if (onReportToDevelopers != null) {
                Button(
                    size = ButtonSize.Large,
                    onClick = onReportToDevelopers,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(Res.string.common_report_to_developers))
                }

                VerticalSpacerD500()

                Text(stringResource(Res.string.common_report_to_developers_aside))

                VerticalSpacerD1600()
            }
        }
    }
}

@Preview
@Composable
private fun BlockingErrorScreenPreview() {
    PreviewContent {
        BlockingErrorScreen(
            title = "Can't load apps",
            subtitle = "Double-check your connection and try again.",
            errorCode = 1001,
            onReportToDevelopers = {}
        )
    }
}
