package com.opson.integration.helpers

import com.opson.server.config.DatabaseConfig
import com.opson.server.config.FoodSourcesConfig
import com.opson.server.db.Database
import com.opson.server.di.ServerComponent
import com.opson.server.di.create
import com.opson.server.installApp
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager
import java.util.UUID

/**
 * A real Ktor server — the production plugins + routes via the same [installApp]
 * seam production boots through, a real [ServerComponent] over a real Postgres
 * (Testcontainers), test JWT auth — bound to an ephemeral port.
 *
 * Bound to a real port on purpose: the production client builds its own Ktor
 * engine and talks over real TCP, which Ktor's in-memory `testApplication`
 * engine can't serve. Auth is the server's real validate/challenge path with a
 * [com.opson.server.plugins.JwtVerification.Static] verifier swapped in
 * (see [IntegrationAuth]).
 *
 * The Postgres container + migrated [Database] are shared across the whole JVM
 * (starting Postgres costs seconds; per-test containers would make the suite
 * unusable — same reasoning as the server's own `DatabaseTest`). Each
 * [InProcessServer] gets a fresh engine + a fresh [ServerComponent]; tests
 * isolate by minting random user ids rather than by wiping tables.
 */
class InProcessServer : AutoCloseable {

    private val engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
        embeddedServer(Netty, port = 0) {
            installApp(
                component = ServerComponent::class.create(sharedDatabase(), null, FoodSourcesConfig()),
                verification = IntegrationAuth.verification,
            )
        }.start(wait = false)

    private val boundPort: Int = runBlocking {
        engine.engine.resolvedConnectors().first().port
    }

    /** The base URL a client's `NetworkConfig` should point at. */
    val baseUrl: String get() = "http://127.0.0.1:$boundPort"

    /**
     * Insert (or no-op if present) a row in the stub `auth.users` for [userId].
     * The V2 FK blocks orphan profiles, so any flow that reaches `/v1/me` needs
     * the auth row a real Supabase sign-up would have created. Raw JDBC because
     * Exposed's DSL lives on the server's `implementation` classpath and isn't
     * visible here — one insert doesn't justify re-declaring the dependency.
     */
    fun seedAuthUser(userId: String, isAnonymous: Boolean = true) {
        val container = sharedContainer()
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
            .use { connection ->
                connection.prepareStatement(
                    "INSERT INTO auth.users (id, is_anonymous) VALUES (?, ?) ON CONFLICT (id) DO NOTHING",
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(userId))
                    statement.setBoolean(2, isAnonymous)
                    statement.executeUpdate()
                }
            }
    }

    override fun close() {
        engine.stop(0, 0)
    }

    companion object {
        private const val POSTGRES_IMAGE = "postgres:16-alpine"

        private val shared by lazy {
            val container = PostgreSQLContainer(POSTGRES_IMAGE)
                .withDatabaseName("template_integration")
                .withUsername("template")
                .withPassword("template")
                // Runs before Flyway so the V2 FK to auth.users(id) resolves.
                // Same stub the server's DatabaseTest uses (copied into this
                // module's test resources).
                .withInitScript("init-auth.sql")
                .also { it.start() }
            val database = Database.connect(
                DatabaseConfig(
                    jdbcUrl = container.jdbcUrl,
                    username = container.username,
                    password = container.password,
                    poolMaxSize = 4,
                    poolMinIdle = 1,
                ),
            )
            container to database
        }

        private fun sharedContainer(): PostgreSQLContainer<*> = shared.first
        private fun sharedDatabase(): Database = shared.second

        /**
         * Skip (JUnit `Assume`) rather than fail when Docker isn't reachable,
         * so contributors without Docker still get a green build — mirrors the
         * server's `DatabaseTest`.
         */
        fun assumeDockerAvailable() {
            Assume.assumeTrue(
                "Docker is not available; skipping integration tests",
                try {
                    DockerClientFactory.instance().client().pingCmd().exec()
                    true
                } catch (_: Throwable) {
                    false
                },
            )
        }
    }
}
