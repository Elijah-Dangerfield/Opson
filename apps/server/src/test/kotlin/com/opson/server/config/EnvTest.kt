package com.opson.server.config

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * `Env` is the only place the server reads environment variables. These tests
 * pin the contract every config field relies on: OS env beats the file, blank
 * is unset, `require` fails fast, `int` falls back to its default.
 */
class EnvTest {

    @Test
    fun osEnvBeatsFile() {
        val file = tempEnv("FOO=from-file")
        val env = Env(envFilePath = file.path, realEnv = { if (it == "FOO") "from-env" else null })
        assertEquals("from-env", env["FOO"])
    }

    @Test
    fun fallsBackToFileWhenOsEnvMissing() {
        val file = tempEnv("FOO=from-file")
        val env = Env(envFilePath = file.path, realEnv = { null })
        assertEquals("from-file", env["FOO"])
    }

    @Test
    fun blankIsTreatedAsUnset() {
        val env = Env(envFilePath = "does-not-exist", realEnv = { "" })
        assertNull(env["FOO"])
    }

    @Test
    fun requireThrowsWhenMissing() {
        val env = Env(envFilePath = "does-not-exist", realEnv = { null })
        assertFailsWith<IllegalStateException> { env.require("MISSING") }
    }

    @Test
    fun intReturnsDefaultWhenUnset() {
        val env = Env(envFilePath = "does-not-exist", realEnv = { null })
        assertEquals(8080, env.int("SERVER_PORT", default = 8080))
    }

    @Test
    fun parsesQuotedValuesAndSkipsComments() {
        val file = tempEnv(
            """
            # a comment
            QUOTED="hello world"

            PLAIN=value
            """.trimIndent(),
        )
        val env = Env(envFilePath = file.path, realEnv = { null })
        assertEquals("hello world", env["QUOTED"])
        assertEquals("value", env["PLAIN"])
    }

    private fun tempEnv(content: String): File =
        File.createTempFile("env", ".test").apply {
            writeText(content)
            deleteOnExit()
        }
}
