package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.data.PostgresPublicRecipeRepository
import com.opson.server.data.RecipeReverter
import com.opson.server.db.DatabaseTest
import com.opson.server.domain.CatalogSource
import com.opson.server.domain.ContributionGate
import com.opson.server.domain.ContributionSignals
import com.opson.server.domain.ContributionVerdict
import com.opson.server.domain.Food
import com.opson.server.domain.Nutrients
import com.opson.server.domain.Recipe
import com.opson.server.domain.RecipeLimiter
import com.opson.server.domain.RecipeRules
import com.opson.server.domain.UserId
import com.opson.server.plugins.ProblemResponse
import com.opson.server.plugins.installAuthentication
import com.opson.server.plugins.installBodyLimit
import com.opson.server.plugins.installRateLimits
import com.opson.server.plugins.installSerialization
import com.opson.server.plugins.installStatusPages
import com.opson.server.testing.DiaryFixtures
import com.opson.server.testing.FakeFoodCatalog
import com.opson.server.testing.TestJwt
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/** Sharing a recipe, reading it with no account, finding it, reporting it and the console's side. */
class RecipeRoutesTest : DatabaseTest() {

    @After
    fun clean() = DiaryFixtures.cleanAll(database)

    private val adminToken = "admin-secret"

    private val beans = Food(
        id = UUID.fromString("0f0f0f0f-0000-0000-0000-0000000000be"),
        source = CatalogSource.UsdaSr,
        sourceId = "beans",
        name = "Beans, black, canned",
        brand = null,
        barcode = null,
        per100g = Nutrients(kcal = 91.0, protein = 6.0, carbs = 16.6, fat = 0.3, fiber = 6.9),
        micros = emptyMap(),
        portions = emptyList(),
    )

    private val repository get() = PostgresPublicRecipeRepository(database, Clock.System)

    private suspend fun withApp(
        gate: ContributionGate = object : ContributionGate {
            override suspend fun check(userId: UserId, signals: ContributionSignals) = ContributionVerdict.Allowed
            override suspend fun mayReceiveFreePro(userId: UserId) = true
        },
        catalog: FakeFoodCatalog = FakeFoodCatalog(listOf(beans)),
        block: suspend (HttpClient) -> Unit,
    ) {
        testApplication {
            application {
                installSerialization()
                installBodyLimit()
                installRateLimits()
                installStatusPages()
                installAuthentication(TestJwt.verification)
                routing {
                    publicRecipeRoutes(repository, catalog)
                    recipeRoutes(repository, catalog, RecipeLimiter(Clock.System), Clock.System, gate)
                    recipeAdminRoutes(AdminConfig(apiToken = adminToken), repository, catalog)
                }
            }
            block(createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } })
        }
    }

    private suspend fun HttpClient.publish(user: UserId, recipe: Recipe, showName: Boolean = false): HttpResponse =
        post("/v1/recipes/${recipe.id}/publish") {
            header(HttpHeaders.Authorization, TestJwt.bearer(user))
            contentType(ContentType.Application.Json)
            setBody(PublishRecipeRequest(recipe.toRowDto(), showName))
        }

    private suspend fun HttpClient.report(user: UserId, code: String, reason: String = "spam"): HttpResponse =
        post("/v1/recipes/public/$code/reports") {
            header(HttpHeaders.Authorization, TestJwt.bearer(user))
            contentType(ContentType.Application.Json)
            setBody(RecipeReportRequest(UUID.randomUUID().toString(), reason))
        }

    private suspend fun HttpResponse.code(): String = body<ProblemResponse>().error.code

    @Test
    fun aSharedRecipeReadsWithNoAccount_withItsNumbersWorkedOutFromTheCatalog() = runTest {
        val owner = seedAuthUser()
        val recipe = DiaryFixtures.recipe(foodId = beans.id)
        withApp { client ->
            val published = client.publish(owner, recipe).body<PublishRecipeResponse>()
            assertEquals("live", published.state)
            assertEquals(8, published.code.length)

            val response = client.get("/v1/recipes/public/${published.code}") { header(HttpHeaders.Origin, "https://www.opsontracker.com") }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            val body = response.body<PublicRecipeResponse>()
            assertNull(body.by, "the owner didn't choose to show their name")
            val beansRow = body.ingredients.first()
            assertTrue(beansRow.checked)
            // 800 g of a food with 91 kcal per 100 g, not the 1000 the creator wrote.
            assertEquals(728.0, beansRow.nutrients.kcal!!, 0.01)
            assertEquals(false, body.ingredients[1].checked)
            assertEquals(1, body.checkedCount)
            assertEquals((728.0 + 1150.0) / 6, body.perServing.kcal!!, 0.01)
            assertTrue(body.ingredients.none { it.name.contains("customFood") })
        }
    }

    @Test
    fun stoppingTakesItDown_andSharingAgainKeepsTheCode() = runTest {
        val owner = seedAuthUser()
        val recipe = DiaryFixtures.recipe()
        withApp { client ->
            val code = client.publish(owner, recipe).body<PublishRecipeResponse>().code
            val stop = client.post("/v1/recipes/${recipe.id}/unpublish") { header(HttpHeaders.Authorization, TestJwt.bearer(owner)) }
            assertEquals(HttpStatusCode.NoContent, stop.status)
            val gone = client.get("/v1/recipes/public/$code")
            assertEquals(HttpStatusCode.Gone, gone.status)
            assertEquals("not_shared", gone.code())

            val again = client.publish(owner, recipe.copy(clientUpdatedAt = recipe.clientUpdatedAt + 1.hours)).body<PublishRecipeResponse>()
            assertEquals(code, again.code)
            assertEquals(HttpStatusCode.NotFound, client.get("/v1/recipes/public/zzzz2222").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/v1/recipes/public/not-a-code").status)
        }
    }

    @Test
    fun aBodyOverTheRoutesCapIs413_whetherItSaysItsLengthOrNot() = runTest {
        val owner = seedAuthUser()
        val recipe = DiaryFixtures.recipe()
        withApp { client ->
            val padded = PublishRecipeRequest(recipe.toRowDto().copy(notes = "x".repeat(RecipeRules.MAX_PUBLISH_BODY_BYTES.toInt())))
            val publish = client.post("/v1/recipes/${recipe.id}/publish") {
                header(HttpHeaders.Authorization, TestJwt.bearer(owner))
                contentType(ContentType.Application.Json)
                setBody(padded)
            }
            assertEquals(HttpStatusCode.PayloadTooLarge, publish.status)
            val code = client.publish(owner, recipe).body<PublishRecipeResponse>().code
            val report = client.post("/v1/recipes/public/$code/reports") {
                header(HttpHeaders.Authorization, TestJwt.bearer(seedAuthUser()))
                setBody(chunkedJson("{\"id\": \"${UUID.randomUUID()}\", \"reason\": \"spam\", \"note\": \"${"x".repeat(RecipeRules.MAX_REPORT_BODY_BYTES.toInt())}\"}"))
            }
            assertEquals(HttpStatusCode.PayloadTooLarge, report.status)
        }
    }

    /** A body sent chunked, with no Content-Length to check up front. */
    private fun chunkedJson(text: String) = object : OutgoingContent.WriteChannelContent() {
        override val contentType = ContentType.Application.Json
        override suspend fun writeTo(channel: ByteWriteChannel) = channel.writeFully(text.encodeToByteArray())
    }

    /** A recipe whose every ingredient is a different catalog food, by weight. */
    private fun recipeOf(foods: List<Food>, name: String = "Bean salad"): Recipe {
        val items = buildJsonArray {
            for (food in foods) {
                add(
                    buildJsonObject {
                        put("name", food.name)
                        put("grams", 100.0)
                        put("kcal", 50.0)
                        put("foodId", food.id.toString())
                    },
                )
            }
        }
        return DiaryFixtures.recipe(name = name).copy(itemsJson = items.toString(), itemCount = foods.size)
    }

    @Test
    fun aRecipesIngredientsLoadInOneGo_andTheNextReadOfTheSameRecipeIsServedAsBefore() = runTest {
        val owner = seedAuthUser()
        val searcher = seedAuthUser()
        val foods = List(6) { beans.copy(id = UUID.randomUUID(), sourceId = "beans-$it", name = "Beans $it") }
        val catalog = FakeFoodCatalog(foods)
        val salad = recipeOf(foods.take(3), name = "Bean salad")
        val stew = recipeOf(foods.drop(3), name = "Bean stew")
        withApp(catalog = catalog) { client ->
            val code = client.publish(owner, salad).body<PublishRecipeResponse>().code
            client.publish(owner, stew)

            catalog.loads = 0
            val page = client.get("/v1/recipes/public/$code").body<PublicRecipeResponse>()
            assertEquals(3, page.ingredients.count { it.nutrients.kcal == 91.0 }, "worked out from the catalog")
            assertEquals(1, catalog.loads, "three foods, one load")
            client.get("/v1/recipes/public/$code")
            assertEquals(1, catalog.loads, "the same recipe again is served as before")

            client.publish(owner, salad.copy(name = "Bean salad, bigger", clientUpdatedAt = salad.clientUpdatedAt + 1.hours))
            assertEquals("Bean salad, bigger", client.get("/v1/recipes/public/$code").body<PublicRecipeResponse>().name)
            assertEquals(2, catalog.loads, "an edit is served fresh")

            catalog.loads = 0
            val hits = client.get("/v1/recipes/search?q=bean") { header(HttpHeaders.Authorization, TestJwt.bearer(searcher)) }
                .body<RecipeSearchResponse>()
            assertEquals(2, hits.recipes.size)
            assertEquals(1, catalog.loads, "six foods across two recipes, one load")
        }
    }

    @Test
    fun someoneElsesRecipeIdIsNotYoursToShare() = runTest {
        val owner = seedAuthUser()
        val other = seedAuthUser()
        val recipe = DiaryFixtures.recipe()
        withApp { client ->
            client.publish(owner, recipe)
            val stolen = client.publish(other, recipe.copy(name = "Mine now", clientUpdatedAt = recipe.clientUpdatedAt + 1.hours))
            assertEquals(HttpStatusCode.NotFound, stolen.status)
            val unpublish = client.post("/v1/recipes/${recipe.id}/unpublish") { header(HttpHeaders.Authorization, TestJwt.bearer(other)) }
            assertEquals(HttpStatusCode.NotFound, unpublish.status)
        }
    }

    @Test
    fun aRecipeWithNoIngredientsOrAWrongIdIsRefused() = runTest {
        val owner = seedAuthUser()
        withApp { client ->
            val empty = DiaryFixtures.recipe().copy(itemsJson = "[]", itemCount = 0)
            assertEquals("empty_recipe", client.publish(owner, empty).code())
            val recipe = DiaryFixtures.recipe()
            val mismatched = client.post("/v1/recipes/${UUID.randomUUID()}/publish") {
                header(HttpHeaders.Authorization, TestJwt.bearer(owner))
                contentType(ContentType.Application.Json)
                setBody(PublishRecipeRequest(recipe.toRowDto()))
            }
            assertEquals("invalid_id", mismatched.code())
            assertEquals("invalid_recipe", client.publish(owner, recipe.copy(servings = 0.0)).code())
        }
    }

    @Test
    fun aRestrictedAccountCantShare() = runTest {
        val owner = seedAuthUser()
        val gate = object : ContributionGate {
            override suspend fun check(userId: UserId, signals: ContributionSignals) =
                ContributionVerdict.Restricted(com.opson.server.domain.RestrictedBy.Account)
            override suspend fun mayReceiveFreePro(userId: UserId) = false
        }
        withApp(gate) { client ->
            val response = client.publish(owner, DiaryFixtures.recipe())
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals("contributions_restricted", response.code())
        }
    }

    @Test
    fun searchFindsLiveRecipesByName() = runTest {
        val owner = seedAuthUser()
        val searcher = seedAuthUser()
        withApp { client ->
            client.publish(owner, DiaryFixtures.recipe(name = "Weeknight chili"))
            val private = DiaryFixtures.recipe(name = "Secret chili")
            database.transaction { com.opson.server.data.upsertRecipe(owner, private) }
            val hits = client.get("/v1/recipes/search?q=chili") { header(HttpHeaders.Authorization, TestJwt.bearer(searcher)) }
                .body<RecipeSearchResponse>()
            assertEquals(listOf("Weeknight chili"), hits.recipes.map { it.name })
            val short = client.get("/v1/recipes/search?q=c") { header(HttpHeaders.Authorization, TestJwt.bearer(searcher)) }
            assertEquals(HttpStatusCode.BadRequest, short.status)
        }
    }

    @Test
    fun threeReportsHideIt_andTheConsoleCanBringItBackOrTakeItDown() = runTest {
        val owner = seedAuthUser()
        val reporters = List(3) { seedAuthUser() }
        val recipe = DiaryFixtures.recipe()
        withApp { client ->
            val code = client.publish(owner, recipe).body<PublishRecipeResponse>().code
            assertEquals("own_recipe", client.report(owner, code).code())
            assertEquals(HttpStatusCode.Created, client.report(reporters[0], code).status)
            assertEquals(HttpStatusCode.OK, client.report(reporters[0], code).status, "a second report from one person is the same report")
            assertEquals(HttpStatusCode.OK, client.get("/v1/recipes/public/$code").status)
            client.report(reporters[1], code, "unsafe")
            client.report(reporters[2], code)
            assertEquals(HttpStatusCode.Gone, client.get("/v1/recipes/public/$code").status, "three people hide it")

            val queue = client.get("/v1/admin/recipes?filter=reported") { header("X-Admin-Token", adminToken) }.body<AdminRecipeListResponse>()
            val row = queue.recipes.single()
            assertEquals(3, row.openReports)
            assertEquals("hidden", row.state)
            assertEquals(mapOf("spam" to 2, "unsafe" to 1), row.reasons)

            val dismissed = client.post("/v1/admin/recipes/${recipe.id}/dismiss-reports") { header("X-Admin-Token", adminToken) }
                .body<RecipeModerationResponse>()
            assertEquals(3, dismissed.reports)
            assertEquals(HttpStatusCode.OK, client.get("/v1/recipes/public/$code").status)

            val removed = client.post("/v1/admin/recipes/${recipe.id}/remove") {
                header("X-Admin-Token", adminToken)
                header("X-Admin-Actor", "Elijah")
                contentType(ContentType.Application.Json)
                setBody(RemoveRecipeRequest("Unsafe advice"))
            }
            assertEquals(HttpStatusCode.OK, removed.status)
            assertEquals(HttpStatusCode.Gone, client.get("/v1/recipes/public/$code").status)
            assertEquals("removed", client.publish(owner, recipe.copy(clientUpdatedAt = recipe.clientUpdatedAt + 1.hours)).code())

            client.post("/v1/admin/recipes/${recipe.id}/restore") { header("X-Admin-Token", adminToken) }
            assertEquals(HttpStatusCode.OK, client.publish(owner, recipe.copy(clientUpdatedAt = recipe.clientUpdatedAt + 2.hours)).status)
        }
    }

    @Test
    fun stoppingAndSharingAgainCantPutAHiddenRecipeBackUp() = runTest {
        val owner = seedAuthUser()
        val recipe = DiaryFixtures.recipe()
        withApp { client ->
            val code = client.publish(owner, recipe).body<PublishRecipeResponse>().code
            repeat(RecipeRules.HIDE_AFTER_REPORTS) { client.report(seedAuthUser(), code) }
            assertEquals(HttpStatusCode.Gone, client.get("/v1/recipes/public/$code").status)

            client.post("/v1/recipes/${recipe.id}/unpublish") { header(HttpHeaders.Authorization, TestJwt.bearer(owner)) }
            val again = client.publish(owner, recipe.copy(clientUpdatedAt = recipe.clientUpdatedAt + 1.hours)).body<PublishRecipeResponse>()
            assertEquals("hidden", again.state)
            assertEquals(HttpStatusCode.Gone, client.get("/v1/recipes/public/$code").status)

            client.post("/v1/admin/recipes/${recipe.id}/dismiss-reports") { header("X-Admin-Token", adminToken) }
            assertEquals(HttpStatusCode.OK, client.get("/v1/recipes/public/$code").status, "a person looked and it is back")
        }
    }

    @Test
    fun aSaveCountsOncePerPerson_andNeverForTheOwner() = runTest {
        val owner = seedAuthUser()
        val savers = List(2) { seedAuthUser() }
        val recipe = DiaryFixtures.recipe()
        withApp { client ->
            val code = client.publish(owner, recipe).body<PublishRecipeResponse>().code
            suspend fun save(user: UserId) = client.post("/v1/recipes/public/$code/saves") { header(HttpHeaders.Authorization, TestJwt.bearer(user)) }
            repeat(3) { assertEquals(HttpStatusCode.NoContent, save(savers[0]).status) }
            assertEquals(HttpStatusCode.NoContent, save(savers[1]).status)
            assertEquals(HttpStatusCode.NoContent, save(owner).status)
            assertEquals(HttpStatusCode.NotFound, client.post("/v1/recipes/public/zzzz2222/saves") { header(HttpHeaders.Authorization, TestJwt.bearer(owner)) }.status)

            val live = client.get("/v1/admin/recipes?filter=live") { header("X-Admin-Token", adminToken) }.body<AdminRecipeListResponse>()
            assertEquals(2, live.recipes.single().saveCount)
        }
    }

    @Test
    fun aRollbackTakesEverySharedRecipeDown() = runTest {
        val owner = seedAuthUser()
        val first = DiaryFixtures.recipe()
        val second = DiaryFixtures.recipe(name = "Pancakes")
        withApp { client ->
            val code = client.publish(owner, first).body<PublishRecipeResponse>().code
            client.publish(owner, second)
            val result = RecipeReverter(database, Clock.System).revert(owner, "Elijah", "fake_data", null)
            assertEquals(2, result.undone)
            assertEquals(HttpStatusCode.Gone, client.get("/v1/recipes/public/$code").status)
        }
    }

    @Test
    fun theOwnersNameShowsOnlyWhenTheyChoseIt_andNeverAMadeUpOne() = runTest {
        val owner = seedAuthUser()
        val name = "Sam " + UUID.randomUUID().toString().take(6)
        database.blockingTransaction {
            TransactionManager.current().exec("INSERT INTO profiles (user_id, display_name) VALUES ('${owner.value}', '$name')")
        }
        val generated = seedAuthUser()
        database.blockingTransaction {
            TransactionManager.current().exec("INSERT INTO profiles (user_id, display_name) VALUES ('${generated.value}', 'user-${generated.value.toString().take(8)}')")
        }
        withApp { client ->
            val named = client.publish(owner, DiaryFixtures.recipe(), showName = true).body<PublishRecipeResponse>().code
            assertEquals(name, client.get("/v1/recipes/public/$named").body<PublicRecipeResponse>().by)
            val made = client.publish(generated, DiaryFixtures.recipe(), showName = true).body<PublishRecipeResponse>().code
            assertNull(client.get("/v1/recipes/public/$made").body<PublicRecipeResponse>().by)
        }
    }
}
