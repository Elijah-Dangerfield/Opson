package com.opson.integration.helpers

import com.opson.server.config.AdminConfig
import com.opson.server.config.AnthropicConfig
import com.opson.server.config.AppleSignInConfig
import com.opson.server.config.DatabaseConfig
import com.opson.server.config.DeviceIntegrityConfig
import com.opson.server.config.FoodSourcesConfig
import com.opson.server.config.SupabaseConfig
import com.opson.server.config.WaitlistConfig
import com.opson.server.db.Database
import com.opson.server.di.ServerComponent
import com.opson.server.di.create
import com.opson.server.installApp
import com.opson.server.plugins.BanGate
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.sql.Connection
import java.sql.DriverManager
import java.util.Base64
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
 * The server is configured as production is, with the outside services it
 * calls (Supabase admin, Anthropic, Open Food Facts) pointed at [upstreams].
 * [photoEstimates] false leaves the Anthropic key unset, the way a server with
 * photo estimates switched off runs.
 *
 * The Postgres container + migrated [Database] are shared across the whole JVM
 * (starting Postgres costs seconds; per-test containers would make the suite
 * unusable — same reasoning as the server's own `DatabaseTest`). Each
 * [InProcessServer] gets a fresh engine + a fresh [ServerComponent], so its
 * in-memory rate limiters start empty; tests isolate data by minting random
 * user ids, barcodes and food names rather than by wiping tables.
 */
class InProcessServer(photoEstimates: Boolean = true) : AutoCloseable {

    /** The server's clock: real time until a test steps it, e.g. past the app-config cache. */
    val clock = TestClock()

    val upstreams = FakeUpstreams(deleteAuthUser = { id -> sql("DELETE FROM auth.users WHERE id = ?::uuid", id) })

    private val component: ServerComponent = ServerComponent::class.create(
        sharedDatabase(),
        SupabaseConfig(
            projectUrl = upstreams.baseUrl,
            serviceRoleKey = "integration-service-role",
            appleSignIn = AppleSignInConfig(
                teamId = "INTEGRATN1",
                keyId = "INTEGRATN2",
                privateKeyPem = throwawayEcKeyPem(),
                baseUrl = upstreams.baseUrl,
            ),
        ),
        FoodSourcesConfig(
            anthropic = if (photoEstimates) AnthropicConfig(apiKey = "integration", baseUrl = upstreams.baseUrl) else null,
            openFoodFactsUrl = upstreams.baseUrl,
        ),
        clock,
        DeviceIntegrityConfig(),
    )

    private val engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
        embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            installApp(
                component = component,
                verification = IntegrationAuth.verification,
                // As production's `module` builds it, so a banned user meets the same 403.
                banGate = BanGate(component.moderationRepository, appealUrl = APPEAL_URL, clock = clock),
                adminConfig = AdminConfig(apiToken = ADMIN_TOKEN),
                waitlistConfig = WaitlistConfig(allowedOrigins = setOf(SITE_ORIGIN)),
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
     * the auth row a real Supabase sign-up would have created.
     */
    fun seedAuthUser(userId: String, isAnonymous: Boolean = true) {
        sql("INSERT INTO auth.users (id, is_anonymous) VALUES (?::uuid, ?) ON CONFLICT (id) DO NOTHING", userId, isAnonymous)
    }

    /**
     * A catalog food, as the USDA importer writes one, with a unique source id.
     * Returns its id. Raw JDBC because Exposed's DSL lives on the server's
     * `implementation` classpath and isn't visible here.
     */
    fun seedFood(
        name: String,
        kcalPer100g: Double,
        barcode: String? = null,
        portionsJson: String = "[]",
        source: String = "usda_sr",
        category: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        sql(
            """
            INSERT INTO foods (id, source, source_id, name, barcode, kcal_100g, protein_100g, micros, portions, category)
            VALUES (?::uuid, ?, ?, ?, ?, ?, 1, '{}'::jsonb, ?::jsonb, ?)
            """.trimIndent(),
            id, source, id, name, barcode, kcalPer100g, portionsJson, category,
        )
        return id
    }

    /** Rows of a query, each column by its lower-case name. */
    fun query(sql: String, vararg args: Any?): List<Map<String, Any?>> = connection { connection ->
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
            statement.executeQuery().use { rows ->
                val meta = rows.metaData
                buildList {
                    while (rows.next()) {
                        add((1..meta.columnCount).associate { meta.getColumnName(it).lowercase() to rows.getObject(it) })
                    }
                }
            }
        }
    }

    /** A statement with no result. Returns the affected row count. */
    fun sql(sql: String, vararg args: Any?): Int = connection { connection ->
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
            statement.executeUpdate()
        }
    }

    /**
     * Every table with a `user_id` column, read from the migrated schema rather than listed, so a
     * table a new migration adds is in every check that walks them the day it lands.
     */
    fun userTables(): List<String> = query(
        """
        SELECT c.table_name AS name
        FROM information_schema.columns c
        JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name
        WHERE c.table_schema = 'public' AND c.column_name = 'user_id' AND t.table_type = 'BASE TABLE'
        ORDER BY 1
        """.trimIndent(),
    ).map { it.getValue("name").toString() }

    /** How many rows [table] holds for [userId], tombstones included. */
    fun countFor(table: String, userId: String): Long =
        query("SELECT count(*) AS n FROM $table WHERE user_id = ?::uuid", userId).single()["n"] as Long

    override fun close() {
        engine.stop(0, 0)
        upstreams.close()
    }

    companion object {
        private const val POSTGRES_IMAGE = "postgres:16-alpine"

        /** `X-Admin-Token` for the `/v1/admin` routes. */
        const val ADMIN_TOKEN = "integration-admin-token"

        /** The one origin the waitlist form accepts here, standing in for the marketing site. */
        const val SITE_ORIGIN = "https://site.integration.test"

        /** Where a banned user is sent to appeal, as `ACCESS_APPEAL_URL` sets it. */
        const val APPEAL_URL = "https://site.integration.test/appeal"

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

        private fun <T> connection(block: (Connection) -> T): T {
            val container = sharedContainer()
            return DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use(block)
        }

        /**
         * Skip (JUnit `Assume`) rather than fail when Docker isn't reachable,
         * so contributors without Docker still get a green build — mirrors the
         * server's `DatabaseTest`. Not on CI: there a skip is every journey
         * passing without running, so a runner without Docker fails instead.
         */
        fun assumeDockerAvailable() {
            val available = try {
                DockerClientFactory.instance().client().pingCmd().exec()
                true
            } catch (_: Throwable) {
                false
            }
            check(available || System.getenv("CI") != "true") {
                "Docker is not available on CI, so every integration journey would skip and the job pass"
            }
            Assume.assumeTrue("Docker is not available; skipping integration tests", available)
        }

        /** A P-256 key in the `.p8` format Apple hands out, so the server signs real client secrets. */
        private fun throwawayEcKeyPem(): String {
            val key = KeyPairGenerator.getInstance("EC")
                .apply { initialize(ECGenParameterSpec("secp256r1")) }
                .generateKeyPair()
                .private
            return "-----BEGIN PRIVATE KEY-----\n${Base64.getMimeEncoder().encodeToString(key.encoded)}\n-----END PRIVATE KEY-----"
        }
    }
}
