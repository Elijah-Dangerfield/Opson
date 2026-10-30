package com.opson.server.db

import org.flywaydb.core.api.FlywayException
import java.sql.DriverManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * A migration that can't get its lock fails instead of waiting. While an `ALTER TABLE` waits for
 * its exclusive lock every query on the table queues behind it, so an unbounded wait during a
 * deploy is an outage of whatever reads that table. Real wall time against the shared container,
 * in a database of its own so the probe column never reaches the other tests.
 */
class MigrationLockTimeoutTest : DatabaseTest() {

    @Test
    fun aMigrationThatCantGetItsLockFailsWithinTheTimeout_andRunsOnceTheLockIsFree() {
        createEmptyDatabase("migrate_lock_timeout")
        val config = configWith(databaseName = "migrate_lock_timeout")
        Database.runMigrations(config)
        val withProbe = listOf(Database.MIGRATIONS, "classpath:db/lock-timeout-test")

        DriverManager.getConnection(config.jdbcUrl, config.username, config.password).use { reader ->
            // A long transaction that has read foods: an ALTER must wait for it to end.
            reader.autoCommit = false
            reader.createStatement().use { it.execute("LOCK TABLE foods IN ACCESS SHARE MODE") }

            val started = TimeSource.Monotonic.markNow()
            val migrating = CompletableFuture.supplyAsync { runCatching { Database.runMigrations(config, withProbe) } }
            val outcome = try {
                migrating.get(30, TimeUnit.SECONDS)
            } finally {
                reader.rollback()
            }
            val failure = assertIs<FlywayException>(outcome.exceptionOrNull(), "the migration gave up: $outcome")
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it.message.orEmpty().contains("lock timeout") }, "$failure")
            assertTrue(started.elapsedNow() < 20.seconds, "failed after ${started.elapsedNow()}")
        }

        Database.runMigrations(config, withProbe)
        DriverManager.getConnection(config.jdbcUrl, config.username, config.password).use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_name = 'foods' AND column_name = 'lock_timeout_probe'").use { rs ->
                    rs.next()
                    assertTrue(rs.getInt(1) == 1, "the next try, with nothing in the way, applies it")
                }
            }
        }
    }
}
