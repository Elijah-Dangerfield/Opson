package com.opson.server.db

import com.opson.server.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database as ExposedDatabase
import org.jetbrains.exposed.sql.DatabaseConfig as ExposedDatabaseConfig
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction as exposedBlockingTransaction
import java.sql.SQLException
import java.sql.SQLTransientConnectionException

/**
 * The server's database handle. Owns the Hikari connection pool, runs Flyway
 * migrations on construction, and exposes a coroutine-friendly `transaction { … }`
 * for repositories.
 *
 * Constructed once at startup from [DatabaseConfig], passed into the DI graph,
 * and shared by all repositories. Tests build their own instance pointed at a
 * Testcontainers Postgres.
 *
 * A transaction that cannot get a connection within the pool timeout, or runs
 * a statement past the statement timeout, throws [DatabaseBusyException],
 * which the API answers with 503. It is not retried: retrying a timeout only
 * multiplies the wait. A serialization failure or deadlock is retried, up to
 * [MAX_ATTEMPTS] runs of the block in all.
 */
class Database private constructor(
    private val dataSource: HikariDataSource,
    private val exposed: ExposedDatabase,
) {

    /**
     * Run a block inside a database transaction on the IO dispatcher. Use this
     * from suspend handler code — it suspends rather than blocking the Netty
     * event loop. Repositories express "I need a transaction" and stay
     * dispatcher-agnostic.
     */
    suspend fun <T> transaction(block: suspend () -> T): T =
        // Pin the transaction (and Exposed's connection open/commit/close) to
        // the IO dispatcher rather than inheriting the caller's. In production
        // this keeps blocking JDBC off the Netty event loop; under `runTest`
        // it keeps that work off the virtual TestDispatcher, so Exposed's
        // connection-cleanup coroutines don't outlive the test scope and leak
        // as UncaughtExceptionsBeforeTest into the next test.
        retryingConflicts { newSuspendedTransaction(context = Dispatchers.IO, db = exposed) { block() } }

    /**
     * [transaction] at READ COMMITTED instead of the pool's REPEATABLE READ.
     *
     * For a transaction that takes a lock and must then see everything
     * committed before the lock was granted. Under REPEATABLE READ the
     * snapshot is taken by the first statement, which is the lock statement
     * itself, so work committed while it waited stays invisible. `/v1/sync`
     * needs this: it serializes a user's syncs with an advisory lock, then
     * pulls by cursor.
     */
    suspend fun <T> readCommittedTransaction(block: suspend () -> T): T =
        retryingConflicts {
            newSuspendedTransaction(
                context = Dispatchers.IO,
                db = exposed,
                transactionIsolation = java.sql.Connection.TRANSACTION_READ_COMMITTED,
            ) { block() }
        }

    /**
     * Synchronous transaction. Only for startup / test code that isn't inside a
     * coroutine. Production request paths use [transaction].
     */
    fun <T> blockingTransaction(block: () -> T): T =
        exposedBlockingTransaction(exposed) { block() }

    fun close() {
        dataSource.close()
    }

    private suspend fun <T> retryingConflicts(run: suspend () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return run()
            } catch (e: SQLException) {
                when {
                    e.isTimeout() -> throw DatabaseBusyException(e)
                    e.isConflict() && attempt < MAX_ATTEMPTS -> attempt++
                    else -> throw e
                }
            }
        }
    }

    companion object {
        /** Runs of a transaction block that hit a serialization failure or deadlock. */
        const val MAX_ATTEMPTS = 3

        /**
         * Build a Hikari pool + Exposed handle for the given config, then run
         * Flyway migrations. The returned [Database] is ready to use.
         *
         * Migrations get their own unpooled connection without the statement
         * timeout: an index build on a large table legitimately runs for minutes.
         */
        fun connect(config: DatabaseConfig): Database {
            runMigrations(config)
            val dataSource = buildDataSource(config)
            // Exposed retries any SQLException three times by default, which turned
            // one statement timeout into three. [retryingConflicts] retries instead.
            val exposed = ExposedDatabase.connect(dataSource, databaseConfig = ExposedDatabaseConfig { defaultMaxAttempts = 1 })
            return Database(dataSource, exposed)
        }

        private fun buildDataSource(config: DatabaseConfig): HikariDataSource {
            val hikariConfig = HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                username = config.username
                password = config.password
                maximumPoolSize = config.poolMaxSize
                minimumIdle = config.poolMinIdle
                // Fail fast on a misconfigured connection rather than letting
                // requests pile up on a non-existent pool.
                initializationFailTimeout = 10_000
                connectionTimeout = config.poolTimeoutMs
                connectionInitSql = "SET statement_timeout = ${config.statementTimeoutMs}"
                // Commits the init SQL. Without it, autocommit off, Hikari rolls the SET back.
                isIsolateInternalQueries = true
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                addDataSourceProperty("ApplicationName", "opson-server")
            }
            return HikariDataSource(hikariConfig)
        }

        /**
         * Applies the migrations under [locations] (tests add their own).
         *
         * Every migration connection waits at most [MIGRATION_LOCK_TIMEOUT] for a lock. An
         * `ALTER TABLE` needs an exclusive lock, and while it waits for one every query on that
         * table queues behind it: one long transaction on `foods` would otherwise turn a
         * millisecond catalog change into an outage of search for as long as it ran. Timed out,
         * the migration fails, Flyway rolls it back, the new server doesn't boot and the old one
         * keeps serving; the next deploy tries again. Set here rather than in each file because
         * Flyway checksums applied migrations, and an edited V33 would stop every database that
         * already has it from booting.
         */
        internal fun runMigrations(config: DatabaseConfig, locations: List<String> = listOf(MIGRATIONS)) {
            Flyway.configure()
                .dataSource(config.jdbcUrl, config.username, config.password)
                .locations(*locations.toTypedArray())
                .baselineOnMigrate(true)
                .initSql("SET lock_timeout = '$MIGRATION_LOCK_TIMEOUT'")
                .load()
                .migrate()
        }

        internal const val MIGRATIONS = "classpath:db/migration"

        /** How long a migration waits for a lock before it fails (Postgres interval syntax). */
        internal const val MIGRATION_LOCK_TIMEOUT = "5s"

        private fun SQLException.chain(): Sequence<Throwable> = generateSequence<Throwable>(this) { it.cause }

        /** Postgres cancelled the statement (`statement_timeout`), or the pool had no connection in time. */
        private fun SQLException.isTimeout(): Boolean =
            chain().any { it is SQLTransientConnectionException || (it as? SQLException)?.sqlState == QUERY_CANCELED }

        private fun SQLException.isConflict(): Boolean =
            chain().any { (it as? SQLException)?.sqlState in CONFLICT_STATES }

        private const val QUERY_CANCELED = "57014"
        private val CONFLICT_STATES = setOf("40001", "40P01")
    }
}

/** The database could not answer in time. Routes let it propagate; StatusPages answers 503 `busy`. */
class DatabaseBusyException(cause: Throwable) : RuntimeException("database busy", cause)
