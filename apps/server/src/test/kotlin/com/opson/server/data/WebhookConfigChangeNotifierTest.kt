package com.opson.server.data

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.opson.server.domain.ConfigChangeEvent
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebhookConfigChangeNotifierTest {

    private val appender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger(WebhookConfigChangeNotifier::class.java) as Logger
    private val scope = CoroutineScope(SupervisorJob())
    private val event = ConfigChangeEvent(actor = "admin", action = "update_flag", flagPath = "upgrade.maintenanceMode", detail = null)

    @Before
    fun attach() {
        appender.start()
        logger.addAppender(appender)
    }

    @After
    fun detach() {
        logger.detachAppender(appender)
        scope.cancel()
    }

    // Real time: the post runs on Dispatchers.IO, and this waits for it to finish.
    private fun send(url: String) = runBlocking {
        WebhookConfigChangeNotifier(url, "test", scope).changed(event)
        scope.coroutineContext.job.children.toList().joinAll()
    }

    private val lines get() = appender.list.map { it.formattedMessage }

    @Test
    fun aMalformedUrlIsLoggedWithoutTheUrl() {
        // A secret pasted with a trailing newline: URI.create refuses it and quotes it in its message.
        send("https://hooks.example.invalid/services/T000/SECRET-TOKEN\n")

        assertEquals(1, lines.size, "$lines")
        assertTrue(lines.none { "SECRET-TOKEN" in it }, "$lines")
    }

    @Test
    fun aNon2xxAnswerIsLogged() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
        try {
            send("http://127.0.0.1:${server.address.port}/services/revoked")
        } finally {
            server.stop(0)
        }

        assertEquals(listOf("Config-change webhook answered HTTP 404"), lines)
    }
}
