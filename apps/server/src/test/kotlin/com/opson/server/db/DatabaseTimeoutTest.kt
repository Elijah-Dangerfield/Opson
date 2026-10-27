package com.opson.server.db

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * How long a request can wait on Postgres. On dev, search once took 27 s
 * while an import built indexes: nothing bounded a statement, Hikari waited
 * 30 s for a connection, and Exposed ran every failed transaction three times.
 * These run on real wall time against the shared container (no virtual time:
 * the waits are Postgres's and Hikari's).
 */
class DatabaseTimeoutTest : DatabaseTest() {

    private fun sql(statement: String) {
        TransactionManager.current().exec(statement)
    }

    @Test
    fun aStatementPastTheTimeoutFailsAsBusy_andRunsOnce() = runBlocking {
        val db = Database.connect(configWith(statementTimeoutMs = 200))
        try {
            val runs = AtomicInteger()
            val started = TimeSource.Monotonic.markNow()

            val shown = db.transaction {
                var v = ""
                TransactionManager.current().exec("SHOW statement_timeout") { rs -> if (rs.next()) v = rs.getString(1) }
                v
            }
            assertEquals("200ms", shown, "every pooled connection carries the timeout")
            assertFailsWith<DatabaseBusyException> {
                db.transaction {
                    runs.incrementAndGet()
                    sql("SELECT pg_sleep(3)")
                }
            }

            assertEquals(1, runs.get(), "a timeout is not retried")
            assertTrue(started.elapsedNow() < 2.seconds, "failed after ${started.elapsedNow()}")
        } finally {
            db.close()
        }
    }

    @Test
    fun waitingForAConnectionFailsAsBusyAfterThePoolTimeout() = runBlocking {
        val db = Database.connect(configWith(poolMaxSize = 1, poolTimeoutMs = 300))
        try {
            val holding = CompletableDeferred<Unit>()
            val holder = async {
                db.transaction {
                    sql("SELECT 1")
                    holding.complete(Unit)
                    sql("SELECT pg_sleep(2)")
                }
            }
            holding.await()
            val started = TimeSource.Monotonic.markNow()

            assertFailsWith<DatabaseBusyException> { db.transaction { sql("SELECT 1") } }

            assertTrue(started.elapsedNow() < 1.5.seconds, "failed after ${started.elapsedNow()}")
            holder.await()
        } finally {
            db.close()
        }
    }

    @Test
    fun aSerializationFailureIsRetried() = runBlocking {
        val db = Database.connect(configWith())
        try {
            db.transaction { sql("CREATE TABLE IF NOT EXISTS retry_probe (id INT PRIMARY KEY, n INT NOT NULL)") }
            db.transaction { sql("INSERT INTO retry_probe VALUES (1, 0) ON CONFLICT (id) DO UPDATE SET n = 0") }
            val runs = AtomicInteger()
            val snapshotTaken = CompletableDeferred<Unit>()
            val otherCommitted = CompletableDeferred<Unit>()

            val first = async {
                db.transaction {
                    sql("SELECT n FROM retry_probe WHERE id = 1")
                    if (runs.incrementAndGet() == 1) {
                        snapshotTaken.complete(Unit)
                        otherCommitted.await()
                    }
                    // REPEATABLE READ: on the first run this row changed after the
                    // snapshot, so Postgres refuses the update (40001).
                    sql("UPDATE retry_probe SET n = n + 1 WHERE id = 1")
                }
            }
            snapshotTaken.await()
            db.transaction { sql("UPDATE retry_probe SET n = n + 10 WHERE id = 1") }
            otherCommitted.complete(Unit)
            first.await()

            assertEquals(2, runs.get())
            val n = db.transaction {
                var value = -1
                TransactionManager.current().exec("SELECT n FROM retry_probe WHERE id = 1") { rs -> if (rs.next()) value = rs.getInt(1) }
                value
            }
            assertEquals(11, n)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationsAreNotBoundByTheStatementTimeout() {
        createEmptyDatabase("migrate_under_timeout")

        // Every migration statement takes longer than 1 ms; through the pool they would all be cancelled.
        Database.connect(configWith(statementTimeoutMs = 1, databaseName = "migrate_under_timeout")).close()

        val c = configWith(databaseName = "migrate_under_timeout")
        java.sql.DriverManager.getConnection(c.jdbcUrl, c.username, c.password).use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success").use { rs ->
                    rs.next()
                    assertTrue(rs.getInt(1) >= 18, "migrations applied: ${rs.getInt(1)}")
                }
            }
        }
    }
}
