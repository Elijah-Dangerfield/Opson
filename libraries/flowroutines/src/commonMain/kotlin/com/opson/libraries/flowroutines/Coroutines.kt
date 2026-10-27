package com.opson.libraries.flowroutines

import com.opson.libraries.core.Catching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

suspend fun <T> tryWithTimeout(
    duration: Duration,
    block: suspend CoroutineScope.() -> Catching<T>
): Catching<T> {
    return try {
        withTimeout(duration, block)
    } catch (e: TimeoutCancellationException) {
        Catching.failure(e)
    }
}