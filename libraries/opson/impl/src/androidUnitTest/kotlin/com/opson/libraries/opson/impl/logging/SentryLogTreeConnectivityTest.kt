package com.opson.libraries.opson.impl.logging

import com.opson.libraries.core.logging.LogContext
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogLevel
import java.net.ConnectException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An offline device's expected connectivity failures must
 * not become Sentry error events — one phone in a tunnel inflated the client
 * error panel with 59 events in 43 minutes. Platform-typed exceptions live in
 * androidUnitTest; the Darwin side is pinned in :libraries:networking iosTest.
 */
class SentryLogTreeConnectivityTest {

    private val tree = SentryLogTree(
        minBreadcrumbLevel = LogLevel.Info,
        minEventLevel = LogLevel.Error,
    )

    @Test
    fun `offline connectivity failure at error level is not captured as an event`() {
        assertFalse(tree.shouldCaptureEvent(errorEntry(UnknownHostException("Unable to resolve host"))))
    }

    @Test
    fun `offline failure wrapped as a cause is still not captured`() {
        val wrapped = RuntimeException("equipment sync failed", UnknownHostException("no DNS"))
        assertFalse(tree.shouldCaptureEvent(errorEntry(wrapped)))
    }

    @Test
    fun `connection refused stays captured - that is the backend, not the device`() {
        assertTrue(tree.shouldCaptureEvent(errorEntry(ConnectException("Connection refused"))))
    }

    private fun errorEntry(throwable: Throwable): LogEntry = LogEntry(
        level = LogLevel.Error,
        tag = "test",
        message = throwable.message ?: "error",
        throwable = throwable,
        context = LogContext.Empty,
    )
}
