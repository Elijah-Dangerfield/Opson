package com.opson.server.plugins

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import org.slf4j.Logger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The shipped `logback.xml`, loaded into a context of its own so the test JVM's logging is
 * untouched. `fly.toml` sets `LOG_LEVEL`, which nothing used to read.
 */
class LogbackConfigTest {

    @Test
    fun theRootLevelFollowsLogLevel() {
        assertEquals(Level.WARN, configured(logLevel = "WARN").getLogger(Logger.ROOT_LOGGER_NAME).level)
        assertEquals(Level.DEBUG, configured(logLevel = "debug").getLogger(Logger.ROOT_LOGGER_NAME).level)
    }

    @Test
    fun withoutLogLevel_theRootIsInfo() {
        assertEquals(Level.INFO, configured(logLevel = null).getLogger(Logger.ROOT_LOGGER_NAME).level)
    }

    @Test
    fun exposedStaysAtErrorWhateverLogLevelSays() {
        // Exposed logs failed statements with their bound values, which are diary rows.
        assertEquals(Level.ERROR, configured(logLevel = "DEBUG").getLogger("Exposed").level)
    }

    private fun configured(logLevel: String?): LoggerContext {
        val context = LoggerContext()
        logLevel?.let { context.putProperty("LOG_LEVEL", it) }
        JoranConfigurator().apply { setContext(context) }
            .doConfigure(checkNotNull(javaClass.classLoader.getResource("logback.xml")))
        return context
    }
}
