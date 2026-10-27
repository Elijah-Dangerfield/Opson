package com.opson.libraries.ui.components

import androidx.compose.runtime.Composable
import com.opson.libraries.ui.PreviewLightDark
import org.jetbrains.compose.ui.tooling.preview.Preview

// The subtitle is a parameter of PageHeader now. This preview stays in its own file because the
// file and function name are its design-sync screen id.
@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun PageHeaderSubtitlePreview() {
    PreviewLightDark {
        PageHeader(title = "Nutrients", subtitle = "Sunday, Oct 25", backLabel = "‹ Today", onBack = {})
    }
}
