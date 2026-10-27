package com.opson.libraries.core

import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.cancellation.CancellationException

val Throwable.shouldNotBeCaught: Boolean
    get() = this is CancellationException && this !is TimeoutCancellationException

/**
 * Marker for typed, expected control-flow throwables — a short-circuit the caller
 * is meant to handle, not a failure. Telemetry sinks drop these before they turn
 * into error events, so an expected signal that reaches an error-level log line
 * never inflates error counts. [AuthUnready] is the reference implementor.
 */
interface ExpectedControlFlow

val Throwable.isExpectedControlFlow: Boolean
    get() = this is ExpectedControlFlow

class DebugException(e: Throwable? = null, message: String? = e?.message) :
    Exception(message, e)

fun throwIfDebug(throwable: Throwable) {
    if (BuildInfo.isDebug) {
        throw DebugException(message = throwable.message.orEmpty())
    }
}
