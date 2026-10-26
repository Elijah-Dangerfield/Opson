package com.opson.libraries.ui.components.dialog.bottomsheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A bottom sheet drawn in place, for previews: the scrim, the sheet's rounded top at
 * [heightFraction] of the screen, the grab handle, and [content]. The real [BottomSheet] is a
 * popup that a screenshot cannot see, so a sheet preview wraps its content in this to look the way
 * the design draws it.
 */
@Composable
fun SheetFrame(
    modifier: Modifier = Modifier,
    heightFraction: Float = BottomSheetDefaults.FullHeightFraction,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(AppTheme.colors.scrim)) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(heightFraction)
                .clip(AppTheme.shapes.sheet)
                .background(AppTheme.colors.bg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetHandle(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            Column(modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}

@Suppress("VerifyStrings")
@Preview(widthDp = 390, heightDp = 500)
@Composable
private fun SheetFramePreview() {
    PreviewContent {
        SheetFrame(heightFraction = 0.6f) {
            Text("Sheet content", modifier = Modifier.padding(20.dp))
        }
    }
}
