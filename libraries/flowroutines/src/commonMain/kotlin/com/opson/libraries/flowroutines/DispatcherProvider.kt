package com.opson.libraries.flowroutines

import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.DebugException
import com.opson.libraries.core.logging.KLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.CoroutineContext

interface DispatcherProvider {
    val io: CoroutineDispatcher

    val main: CoroutineDispatcher

    /**
     * Main dispatcher in `immediate` mode — runs synchronously if the caller
     * is already on the main thread, otherwise posts. Use this when a hop
     * onto Main shouldn't burn a frame (lifecycle-aware flow collection,
     * nav-graph attachment, anything that needs to land before the next
     * frame fires).
     */
    val mainImmediate: CoroutineDispatcher

    val default: CoroutineDispatcher

    val unconfined: CoroutineDispatcher
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val io: CoroutineDispatcher
        get() = Dispatchers.IO

    override val main: CoroutineDispatcher
        get() = Dispatchers.Main

    override val mainImmediate: CoroutineDispatcher
        get() = Dispatchers.Main.immediate

    override val default: CoroutineDispatcher
        get() = Dispatchers.Default

    override val unconfined: CoroutineDispatcher
        get() = Dispatchers.Unconfined
}

/**
 * The scope for work that has to outlive the screen that started it: config
 * refreshes, queued navigation, anything fired and forgotten at app level.
 *
 * ## Why it catches
 *
 * A [SupervisorJob] keeps one failed child from cancelling its siblings, which
 * is what people reach for it for — and it is also what makes them stop
 * looking, because it does nothing about an exception nobody catches. That one
 * goes to the platform's default handler, which on Android ends the process.
 * With no handler here, every `appScope.launch` in the app is one uncaught
 * throw away from killing the app wherever the user happens to be. A
 * downstream app shipped exactly that and died at launch on a release build,
 * when a background sign-in coroutine hit a lifecycle registration made off
 * the main thread.
 *
 * So a failure is logged rather than fatal, and the logging is the point:
 * `KLog.e` carries the throwable to Sentry through `SentryLogTree`, so the
 * report that makes a bug like that findable still arrives. A debug build
 * rethrows, because locally the loud version is the useful one. That is the
 * same shape as `Catching {}.logOnFailure().throwIfDebug()`, which is how the
 * rest of the app treats a failure it cannot act on.
 *
 * None of which is a licence to skip handling errors where they happen. A
 * handler this far out knows only that something failed, so it cannot retry,
 * fall back, or tell anyone. Anything that can do better should still do it at
 * the call site.
 *
 * Cancellation never reaches here — the machinery treats it as normal
 * completion rather than as a failure, so there is nothing to filter out.
 */
@SingleIn(AppScope::class)
class AppCoroutineScope @Inject constructor(
    dispatcherProvider: DispatcherProvider
) : CoroutineScope {

    private val job = SupervisorJob()

    private val failures = CoroutineExceptionHandler { _, throwable ->
        KLog.e(throwable) { "An app-scope coroutine failed" }
        if (BuildInfo.isDebug) throw DebugException(throwable)
    }

    override val coroutineContext: CoroutineContext = job + dispatcherProvider.default + failures
}
