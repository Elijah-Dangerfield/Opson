package com.opson.libraries.core

import com.opson.libraries.core.logging.KLog

typealias Catching<T> = Result<T>

/**
 * [runCatching] that lets cancellation through. A cancelled coroutine has to
 * stop, and a `Result.failure` holding its [kotlin.coroutines.cancellation.CancellationException]
 * would let the caller carry on as if a call had merely failed. A
 * [kotlinx.coroutines.TimeoutCancellationException] is the exception: it is
 * a value here, so `Catching { withTimeout(..) { .. } }` reads as a failure.
 */
inline fun <T> Catching(f: () -> T): Catching<T> = runCatching(f)
    .onFailure {
        if (it.shouldNotBeCaught) throw it
    }

fun <T> Catching<T>.logOnFailure(message: () -> String? = {null}): Catching<T> = onFailure {
    KLog.e(it) { message() ?: "Catching Failure" }
}
fun <T> Catching<T>.logOnFailure(message:  String): Catching<T> = onFailure {
    KLog.e(it) { message }
}

fun <T> Catching<T>.throwIfDebug(): Catching<T> = onFailure {
    if (BuildInfo.isDebug) {
        throw DebugException(it)
    }
}

inline fun <T> Catching<T>.eitherWay(block: (Catching<T>) -> Unit) = this.also(block)

fun <T> Catching<T>.ignoreValue(): Catching<Unit> = this.map { }

inline fun <T> Catching<T>.mapFailure(f: (Throwable) -> Throwable): Catching<T> {
    return when (val exception = exceptionOrNull()) {
        null -> this
        else -> Result.failure(f(exception))
    }
}

inline fun <T, R> Catching<T>.flatMap(f: (right: T) -> Catching<R>): Catching<R> {
    val exception = exceptionOrNull()
    return when {
        exception != null -> Catching.failure(exception)
        else -> f(getOrThrow())
    }
}
