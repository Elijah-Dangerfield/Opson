package com.opson.server.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * The connection-string parser is the only thing between a value pasted from
 * the database dashboard and a successful Hikari handshake. Cover the realistic
 * shapes — direct connection, session pooler, special chars in the password —
 * and the obvious failure modes.
 */
class ParsedPostgresUrlTest {

    @Test
    fun parsesDirectConnection_withPlainPassword() {
        val parsed = ParsedPostgresUrl.parse(
            "postgresql://postgres:secret@db.example.supabase.co:5432/postgres",
        )
        assertEquals("jdbc:postgresql://db.example.supabase.co:5432/postgres", parsed.jdbcUrl)
        assertEquals("postgres", parsed.username)
        assertEquals("secret", parsed.password)
    }

    @Test
    fun urlDecodesPasswordWithSpecialChars() {
        // `$` in a password must be URL-encoded as %24 in the connection string.
        val parsed = ParsedPostgresUrl.parse(
            "postgresql://postgres:nD58ubv82mzv%24EV@db.example.supabase.co:5432/postgres",
        )
        assertEquals("nD58ubv82mzv\$EV", parsed.password)
    }

    @Test
    fun parsesSessionPoolerUrl_keepsUserPrefixSuffix() {
        // Session pooler usernames look like `postgres.<projectref>`.
        val parsed = ParsedPostgresUrl.parse(
            "postgresql://postgres.abcdef:s3cret@aws-0-us-east-1.pooler.supabase.com:5432/postgres",
        )
        assertEquals("postgres.abcdef", parsed.username)
        assertEquals("s3cret", parsed.password)
        assertEquals(
            "jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres",
            parsed.jdbcUrl,
        )
    }

    @Test
    fun preservesQueryParams() {
        val parsed = ParsedPostgresUrl.parse(
            "postgresql://u:p@host:5432/db?sslmode=require&application_name=app",
        )
        assertEquals(
            "jdbc:postgresql://host:5432/db?sslmode=require&application_name=app",
            parsed.jdbcUrl,
        )
    }

    @Test
    fun rejectsNonPostgresScheme() {
        assertFailsWith<IllegalArgumentException> {
            ParsedPostgresUrl.parse("mysql://u:p@host/db")
        }
    }

    @Test
    fun aRejectedUrlIsNeverQuoted() {
        // The message reaches the boot log and Sentry; a password must not.
        val rejected = listOf(
            "mysql://u:hunter2@host/db",
            "u:hunter2@host:5432/db",
            "u:hunter2://@host/db",
            "jdbc:postgresql://u:hunter2@host/db",
            "postgresql://u:hunter2%zz@host/db",
        )
        for (raw in rejected) {
            val message = assertFailsWith<IllegalArgumentException> { ParsedPostgresUrl.parse(raw) }.message.orEmpty()
            assertFalse("hunter2" in message, message)
        }
    }

    @Test
    fun rejectsUrlWithoutCredentials() {
        assertFailsWith<IllegalArgumentException> {
            ParsedPostgresUrl.parse("postgresql://host:5432/db")
        }
    }
}
