package com.opson.server.plugins

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.opson.server.db.DatabaseBusyException
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.After
import org.junit.Before
import org.slf4j.LoggerFactory
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusPagesTest {

    private val appender = ListAppender<ILoggingEvent>()
    private val statusPagesLogger = LoggerFactory.getLogger("StatusPages") as Logger

    @Before
    fun attach() {
        appender.start()
        statusPagesLogger.addAppender(appender)
    }

    @After
    fun detach() {
        statusPagesLogger.detachAppender(appender)
    }

    private fun logged(): String = appender.list.joinToString("\n") { event ->
        buildString {
            append(event.formattedMessage)
            generateSequence(event.throwableProxy) { it.cause }.forEach { append('\n').append(it.className).append(": ").append(it.message) }
        }
    }

    @Test
    fun aBusyDatabaseIsA503WithRetryAfter() = testApplication {
        application {
            installSerialization()
            installStatusPages()
            routing { get("/busy") { throw DatabaseBusyException(SQLException("canceling statement", "57014")) } }
        }

        val response = client.get("/busy")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("2", response.headers[HttpHeaders.RetryAfter])
        assertTrue(response.bodyAsText().contains("\"busy\""))
    }

    @Test
    fun aDatabaseErrorNeverReachesTheBodyOrTheLogWithItsValues() = testApplication {
        application {
            installSerialization()
            installStatusPages()
            routing {
                get("/boom") {
                    throw RuntimeException(
                        "wrapped",
                        SQLException("ERROR: value too long. SQL: INSERT INTO day_records (note) VALUES ('my private note')", "22001"),
                    )
                }
            }
        }

        val response = client.get("/boom")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertFalse(response.bodyAsText().contains("private note"), response.bodyAsText())
        val log = logged()
        assertTrue(log.contains("sqlState=22001"), log)
        assertFalse(log.contains("private note"), log)
    }

    @Test
    fun anUnexpectedErrorsMessageStaysOutOfTheBody() = testApplication {
        application {
            installSerialization()
            installStatusPages()
            routing { get("/boom") { error("token=abc123 for user 42") } }
        }

        val body = client.get("/boom").bodyAsText()

        assertFalse(body.contains("abc123"), body)
        assertTrue(body.contains("\"internal\""))
    }

    @Test
    fun exposedDoesNotLogStatementsBelowError() {
        // Exposed logs each failed transaction's statements, bound values included, at WARN.
        assertFalse(LoggerFactory.getLogger("Exposed").isWarnEnabled)
    }
}
