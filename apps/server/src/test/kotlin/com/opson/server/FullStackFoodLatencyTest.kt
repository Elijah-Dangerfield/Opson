package com.opson.server

import com.opson.server.config.DeviceIntegrityConfig
import com.opson.server.config.FoodSourcesConfig
import com.opson.server.db.Database
import com.opson.server.db.DatabaseTest
import com.opson.server.di.ServerComponent
import com.opson.server.di.create
import com.opson.server.plugins.ProblemResponse
import com.opson.server.testing.DiaryFixtures
import com.opson.server.testing.TestJwt
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Search and food detail against real Postgres while another session holds
 * locks, the way the monthly Open Food Facts reload does (it swaps
 * `off_foods` under an ACCESS EXCLUSIVE lock). On dev, during an import,
 * search took 27 s and detail 17 s.
 *
 *  - With `FOOD_SEARCH_OFF_ENABLED` off, neither reads `off_foods`, so a lock
 *    on it changes nothing.
 *  - When `foods` itself can't be read, the phone gets a 503 `busy` within
 *    about the statement timeout, not a hang.
 */
class FullStackFoodLatencyTest : DatabaseTest() {

    private lateinit var db: Database
    private lateinit var locker: Connection

    @Before
    fun setUp() {
        db = Database.connect(configWith(statementTimeoutMs = STATEMENT_TIMEOUT_MS, poolMaxSize = 4))
        val c = configWith()
        locker = DriverManager.getConnection(c.jdbcUrl, c.username, c.password).apply { autoCommit = false }
    }

    @After
    fun tearDown() {
        locker.rollback()
        locker.close()
        DiaryFixtures.cleanAll(database)
        db.close()
    }

    private fun lock(table: String) {
        locker.createStatement().use { it.execute("LOCK TABLE $table IN ACCESS EXCLUSIVE MODE") }
    }

    @Test
    fun withOpenFoodFactsOff_aLockedOffFoodsTableDoesNotSlowSearchOrDetail() = runFoodApp { get ->
        val banana = DiaryFixtures.insertFood(database, name = "Bananas, raw")
        lock("off_foods")

        val started = TimeSource.Monotonic.markNow()
        assertEquals(HttpStatusCode.OK, get("/v1/foods/search?q=banana").status)
        assertEquals(HttpStatusCode.OK, get("/v1/foods/$banana").status)
        assertEquals(HttpStatusCode.NotFound, get("/v1/foods/00000000-0000-0000-0000-000000000001").status)
        assertTrue(started.elapsedNow() < 2.seconds, "took ${started.elapsedNow()}")
    }

    @Test
    fun whenFoodsCannotBeRead_searchAndDetailAnswerBusyFast() = runFoodApp { get ->
        val banana = DiaryFixtures.insertFood(database, name = "Bananas, raw")
        lock("foods")

        for (path in listOf("/v1/foods/search?q=banana", "/v1/foods/$banana")) {
            val started = TimeSource.Monotonic.markNow()
            val response = get(path)
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status, path)
            assertEquals("busy", response.body<ProblemResponse>().error.code)
            assertEquals("2", response.headers[HttpHeaders.RetryAfter])
            assertTrue(started.elapsedNow() < 2.seconds, "$path took ${started.elapsedNow()}")
        }
    }

    private fun runFoodApp(block: suspend (suspend (String) -> io.ktor.client.statement.HttpResponse) -> Unit) {
        val user = seedAuthUser()
        val component = ServerComponent::class.create(db, null, FoodSourcesConfig(searchOpenFoodFacts = false), Clock.System, DeviceIntegrityConfig())
        testApplication {
            application { installApp(component, TestJwt.verification) }
            val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            block { path -> client.get(path) { header(HttpHeaders.Authorization, TestJwt.bearer(user)) } }
        }
    }

    private companion object {
        const val STATEMENT_TIMEOUT_MS = 500L
    }
}
