package com.opson.libraries.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.button.Button
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconButton
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.Icons
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.HorizontalSpacerD600
import com.opson.system.Radii
import com.opson.system.VerticalSpacerD500
import com.opson.system.VerticalSpacerD800
import com.opson.system.color.ProvideContentColor
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.random.Random
import opson.libraries.resources.generated.resources.Res as SharedRes
import opson.libraries.resources.generated.resources.ds_close
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.SnackbarDuration as MaterialSnackbarDuration
@Composable
fun Snackbar(
    data: AppSnackbarData,
    modifier: Modifier = Modifier,
    shape: Shape = SnackbarDefaults.shape,
    containerColor: ColorResource = SnackbarDefaults.backgroundColor,
    contentColor: ColorResource = SnackbarDefaults.contentColor,
) {
    val message = data.visuals.message
    val title = data.visuals.title
    val actionLabel = data.visuals.actionLabel
    val icon = data.visuals.icon

    ProvideContentColor(color = contentColor) {

        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimension.D500,
                    vertical = Dimension.D500
                )
                .shadow(Dimension.D200, shape)
                .background(containerColor.color, shape)
                .padding(
                    vertical = Dimension.D500,
                    horizontal = Dimension.D200
                )
                .height(IntrinsicSize.Min)

        ) {
            Column(
                modifier = Modifier.weight(1f)
                    .padding(start = Dimension.D500, end = Dimension.D500)

            ) {
                if (title != null) {

                    VerticalSpacerD500()

                    Row(verticalAlignment = CenterVertically) {
                        if (icon != null) {
                            Icon(icon = icon)
                            HorizontalSpacerD600()
                        }

                        Text(
                            text = title,
                            typography = AppTheme.typography.Heading.H600,
                        )

                    }

                    VerticalSpacerD500()
                }

                VerticalSpacerD500()

                Row(verticalAlignment = CenterVertically) {
                    if (icon != null && title.isNullOrBlank()) {
                        Icon(icon = icon)
                        HorizontalSpacerD600()
                    }

                    Text(
                        text = message,
                        typography = AppTheme.typography.Body.B600,
                    )
                }

                VerticalSpacerD800()

                if (actionLabel != null) {
                    Button(
                        onClick = data::performAction,
                        size = ButtonSize.ExtraSmall,
                    ) {
                        Text(text = actionLabel)
                    }
                    VerticalSpacerD500()
                }
            }


            if (data.visuals.withDismissAction) {
                IconButton(
                    size = IconButton.Size.Small,
                    icon = Icons.X(stringResource(SharedRes.string.ds_close)),
                    onClick = data::dismiss,
                )
            }
        }
    }
}

@Composable
fun snackBarData(
    title: String? = null,
    message: String,
    actionLabel: String? = null,
    icon: IconResource? = null,
    withDismissAction: Boolean = true,
    duration: SnackbarDuration = SnackbarDuration.Short,
    onAction: () -> Unit = {},
    onDismiss: () -> Unit = {},
): AppSnackbarData = object : AppSnackbarData {
    override val visuals: AppSnackbarVisuals = AppSnackbarVisuals(
        title = title,
        icon = icon,
        message = message,
        actionLabel = actionLabel,
        withDismissAction = withDismissAction,
        duration = duration.fromMaterial()
    )

    override fun performAction() {
        onAction()
    }

    override fun dismiss() {
        onDismiss()
    }
}

fun snackBarData(
    visuals: AppSnackbarVisuals,
    onAction: () -> Unit = {},
    onDismiss: () -> Unit = {},
): AppSnackbarData = object : AppSnackbarData {
    override val visuals: AppSnackbarVisuals = visuals

    override fun performAction() {
        onAction()
    }

    override fun dismiss() {
        onDismiss()
    }
}

@Preview
@Composable
private fun PreviewSnackbarDebug() {
    PreviewContent(backgroundColor = ColorResource.White) {
        Snackbar(
            data = snackBarData(
                title = "Title",
                message = "This is some text that describes something that takes up a lot of space.".repeat(
                    2
                ),
                icon = Icons.Bug(null),
                actionLabel = "Do something",
                withDismissAction = true,
            )
        )
    }
}

@Preview
@Composable
private fun PreviewSnackbar2() {
    PreviewContent {
        Snackbar(
            data = snackBarData(
                title = "Title",
                message = "This is some text that describes something that takes up a lot of space.".repeat(
                    2
                ),
                actionLabel = "Do something",
                withDismissAction = true,
            )
        )
    }
}

@Preview
@Composable
private fun PreviewSnackbarDeveloper() {
    PreviewContent {
        Snackbar(
            data = snackBarData(
                message = "Hello World",
                icon = Icons.Bug(null)
            )
        )
    }
}

@Preview
@Composable
private fun PreviewSnackbar() {
    PreviewContent {
        Snackbar(
            data = snackBarData(
                message = "Hello World",
                icon = null
            )
        )
    }
}

object SnackbarDefaults {
    /** Default shape of a snackbar. */
    val shape: Shape @Composable get() = Radii.Banner.shape

    /** Default color of a snackbar. */
    val backgroundColor: ColorResource @Composable get() = AppTheme.colors.surfaceSecondary

    /** Default content color of a snackbar. */
    val contentColor: ColorResource @Composable get() = AppTheme.colors.onSurfaceSecondary
}

@Stable
interface AppSnackbarData {
    val visuals: AppSnackbarVisuals

    /**
     * Function to be called when Snackbar action has been performed to notify the listeners.
     */
    fun performAction()

    /**
     * Function to be called when Snackbar is dismissed either by timeout or by the user.
     */
    fun dismiss()
}

@Stable
data class AppSnackbarVisuals(
    val id: Long = Random.nextLong(),
    val title: String?,
    val icon: IconResource? = null,
    override val message: String,
    override val actionLabel: String?,
    override val withDismissAction: Boolean,
    override val duration: androidx.compose.material3.SnackbarDuration
) : SnackbarVisuals


enum class SnackbarDuration {
    /**
     * Show the Snackbar for a short period of time
     */
    Short,

    /**
     * Show the Snackbar for a long period of time
     */
    Long,

    /**
     * Show the Snackbar indefinitely until explicitly dismissed or action is clicked
     */
    Indefinite;

    fun toMaterial(): androidx.compose.material3.SnackbarDuration {
        return when (this) {
            Short -> androidx.compose.material3.SnackbarDuration.Short
            Long -> androidx.compose.material3.SnackbarDuration.Long
            Indefinite -> androidx.compose.material3.SnackbarDuration.Indefinite
        }
    }

    fun fromMaterial(): MaterialSnackbarDuration {
        return when (this) {
            Short -> MaterialSnackbarDuration.Short
            Long -> MaterialSnackbarDuration.Long
            Indefinite -> MaterialSnackbarDuration.Indefinite
        }
    }
}
