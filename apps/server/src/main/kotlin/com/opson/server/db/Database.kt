package com.opson.server.db

import com.opson.server.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database as ExposedDatabase
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction as exposedBlockingTransaction
import javax.sql.DataSource

/**
 * The server's database handle. Owns the Hikari connection pool, runs Flyway
 * migrations on construction, and exposes a coroutine-friendly `transaction { … }`
 * for repositories.
 *
 * Constructed once at startup from [DatabaseConfig], passed into the DI graph,
 * and shared by all repositories. Tests build their own instance pointed at a
 * Testcontainers Postgres.
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
        newSuspendedTransaction(context = Dispatchers.IO, db = exposed) { block() }

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
        newSuspendedTransaction(
            context = Dispatchers.IO,
            db = exposed,
            transactionIsolation = java.sql.Connection.TRANSACTION_READ_COMMITTED,
        ) { block() }

    /**
     * Synchronous transaction. Only for startup / test code that isn't inside a
     * coroutine. Production request paths use [transaction].
     */
    fun <T> blockingTransaction(block: () -> T): T =
        exposedBlockingTransaction(exposed) { block() }

    fun close() {
        dataSource.close()
    }

    companion object {
        /**
         * Build a Hikari pool + Exposed handle for the given config, then run
         * Flyway migrations. The returned [Database] is ready to use.
         */
        fun connect(config: DatabaseConfig): Database {
            val dataSource = buildDataSource(config)
            runMigrations(dataSource)
            val exposed = ExposedDatabase.connect(dataSource)
            return Database(dataSource, exposed)
        }

        /** Test entry point — skip migrations because the caller controls them. */
        internal fun connectWithoutMigrations(dataSource: HikariDataSource): Database {
            val exposed = ExposedDatabase.connect(dataSource)
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
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                addDataSourceProperty("ApplicationName", "opson-server")
            }
            return HikariDataSource(hikariConfig)
        }

        private fun runMigrations(dataSource: DataSource) {
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate()
        }
    }
}
