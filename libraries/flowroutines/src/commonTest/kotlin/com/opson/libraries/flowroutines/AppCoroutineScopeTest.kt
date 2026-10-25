package com.opson.libraries.flowroutines

import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.Catching
import com.opson.libraries.core.DebugException
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogLevel
import com.opson.libraries.core.logging.LogTree
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Covers: a failure in an app-scope coroutine is reported through the scope's
 * own [CoroutineExceptionHandler] — logged at error level so it reaches Sentry
 * through `SentryLogTree`, and rethrown as a [DebugException] only in debug
 * builds — rather than left to the platform's default handler, which on
 * Android ends the process.
 *
 * NOT covered: that kotlinx hands a failed child to the handler in the scope's
 * context. That is its own machinery, and driving it from a test means an
 * uncaught throw, which `runTest` collects and reports against whichever test
 * happens to be running when it lands. So the handler is invoked directly.
 */
class AppCoroutineScopeTest {

    private val logTree = RecordingLogTree()

    @AfterTest
    fun tearDown() {
        KLog.uproot(logTree)
    }

    @Test
    fun failure_isLoggedAtErrorWithTheThrowable() {
        KLog.plant(logTree)
        val failure = IllegalStateException("registration ran on the wrong thread")

        handleInAppScope(failure)

        val entry = logTree.entries.single()
        assertEquals(LogLevel.Error, entry.level)
        assertEquals(failure, entry.throwable)
    }

    @Test
    fun failure_rethrowsOnlyInDebugBuilds() {
        val failure = IllegalStateException("registration ran on the wrong thread")

        val rethrown = handleInAppScope(failure)

        if (BuildInfo.isDebug) {
            assertIs<DebugException>(rethrown)
            assertEquals(failure, rethrown.cause)
        } else {
            assertNull(rethrown)
        }
    }

    /**
     * Hands [failure] to the scope's handler and returns whatever the handler
     * threw. Swallowing that is the point: in a debug build the handler rethrows
     * by design, and letting it escape here would end the test run rather than
     * be asserted on.
     */
    private fun handleInAppScope(failure: Throwable): Throwable? {
        val scope = AppCoroutineScope(ImmediateDispatcherProvider)
        val handler = assertNotNull(
            scope.coroutineContext[CoroutineExceptionHandler],
            "AppCoroutineScope must install a CoroutineExceptionHandler; without one an " +
                "uncaught failure in any appScope.launch reaches the platform default handler.",
        )
        return Catching { handler.handleException(scope.coroutineContext, failure) }
            .exceptionOrNull()
    }
}

private object ImmediateDispatcherProvider : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
    override val mainImmediate: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}

private class RecordingLogTree : LogTree() {
    val entries = mutableListOf<LogEntry>()

    override fun log(entry: LogEntry): LogId? {
        entries += entry
        return null
    }
}
