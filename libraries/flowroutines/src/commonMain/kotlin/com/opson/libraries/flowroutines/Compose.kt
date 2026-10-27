package com.opson.libraries.flowroutines

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.opson.libraries.core.logging.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private const val UNNAMED_FLOW = "unnamed flow"

/**
 * The one place lifecycle-gated collection happens, so it is also the one place
 * that says so out loud.
 *
 * A `repeatOnLifecycle` collector is a spot where the app can go quiet without
 * going wrong: the collector detaches below [state], the producer keeps
 * producing, and nothing in the logs marks the moment. Chasing a stall that way
 * means inferring the lifecycle from what *stopped* appearing, which is a slow
 * way to learn something the framework already knows. So both edges are logged —
 * entry, and a `finally` for the exit — tagged with what is being collected.
 *
 * Pass a [tag] that names the producer, not the screen. "what stopped arriving"
 * is the question the log has to answer.
 */
private suspend fun <T> Flow<T>.collectWithLifecycle(
    lifecycle: Lifecycle,
    state: Lifecycle.State,
    tag: String,
    onItem: suspend (T) -> Unit,
) {
    lifecycle.repeatOnLifecycle(state) {
        KLog.i("Collecting $tag at ${state.name}")
        try {
            withContext(Dispatchers.Main.immediate) {
                collect(onItem)
            }
        } finally {
            KLog.i("Stopped collecting $tag, lifecycle fell below ${state.name}")
        }
    }
}

/**
 * Observe a flow with the lifecycle of the current composable
 * @param flow the flow to observe
 * @param tag names the flow in the start/stop logs
 * @param onItem the action to take when an item is emitted from the flow
 *
 * Observes on main immediate which ensures no emissions are missed
 */
@Composable
fun <T> ObserveWithLifecycle(
    flow: Flow<T>,
    tag: String = UNNAMED_FLOW,
    onItem: suspend (T) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner.lifecycle, flow) {
        flow.collectWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            state = Lifecycle.State.STARTED,
            tag = tag,
            onItem = onItem,
        )
    }
}

@Composable
fun <T : Any> SEAViewModel<*, T, *>.ObserveEvents(onItem: suspend (T) -> Unit) {
    ObserveWithLifecycle(
        flow = eventFlow,
        tag = "${this::class.simpleName ?: "SEAViewModel"} events",
        onItem = onItem,
    )
}

/**
 * Observe a flow with the provided lifecycle
 * @param tag names the flow in the start/stop logs
 * @param onItem the action to take when an item is emitted from the flow
 *
 * starts collection when the lifecycle reaches the started state,
 * stops collection when the lifecycle falls below the started state,
 *
 * Observes on main immediate which ensures no emissions are missed
 */
suspend fun <T> Flow<T>.observeWithLifecycle(
    state: Lifecycle.State = Lifecycle.State.STARTED,
    lifecycle: Lifecycle,
    tag: String = UNNAMED_FLOW,
    onItem: suspend (T) -> Unit
) {
    collectWithLifecycle(
        lifecycle = lifecycle,
        state = state,
        tag = tag,
        onItem = onItem,
    )
}
