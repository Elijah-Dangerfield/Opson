package com.opson.libraries.ui.snackbar

import androidx.compose.material3.SnackbarResult
import com.opson.libraries.ui.components.AppSnackbarVisuals
import com.opson.libraries.ui.components.SnackbarDuration
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.Icons
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.random.Random
import com.opson.libraries.core.BuildInfo
import kotlin.time.Duration

fun showSnackBar(
    message: String,
    title: String? = null,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Short,
    withDismissAction: Boolean = true,
    icon: IconResource? = null,
    delayBy: Duration = Duration.ZERO,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    SnackBarPresenter.show(
        message = message,
        title = title,
        actionLabel = actionLabel,
        duration = duration,
        withDismissAction = withDismissAction,
        delayBy = delayBy,
        icon = icon,
        onAction = onAction,
        onDismiss = onDismiss
    )
}

fun showDebugSnackBar(
    message: String,
    title: String? = null,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Short,
    withDismissAction: Boolean = true,
    icon: IconResource = Icons.Bug(null),
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
): Boolean {
    if (!BuildInfo.isDebug) return false
    return SnackBarPresenter.show(
        message = message,
        title = title,
        actionLabel = actionLabel,
        duration = duration,
        withDismissAction = withDismissAction,
        icon = icon,
        onAction = onAction,
        onDismiss = onDismiss
    )
}

fun showSnackBar(
    message: SnackbarMessage
) {
    SnackBarPresenter.show(message)
}

/**
 * Global presenter that allows any layer to enqueue snackbars without owning a host.
 * The App composable owns the host and collects [requests] to render them.
 */
object SnackBarPresenter {

    private val _requests = MutableSharedFlow<SnackbarMessage>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val requests: SharedFlow<SnackbarMessage> = _requests.asSharedFlow()

    fun show(message: SnackbarMessage): Boolean = _requests.tryEmit(message)

    fun show(
        message: String,
        title: String? = null,
        actionLabel: String? = null,
        duration: SnackbarDuration = SnackbarDuration.Short,
        withDismissAction: Boolean = true,
        delayBy: Duration = Duration.ZERO,
        icon: IconResource? = null,
        onAction: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null,
    ): Boolean {
        return show(
            SnackbarMessage(
                title = title,
                message = message,
                actionLabel = actionLabel,
                duration = duration,
                withDismissAction = withDismissAction,
                delayBy = delayBy,
                icon = icon,
                onAction = onAction,
                onDismiss = onDismiss
            )
        )
    }
}

/**
 * Representation of a snackbar to be presented through [SnackBarPresenter].
 */
data class SnackbarMessage(
    val title: String? = null,
    val message: String,
    val actionLabel: String? = null,
    val duration: SnackbarDuration = SnackbarDuration.Short,
    val withDismissAction: Boolean = true,
    val delayBy: Duration = Duration.ZERO,
    val icon: IconResource? = null,
    val id: Long = Random.nextLong(),
    val onAction: (() -> Unit)? = null,
    val onDismiss: (() -> Unit)? = null,
) {
    /** Runs the callback for how the snackbar ended. The message carries its own callbacks, so none outlive it. */
    internal fun deliver(result: SnackbarResult) {
        when (result) {
            SnackbarResult.ActionPerformed -> onAction?.invoke()
            SnackbarResult.Dismissed -> onDismiss?.invoke()
        }
    }
}

fun SnackbarMessage.toVisuals(): AppSnackbarVisuals = AppSnackbarVisuals(
    id = id,
    title = title,
    icon = icon,
    message = message,
    actionLabel = actionLabel,
    withDismissAction = withDismissAction,
    duration = duration.toMaterial()
)
