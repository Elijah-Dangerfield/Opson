package com.opson.libraries.ui.components.icon

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.text.Text
import com.opson.system.HorizontalSpacerD800
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Design‑system icon resource, similar to `ColorResource`/`TypographyResource`.
 *
 * Icons are always referenced through this type so callers cannot
 * reach directly for raw Material icons.
 */
@Immutable
data class IconResource(
    val imageVector: ImageVector,
    val contentDescription: String?,
    val identifier: String? = null,
)

/** The older icon set, kept for the few callers that still use it. New code uses [AppIcons]. */
enum class Icons(internal val default: ImageVector) {
    Bug(androidx.compose.material.icons.Icons.Rounded.BugReport),
    X(androidx.compose.material.icons.Icons.Rounded.Close),
    Check(androidx.compose.material.icons.Icons.Rounded.Check),
    Settings(androidx.compose.material.icons.Icons.Rounded.Settings),
    ChevronLeft(androidx.compose.material.icons.Icons.Rounded.ChevronLeft),
    ;

    operator fun invoke(contentDescription: String?): IconResource {
        return IconResource(
            imageVector = default,
            contentDescription = contentDescription,
            identifier = name,
        )
    }
}

@Preview
@Composable
private fun IconPreviewGrid() {
    PreviewContent {
        Column {
            Icons.entries.forEach { icon ->
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.border(1.dp, Color.Gray).padding(vertical = 16.dp),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = icon.default,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    HorizontalSpacerD800()
                    Text(text = icon.name, maxLines = 1)
                }
            }
        }
    }
}
