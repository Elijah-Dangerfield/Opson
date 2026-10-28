package com.opson.libraries.navigation.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogLevel
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.TestDispatcherProvider
import com.opson.libraries.navigation.FileShareLauncher
import com.opson.libraries.navigation.WebLinkLauncher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OpenWebLinkTest {

    private val logs = RecordingTree()

    private val helpline = "https://helpline.example.org/chat?from=support"

    // Android's ActivityNotFoundException quotes the intent, link and all.
    private val router = DelegatingRouter(
        appScope = AppCoroutineScope(TestDispatcherProvider(StandardTestDispatcher())),
        webLinkLauncher = WebLinkLauncher { Catching.failure(IllegalStateException("No Activity found for $it")) },
        fileShareLauncher = FileShareLauncher { _, _, _ -> Catching.success(Unit) },
    )

    @BeforeTest
    fun plant() = KLog.plant(logs)

    @AfterTest
    fun uproot() = KLog.uproot(logs)

    @Test
    fun aLinkThatWillNotOpen_isAWarningThatDoesNotNameTheLink() {
        // A debug build used to rethrow here, so the bare call is part of the assertion.
        router.openWebLink(helpline)

        val entry = logs.entries.single()
        assertEquals(LogLevel.Warn, entry.level)
        val written = listOfNotNull(entry.message, entry.throwable?.message) + entry.context.extras.values.map { "$it" }
        assertFalse(written.any { "helpline" in it }, "logged the link: $written")
    }
}

private class RecordingTree : LogTree() {
    val entries = mutableListOf<LogEntry>()

    override fun log(entry: LogEntry): LogId? {
        entries += entry
        return null
    }
}
