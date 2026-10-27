package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.config.FoodSourcesConfig
import com.opson.server.db.DatabaseTest
import com.opson.server.di.ServerComponent
import com.opson.server.di.create
import com.opson.server.installApp
import com.opson.server.testing.TestJwt
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

/**
 * `/v1/admin/users`, real graph and real Postgres, and the install sightings that fill in what
 * build an account runs: a signed-in request is all it takes.
 */
class UserAdminRoutesTest : DatabaseTest() {

    private val token = "admin-secret"
    private val json = Json { ignoreUnknownKeys = true }

    @After
    fun clean() {
        database.blockingTransaction { TransactionManager.current().exec("DELETE FROM user_installs") }
    }

    @Test
    fun bothRoutesNeedTheAdminToken() = adminApp {
        val user = seedAuthUser()
        assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/admin/users/search") { body("{}") }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/admin/users/$user").status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/v1/admin/users/$user") { header("X-Admin-Token", "wrong") }.status,
        )
    }

    @Test
    fun aSignedInRequestRecordsTheBuild_andTheLookupShowsIt() = adminApp {
        val user = seedAuthUser(isAnonymous = true)
        client.get("/v1/app-config") {
            header(HttpHeaders.Authorization, "Bearer ${TestJwt.token(user)}")
            header("X-Platform", "ios")
            header("X-App-Version", "0.2.0")
            header("X-Build-Number", "52")
            header("X-Install-Id", "install-a")
        }

        // Written after the response, off the request.
        val detail = withTimeout(10_000) {
            var found: UserDetailResponse
            do {
                delay(25)
                found = detailOf(user.toString())
            } while (found.installs.isEmpty())
            found
        }
        assertEquals(listOf("ios" to "0.2.0"), detail.installs.map { it.platform to it.appVersion })
        assertEquals(52, detail.installs.single().buildNumber)
        assertEquals("install-a", detail.installs.single().installId)
        assertEquals(true, detail.user.isGuest)

        val found = search(user.toString())
        assertEquals(listOf(user.toString()), found.users.map { it.userId })
        assertEquals("0.2.0", found.users.single().appVersion)
    }

    @Test
    fun anUnknownOrMalformedIdIsSaidPlainly() = adminApp {
        val unknown = java.util.UUID.randomUUID()
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/admin/users/$unknown") { admin() }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/admin/users/not-an-id") { admin() }.status)
    }

    private fun adminApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val component = ServerComponent::class.create(database, null, FoodSourcesConfig(), Clock.System)
        application {
            installApp(component, verification = TestJwt.verification, adminConfig = AdminConfig(apiToken = token))
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.search(query: String): UserSearchResponse =
        json.decodeFromString(
            client.post("/v1/admin/users/search") {
                admin()
                body("""{"query":"$query"}""")
            }.bodyAsText(),
        )

    private suspend fun ApplicationTestBuilder.detailOf(id: String): UserDetailResponse =
        json.decodeFromString(client.get("/v1/admin/users/$id") { admin() }.bodyAsText())

    private fun io.ktor.client.request.HttpRequestBuilder.admin() = header("X-Admin-Token", token)

    private fun io.ktor.client.request.HttpRequestBuilder.body(text: String) {
        contentType(ContentType.Application.Json)
        setBody(text)
    }
}
