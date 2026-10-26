package com.opson.server.db

import com.opson.server.config.DatabaseConfig
import com.opson.server.domain.UserId
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.update
import org.junit.AfterClass
import org.junit.Assume
import org.junit.BeforeClass
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID

/**
 * Base class for tests that need a real Postgres. Spins up a single
 * Testcontainers Postgres for the whole test class (`@BeforeClass`), runs Flyway
 * migrations through the production [Database.connect] path, and exposes a
 * [Database] handle.
 *
 * Why class-level (not per-test) containers: starting Postgres costs ~3s cold;
 * per-test containers would make the suite unusable. Tests in a class don't
 * share state — clean tables in `@After` (see PostgresProfileRepositoryTest) or
 * use unique data per test.
 *
 * If Docker isn't reachable the suite is skipped (JUnit `Assume`) rather than
 * failing red, so contributors without Docker still get a green build.
 *
 * ```
 * class MyRepoTest : DatabaseTest() {
 *     @Test fun something() = runTest {
 *         val userId = seedAuthUser()
 *         database.transaction { … }
 *     }
 * }
 * ```
 */
abstract class DatabaseTest {

    protected val database: Database
        get() = sharedDatabase ?: error("Database not initialized; @BeforeClass must run")

    /**
     * Insert (or no-op if present) a row in the stub `auth.users` and return it
     * as a [UserId]. Tests that create rows referencing `user_id` must seed the
     * matching auth row first — the V2 FK blocks orphan profiles.
     */
    protected fun seedAuthUser(
        id: UUID = UUID.randomUUID(),
        isAnonymous: Boolean = false,
    ): UserId {
        database.blockingTransaction {
            AuthUsersTable.insertIgnore {
                it[AuthUsersTable.id] = id
                it[AuthUsersTable.isAnonymous] = isAnonymous
            }
            AuthUsersTable.update({ AuthUsersTable.id eq id }) {
                it[AuthUsersTable.isAnonymous] = isAnonymous
            }
        }
        return UserId(id)
    }

    /**
     * Set (or clear, with null) `auth.users.banned_until` for a seeded user —
     * what the Supabase dashboard's ban action does to the real table.
     *
     * Raw SQL rather than an Exposed column mapping so the test doesn't have
     * to pin a TIMESTAMPTZ column type — the JDBC driver coerces the bound
     * timestamp into the real `auth.users.banned_until` column directly.
     */
    protected fun setBannedUntil(userId: UserId, until: java.time.Instant?) {
        database.blockingTransaction {
            org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
                stmt = "UPDATE auth.users SET banned_until = ? WHERE id = ?",
                args = listOf(
                    org.jetbrains.exposed.sql.javatime.JavaInstantColumnType() to until,
                    org.jetbrains.exposed.sql.UUIDColumnType() to userId.value,
                ),
            )
        }
    }

    /**
     * A config for the shared container with other pool settings, for tests
     * that need a second [Database] with its own timeouts. [databaseName]
     * points it at another database in the same container (see
     * [createEmptyDatabase]).
     */
    protected fun configWith(
        statementTimeoutMs: Long = DatabaseConfig.DEFAULT_STATEMENT_TIMEOUT_MS,
        poolTimeoutMs: Long = DatabaseConfig.DEFAULT_POOL_TIMEOUT_MS,
        poolMaxSize: Int = 2,
        databaseName: String? = null,
    ): DatabaseConfig {
        val c = container ?: error("Container not started; @BeforeClass must run")
        val url = databaseName?.let { c.jdbcUrl.replace("/${c.databaseName}", "/$it") } ?: c.jdbcUrl
        return DatabaseConfig(
            jdbcUrl = url,
            username = c.username,
            password = c.password,
            poolMaxSize = poolMaxSize,
            poolMinIdle = 0,
            poolTimeoutMs = poolTimeoutMs,
            statementTimeoutMs = statementTimeoutMs,
        )
    }

    /** A new, unmigrated database in the shared container, with the `auth.users` stub, so Flyway can run from V1. */
    protected fun createEmptyDatabase(name: String) {
        val c = container ?: error("Container not started; @BeforeClass must run")
        java.sql.DriverManager.getConnection(c.jdbcUrl, c.username, c.password).use { conn ->
            conn.createStatement().use { it.execute("CREATE DATABASE $name") }
        }
        val script = DatabaseTest::class.java.classLoader.getResource("init-auth.sql")!!.readText()
        java.sql.DriverManager.getConnection(configWith(databaseName = name).jdbcUrl, c.username, c.password).use { conn ->
            conn.createStatement().use { it.execute(script) }
        }
    }

    /** Exposed mapping for the minimal `auth.users` stub (see init-auth.sql). */
    private object AuthUsersTable : Table("auth.users") {
        val id = uuid("id")
        val isAnonymous = bool("is_anonymous")
        override val primaryKey = PrimaryKey(id)
    }

    companion object {
        private const val POSTGRES_IMAGE = "postgres:16-alpine"

        private var container: PostgreSQLContainer<*>? = null
        private var sharedDatabase: Database? = null

        @JvmStatic
        @BeforeClass
        fun startPostgres() {
            Assume.assumeTrue(
                "Docker is not available; skipping Postgres integration tests",
                isDockerAvailable(),
            )
            val c = PostgreSQLContainer(POSTGRES_IMAGE)
                .withDatabaseName("template_test")
                .withUsername("template")
                .withPassword("template")
                // Runs before Flyway so the V2 FK to auth.users(id) resolves.
                // The real auth schema is owned by Supabase Auth; the stub is
                // just id + is_anonymous.
                .withInitScript("init-auth.sql")
                .also { it.start() }
            container = c
            sharedDatabase = Database.connect(
                DatabaseConfig(
                    jdbcUrl = c.jdbcUrl,
                    username = c.username,
                    password = c.password,
                    poolMaxSize = 4,
                    poolMinIdle = 1,
                ),
            )
        }

        private fun isDockerAvailable(): Boolean = try {
            DockerClientFactory.instance().client().pingCmd().exec()
            true
        } catch (_: Throwable) {
            false
        }

        @JvmStatic
        @AfterClass
        fun stopPostgres() {
            sharedDatabase?.close()
            sharedDatabase = null
            container?.stop()
            container = null
        }
    }
}
